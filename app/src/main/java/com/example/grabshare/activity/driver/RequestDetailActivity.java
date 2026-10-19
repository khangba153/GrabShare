package com.example.grabshare.activity.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.activity.notifications.NotificationsActivity;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.DriverRequestItem;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.driver.DriverRequestRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Hiển thị chi tiết yêu cầu đi nhờ dành cho chủ xe. */
public class RequestDetailActivity extends AppCompatActivity {

    private static final String STATE_REQUEST_ID = "request_detail_request_id";
    private static final String STATE_TRIP_ID = "request_detail_trip_id";

    private ImageButton backButton;
    private ImageButton notificationsButton;
    private TextView passengerNameText;
    private TextView passengerMetaText;
    private TextView passengerBadgeText;
    private TextView routeStartText;
    private TextView routeEndText;
    private TextView timeText;
    private TextView pickupText;
    private TextView messageText;
    private TextView costText;
    private TextView statusText;
    private AppCompatButton acceptButton;
    private AppCompatButton rejectButton;

    private DriverRequestRepository requestRepository;
    private String requestId;
    private String tripId;
    private DriverRequestItem currentItem;
    private boolean isLoading;

    /** Khởi tạo giao diện và khôi phục ID yêu cầu sau khi xoay màn hình. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_request_detail);

        initializeViews();
        requestRepository = new DriverRequestRepository(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance()
        );

        if (savedInstanceState != null) {
            requestId = savedInstanceState.getString(
                    STATE_REQUEST_ID,
                    getIntent().getStringExtra(NavigationKeys.REQUEST_ID)
            );
            tripId = savedInstanceState.getString(
                    STATE_TRIP_ID,
                    getIntent().getStringExtra(NavigationKeys.TRIP_ID)
            );
        } else if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
            tripId = getIntent().getStringExtra(NavigationKeys.TRIP_ID);
        }

        connectEvents();
        setActionsEnabled(false);
        statusText.setText("Đang tải yêu cầu...");
        notificationsButton.setVisibility(View.GONE);

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Thiếu mã yêu cầu.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        requestId = requestId.trim();
    }

    /** Đọc lại yêu cầu từ máy chủ mỗi khi màn hình xuất hiện. */
    @Override
    protected void onResume() {
        super.onResume();
        loadRequest();
    }

    /** Lưu các ID cần thiết để dựng lại đúng chi tiết. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_REQUEST_ID, requestId);
        outState.putString(STATE_TRIP_ID, tripId);
        super.onSaveInstanceState(outState);
    }

    /** Ánh xạ view từ screen_request_detail.xml. */
    private void initializeViews() {
        backButton = findViewById(R.id.header_back);
        notificationsButton = findViewById(R.id.header_action);
        passengerNameText = findViewById(R.id.driver_name);
        passengerMetaText = findViewById(R.id.driver_meta);
        passengerBadgeText = findViewById(R.id.verified_badge);
        routeStartText = findViewById(R.id.route_start);
        routeEndText = findViewById(R.id.route_end);
        timeText = findViewById(R.id.request_time);
        pickupText = findViewById(R.id.request_pickup_value);
        messageText = findViewById(R.id.request_message);
        costText = findViewById(R.id.request_cost);
        statusText = findViewById(R.id.request_status);
        acceptButton = findViewById(R.id.primary_action);
        rejectButton = findViewById(R.id.secondary_action);
    }

    /** Kết nối quay lại và giữ hai nút phản hồi ở trạng thái chưa triển khai Task 6. */
    private void connectEvents() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        notificationsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(
                        RequestDetailActivity.this,
                        NotificationsActivity.class
                ));
            }
        });

        acceptButton.setVisibility(View.GONE);
        rejectButton.setVisibility(View.GONE);
    }

    /** Tải yêu cầu và dữ liệu chuyến liên quan từ repository. */
    private void loadRequest() {
        if (isLoading || requestId == null || requestId.trim().isEmpty()) {
            return;
        }

        isLoading = true;
        setActionsEnabled(false);
        requestRepository.loadRequestForCurrentDriver(
                requestId,
                new RepositoryCallback<DriverRequestItem>() {
                    @Override
                    public void onSuccess(DriverRequestItem item) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        isLoading = false;
                        currentItem = item;
                        if (item.getRequest() != null) {
                            tripId = item.getRequest().getTripId();
                        }
                        renderRequest(item);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        isLoading = false;
                        currentItem = null;
                        statusText.setText("Không thể tải yêu cầu");
                        Toast.makeText(
                                RequestDetailActivity.this,
                                readableError(error),
                                Toast.LENGTH_LONG
                        ).show();
                        setActionsEnabled(false);
                    }
                }
        );
    }

    /** Hiển thị hồ sơ công khai, tuyến, thời gian, điểm đón và lời nhắn. */
    private void renderRequest(DriverRequestItem item) {
        TripRequest request = item.getRequest();
        Trip trip = item.getTrip();

        String passengerName = "Hành khách";
        String vehicleName = "";
        if (item.getPassengerProfile() != null) {
            String storedName = item.getPassengerProfile().getDisplayName();
            if (storedName != null && !storedName.trim().isEmpty()) {
                passengerName = storedName.trim();
            }
            String storedVehicle = item.getPassengerProfile().getVehicleName();
            if (storedVehicle != null && !storedVehicle.trim().isEmpty()) {
                vehicleName = storedVehicle.trim();
            }
        }

        passengerNameText.setText(passengerName);
        passengerMetaText.setText(vehicleName.isEmpty()
                ? "Người gửi yêu cầu đi nhờ"
                : vehicleName);
        passengerBadgeText.setVisibility(View.GONE);
        findViewById(R.id.driver_avatar).setVisibility(View.GONE);

        if (trip != null) {
            routeStartText.setText(getPlaceName(trip.getOrigin(), "Chưa có điểm đi"));
            routeEndText.setText(getPlaceName(trip.getDestination(), "Chưa có điểm đến"));
            timeText.setText(formatDepartureTime(trip.getDepartureAt()));
            costText.setText(formatCost(trip.getCostVnd()));
        } else {
            routeStartText.setText("Không còn dữ liệu chuyến");
            routeEndText.setText("");
            timeText.setText("Không có dữ liệu giờ khởi hành");
            costText.setText("Không có dữ liệu chi phí");
        }

        pickupText.setText(getPlaceName(request.getPickup(), "Chưa có điểm đón"));
        String message = request.getMessage();
        messageText.setText(message == null || message.trim().isEmpty()
                ? "Không có lời nhắn."
                : message.trim());
        statusText.setText("Trạng thái yêu cầu: " + formatRequestStatus(request.getStatus()));
    }

    /** Khóa các nút hành động trong màn chỉ đọc của Task 5. */
    private void setActionsEnabled(boolean enabled) {
        acceptButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
    }

    /** Lấy tên địa điểm hoặc nhãn thay thế. */
    private String getPlaceName(Place place, String fallback) {
        if (place == null || place.getName() == null || place.getName().trim().isEmpty()) {
            return fallback;
        }
        return place.getName().trim();
    }

    /** Định dạng thời gian khởi hành theo múi giờ thiết bị. */
    private String formatDepartureTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "Chưa có giờ khởi hành";
        }
        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd/MM/yyyy · HH:mm",
                Locale.forLanguageTag("vi-VN")
        );
        return formatter.format(timestamp.toDate());
    }

    /** Định dạng chi phí đóng góp. */
    private String formatCost(Long cost) {
        if (cost == null) {
            return "Không có dữ liệu chi phí";
        }
        if (cost == 0L) {
            return "Miễn phí";
        }
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
                .format(cost) + " đ";
    }

    /** Chuyển mã trạng thái sang nhãn tiếng Việt. */
    private String formatRequestStatus(String status) {
        if ("PENDING".equals(status)) {
            return "Chờ duyệt";
        }
        if ("ACCEPTED".equals(status)) {
            return "Đã chấp nhận";
        }
        if ("REJECTED".equals(status)) {
            return "Đã từ chối";
        }
        if ("CANCELLED".equals(status)) {
            return "Đã hủy";
        }
        return "Không rõ";
    }

    /** Hiện lỗi tải dữ liệu. */
    private String readableError(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "Không thể tải dữ liệu. Kiểm tra kết nối và thử lại.";
        }
        return error.getMessage();
    }
}
