package com.example.grabshare.activity.driver;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.repository.search.SearchTripPolicy;
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

/** Hiển thị chi tiết và phản hồi yêu cầu đi nhờ của chủ xe. */
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
    private TextView dropoffText;
    private TextView routeNoticeText;
    private TextView contributionDetailsText;
    private TextView fullTripCostText;
    private TextView decisionNoticeText;
    private TextView messageText;
    private TextView costText;
    private TextView statusText;
    private AppCompatButton acceptButton;
    private AppCompatButton rejectButton;

    private DriverRequestRepository requestRepository;
    private DriverRequestDecisionViewModel decisionViewModel;
    private String requestId;
    private String tripId;
    private DriverRequestItem currentItem;
    private boolean isLoading;
    private boolean isResponding;
    private boolean requestDetailsValid;

    /** Khởi tạo giao diện và khôi phục ID yêu cầu sau khi xoay màn hình. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_request_detail);

        initializeViews();

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        requestRepository = new DriverRequestRepository(firestore, firebaseAuth);
        decisionViewModel = new ViewModelProvider(this).get(DriverRequestDecisionViewModel.class);

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
        hideActionButtons();
        statusText.setText("Đang tải yêu cầu...");
        notificationsButton.setVisibility(View.GONE);

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Thiếu mã yêu cầu.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        requestId = requestId.trim();
        decisionViewModel.getState().observe(this,
                new Observer<DriverRequestDecisionViewModel.State>() {
                    /** Observer mới tiếp nhận cùng tác vụ sau xoay, không gửi lại quyết định. */
                    @Override
                    public void onChanged(DriverRequestDecisionViewModel.State state) {
                        renderDecisionState(state);
                    }
                });
    }

    /** Đọc lại yêu cầu từ máy chủ mỗi khi màn hình xuất hiện. */
    @Override
    protected void onResume() {
        super.onResume();
        renderDecisionState(decisionViewModel.getState().getValue());
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
        dropoffText = findViewById(R.id.request_dropoff_value);
        routeNoticeText = findViewById(R.id.request_route_notice);
        contributionDetailsText = findViewById(R.id.request_contribution_details);
        fullTripCostText = findViewById(R.id.request_full_trip_cost);
        decisionNoticeText = findViewById(R.id.request_decision_notice);
        messageText = findViewById(R.id.request_message);
        costText = findViewById(R.id.request_cost);
        statusText = findViewById(R.id.request_status);
        acceptButton = findViewById(R.id.primary_action);
        rejectButton = findViewById(R.id.secondary_action);
    }

    /** Kết nối quay lại và các thao tác chấp nhận/từ chối. */
    private void connectEvents() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDecision("ACCEPTED");
            }
        });

        rejectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDecision("REJECTED");
            }
        });
    }

    /** Tải yêu cầu và dữ liệu chuyến liên quan từ repository. */
    private void loadRequest() {
        if (isLoading || isResponding || requestId == null || requestId.trim().isEmpty()) {
            return;
        }

        isLoading = true;
        requestDetailsValid = false;
        statusText.setText("Đang tải yêu cầu…");
        hideActionButtons();
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
                        hideActionButtons();
                        statusText.setText("Không thể tải yêu cầu");
                        Toast.makeText(
                                RequestDetailActivity.this,
                                readableError(error),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    /** Hiển thị dữ liệu thật và chỉ mở hành động khi trạng thái còn hợp lệ. */
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
            timeText.setText("Bạn khởi hành: " + formatDepartureTime(trip.getDepartureAt()));
            fullTripCostText.setText("Mức tham khảo toàn tuyến hiện tại: "
                    + formatCost(trip.getCostVnd()));
        } else {
            routeStartText.setText("Không còn dữ liệu chuyến");
            routeEndText.setText("");
            timeText.setText("Không có dữ liệu giờ khởi hành");
            fullTripCostText.setText("Không còn dữ liệu mức tham khảo toàn tuyến hiện tại.");
        }

        pickupText.setText(getPlaceName(request.getPickup(), "Chưa có điểm đón"));
        renderPassengerJourney(request, trip);
        renderConfirmedContribution(request);
        String message = request.getMessage();
        messageText.setText(message == null || message.trim().isEmpty()
                ? "Không có lời nhắn."
                : message.trim());

        String statusMessage = "Trạng thái yêu cầu: "
                + formatRequestStatus(request.getStatus());
        if ("PENDING".equals(request.getStatus())) {
            if (trip == null || trip.getDepartureAt() == null) {
                statusMessage += " · Không thể xác thực giờ khởi hành.";
            } else if (!isDepartureInFuture(trip)) {
                statusMessage += " · Đã qua giờ khởi hành.";
            } else if (!isTripOpen(trip) && !isTripFull(trip)) {
                statusMessage += " · Chuyến không còn nhận phản hồi.";
            }
        }
        statusText.setText(statusMessage);
        configureActionButtons(item);
    }

    /** Hiện nút theo trạng thái yêu cầu và tình trạng chuyến đã tải từ máy chủ. */
    private void configureActionButtons(DriverRequestItem item) {
        TripRequest request = item.getRequest();
        Trip trip = item.getTrip();

        boolean isPending = request != null && "PENDING".equals(request.getStatus());
        boolean departureIsFuture = isDepartureInFuture(trip);
        boolean tripIsOpen = isTripOpen(trip);
        boolean tripCanBeRejected = tripIsOpen || isTripFull(trip);

        boolean canAccept = isPending && departureIsFuture && tripIsOpen && requestDetailsValid;
        boolean canReject = isPending && departureIsFuture && tripCanBeRejected;

        acceptButton.setVisibility(View.GONE);
        if (isPending && departureIsFuture && tripIsOpen) {
            acceptButton.setVisibility(View.VISIBLE);
        }
        rejectButton.setVisibility(View.GONE);
        if (canReject) {
            rejectButton.setVisibility(View.VISIBLE);
        }
        acceptButton.setEnabled(canAccept && !isResponding && !isLoading);
        rejectButton.setEnabled(canReject && !isResponding && !isLoading);
    }

    /** Chỉ yêu cầu thiếu hẳn C mới dùng D của đúng chuyến theo hợp đồng dữ liệu cũ. */
    private void renderPassengerJourney(TripRequest request, Trip trip) {
        requestDetailsValid = false;
        try {
            if (!SearchTripPolicy.validPlace(request.getPickup())) {
                throw new IllegalArgumentException("Điểm đón không hợp lệ.");
            }
            Place dropoff = RequestRouteResolver.resolveDropoff(request, trip);
            dropoffText.setText(getPlaceName(dropoff, "Chưa có điểm xuống"));
            routeNoticeText.setText("");
            routeNoticeText.setVisibility(View.GONE);
            if (!request.hasDropoffField()) {
                routeNoticeText.setText("Yêu cầu cũ chưa lưu điểm xuống riêng; đang dùng điểm đến"
                        + " của chuyến theo quy ước dữ liệu cũ.");
                routeNoticeText.setVisibility(View.VISIBLE);
            }
            requestDetailsValid = true;
        } catch (IllegalArgumentException error) {
            dropoffText.setText("Chưa xác nhận được điểm xuống");
            routeNoticeText.setText("Dữ liệu đón/trả không hợp lệ. Chưa thể chấp nhận yêu cầu này.");
            routeNoticeText.setVisibility(View.VISIBLE);
        }
    }

    /** Hiển thị snapshot của đúng yêu cầu; không lấy giá chuyến hiện tại bù tiền của khách. */
    private void renderConfirmedContribution(TripRequest request) {
        try {
            ContributionSnapshot contribution = ContributionCalculator.readConfirmed(request);
            if (contribution == null) {
                costText.setText("Chưa xác nhận đóng góp");
                contributionDetailsText.setText("Yêu cầu cũ chưa lưu khoản đóng góp đã xác nhận."
                        + " Không suy ra tiền khách từ mức tham khảo toàn tuyến.");
                return;
            }
            costText.setText(formatCost(contribution.getAmountVnd()));
            NumberFormat distanceFormatter = NumberFormat.getNumberInstance(
                    Locale.forLanguageTag("vi-VN"));
            distanceFormatter.setMaximumFractionDigits(2);
            contributionDetailsText.setText("Chặng khách đi P → C: "
                    + distanceFormatter.format(contribution.getPassengerDistanceMeters() / 1000.0)
                    + " km\nMức toàn tuyến lúc khách xác nhận: "
                    + formatCost(contribution.getFullTripCostVnd())
                    + "\nKhoản khách đã xác nhận khi gửi; chưa phải xác nhận đã thanh toán.");
        } catch (IllegalArgumentException error) {
            requestDetailsValid = false;
            costText.setText("Khoản đóng góp không hợp lệ");
            contributionDetailsText.setText("Không đọc được khoản khách đã xác nhận."
                    + " Chưa thể chấp nhận yêu cầu này.");
        }
    }

    /** Xác nhận trước khi ghi trạng thái mới lên Firestore. */
    private void confirmDecision(final String targetStatus) {
        if (currentItem == null || isLoading || isResponding) {
            return;
        }

        TripRequest request = currentItem.getRequest();
        if (request == null || !"PENDING".equals(request.getStatus())) {
            Toast.makeText(this, "Yêu cầu không còn chờ duyệt.", Toast.LENGTH_LONG).show();
            loadRequest();
            return;
        }

        boolean isAccepting = "ACCEPTED".equals(targetStatus);
        if (isAccepting && !requestDetailsValid) {
            return;
        }
        String title = "Từ chối yêu cầu?";
        String message = "Yêu cầu này sẽ được chuyển sang trạng thái Đã từ chối.";
        String confirmText = "Từ chối";
        if (isAccepting) {
            title = "Chấp nhận yêu cầu?";
            confirmText = "Chấp nhận";
            message = "Đón: " + pickupText.getText() + "\nTrả: " + dropoffText.getText()
                    + "\nKhách đóng góp: " + costText.getText()
                    + "\n\nChấp nhận sẽ giữ chỗ cho khách này và dừng nhận thêm khách."
                    + " Hai bên tự thanh toán; thao tác này không thu tiền.";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Quay lại", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(confirmText, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        submitDecision(targetStatus);
                    }
                })
                .show();
    }

    /** Gửi phản hồi; repository xác minh lại điều kiện trong transaction nguyên tử. */
    private void submitDecision(final String targetStatus) {
        if (isLoading || isResponding || currentItem == null) {
            return;
        }

        decisionViewModel.submit(requestId, "ACCEPTED".equals(targetStatus));
    }

    /** Hiện tiến trình, chỉ báo kết quả khi người dùng đang ở màn này. */
    private void renderDecisionState(DriverRequestDecisionViewModel.State state) {
        if (state == null) {
            return;
        }
        isResponding = state.busy;
        decisionNoticeText.setVisibility(View.GONE);
        if (state.busy) {
            setActionButtonsEnabled(false);
            acceptButton.setText("Đang xử lý…");
            statusText.setText(state.message);
            return;
        }
        acceptButton.setText("Chấp nhận");
        if (!state.message.isEmpty()) {
            decisionNoticeText.setText(state.message);
            decisionNoticeText.setVisibility(View.VISIBLE);
        }
        if (state.completed && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            String message = state.message;
            decisionViewModel.acknowledgeResult();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            loadRequest();
        }
    }

    /** Khóa cả hai nút trong lúc đang gửi quyết định lên máy chủ. */
    private void setActionButtonsEnabled(boolean enabled) {
        acceptButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
    }

    /** Ẩn hai nút cho đến khi dữ liệu mới được xác thực. */
    private void hideActionButtons() {
        acceptButton.setEnabled(false);
        rejectButton.setEnabled(false);
        acceptButton.setVisibility(View.GONE);
        rejectButton.setVisibility(View.GONE);
    }

    /** Kiểm tra sơ bộ thời gian; repository vẫn kiểm tra lại khi ghi. */
    private boolean isDepartureInFuture(Trip trip) {
        return trip != null
                && trip.getDepartureAt() != null
                && trip.getDepartureAt().toDate().getTime() > System.currentTimeMillis();
    }

    /** Xác minh chuyến đang OPEN theo cấu trúc trạng thái đã thống nhất. */
    private boolean isTripOpen(Trip trip) {
        return trip != null
                && "OPEN".equals(trip.getStatus())
                && trip.getAvailableSeats() != null
                && trip.getAvailableSeats().longValue() == 1L
                && (trip.getAcceptedRequestId() == null
                || trip.getAcceptedRequestId().trim().isEmpty());
    }

    /** Xác minh chuyến FULL để vẫn cho phép từ chối các yêu cầu chờ còn lại. */
    private boolean isTripFull(Trip trip) {
        return trip != null
                && "FULL".equals(trip.getStatus())
                && trip.getAvailableSeats() != null
                && trip.getAvailableSeats().longValue() == 0L
                && trip.getAcceptedRequestId() != null
                && !trip.getAcceptedRequestId().trim().isEmpty();
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

    /** Hiện lỗi tải hoặc phản hồi dữ liệu. */
    private String readableError(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "Không thể hoàn tất thao tác. Kiểm tra kết nối và thử lại.";
        }
        return error.getMessage();
    }
}
