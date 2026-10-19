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

import java.text.DecimalFormat;
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
    private TextView tripSeats;
    private TextView routeStart;
    private TextView routeEnd;
    private TextView tripPickup;
    private TextView tripCost;
    private TextView mapStartLabel;
    private TextView mapEndLabel;

    private PassengerTripsRepository tripsRepository;
    private RequestCancellationRepository cancellationRepository;
    private ListenerRegistration singleRequestListener;

    private String requestId;
    private String currentUserId;
    private SingleRequestDetails currentDetails;
    private boolean hasNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_upcoming);

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

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Yêu cầu không hợp lệ hoặc thiếu mã nhận dạng.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initRepositories();
        initViews();
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
        tripSeats = findViewById(R.id.trip_seats);
        routeStart = findViewById(R.id.route_start);
        routeEnd = findViewById(R.id.route_end);
        tripPickup = findViewById(R.id.trip_pickup);
        tripCost = findViewById(R.id.trip_cost);
        mapStartLabel = findViewById(R.id.map_start_label);
        mapEndLabel = findViewById(R.id.map_end_label);
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
        startListeningRequest();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (singleRequestListener != null) {
            singleRequestListener.remove();
            singleRequestListener = null;
        }
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

        singleRequestListener = tripsRepository.listenSingleRequest(requestId, new RepositoryCallback<SingleRequestDetails>() {
            @Override
            public void onSuccess(SingleRequestDetails details) {
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
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(UpcomingActivity.this, "Không thể tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Cập nhật toàn bộ giao diện và trạng thái nút theo dữ liệu Realtime thu được.
     */
    private void updateUiForDetails(SingleRequestDetails details) {
        if (details == null) return;

        TripRequest request = details.getRequest();
        Trip trip = details.getTrip();
        PublicProfile profile = details.getDriverProfile();

        bindDriverProfile(profile);
        bindTripAndRequestInfo(trip, request);

        // Đảm bảo nút liên hệ luôn bị ẩn vì chưa thuộc scope
        if (tripCall != null) {
            tripCall.setVisibility(View.GONE);
        }

        // Kiểm tra chuyến đi thiếu hoặc không khả dụng -> Khóa mutation
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

        // Chuyển hướng nếu request không ở trạng thái ACCEPTED
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
            if (upcomingStatus != null) upcomingStatus.setText("Đã hủy");
            if (upcomingDescription != null) upcomingDescription.setText("Chuyến đi đã bị hủy.");

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.GONE);
            }
            if (primaryAction != null) {
                primaryAction.setText("Quay về Chuyến của tôi");
                primaryAction.setEnabled(true);
            }
            return;
        }

        // Kiểm tra pointer của chuyến đi (chỉ chấp nhận pointer trùng khớp hoặc rỗng nếu chưa gán)
        if (trip.getAcceptedRequestId() != null && !trip.getAcceptedRequestId().trim().isEmpty()) {
            if (!trip.getAcceptedRequestId().trim().equals(requestId)) {
                Toast.makeText(this, "Chuyến đi này đã nhận người khác.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Trường hợp chuyến đi đã COMPLETED
        if ("COMPLETED".equals(tripStatus)) {
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

        // Kiểm tra điều kiện thời gian: Hủy chỉ trước giờ đi
        boolean isAfterDeparture = false;
        if (trip.getDepartureAt() != null) {
            Date departureDate = trip.getDepartureAt().toDate();
            if (departureDate.before(new Date())) {
                isAfterDeparture = true;
            }
        }

        if (isAfterDeparture) {
            if (upcomingStatus != null) upcomingStatus.setText("Chờ hoàn thành");
            if (upcomingDescription != null) {
                String timeStr = (trip.getDepartureAt() != null) ? formatTimeOnly(trip.getDepartureAt()) : "giờ hẹn";
                upcomingDescription.setText("Đã quá giờ khởi hành (" + timeStr + "). Đang chờ chủ xe hoàn thành.");
            }

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.GONE); // Chặn hoàn toàn nút Hủy sau giờ đi
            }
            if (primaryAction != null) {
                primaryAction.setText("Chờ chủ xe hoàn thành");
                primaryAction.setEnabled(false); // Passenger không tự complete
            }
        } else {
            if (upcomingStatus != null) upcomingStatus.setText("Sắp khởi hành");
            if (upcomingDescription != null && trip.getDepartureAt() != null) {
                upcomingDescription.setText("Hẹn bạn tại điểm đón lúc " + formatTimeOnly(trip.getDepartureAt()) + ".");
            }

            if (secondaryAction != null) {
                secondaryAction.setVisibility(View.VISIBLE);
                secondaryAction.setText("Hủy tham gia");
                secondaryAction.setEnabled(true);
            }
            if (primaryAction != null) {
                primaryAction.setText("Đã xác nhận chuyến đi");
                primaryAction.setEnabled(false);
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

            if (tripSeats != null) {
                long seats = trip.getAvailableSeats() != null ? trip.getAvailableSeats() : 0;
                tripSeats.setText(seats + " chỗ trống");
            }

            if (routeStart != null && trip.getOrigin() != null) {
                routeStart.setText(trip.getOrigin().getName() != null ? trip.getOrigin().getName() : "Điểm đi");
            }

            if (routeEnd != null && trip.getDestination() != null) {
                routeEnd.setText(trip.getDestination().getName() != null ? trip.getDestination().getName() : "Điểm đến");
            }

            if (mapStartLabel != null && trip.getOrigin() != null) {
                mapStartLabel.setText(trip.getOrigin().getName() != null ? trip.getOrigin().getName() : "Điểm đi");
            }

            if (mapEndLabel != null && trip.getDestination() != null) {
                mapEndLabel.setText(trip.getDestination().getName() != null ? trip.getDestination().getName() : "Điểm đến");
            }

            if (tripCost != null && trip.getCostVnd() != null) {
                DecimalFormat df = new DecimalFormat("#,###");
                tripCost.setText(df.format(trip.getCostVnd()) + "đ");
            }
        }

        if (request != null && request.getPickup() != null) {
            Place pickupPlace = request.getPickup();
            if (tripPickup != null) {
                tripPickup.setText("Điểm đón: " + (pickupPlace.getName() != null ? pickupPlace.getName() : "Điểm hẹn đã chọn"));
            }
        }
    }

    /**
     * Nút hủy tham gia (secondaryAction) chỉ cho phép khi còn trước giờ đi.
     */
    private void handleSecondaryAction() {
        if (currentDetails == null || currentDetails.getTrip() == null) {
            return;
        }

        Trip trip = currentDetails.getTrip();

        // Kiểm tra lại lần nữa tại thời điểm bấm nút
        if (trip.getDepartureAt() != null && trip.getDepartureAt().toDate().before(new Date())) {
            Toast.makeText(this, "Đã quá giờ khởi hành, không thể hủy tham gia.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mở dialog xác nhận hủy
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy tham gia")
                .setMessage("Bạn có chắc chắn muốn hủy tham gia chuyến đi này? Chỗ ngồi sẽ được nhường lại cho người khác.")
                .setPositiveButton("Xác nhận hủy", new DialogInterface.OnClickListener() {
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
        if (secondaryAction != null) secondaryAction.setEnabled(false);

        cancellationRepository.cancelAcceptedRequest(requestId, currentUserId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(UpcomingActivity.this, "Đã hủy tham gia chuyến đi thành công.", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Exception error) {
                if (secondaryAction != null) secondaryAction.setEnabled(true);
                Toast.makeText(UpcomingActivity.this, "Không thể hủy chuyến: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Nút hành động chính (primaryAction).
     */
    private void handlePrimaryAction() {
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
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd/MM · HH:mm", new Locale("vi", "VN"));
        return sdf.format(date);
    }

    private String formatTimeOnly(Timestamp timestamp) {
        if (timestamp == null) return "giờ hẹn";
        Date date = timestamp.toDate();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));
        return sdf.format(date);
    }
}
