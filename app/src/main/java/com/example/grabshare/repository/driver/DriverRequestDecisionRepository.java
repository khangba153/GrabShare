package com.example.grabshare.repository.driver;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Timestamp;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý chấp nhận hoặc từ chối yêu cầu đi nhờ bằng transaction.
 * Những dữ liệu liên quan được ghi theo đúng schema Firestore Rules.
 */
public class DriverRequestDecisionRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    public DriverRequestDecisionRepository(
            FirebaseFirestore firestore,
            FirebaseAuth firebaseAuth
    ) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    /** Chấp nhận yêu cầu và chuyển chuyến sang FULL trong cùng transaction. */
    public void acceptRequest(
            String requestId,
            RepositoryCallback<Void> callback
    ) {
        respondToRequest(requestId, "ACCEPTED", callback);
    }

    /** Từ chối yêu cầu; không thay đổi trạng thái chuyến. */
    public void rejectRequest(
            String requestId,
            RepositoryCallback<Void> callback
    ) {
        respondToRequest(requestId, "REJECTED", callback);
    }

    /**
     * Xác minh tài khoản, yêu cầu và chuyến trước khi ghi. Mọi trạng thái liên quan
     * đến một lần phản hồi được commit nguyên tử, không queue thao tác khi offline.
     */
    private void respondToRequest(
            String requestId,
            final String targetStatus,
            final RepositoryCallback<Void> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(new IllegalStateException(
                    "Bạn cần đăng nhập để phản hồi yêu cầu."
            ));
            return;
        }

        if (requestId == null || requestId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Mã yêu cầu không hợp lệ."));
            return;
        }

        final String safeRequestId = requestId.trim();
        final String currentDriverId = currentUser.getUid();
        final DocumentReference requestReference = firestore.collection("requests")
                .document(safeRequestId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                DocumentSnapshot requestSnapshot = transaction.get(requestReference);

                if (!requestSnapshot.exists()) {
                    throw new FirebaseFirestoreException(
                            "Yêu cầu không tồn tại hoặc đã bị xóa.",
                            FirebaseFirestoreException.Code.NOT_FOUND
                    );
                }

                String requestTripId = requestSnapshot.getString("tripId");
                String requestDriverId = requestSnapshot.getString("driverId");
                String passengerId = requestSnapshot.getString("passengerId");
                String currentStatus = requestSnapshot.getString("status");

                if (!currentDriverId.equals(requestDriverId)) {
                    throw new FirebaseFirestoreException(
                            "Bạn không có quyền phản hồi yêu cầu này.",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED
                    );
                }

                if (requestTripId == null || requestTripId.trim().isEmpty()
                        || passengerId == null || passengerId.trim().isEmpty()
                        || passengerId.equals(currentDriverId)
                        || !safeRequestId.equals(requestTripId + "_" + passengerId)) {
                    throw new FirebaseFirestoreException(
                            "Dữ liệu yêu cầu không hợp lệ.",
                            FirebaseFirestoreException.Code.ABORTED
                    );
                }

                // Cho phép đối soát an toàn nếu máy chủ đã ghi thành công nhưng
                // ứng dụng không nhận được kết quả lần trước.
                if (targetStatus.equals(currentStatus)) {
                    return null;
                }

                if (!"PENDING".equals(currentStatus)) {
                    throw new FirebaseFirestoreException(
                            "Yêu cầu không còn ở trạng thái Chờ duyệt.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }

                final String safeTripId = requestTripId.trim();
                final DocumentReference tripReference = firestore.collection("trips")
                        .document(safeTripId);
                DocumentSnapshot tripSnapshot = transaction.get(tripReference);

                if (!tripSnapshot.exists()) {
                    throw new FirebaseFirestoreException(
                            "Chuyến liên quan không tồn tại hoặc đã bị xóa.",
                            FirebaseFirestoreException.Code.NOT_FOUND
                    );
                }

                String tripDriverId = tripSnapshot.getString("driverId");
                String tripStatus = tripSnapshot.getString("status");
                Long availableSeats = tripSnapshot.getLong("availableSeats");
                String acceptedRequestId = tripSnapshot.getString("acceptedRequestId");
                Timestamp departureAt = tripSnapshot.getTimestamp("departureAt");

                if (!currentDriverId.equals(tripDriverId)) {
                    throw new FirebaseFirestoreException(
                            "Yêu cầu không thuộc chuyến do bạn quản lý.",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED
                    );
                }

                if (departureAt == null
                        || departureAt.toDate().getTime() <= System.currentTimeMillis()) {
                    throw new FirebaseFirestoreException(
                            "Đã đến hoặc quá giờ khởi hành; không thể phản hồi yêu cầu.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }

                boolean tripIsOpen = "OPEN".equals(tripStatus)
                        && availableSeats != null
                        && availableSeats.longValue() == 1L
                        && (acceptedRequestId == null || acceptedRequestId.trim().isEmpty());

                boolean tripIsFull = "FULL".equals(tripStatus)
                        && availableSeats != null
                        && availableSeats.longValue() == 0L
                        && acceptedRequestId != null
                        && !acceptedRequestId.trim().isEmpty();

                if ("ACCEPTED".equals(targetStatus) && !tripIsOpen) {
                    throw new FirebaseFirestoreException(
                            "Chuyến không còn chỗ trống để chấp nhận yêu cầu.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }

                if ("REJECTED".equals(targetStatus) && !tripIsOpen && !tripIsFull) {
                    throw new FirebaseFirestoreException(
                            "Chuyến không còn ở trạng thái có thể phản hồi yêu cầu.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }

                String notificationType = "ACCEPTED".equals(targetStatus)
                        ? "REQUEST_ACCEPTED"
                        : "REQUEST_REJECTED";
                DocumentReference notificationReference = firestore
                        .collection("notifications")
                        .document(safeRequestId + "_" + notificationType);

                Map<String, Object> requestUpdates = new HashMap<>();
                requestUpdates.put("status", targetStatus);
                requestUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(requestReference, requestUpdates);

                if ("ACCEPTED".equals(targetStatus)) {
                    Map<String, Object> tripUpdates = new HashMap<>();
                    tripUpdates.put("status", "FULL");
                    tripUpdates.put("availableSeats", 0L);
                    tripUpdates.put("acceptedRequestId", safeRequestId);
                    tripUpdates.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(tripReference, tripUpdates);
                }

                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("recipientId", passengerId);
                notificationData.put("actorId", currentDriverId);
                notificationData.put("type", notificationType);
                notificationData.put("tripId", safeTripId);
                notificationData.put("requestId", safeRequestId);
                notificationData.put("isRead", false);
                notificationData.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(notificationReference, notificationData);

                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                callback.onSuccess(null);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }
}
