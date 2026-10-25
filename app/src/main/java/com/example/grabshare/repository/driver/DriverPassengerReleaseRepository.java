package com.example.grabshare.repository.driver;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/** Hủy nhận một khách và mở lại chuyến, không hủy chuyến hoặc xóa lịch sử. */
public final class DriverPassengerReleaseRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth authentication = FirebaseAuth.getInstance();

    public void release(final String requestId, final String driverId,
            final RepositoryCallback<Void> callback) {
        if (!validId(requestId) || !validId(driverId) || !sameUser(driverId)) {
            callback.onError(new IllegalStateException("Yêu cầu hoặc phiên đăng nhập không hợp lệ."));
            return;
        }
        final DocumentReference requestReference = firestore.collection("requests").document(requestId);
        final DocumentReference noticeReference = firestore.collection("notifications")
                .document(requestId + "_REQUEST_RELEASED");
        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Mỗi lần transaction chạy lại đều đọc trạng thái mới và kiểm tra đúng UID. */
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                requireUser(driverId);
                DocumentSnapshot request = transaction.get(requestReference);
                if (!request.exists() || !driverId.equals(request.getString("driverId"))) {
                    throw new IllegalStateException("Bạn không có quyền hủy nhận khách này.");
                }
                String tripId = request.getString("tripId");
                String passengerId = request.getString("passengerId");
                if (!validId(tripId) || !validId(passengerId) || driverId.equals(passengerId)
                        || !requestId.equals(tripId + "_" + passengerId)) {
                    throw new IllegalStateException("Dữ liệu khách và chuyến không nhất quán.");
                }
                DocumentReference tripReference = firestore.collection("trips").document(tripId);
                DocumentSnapshot trip = transaction.get(tripReference);
                if (!trip.exists() || !driverId.equals(trip.getString("driverId"))) {
                    throw new IllegalStateException("Không tìm thấy chuyến do bạn quản lý.");
                }
                // Yêu cầu đã hủy thì chỉ đối soát, không mở lại chuyến đang giữ khách mới.
                // Không đọc thông báo gửi cho khách: chủ xe không có quyền đọc Inbox của khách.
                if ("CANCELLED".equals(request.getString("status"))) {
                    return null;
                }
                Timestamp departureAt = trip.getTimestamp("departureAt");
                if (departureAt == null || departureAt.compareTo(Timestamp.now()) <= 0) {
                    throw new IllegalStateException("Đã đến giờ khởi hành, không thể hủy nhận khách.");
                }
                if (!"ACCEPTED".equals(request.getString("status"))
                        || !"FULL".equals(trip.getString("status"))
                        || !Long.valueOf(0L).equals(trip.getLong("availableSeats"))
                        || !requestId.equals(trip.getString("acceptedRequestId"))) {
                    throw new IllegalStateException("Khách không còn là người đang được nhận. Hãy tải lại chuyến.");
                }
                Map<String, Object> requestUpdates = new HashMap<String, Object>();
                requestUpdates.put("status", "CANCELLED");
                requestUpdates.put("updatedAt", FieldValue.serverTimestamp());
                Map<String, Object> tripUpdates = new HashMap<String, Object>();
                tripUpdates.put("status", "OPEN");
                tripUpdates.put("availableSeats", 1L);
                tripUpdates.put("acceptedRequestId", "");
                tripUpdates.put("updatedAt", FieldValue.serverTimestamp());
                Map<String, Object> notification = new HashMap<String, Object>();
                notification.put("recipientId", passengerId);
                notification.put("actorId", driverId);
                notification.put("type", "REQUEST_RELEASED");
                notification.put("tripId", tripId);
                notification.put("requestId", requestId);
                notification.put("isRead", false);
                notification.put("createdAt", FieldValue.serverTimestamp());
                requireUser(driverId);
                transaction.update(requestReference, requestUpdates);
                transaction.update(tripReference, tripUpdates);
                transaction.set(noticeReference, notification);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (sameUser(driverId)) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception error) {
                if (sameUser(driverId)) {
                    callback.onError(error);
                }
            }
        });
    }

    private boolean validId(String value) {
        return value != null && !value.trim().isEmpty() && !value.contains("/");
    }

    private boolean sameUser(String driverId) {
        FirebaseUser user = authentication.getCurrentUser();
        return user != null && driverId.equals(user.getUid());
    }

    private void requireUser(String driverId) {
        if (!sameUser(driverId)) {
            throw new IllegalStateException("Phiên đăng nhập đã thay đổi.");
        }
    }
}
