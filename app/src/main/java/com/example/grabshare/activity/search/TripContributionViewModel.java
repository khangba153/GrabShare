package com.example.grabshare.activity.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.api.VietnamDetourChecker;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.RequestJourney;
import com.example.grabshare.repository.search.RequestQuote;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Tính khoản dự kiến từ chuyến server và P/C hiện tại; không ghi Request hoặc xác nhận thay khách.
 */
public class TripContributionViewModel extends ViewModel {
    /** Quote chỉ dùng được khi cùng đầu vào và còn đúng phiên. */
    public static final class State {
        public final boolean busy;
        public final ContributionSnapshot contribution;
        public final Long detourMeters;
        public final String message;
        public final RequestQuote quote;

        /** Không giữ Activity hoặc Place có thể bị form sửa trong callback. */
        State(boolean busy, ContributionSnapshot contribution, Long detourMeters, String message) {
            this(busy, contribution, detourMeters, message, null);
        }

        /** Giữ token đầu vào bất biến cùng khoản đang hiển thị. */
        State(
                boolean busy,
                ContributionSnapshot contribution,
                Long detourMeters,
                String message,
                RequestQuote quote) {
            this.quote = quote;
            this.busy = busy;
            this.contribution = contribution;
            this.detourMeters = detourMeters;
            this.message = message;
        }
    }

    private final MutableLiveData<State> state =
            new MutableLiveData<State>(
                    new State(
                            false,
                            null,
                            null,
                            "Chọn đủ điểm đón và điểm xuống để tính khoản đóng góp."));
    private RequestQuote.Inputs inputs;
    private RequestQuote confirmedQuote;
    private VietnamDetourChecker checker;
    private int generation;
    private boolean closed;

    /** Cho Activity quan sát mà không sở hữu tác vụ Routing. */
    public LiveData<State> getState() {
        return state;
    }

    /** Chụp toàn bộ đầu vào liên quan; đổi tên/toạ độ/giờ/giá/giới hạn đều bỏ quote cũ. */
    public void update(Trip trip, String userIdentifier, Place pickup, Place dropoff) {
        if (closed) {
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null
                || !FirebaseAuth.getInstance().getCurrentUser().getUid().equals(userIdentifier)) {
            clear("Phiên đăng nhập đã đổi. Vui lòng mở lại chuyến bằng tài khoản hiện tại.");
            return;
        }
        String reason = SearchTripPolicy.blockedReason(trip, userIdentifier);
        if (!reason.isEmpty()) {
            clear(reason);
            return;
        }
        final RequestJourney journey;
        try {
            journey = RequestJourney.forNewRequest(pickup, dropoff);
        } catch (IllegalArgumentException invalid) {
            clear("Chọn đủ điểm đón và điểm xuống hợp lệ để tính khoản đóng góp.");
            return;
        }
        if (trip.getBaseDistanceMeters() == null
                || trip.getBaseDistanceMeters() <= 0
                || !DetourLimit.isValidMeters(trip.getMaxDetourMeters())) {
            clear("Chuyến chưa có đủ thông tin giới hạn đi thêm để tính khoản đóng góp.");
            return;
        }
        final RequestQuote.Inputs requestedInputs =
                new RequestQuote.Inputs(trip, userIdentifier, pickup, dropoff);
        if (requestedInputs.sameAs(inputs)) {
            return;
        }
        cancel();
        inputs = requestedInputs;
        final int requestedGeneration = generation;

        checker = createChecker();
        state.setValue(
                new State(true, null, null, "Đang tính quãng đường và khoản đóng góp của bạn…"));
        checker.checkDetour(
                trip.getOrigin(),
                journey.getPickup(),
                journey.getDropoff(),
                trip.getDestination(),
                trip.getMaxDetourMeters(),
                new RepositoryCallback<DetourCheckResult>() {
                    /** Chỉ công bố quote của đúng đầu vào và tài khoản đang mở màn. */
                    @Override
                    public void onSuccess(DetourCheckResult route) {
                        if (!current(requestedGeneration, requestedInputs, userIdentifier)) {
                            return;
                        }
                        try {
                            RequestQuote quote = requestedInputs.withRoute(route);
                            ContributionSnapshot contribution = quote.getContribution();
                            state.setValue(
                                    new State(
                                            false,
                                            contribution,
                                            route.getDetourMeters(),
                                            "Khoản dự kiến theo đoạn bạn đi; chưa phải khoản đã gửi"
                                                    + " cho chủ xe.",
                                            quote));
                        } catch (IllegalArgumentException invalid) {
                            String message =
                                    "Chưa tính được khoản đóng góp. Hãy tải lại thông tin.";
                            if (route != null && !route.isWithinLimit()) {
                                message =
                                        "Tuyến đón/trả vượt giới hạn đi thêm của chủ xe. Hãy chọn"
                                                + " chuyến khác.";
                            }
                            state.setValue(new State(false, null, null, message));
                        }
                    }

                    /** Không dùng quote hoặc giá 0 dự phòng khi Routing lỗi. */
                    @Override
                    public void onError(Exception error) {
                        if (current(requestedGeneration, requestedInputs, userIdentifier)) {
                            state.setValue(
                                    new State(
                                            false,
                                            null,
                                            null,
                                            "Chưa tính được đường đón/trả. Giữ nguyên điểm đã chọn;"
                                                    + " hãy tải lại để thử tiếp."));
                        }
                    }
                });
    }

    /** Tải lại chủ động mới thử API sau lỗi, không lặp tự động từ render. */
    public void retry() {
        cancel();
        inputs = null;
    }

    /** Cache/loading/đổi phiên không được giữ quote làm quyền gửi. */
    public void clear(String message) {
        State value = state.getValue();
        if (inputs == null
                && value != null
                && !value.busy
                && value.contribution == null
                && message.equals(value.message)) {
            return;
        }
        cancel();
        inputs = null;
        state.setValue(new State(false, null, null, message));
    }

    /** Điểm nối để kiểm tra đổi dữ liệu/phiên mà không gọi mạng thật. */
    protected VietnamDetourChecker createChecker() {
        return new VietnamDetourChecker();
    }

    /** Bỏ kết quả muộn sau đổi P/C, Trip, logout hoặc đóng màn. */
    private boolean current(
            int requestedGeneration, RequestQuote.Inputs requestedInputs, String userIdentifier) {
        return !closed
                && requestedGeneration == generation
                && requestedInputs.sameAs(inputs)
                && FirebaseAuth.getInstance().getCurrentUser() != null
                && userIdentifier.equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    /** Chỉ xác nhận token quote đang hiện; đổi đầu vào hoặc retry luôn bỏ token này. */
    public void confirm(boolean accepted) {
        State value = state.getValue();
        RequestQuote selected = null;
        if (accepted && value != null && !value.busy) {
            selected = value.quote;
        }
        if (selected == confirmedQuote) {
            return;
        }
        confirmedQuote = selected;
        state.setValue(
                new State(
                        value.busy,
                        value.contribution,
                        value.detourMeters,
                        value.message,
                        value.quote));
    }

    /** Chỉ trả quote khách đã đồng ý, không lấy số tiền trong Bundle. */
    public RequestQuote getConfirmedQuote() {
        State value = state.getValue();
        if (value == null || value.quote != confirmedQuote || value.busy) {
            return null;
        }
        return confirmedQuote;
    }

    /** Hủy worker và tăng thế hệ trước khi thay đầu vào. */
    private void cancel() {
        confirmedQuote = null;
        generation = generation + 1;
        if (checker != null) {
            checker.close();
            checker = null;
        }
    }

    /** Giải phóng checker khi người dùng đóng hẳn Detail. */
    @Override
    protected void onCleared() {
        closed = true;
        cancel();
    }
}
