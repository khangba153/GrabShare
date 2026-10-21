package com.example.grabshare.repository.search;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.example.grabshare.api.VietnamDetourChecker;
import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Tạo một yêu cầu và thông báo tương ứng, không thay đổi chỗ của chuyến. */
public class RequestCreationRepository {

    private final Context context;

    private final FirebaseFirestore firestore;

    private final FirebaseAuth auth;

    private final VietnamDetourChecker detourChecker = new VietnamDetourChecker();

    private boolean closed;

    /** Snapshot private chỉ dùng trong lần gửi, không thêm field vào request Firestore. */
    private static final class VerifiedRoute {

        final Trip trip;

        /** Trip được đọc riêng từ server, không chia sẻ model với Activity đang sửa form. */
        VerifiedRoute(Trip trip) {
            this.trip = trip;
        }

        /** Đổi tuyến/giới hạn/giờ/giá phải tính lại và xác nhận lại, không dùng kết quả cũ. */
        boolean matches(Trip currentTrip) {
            return currentTrip != null
                    && Objects.equals(trip.getDriverId(), currentTrip.getDriverId())
                    && samePlace(trip.getOrigin(), currentTrip.getOrigin())
                    && samePlace(trip.getDestination(), currentTrip.getDestination())
                    && Objects.equals(
                            trip.getBaseDistanceMeters(), currentTrip.getBaseDistanceMeters())
                    && Objects.equals(trip.getMaxDetourMeters(), currentTrip.getMaxDetourMeters())
                    && Objects.equals(trip.getDepartureAt(), currentTrip.getDepartureAt())
                    && Objects.equals(trip.getCostVnd(), currentTrip.getCostVnd());
        }
    }

    /** So toàn bộ thông tin điểm, không coi hai nhãn giống nhau là cùng tọa độ. */
    private static boolean samePlace(Place firstPlace, Place secondPlace) {
        return firstPlace != null
                && secondPlace != null
                && Objects.equals(firstPlace.getName(), secondPlace.getName())
                && Objects.equals(firstPlace.getLatitude(), secondPlace.getLatitude())
                && Objects.equals(firstPlace.getLongitude(), secondPlace.getLongitude());
    }

    /** Kết quả được dùng để mở trạng thái hiện có sau xác nhận từ máy chủ. */
    public static class Result {

        public final String requestId;

        public final String status;

        public final boolean existing;

        /** Lưu ID và trạng thái, không truyền model qua Intent. */
        public Result(String requestId, String status, boolean existing) {
            this.requestId = requestId;
            this.status = status;
            this.existing = existing;
        }
    }

    /** Giữ applicationContext và dùng Firebase chung của ứng dụng. */
    public RequestCreationRepository(Context context) {
        this.context = context.getApplicationContext();
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /** Chặn gửi khi chưa có kết nối Internet được hệ thống xác nhận. */
    private boolean online() {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Đảm bảo callback và mỗi lượt transaction vẫn thuộc phiên đã khởi tạo. */
    private boolean sameUser(String userIdentifier) {
        FirebaseUser current = auth.getCurrentUser();
        return current != null && userIdentifier.equals(current.getUid());
    }

    /** Chuyển dữ liệu request đã tồn tại thành kết quả, không cho gửi lại. */
    private Result existingResult(DocumentSnapshot snapshot, String tripId, String userIdentifier) {
        TripRequest request = snapshot.toObject(TripRequest.class);
        if (request == null
                || !tripId.equals(request.getTripId())
                || !userIdentifier.equals(request.getPassengerId())
                || !("PENDING".equals(request.getStatus())
                        || "ACCEPTED".equals(request.getStatus())
                        || "REJECTED".equals(request.getStatus())
                        || "CANCELLED".equals(request.getStatus()))) {
            throw new IllegalStateException("Dữ liệu yêu cầu đã có không hợp lệ.");
        }
        return new Result(snapshot.getId(), request.getStatus(), true);
    }

    /** Đọc request đúng UID trên server để khôi phục sau process death hoặc lỗi mạng. */
    public void findExisting(
            final String tripId,
            final String userIdentifier,
            final RepositoryCallback<Result> callback) {
        if (!SearchTripPolicy.validId(tripId)
                || !SearchTripPolicy.validId(userIdentifier)
                || !sameUser(userIdentifier)) {
            callback.onError(
                    new IllegalStateException("Thiếu mã chuyến hoặc phiên đăng nhập đã đổi."));
            return;
        }
        firestore
                .collection("requests")
                .document(tripId + "_" + userIdentifier)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<DocumentSnapshot>() {

                            /**
                             * Chỉ trả trạng thái của phiên hiện tại; null nghĩa là server xác nhận
                             * chưa có.
                             */
                            @Override
                            public void onSuccess(DocumentSnapshot snapshot) {
                                if (!sameUser(userIdentifier)) {
                                    callback.onError(
                                            new IllegalStateException("Phiên đăng nhập đã đổi."));
                                    return;
                                }
                                final Result result;
                                try {
                                    if (snapshot.exists()) {
                                        result = existingResult(snapshot, tripId, userIdentifier);
                                    } else {
                                        result = null;
                                    }
                                } catch (RuntimeException error) {
                                    callback.onError(error);
                                    return;
                                }
                                callback.onSuccess(result);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {

                            /** Không coi lỗi đọc là chưa có request. */
                            @Override
                            public void onFailure(@NonNull Exception error) {
                                callback.onError(error);
                            }
                        });
    }

    /** Caller chỉ có điểm đón vẫn phải qua checker ba điểm, không bỏ kiểm tra trước gửi. */
    public void create(
            final String tripId,
            final String userIdentifier,
            Place pickup,
            String inputMessage,
            final RepositoryCallback<Result> callback) {
        create(tripId, userIdentifier, pickup, null, inputMessage, callback);
    }

    /**
     * Đọc lại tuyến và gọi API trước transaction; điểm xuống hiện chỉ dùng kiểm tra trong gói
     * Thành.
     */
    public void create(
            final String tripId,
            final String userIdentifier,
            Place pickup,
            Place dropoff,
            String inputMessage,
            final RepositoryCallback<Result> callback) {
        if (closed
                || !SearchTripPolicy.validId(tripId)
                || !SearchTripPolicy.validId(userIdentifier)
                || !sameUser(userIdentifier)
                || !online()) {
            callback.onError(
                    new IllegalStateException(
                            "Cần đúng tài khoản và kết nối Internet để gửi yêu cầu."));
            return;
        }
        if (!SearchTripPolicy.validPlace(pickup)
                || (dropoff != null && !SearchTripPolicy.validPlace(dropoff))) {
            callback.onError(new IllegalArgumentException("Điểm đón/trả không hợp lệ."));
            return;
        }
        final String message;
        if (inputMessage == null) {
            message = "";
        } else {
            message = inputMessage.trim();
        }
        if (message.length() > 500) {
            callback.onError(new IllegalArgumentException("Lời nhắn tối đa 500 ký tự."));
            return;
        }
        final Place requestedPickup =
                new Place(pickup.getName(), pickup.getLatitude(), pickup.getLongitude());
        final Place requestedDropoff;
        if (dropoff == null) {
            requestedDropoff = null;
        } else {
            requestedDropoff =
                    new Place(dropoff.getName(), dropoff.getLatitude(), dropoff.getLongitude());
        }
        findExisting(
                tripId,
                userIdentifier,
                new RepositoryCallback<Result>() {

                    /**
                     * Request có sẵn luôn mở trạng thái, không gọi lại Routing hoặc tạo thêm event.
                     */
                    @Override
                    public void onSuccess(Result existing) {
                        if (closed) {
                            return;
                        }
                        if (existing != null) {
                            callback.onSuccess(existing);
                            return;
                        }
                        verifyLatestRoute(
                                tripId,
                                userIdentifier,
                                requestedPickup,
                                requestedDropoff,
                                message,
                                callback);
                    }

                    /** Không coi lỗi đối soát là chưa có request rồi tiếp tục ghi. */
                    @Override
                    public void onError(Exception error) {
                        if (!closed) {
                            callback.onError(error);
                        }
                    }
                });
    }

    /** Chỉ dùng snapshot server mới; Results phù hợp không cấp quyền bỏ qua bước này. */
    private void verifyLatestRoute(
            final String tripId,
            final String userIdentifier,
            final Place pickup,
            final Place dropoff,
            final String message,
            final RepositoryCallback<Result> callback) {
        firestore
                .collection("trips")
                .document(tripId)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<DocumentSnapshot>() {

                            /**
                             * Thu snapshot A/D/giới hạn trước khi chạy Routing, không ghi ở
                             * callback này.
                             */
                            @Override
                            public void onSuccess(DocumentSnapshot document) {
                                if (closed) {
                                    return;
                                }
                                if (!sameUser(userIdentifier) || !online()) {
                                    callback.onError(
                                            new IllegalStateException(
                                                    "Phiên đã đổi hoặc mất mạng."));
                                    return;
                                }
                                final Trip trip;
                                try {
                                    trip = document.toObject(Trip.class);
                                    if (trip != null) {
                                        trip.setId(document.getId());
                                    }
                                    String reason =
                                            SearchTripPolicy.blockedReason(trip, userIdentifier);
                                    if (!reason.isEmpty()) {
                                        throw new IllegalStateException(reason);
                                    }
                                    if (trip.getBaseDistanceMeters() == null
                                            || trip.getBaseDistanceMeters() <= 0
                                            || !DetourLimit.isValidMeters(
                                                    trip.getMaxDetourMeters())) {
                                        throw new IllegalStateException(
                                                "Chuyến thiếu cấu hình đường vòng. Chủ xe cần bổ"
                                                    + " sung trước khi nhận yêu cầu mới.");
                                    }
                                } catch (RuntimeException error) {
                                    callback.onError(error);
                                    return;
                                }
                                final VerifiedRoute verifiedRoute = new VerifiedRoute(trip);
                                detourChecker.checkEnRoute(
                                        trip.getOrigin(),
                                        pickup,
                                        dropoff,
                                        trip.getDestination(),
                                        trip.getMaxDetourMeters(),
                                        new RepositoryCallback<DetourCheckResult>() {

                                            /**
                                             * Chỉ chuyển sang transaction khi đường bộ mới đạt đúng
                                             * giới hạn.
                                             */
                                            @Override
                                            public void onSuccess(DetourCheckResult route) {
                                                if (closed) {
                                                    return;
                                                }
                                                if (!route.isWithinLimit()) {
                                                    callback.onError(
                                                            new IllegalStateException(
                                                                    "Tuyến đón/trả vượt giới hạn đi"
                                                                        + " vòng của chủ xe. Hãy"
                                                                        + " tìm chuyến khác."));
                                                    return;
                                                }
                                                createVerified(
                                                        tripId,
                                                        userIdentifier,
                                                        pickup,
                                                        message,
                                                        verifiedRoute,
                                                        callback);
                                            }

                                            /**
                                             * Lỗi API không tạo request, event hoặc kết luận phù
                                             * hợp giả.
                                             */
                                            @Override
                                            public void onError(Exception error) {
                                                if (!closed) {
                                                    callback.onError(error);
                                                }
                                            }
                                        });
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {

                            /** Giữ bản nháp khi chưa đọc được chuyến mới nhất. */
                            @Override
                            public void onFailure(@NonNull Exception error) {
                                if (!closed) {
                                    callback.onError(error);
                                }
                            }
                        });
    }

    /** Transaction chỉ đọc/so snapshot rồi ghi nguyên tử, không có HTTP trong callback. */
    private void createVerified(
            final String tripId,
            final String userIdentifier,
            Place pickup,
            String inputMessage,
            final VerifiedRoute verifiedRoute,
            final RepositoryCallback<Result> callback) {
        if (!SearchTripPolicy.validId(tripId)
                || !SearchTripPolicy.validId(userIdentifier)
                || !sameUser(userIdentifier)) {
            callback.onError(
                    new IllegalStateException("Thiếu mã chuyến hoặc phiên đăng nhập đã đổi."));
            return;
        }
        if (!SearchTripPolicy.validPlace(pickup)) {
            callback.onError(
                    new IllegalArgumentException(
                            "Điểm đón phải có tên tối đa 200 ký tự và tọa độ hợp lệ."));
            return;
        }
        final String message;
        if (inputMessage == null) {
            message = "";
        } else {
            message = inputMessage.trim();
        }
        if (message.length() > 500) {
            callback.onError(new IllegalArgumentException("Lời nhắn tối đa 500 ký tự."));
            return;
        }
        if (!online()) {
            callback.onError(new IllegalStateException("Cần kết nối Internet để gửi yêu cầu."));
            return;
        }
        // Sao chép form để người dùng sửa giao diện không làm đổi dữ liệu transaction đang chạy.
        final Map<String, Object> pickupData = new HashMap<String, Object>();
        pickupData.put("name", pickup.getName().trim());
        pickupData.put("latitude", pickup.getLatitude());
        pickupData.put("longitude", pickup.getLongitude());
        final String requestId = tripId + "_" + userIdentifier;
        final DocumentReference tripRef = firestore.collection("trips").document(tripId);
        final DocumentReference requestRef = firestore.collection("requests").document(requestId);
        final DocumentReference eventRef =
                firestore.collection("notifications").document(requestId + "_REQUEST_SENT");
        firestore
                .runTransaction(
                        new Transaction.Function<Result>() {

                            /**
                             * Hàm có thể được Firestore chạy lại; không thay UI hoặc tạo ID ngẫu
                             * nhiên ở đây.
                             */
                            @Override
                            public Result apply(@NonNull Transaction transaction)
                                    throws FirebaseFirestoreException {
                                if (!sameUser(userIdentifier) || !online()) {
                                    throw failure(
                                            "Phiên đã đổi hoặc mất mạng. Yêu cầu chưa được xác"
                                                + " nhận.");
                                }
                                DocumentSnapshot tripSnapshot = transaction.get(tripRef);
                                DocumentSnapshot requestSnapshot = transaction.get(requestRef);
                                if (requestSnapshot.exists()) {
                                    try {
                                        return existingResult(
                                                requestSnapshot, tripId, userIdentifier);
                                    } catch (RuntimeException error) {
                                        throw failure(
                                                "Yêu cầu đã tồn tại nhưng dữ liệu không hợp lệ.");
                                    }
                                }
                                if (!tripSnapshot.exists()) {
                                    throw failure("Chuyến không còn tồn tại.");
                                }
                                Trip trip = tripSnapshot.toObject(Trip.class);
                                if (trip != null) {
                                    trip.setId(tripSnapshot.getId());
                                }
                                String reason =
                                        SearchTripPolicy.blockedReason(trip, userIdentifier);
                                if (!reason.isEmpty()) {
                                    throw failure(reason);
                                }
                                if (!verifiedRoute.matches(trip)) {
                                    throw failure(
                                            "Chuyến đã đổi tuyến, giờ, giá hoặc giới hạn. Kiểm tra"
                                                + " và gửi lại để tính lại đường.");
                                }
                                Map<String, Object> request = new HashMap<String, Object>();
                                request.put("tripId", tripId);
                                request.put("passengerId", userIdentifier);
                                request.put("driverId", trip.getDriverId());
                                request.put("pickup", pickupData);
                                request.put("message", message);
                                request.put("status", "PENDING");
                                request.put("createdAt", FieldValue.serverTimestamp());
                                request.put("updatedAt", FieldValue.serverTimestamp());
                                Map<String, Object> event = new HashMap<String, Object>();
                                event.put("recipientId", trip.getDriverId());
                                event.put("actorId", userIdentifier);
                                event.put("type", "REQUEST_SENT");
                                event.put("tripId", tripId);
                                event.put("requestId", requestId);
                                event.put("isRead", false);
                                event.put("createdAt", FieldValue.serverTimestamp());
                                transaction.set(requestRef, request);
                                transaction.set(eventRef, event);
                                return new Result(requestId, "PENDING", false);
                            }
                        })
                .addOnSuccessListener(
                        new OnSuccessListener<Result>() {

                            /** Đây là xác nhận server, không phải bản ghi pending trong cache. */
                            @Override
                            public void onSuccess(Result result) {
                                if (sameUser(userIdentifier)) {
                                    callback.onSuccess(result);
                                } else {
                                    callback.onError(
                                            new IllegalStateException(
                                                    "Phiên đăng nhập đã đổi. Kiểm tra lại ở tài"
                                                        + " khoản cũ."));
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {

                            /** Giữ lỗi quyền/mạng/state để caller hiển thị và giữ form. */
                            @Override
                            public void onFailure(@NonNull Exception error) {
                                callback.onError(error);
                            }
                        });
    }

    /** Tạo lỗi nghiệp vụ dừng transaction trước khi có thao tác ghi. */
    private static FirebaseFirestoreException failure(String message) {
        return new FirebaseFirestoreException(
                message, FirebaseFirestoreException.Code.FAILED_PRECONDITION);
    }

    /** ViewModel đóng checker khi Back khỏi Review, không giữ tác vụ định tuyến mồ côi. */
    public void close() {
        closed = true;
        detourChecker.close();
    }
}
