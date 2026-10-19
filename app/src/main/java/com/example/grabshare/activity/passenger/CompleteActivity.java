package com.example.grabshare.activity.passenger;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Rating;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.SingleRequestDetails;
import com.example.grabshare.repository.passenger.RatingRepository;
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
 * Activity hiển thị thông tin chuyến đi đã hoàn thành và cho phép Hành khách gửi đánh giá (Thuận-6).
 */
public class CompleteActivity extends AppCompatActivity {

    private ImageButton headerBack;
    private TextView headerTitle;

    private TextView driverName;
    private TextView driverMeta;
    private TextView tripTime;
    private TextView tripSeats;
    private TextView routeStart;
    private TextView routeEnd;
    private TextView tripPickup;
    private TextView tripCost;

    private RatingBar ratingBar;
    private EditText ratingComment;
    private Button primaryAction;

    private PassengerTripsRepository tripsRepository;
    private RatingRepository ratingRepository;
    private ListenerRegistration singleRequestListener;

    private String requestId;
    private String currentUserId;
    private SingleRequestDetails currentDetails;
    private boolean isAlreadyRated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_complete);

        if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        }

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
        ratingRepository = new RatingRepository(firestore);
    }

    private void initViews() {
        headerBack = findViewById(R.id.header_back);
        headerTitle = findViewById(R.id.header_title);

        if (headerBack != null) {
            headerBack.setClickable(true);
            headerBack.setFocusable(true);
        }

        if (headerTitle != null) {
            headerTitle.setText("Hoàn thành & đánh giá");
        }

        // View chi tiết chuyến đi
        driverName = findViewById(R.id.driver_name);
        driverMeta = findViewById(R.id.driver_meta);
        tripTime = findViewById(R.id.trip_time);
        tripSeats = findViewById(R.id.trip_seats);
        routeStart = findViewById(R.id.route_start);
        routeEnd = findViewById(R.id.route_end);
        tripPickup = findViewById(R.id.trip_pickup);
        tripCost = findViewById(R.id.trip_cost);

        // View đánh giá
        ratingBar = findViewById(R.id.rating);
        ratingComment = findViewById(R.id.rating_comment);
        primaryAction = findViewById(R.id.primary_action);

        if (primaryAction != null) {
            primaryAction.setClickable(true);
            primaryAction.setFocusable(true);
            primaryAction.setText("Gửi đánh giá");
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

        if (primaryAction != null) {
            primaryAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performSubmitRating();
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
                    Toast.makeText(CompleteActivity.this, "Không tìm thấy thông tin chuyến đi.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                TripRequest request = details.getRequest();
                if (!currentUserId.equals(request.getPassengerId())) {
                    Toast.makeText(CompleteActivity.this, "Bạn không có quyền xem thông tin này.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentDetails = details;
                bindDetailsToViews(details);
                checkExistingRating(details);
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(CompleteActivity.this, "Không thể tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void checkExistingRating(SingleRequestDetails details) {
        if (details == null || details.getTrip() == null) {
            return;
        }

        String tripId = details.getTrip().getId();
        if (tripId == null || tripId.trim().isEmpty()) {
            return;
        }

        ratingRepository.getRating(tripId, currentUserId, new RepositoryCallback<Rating>() {
            @Override
            public void onSuccess(Rating rating) {
                if (rating != null) {
                    isAlreadyRated = true;
                    if (ratingBar != null) {
                        ratingBar.setRating(rating.getScore() != null ? rating.getScore().floatValue() : 5.0f);
                        ratingBar.setIsIndicator(true);
                    }

                    if (ratingComment != null) {
                        ratingComment.setText(rating.getComment() != null ? rating.getComment() : "");
                        ratingComment.setFocusable(false);
                        ratingComment.setFocusableInTouchMode(false);
                        ratingComment.setCursorVisible(false);
                        ratingComment.setLongClickable(false);
                    }

                    if (primaryAction != null) {
                        primaryAction.setText("Đã đánh giá");
                        primaryAction.setEnabled(false);
                    }
                } else {
                    isAlreadyRated = false;
                    if (ratingBar != null) {
                        ratingBar.setIsIndicator(false);
                    }

                    if (ratingComment != null) {
                        ratingComment.setFocusable(true);
                        ratingComment.setFocusableInTouchMode(true);
                        ratingComment.setCursorVisible(true);
                        ratingComment.setLongClickable(true);
                    }

                    if (primaryAction != null) {
                        primaryAction.setText("Gửi đánh giá");
                        primaryAction.setEnabled(true);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                // Không chặn UI nếu đọc rating bị lỗi
            }
        });
    }

    private void performSubmitRating() {
        if (isAlreadyRated) {
            Toast.makeText(this, "Bạn đã gửi đánh giá cho chuyến đi này rồi.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentDetails == null || currentDetails.getTrip() == null || currentDetails.getRequest() == null) {
            Toast.makeText(this, "Dữ liệu chuyến đi chưa sẵn sàng.", Toast.LENGTH_SHORT).show();
            return;
        }

        Trip trip = currentDetails.getTrip();
        TripRequest request = currentDetails.getRequest();

        // 1. Kiểm tra trạng thái chuyến đi phải là COMPLETED
        if (!"COMPLETED".equalsIgnoreCase(trip.getStatus())) {
            Toast.makeText(this, "Chuyến đi chưa hoàn thành, không thể gửi đánh giá.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Kiểm tra chính chủ passenger
        if (!currentUserId.equals(request.getPassengerId())) {
            Toast.makeText(this, "Bạn không có quyền đánh giá chuyến đi này.", Toast.LENGTH_SHORT).show();
            return;
        }

        String tripId = trip.getId();
        String targetUserId = trip.getDriverId();

        if (tripId == null || tripId.trim().isEmpty() || targetUserId == null || targetUserId.trim().isEmpty()) {
            Toast.makeText(this, "Thông tin chuyến đi hoặc chủ xe không hợp lệ.", Toast.LENGTH_SHORT).show();
            return;
        }

        float ratingValue = ratingBar != null ? ratingBar.getRating() : 5.0f;
        long score = Math.max(1, Math.min(5, Math.round(ratingValue)));

        String comment = ratingComment != null && ratingComment.getText() != null
                ? ratingComment.getText().toString().trim() : "";

        if (primaryAction != null) primaryAction.setEnabled(false);

        ratingRepository.submitRating(tripId, currentUserId, targetUserId, score, comment, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(CompleteActivity.this, "Cảm ơn bạn đã gửi đánh giá!", Toast.LENGTH_SHORT).show();
                isAlreadyRated = true;

                if (ratingBar != null) ratingBar.setIsIndicator(true);
                if (ratingComment != null) {
                    ratingComment.setFocusable(false);
                    ratingComment.setFocusableInTouchMode(false);
                    ratingComment.setCursorVisible(false);
                }
                if (primaryAction != null) {
                    primaryAction.setText("Đã đánh giá");
                    primaryAction.setEnabled(false);
                }
            }

            @Override
            public void onError(Exception error) {
                if (primaryAction != null) primaryAction.setEnabled(true);
                Toast.makeText(CompleteActivity.this, "Gửi đánh giá thất bại: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Hôm nay";
        Date date = timestamp.toDate();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd/MM · HH:mm", new Locale("vi", "VN"));
        return sdf.format(date);
    }
}
