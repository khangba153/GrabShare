package com.example.grabshare.activity.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.example.grabshare.repository.search.TripSearchRepository;
import com.example.grabshare.repository.search.TripSearchRepository.DriverInfo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

/** Giữ dữ liệu đọc của Detail/Review; listener chỉ sống khi màn đang hiển thị. */
public class OnlineTripViewModel extends ViewModel {
    /** Trạng thái hiển thị, online chỉ đúng sau snapshot server của phiên hiện tại. */
    public static class State {
        public final Trip trip;
        public final DriverInfo driver;
        public final boolean online;
        public final String message;

        /** Tạo snapshot trạng thái, không giữ Activity hoặc View. */
        State(Trip trip, DriverInfo driver, boolean online, String message) {
            this.trip = trip;
            this.driver = driver;
            this.online = online;
            this.message = message;
        }
    }

    private final MutableLiveData<State> state =
            new MutableLiveData<State>(new State(null, null, false, "Đang tải chuyến…"));
    private final TripSearchRepository repository = new TripSearchRepository();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private ListenerRegistration listener;
    private FirebaseAuth.AuthStateListener authListener;
    private int generation;
    private String userIdentifier;
    private String driverIdentifier;
    private DriverInfo driverInfo;

    /** Cho Activity quan sát theo lifecycle. */
    public LiveData<State> getState() {
        return state;
    }

    /** Chỉ chấp nhận callback của phiên và thế hệ listener hiện tại. */
    private boolean current(int expectedGeneration) {
        FirebaseUser user = auth.getCurrentUser();
        return expectedGeneration == generation
                && user != null
                && user.getUid().equals(userIdentifier);
    }

    /** Bắt đầu đọc đúng ID; lần mở lại không dùng dữ liệu đã xác nhận ở phiên cũ. */
    public void start(final String tripId, String userId) {
        stop();
        userIdentifier = userId;
        driverIdentifier = null;
        driverInfo = null;
        final int expectedGeneration = generation;
        if (!SearchTripPolicy.validId(tripId) || !current(expectedGeneration)) {
            state.setValue(
                    new State(
                            null,
                            null,
                            false,
                            "Thiếu mã chuyến hoặc bạn cần đăng nhập. Dùng Back để quay lại."));
            return;
        }
        state.setValue(new State(null, null, false, "Đang xác nhận chuyến trên máy chủ…"));
        authListener =
                new FirebaseAuth.AuthStateListener() {
                    /** Ngăn tài khoản mới nhìn dữ liệu hoặc thao tác của phiên cũ. */
                    @Override
                    public void onAuthStateChanged(FirebaseAuth firebaseAuth) {
                        if (!current(expectedGeneration)) {
                            stop();
                            state.setValue(
                                    new State(
                                            null,
                                            null,
                                            false,
                                            "Phiên đăng nhập đã đổi. Quay lại và mở chuyến lần"
                                                + " nữa."));
                        }
                    }
                };
        auth.addAuthStateListener(authListener);
        listener =
                repository.watchOnlineTrip(
                        tripId,
                        new RepositoryCallback<Trip>() {
                            /** Cập nhật state và chỉ tải hồ sơ lại khi tài xế thay đổi. */
                            @Override
                            public void onSuccess(Trip trip) {
                                if (!current(expectedGeneration)) {
                                    return;
                                }
                                if (!trip.getDriverId().equals(driverIdentifier)) {
                                    driverIdentifier = trip.getDriverId();
                                    driverInfo = null;
                                    state.setValue(new State(trip, null, true, ""));
                                    loadDriver(driverIdentifier, expectedGeneration);
                                } else {
                                    String warning = "";
                                    if (driverInfo != null) {
                                        warning = driverInfo.warning;
                                    }
                                    state.setValue(new State(trip, driverInfo, true, warning));
                                }
                            }

                            /** Offline/lỗi không được giữ quyền gửi từ dữ liệu cũ. */
                            @Override
                            public void onError(Exception error) {
                                if (current(expectedGeneration)) {
                                    String message = "Không tải được chuyến. Hãy thử lại.";
                                    if (error.getMessage() != null) {
                                        message = error.getMessage();
                                    }
                                    state.setValue(new State(null, null, false, message));
                                }
                            }
                        });
    }

    /** Ghép dữ liệu công khai mà không để callback cũ thay chuyến hoặc phiên mới. */
    private void loadDriver(final String expectedDriverIdentifier, final int expectedGeneration) {
        repository.getOnlineDriverInfo(
                expectedDriverIdentifier,
                new RepositoryCallback<DriverInfo>() {
                    /** Giữ riêng cảnh báo profile/rating, không giả điểm bằng không. */
                    @Override
                    public void onSuccess(DriverInfo driverInformation) {
                        if (!current(expectedGeneration)
                                || !expectedDriverIdentifier.equals(driverIdentifier)) {
                            return;
                        }
                        driverInfo = driverInformation;
                        State currentState = state.getValue();
                        if (currentState != null && currentState.online) {
                            state.setValue(
                                    new State(
                                            currentState.trip,
                                            driverInformation,
                                            true,
                                            driverInformation.warning));
                        }
                    }

                    /** Có thể tải lại từ nút Thử lại khi dịch vụ không trả dữ liệu. */
                    @Override
                    public void onError(Exception error) {
                        if (!current(expectedGeneration)) {
                            return;
                        }
                        State value = state.getValue();
                        if (value != null && value.online) {
                            state.setValue(
                                    new State(
                                            value.trip,
                                            null,
                                            true,
                                            "Chưa tải được thông tin chủ xe. Hãy thử lại."));
                        }
                    }
                });
    }

    /** Gỡ listener và vô hiệu hóa callback khi màn dừng. */
    public void stop() {
        generation = generation + 1;
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
            authListener = null;
        }
    }

    /** Giải phóng listener khi người dùng đóng hẳn màn. */
    @Override
    protected void onCleared() {
        stop();
    }
}
