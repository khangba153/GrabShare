package com.example.grabshare.activity.search;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.firebase.auth.FirebaseAuth;

/** Detail online của Thành; Favorite và SQLite được tích hợp ở bước bàn giao riêng. */
public class TripDetailActivity extends AppCompatActivity {

    private OnlineTripViewModel tripViewModel;

    private String tripId;

    private String userIdentifier;

    private PassengerSearchRoute passengerSearchRoute;

    /** Gắn layout riêng và quan sát dữ liệu theo lifecycle. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_detail);
        if (savedInstanceState == null) {
            tripId = getIntent().getStringExtra(NavigationKeys.TRIP_ID);
        } else {
            tripId = savedInstanceState.getString(NavigationKeys.TRIP_ID);
        }
        if (savedInstanceState == null) {
            userIdentifier = currentUserIdentifier();
        } else {
            userIdentifier = savedInstanceState.getString("detail_uid");
        }
        Bundle routeArguments = savedInstanceState;
        if (routeArguments == null) {
            routeArguments = getIntent().getExtras();
        }
        passengerSearchRoute = PassengerSearchRoute.read(routeArguments, tripId, userIdentifier);
        tripViewModel = new ViewModelProvider(this).get(OnlineTripViewModel.class);
        findViewById(R.id.header_back)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Trở về đúng caller. */
                            @Override
                            public void onClick(View view) {
                                finish();
                            }
                        });
        findViewById(R.id.search_retry)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Đọc lại server, bao gồm phần profile/rating bị lỗi. */
                            @Override
                            public void onClick(View view) {
                                tripViewModel.start(tripId, userIdentifier);
                            }
                        });
        findViewById(R.id.primary_action)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Kiểm tra lại thời gian và phiên ngay tại lúc nhấn. */
                            @Override
                            public void onClick(View view) {
                                openReview();
                            }
                        });
        tripViewModel
                .getState()
                .observe(
                        this,
                        new Observer<OnlineTripViewModel.State>() {

                            /** Observer chỉ cập nhật màn đang hoạt động. */
                            @Override
                            public void onChanged(OnlineTripViewModel.State state) {
                                render(state);
                            }
                        });
    }

    /** Bắt đầu listener khi màn hiện. */
    @Override
    protected void onStart() {
        super.onStart();
        tripViewModel.start(tripId, userIdentifier);
    }

    /** Gỡ listener khi màn dừng. */
    @Override
    protected void onStop() {
        tripViewModel.stop();
        super.onStop();
    }

    /** Giữ ID và chủ phiên qua xoay/process death, không serialize model. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(NavigationKeys.TRIP_ID, tripId);
        state.putString("detail_uid", userIdentifier);
        if (passengerSearchRoute != null) {
            passengerSearchRoute.saveTo(state);
        }
        super.onSaveInstanceState(state);
    }

    /** Không giữ quyền gửi từ snapshot offline hoặc tài khoản cũ. */
    private void render(OnlineTripViewModel.State state) {
        PassengerSearchRoute.bind(this, passengerSearchRoute, sameUser());
        boolean ready = state.online && state.trip != null && sameUser();
        if (ready) {
            findViewById(R.id.search_trip_content).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.search_trip_content).setVisibility(View.GONE);
        }
        if (ready) {
            TripDisplay.bind(this, state.trip, state.driver);
        }
        String reason;
        if (ready) {
            reason = SearchTripPolicy.blockedReason(state.trip, userIdentifier);
        } else {
            reason = "";
        }
        if (!sameUser()) {
            ((TextView) findViewById(R.id.search_status))
                    .setText("Bạn cần đăng nhập đúng tài khoản đã mở chuyến.");
        } else {
            ((TextView) findViewById(R.id.search_status)).setText(state.message);
        }
        ((TextView) findViewById(R.id.search_action_hint)).setText(reason);
        findViewById(R.id.primary_action).setEnabled(ready && reason.isEmpty());
    }

    /** Mở Review theo trip_id; thiếu đăng ký Manifest vẫn giữ Detail an toàn. */
    private void openReview() {
        OnlineTripViewModel.State state = tripViewModel.getState().getValue();
        if (state == null || !state.online || !sameUser()) {
            return;
        }
        String reason = SearchTripPolicy.blockedReason(state.trip, userIdentifier);
        if (!reason.isEmpty()) {
            render(state);
            return;
        }
        try {
            Intent intent = new Intent(this, RequestReviewActivity.class);
            intent.putExtra(NavigationKeys.TRIP_ID, tripId);
            if (passengerSearchRoute != null) {
                passengerSearchRoute.forwardTo(intent);
            }
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(
                            this,
                            "Màn kiểm tra yêu cầu chưa sẵn sàng. Vui lòng thử lại sau.",
                            Toast.LENGTH_LONG)
                    .show();
        }
    }

    /** Lấy UID thật, không hardcode tài khoản demo. */
    private String currentUserIdentifier() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        } else {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    /** Chặn thao tác sau logout hoặc đổi tài khoản. */
    private boolean sameUser() {
        return userIdentifier != null && userIdentifier.equals(currentUserIdentifier());
    }
}
