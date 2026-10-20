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

    private String userIdentifier;

    private OnlineTripViewModel tripViewModel;

    private RequestReviewViewModel requestViewModel;

    private EditText messageInput;

    private ActivityResultLauncher<Intent> locationPicker;

    private PassengerSearchRoute passengerSearchRoute;

    /** Đăng ký Activity Result theo thứ tự cố định, nối bản nháp và observer. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_review);
        if (savedInstanceState == null) {
            tripId = getIntent().getStringExtra(NavigationKeys.TRIP_ID);
        } else {
            tripId = savedInstanceState.getString(NavigationKeys.TRIP_ID);
        }
        if (savedInstanceState == null) {
            userIdentifier = currentUserIdentifier();
        } else {
            userIdentifier = savedInstanceState.getString("review_uid");
        }
        Bundle routeArguments = savedInstanceState;
        if (routeArguments == null) {
            routeArguments = getIntent().getExtras();
        }
        passengerSearchRoute = PassengerSearchRoute.read(routeArguments, tripId, userIdentifier);
        tripViewModel = new ViewModelProvider(this).get(OnlineTripViewModel.class);
        requestViewModel = new ViewModelProvider(this).get(RequestReviewViewModel.class);
        locationPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        new ActivityResultCallback<ActivityResult>() {

                            /** Cancel giữ nguyên form; chỉ nhận đủ ba extra hợp lệ của điểm đón. */
                            @Override
                            public void onActivityResult(ActivityResult result) {
                                if (passengerSearchRoute != null) {
                                    return;
                                }
                                if (!sameUser()
                                        || result.getResultCode() != RESULT_OK
                                        || result.getData() == null) {
                                    return;
                                }
                                Intent data = result.getData();
                                if (!data.hasExtra(LocationKeys.PLACE_NAME)
                                        || !data.hasExtra(LocationKeys.PLACE_LATITUDE)
                                        || !data.hasExtra(LocationKeys.PLACE_LONGITUDE)) {
                                    showError("Kết quả chọn địa điểm thiếu thông tin.");
                                    return;
                                }
                                Place place =
                                        new Place(
                                                data.getStringExtra(LocationKeys.PLACE_NAME),
                                                data.getDoubleExtra(
                                                        LocationKeys.PLACE_LATITUDE, Double.NaN),
                                                data.getDoubleExtra(
                                                        LocationKeys.PLACE_LONGITUDE, Double.NaN));
                                if (!SearchTripPolicy.validPlace(place)) {
                                    showError(
                                            "Tên điểm đón tối đa 200 ký tự và phải có tọa độ hợp"
                                                + " lệ.");
                                    return;
                                }
                                requestViewModel.pickup = place;
                                render();
                            }
                        });
        if (sameUser() && savedInstanceState != null && requestViewModel.pickup == null) {
            restoreDraft(savedInstanceState);
        }
        if (sameUser() && passengerSearchRoute != null) {
            requestViewModel.pickup = passengerSearchRoute.getPickup();
        }
        messageInput = findViewById(R.id.review_message);
        if (sameUser()) {
            messageInput.setText(requestViewModel.message);
        } else {
            messageInput.setText("");
        }
        messageInput.addTextChangedListener(
                new TextWatcher() {

                    /** Không gửi tác vụ khi nội dung form thay đổi. */
                    @Override
                    public void beforeTextChanged(
                            CharSequence text, int start, int count, int after) {}

                    /** Bản nháp không ghi Firestore. */
                    @Override
                    public void onTextChanged(
                            CharSequence text, int start, int before, int count) {}

                    /** Giữ lời nhắn trên ViewModel cho lần xoay sau. */
                    @Override
                    public void afterTextChanged(Editable text) {
                        if (sameUser()) {
                            requestViewModel.message = text.toString();
                        }
                    }
                });
        findViewById(R.id.header_back)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Quay lại Detail, không tự gửi yêu cầu. */
                            @Override
                            public void onClick(View view) {
                                finish();
                            }
                        });
        findViewById(R.id.review_pickup)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /**
                             * Review chỉ có một ô chọn: điểm đón, không suy đoán caller của
                             * Location.
                             */
                            @Override
                            public void onClick(View view) {
                                if (sameUser() && passengerSearchRoute == null) {
                                    locationPicker.launch(
                                            new Intent(
                                                    RequestReviewActivity.this,
                                                    LocationActivity.class));
                                }
                            }
                        });
        findViewById(R.id.search_retry)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Tải lại chuyến và đối soát request, không tự gửi lại. */
                            @Override
                            public void onClick(View view) {
                                tripViewModel.start(tripId, userIdentifier);
                                requestViewModel.checkExisting();
                            }
                        });
        findViewById(R.id.primary_action)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Chỉ đường submit này được phép khởi tạo transaction từ giao diện. */
                            @Override
                            public void onClick(View view) {
                                primaryAction();
                            }
                        });
        tripViewModel
                .getState()
                .observe(
                        this,
                        new Observer<OnlineTripViewModel.State>() {

                            /** Render chỉ khi màn có lifecycle hoạt động. */
                            @Override
                            public void onChanged(OnlineTripViewModel.State state) {
                                render();
                            }
                        });
        requestViewModel
                .getState()
                .observe(
                        this,
                        new Observer<RequestReviewViewModel.State>() {

                            /** Giữ kết quả đến khi Activity RESUMED trước khi mở Pending. */
                            @Override
                            public void onChanged(RequestReviewViewModel.State state) {
                                render();
                                if (requestViewModel.shouldNavigate()) {
                                    openRequest();
                                }
                            }
                        });
        requestViewModel.initialize(tripId, userIdentifier);
    }

    /** Listener đọc sống theo màn; tác vụ gửi nằm riêng trong ViewModel qua xoay. */
    @Override
    protected void onStart() {
        super.onStart();
        tripViewModel.start(tripId, userIdentifier);
    }

    /** Chỉ mở kết quả gửi đang chờ khi màn thực sự trở lại foreground. */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (requestViewModel.shouldNavigate()) {
            openRequest();
        }
    }

    /** Không để listener chuyến sống sau khi màn dừng. */
    @Override
    protected void onStop() {
        tripViewModel.stop();
        super.onStop();
    }

    /** Lưu dữ liệu form theo UID và ID; không lưu đối tượng model Serializable. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(NavigationKeys.TRIP_ID, tripId);
        state.putString("review_uid", userIdentifier);
        state.putString("draft_message", requestViewModel.message);
        if (passengerSearchRoute != null) {
            passengerSearchRoute.saveTo(state);
        }
        if (SearchTripPolicy.validPlace(requestViewModel.pickup)) {
            state.putString("pickup_name", requestViewModel.pickup.getName());
            state.putDouble("pickup_latitude", requestViewModel.pickup.getLatitude());
            state.putDouble("pickup_longitude", requestViewModel.pickup.getLongitude());
        }
        super.onSaveInstanceState(state);
    }

    /** Process death chỉ phục hồi bản nháp; initialize sẽ đọc lại request từ máy chủ. */
    private void restoreDraft(Bundle state) {
        requestViewModel.message = state.getString("draft_message", "");
        if (state.containsKey("pickup_name")
                && state.containsKey("pickup_latitude")
                && state.containsKey("pickup_longitude")) {
            Place pickup =
                    new Place(
                            state.getString("pickup_name"),
                            state.getDouble("pickup_latitude"),
                            state.getDouble("pickup_longitude"));
            if (SearchTripPolicy.validPlace(pickup)) {
                requestViewModel.pickup = pickup;
            }
        }
    }

    /** Gắn trạng thái server và khóa form khi đang gửi/đã có request. */
    private void render() {
        OnlineTripViewModel.State trip = tripViewModel.getState().getValue();
        RequestReviewViewModel.State request = requestViewModel.getState().getValue();
        boolean sameAccount = sameUser();
        PassengerSearchRoute.bind(this, passengerSearchRoute, sameAccount);
        int pickupVisibility = View.VISIBLE;
        if (passengerSearchRoute != null) {
            pickupVisibility = View.GONE;
        }
        findViewById(R.id.review_pickup_label).setVisibility(pickupVisibility);
        findViewById(R.id.review_pickup).setVisibility(pickupVisibility);
        findViewById(R.id.review_pickup_value).setVisibility(pickupVisibility);
        boolean ready = sameAccount && trip != null && trip.online && trip.trip != null;
        boolean busy = request != null && request.busy;
        boolean existing = sameAccount && request != null && request.result != null;
        if (sameAccount) {
            findViewById(R.id.review_form).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.review_form).setVisibility(View.GONE);
        }
        if (ready) {
            findViewById(R.id.search_trip_content).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.search_trip_content).setVisibility(View.GONE);
        }
        if (ready) {
            TripDisplay.bind(this, trip.trip, trip.driver);
        }
        if (!sameAccount) {
            ((TextView) findViewById(R.id.search_status))
                    .setText("Phiên đăng nhập đã đổi. Quay lại để mở bằng tài khoản hiện tại.");
        } else {
            if (trip == null) {
                ((TextView) findViewById(R.id.search_status)).setText("Đang tải chuyến…");
            } else {
                ((TextView) findViewById(R.id.search_status)).setText(trip.message);
            }
        }
        if (sameAccount && request != null) {
            ((TextView) findViewById(R.id.review_status)).setText(request.message);
        } else {
            ((TextView) findViewById(R.id.review_status)).setText("");
        }
        if (sameAccount && requestViewModel.pickup != null) {
            ((TextView) findViewById(R.id.review_pickup_value))
                    .setText(requestViewModel.pickup.getName());
        } else {
            ((TextView) findViewById(R.id.review_pickup_value)).setText("Chưa chọn điểm đón");
        }
        messageInput.setEnabled(sameAccount && !busy && !existing);
        findViewById(R.id.review_pickup).setEnabled(sameAccount && !busy && !existing);
        findViewById(R.id.search_retry).setEnabled(sameAccount && !busy);
        String reason;
        if (ready) {
            reason = SearchTripPolicy.blockedReason(trip.trip, userIdentifier);
        } else {
            reason = "";
        }
        if (existing) {
            ((TextView) findViewById(R.id.search_action_hint)).setText("");
        } else {
            ((TextView) findViewById(R.id.search_action_hint)).setText(reason);
        }
        Button button = findViewById(R.id.primary_action);
        boolean checked = request != null && request.checked;
        if (busy) {
            button.setText("Đang xử lý…");
        } else {
            if (existing) {
                button.setText("Xem trạng thái yêu cầu");
            } else {
                if (!checked) {
                    button.setText("Kiểm tra lại yêu cầu");
                } else {
                    button.setText("Gửi yêu cầu");
                }
            }
        }
        button.setEnabled(
                sameAccount && !busy && (existing || (ready && (!checked || reason.isEmpty()))));
    }

    /** Kiểm tra điều kiện mới nhất, không dùng trạng thái nút cũ để cho phép ghi. */
    private void primaryAction() {
        if (!sameUser()) {
            return;
        }
        RequestReviewViewModel.State request = requestViewModel.getState().getValue();
        if (request == null || request.busy) {
            return;
        }
        if (request.result != null) {
            openRequest();
            return;
        }
        if (!request.checked) {
            requestViewModel.checkExisting();
            return;
        }
        OnlineTripViewModel.State trip = tripViewModel.getState().getValue();
        if (trip == null
                || !trip.online
                || !SearchTripPolicy.blockedReason(trip.trip, userIdentifier).isEmpty()) {
            render();
            return;
        }
        if (!SearchTripPolicy.validPlace(requestViewModel.pickup)) {
            showError("Vui lòng chọn điểm đón hợp lệ.");
            return;
        }
        if (passengerSearchRoute != null) {
            requestViewModel.pickup = passengerSearchRoute.getPickup();
        }
        requestViewModel.message = messageInput.getText().toString();
        requestViewModel.submit();
    }

    /** Mở đúng màn Thuận bằng ID đã xác nhận, không tạo Pending bản sao. */
    private void openRequest() {
        if (!sameUser() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return;
        }
        RequestReviewViewModel.State state = requestViewModel.getState().getValue();
        Result result;
        if (state == null) {
            result = null;
        } else {
            result = state.result;
        }
        if (result == null) {
            return;
        }
        try {
            Intent intent = new Intent(this, PendingActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, result.requestId);
            startActivity(intent);
            requestViewModel.navigationHandled();
            finish();
        } catch (ActivityNotFoundException unavailable) {
            requestViewModel.navigationHandled();
            showError("Yêu cầu đã có nhưng màn trạng thái chưa sẵn sàng. Bạn có thể thử mở lại.");
        }
    }

    /** Lấy UID thật để bảo vệ bản nháp và kết quả gửi. */
    private String currentUserIdentifier() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        } else {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    /** Không cho kết quả của phiên cũ cập nhật form hiện tại. */
    private boolean sameUser() {
        return userIdentifier != null && userIdentifier.equals(currentUserIdentifier());
    }

    /** Báo lỗi và giữ nguyên form. */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
