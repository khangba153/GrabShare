package com.example.grabshare.activity.driver;

//region Imports

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.adapter.driver.DriverTripAdapter;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.driver.DriverTripRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//endregion


/**
 * Hiển thị danh sách chuyến do tài khoản hiện tại đăng.
 *
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

    private final List<Trip> allTrips = new ArrayList<>();

    //endregion


    //region 4. Screen state

    private int selectedFilter = FILTER_UPCOMING;

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

        if (savedInstanceState != null) {
            selectedFilter = savedInstanceState.getInt(
                    STATE_SELECTED_FILTER,
                    FILTER_UPCOMING
            );
        }

        updateFilterAppearance();

        connectFilterButtons();
        connectPostTripButton();
    }

    /**
     * Tải lại dữ liệu khi màn hình xuất hiện trở lại.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadTrips();
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

        showLoadingState();

        tripRepository.loadTripsForCurrentDriver(
                new RepositoryCallback<List<Trip>>() {

                    /**
                     * Chuyển kết quả tải thành công sang luồng xử lý.
                     */
                    @Override
                    public void onSuccess(List<Trip> loadedTrips) {
                        handleTripsLoaded(loadedTrips);
                    }

                    /**
                     * Chuyển lỗi truy vấn sang luồng xử lý lỗi.
                     */
                    @Override
                    public void onError(Exception error) {
                        handleTripsLoadError(error);
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
        List<Trip> filteredTrips = filterTripsForSelectedTab();

        tripAdapter.replaceTrips(filteredTrips);

        if (filteredTrips.isEmpty()) {
            showEmptyTripsState();
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
     *
     * Chỉ dùng trạng thái lưu trong Firestore,
     * không suy ra COMPLETED từ thời gian khởi hành.
     */
    private boolean isHistoryTrip(Trip trip) {
        String tripStatus = trip.getStatus();

        String normalizedStatus = tripStatus == null
                ? ""
                : tripStatus.trim().toUpperCase(Locale.ROOT);

        return normalizedStatus.equals("COMPLETED")
                || normalizedStatus.equals("CANCELLED");
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

    //endregion
}