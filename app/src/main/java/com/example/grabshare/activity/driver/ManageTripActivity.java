package com.example.grabshare.activity.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.driver.DriverTripRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Hiển thị và điều phối màn quản lý một chuyến của tài xế hiện tại.
 * Đọc dữ liệu và nghiệp vụ Firestore được giao cho DriverTripRepository.
 */
public class ManageTripActivity extends AppCompatActivity {

    private static final String STATE_TRIP_ID = "manage_trip_id";

    private ImageButton backButton;
    private ProgressBar loadingProgressBar;
    private TextView statusText;
    private TextView timeText;
    private TextView messageText;
    private TextView originText;
    private TextView destinationText;
    private TextView seatsText;
    private TextView costText;
    private TextView noteText;
    private AppCompatButton editButton;

    private DriverTripRepository tripRepository;
    private ActivityResultLauncher<Intent> editTripLauncher;

    private String tripId;
    private Trip currentTrip;
    private boolean isLoading;

    /** Khởi tạo giao diện, phục hồi ID chuyến và tải dữ liệu từ máy chủ. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_manage);

        initializeViews();
        initializeRepository();
        registerEditTripLauncher();
        connectEvents();

        if (savedInstanceState != null) {
            tripId = savedInstanceState.getString(
                    STATE_TRIP_ID,
                    getIntent().getStringExtra(NavigationKeys.TRIP_ID)
            );
        } else if (getIntent() != null) {
            tripId = getIntent().getStringExtra(NavigationKeys.TRIP_ID);
        }

        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Thiếu ID chuyến cần quản lý.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        tripId = tripId.trim();
        loadTrip();
    }

    /** Ánh xạ các thành phần giao diện từ screen_manage.xml. */
    private void initializeViews() {
        backButton = findViewById(R.id.header_back);
        loadingProgressBar = findViewById(R.id.manage_loading);
        statusText = findViewById(R.id.manage_status);
        timeText = findViewById(R.id.manage_time);
        messageText = findViewById(R.id.manage_message);
        originText = findViewById(R.id.map_start_label);
        destinationText = findViewById(R.id.map_end_label);
        seatsText = findViewById(R.id.remaining_seats);
        costText = findViewById(R.id.manage_cost);
        noteText = findViewById(R.id.manage_note);
        editButton = findViewById(R.id.manage_edit);
    }

    /** Khởi tạo repository với các instance Firebase dùng chung của ứng dụng. */
    private void initializeRepository() {
        tripRepository = new DriverTripRepository(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance()
        );
    }

    /** Đăng ký nhận kết quả sửa chuyến trước khi Activity vào trạng thái STARTED. */
    private void registerEditTripLauncher() {
        editTripLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        loadTrip();
                    }
                }
        );
    }

    /** Kết nối thao tác quay lại và chỉnh sửa chuyến. */
    private void connectEvents() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openEditTrip();
            }
        });
    }

    /** Đọc lại chuyến từ máy chủ để tránh hiển thị dữ liệu cache cũ. */
    private void loadTrip() {
        if (isLoading) {
            return;
        }

        isLoading = true;
        currentTrip = null;
        setContentEnabled(false);
        loadingProgressBar.setVisibility(View.VISIBLE);
        messageText.setVisibility(View.VISIBLE);
        messageText.setText("Đang tải thông tin chuyến...");
        statusText.setText("Đang tải...");

        tripRepository.loadTripForManagement(
                tripId,
                new RepositoryCallback<Trip>() {
                    @Override
                    public void onSuccess(Trip trip) {
                        if (!isActivityUsable()) {
                            return;
                        }

                        isLoading = false;
                        currentTrip = trip;
                        loadingProgressBar.setVisibility(View.GONE);
                        messageText.setVisibility(View.GONE);
                        renderTrip(trip);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isActivityUsable()) {
                            return;
                        }

                        isLoading = false;
                        loadingProgressBar.setVisibility(View.GONE);
                        statusText.setText("Không thể tải chuyến");
                        messageText.setVisibility(View.VISIBLE);
                        messageText.setText(buildErrorMessage(error));
                        setContentEnabled(false);
                    }
                }
        );
    }

    /** Hiển thị các field chuyến thật và tính các thao tác có thể thực hiện. */
    private void renderTrip(Trip trip) {
        statusText.setText(formatTripStatus(trip.getStatus()));
        timeText.setText(formatDepartureTime(trip.getDepartureAt()));
        originText.setText(getPlaceName(trip.getOrigin(), "Chưa có điểm đi"));
        destinationText.setText(
                getPlaceName(trip.getDestination(), "Chưa có điểm đến")
        );
        seatsText.setText(formatAvailableSeats(trip));
        costText.setText(formatCost(trip.getCostVnd()));

        String note = trip.getNote();
        if (note == null || note.trim().isEmpty()) {
            noteText.setText("Không có ghi chú.");
        } else {
            noteText.setText(note.trim());
        }

        boolean canEdit = canEditTrip(trip);
        editButton.setEnabled(canEdit);
        editButton.setText(
                canEdit ? "Chỉnh sửa chuyến" : "Không thể chỉnh sửa chuyến này"
        );
        setContentEnabled(true);
        editButton.setEnabled(canEdit);
    }

    /** Mở EditTripActivity bằng trip_id và tải lại Manage khi quay về. */
    private void openEditTrip() {
        if (currentTrip == null || !canEditTrip(currentTrip) || isLoading) {
            Toast.makeText(
                    this,
                    "Chỉ chuyến OPEN chưa đến giờ khởi hành mới được sửa.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent editIntent = new Intent(
                ManageTripActivity.this,
                EditTripActivity.class
        );
        editIntent.putExtra(NavigationKeys.TRIP_ID, tripId);
        editTripLauncher.launch(editIntent);
    }

    /** Cho phép sửa khi chuyến còn OPEN và giờ khởi hành vẫn ở tương lai. */
    private boolean canEditTrip(Trip trip) {
        return trip != null
                && "OPEN".equals(trip.getStatus())
                && trip.getDepartureAt() != null
                && trip.getDepartureAt().toDate().getTime()
                > System.currentTimeMillis();
    }

    /** Khóa thao tác trong lúc đọc dữ liệu hoặc khi không thể quản lý chuyến. */
    private void setContentEnabled(boolean enabled) {
        editButton.setEnabled(enabled);
    }

    /** Kiểm tra Activity còn có thể nhận callback để tránh cập nhật View đã bị hủy. */
    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
    }

    /** Định dạng tên địa điểm lấy từ document chuyến. */
    private String getPlaceName(Place place, String fallback) {
        if (place == null || place.getName() == null
                || place.getName().trim().isEmpty()) {
            return fallback;
        }
        return place.getName().trim();
    }

    /** Chuyển mã trạng thái thành nhãn tiếng Việt. */
    private String formatTripStatus(String status) {
        if (status == null) {
            return "Không rõ trạng thái";
        }

        switch (status) {
            case "OPEN":
                return "Đang mở chỗ";
            case "FULL":
                return "Đã nhận người đi nhờ";
            case "CANCELLED":
                return "Đã hủy chuyến";
            case "COMPLETED":
                return "Đã hoàn thành";
            default:
                return "Trạng thái không hợp lệ";
        }
    }

    /** Định dạng thời gian khởi hành theo múi giờ thiết bị. */
    private String formatDepartureTime(Timestamp departureAt) {
        if (departureAt == null) {
            return "Chưa có thời gian khởi hành";
        }

        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd/MM/yyyy · HH:mm",
                Locale.forLanguageTag("vi-VN")
        );
        return formatter.format(departureAt.toDate());
    }

    /** Định dạng số chỗ hiện còn dựa trên dữ liệu thật. */
    private String formatAvailableSeats(Trip trip) {
        Long availableSeats = trip.getAvailableSeats();
        if (availableSeats == null) {
            return "Chưa rõ số chỗ";
        }
        return availableSeats + " chỗ";
    }

    /** Định dạng số tiền đóng góp theo kiểu tiền Việt Nam. */
    private String formatCost(Long costVnd) {
        if (costVnd == null) {
            return "Chưa có chi phí";
        }
        if (costVnd == 0L) {
            return "Miễn phí";
        }

        NumberFormat formatter = NumberFormat.getIntegerInstance(
                Locale.forLanguageTag("vi-VN")
        );
        return formatter.format(costVnd) + " đ";
    }

    /** Trả thông báo đọc dữ liệu dễ hiểu nhưng không che mất nguyên nhân lỗi. */
    private String buildErrorMessage(Exception error) {
        if (error == null || error.getMessage() == null
                || error.getMessage().trim().isEmpty()) {
            return "Không thể tải chuyến. Hãy kiểm tra kết nối và quyền truy cập.";
        }
        return error.getMessage();
    }

    /** Giữ lại trip_id nếu Android tạo lại Activity do xoay màn hình. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_TRIP_ID, tripId);
        super.onSaveInstanceState(outState);
    }
}
