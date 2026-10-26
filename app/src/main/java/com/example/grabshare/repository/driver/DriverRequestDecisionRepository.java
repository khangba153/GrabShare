package com.example.grabshare.repository.driver;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.api.VietnamDetourChecker;
import com.example.grabshare.contract.DetourCheckResult;
import android.os.SystemClock;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý chấp nhận hoặc từ chối yêu cầu đi nhờ bằng transaction.
 * Những dữ liệu liên quan được ghi theo đúng schema Firestore Rules.
 */
public class DriverRequestDecisionRepository {

    private static final long ROUTE_VALIDITY_MILLISECONDS = 60000L;

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final VietnamDetourChecker routeChecker = new VietnamDetourChecker();
    private volatile boolean closed;

    public DriverRequestDecisionRepository(
            FirebaseFirestore firestore,
            FirebaseAuth firebaseAuth
    ) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    /** Chấp nhận yêu cầu và chuyển chuyến sang FULL trong cùng transaction. */
    public void acceptRequest(
            final String requestId,
            final RepositoryCallback<Void> callback
    ) {
        final FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || requestId == null || requestId.trim().isEmpty()
                || requestId.contains("/") || closed) {
            callback.onError(new IllegalStateException("Thiếu yêu cầu hoặc phiên đăng nhập hợp lệ."));
            return;
        }
        final String driverIdentifier = currentUser.getUid();
        final DocumentReference requestReference = firestore.collection("requests").document(requestId.trim());
        firestore.runTransaction(new Transaction.Function<DriverAcceptanceSnapshot>() {
            /** Đọc nhất quán yêu cầu và chuyến; không gọi HTTP trong transaction. */
            @Override
            public DriverAcceptanceSnapshot apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                requireSession(driverIdentifier);
                DocumentSnapshot requestDocument = transaction.get(requestReference);
                String tripIdentifier = requestDocument.getString("tripId");
                if (!requestDocument.exists() || tripIdentifier == null
                        || tripIdentifier.trim().isEmpty() || tripIdentifier.contains("/")) {
                    throw new IllegalArgumentException("Không tìm thấy chuyến của yêu cầu.");
                }
                DocumentSnapshot tripDocument = transaction.get(
                        firestore.collection("trips").document(tripIdentifier));
                return new DriverAcceptanceSnapshot(requestDocument, tripDocument, driverIdentifier);
            }
        }).addOnSuccessListener(new OnSuccessListener<DriverAcceptanceSnapshot>() {
            /** Chỉ đo tuyến sau khi đọc server, không dùng dữ liệu hiển thị cũ của Activity. */
            @Override
            public void onSuccess(final DriverAcceptanceSnapshot prepared) {
                if (!sameSession(driverIdentifier)) {
                    callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi."));
                    return;
                }
                if (prepared.alreadyAccepted) {
                    callback.onSuccess(null);
                    return;
                }
                routeChecker.checkDetour(prepared.trip.getOrigin(), prepared.request.getPickup(),
                        prepared.dropoff, prepared.trip.getDestination(),
                        prepared.trip.getMaxDetourMeters(), new RepositoryCallback<DetourCheckResult>() {
                            /** Kết quả mới chỉ có giá trị với đúng snapshot đã đọc trước khi đo. */
                            @Override
                            public void onSuccess(DetourCheckResult result) {
                                if (!sameSession(driverIdentifier)) {
                                    callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi."));
                                    return;
                                }
                                if (result == null || !result.isWithinLimit()) {
                                    callback.onError(new IllegalStateException("Tuyến đón/trả vượt giới hạn"
                                            + " đi thêm của chuyến. Chưa thể chấp nhận khách này."));
                                    return;
                                }
                                respondToRequest(requestId, "ACCEPTED", prepared,
                                        SystemClock.elapsedRealtime(), callback);
                            }

                            /** Lỗi mạng hoặc API không được bỏ qua kiểm tra hay thay bằng 0 km. */
                            @Override
                            public void onError(Exception error) {
                                callback.onError(new IllegalStateException("Chưa kiểm tra được tuyến"
                                        + " đón/trả. Kiểm tra mạng và thử chấp nhận lại.", error));
                            }
                        });
            }
        }).addOnFailureListener(new OnFailureListener() {
            /** Chưa xác nhận được dữ liệu đầu vào thì không tạo quyết định hoặc thông báo. */
            @Override
            public void onFailure(@NonNull Exception error) {
                resolveAcceptanceFailure(requestReference, driverIdentifier, error, callback);
            }
        });
    }

    /** Từ chối yêu cầu; không thay đổi trạng thái chuyến. */
    public void rejectRequest(
            String requestId,
            RepositoryCallback<Void> callback
    ) {
        respondToRequest(requestId, "REJECTED", null, 0L, callback);
    }

    /**
     * Xác minh tài khoản, yêu cầu và chuyến trước khi ghi. Mọi trạng thái liên quan
     * đến một lần phản hồi được commit nguyên tử, không queue thao tác khi offline.
     */
    private void respondToRequest(
            String requestId,
            final String targetStatus,
            final DriverAcceptanceSnapshot prepared,
            final long routeCheckedAt,
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
                requireSession(currentDriverId);
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
                if (targetStatus.equals(currentStatus) && !"ACCEPTED".equals(targetStatus)) {
                    return null;
                }

                if (!"PENDING".equals(currentStatus)
                        && !("ACCEPTED".equals(targetStatus) && "ACCEPTED".equals(currentStatus))) {
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

                if ("ACCEPTED".equals(targetStatus)) {
                    if ("ACCEPTED".equals(currentStatus)
                            && safeRequestId.equals(acceptedRequestId)
                            && ("FULL".equals(tripStatus) || "COMPLETED".equals(tripStatus))) {
                        return null;
                    }
                    if (prepared == null || !prepared.matches(requestSnapshot, tripSnapshot)
                            || SystemClock.elapsedRealtime() - routeCheckedAt > ROUTE_VALIDITY_MILLISECONDS) {
                        throw new IllegalStateException("Dữ liệu chuyến hoặc yêu cầu đã đổi, hoặc"
                                + " kết quả kiểm tra đã hết hạn. Hãy tải lại và chấp nhận lại.");
                    }
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

                String notificationType = "REQUEST_REJECTED";
                if ("ACCEPTED".equals(targetStatus)) {
                    notificationType = "REQUEST_ACCEPTED";
                }
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
                if ("ACCEPTED".equals(targetStatus)) {
                    resolveAcceptanceFailure(requestReference, currentDriverId, error, callback);
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    /** Chỉ giải thích tranh chỗ sau khi đọc lại cả yêu cầu và chuyến từ máy chủ. */
    private void resolveAcceptanceFailure(
            final DocumentReference requestReference,
            final String driverIdentifier,
            final Exception originalError,
            final RepositoryCallback<Void> callback
    ) {
        if (!sameSession(driverIdentifier)) {
            callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi."));
            return;
        }
        firestore.runTransaction(new Transaction.Function<AcceptanceResolution>() {
            @Override
            public AcceptanceResolution apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                requireSession(driverIdentifier);
                DocumentSnapshot request = transaction.get(requestReference);
                String tripIdentifier = request.getString("tripId");
                if (!request.exists() || !driverIdentifier.equals(request.getString("driverId"))
                        || tripIdentifier == null || tripIdentifier.trim().isEmpty()
                        || tripIdentifier.contains("/")) {
                    return new AcceptanceResolution(false, null);
                }
                DocumentSnapshot trip = transaction.get(
                        firestore.collection("trips").document(tripIdentifier));
                if (!trip.exists() || !driverIdentifier.equals(trip.getString("driverId"))) {
                    return new AcceptanceResolution(false, null);
                }
                String requestStatus = request.getString("status");
                String tripStatus = trip.getString("status");
                String acceptedIdentifier = trip.getString("acceptedRequestId");
                Long remainingSeats = trip.getLong("availableSeats");
                boolean acceptedThisRequest = requestReference.getId().equals(acceptedIdentifier);
                boolean tripHasPassenger = "FULL".equals(tripStatus)
                        && remainingSeats != null && remainingSeats.longValue() == 0L
                        && acceptedIdentifier != null && !acceptedIdentifier.trim().isEmpty();
                // Đối soát kết quả đã ghi, không ghi lại hay tạo thông báo lần thứ hai.
                if ("ACCEPTED".equals(requestStatus) && acceptedThisRequest
                        && (tripHasPassenger || "COMPLETED".equals(tripStatus))) {
                    return new AcceptanceResolution(true, null);
                }
                if ("CANCELLED".equals(requestStatus)) {
                    return new AcceptanceResolution(false, "Yêu cầu này đã bị hủy. Không thể nhận khách.");
                }
                if ("REJECTED".equals(requestStatus)) {
                    return new AcceptanceResolution(false, "Yêu cầu này đã bị từ chối. Hãy tải lại danh sách.");
                }
                if ("CANCELLED".equals(tripStatus) || "COMPLETED".equals(tripStatus)) {
                    return new AcceptanceResolution(false, "Chuyến đã hủy hoặc hoàn thành. Không thể nhận thêm khách.");
                }
                if ("PENDING".equals(requestStatus) && tripHasPassenger && !acceptedThisRequest) {
                    return new AcceptanceResolution(false,
                            "Chuyến đã có khách khác và không còn chỗ. Yêu cầu này chưa được chấp nhận.");
                }
                Timestamp departure = trip.getTimestamp("departureAt");
                if (departure != null && departure.toDate().getTime() <= System.currentTimeMillis()) {
                    return new AcceptanceResolution(false, "Đã đến giờ khởi hành. Không thể nhận thêm khách.");
                }
                return new AcceptanceResolution(false, null);
            }
        }).addOnSuccessListener(new OnSuccessListener<AcceptanceResolution>() {
            @Override
            public void onSuccess(AcceptanceResolution resolution) {
                if (!sameSession(driverIdentifier)) {
                    callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi."));
                    return;
                }
                if (resolution.alreadyAccepted) {
                    callback.onSuccess(null);
                } else if (resolution.message != null) {
                    callback.onError(new IllegalStateException(resolution.message, originalError));
                } else {
                    callback.onError(readableAcceptanceError(originalError));
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception verificationError) {
                // Không xác minh được thì không suy đoán hết chỗ hoặc báo nhận thành công.
                callback.onError(readableAcceptanceError(originalError));
            }
        });
    }

    /** Giữ lỗi quyền và mạng riêng, không thay mọi PERMISSION_DENIED bằng hết chỗ. */
    private Exception readableAcceptanceError(Exception error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 10; depth = depth + 1) {
            if (cause instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) cause).getCode();
                if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    return new IllegalStateException("Máy chủ chưa cho phép nhận yêu cầu này."
                            + " Chưa xác minh được nguyên nhân; hãy tải lại hoặc liên hệ nhóm quản lý.", error);
                }
                if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                        || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                    return new IllegalStateException("Chưa xác nhận được kết quả nhận khách."
                            + " Kiểm tra mạng và tải lại yêu cầu trước khi thử lại.", error);
                }
            }
            cause = cause.getCause();
        }
        return error;
    }

    /** Kết quả đối soát chỉ dùng phản hồi giao diện, không thay đổi dữ liệu nghiệp vụ. */
    private static final class AcceptanceResolution {
        private final boolean alreadyAccepted;
        private final String message;

        private AcceptanceResolution(boolean alreadyAccepted, String message) {
            this.alreadyAccepted = alreadyAccepted;
            this.message = message;
        }
    }

    /** Kiểm tra cả UID và trạng thái đóng để callback muộn không ghi cho tài khoản khác. */
    private boolean sameSession(String driverIdentifier) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        return !closed && currentUser != null && driverIdentifier.equals(currentUser.getUid());
    }

    /** Firestore có thể chạy lại transaction; mỗi lượt đều phải kiểm tra lại phiên. */
    private void requireSession(String driverIdentifier) {
        if (!sameSession(driverIdentifier)) {
            throw new IllegalStateException("Phiên đăng nhập đã đổi hoặc màn đã đóng.");
        }
    }

    /** Giải phóng worker khi ViewModel không còn được sử dụng. */
    public void close() {
        closed = true;
        routeChecker.close();
    }
}
