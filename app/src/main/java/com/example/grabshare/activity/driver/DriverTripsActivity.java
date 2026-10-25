package com.example.grabshare.activity.driver;

//region Imports

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.presentation.MainNavigation;
import com.example.grabshare.presentation.TripListFilter;
import com.example.grabshare.presentation.TripListState;
import com.example.grabshare.presentation.TripListTimeRefresh;
import com.example.grabshare.adapter.driver.DriverTripAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.driver.DriverTripRepository;
import com.example.grabshare.repository.driver.DriverRequestRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

//endregion


/**
 * Hiển thị danh sách chuyến do tài khoản hiện tại đăng.
 * Chỉ đọc dữ liệu và điều phối giao diện.
 * Việc truy vấn Firestore thuộc DriverTripRepository.
 */
public class DriverTripsActivity extends AppCompatActivity {

    //region 1. Constants

    private static final String STATE_SELECTED_FILTER =
            "selected_driver_trip_filter";

    private static final int FILTER_UPCOMING = 0;
    private static final int FILTER_HISTORY = 1;

    //endregion


    //region 2. View references

    private ListView tripListView;
    private ProgressBar loadingProgressBar;
    private TextView messageTextView;

    private AppCompatButton upcomingButton;
    private AppCompatButton historyButton;
    private AppCompatButton postTripButton;

    //endregion


    //region 3. Dependencies and data

    private DriverTripAdapter tripAdapter;
    private DriverTripRepository tripRepository;
    private FirebaseAuth firebaseAuth;
    private DriverRequestRepository requestRepository;
    private ListenerRegistration requestCountsListener;
    private FirebaseAuth.AuthStateListener sessionListener;
    private int screenGeneration;

    private final List<Trip> allTrips = new ArrayList<>();

    //endregion


    //region 4. Screen state

    private int selectedFilter = FILTER_UPCOMING;
    private final TripListFilter activeFilter = new TripListFilter(
            new String[]{"Tất cả", "Đang nhận yêu cầu", "Đã nhận khách"},
            new String[]{"", "OPEN", "FULL"});
    private final TripListFilter historyFilter = new TripListFilter(
            new String[]{"Tất cả", "Hoàn thành", "Đã hủy", "Hết hạn"},
            new String[]{"", "COMPLETED", "CANCELLED", "EXPIRED"});
    private TripListFilter listFilter = activeFilter;
    private String filterOwnerIdentifier;
    private final TripListTimeRefresh timeRefresh = new TripListTimeRefresh();

    private boolean isLoading;
    private boolean hasLoadedSuccessfully;
    private boolean hasLoadError;

    //endregion


    //region 5. Activity lifecycle

    /**
     * Khởi tạo màn hình và kết nối các thành phần giao diện.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_driver_trips);

        initializeViews();
        initializeRepository();
        initializeAdapter();
        restoreFilters(savedInstanceState);
        ((TextView) findViewById(R.id.trip_list_filter)).setText(listFilter.summary());
        findViewById(R.id.trip_list_filter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listFilter.show(DriverTripsActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.trip_list_filter)).setText(listFilter.summary());
                        if (hasLoadedSuccessfully && !isLoading && !hasLoadError) {
                            displayFilteredTrips();
                        }
                    }
                });
            }
        });

        updateFilterAppearance();

        connectFilterButtons();
        connectPostTripButton();
        connectTripItemClick();
        connectBottomNavigation();
    }

    /** Nối các tab dưới để chuyển màn trực tiếp từ danh sách chuyến chủ xe. */
    private void connectBottomNavigation() {
        MainNavigation.bind(this);
        MainNavigation.bindNotifications(this, R.id.header_action);
    }

    /**
     * Tải lại dữ liệu khi màn hình xuất hiện trở lại.
     */
    @Override
    protected void onResume() {
        super.onResume();
        sessionListener = new FirebaseAuth.AuthStateListener() {
            /** Xóa dữ liệu tài khoản cũ trước khi tải lại cho phiên hiện tại. */
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth authentication) {
                stopRequestCounts();
                FirebaseUser currentUser = authentication.getCurrentUser();
                String userIdentifier = null;
                if (currentUser != null) {
                    userIdentifier = currentUser.getUid();
                }
                if (!java.util.Objects.equals(filterOwnerIdentifier, userIdentifier)) {
                    saveFilters();
                    restoreFilters(null);
                    updateFilterAppearance();
                }
                allTrips.clear();
                tripAdapter.replaceTrips(new ArrayList<Trip>());
                tripAdapter.replacePendingCounts(null);
                loadTrips();
                watchRequestCounts();
            }
        };
        firebaseAuth.addAuthStateListener(sessionListener);
    }

    /** Dừng listener và vô hiệu callback khi chuyển màn hoặc đăng xuất. */
    @Override
    protected void onPause() {
        saveFilters();
        timeRefresh.stop();
        if (sessionListener != null) {
            firebaseAuth.removeAuthStateListener(sessionListener);
            sessionListener = null;
        }
        stopRequestCounts();
        super.onPause();
    }

    /** Một listener dùng chung cho tất cả thẻ chuyến của chủ xe đang đăng nhập. */
    private void watchRequestCounts() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        final String driverIdentifier = currentUser.getUid();
        final int expectedGeneration = screenGeneration;
        requestCountsListener = requestRepository.watchPendingCounts(
                driverIdentifier, new RepositoryCallback<Map<String, Integer>>() {
                    /** Nhận số lượng mới khi khách gửi, hủy hoặc chủ xe phản hồi yêu cầu. */
                    @Override
                    public void onSuccess(Map<String, Integer> counts) {
                        if (isCurrentSession(expectedGeneration, driverIdentifier)) {
                            tripAdapter.replacePendingCounts(counts);
                        }
                    }

                    /** Giữ lối mở danh sách nhưng không công bố số lượng cũ khi lỗi hoặc offline. */
                    @Override
                    public void onError(Exception error) {
                        if (isCurrentSession(expectedGeneration, driverIdentifier)) {
                            tripAdapter.replacePendingCounts(null);
                        }
                    }
                });
    }

    /** Kiểm tra cả vòng đời màn và UID để callback muộn không thay dữ liệu phiên khác. */
    private boolean isCurrentSession(int expectedGeneration, String driverIdentifier) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        return expectedGeneration == screenGeneration
                && !isFinishing() && !isDestroyed()
                && currentUser != null && driverIdentifier.equals(currentUser.getUid());
    }

    /** Đóng kết nối theo dõi và vô hiệu cả tác vụ tải chuyến đang chạy. */
    private void stopRequestCounts() {
        screenGeneration = screenGeneration + 1;
        if (requestCountsListener != null) {
            requestCountsListener.remove();
            requestCountsListener = null;
        }
    }

    /**
     * Lưu bộ lọc đang chọn khi Android tạo lại màn hình.
     */
    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle savedInstanceState
    ) {
        savedInstanceState.putInt(
                STATE_SELECTED_FILTER,
                selectedFilter
        );

        Bundle activeState = new Bundle();
        Bundle historyState = new Bundle();
        activeFilter.save(activeState);
        historyFilter.save(historyState);
        savedInstanceState.putBundle("driver_active_filter", activeState);
        savedInstanceState.putBundle("driver_history_filter", historyState);
        savedInstanceState.putString("driver_filter_uid", filterOwnerIdentifier);
        super.onSaveInstanceState(savedInstanceState);
    }

    //endregion


    //region 6. Initialization

    /**
     * Lấy các thành phần giao diện từ XML.
     */
    private void initializeViews() {
        tripListView = findViewById(R.id.trip_list);
        loadingProgressBar = findViewById(R.id.trip_loading);
        messageTextView = findViewById(R.id.trip_message);

        upcomingButton = findViewById(R.id.tab_upcoming);
        historyButton = findViewById(R.id.tab_history);
        postTripButton = findViewById(R.id.primary_action);
    }

    /**
     * Khởi tạo các thành phần Firebase và repository.
     */
    private void initializeRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        requestRepository = new DriverRequestRepository(
                FirebaseFirestore.getInstance(), firebaseAuth);

        tripRepository = new DriverTripRepository(
                FirebaseFirestore.getInstance(),
                firebaseAuth
        );
    }

    /**
     * Gắn adapter vào danh sách chuyến.
     */
    private void initializeAdapter() {
        tripAdapter = new DriverTripAdapter(this);
        tripAdapter.setOnRequestsClickListener(new DriverTripAdapter.OnRequestsClickListener() {
            /** Thẻ chuyến vẫn mở quản lý; nhãn yêu cầu mở trực tiếp danh sách của chuyến đó. */
            @Override
            public void onRequestsClick(Trip trip) {
                FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                if (currentUser == null || trip == null
                        || !currentUser.getUid().equals(trip.getDriverId())
                        || trip.getId() == null || trip.getId().trim().isEmpty()) {
                    return;
                }
                Intent requestsIntent = new Intent(
                        DriverTripsActivity.this, DriverRequestsActivity.class);
                requestsIntent.putExtra(NavigationKeys.TRIP_ID, trip.getId());
                startActivity(requestsIntent);
            }
        });
        tripListView.setAdapter(tripAdapter);
    }

    //endregion


    //region 7. Main data flow

    /**
     * Kiểm tra đăng nhập rồi yêu cầu repository tải danh sách chuyến.
     */
    private void loadTrips() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            isLoading = false;
            hasLoadedSuccessfully = false;
            hasLoadError = true;

            showStatusMessage(
                    "Bạn cần đăng nhập để xem chuyến của mình.",
                    false
            );

            return;
        }

        isLoading = true;
        hasLoadError = false;
        final int expectedGeneration = screenGeneration;
        final String driverIdentifier = currentUser.getUid();

        showLoadingState();

        tripRepository.loadTripsForCurrentDriver(
                new RepositoryCallback<>() {

                    /**
                     * Chuyển kết quả tải thành công sang luồng xử lý.
                     */
                    @Override
                    public void onSuccess(List<Trip> loadedTrips) {
                        if (isCurrentSession(expectedGeneration, driverIdentifier)) {
                            handleTripsLoaded(loadedTrips);
                        }
                    }

                    /**
                     * Chuyển lỗi truy vấn sang luồng xử lý lỗi.
                     */
                    @Override
                    public void onError(Exception error) {
                        if (isCurrentSession(expectedGeneration, driverIdentifier)) {
                            handleTripsLoadError(error);
                        }
                    }
                }
        );
    }

    /**
     * Cập nhật dữ liệu nội bộ sau khi tải thành công.
     */
    private void handleTripsLoaded(List<Trip> loadedTrips) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        isLoading = false;
        hasLoadedSuccessfully = true;
        hasLoadError = false;
        findViewById(R.id.trip_list_filter).setEnabled(true);

        allTrips.clear();

        if (loadedTrips != null) {
            allTrips.addAll(loadedTrips);
        }

        displayFilteredTrips();
    }

    /**
     * Cập nhật trạng thái màn hình khi tải dữ liệu thất bại.
     */
    private void handleTripsLoadError(Exception error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        isLoading = false;
        hasLoadedSuccessfully = false;
        hasLoadError = true;

        showStatusMessage(
                "Không thể tải danh sách chuyến. "
                        + "Hãy kiểm tra kết nối, quyền "
                        + "truy cập và thử lại.",
                true
        );
    }

    //endregion


    //region 8. Filter and list display

    /**
     * Xử lý khi người dùng chọn Sắp đi hoặc Lịch sử.
     */
    private void selectFilter(int requestedFilter) {
        selectedFilter = requestedFilter;
        listFilter = activeFilter;
        if (selectedFilter == FILTER_HISTORY) {
            listFilter = historyFilter;
        }
        ((TextView) findViewById(R.id.trip_list_filter)).setText(listFilter.summary());

        updateFilterAppearance();

        if (isLoading || hasLoadError || !hasLoadedSuccessfully) {
            return;
        }

        displayFilteredTrips();
    }

    /**
     * Cập nhật màu sắc để thể hiện bộ lọc đang chọn.
     */
    private void updateFilterAppearance() {
        boolean showingUpcoming = selectedFilter == FILTER_UPCOMING;

        int selectedBackgroundColor = getColor(R.color.primary);
        int unselectedBackgroundColor = getColor(R.color.white);
        int selectedTextColor = getColor(R.color.white);
        int unselectedTextColor = getColor(R.color.ink);

        upcomingButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        showingUpcoming
                                ? selectedBackgroundColor
                                : unselectedBackgroundColor
                )
        );

        upcomingButton.setTextColor(
                showingUpcoming
                        ? selectedTextColor
                        : unselectedTextColor
        );

        historyButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        showingUpcoming
                                ? unselectedBackgroundColor
                                : selectedBackgroundColor
                )
        );

        historyButton.setTextColor(
                showingUpcoming
                        ? unselectedTextColor
                        : selectedTextColor
        );
    }

    /**
     * Điều phối việc lọc dữ liệu và cập nhật danh sách trên màn hình.
     */
    private void displayFilteredTrips() {
        final long now = System.currentTimeMillis();
        List<Trip> filteredTrips = filterTripsForSelectedTab();

        List<Trip> matchedTrips = new ArrayList<>();
        for (Trip trip : filteredTrips) {
            if (listFilter.matches(TripListState.driverStatus(trip, now), trip.getDepartureAt())) {
                matchedTrips.add(trip);
            }
        }
        Collections.sort(matchedTrips, new Comparator<Trip>() {
            @Override
            public int compare(Trip first, Trip second) {
                if (selectedFilter == FILTER_HISTORY && listFilter.automaticOrder()) {
                    return TripListState.compareTime(
                            TripListState.historyTime(TripListState.driverStatus(first, now),
                                    first.getUpdatedAt(), first.getCreatedAt(), first),
                            TripListState.historyTime(TripListState.driverStatus(second, now),
                                    second.getUpdatedAt(), second.getCreatedAt(), second), true);
                }
                if (selectedFilter == FILTER_UPCOMING) {
                    int firstPriority = TripListState.attentionPriority(first.getStatus(), first, now);
                    int secondPriority = TripListState.attentionPriority(second.getStatus(), second, now);
                    if (firstPriority != secondPriority) {
                        return Integer.compare(firstPriority, secondPriority);
                    }
                }
                return listFilter.compare(first.getDepartureAt(), second.getDepartureAt());
            }
        });
        filteredTrips = matchedTrips;
        tripAdapter.replaceTrips(filteredTrips);
        long nextDeparture = Long.MAX_VALUE;
        for (Trip trip : allTrips) {
            if (trip.getDepartureAt() != null && trip.getDepartureAt().toDate().getTime() > now) {
                nextDeparture = Math.min(nextDeparture, trip.getDepartureAt().toDate().getTime());
            }
        }
        timeRefresh.schedule(nextDeparture, new Runnable() {
            /** Tới giờ khởi hành tự chuyển nhóm hiển thị, không sửa trạng thái chuyến. */
            @Override
            public void run() {
                if (hasLoadedSuccessfully && !isLoading && !hasLoadError) {
                    displayFilteredTrips();
                }
            }
        });

        if (filteredTrips.isEmpty()) {
            String message = "Chưa có chuyến đang quản lý.";
            if (selectedFilter == FILTER_HISTORY) {
                message = "Chưa có lịch sử đăng chuyến.";
            }
            if (!"Bộ lọc".equals(listFilter.summary())) {
                message = "Không có chuyến phù hợp. Chọn Bộ lọc → Đặt lại.";
            }
            showStatusMessage(message, false);
            return;
        }

        loadingProgressBar.setVisibility(View.GONE);
        messageTextView.setVisibility(View.GONE);
        tripListView.setVisibility(View.VISIBLE);
    }

    /**
     * Lọc dữ liệu theo bộ lọc đang chọn.
     */
    private List<Trip> filterTripsForSelectedTab() {
        List<Trip> filteredTrips = new ArrayList<>();

        for (Trip currentTrip : allTrips) {
            boolean isHistoryTrip = isHistoryTrip(currentTrip);

            if (selectedFilter == FILTER_UPCOMING && !isHistoryTrip) {
                filteredTrips.add(currentTrip);
            } else if (
                    selectedFilter == FILTER_HISTORY && isHistoryTrip
            ) {
                filteredTrips.add(currentTrip);
            }
        }

        return filteredTrips;
    }

    /**
     * Xác định một chuyến có thuộc lịch sử hay không.
     * Chỉ dùng trạng thái lưu trong Firestore,
     * không suy ra COMPLETED từ thời gian khởi hành.
     */
    private boolean isHistoryTrip(Trip trip) {
        return TripListState.history(TripListState.driverStatus(trip, System.currentTimeMillis()));
    }

    /**
     * Hiển thị thông báo phù hợp khi danh sách đang lọc rỗng.
     */
    private void showEmptyTripsState() {
        if (allTrips.isEmpty()) {
            showStatusMessage(
                    "Bạn chưa có chuyến nào được đăng.",
                    false
            );
        } else if (selectedFilter == FILTER_UPCOMING) {
            showStatusMessage(
                    "Không có chuyến nào trong mục Sắp đi.",
                    false
            );
        } else {
            showStatusMessage(
                    "Chưa có chuyến nào trong lịch sử.",
                    false
            );
        }
    }

    //endregion


    //region 9. UI states

    /**
     * Hiển thị trạng thái đang tải dữ liệu.
     */
    private void showLoadingState() {
        timeRefresh.stop();
        findViewById(R.id.trip_list_filter).setEnabled(false);
        loadingProgressBar.setVisibility(View.VISIBLE);
        tripListView.setVisibility(View.GONE);
        messageTextView.setVisibility(View.GONE);
    }

    /**
     * Hiển thị thông báo rỗng, đăng nhập hoặc lỗi tải dữ liệu.
     *
     * @param message nội dung thông báo
     * @param canRetry cho biết người dùng có thể thử tải lại
     */
    private void showStatusMessage(
            String message,
            boolean canRetry
    ) {
        loadingProgressBar.setVisibility(View.GONE);
        tripListView.setVisibility(View.GONE);

        messageTextView.setText(
                canRetry
                        ? message + "\nChạm vào đây để thử tải lại."
                        : message
        );

        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setClickable(canRetry);
        messageTextView.setFocusable(canRetry);

        if (canRetry) {
            messageTextView.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View view) {
                            loadTrips();
                        }
                    }
            );
        } else {
            messageTextView.setOnClickListener(null);
        }
    }

    //endregion


    //region 10. UI events and navigation

    /**
     * Kết nối hai nút bộ lọc với luồng xử lý.
     */
    private void connectFilterButtons() {
        upcomingButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        selectFilter(FILTER_UPCOMING);
                    }
                }
        );

        historyButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        selectFilter(FILTER_HISTORY);
                    }
                }
        );
    }

    /**
     * Mở màn quản lý khi người dùng chọn một chuyến trong danh sách.
     * Chỉ truyền trip_id; ManageTripActivity sẽ đọc lại dữ liệu và quyền từ máy chủ.
     */
    private void connectTripItemClick() {
        tripListView.setOnItemClickListener(
                new android.widget.AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long itemId
                    ) {
                        if (position < 0 || position >= tripAdapter.getCount()) {
                            return;
                        }

                        Trip selectedTrip = tripAdapter.getItem(position);
                        if (selectedTrip == null
                                || selectedTrip.getId() == null
                                || selectedTrip.getId().trim().isEmpty()) {
                            Toast.makeText(
                                    DriverTripsActivity.this,
                                    "Không tìm thấy ID chuyến.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        Intent manageIntent = new Intent(
                                DriverTripsActivity.this,
                                ManageTripActivity.class
                        );
                        manageIntent.putExtra(
                                NavigationKeys.TRIP_ID,
                                selectedTrip.getId()
                        );

                        if (manageIntent.resolveActivity(getPackageManager()) == null) {
                            Toast.makeText(
                                    DriverTripsActivity.this,
                                    "Màn Quản lý chuyến chưa được đăng ký trong Manifest.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        startActivity(manageIntent);
                    }
                }
        );
    }

    /**
     * Kết nối nút đăng chuyến với màn hình đăng chuyến.
     */
    private void connectPostTripButton() {
        postTripButton.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        openPostTripScreen();
                    }
                }
        );
    }

    /**
     * Mở biểu mẫu đăng chuyến.
     */
    private void openPostTripScreen() {
        Intent postTripIntent = new Intent(
                DriverTripsActivity.this,
                PostTripActivity.class
        );

        startActivity(postTripIntent);
    }

    /** Phục hồi riêng hai mục và đúng tài khoản, không dùng chung với bộ lọc Đi nhờ. */
    private void restoreFilters(Bundle state) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        filterOwnerIdentifier = null;
        if (currentUser != null) {
            filterOwnerIdentifier = currentUser.getUid();
        }
        activeFilter.restorePreferences(this, filterKey("active"));
        historyFilter.restorePreferences(this, filterKey("history"));
        selectedFilter = getSharedPreferences("trip_list_preferences", MODE_PRIVATE)
                .getInt(filterKey("selected_group"), FILTER_UPCOMING);
        if (state != null && java.util.Objects.equals(filterOwnerIdentifier, state.getString("driver_filter_uid"))) {
            selectedFilter = state.getInt(STATE_SELECTED_FILTER, selectedFilter);
            activeFilter.restore(state.getBundle("driver_active_filter"));
            historyFilter.restore(state.getBundle("driver_history_filter"));
        }
        if (selectedFilter != FILTER_HISTORY) {
            selectedFilter = FILTER_UPCOMING;
        }
        listFilter = activeFilter;
        if (selectedFilter == FILTER_HISTORY) {
            listFilter = historyFilter;
        }
        ((TextView) findViewById(R.id.trip_list_filter)).setText(listFilter.summary());
    }

    private String filterKey(String suffix) {
        return "driver_" + filterOwnerIdentifier + "_" + suffix;
    }

    private void saveFilters() {
        if (filterOwnerIdentifier == null) {
            return;
        }
        activeFilter.savePreferences(this, filterKey("active"));
        historyFilter.savePreferences(this, filterKey("history"));
        getSharedPreferences("trip_list_preferences", MODE_PRIVATE).edit()
                .putInt(filterKey("selected_group"), selectedFilter).apply();
    }

    @Override
    protected void onDestroy() {
        timeRefresh.close();
        super.onDestroy();
    }

    //endregion
}
