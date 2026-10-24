package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.contract.RequestAttemptContract;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.Source;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository xử lý nghiệp vụ hủy yêu cầu đi nhờ của Hành khách (Thuận-5).
 */
public class RequestCancellationRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    /** Dùng cùng FirebaseApp và phiên Auth với Firestore được bàn giao. */
    public RequestCancellationRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
        auth = FirebaseAuth.getInstance(firestore.getApp());
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

        if (!validateSession(passengerId, callback)) {
            return;
        }
        final DocumentReference requestRef = firestore.collection("requests").document(requestId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Kiểm tra lại UID khi transaction thử lại, đọc mọi điều kiện trước ghi. */
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                requireCurrentPassenger(passengerId);
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

                if (trip == null || trip.getDriverId() == null
                        || !trip.getDriverId().equals(request.getDriverId())
                        || (!"OPEN".equals(trip.getStatus()) && !"FULL".equals(trip.getStatus()))) {
                    throw new FirebaseFirestoreException("Chuyến không còn hợp lệ để hủy yêu cầu chờ.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                if (trip.getDepartureAt() == null || !trip.getDepartureAt().toDate().after(new Date())) {
                    throw new FirebaseFirestoreException("Chỉ được hủy trước giờ khởi hành.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                requireActiveAttempt(transaction, request, requestId, passengerId);
                requireCurrentPassenger(passengerId);

                // Cập nhật trạng thái Request thành CANCELLED
                Map<String, Object> requestUpdates = new HashMap<>();
                requestUpdates.put("status", "CANCELLED");
                requestUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(requestRef, requestUpdates);

                // Tạo thông báo REQUEST_CANCELLED cho chủ xe với ID cố định: requestId_REQUEST_CANCELLED
                if (trip != null && trip.getDriverId() != null && !trip.getDriverId().trim().isEmpty()) {
                    String notificationDocId = requestId + "_REQUEST_CANCELLED";
                    DocumentReference notificationRef = firestore.collection("notifications").document(notificationDocId);
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
            /** Không trả thành công của lệnh hủy cho tài khoản mới. */
            @Override
            public void onSuccess(Void unused) {
                if (callback != null && validateSession(passengerId, callback)) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
            /** Trả lỗi thực tế để caller cập nhật đúng trạng thái. */
            @Override
            public void onFailure(Exception e) {
                reconcileCancellationFailure(requestId, passengerId, e, callback);
            }
        });
    }

    /**
     * Thực hiện hủy yêu cầu ACCEPTED đã được chủ xe chấp nhận trước giờ đi (Thuận-5).
     * Giải phóng 1 chỗ ngồi trên Trip, đổi trạng thái Trip về OPEN và xóa pointer acceptedRequestId.
     * Kiểm tra toàn bộ preconditions/invariants nguyên tử trong transaction.
     * @param requestId ID của request cần hủy.
     * @param passengerId UID người hủy (phải khớp chính chủ).
     * @param callback Nhận kết quả thành công hoặc thất bại.
     */
    public void cancelAcceptedRequest(final String requestId, final String passengerId, final RepositoryCallback<Void> callback) {
        if (requestId == null || requestId.trim().isEmpty() || passengerId == null || passengerId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Thông tin yêu cầu hoặc tài khoản không hợp lệ."));
            }
            return;
        }

        if (!validateSession(passengerId, callback)) {
            return;
        }
        final DocumentReference requestRef = firestore.collection("requests").document(requestId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Giữ invariant FULL/0/pointer và kiểm tra lại phiên trong transaction. */
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                requireCurrentPassenger(passengerId);
                DocumentSnapshot requestDoc = transaction.get(requestRef);
                if (!requestDoc.exists()) {
                    throw new FirebaseFirestoreException("Yêu cầu không tồn tại.", FirebaseFirestoreException.Code.NOT_FOUND);
                }

                TripRequest request = requestDoc.toObject(TripRequest.class);
                if (request == null) {
                    throw new FirebaseFirestoreException("Dữ liệu yêu cầu không hợp lệ.", FirebaseFirestoreException.Code.ABORTED);
                }

                // 1. Kiểm tra chính chủ request
                if (!passengerId.equals(request.getPassengerId())) {
                    throw new FirebaseFirestoreException("Bạn không có quyền hủy yêu cầu này.", FirebaseFirestoreException.Code.PERMISSION_DENIED);
                }

                // 2. Kiểm tra trạng thái request phải là ACCEPTED
                if (!"ACCEPTED".equalsIgnoreCase(request.getStatus())) {
                    throw new FirebaseFirestoreException("Chỉ có thể hủy yêu cầu đang ở trạng thái Đã chấp nhận.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                String tripId = request.getTripId();
                if (tripId == null || tripId.trim().isEmpty()) {
                    throw new FirebaseFirestoreException("Yêu cầu thiếu mã chuyến đi liên quan.", FirebaseFirestoreException.Code.ABORTED);
                }

                DocumentReference tripRef = firestore.collection("trips").document(tripId);
                DocumentSnapshot tripDoc = transaction.get(tripRef);
                if (!tripDoc.exists()) {
                    throw new FirebaseFirestoreException("Chuyến đi liên quan không tồn tại.", FirebaseFirestoreException.Code.NOT_FOUND);
                }

                Trip trip = tripDoc.toObject(Trip.class);
                if (trip == null) {
                    throw new FirebaseFirestoreException("Dữ liệu chuyến đi không hợp lệ.", FirebaseFirestoreException.Code.ABORTED);
                }

                // 3. Kiểm tra driverId liên quan phải khớp với request
                if (request.getDriverId() == null || !request.getDriverId().equals(trip.getDriverId())) {
                    throw new FirebaseFirestoreException("Thông tin chủ xe không khớp giữa yêu cầu và chuyến đi.", FirebaseFirestoreException.Code.ABORTED);
                }

                // 4. Kiểm tra trạng thái Trip phải là FULL (không hủy nếu trip đã CANCELLED, COMPLETED hoặc OPEN)
                if (!"FULL".equalsIgnoreCase(trip.getStatus())) {
                    throw new FirebaseFirestoreException("Trạng thái chuyến đi không hợp lệ để hủy tham gia (chỉ hủy khi chuyến FULL).", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                // 5. Kiểm tra số chỗ trống trên Trip phải là 0 (đang nhận người)
                if (trip.getAvailableSeats() == null || trip.getAvailableSeats() != 0) {
                    throw new FirebaseFirestoreException("Số chỗ trống của chuyến đi không hợp lệ để hủy tham gia.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                // 6. Kiểm tra pointer acceptedRequestId trên Trip phải khớp chính xác với requestId này
                if (trip.getAcceptedRequestId() == null || !requestId.equals(trip.getAcceptedRequestId().trim())) {
                    throw new FirebaseFirestoreException("Mã yêu cầu không khớp với người được nhận trên chuyến đi.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                // 7. Kiểm tra điều kiện thời gian: Hủy bắt buộc phải trước giờ đi
                if (trip.getDepartureAt() == null || !trip.getDepartureAt().toDate().after(new Date())) {
                    throw new FirebaseFirestoreException("Đã quá giờ khởi hành, không thể hủy chuyến đi này.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                requireActiveAttempt(transaction, request, requestId, passengerId);
                requireCurrentPassenger(passengerId);
                // 8. Cập nhật trạng thái Request thành CANCELLED
                Map<String, Object> requestUpdates = new HashMap<>();
                requestUpdates.put("status", "CANCELLED");
                requestUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(requestRef, requestUpdates);

                // 9. Cập nhật Trip: trả lại 1 chỗ (availableSeats = 1), đổi status thành OPEN, xóa pointer acceptedRequestId
                Map<String, Object> tripUpdates = new HashMap<>();
                tripUpdates.put("availableSeats", 1L);
                tripUpdates.put("status", "OPEN");
                tripUpdates.put("acceptedRequestId", "");
                tripUpdates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(tripRef, tripUpdates);

                // 10. Tạo thông báo REQUEST_CANCELLED cho chủ xe với ID cố định: requestId_REQUEST_CANCELLED
                if (trip.getDriverId() != null && !trip.getDriverId().trim().isEmpty()) {
                    String notificationDocId = requestId + "_REQUEST_CANCELLED";
                    DocumentReference notificationRef = firestore.collection("notifications").document(notificationDocId);
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
            /** Chỉ xác nhận cho đúng tài khoản đã yêu cầu hủy. */
            @Override
            public void onSuccess(Void unused) {
                if (callback != null && validateSession(passengerId, callback)) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
            /** Không giả thành công khi giao dịch bị từ chối hoặc mất mạng. */
            @Override
            public void onFailure(Exception e) {
                reconcileCancellationFailure(requestId, passengerId, e, callback);
            }
        });
    }

    /** Yêu cầu theo attempt mới chỉ được hủy khi guard vẫn trỏ đúng lần đó. */
    private void requireActiveAttempt(Transaction transaction, TripRequest request,
            String requestId, String passengerId) throws FirebaseFirestoreException {
        if (!request.hasAttemptNumberField()) return;
        Long attemptNumber = request.getAttemptNumber();
        String tripId = request.getTripId();
        if (attemptNumber == null || attemptNumber < 1 || tripId == null
                || tripId.trim().isEmpty()) {
            throw new FirebaseFirestoreException("Dữ liệu lần gửi yêu cầu không hợp lệ.",
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION);
        }
        DocumentReference guardRef = firestore.document(
                RequestAttemptContract.guardPath(tripId, passengerId));
        DocumentSnapshot guard = transaction.get(guardRef);
        if (!guard.exists() || !requestId.equals(guard.getString("requestId"))
                || !attemptNumber.equals(guard.getLong("attemptNumber"))) {
            throw new FirebaseFirestoreException("Lần gửi này không còn là yêu cầu hiện hành.",
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION);
        }
    }

    /** Sau lỗi ghi, chỉ công nhận hủy nếu server xác nhận chính request đã CANCELLED. */
    private void reconcileCancellationFailure(final String requestId, final String passengerId,
            final Exception writeError, final RepositoryCallback<Void> callback) {
        if (callback == null) return;
        if (!validateSession(passengerId, callback)) return;
        firestore.collection("requests").document(requestId).get(Source.SERVER)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>() {
                    /** Không suy luận thành công từ cache, request khác hoặc phiên đã đổi. */
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (snapshot != null && snapshot.exists()
                                && !snapshot.getMetadata().isFromCache()
                                && !snapshot.getMetadata().hasPendingWrites()
                                && passengerId.equals(snapshot.getString("passengerId"))
                                && "CANCELLED".equals(snapshot.getString("status"))) {
                            if (validateSession(passengerId, callback)) callback.onSuccess(null);
                        } else {
                            callback.onError(writeError);
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    /** Không có xác nhận server thì giữ nguyên lỗi giao dịch. */
                    @Override
                    public void onFailure(Exception readError) {
                        callback.onError(writeError);
                    }
                });
    }

    /** Kiểm tra UID thật trước gọi Firestore và trước trả callback thành công. */
    private boolean validateSession(String passengerId, RepositoryCallback<Void> callback) {
        try {
            requireCurrentPassenger(passengerId);
            return true;
        } catch (FirebaseFirestoreException error) {
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }
    }

    /** Không tin UID caller khi tài khoản đã đổi trong lúc thao tác đang chờ. */
    private void requireCurrentPassenger(String passengerId) throws FirebaseFirestoreException {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !user.getUid().equals(passengerId)) {
            throw new FirebaseFirestoreException("Phiên đăng nhập đã thay đổi.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
        }
    }
}
