package com.example.grabshare.repository.search;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.example.grabshare.api.VietnamDetourChecker;
import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.DetourChecker;
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

    private final VietnamDetourChecker routingWorker;

    private final DetourChecker detourChecker;

    private volatile boolean closed;

    /** Snapshot private chỉ dùng trong lần gửi, không thêm field vào request Firestore. */
    private static final class VerifiedRoute {

        final Trip trip;

        final RequestJourney journey;

        /** Giữ Trip server và P/C riêng cho đúng một lần kiểm tra, không chia sẻ với form. */
        VerifiedRoute(Trip trip, RequestJourney journey) {
            this.trip = trip;
            this.journey = journey;
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

        private final RequestJourney journey;

        /** Giữ tuyến đã lưu trên server, không lấy Bundle làm nguồn của request hiện có. */
        private Result(String requestId, String status, boolean existing, RequestJourney journey) {
            this.requestId = requestId;
            this.status = status;
            this.existing = existing;
            this.journey = journey;
        }

        /** Trả bản sao P của request đã được server xác nhận. */
        public Place getPickup() {
            return journey.getPickup();
        }

        /** Trả bản sao C hoặc D dự phòng hợp lệ cho request legacy. */
        public Place getDropoff() {
            return journey.getDropoff();
        }
    }

    /** Giữ applicationContext và dùng Firebase chung của ứng dụng. */
    public RequestCreationRepository(Context context) {
        this.context = context.getApplicationContext();
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        routingWorker = new VietnamDetourChecker();
        detourChecker = routingWorker;
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
    private Result existingResult(
            DocumentSnapshot snapshot, Trip trip, String tripId, String userIdentifier) {
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
        request.setId(snapshot.getId());
        RequestJourney journey = RequestJourney.fromExistingRequest(request, trip);
        return new Result(snapshot.getId(), request.getStatus(), true, journey);
    }

    /** Đọc đúng chuyến từ server để kiểm tra liên kết request và áp dụng fallback legacy. */
    private void restoreExisting(
            final DocumentSnapshot requestSnapshot,
            final String tripId,
            final String userIdentifier,
            final RepositoryCallback<Result> callback) {
        firestore
                .collection("trips")
                .document(tripId)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<DocumentSnapshot>() {
                            /** Không dùng bản nháp tìm kiếm thay cho P/C của request đã tồn tại. */
                            @Override
                            public void onSuccess(DocumentSnapshot tripSnapshot) {
                                if (closed) {
                                    return;
                                }
                                if (!sameUser(userIdentifier)) {
                                    callback.onError(
                                            new IllegalStateException("Phiên đăng nhập đã đổi."));
                                    return;
                                }
                                final Result result;
                                try {
                                    Trip trip = tripSnapshot.toObject(Trip.class);
                                    if (trip != null) {
                                        trip.setId(tripSnapshot.getId());
                                    }
                                    result =
                                            existingResult(
                                                    requestSnapshot, trip, tripId, userIdentifier);
                                } catch (RuntimeException error) {
                                    callback.onError(error);
                                    return;
                                }
                                callback.onSuccess(result);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            /** Lỗi tải chuyến không được ngầm suy ra C=D hoặc chưa có request. */
                            @Override
                            public void onFailure(@NonNull Exception error) {
                                if (!closed) {
                                    callback.onError(error);
                                }
                            }
                        });
    }

    /** Đọc request đúng UID trên server để khôi phục sau process death hoặc lỗi mạng. */
    public void findExisting(
            final String tripId,
            final String userIdentifier,
            final RepositoryCallback<Result> callback) {
        if (closed
                || !SearchTripPolicy.validId(tripId)
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
                                if (closed) {
                                    return;
                                }
                                if (!sameUser(userIdentifier)) {
                                    callback.onError(
                                            new IllegalStateException("Phiên đăng nhập đã đổi."));
                                    return;
                                }
                                if (snapshot.exists()) {
                                    restoreExisting(snapshot, tripId, userIdentifier, callback);
                                    return;
                                }
                                callback.onSuccess(null);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {

                            /** Không coi lỗi đọc là chưa có request. */
                            @Override
                            public void onFailure(@NonNull Exception error) {
                                if (!closed) {
                                    callback.onError(error);
                                }
                            }
                        });
    }

    /** Bắt buộc P/C cho request mới, đọc lại tuyến và gọi API trước transaction. */
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
        final RequestJourney journey;
        try {
            journey = RequestJourney.forNewRequest(pickup, dropoff);
        } catch (IllegalArgumentException invalid) {
            callback.onError(invalid);
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
                        verifyLatestRoute(tripId, userIdentifier, journey, message, callback);
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
            final RequestJourney journey,
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
                                final VerifiedRoute verifiedRoute =
                                        new VerifiedRoute(trip, journey);
                                detourChecker.checkDetour(
                                        trip.getOrigin(),
                                        journey.getPickup(),
                                        journey.getDropoff(),
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
            final String message,
            final VerifiedRoute verifiedRoute,
            final RepositoryCallback<Result> callback) {
        if (!SearchTripPolicy.validId(tripId)
                || !SearchTripPolicy.validId(userIdentifier)
                || !sameUser(userIdentifier)) {
            callback.onError(
                    new IllegalStateException("Thiếu mã chuyến hoặc phiên đăng nhập đã đổi."));
            return;
        }
        if (!online()) {
            callback.onError(new IllegalStateException("Cần kết nối Internet để gửi yêu cầu."));
            return;
        }
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
                                if (closed || !sameUser(userIdentifier) || !online()) {
                                    throw failure(
                                            "Phiên đã đổi hoặc mất mạng. Yêu cầu chưa được xác"
                                                    + " nhận.");
                                }
                                DocumentSnapshot tripSnapshot = transaction.get(tripRef);
                                DocumentSnapshot requestSnapshot = transaction.get(requestRef);
                                Trip trip = tripSnapshot.toObject(Trip.class);
                                if (trip != null) {
                                    trip.setId(tripSnapshot.getId());
                                }
                                if (requestSnapshot.exists()) {
                                    try {
                                        return existingResult(
                                                requestSnapshot, trip, tripId, userIdentifier);
                                    } catch (RuntimeException error) {
                                        throw failure(
                                                "Yêu cầu đã tồn tại nhưng dữ liệu không hợp lệ.");
                                    }
                                }
                                if (!tripSnapshot.exists()) {
                                    throw failure("Chuyến không còn tồn tại.");
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
                                request.put("pickup", verifiedRoute.journey.getPickupData());
                                request.put("dropoff", verifiedRoute.journey.getDropoffData());
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
                                return new Result(
                                        requestId, "PENDING", false, verifiedRoute.journey);
                            }
                        })
                .addOnSuccessListener(
                        new OnSuccessListener<Result>() {

                            /** Đây là xác nhận server, không phải bản ghi pending trong cache. */
                            @Override
                            public void onSuccess(Result result) {
                                if (closed) {
                                    return;
                                }
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

                            /** Đối soát sau lỗi commit; lượt khác có thể đã tạo đúng ID này. */
                            @Override
                            public void onFailure(@NonNull final Exception error) {
                                if (closed) {
                                    return;
                                }
                                if (!sameUser(userIdentifier)) {
                                    callback.onError(error);
                                    return;
                                }
                                findExisting(
                                        tripId,
                                        userIdentifier,
                                        new RepositoryCallback<Result>() {
                                            /**
                                             * Chỉ công nhận dữ liệu server; không tự thử ghi lại.
                                             */
                                            @Override
                                            public void onSuccess(Result existing) {
                                                if (existing != null) {
                                                    callback.onSuccess(existing);
                                                } else {
                                                    callback.onError(error);
                                                }
                                            }

                                            /**
                                             * Chưa đối soát được thì giữ trạng thái chưa xác nhận.
                                             */
                                            @Override
                                            public void onError(Exception reconciliationError) {
                                                callback.onError(reconciliationError);
                                            }
                                        });
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
        routingWorker.close();
    }
}
