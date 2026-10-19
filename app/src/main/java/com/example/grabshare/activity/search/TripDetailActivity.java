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
    private OnlineTripViewModel model;
    private String tripId;
    private String uid;

    /** Gắn layout riêng và quan sát dữ liệu theo lifecycle. */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_detail);
        tripId = savedInstanceState == null ? getIntent().getStringExtra(NavigationKeys.TRIP_ID)
                : savedInstanceState.getString(NavigationKeys.TRIP_ID);
        uid = savedInstanceState == null ? currentUid() : savedInstanceState.getString("detail_uid");
        model = new ViewModelProvider(this).get(OnlineTripViewModel.class);
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            /** Trở về đúng caller. */
            @Override public void onClick(View view) { finish(); }
        });
        findViewById(R.id.search_retry).setOnClickListener(new View.OnClickListener() {
            /** Đọc lại server, bao gồm phần profile/rating bị lỗi. */
            @Override public void onClick(View view) { model.start(tripId, uid); }
        });
        findViewById(R.id.primary_action).setOnClickListener(new View.OnClickListener() {
            /** Kiểm tra lại thời gian và phiên ngay tại lúc nhấn. */
            @Override public void onClick(View view) { openReview(); }
        });
        model.getState().observe(this, new Observer<OnlineTripViewModel.State>() {
            /** Observer chỉ cập nhật màn đang hoạt động. */
            @Override public void onChanged(OnlineTripViewModel.State state) { render(state); }
        });
    }
    /** Bắt đầu listener khi màn hiện. */
    @Override protected void onStart() { super.onStart(); model.start(tripId, uid); }
    /** Gỡ listener khi màn dừng. */
    @Override protected void onStop() { model.stop(); super.onStop(); }
    /** Giữ ID và chủ phiên qua xoay/process death, không serialize model. */
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(NavigationKeys.TRIP_ID, tripId);
        state.putString("detail_uid", uid);
        super.onSaveInstanceState(state);
    }
    /** Không giữ quyền gửi từ snapshot offline hoặc tài khoản cũ. */
    private void render(OnlineTripViewModel.State state) {
        boolean ready = state.online && state.trip != null && sameUser();
        findViewById(R.id.search_trip_content).setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) TripDisplay.bind(this, state.trip, state.driver);
        String reason = ready ? SearchTripPolicy.blockedReason(state.trip, uid) : "";
        ((TextView) findViewById(R.id.search_status)).setText(!sameUser()
                ? "Bạn cần đăng nhập đúng tài khoản đã mở chuyến." : state.message);
        ((TextView) findViewById(R.id.search_action_hint)).setText(reason);
        findViewById(R.id.primary_action).setEnabled(ready && reason.isEmpty());
    }
    /** Mở Review theo trip_id; thiếu đăng ký Manifest vẫn giữ Detail an toàn. */
    private void openReview() {
        OnlineTripViewModel.State state = model.getState().getValue();
        if (state == null || !state.online || !sameUser()) return;
        String reason = SearchTripPolicy.blockedReason(state.trip, uid);
        if (!reason.isEmpty()) { render(state); return; }
        try {
            Intent intent = new Intent(this, RequestReviewActivity.class);
            intent.putExtra(NavigationKeys.TRIP_ID, tripId);
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, "Màn kiểm tra yêu cầu chưa sẵn sàng. Vui lòng thử lại sau.", Toast.LENGTH_LONG).show();
        }
    }
    /** Lấy UID thật, không hardcode tài khoản demo. */
    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
    /** Chặn thao tác sau logout hoặc đổi tài khoản. */
    private boolean sameUser() { return uid != null && uid.equals(currentUid()); }
}
