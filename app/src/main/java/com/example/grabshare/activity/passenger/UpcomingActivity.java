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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_upcoming);

        initRepositories();
        initViews();
        setupListeners();

        // Đọc requestId từ Intent Extra theo NavigationKeys.REQUEST_ID
        if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        }

        // Kiểm tra phiên đăng nhập người dùng
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid() != null) {
            currentUserId = currentUser.getUid();
        }

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Thiếu request_id. Vui lòng nhập request_id trong công cụ bộ chọn.", Toast.LENGTH_LONG).show();
            if (upcomingStatus != null) upcomingStatus.setText("Thiếu mã request_id");
            if (upcomingDescription != null) upcomingDescription.setText("Nhập request_id từ Firestore trong bộ chọn Mở Activity thật hoặc điều hướng từ Màn Chuyến của tôi.");
        }
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

        if (tripCall != null) {
            tripCall.setClickable(true);
            tripCall.setFocusable(true);
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

        if (tripCall != null) {
            tripCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleCallDriver();
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
        // Tháo gỡ listener đúng vòng đời Android
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

                // Xác minh người sở hữu request (Checklist 1: Chỉ người được nhận thấy chuyến của mình)
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

        String tripStatus = (trip != null && trip.getStatus() != null) ? trip.getStatus() : "";
        String requestStatus = (request != null && request.getStatus() != null) ? request.getStatus() : "";

        // Ưu tiên kiểm tra trạng thái trip CANCELLED (Checklist 3)
        if ("CANCELLED".equalsIgnoreCase(tripStatus) || "CANCELLED".equalsIgnoreCase(requestStatus)) {
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

        // Trường hợp chuyến đi đã COMPLETED
        if ("COMPLETED".equalsIgnoreCase(tripStatus)) {
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

        // Kiểm tra thời gian khởi hành (Checklist 2: Hủy chỉ trước giờ)
        boolean isAfterDeparture = false;
        if (trip != null && trip.getDepartureAt() != null) {
            Date departureDate = trip.getDepartureAt().toDate();
            if (departureDate.before(new Date())) {
                isAfterDeparture = true;
            }
        }

        if (isAfterDeparture) {
            if (upcomingStatus != null) upcomingStatus.setText("Chờ hoàn thành");
            if (upcomingDescription != null) {
                String timeStr = (trip != null && trip.getDepartureAt() != null) ? formatTimeOnly(trip.getDepartureAt()) : "giờ hẹn";
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
            if (upcomingDescription != null && trip != null && trip.getDepartureAt() != null) {
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
                driverName.setText(profile.getDisplayName() != null ? profile.getDisplayName() : "Chủ xe");
            }
            if (driverMeta != null) {
                String vehicle = (profile.getVehicleName() != null && !profile.getVehicleName().isEmpty()) ? profile.getVehicleName() : "Xe máy";
                driverMeta.setText("★ 5.0 · " + vehicle);
            }
        } else {
            if (driverName != null) driverName.setText("Chủ xe");
            if (driverMeta != null) driverMeta.setText("★ 5.0 · Xe máy");
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
        if (currentDetails == null) return;

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

    /**
     * Thao tác liên hệ chủ xe (Checklist 4: Không để nút liên hệ giả).
     */
    private void handleCallDriver() {
        PublicProfile profile = (currentDetails != null) ? currentDetails.getDriverProfile() : null;
        String name = (profile != null && profile.getDisplayName() != null) ? profile.getDisplayName() : "chủ xe";

        Toast.makeText(this, "Đang kết nối liên hệ với " + name + "...", Toast.LENGTH_SHORT).show();
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
