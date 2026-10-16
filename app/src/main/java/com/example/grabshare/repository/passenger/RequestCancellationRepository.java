package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository xử lý nghiệp vụ hủy yêu cầu đi nhờ của Hành khách (Thuận-5).
 */
public class RequestCancellationRepository {

    private final FirebaseFirestore firestore;

    public RequestCancellationRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Thực hiện hủy yêu cầu PENDING bằng Firestore Transaction nguyên tử.
     * @param requestId ID của request cần hủy.
     * @param passengerId UID người hủy (phải khớp chính chủ).
     * @param callback Nhận kết quả thành công hoặc thất bại.
     */
    public void cancelPendingRequest(final String requestId, final String passengerId, final RepositoryCallback<Void> callback) {
        if (requestId == null || requestId.trim().isEmpty() || passengerId == null || passengerId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Thông tin yêu cầu hoặc tài khoản không hợp lệ."));
            }
            return;
        }

        final DocumentReference requestRef = firestore.collection("requests").document(requestId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot requestDoc = transaction.get(requestRef);
                if (!requestDoc.exists()) {
                    throw new FirebaseFirestoreException("Yêu cầu không tồn tại.", FirebaseFirestoreException.Code.NOT_FOUND);
                }

                TripRequest request = requestDoc.toObject(TripRequest.class);
                if (request == null) {
                    throw new FirebaseFirestoreException("Dữ liệu yêu cầu không hợp lệ.", FirebaseFirestoreException.Code.ABORTED);
                }

                if (!passengerId.equals(request.getPassengerId())) {
                    throw new FirebaseFirestoreException("Bạn không có quyền hủy yêu cầu này.", FirebaseFirestoreException.Code.PERMISSION_DENIED);
                }

                if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
                    throw new FirebaseFirestoreException("Chỉ có thể hủy yêu cầu đang ở trạng thái Chờ xác nhận.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                String tripId = request.getTripId();
                DocumentReference tripRef = null;
                Trip trip = null;
                if (tripId != null && !tripId.trim().isEmpty()) {
                    tripRef = firestore.collection("trips").document(tripId);
                    DocumentSnapshot tripDoc = transaction.get(tripRef);
                    if (tripDoc.exists()) {
                        trip = tripDoc.toObject(Trip.class);
                    }
                }

                // Cập nhật trạng thái Request thành CANCELLED
                Map<String, Object> requestUpdates = new HashMap<>();
                requestUpdates.put("status", "CANCELLED");
                requestUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(requestRef, requestUpdates);

                // Tạo thông báo cho chủ xe nếu thông tin hợp lệ
                if (trip != null && trip.getDriverId() != null && !trip.getDriverId().trim().isEmpty()) {
                    DocumentReference notificationRef = firestore.collection("notifications").document();
                    Map<String, Object> notifData = new HashMap<>();
                    notifData.put("recipientId", trip.getDriverId());
                    notifData.put("actorId", passengerId);
                    notifData.put("type", "REQUEST_CANCELLED");
                    notifData.put("tripId", tripId);
                    notifData.put("requestId", requestId);
                    notifData.put("isRead", false);
                    notifData.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(notificationRef, notifData);
                }

                return null;
            }
        }).addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }
}
