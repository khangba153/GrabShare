package com.example.grabshare.activity.passenger;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.presentation.RideProgressGuidance;
import com.example.grabshare.presentation.PlaceNameFormatter;
import com.example.grabshare.presentation.MainNavigation;
import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.SingleRequestDetails;
import com.example.grabshare.repository.passenger.RequestCancellationRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity hiển thị màn Chuyến sắp đi (UpcomingActivity) của Hành khách (Thuận-3).
 * Quản lý thông tin chuyến đi đã được nhận, xử lý hủy trước giờ đi (Thuận-5)
 * và tự động chuyển trạng thái khi chủ xe hoàn thành hoặc hủy chuyến.
 */
public class UpcomingActivity extends AppCompatActivity {

    private ImageButton headerBack;
    private TextView headerTitle;
    private TextView upcomingStatus;
    private TextView upcomingDescription;

    private Button primaryAction;
    private Button secondaryAction;
    private Button tripCall;

    // View thông tin chi tiết
    private TextView driverName;
    private TextView driverMeta;
    private TextView tripTime;
    private TextView routeStart;
    private TextView routeEnd;
    private TextView tripCost;

    private PassengerTripsRepository tripsRepository;
    private RequestCancellationRepository cancellationRepository;
    private ListenerRegistration singleRequestListener;

    private String requestId;
    private String currentUserId;
    private SingleRequestDetails currentDetails;
    private boolean hasNavigated = false;
    private PassengerSessionGuard sessionGuard;
    private boolean cancellationInFlight;
    private AlertDialog cancelDialog;
    private boolean fullAddressesVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_upcoming);
        if (savedInstanceState != null) {
            fullAddressesVisible = savedInstanceState.getBoolean("full_addresses_visible", false);
        }

        // Đọc requestId từ Intent Extra theo NavigationKeys.REQUEST_ID
        if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        }

        // Kiểm tra Auth trước khi khởi tạo listener
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem thông tin.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        if (savedInstanceState != null
                && !currentUserId.equals(savedInstanceState.getString("passenger_expected_uid"))) {
            finish();
            return;
        }
        sessionGuard = new PassengerSessionGuard(this);

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Yêu cầu không hợp lệ hoặc thiếu mã nhận dạng.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initRepositories();
        initViews();
        MainNavigation.bindNotifications(this, R.id.header_action);
        setupListeners();
    }

    private void initRepositories() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        tripsRepository = new PassengerTripsRepository(firestore);
        cancellationRepository = new RequestCancellationRepository(firestore);
    }

    private void initViews() {
        headerBack = findViewById(R.id.header_back);
        headerTitle = findViewById(R.id.header_title);
        upcomingStatus = findViewById(R.id.upcoming_status);
        upcomingDescription = findViewById(R.id.upcoming_description);

        primaryAction = findViewById(R.id.primary_action);
        secondaryAction = findViewById(R.id.secondary_action);
        tripCall = findViewById(R.id.trip_call);

        if (headerBack != null) {
            headerBack.setClickable(true);
            headerBack.setFocusable(true);
        }
        if (headerTitle != null) {
            headerTitle.setText("Chuyến sắp đi");
        }

        if (primaryAction != null) {
            primaryAction.setClickable(true);
            primaryAction.setFocusable(true);
            primaryAction.setVisibility(View.GONE);
        }

        if (secondaryAction != null) {
            secondaryAction.setClickable(true);
            secondaryAction.setFocusable(true);
        }

        // Ẩn nút liên hệ giả theo review "Ẩn nút nếu liên hệ chưa thuộc scope"
        if (tripCall != null) {
            tripCall.setVisibility(View.GONE);
        }

        // View chi tiết
        driverName = findViewById(R.id.driver_name);
        driverMeta = findViewById(R.id.driver_meta);
        tripTime = findViewById(R.id.trip_time);
        routeStart = findViewById(R.id.route_start);
        routeEnd = findViewById(R.id.route_end);
        tripCost = findViewById(R.id.trip_cost);
        updateAddressDisclosure();
        findViewById(R.id.passenger_route_toggle).setOnClickListener(new View.OnClickListener() {
            /** Địa chỉ đầy đủ chỉ mở theo nhu cầu, không lặp tuyến dài trong màn mặc định. */
            @Override
            public void onClick(View clickedView) {
                fullAddressesVisible = !fullAddressesVisible;
                updateAddressDisclosure();
            }
        });
    }

    /** Giữ tuyến rút gọn ở thẻ chính và cho phép mở nguyên văn địa chỉ đã lưu. */
    private void updateAddressDisclosure() {
        TextView routeSummary = findViewById(R.id.passenger_route_summary);
        TextView toggle = findViewById(R.id.passenger_route_toggle);
        routeSummary.setVisibility(View.GONE);
        toggle.setText("Xem địa chỉ đầy đủ");
        if (fullAddressesVisible) {
            routeSummary.setVisibility(View.VISIBLE);
            toggle.setText("Thu gọn địa chỉ");
        }
    }

    private void setupListeners() {
        if (headerBack != null) {
            headerBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (secondaryAction != null) {
            secondaryAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleSecondaryAction();
                }
            });
        }

        if (primaryAction != null) {
            primaryAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handlePrimaryAction();
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sessionGuard != null && sessionGuard.start(currentUserId)) {
            cancellationInFlight = false;
            currentDetails = null;
            if (primaryAction != null) {
                primaryAction.setVisibility(View.GONE);
            }
            if (secondaryAction != null) {
                secondaryAction.setEnabled(false);
            }
            startListeningRequest();
        }
    }

    @Override
    protected void onStop() {
        if (sessionGuard != null) {
            sessionGuard.stop();
        }
        if (cancelDialog != null) {
            cancelDialog.dismiss();
            cancelDialog = null;
        }
        if (singleRequestListener != null) {
            singleRequestListener.remove();
            singleRequestListener = null;
        }
        super.onStop();
    }

    /** Giữ UID kỳ vọng qua xoay và không tự gửi lại lệnh hủy. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("passenger_expected_uid", currentUserId);
        state.putBoolean("full_addresses_visible", fullAddressesVisible);
        super.onSaveInstanceState(state);
    }

    /**
     * Khởi tạo và lắng nghe thông tin chuyến đi bằng Realtime listener.
     */
    private void startListeningRequest() {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }

        if (singleRequestListener != null) {
            singleRequestListener.remove();
        }

        final int sessionToken = sessionGuard.token();
        singleRequestListener = tripsRepository.listenSingleRequest(requestId, new RepositoryCallback<SingleRequestDetails>() {
            /** Chỉ nhận dữ liệu cho đúng UID và thế hệ màn đang hoạt động. */
            @Override
            public void onSuccess(SingleRequestDetails details) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                if (details == null || details.getRequest() == null) {
                    Toast.makeText(UpcomingActivity.this, "Không tìm thấy thông tin chuyến đi.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                TripRequest request = details.getRequest();

                // Xác minh chính chủ request
                if (!currentUserId.equals(request.getPassengerId())) {
                    Toast.makeText(UpcomingActivity.this, "Bạn không có quyền xem chuyến đi này.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentDetails = details;
                updateUiForDetails(details);
                if (cancellationInFlight && secondaryAction != null) {
                    secondaryAction.setEnabled(false);
                }
            }

            /** Bỏ lỗi của listener đã dừng hoặc tài khoản cũ. */
            @Override
            public void onError(Exception error) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                currentDetails = null;
                upcomingStatus.setText("Chưa tải được chuyến");
                upcomingDescription.setText("Kiểm tra kết nối rồi thử lại.");
                secondaryAction.setVisibility(View.GONE);
                primaryAction.setText("Thử lại");
                primaryAction.setEnabled(true);
                primaryAction.setVisibility(View.VISIBLE);
                primaryAction.setOnClickListener(new View.OnClickListener() {
                    /** Thử đọc lại dữ liệu, không tự gửi hoặc xác nhận bất kỳ yêu cầu nào. */
                    @Override
                    public void onClick(View clickedView) {
                        primaryAction.setVisibility(View.GONE);
                        upcomingStatus.setText("Đang tải chuyến…");
                        startListeningRequest();
                    }
                });
            }
        });
    }

    /**
     * Predicate kiểm tra chính xác một chuyến đi đã ACCEPTED có hợp lệ hay không.
     * Bắt buộc: request.status == ACCEPTED, trip.status == FULL, availableSeats == 0 và acceptedRequestId == requestId.
     */
    private boolean isAcceptedRideValid(TripRequest request, Trip trip) {
        if (request == null || trip == null) return false;

        boolean isReqAccepted = "ACCEPTED".equalsIgnoreCase(request.getStatus());
        boolean isTripFull = "FULL".equalsIgnoreCase(trip.getStatus());
        boolean isSeatsZero = trip.getAvailableSeats() != null && trip.getAvailableSeats() == 0;
        boolean isPointerMatched = trip.getAcceptedRequestId() != null
                && requestId.equals(trip.getAcceptedRequestId().trim());

        return isReqAccepted && isTripFull && isSeatsZero && isPointerMatched;
    }

    /**
     * Cập nhật toàn bộ giao diện và trạng thái nút theo dữ liệu Realtime thu được.
     */
    private void updateUiForDetails(SingleRequestDetails details) {
        if (details == null) return;

        TripRequest request = details.getRequest();
        Trip trip = details.getTrip();
        PassengerRouteText.bind(findViewById(R.id.passenger_route_summary), request, trip);
        if (primaryAction != null) {
            primaryAction.setVisibility(View.VISIBLE);
            primaryAction.setOnClickListener(new View.OnClickListener() {
                /** Gắn lại hành động theo dữ liệu mới, không giữ nút Quay lại của trạng thái lỗi cũ. */
                @Override
                public void onClick(View clickedView) {
                    handlePrimaryAction();
                }
            });
        }
        RideProgressGuidance.bindPassenger(findViewById(R.id.ride_progress_guidance), request, trip);
        PublicProfile profile = details.getDriverProfile();

        bindDriverProfile(profile);
        bindTripAndRequestInfo(trip, request);

        if (tripCall != null) {
            tripCall.setVisibility(View.GONE);
        }

        if (trip == null) {
            if (upcomingStatus != null) upcomingStatus.setText("Không khả dụng");
            if (upcomingDescription != null) upcomingDescription.setText("Thông tin chuyến đi liên quan không tồn tại.");
            if (secondaryAction != null) secondaryAction.setVisibility(View.GONE);
            if (primaryAction != null) {
                primaryAction.setText("Quay lại");
                primaryAction.setEnabled(true);
                primaryAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
            return;
        }

        String requestStatus = (request.getStatus() != null) ? request.getStatus().toUpperCase() : "";
        String tripStatus = (trip.getStatus() != null) ? trip.getStatus().toUpperCase() : "";

        // Chuyển hướng nếu request ở trạng thái PENDING / REJECTED
        if ("PENDING".equals(requestStatus)) {
            if (!hasNavigated) {
                hasNavigated = true;
                Intent intent = new Intent(this, PendingActivity.class);
                intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
                startActivity(intent);
                finish();
            }
            return;
        } else if ("REJECTED".equals(requestStatus)) {
            if (!hasNavigated) {
                hasNavigated = true;
                Intent intent = new Intent(this, RejectedActivity.class);
                intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
                startActivity(intent);
                finish();
            }
            return;
        }

        // Kiểm tra trạng thái chuyến đi đã bị hủy bởi chủ xe hoặc hành khách
        if ("CANCELLED".equals(tripStatus) || "CANCELLED".equals(requestStatus)) {
            if (headerTitle != null) {
                headerTitle.setText("Lượt đi nhờ đã hủy");
            }
            if (upcomingStatus != null) upcomingStatus.setText("Đã hủy");
            if (upcomingDescription != null) {
                upcomingDescription.setText("Bạn không còn giữ chỗ trong chuyến này. Có thể quay lại tìm chuyến khác.");
                if ("CANCELLED".equals(tripStatus)) {
                    upcomingDescription.setText("Chủ xe đã hủy chuyến đi.");
                }
            }

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.GONE);
            }
            if (primaryAction != null) {
                primaryAction.setText("Quay về Chuyến của tôi");
                primaryAction.setEnabled(true);
            }
            return;
        }

        // Trường hợp chuyến đi đã COMPLETED
        if ("COMPLETED".equals(tripStatus)) {
            if (headerTitle != null) {
                headerTitle.setText("Chuyến đã hoàn thành");
            }
            if (upcomingStatus != null) upcomingStatus.setText("Đã hoàn thành");
            if (upcomingDescription != null) upcomingDescription.setText("Chuyến đi đã hoàn thành. Hãy đánh giá chủ xe!");

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.GONE);
            }
            if (primaryAction != null) {
                primaryAction.setText("Đánh giá chuyến đi");
                primaryAction.setEnabled(true);
            }
            return;
        }

        // Siết chặt predicate: Bắt buộc request.status==ACCEPTED, trip.status==FULL, seats==0, acceptedRequestId==requestId
        if (!isAcceptedRideValid(request, trip)) {
            if (upcomingStatus != null) upcomingStatus.setText("Không khả dụng");
            if (upcomingDescription != null) upcomingDescription.setText("Dữ liệu chuyến đi chưa nhất quán hoặc đã gán cho người khác.");

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.GONE); // Khóa mutation khi predicate không hợp lệ
            }
            if (primaryAction != null) {
                primaryAction.setText("Quay lại");
                primaryAction.setEnabled(true);
                primaryAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
            return;
        }

        // Kiểm tra điều kiện thời gian: Hủy chỉ trước giờ đi
        boolean isAfterDeparture = false;
        if (trip.getDepartureAt() != null) {
            Date departureDate = trip.getDepartureAt().toDate();
            if (departureDate.before(new Date())) {
                isAfterDeparture = true;
            }
        }

        if (isAfterDeparture) {
            if (headerTitle != null) {
                headerTitle.setText("Chuyến chưa kết thúc");
            }
            if (upcomingStatus != null) upcomingStatus.setText("Chờ hoàn thành");
            if (upcomingDescription != null) {
                String timeStr = (trip.getDepartureAt() != null) ? formatTimeOnly(trip.getDepartureAt()) : "giờ hẹn";
                upcomingDescription.setText("Đã quá giờ khởi hành (" + timeStr + "). Đang chờ chủ xe hoàn thành.");
            }

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.GONE); // Chặn hoàn toàn nút Hủy sau giờ đi
            }
            if (primaryAction != null) {
                primaryAction.setVisibility(View.GONE);
            }
        } else {
            if (headerTitle != null) {
                headerTitle.setText("Chuyến đã được nhận");
            }
            if (upcomingStatus != null) upcomingStatus.setText("Đã được nhận · Đã giữ chỗ");
            if (upcomingDescription != null && trip.getDepartureAt() != null) {
                upcomingDescription.setText("Chủ xe khởi hành lúc " + formatTimeOnly(trip.getDepartureAt())
                        + ". Cần thống nhất riêng giờ đón.");
            }

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.VISIBLE);
                secondaryAction.setText("Hủy tham gia");
                secondaryAction.setEnabled(true);
            }
            if (primaryAction != null) {
                primaryAction.setVisibility(View.GONE);
            }
        }
    }

    private void bindDriverProfile(PublicProfile profile) {
        if (profile != null) {
            if (driverName != null) {
                driverName.setText(profile.getDisplayName() != null && !profile.getDisplayName().trim().isEmpty()
                        ? profile.getDisplayName().trim() : "Chủ xe");
            }
            if (driverMeta != null) {
                String vehicle = (profile.getVehicleName() != null && !profile.getVehicleName().trim().isEmpty())
                        ? profile.getVehicleName().trim() : "Xe máy";
                driverMeta.setText(vehicle);
            }
        } else {
            if (driverName != null) driverName.setText("Chủ xe");
            if (driverMeta != null) driverMeta.setText("Xe máy");
        }
    }

    private void bindTripAndRequestInfo(Trip trip, TripRequest request) {
        if (trip != null) {
            if (tripTime != null && trip.getDepartureAt() != null) {
                tripTime.setText(formatTimestamp(trip.getDepartureAt()));
            }

            if (routeStart != null) {
                routeStart.setText("Chưa xác nhận được điểm đón");
                if (request != null && request.getPickup() != null) {
                    routeStart.setText(PlaceNameFormatter.format(request.getPickup().getName()));
                    routeStart.setContentDescription(request.getPickup().getName());
                }
            }
            if (routeEnd != null) {
                routeEnd.setText("Chưa xác nhận được điểm xuống");
                try {
                    Place dropoff = RequestRouteResolver.resolveDropoff(request, trip);
                    routeEnd.setText(PlaceNameFormatter.format(dropoff.getName()));
                    routeEnd.setContentDescription(dropoff.getName());
                } catch (IllegalArgumentException error) {
                    routeEnd.setText("Điểm xuống chưa hợp lệ");
                }
            }

        }

        PassengerContributionText.bindAmount(tripCost, request);
    }

    /**
     * Nút hủy tham gia (secondaryAction) chỉ cho phép khi predicate hợp lệ và còn trước giờ đi.
     */
    private void handleSecondaryAction() {
        if (!sessionGuard.current(sessionGuard.token()) || cancellationInFlight) {
            return;
        }
        if (cancelDialog != null && cancelDialog.isShowing()) {
            return;
        }
        if (currentDetails == null || currentDetails.getTrip() == null || currentDetails.getRequest() == null) {
            return;
        }

        TripRequest request = currentDetails.getRequest();
        Trip trip = currentDetails.getTrip();

        // Kiểm tra lại predicate siết chặt trước khi mở dialog
        if (!isAcceptedRideValid(request, trip)) {
            Toast.makeText(this, "Trạng thái chuyến đi không hợp lệ để hủy tham gia.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra lại thời gian tại thời điểm bấm nút
        if (trip.getDepartureAt() == null || !trip.getDepartureAt().toDate().after(new Date())) {
            Toast.makeText(this, "Đã quá giờ khởi hành, không thể hủy tham gia.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mở dialog xác nhận hủy
        cancelDialog = new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy tham gia")
                .setMessage("Bạn có chắc chắn muốn hủy tham gia chuyến đi này? Chỗ ngồi sẽ được nhường lại cho người khác.")
                .setPositiveButton("Xác nhận hủy", new DialogInterface.OnClickListener() {
                    /** Xác nhận chỉ chạy khi màn và phiên vẫn còn hợp lệ. */
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executeCancellation();
                    }
                })
                .setNegativeButton("Quay lại", null)
                .show();
    }

    /**
     * Thực thi hủy ACCEPTED request qua RequestCancellationRepository.
     */
    private void executeCancellation() {
        final int sessionToken = sessionGuard.token();
        if (!sessionGuard.current(sessionToken) || cancellationInFlight || !canCancelAccepted()) {
            return;
        }
        cancellationInFlight = true;
        if (secondaryAction != null) secondaryAction.setEnabled(false);

        cancellationRepository.cancelAcceptedRequest(requestId, currentUserId, new RepositoryCallback<Void>() {
            /** Không báo thành công hoặc đóng màn thuộc phiên cũ. */
            @Override
            public void onSuccess(Void result) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                cancellationInFlight = false;
                Toast.makeText(UpcomingActivity.this, "Đã hủy tham gia chuyến đi thành công.", Toast.LENGTH_SHORT).show();
                finish();
            }

            /** Chỉ mở lại hủy nếu state hiện tại còn cho phép, không dựa vào state trước gửi. */
            @Override
            public void onError(Exception error) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                cancellationInFlight = false;
                if (secondaryAction != null) secondaryAction.setEnabled(canCancelAccepted());
                Toast.makeText(UpcomingActivity.this, "Không thể hủy chuyến: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Kiểm tra lại pointer và giờ ngay lúc xác nhận; transaction vẫn là nguồn quyết định. */
    private boolean canCancelAccepted() {
        if (currentDetails == null || currentDetails.getRequest() == null || currentDetails.getTrip() == null) {
            return false;
        }
        TripRequest request = currentDetails.getRequest();
        Trip trip = currentDetails.getTrip();
        return currentUserId.equals(request.getPassengerId()) && isAcceptedRideValid(request, trip)
                && trip.getDepartureAt() != null && trip.getDepartureAt().toDate().after(new Date());
    }

    /**
     * Nút hành động chính (primaryAction).
     */
    private void handlePrimaryAction() {
        if (!sessionGuard.current(sessionGuard.token())) {
            return;
        }
        if (currentDetails == null) {
            finish();
            return;
        }

        Trip trip = currentDetails.getTrip();
        TripRequest request = currentDetails.getRequest();
        String tripStatus = (trip != null && trip.getStatus() != null) ? trip.getStatus() : "";
        String requestStatus = (request != null && request.getStatus() != null) ? request.getStatus() : "";

        if ("CANCELLED".equalsIgnoreCase(tripStatus) || "CANCELLED".equalsIgnoreCase(requestStatus)) {
            finish();
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(tripStatus)) {
            Intent intent = new Intent(UpcomingActivity.this, CompleteActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
            startActivity(intent);
            finish();
        }
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Hôm nay";
        Date date = timestamp.toDate();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd/MM · HH:mm", new Locale("vi", "VN"));
        return dateFormat.format(date);
    }

    private String formatTimeOnly(Timestamp timestamp) {
        if (timestamp == null) return "giờ hẹn";
        Date date = timestamp.toDate();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));
        return timeFormat.format(date);
    }
}
