package com.example.grabshare.repository.search;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.example.grabshare.api.VietnamDetourChecker;
import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.DetourChecker;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.contract.RequestAttemptContract;
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

/** Tạo một yêu cầu và thông báo tương ứng, không thay đổi chỗ của chuyến. */
public class RequestCreationRepository {

    // Leader chỉ bật cùng mốc Publish Rules tiền đã nghiệm thu; không tự bật từ UI.
    public static final boolean CONTRIBUTION_SUBMISSION_ENABLED = true;

    // Chỉ bật sau khi leader nghiệm thu client hai phía và Rules guard cùng phiên bản.
    public static final boolean REQUEST_ATTEMPTS_ENABLED = false;

    // Dùng chung thông báo khi hệ thống chưa mở ghi theo hợp đồng tiền mới.
    public static final String SUBMISSION_UNAVAILABLE_MESSAGE =
            "Hệ thống chưa mở gửi yêu cầu theo khoản đóng góp mới. Vui lòng thử lại sau.";

    private final Context context;

    private final FirebaseFirestore firestore;

    private final FirebaseAuth auth;

    private final VietnamDetourChecker routingWorker;

    private final DetourChecker detourChecker;

    private volatile boolean closed;

    /** Kết quả được dùng để mở trạng thái hiện có sau xác nhận từ máy chủ. */
    public static class Result {

        public final String requestId;

        public final String status;

        public final boolean existing;

        private final RequestJourney journey;

        public final ContributionSnapshot contribution;

        public final Long attemptNumber;

        /** Số lần null chỉ dành cho document legacy không có field attemptNumber. */
        private Result(
                String requestId,
                String status,
                boolean existing,
                RequestJourney journey,
                ContributionSnapshot contribution,
                Long attemptNumber) {
            this.requestId = requestId;
            this.status = status;
            this.existing = existing;
            this.journey = journey;
            this.contribution = contribution;
            this.attemptNumber = attemptNumber;
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

    /** Đọc tuyến/tiền/số lần từ server; không suy trạng thái hoặc tính lại tiền lịch sử. */
    private Result existingResult(
            DocumentSnapshot snapshot, Trip trip, String tripId, String userIdentifier) {
        TripRequest request = snapshot.toObject(TripRequest.class);
        if (request == null
                || !tripId.equals(request.getTripId())
                || !userIdentifier.equals(request.getPassengerId())
                || !("PENDING".equals(request.getStatus())
                        || "ACCEPTED".equals(request.getStatus())
                        || "REJECTED".equals(request.getStatus())
                        || "CANCELLED".equals(request.getStatus())
                        || "COMPLETED".equals(request.getStatus()))
                || trip == null
                || !tripId.equals(trip.getId())
                || !java.util.Objects.equals(trip.getDriverId(), request.getDriverId())) {
            throw new IllegalStateException("Dữ liệu yêu cầu đã có không hợp lệ.");
        }
        if (request.hasAttemptNumberField()) {
            Object number = snapshot.get("attemptNumber");
            if (!(number instanceof Long)
                    || request.getAttemptNumber() == null
                    || request.getAttemptNumber() < 1
                    || !snapshot.getId()
                            .matches(
                                    "[0-9a-f]{32}_"
                                            + java.util.regex.Pattern.quote(userIdentifier))) {
                throw new IllegalStateException("Số lần yêu cầu đã lưu không hợp lệ.");
            }
        } else {
            if (!RequestAttemptContract.legacyRequestId(tripId, userIdentifier)
                    .equals(snapshot.getId())) {
                throw new IllegalStateException("Yêu cầu mới thiếu số lần đã lưu.");
            }
        }
        request.setId(snapshot.getId());
        RequestJourney journey = RequestJourney.fromExistingRequest(request, trip);
        return new Result(
                snapshot.getId(),
                request.getStatus(),
                true,
                journey,
                ContributionCalculator.readConfirmed(request),
                request.getAttemptNumber());
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

    /** Toàn bộ dữ liệu server cần đối chiếu; không chứa tác vụ ghi hoặc API định tuyến. */
    private static final class AttemptState {
        final Trip trip;
        final Result latest;
        final Result intended;

        /** Giữ riêng lần gần nhất và ID đang đối soát để callback muộn không gửi lần mới. */
        AttemptState(Trip trip, Result latest, Result intended) {
            this.trip = trip;
            this.latest = latest;
            this.intended = intended;
        }

        /** Ưu tiên document của chính ý định đã gửi, kể cả nó đã bị hủy sau commit. */
        Result resolved() {
            if (intended != null) {
                return intended;
            }
            return latest;
        }
    }

    /** Đọc theo path đã chốt; không query hoặc list collection guard. */
    private DocumentReference guardReference(String tripId, String userIdentifier) {
        return firestore.document(RequestAttemptContract.guardPath(tripId, userIdentifier));
    }

    /** Đọc đủ Trip/guard/lần trước/ID ý định trước mọi thao tác ghi trong transaction. */
    private AttemptState readAttemptState(
            Transaction transaction,
            String tripId,
            String userIdentifier,
            RequestSubmissionIntent intent)
            throws FirebaseFirestoreException {
        DocumentSnapshot tripSnapshot =
                transaction.get(firestore.collection("trips").document(tripId));
        DocumentSnapshot guardSnapshot = transaction.get(guardReference(tripId, userIdentifier));
        Trip trip = tripSnapshot.toObject(Trip.class);
        if (trip == null || !tripSnapshot.exists()) {
            throw failure("Chuyến không còn tồn tại.");
        }
        trip.setId(tripSnapshot.getId());
        String latestIdentifier = RequestAttemptContract.legacyRequestId(tripId, userIdentifier);
        Long guardNumber = null;
        if (guardSnapshot.exists()) {
            Map<String, Object> fields = guardSnapshot.getData();
            if (fields == null
                    || fields.size() != 3
                    || !(fields.get("requestId") instanceof String)
                    || !(fields.get("attemptNumber") instanceof Long)
                    || !(fields.get("updatedAt") instanceof com.google.firebase.Timestamp)) {
                throw failure("Con trỏ yêu cầu không hợp lệ. Vui lòng báo nhóm hỗ trợ.");
            }
            latestIdentifier = (String) fields.get("requestId");
            guardNumber = (Long) fields.get("attemptNumber");
            if (guardNumber < 1
                    || !latestIdentifier.matches(
                            "[0-9a-f]{32}_" + java.util.regex.Pattern.quote(userIdentifier))) {
                throw failure("Con trỏ yêu cầu sai ID hoặc số lần.");
            }
        }
        DocumentSnapshot latestSnapshot =
                transaction.get(firestore.collection("requests").document(latestIdentifier));
        Result latest = null;
        if (latestSnapshot.exists()) {
            latest = existingResult(latestSnapshot, trip, tripId, userIdentifier);
        }
        if (guardSnapshot.exists()) {
            if (latest == null || !guardNumber.equals(latest.attemptNumber)) {
                throw failure("Con trỏ không khớp yêu cầu đã lưu.");
            }
        } else {
            if (latest != null && latest.attemptNumber != null) {
                throw failure("Yêu cầu legacy có số lần không hợp lệ.");
            }
        }
        Result intended = null;
        if (intent != null) {
            DocumentSnapshot intendedSnapshot = latestSnapshot;
            if (!intent.requestIdentifier.equals(latestIdentifier)) {
                intendedSnapshot =
                        transaction.get(
                                firestore
                                        .collection("requests")
                                        .document(intent.requestIdentifier));
            }
            if (intendedSnapshot.exists()) {
                intended = existingResult(intendedSnapshot, trip, tripId, userIdentifier);
                if (guardNumber == null
                        || intended.attemptNumber == null
                        || intended.attemptNumber > guardNumber
                        || (!intent.requestIdentifier.equals(latestIdentifier)
                                && (!"CANCELLED".equals(intended.status)
                                        || intended.attemptNumber.equals(guardNumber)))) {
                    throw failure("Lần yêu cầu không khớp con trỏ lịch sử.");
                }
            }
        }
        return new AttemptState(trip, latest, intended);
    }

    /** Đối soát trên server bằng transaction chỉ đọc, không tự submit từ restore. */
    private void readLatestAttempt(
            final String tripId,
            final String userIdentifier,
            final RequestSubmissionIntent intent,
            final RepositoryCallback<AttemptState> callback) {
        firestore
                .runTransaction(
                        new Transaction.Function<AttemptState>() {
                            /**
                             * Firestore có thể đọc lại khi guard đổi; không phát sinh ID hoặc HTTP.
                             */
                            @Override
                            public AttemptState apply(@NonNull Transaction transaction)
                                    throws FirebaseFirestoreException {
                                if (closed || !sameUser(userIdentifier) || !online()) {
                                    throw failure(
                                            "Phiên đã đổi hoặc mất mạng khi kiểm tra yêu cầu.");
                                }
                                return readAttemptState(
                                        transaction, tripId, userIdentifier, intent);
                            }
                        })
                .addOnSuccessListener(
                        new OnSuccessListener<AttemptState>() {
                            /** Chỉ giao dữ liệu server cho phiên vẫn đang mở màn. */
                            @Override
                            public void onSuccess(AttemptState value) {
                                if (!closed) {
                                    if (sameUser(userIdentifier)) {
                                        callback.onSuccess(value);
                                    } else {
                                        callback.onError(
                                                new IllegalStateException(
                                                        "Phiên đăng nhập đã đổi."));
                                    }
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            /** Quyền/mạng/dữ liệu sai không được biến thành chưa có yêu cầu. */
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
            final RequestSubmissionIntent intent,
            final RepositoryCallback<Result> callback) {
        if (closed
                || !SearchTripPolicy.validId(tripId)
                || !SearchTripPolicy.validId(userIdentifier)
                || !sameUser(userIdentifier)) {
            callback.onError(
                    new IllegalStateException("Thiếu mã chuyến hoặc phiên đăng nhập đã đổi."));
            return;
        }
        if (intent != null
                && (!tripId.equals(intent.tripIdentifier)
                        || !userIdentifier.equals(intent.passengerIdentifier))) {
            callback.onError(
                    new IllegalArgumentException("Ý định gửi thuộc chuyến hoặc phiên khác."));
            return;
        }
        if (isRequestAttemptsEnabled()) {
            readLatestAttempt(
                    tripId,
                    userIdentifier,
                    intent,
                    new RepositoryCallback<AttemptState>() {
                        /** Trả đúng ID server, không ghép tripId_uid để mở màn trạng thái. */
                        @Override
                        public void onSuccess(AttemptState value) {
                            callback.onSuccess(value.resolved());
                        }

                        /** Giữ lỗi đọc guard, không fallback legacy khi server từ chối quyền. */
                        @Override
                        public void onError(Exception error) {
                            callback.onError(error);
                        }
                    });
            return;
        }
        firestore
                .collection("requests")
                .document(RequestAttemptContract.legacyRequestId(tripId, userIdentifier))
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
            final RequestQuote confirmedQuote,
            final RequestSubmissionIntent intent,
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
        if (confirmedQuote == null) {
            callback.onError(
                    new IllegalArgumentException(
                            "Vui lòng xác nhận khoản đóng góp trước khi gửi."));
            return;
        }
        if (intent == null
                || !tripId.equals(intent.tripIdentifier)
                || !userIdentifier.equals(intent.passengerIdentifier)) {
            callback.onError(
                    new IllegalArgumentException("Thiếu ý định gửi đúng chuyến và tài khoản."));
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
                intent,
                new RepositoryCallback<Result>() {

                    /**
                     * Request có sẵn luôn mở trạng thái, không gọi lại Routing hoặc tạo thêm event.
                     */
                    @Override
                    public void onSuccess(Result existing) {
                        if (closed) {
                            return;
                        }
                        if (existing != null
                                && (!isRequestAttemptsEnabled()
                                        || intent.requestIdentifier.equals(existing.requestId)
                                        || !"CANCELLED".equals(existing.status))) {
                            callback.onSuccess(existing);
                            return;
                        }
                        if (isRequestAttemptsEnabled() && !intent.follows(existing)) {
                            callback.onError(
                                    new IllegalStateException(
                                            "Lịch sử yêu cầu đã đổi. Hãy kiểm tra lại trước khi"
                                                    + " gửi."));
                            return;
                        }
                        if (!isContributionSubmissionEnabled()) {
                            callback.onError(
                                    new IllegalStateException(SUBMISSION_UNAVAILABLE_MESSAGE));
                            return;
                        }
                        verifyLatestRoute(
                                tripId,
                                userIdentifier,
                                journey,
                                message,
                                confirmedQuote,
                                intent,
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
            final RequestJourney journey,
            final String message,
            final RequestQuote confirmedQuote,
            final RequestSubmissionIntent intent,
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
                                    if (!confirmedQuote.matches(
                                            trip,
                                            userIdentifier,
                                            journey.getPickup(),
                                            journey.getDropoff())) {
                                        throw new RequestQuote.ChangedException();
                                    }
                                } catch (RuntimeException error) {
                                    callback.onError(error);
                                    return;
                                }
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
                                                if (!confirmedQuote.matchesRoute(route)) {
                                                    callback.onError(
                                                            new RequestQuote.ChangedException());
                                                    return;
                                                }
                                                createVerified(
                                                        tripId,
                                                        userIdentifier,
                                                        message,
                                                        journey,
                                                        confirmedQuote,
                                                        intent,
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
            final RequestJourney journey,
            final RequestQuote confirmedQuote,
            final RequestSubmissionIntent intent,
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
        final boolean attemptsEnabled = isRequestAttemptsEnabled();
        final String requestId;
        if (attemptsEnabled) {
            requestId = intent.requestIdentifier;
        } else {
            requestId = RequestAttemptContract.legacyRequestId(tripId, userIdentifier);
        }
        final DocumentReference tripRef = firestore.collection("trips").document(tripId);
        final DocumentReference requestRef = firestore.collection("requests").document(requestId);
        final DocumentReference eventRef =
                firestore
                        .collection("notifications")
                        .document(RequestAttemptContract.requestEventId(requestId, "REQUEST_SENT"));
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
                                Trip trip;
                                Long attemptNumber = null;
                                if (attemptsEnabled) {
                                    AttemptState attempt =
                                            readAttemptState(
                                                    transaction, tripId, userIdentifier, intent);
                                    if (attempt.intended != null) {
                                        return attempt.intended;
                                    }
                                    if (attempt.latest != null
                                            && !"CANCELLED".equals(attempt.latest.status)) {
                                        return attempt.latest;
                                    }
                                    if (!intent.follows(attempt.latest)) {
                                        throw failure(
                                                "Lịch sử yêu cầu đã đổi. Cần thao tác gửi mới sau"
                                                        + " khi kiểm tra lại.");
                                    }
                                    String previousStatus = null;
                                    Long previousNumber = null;
                                    if (attempt.latest != null) {
                                        previousStatus = attempt.latest.status;
                                        previousNumber = attempt.latest.attemptNumber;
                                    }
                                    attemptNumber =
                                            RequestAttemptContract.nextAttemptNumber(
                                                    previousNumber, previousStatus);
                                    trip = attempt.trip;
                                } else {
                                    DocumentSnapshot tripSnapshot = transaction.get(tripRef);
                                    DocumentSnapshot requestSnapshot = transaction.get(requestRef);
                                    trip = tripSnapshot.toObject(Trip.class);
                                    if (trip != null) {
                                        trip.setId(tripSnapshot.getId());
                                    }
                                    if (requestSnapshot.exists()) {
                                        return existingResult(
                                                requestSnapshot, trip, tripId, userIdentifier);
                                    }
                                    if (!tripSnapshot.exists()) {
                                        throw failure("Chuyến không còn tồn tại.");
                                    }
                                }
                                String reason =
                                        SearchTripPolicy.blockedReason(trip, userIdentifier);
                                if (!reason.isEmpty()) {
                                    throw failure(reason);
                                }
                                if (!confirmedQuote.matches(
                                        trip,
                                        userIdentifier,
                                        journey.getPickup(),
                                        journey.getDropoff())) {
                                    throw new RequestQuote.ChangedException();
                                }
                                Map<String, Object> request = new HashMap<String, Object>();
                                request.put("tripId", tripId);
                                request.put("passengerId", userIdentifier);
                                request.put("driverId", trip.getDriverId());
                                request.put("pickup", journey.getPickupData());
                                request.put("dropoff", journey.getDropoffData());
                                request.put(
                                        "contributionSnapshot",
                                        confirmedQuote.getContribution().toMap());
                                request.put("message", message);
                                request.put("status", "PENDING");
                                request.put("createdAt", FieldValue.serverTimestamp());
                                request.put("updatedAt", FieldValue.serverTimestamp());
                                if (attemptsEnabled) {
                                    request.put("attemptNumber", attemptNumber);
                                }
                                Map<String, Object> event = new HashMap<String, Object>();
                                event.put("recipientId", trip.getDriverId());
                                event.put("actorId", userIdentifier);
                                event.put("type", "REQUEST_SENT");
                                event.put("tripId", tripId);
                                event.put("requestId", requestId);
                                event.put("isRead", false);
                                event.put("createdAt", FieldValue.serverTimestamp());
                                transaction.set(requestRef, request);
                                if (attemptsEnabled) {
                                    transaction.set(
                                            guardReference(tripId, userIdentifier),
                                            RequestAttemptContract.guardFields(
                                                    requestId, attemptNumber));
                                }
                                transaction.set(eventRef, event);
                                return new Result(
                                        requestId,
                                        "PENDING",
                                        false,
                                        journey,
                                        confirmedQuote.getContribution(),
                                        attemptNumber);
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
                                        intent,
                                        new RepositoryCallback<Result>() {
                                            /**
                                             * Chỉ công nhận dữ liệu server; không tự thử ghi lại.
                                             */
                                            @Override
                                            public void onSuccess(Result existing) {
                                                if (existing != null
                                                        && (!attemptsEnabled
                                                                || intent.requestIdentifier.equals(
                                                                        existing.requestId)
                                                                || !intent.follows(existing))) {
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

    /** Cổng rollout dùng chung với UI; kiểm thử local có thể thay mà không đổi Rules thật. */
    protected boolean isContributionSubmissionEnabled() {
        return CONTRIBUTION_SUBMISSION_ENABLED;
    }

    /** Đọc/ghi guard chỉ ở stage attempt; stage tiền cũ không đòi quyền guard chưa Publish. */
    protected boolean isRequestAttemptsEnabled() {
        return REQUEST_ATTEMPTS_ENABLED;
    }

    /** ViewModel đóng checker khi Back khỏi Review, không giữ tác vụ định tuyến mồ côi. */
    public void close() {
        closed = true;
        routingWorker.close();
    }
}
