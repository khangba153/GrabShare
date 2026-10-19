package com.example.grabshare.activity.search;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.example.grabshare.R;
import com.example.grabshare.activity.passenger.PendingActivity;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.RequestCreationRepository.Result;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.firebase.auth.FirebaseAuth;

/** Form kiểm tra và gửi yêu cầu một người đi nhờ, giữ tác vụ qua xoay màn. */
public class RequestReviewActivity extends AppCompatActivity {
    private String tripId;
    private String uid;
    private OnlineTripViewModel tripModel;
    private RequestReviewViewModel requestModel;
    private EditText messageInput;
    private ActivityResultLauncher<Intent> locationPicker;

    /** Đăng ký Activity Result theo thứ tự cố định, nối bản nháp và observer. */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_review);
        tripId = savedInstanceState == null ? getIntent().getStringExtra(NavigationKeys.TRIP_ID)
                : savedInstanceState.getString(NavigationKeys.TRIP_ID);
        uid = savedInstanceState == null ? currentUid() : savedInstanceState.getString("review_uid");
        tripModel = new ViewModelProvider(this).get(OnlineTripViewModel.class);
        requestModel = new ViewModelProvider(this).get(RequestReviewViewModel.class);
        locationPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    /** Cancel giữ nguyên form; chỉ nhận đủ ba extra hợp lệ của điểm đón. */
                    @Override public void onActivityResult(ActivityResult result) {
                        if (!sameUser() || result.getResultCode() != RESULT_OK || result.getData() == null) return;
                        Intent data = result.getData();
                        if (!data.hasExtra(LocationKeys.PLACE_NAME)
                                || !data.hasExtra(LocationKeys.PLACE_LATITUDE)
                                || !data.hasExtra(LocationKeys.PLACE_LONGITUDE)) {
                            showError("Kết quả chọn địa điểm thiếu thông tin.");
                            return;
                        }
                        Place place = new Place(data.getStringExtra(LocationKeys.PLACE_NAME),
                                data.getDoubleExtra(LocationKeys.PLACE_LATITUDE, Double.NaN),
                                data.getDoubleExtra(LocationKeys.PLACE_LONGITUDE, Double.NaN));
                        if (!SearchTripPolicy.validPlace(place)) {
                            showError("Tên điểm đón tối đa 200 ký tự và phải có tọa độ hợp lệ.");
                            return;
                        }
                        requestModel.pickup = place;
                        render();
                    }
                });
        if (sameUser() && savedInstanceState != null && requestModel.pickup == null) restoreDraft(savedInstanceState);
        messageInput = findViewById(R.id.review_message);
        messageInput.setText(sameUser() ? requestModel.message : "");
        messageInput.addTextChangedListener(new TextWatcher() {
            /** Không gửi tác vụ khi nội dung form thay đổi. */
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            /** Bản nháp không ghi Firestore. */
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            /** Giữ lời nhắn trên ViewModel cho lần xoay sau. */
            @Override public void afterTextChanged(Editable text) {
                if (sameUser()) requestModel.message = text.toString();
            }
        });
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            /** Quay lại Detail, không tự gửi yêu cầu. */
            @Override public void onClick(View view) { finish(); }
        });
        findViewById(R.id.review_pickup).setOnClickListener(new View.OnClickListener() {
            /** Review chỉ có một ô chọn: điểm đón, không suy đoán caller của Location. */
            @Override public void onClick(View view) {
                if (sameUser()) locationPicker.launch(new Intent(RequestReviewActivity.this, LocationActivity.class));
            }
        });
        findViewById(R.id.search_retry).setOnClickListener(new View.OnClickListener() {
            /** Tải lại chuyến và đối soát request, không tự gửi lại. */
            @Override public void onClick(View view) {
                tripModel.start(tripId, uid);
                requestModel.checkExisting();
            }
        });
        findViewById(R.id.primary_action).setOnClickListener(new View.OnClickListener() {
            /** Chỉ đường submit này được phép khởi tạo transaction từ giao diện. */
            @Override public void onClick(View view) { primaryAction(); }
        });
        tripModel.getState().observe(this, new Observer<OnlineTripViewModel.State>() {
            /** Render chỉ khi màn có lifecycle hoạt động. */
            @Override public void onChanged(OnlineTripViewModel.State state) { render(); }
        });
        requestModel.getState().observe(this, new Observer<RequestReviewViewModel.State>() {
            /** Giữ kết quả đến khi Activity RESUMED trước khi mở Pending. */
            @Override public void onChanged(RequestReviewViewModel.State state) {
                render();
                if (requestModel.shouldNavigate()) openRequest();
            }
        });
        requestModel.initialize(tripId, uid);
    }

    /** Listener đọc sống theo màn; tác vụ gửi nằm riêng trong ViewModel qua xoay. */
    @Override protected void onStart() { super.onStart(); tripModel.start(tripId, uid); }
    /** Chỉ mở kết quả gửi đang chờ khi màn thực sự trở lại foreground. */
    @Override protected void onPostResume() {
        super.onPostResume();
        if (requestModel.shouldNavigate()) openRequest();
    }
    /** Không để listener chuyến sống sau khi màn dừng. */
    @Override protected void onStop() { tripModel.stop(); super.onStop(); }
    /** Lưu dữ liệu form theo UID và ID; không lưu đối tượng model Serializable. */
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(NavigationKeys.TRIP_ID, tripId);
        state.putString("review_uid", uid);
        state.putString("draft_message", requestModel.message);
        if (SearchTripPolicy.validPlace(requestModel.pickup)) {
            state.putString("pickup_name", requestModel.pickup.getName());
            state.putDouble("pickup_latitude", requestModel.pickup.getLatitude());
            state.putDouble("pickup_longitude", requestModel.pickup.getLongitude());
        }
        super.onSaveInstanceState(state);
    }
    /** Process death chỉ phục hồi bản nháp; initialize sẽ đọc lại request từ máy chủ. */
    private void restoreDraft(Bundle state) {
        requestModel.message = state.getString("draft_message", "");
        if (state.containsKey("pickup_name") && state.containsKey("pickup_latitude")
                && state.containsKey("pickup_longitude")) {
            Place pickup = new Place(state.getString("pickup_name"),
                    state.getDouble("pickup_latitude"), state.getDouble("pickup_longitude"));
            if (SearchTripPolicy.validPlace(pickup)) requestModel.pickup = pickup;
        }
    }
    /** Gắn trạng thái server và khóa form khi đang gửi/đã có request. */
    private void render() {
        OnlineTripViewModel.State trip = tripModel.getState().getValue();
        RequestReviewViewModel.State request = requestModel.getState().getValue();
        boolean same = sameUser();
        boolean ready = same && trip != null && trip.online && trip.trip != null;
        boolean busy = request != null && request.busy;
        boolean existing = same && request != null && request.result != null;
        findViewById(R.id.review_form).setVisibility(same ? View.VISIBLE : View.GONE);
        findViewById(R.id.search_trip_content).setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) TripDisplay.bind(this, trip.trip, trip.driver);
        ((TextView) findViewById(R.id.search_status)).setText(!same
                ? "Phiên đăng nhập đã đổi. Quay lại để mở bằng tài khoản hiện tại."
                : trip == null ? "Đang tải chuyến…" : trip.message);
        ((TextView) findViewById(R.id.review_status)).setText(same && request != null ? request.message : "");
        ((TextView) findViewById(R.id.review_pickup_value)).setText(same && requestModel.pickup != null
                ? requestModel.pickup.getName() : "Chưa chọn điểm đón");
        messageInput.setEnabled(same && !busy && !existing);
        findViewById(R.id.review_pickup).setEnabled(same && !busy && !existing);
        findViewById(R.id.search_retry).setEnabled(same && !busy);
        String reason = ready ? SearchTripPolicy.blockedReason(trip.trip, uid) : "";
        ((TextView) findViewById(R.id.search_action_hint)).setText(existing ? "" : reason);
        Button button = findViewById(R.id.primary_action);
        boolean checked = request != null && request.checked;
        button.setText(busy ? "Đang xử lý…" : existing ? "Xem trạng thái yêu cầu"
                : !checked ? "Kiểm tra lại yêu cầu" : "Gửi yêu cầu");
        button.setEnabled(same && !busy && (existing || (ready && (!checked || reason.isEmpty()))));
    }
    /** Kiểm tra điều kiện mới nhất, không dùng trạng thái nút cũ để cho phép ghi. */
    private void primaryAction() {
        if (!sameUser()) return;
        RequestReviewViewModel.State request = requestModel.getState().getValue();
        if (request == null || request.busy) return;
        if (request.result != null) { openRequest(); return; }
        if (!request.checked) { requestModel.checkExisting(); return; }
        OnlineTripViewModel.State trip = tripModel.getState().getValue();
        if (trip == null || !trip.online || !SearchTripPolicy.blockedReason(trip.trip, uid).isEmpty()) {
            render();
            return;
        }
        if (!SearchTripPolicy.validPlace(requestModel.pickup)) { showError("Vui lòng chọn điểm đón hợp lệ."); return; }
        requestModel.message = messageInput.getText().toString();
        requestModel.submit();
    }
    /** Mở đúng màn Thuận bằng ID đã xác nhận, không tạo Pending bản sao. */
    private void openRequest() {
        if (!sameUser() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
        RequestReviewViewModel.State state = requestModel.getState().getValue();
        Result result = state == null ? null : state.result;
        if (result == null) return;
        try {
            Intent intent = new Intent(this, PendingActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, result.requestId);
            startActivity(intent);
            requestModel.navigationHandled();
            finish();
        } catch (ActivityNotFoundException unavailable) {
            requestModel.navigationHandled();
            showError("Yêu cầu đã có nhưng màn trạng thái chưa sẵn sàng. Bạn có thể thử mở lại.");
        }
    }
    /** Lấy UID thật để bảo vệ bản nháp và kết quả gửi. */
    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
    /** Không cho kết quả của phiên cũ cập nhật form hiện tại. */
    private boolean sameUser() { return uid != null && uid.equals(currentUid()); }
    /** Báo lỗi và giữ nguyên form. */
    private void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
