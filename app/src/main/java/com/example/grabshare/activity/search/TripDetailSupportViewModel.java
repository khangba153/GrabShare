package com.example.grabshare.activity.search;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.grabshare.contract.CachedTrip;
import com.example.grabshare.contract.TripCacheStore;
import com.example.grabshare.local.TripCacheRepository;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Nối SQLite Thuận bàn giao; dữ liệu lưu chỉ được dùng đọc, không cấp quyền gửi. */
public final class TripDetailSupportViewModel extends AndroidViewModel {
    /** Snapshot giao diện không giữ Activity hoặc callback từ màn đã đóng. */
    public static final class State {
        public final CachedTrip cachedTrip;
        public final String cacheMessage;
        public final boolean onlineReady;

        /** Sao chép trạng thái bất biến cho mỗi lần render. */
        private State(CachedTrip cachedTrip, String cacheMessage, boolean onlineReady) {
            this.cachedTrip = cachedTrip;
            this.cacheMessage = cacheMessage;
            this.onlineReady = onlineReady;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<State>();
    private final TripCacheStore cache;
    private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private FirebaseAuth.AuthStateListener authListener;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile int generation;
    private volatile boolean active;
    private String userIdentifier;
    private String tripIdentifier;
    private Trip onlineTrip;
    private CachedTrip cachedTrip;
    private String cacheMessage = "";
    private boolean cacheReadPending;

    /** Dùng đúng implementation đã bàn giao, không tạo kho dữ liệu hoặc schema khác. */
    public TripDetailSupportViewModel(Application application) {
        super(application);
        cache = new TripCacheRepository(application);
        publish();
    }

    /** Activity chỉ quan sát trạng thái, không truy cập SQLite trên luồng giao diện. */
    public LiveData<State> getState() {
        return state;
    }

    /** Mở phiên cache theo đúng cặp UID/trip_id và kiểm tra lại kết nối khi quay lại. */
    public void start(String tripIdentifier, String userIdentifier) {
        stop();
        this.tripIdentifier = tripIdentifier;
        this.userIdentifier = userIdentifier;
        active = true;
        onlineTrip = null;
        cachedTrip = null;
        cacheMessage = "";
        cacheReadPending = false;
        final int expectedGeneration = generation;
        authListener =
                new FirebaseAuth.AuthStateListener() {
                    /** Đổi tài khoản phải bỏ dữ liệu giao diện và vô hiệu mọi tác vụ cache chờ. */
                    @Override
                    public void onAuthStateChanged(FirebaseAuth firebaseAuth) {
                        if (!current(expectedGeneration)) {
                            stop();
                            onlineTrip = null;
                            cachedTrip = null;
                            publish();
                        }
                    }
                };
        auth.addAuthStateListener(authListener);
        ConnectivityManager manager =
                (ConnectivityManager)
                        getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            networkCallback =
                    new ConnectivityManager.NetworkCallback() {
                        /**
                         * Mất xác nhận Internet phải khóa thao tác ngay, không đợi Firestore
                         * timeout.
                         */
                        @Override
                        public void onCapabilitiesChanged(
                                Network network, NetworkCapabilities capabilities) {
                            updateConnectivity(expectedGeneration);
                        }

                        /** Mất mạng đang dùng cũng phải chuyển sang bản chỉ đọc. */
                        @Override
                        public void onLost(Network network) {
                            updateConnectivity(expectedGeneration);
                        }
                    };
            manager.registerDefaultNetworkCallback(networkCallback);
        }
        publish();
    }

    /** Chỉ lưu snapshot xác nhận từ server; lỗi mạng mới được thử đọc bản SQLite. */
    public void onTripState(final OnlineTripViewModel.State tripState) {
        final int expectedGeneration = generation;
        if (!current(expectedGeneration)) {
            return;
        }
        if (tripState.online && tripState.trip != null && hasInternet()) {
            final String expectedUserIdentifier = userIdentifier;
            onlineTrip = tripState.trip;
            cachedTrip = null;
            cacheMessage = "";
            cacheExecutor.execute(
                    new Runnable() {
                        /** Kiểm tra phiên và vòng đời ngay trước khi gọi API cache đồng bộ. */
                        @Override
                        public void run() {
                            if (!current(expectedGeneration)) {
                                return;
                            }
                            try {
                                com.example.grabshare.model.PublicProfile profile = null;
                                if (tripState.driver != null) {
                                    profile = tripState.driver.profile;
                                }
                                cache.saveViewedTrip(
                                        expectedUserIdentifier, tripState.trip, profile);
                            } catch (RuntimeException error) {
                                showCacheResult(
                                        expectedGeneration,
                                        null,
                                        "Chưa lưu được bản xem offline. Thông tin online vẫn dùng"
                                                + " được.");
                            }
                        }
                    });
        } else {
            onlineTrip = null;
            if (!hasInternet()) {
                loadCachedTrip(expectedGeneration);
            } else {
                cachedTrip = null;
            }
        }
        publish();
    }

    /** Cache không chứa đủ trường nghiệp vụ mới nên chỉ kiểm tra dữ liệu phục vụ đọc. */
    private void loadCachedTrip(final int expectedGeneration) {
        if (cacheReadPending) {
            return;
        }
        cacheReadPending = true;
        final String expectedUserIdentifier = userIdentifier;
        final String expectedTripIdentifier = tripIdentifier;
        cacheExecutor.execute(
                new Runnable() {
                    /** Không dùng Firebase cache để thay cho hợp đồng SQLite được bàn giao. */
                    @Override
                    public void run() {
                        if (!current(expectedGeneration)) {
                            return;
                        }
                        try {
                            CachedTrip viewedTrip =
                                    cache.getViewedTrip(
                                            expectedUserIdentifier, expectedTripIdentifier);
                            if (viewedTrip != null && validCachedTrip(viewedTrip)) {
                                showCacheResult(expectedGeneration, viewedTrip, "");
                            } else {
                                showCacheResult(
                                        expectedGeneration,
                                        null,
                                        "Không có bản lưu đọc được cho tài khoản này. Cần mạng để"
                                                + " tải chuyến.");
                            }
                        } catch (RuntimeException error) {
                            showCacheResult(
                                    expectedGeneration,
                                    null,
                                    "Không đọc được dữ liệu SQLite. Hãy thử lại khi có mạng.");
                        }
                    }
                });
    }

    /** Không để kết quả đọc SQLite trễ thay thế snapshot server mới. */
    private void showCacheResult(
            final int expectedGeneration, final CachedTrip result, final String message) {
        TaskExecutors.MAIN_THREAD.execute(
                new Runnable() {
                    /** Chỉ công bố cache của đúng phiên đang xem và khi không có dữ liệu online. */
                    @Override
                    public void run() {
                        if (!current(expectedGeneration)) {
                            return;
                        }
                        cacheReadPending = false;
                        if (onlineTrip == null && !hasInternet()) {
                            cachedTrip = result;
                        }
                        cacheMessage = message;
                        publish();
                    }
                });
    }

    /** Không suy đoán acceptedRequestId hoặc quyền thao tác từ cache thiếu trường. */
    private boolean validCachedTrip(CachedTrip value) {
        Trip trip = value.getTrip();
        return trip != null
                && tripIdentifier.equals(trip.getId())
                && SearchTripPolicy.validPlace(trip.getOrigin())
                && SearchTripPolicy.validPlace(trip.getDestination())
                && trip.getDepartureAt() != null
                && trip.getCostVnd() != null
                && trip.getCostVnd() >= 0
                && trip.getAvailableSeats() != null
                && trip.getAvailableSeats() >= 0
                && trip.getAvailableSeats() <= 1
                && ("OPEN".equals(trip.getStatus())
                        || "FULL".equals(trip.getStatus())
                        || "CANCELLED".equals(trip.getStatus())
                        || "COMPLETED".equals(trip.getStatus()))
                && value.getSavedAt() > 0;
    }

    /** Mọi mutation vẫn bị chặn khi mạng không được hệ thống xác nhận. */
    private boolean hasInternet() {
        ConnectivityManager manager =
                (ConnectivityManager)
                        getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Callback mạng đến từ luồng hệ thống, chỉ thay LiveData sau khi chuyển về UI thread. */
    private void updateConnectivity(final int expectedGeneration) {
        TaskExecutors.MAIN_THREAD.execute(
                new Runnable() {
                    /** Không cấp lại quyền online chỉ vì có mạng; phải chờ snapshot server mới. */
                    @Override
                    public void run() {
                        if (!current(expectedGeneration)) {
                            return;
                        }
                        if (!hasInternet()) {
                            onlineTrip = null;
                            loadCachedTrip(expectedGeneration);
                        } else {
                            if (onlineTrip == null) {
                                cachedTrip = null;
                            }
                        }
                        publish();
                    }
                });
    }

    /** Chặn callback và tác vụ nền của màn hoặc UID đã hết hiệu lực. */
    private boolean current(int expectedGeneration) {
        FirebaseUser user = auth.getCurrentUser();
        return active
                && expectedGeneration == generation
                && user != null
                && user.getUid().equals(userIdentifier)
                && SearchTripPolicy.validId(tripIdentifier);
    }

    /** State chỉ được ghi trên UI thread; worker luôn chuyển về qua main executor. */
    private void publish() {
        state.setValue(
                new State(
                        cachedTrip,
                        cacheMessage,
                        onlineTrip != null && hasInternet() && current(generation)));
    }

    /** Ngừng tác vụ chờ trước khi sang Account/logout; không giữ quyền ghi của màn cũ. */
    public void stop() {
        active = false;
        generation = generation + 1;
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
            authListener = null;
        }
        if (networkCallback != null) {
            ConnectivityManager manager =
                    (ConnectivityManager)
                            getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager != null) {
                manager.unregisterNetworkCallback(networkCallback);
            }
            networkCallback = null;
        }
    }

    /** Đóng executor khi Detail không còn được giữ qua xoay màn. */
    @Override
    protected void onCleared() {
        stop();
        cacheExecutor.shutdownNow();
    }
}
