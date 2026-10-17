package com.example.grabshare.activity.passenger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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
 * Activity hiển thị trạng thái Đã được chủ xe chấp nhận (Accepted) của Passenger (Thuận-2).
 */
public class AcceptedActivity extends AppCompatActivity {

    private ImageButton headerBack;
    private TextView headerTitle;
    private TextView statusTitle;
    private TextView statusDescription;
    private Button primaryAction;
    private Button secondaryAction;

    // View trong view_trip_summary.xml và view_map.xml
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
    private ListenerRegistration singleRequestListener;

    private String requestId;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_accepted);

        initRepositories();
        initViews();
        setupListeners();

        // Đọc requestId từ Intent Extra theo NavigationKeys.REQUEST_ID
        if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        }

        // Kiểm tra phiên đăng nhập
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Thiếu request_id. Vui lòng nhập request_id trong công cụ bộ chọn.", Toast.LENGTH_LONG).show();
            if (statusTitle != null) statusTitle.setText("Thiếu mã request_id");
            if (statusDescription != null) statusDescription.setText("Nhập request_id từ Firestore trong công cụ bộ chọn để xem thông tin thật.");
        }
    }

    private void initRepositories() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        tripsRepository = new PassengerTripsRepository(firestore);
    }

    private void initViews() {
        headerBack = findViewById(R.id.header_back);
        headerTitle = findViewById(R.id.header_title);
        statusTitle = findViewById(R.id.status_title);
        statusDescription = findViewById(R.id.status_description);
        primaryAction = findViewById(R.id.primary_action);
        secondaryAction = findViewById(R.id.secondary_action);

        if (headerBack != null) {
            headerBack.setClickable(true);
            headerBack.setFocusable(true);
        }
        if (primaryAction != null) {
            primaryAction.setClickable(true);
            primaryAction.setFocusable(true);
            primaryAction.setText("Xem chuyến");
        }
        if (secondaryAction != null) {
            secondaryAction.setClickable(true);
            secondaryAction.setFocusable(true);
            secondaryAction.setText("Quay lại");
            secondaryAction.setVisibility(View.VISIBLE);
        }

        if (headerTitle != null) {
            headerTitle.setText("Được chấp nhận");
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
                    finish();
                }
            });
        }

        if (primaryAction != null) {
            primaryAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AcceptedActivity.this, UpcomingActivity.class);
                    intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
                    startActivity(intent);
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
        // Hủy listener đúng vòng đời Android (Checklist 3)
        if (singleRequestListener != null) {
            singleRequestListener.remove();
            singleRequestListener = null;
        }
    }

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
                    Toast.makeText(AcceptedActivity.this, "Không tìm thấy dữ liệu yêu cầu.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                TripRequest request = details.getRequest();

                // Kiểm tra quyền truy cập: không rò dữ liệu request của người khác (Checklist 2)
                if (!currentUserId.equals(request.getPassengerId())) {
                    Toast.makeText(AcceptedActivity.this, "Bạn không có quyền truy cập yêu cầu này.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                Trip trip = details.getTrip();
                String tripStatus = (trip != null) ? trip.getStatus() : "";

                if ("CANCELLED".equalsIgnoreCase(tripStatus)) {
                    if (statusTitle != null) statusTitle.setText("Chuyến đi đã bị hủy");
                    if (statusDescription != null) statusDescription.setText("Chủ xe đã hủy chuyến đi này.");
                    bindDetailsToViews(details);
                    return;
                }

                bindDetailsToViews(details);
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(AcceptedActivity.this, "Không thể tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void bindDetailsToViews(SingleRequestDetails details) {
        if (details == null) return;

        TripRequest request = details.getRequest();
        Trip trip = details.getTrip();
        PublicProfile profile = details.getDriverProfile();

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

        if (trip != null) {
            if (tripTime != null && trip.getDepartureAt() != null) {
                String formattedTime = formatTimestamp(trip.getDepartureAt());
                tripTime.setText(formattedTime);
                if (statusDescription != null) {
                    statusDescription.setText("Hẹn bạn tại điểm đón lúc " + formatTimeOnly(trip.getDepartureAt()) + ".");
                }
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
