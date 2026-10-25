package com.example.grabshare.activity.driver;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.presentation.TripListState;
import com.example.grabshare.presentation.PlaceNameFormatter;
import com.example.grabshare.adapter.driver.RequestAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.DriverRequestItem;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.driver.DriverRequestRepository;
import com.example.grabshare.repository.driver.DriverTripRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Danh sách yêu cầu đi nhờ của tài xế đang đăng nhập. */
public class DriverRequestsActivity extends AppCompatActivity
        implements RequestAdapter.OnRequestClickListener {

    private static final String STATE_TRIP_ID = "driver_requests_trip_id";
    private static final String STATE_SELECTED_TAB = "driver_requests_selected_tab";
    private static final int TAB_PENDING = 0;
    private static final int TAB_RESPONDED = 1;

    private ImageButton backButton;
    private TextView routeStartText;
    private TextView routeEndText;
    private TextView summaryText;
    private LinearLayout requestList;
    private AppCompatButton pendingTabButton;
    private AppCompatButton respondedTabButton;

    private DriverRequestRepository requestRepository;
    private DriverTripRepository tripRepository;
    private RequestAdapter adapter;

    private String tripId;
    private int selectedTab = TAB_PENDING;
    private boolean isLoading;
    private boolean loadFailed;

    /** Khởi tạo giao diện, phục hồi trạng thái và kết nối dữ liệu. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_requests);

        initializeViews();
        initializeRepositories();
        initializeAdapter();

        if (savedInstanceState != null) {
            tripId = savedInstanceState.getString(
                    STATE_TRIP_ID,
                    getIntent().getStringExtra(NavigationKeys.TRIP_ID)
            );
            selectedTab = savedInstanceState.getInt(
                    STATE_SELECTED_TAB,
                    TAB_PENDING
            );
        } else if (getIntent() != null) {
            tripId = getIntent().getStringExtra(NavigationKeys.TRIP_ID);
        }

        connectEvents();
        updateTabAppearance();

        // XML ban đầu chứa card minh họa; thay chúng bằng yêu cầu thật từ Firestore.
        requestList.removeAllViews();
        summaryText.setText("Đang tải yêu cầu...");

        if (tripId != null && !tripId.trim().isEmpty()) {
            tripId = tripId.trim();
            loadTripSummary();
        } else {
            routeStartText.setText("Các chuyến của bạn");
            routeEndText.setText("Yêu cầu đi nhờ");
            summaryText.setText("Các yêu cầu gửi đến những chuyến bạn đăng");
        }
    }

    /** Tải lại danh sách khi quay lại màn hình. */
    @Override
    protected void onResume() {
        super.onResume();
        loadRequests();
    }

    /** Lưu ID chuyến và tab đang chọn khi Activity được tạo lại. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_TRIP_ID, tripId);
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }

    /** Ánh xạ các view trong screen_requests.xml. */
    private void initializeViews() {
        backButton = findViewById(R.id.header_back);
        routeStartText = findViewById(R.id.route_start);
        routeEndText = findViewById(R.id.route_end);
        summaryText = findViewById(R.id.requests_summary);
        requestList = findViewById(R.id.request_list);
        pendingTabButton = findViewById(R.id.requests_pending);
        respondedTabButton = findViewById(R.id.requests_accepted);
    }

    /** Khởi tạo repository sử dụng các instance Firebase của ứng dụng. */
    private void initializeRepositories() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        requestRepository = new DriverRequestRepository(firestore, firebaseAuth);
        tripRepository = new DriverTripRepository(firestore, firebaseAuth);
    }

    /** Tạo adapter kết nối item yêu cầu với thao tác mở chi tiết. */
    private void initializeAdapter() {
        adapter = new RequestAdapter(this, this);
    }

    /** Kết nối nút quay lại và hai bộ lọc trạng thái. */
    private void connectEvents() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Nút chuông chưa thuộc phạm vi Task 5, tránh mở một Activity chưa triển khai.
        findViewById(R.id.header_action).setVisibility(View.GONE);

        pendingTabButton.setClickable(true);
        pendingTabButton.setFocusable(true);
        pendingTabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedTab = TAB_PENDING;
                updateTabAppearance();
                renderRequests();
            }
        });

        respondedTabButton.setText("Tất cả");
        respondedTabButton.setClickable(true);
        respondedTabButton.setFocusable(true);
        respondedTabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedTab = TAB_RESPONDED;
                updateTabAppearance();
                renderRequests();
            }
        });
    }

    /** Đọc thông tin tuyến và giờ của chuyến nếu màn hình được mở từ một chuyến cụ thể. */
    private void loadTripSummary() {
        tripRepository.loadTripForManagement(
                tripId,
                new RepositoryCallback<Trip>() {
                    @Override
                    public void onSuccess(Trip trip) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        routeStartText.setText(getPlaceName(
                                trip.getOrigin(),
                                "Chưa có điểm đi"
                        ));
                        routeEndText.setText(getPlaceName(
                                trip.getDestination(),
                                "Chưa có điểm đến"
                        ));
                        summaryText.setText(buildTripSummary(trip));
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        routeStartText.setText("Không tải được điểm đi");
                        routeEndText.setText("Không tải được điểm đến");
                        summaryText.setText(readableError(error));
                    }
                }
        );
    }

    /** Tải yêu cầu của tài xế hiện tại từ repository. */
    private void loadRequests() {
        if (isLoading) {
            return;
        }

        isLoading = true;
        loadFailed = false;
        requestList.removeAllViews();
        TextView loadingText = new TextView(this);
        loadingText.setText("Đang tải yêu cầu…");
        requestList.addView(loadingText);
        requestRepository.loadRequestsForCurrentDriver(
                tripId,
                new RepositoryCallback<List<DriverRequestItem>>() {
                    @Override
                    public void onSuccess(List<DriverRequestItem> items) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        isLoading = false;
                        loadFailed = false;
                        adapter.replaceItems(items);
                        renderRequests();

                        if (tripId == null || tripId.trim().isEmpty()) {
                            summaryText.setText(
                                    items.size() + " yêu cầu trên các chuyến bạn đăng"
                            );
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        isLoading = false;
                        loadFailed = true;
                        adapter.replaceItems(new ArrayList<DriverRequestItem>());
                        summaryText.setText("Không thể tải yêu cầu");
                        showRequestLoadError();
                        Toast.makeText(
                                DriverRequestsActivity.this,
                                readableError(error),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    /** Cập nhật trạng thái trực quan của tab đang chọn. */
    private void updateTabAppearance() {
        styleTab(pendingTabButton, selectedTab == TAB_PENDING);
        styleTab(respondedTabButton, selectedTab == TAB_RESPONDED);
    }

    /** Tab đang chọn dùng màu và selected, không giả thành nút bị vô hiệu hóa. */
    private void styleTab(AppCompatButton button, boolean selected) {
        int background = R.color.white;
        int text = R.color.ink;
        if (selected) {
            background = R.color.primary;
            text = R.color.white;
        }
        button.setEnabled(true);
        button.setSelected(selected);
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(background)));
        button.setTextColor(getColor(text));
    }

    /** Hiển thị item theo trạng thái; mọi trạng thái không phải PENDING nằm ở tab đã phản hồi. */
    private void renderRequests() {
        if (isLoading) {
            return;
        }
        if (loadFailed) {
            showRequestLoadError();
            return;
        }
        requestList.removeAllViews();
        int visibleCount = 0;

        for (int index = 0; index < adapter.getCount(); index++) {
            DriverRequestItem item = adapter.getItem(index);
            String status = TripListState.passengerStatus(item.getRequest(), item.getTrip(), System.currentTimeMillis());
            boolean isPending = "PENDING".equals(status);

            if ((selectedTab == TAB_PENDING && isPending)
                    || selectedTab == TAB_RESPONDED) {
                requestList.addView(adapter.getView(index, null, requestList));
                visibleCount++;
            }
        }

        if (visibleCount == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText(selectedTab == TAB_PENDING
                    ? "Chưa có yêu cầu nào đang chờ duyệt."
                    : "Chưa có yêu cầu đi nhờ.");
            emptyText.setTextColor(getColor(R.color.muted));
            emptyText.setTextSize(14);
            emptyText.setPadding(12, 28, 12, 28);
            requestList.addView(emptyText);
        }
    }

    /** Đổi tab không được biến lỗi tải thành kết luận chưa có yêu cầu. */
    private void showRequestLoadError() {
        requestList.removeAllViews();
        TextView message = new TextView(this);
        message.setText("Chưa tải được yêu cầu. Kiểm tra kết nối rồi thử lại.");
        message.setTextColor(getColor(R.color.muted));
        requestList.addView(message);
        AppCompatButton retry = new AppCompatButton(this);
        retry.setText("Thử tải lại");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clickedView) {
                loadRequests();
            }
        });
        requestList.addView(retry);
    }

    /** Mở chi tiết bằng ID Firestore, không truyền nguyên đối tượng qua Intent. */
    @Override
    public void onOpenRequest(DriverRequestItem item) {
        if (item == null || item.getRequest() == null
                || item.getRequest().getId() == null
                || item.getRequest().getId().trim().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã yêu cầu.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, RequestDetailActivity.class);
        intent.putExtra(NavigationKeys.REQUEST_ID, item.getRequest().getId());
        intent.putExtra(NavigationKeys.TRIP_ID, item.getRequest().getTripId());
        startActivity(intent);
    }

    /** Lấy tên địa điểm hoặc nhãn thay thế. */
    private String getPlaceName(com.example.grabshare.model.Place place, String fallback) {
        if (place == null || place.getName() == null || place.getName().trim().isEmpty()) {
            return fallback;
        }
        return PlaceNameFormatter.format(place.getName());
    }

    /** Tạo tóm tắt chuyến bằng thời gian và số chỗ hiện tại. */
    private String buildTripSummary(Trip trip) {
        String departure = "Chưa có giờ khởi hành";
        if (trip.getDepartureAt() != null) {
            SimpleDateFormat formatter = new SimpleDateFormat(
                    "dd/MM/yyyy · HH:mm",
                    Locale.forLanguageTag("vi-VN")
            );
            departure = formatter.format(trip.getDepartureAt().toDate());
        }

        String seats = trip.getAvailableSeats() == null
                ? "Chưa rõ số chỗ"
                : trip.getAvailableSeats() + " chỗ trống";
        return departure + " · " + seats;
    }

    /** Hiển thị lỗi dễ đọc thay cho thông báo trống. */
    private String readableError(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "Không thể tải dữ liệu. Kiểm tra kết nối và thử lại.";
        }
        return error.getMessage();
    }
}
