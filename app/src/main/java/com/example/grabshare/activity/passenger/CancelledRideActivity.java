package com.example.grabshare.activity.passenger;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.adapter.passenger.RideAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.presentation.ScreenFeedback;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.SingleRequestDetails;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/** Chi tiết lịch sử hủy chỉ đọc; không mở lại yêu cầu cũ hoặc tự tạo lần gửi mới. */
public class CancelledRideActivity extends AppCompatActivity {
    private String expectedUserIdentifier;
    private String requestIdentifier;
    private ListenerRegistration listener;
    private PassengerSessionGuard sessionGuard;
    private PassengerTripsRepository repository;
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.screen_cancelled_ride);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        expectedUserIdentifier = user.getUid();
        if (state != null && !expectedUserIdentifier.equals(state.getString("history_uid"))) {
            finish();
            return;
        }
        requestIdentifier = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        if (requestIdentifier == null || requestIdentifier.isEmpty() || requestIdentifier.contains("/")) {
            finish();
            return;
        }
        repository = new PassengerTripsRepository(FirebaseFirestore.getInstance());
        sessionGuard = new PassengerSessionGuard(this);
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.history_retry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                load();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sessionGuard != null && sessionGuard.start(expectedUserIdentifier)) {
            load();
        }
    }

    private void load() {
        if (!sessionGuard.current(sessionGuard.token())) {
            return;
        }
        generation = generation + 1;
        final int expectedGeneration = generation;
        final int sessionToken = sessionGuard.token();
        if (listener != null) {
            listener.remove();
        }
        final TextView status = findViewById(R.id.history_status);
        final TextView details = findViewById(R.id.history_details);
        details.setText("");
        findViewById(R.id.history_retry).setVisibility(View.GONE);
        ScreenFeedback.show(status, "Đang tải lịch sử…", ScreenFeedback.Kind.INFORMATION);
        listener = repository.listenSingleRequest(requestIdentifier, new RepositoryCallback<SingleRequestDetails>() {
            @Override
            public void onSuccess(SingleRequestDetails result) {
                if (generation != expectedGeneration || !sessionGuard.current(sessionToken)) {
                    return;
                }
                if (result == null || result.getRequest() == null
                        || !expectedUserIdentifier.equals(result.getRequest().getPassengerId())
                        || !requestIdentifier.equals(result.getRequest().getId())) {
                    ScreenFeedback.show(status, "Không thể xác thực yêu cầu lịch sử này.", ScreenFeedback.Kind.ERROR);
                    details.setText("");
                    return;
                }
                String effective = RideAdapter.RideViewHolder.resolveEffectiveStatus(result.getRequest(), result.getTrip());
                String message = "Yêu cầu hoặc chuyến đã hủy. Đây là lịch sử, không phải một lần đặt chỗ mới.";
                if (!"CANCELLED".equals(effective)) {
                    message = "Trạng thái đã thay đổi: " + effective + ". Quay lại danh sách để mở màn tương ứng.";
                }
                ScreenFeedback.show(status, message, ScreenFeedback.Kind.INFORMATION);
                String messageText = result.getRequest().getMessage();
                if (messageText == null || messageText.trim().isEmpty()) {
                    messageText = "Không có lời nhắn.";
                }
                String departure = "Chưa rõ giờ khởi hành";
                if (result.getTrip() != null && result.getTrip().getDepartureAt() != null) {
                    departure = android.text.format.DateFormat.format("dd/MM/yyyy · HH:mm",
                            result.getTrip().getDepartureAt().toDate()).toString();
                }
                details.setText("Chủ xe khởi hành: " + departure + "\n\n"
                        + PassengerRouteText.format(result.getRequest(), result.getTrip())
                        + "\n\n" + PassengerContributionText.format(result.getRequest())
                        + "\n\nLời nhắn: " + messageText);
            }

            @Override
            public void onError(Exception error) {
                if (generation == expectedGeneration && sessionGuard.current(sessionToken)) {
                    details.setText("");
                    ScreenFeedback.show(status, "Không tải được lịch sử. Kiểm tra mạng và thử lại.", ScreenFeedback.Kind.ERROR);
                    findViewById(R.id.history_retry).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("history_uid", expectedUserIdentifier);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStop() {
        generation = generation + 1;
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        if (sessionGuard != null) {
            sessionGuard.stop();
        }
        super.onStop();
    }
}
