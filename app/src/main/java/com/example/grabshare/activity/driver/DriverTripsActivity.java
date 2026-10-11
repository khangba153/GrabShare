package com.example.grabshare.activity.driver;

import android.content.res.ColorStateList;
import android.content.Intent;
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

/**
 * Hiển thị danh sách chuyến do tài khoản hiện tại đăng.
 * <p>
 * Chỉ đọc dữ liệu Firestore, không tạo hoặc cập nhật chuyến.
 */
public class DriverTripsActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_FILTER =
            "selected_driver_trip_filter";

    private static final int FILTER_UPCOMING = 0;
    private static final int FILTER_HISTORY = 1;

    private ListView tripListView;
    private ProgressBar loadingProgressBar;
    private TextView messageTextView;

    private AppCompatButton upcomingButton;
    private AppCompatButton historyButton;
    private AppCompatButton postTripButton;

    private DriverTripAdapter tripAdapter;
    private DriverTripRepository tripRepository;
    private FirebaseAuth firebaseAuth;

    private final List<Trip> allTrips = new ArrayList<>();

    private int selectedFilter = FILTER_UPCOMING;

    private boolean isLoading;
    private boolean hasLoadedSuccessfully;
    private boolean hasLoadError;

    /**
     * Khởi tạo màn hình, adapter và các sự kiện giao diện.
     *
     * @param savedInstanceState trạng thái giao diện đã lưu trước đó
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
     * Khởi tạo repository dùng chung Firebase Authentication và Firestore.
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

    /**
     * Tải lại danh sách khi màn hình được mở hoặc quay trở lại.
     * <p>
     * Lần quay lại từ màn hình đăng, sửa hoặc quản lý chuyến sẽ làm mới
     * dữ liệu thay vì mặc định giữ danh sách cũ.
     */
    @Override
    protected void onResume() {
        super.onResume();

        loadTrips();
    }

    /**
     * Lưu bộ lọc hiện tại khi Android tạo lại màn hình.
     *
     * @param savedInstanceState nơi lưu trạng thái màn hình
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        savedInstanceState.putInt(
                STATE_SELECTED_FILTER,
                selectedFilter
        );

        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Kiểm tra phiên đăng nhập rồi yêu cầu repository tải dữ liệu từ máy chủ.
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

        loadingProgressBar.setVisibility(View.VISIBLE);
        tripListView.setVisibility(View.GONE);
        messageTextView.setVisibility(View.GONE);

        tripRepository.loadTripsForCurrentDriver(
                new RepositoryCallback<>() {

                    /**
                     * Nhận danh sách chuyến sau khi truy vấn thành công.
                     *
                     * @param loadedTrips danh sách chuyến lấy từ Firestore
                     */
                    @Override
                    public void onSuccess(List<Trip> loadedTrips) {
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
                     * Hiển thị lỗi thật khi không tải được danh sách.
                     *
                     * @param error lỗi truy vấn từ repository
                     */
                    @Override
                    public void onError(Exception error) {
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
                }
        );
    }

    /**
     * Kết nối nút lọc chuyến sắp đi và lịch sử.
     */
    private void connectFilterButtons() {
        upcomingButton.setOnClickListener(
                new View.OnClickListener() {

                    /**
                     * Chuyển sang nhóm chuyến chưa hoàn thành hoặc chưa bị hủy.
                     *
                     * @param view nút Sắp đi được nhấn
                     */
                    @Override
                    public void onClick(View view) {
                        selectFilter(FILTER_UPCOMING);
                    }
                }
        );

        historyButton.setOnClickListener(
                new View.OnClickListener() {

                    /**
                     * Chuyển sang nhóm chuyến đã hoàn thành hoặc đã bị hủy.
                     *
                     * @param view nút Lịch sử được nhấn
                     */
                    @Override
                    public void onClick(View view) {
                        selectFilter(FILTER_HISTORY);
                    }
                }
        );
    }

    /**
     * Cập nhật bộ lọc đang chọn và hiển thị lại dữ liệu đã tải thành công.
     *
     * @param requestedFilter bộ lọc mà người dùng chọn
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
     * Cập nhật màu hai nút để thể hiện bộ lọc đang được chọn.
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
     * Lọc danh sách theo trạng thái được lưu trong Firestore.
     * <p>
     * Không suy ra trạng thái COMPLETED từ thời gian khởi hành.
     */
    private void displayFilteredTrips() {
        List<Trip> filteredTrips = new ArrayList<>();

        for (Trip currentTrip : allTrips) {
            String tripStatus = currentTrip.getStatus();

            String normalizedStatus = tripStatus == null
                    ? ""
                    : tripStatus.trim().toUpperCase(Locale.ROOT);

            boolean isHistoryTrip =
                    normalizedStatus.equals("COMPLETED")
                            || normalizedStatus.equals("CANCELLED");

            if (selectedFilter == FILTER_UPCOMING && !isHistoryTrip) {
                filteredTrips.add(currentTrip);
            } else if (selectedFilter == FILTER_HISTORY && isHistoryTrip) {
                filteredTrips.add(currentTrip);
            }
        }

        tripAdapter.replaceTrips(filteredTrips);

        loadingProgressBar.setVisibility(View.GONE);
        messageTextView.setVisibility(View.GONE);

        if (filteredTrips.isEmpty()) {
            tripListView.setVisibility(View.GONE);

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

            return;
        }

        tripListView.setVisibility(View.VISIBLE);
    }

    /**
     * Hiển thị trạng thái rỗng, đăng nhập hoặc lỗi tải dữ liệu.
     *
     * @param message nội dung cần hiển thị
     * @param canRetry cho biết người dùng có thể chạm để tải lại hay không
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

                        /**
                         * Thực hiện tải lại khi người dùng chạm thông báo lỗi.
                         *
                         * @param view thông báo lỗi được nhấn
                         */
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

    /**
     * Kết nối nút đăng chuyến với biểu mẫu đăng chuyến hiện có.
     */
    private void connectPostTripButton() {
        postTripButton.setOnClickListener(
                new View.OnClickListener() {

                    /**
                     * Mở biểu mẫu đăng chuyến.
                     *
                     * @param view nút đăng chuyến được nhấn
                     */
                    @Override
                    public void onClick(View view) {
                        Intent postTripIntent = new Intent(
                                DriverTripsActivity.this,
                                PostTripActivity.class
                        );

                        startActivity(postTripIntent);
                    }
                }
        );
    }
}
