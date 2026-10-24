package com.example.grabshare.activity.search;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import com.example.grabshare.contract.CachedTrip;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.RequestCreationRepository;
import com.example.grabshare.repository.search.RequestCreationRepository.Result;
import com.example.grabshare.repository.search.RequestQuote;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.firebase.auth.FirebaseAuth;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Một màn xem chuyến, yêu thích, xác nhận P/C và gửi yêu cầu; offline chỉ xem bản lưu. */
public class TripDetailActivity extends AppCompatActivity {

    private String tripId;

    private String userIdentifier;

    private OnlineTripViewModel tripViewModel;

    private RequestReviewViewModel requestViewModel;

    private TripDetailSupportViewModel supportViewModel;

    private TripContributionViewModel contributionViewModel;

    private EditText messageInput;

    private ActivityResultLauncher<Intent> locationPicker;

    private PassengerSearchRoute passengerSearchRoute;

    private String pickingField;

    /** Đăng ký Activity Result theo thứ tự cố định, nối bản nháp và observer. */
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
            if (userIdentifier == null) {
                userIdentifier = savedInstanceState.getString("review_uid");
            }
        }
        Bundle routeArguments = savedInstanceState;
        if (routeArguments == null) {
            routeArguments = getIntent().getExtras();
        }
        passengerSearchRoute = PassengerSearchRoute.read(routeArguments, tripId, userIdentifier);
        if (savedInstanceState != null) {
            pickingField = savedInstanceState.getString("review_picking_field");
        }
        tripViewModel = new ViewModelProvider(this).get(OnlineTripViewModel.class);
        requestViewModel = new ViewModelProvider(this).get(RequestReviewViewModel.class);
        supportViewModel = new ViewModelProvider(this).get(TripDetailSupportViewModel.class);
        contributionViewModel = new ViewModelProvider(this).get(TripContributionViewModel.class);
        ((CheckBox) findViewById(R.id.detail_confirm_contribution))
                .setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            /** Đồng ý chỉ gắn với quote đang hiện, không tự gửi yêu cầu. */
                            @Override
                            public void onCheckedChanged(CompoundButton button, boolean checked) {
                                contributionViewModel.confirm(checked);
                            }
                        });
        locationPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        new ActivityResultCallback<ActivityResult>() {

                            /** Giữ đúng ô đang chọn qua xoay; Cancel không thay đổi P/C. */
                            @Override
                            public void onActivityResult(ActivityResult result) {
                                String selectedField = pickingField;
                                pickingField = null;
                                RequestReviewViewModel.State request =
                                        requestViewModel.getState().getValue();
                                if (passengerSearchRoute != null
                                        || request == null
                                        || request.busy
                                        || request.result != null) {
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
                                            "Tên địa điểm tối đa 200 ký tự và phải có tọa độ hợp"
                                                    + " lệ.");
                                    return;
                                }
                                if ("pickup".equals(selectedField)) {
                                    requestViewModel.pickup = place;
                                } else {
                                    if (!"dropoff".equals(selectedField)) {
                                        showError(
                                                "Không xác định được ô chọn địa điểm. Hãy chọn"
                                                        + " lại.");
                                        return;
                                    }
                                    requestViewModel.dropoff = place;
                                }
                                render();
                            }
                        });
        if (sameUser() && savedInstanceState != null && requestViewModel.pickup == null) {
            restoreDraft(savedInstanceState);
        }
        RequestReviewViewModel.State initialRequest = requestViewModel.getState().getValue();
        if (sameUser()
                && passengerSearchRoute != null
                && initialRequest != null
                && !initialRequest.busy
                && initialRequest.result == null) {
            requestViewModel.pickup = passengerSearchRoute.getPickup();
            requestViewModel.dropoff = passengerSearchRoute.getDropoff();
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

                            /** Trở về nơi đã mở chuyến, không tự gửi yêu cầu. */
                            @Override
                            public void onClick(View view) {
                                finish();
                            }
                        });
        findViewById(R.id.review_pickup)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Chỉ thay P khi mở Review trực tiếp, chưa có tuyến tìm kiếm. */
                            @Override
                            public void onClick(View view) {
                                pickLocation("pickup");
                            }
                        });
        findViewById(R.id.review_dropoff)
                .setOnClickListener(
                        new View.OnClickListener() {
                            /** Chọn C rõ ràng; không mặc định request mới dùng D của chủ xe. */
                            @Override
                            public void onClick(View view) {
                                pickLocation("dropoff");
                            }
                        });
        findViewById(R.id.search_retry)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Tải lại chuyến và đối soát request, không tự gửi lại. */
                            @Override
                            public void onClick(View view) {
                                contributionViewModel.retry();
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
        findViewById(R.id.detail_favorite)
                .setOnClickListener(
                        new View.OnClickListener() {
                            /** Chỉ đổi trạng thái yêu thích sau xác nhận server. */
                            @Override
                            public void onClick(View view) {
                                supportViewModel.toggleFavorite();
                            }
                        });
        supportViewModel
                .getState()
                .observe(
                        this,
                        new Observer<TripDetailSupportViewModel.State>() {
                            /** Trạng thái mạng/cache/tim thay đổi phải khóa quyền gửi tương ứng. */
                            @Override
                            public void onChanged(TripDetailSupportViewModel.State state) {
                                render();
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
                                supportViewModel.onTripState(state);
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
        contributionViewModel
                .getState()
                .observe(
                        this,
                        new Observer<TripContributionViewModel.State>() {
                            /** Kết quả Routing chỉ cập nhật giao diện của màn còn hoạt động. */
                            @Override
                            public void onChanged(TripContributionViewModel.State state) {
                                render();
                            }
                        });
    }

    /** Listener đọc sống theo màn; tác vụ gửi nằm riêng trong ViewModel qua xoay. */
    @Override
    protected void onStart() {
        super.onStart();
        supportViewModel.start(tripId, userIdentifier);
        tripViewModel.start(tripId, userIdentifier);
        RequestReviewViewModel.State request = requestViewModel.getState().getValue();
        if (request != null && request.result != null && !requestViewModel.shouldNavigate()) {
            requestViewModel.checkExisting();
        }
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
        supportViewModel.stop();
        tripViewModel.stop();
        super.onStop();
    }

    /** Lưu dữ liệu form theo UID và ID; không lưu đối tượng model Serializable. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(NavigationKeys.TRIP_ID, tripId);
        state.putString("detail_uid", userIdentifier);
        state.putString("draft_message", requestViewModel.message);
        state.putString("review_picking_field", pickingField);
        if (passengerSearchRoute != null) {
            passengerSearchRoute.saveTo(state);
        }
        if (SearchTripPolicy.validPlace(requestViewModel.pickup)) {
            state.putString("pickup_name", requestViewModel.pickup.getName());
            state.putDouble("pickup_latitude", requestViewModel.pickup.getLatitude());
            state.putDouble("pickup_longitude", requestViewModel.pickup.getLongitude());
        }
        if (SearchTripPolicy.validPlace(requestViewModel.dropoff)) {
            state.putString("dropoff_name", requestViewModel.dropoff.getName());
            state.putDouble("dropoff_latitude", requestViewModel.dropoff.getLatitude());
            state.putDouble("dropoff_longitude", requestViewModel.dropoff.getLongitude());
        }
        super.onSaveInstanceState(state);
    }

    /** Process death chỉ phục hồi bản nháp; initialize sẽ đọc lại request từ máy chủ. */
    private void restoreDraft(Bundle state) {
        requestViewModel.message = state.getString("draft_message", "");
        requestViewModel.pickup = readDraftPlace(state, "pickup");
        requestViewModel.dropoff = readDraftPlace(state, "dropoff");
    }

    /** Khôi phục tọa độ có kiểm tra, không biến field bị thiếu thành vị trí (0,0). */
    private Place readDraftPlace(Bundle state, String field) {
        Place place =
                new Place(
                        state.getString(field + "_name"),
                        state.getDouble(field + "_latitude", Double.NaN),
                        state.getDouble(field + "_longitude", Double.NaN));
        if (SearchTripPolicy.validPlace(place)) {
            return place;
        }
        return null;
    }

    /** Khóa lựa chọn khi đang gửi/đã có request và ghi nhớ đúng ô trước khi mở Location. */
    private void pickLocation(String field) {
        RequestReviewViewModel.State request = requestViewModel.getState().getValue();
        if (!sameUser()
                || passengerSearchRoute != null
                || request == null
                || request.busy
                || request.result != null) {
            return;
        }
        pickingField = field;
        locationPicker.launch(new Intent(this, LocationActivity.class));
    }

    /** Gắn trạng thái server và khóa form khi đang gửi/đã có request. */
    private void render() {
        OnlineTripViewModel.State trip = tripViewModel.getState().getValue();
        RequestReviewViewModel.State request = requestViewModel.getState().getValue();
        TripDetailSupportViewModel.State supportState = supportViewModel.getState().getValue();
        boolean sameAccount = sameUser();
        boolean existing = sameAccount && request != null && request.result != null;
        if (existing) {
            PassengerSearchRoute.bind(
                    this, request.result.getPickup(), request.result.getDropoff(), sameAccount);
        } else {
            PassengerSearchRoute.bind(this, passengerSearchRoute, sameAccount);
        }
        int pickupVisibility = View.VISIBLE;
        if (passengerSearchRoute != null || existing) {
            pickupVisibility = View.GONE;
        }
        findViewById(R.id.review_pickup_label).setVisibility(pickupVisibility);
        findViewById(R.id.review_pickup).setVisibility(pickupVisibility);
        findViewById(R.id.review_pickup_value).setVisibility(pickupVisibility);
        findViewById(R.id.review_dropoff_label).setVisibility(pickupVisibility);
        findViewById(R.id.review_dropoff).setVisibility(pickupVisibility);
        findViewById(R.id.review_dropoff_value).setVisibility(pickupVisibility);
        boolean ready =
                sameAccount
                        && trip != null
                        && trip.online
                        && trip.trip != null
                        && supportState != null
                        && supportState.onlineReady;
        CachedTrip cachedTrip = null;
        if (!ready && sameAccount && supportState != null) {
            cachedTrip = supportState.cachedTrip;
        }
        boolean busy = request != null && request.busy;
        if (ready && !existing && request != null && request.checked) {
            contributionViewModel.update(
                    trip.trip, userIdentifier, requestViewModel.pickup, requestViewModel.dropoff);
        } else {
            contributionViewModel.clear(
                    "Khoản dự kiến cần thông tin chuyến mới nhất và kết nối Internet.");
        }
        renderContribution(existing, ready, sameAccount, request);
        if (ready) {
            findViewById(R.id.review_form).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.review_form).setVisibility(View.GONE);
        }
        if (ready || cachedTrip != null) {
            findViewById(R.id.search_trip_content).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.search_trip_content).setVisibility(View.GONE);
        }
        if (ready) {
            TripDisplay.bind(this, trip.trip, trip.driver);
        } else {
            if (cachedTrip != null) {
                TripDisplay.bindCached(this, cachedTrip);
            }
        }
        if (existing && request.result.contribution != null) {
            ((TextView) findViewById(R.id.search_trip_cost))
                    .setText(formatContribution(request.result.contribution.getFullTripCostVnd()));
            ((TextView) findViewById(R.id.search_trip_cost_label))
                    .setText("Mức toàn tuyến lúc xác nhận");
        } else {
            ((TextView) findViewById(R.id.search_trip_cost_label))
                    .setText("Mức tham khảo toàn tuyến hiện tại");
        }
        renderFavorite(ready, supportState);
        if (!sameAccount) {
            ((TextView) findViewById(R.id.search_status))
                    .setText("Phiên đăng nhập đã đổi. Quay lại để mở bằng tài khoản hiện tại.");
        } else {
            if (cachedTrip != null) {
                String savedTime =
                        new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"))
                                .format(cachedTrip.getSavedAt());
                ((TextView) findViewById(R.id.search_status))
                        .setText(
                                "Ngoại tuyến · Bản lưu lúc "
                                        + savedTime
                                        + ". Chỉ xem, thông tin có thể đã thay đổi.");
            } else {
                if (trip == null) {
                    ((TextView) findViewById(R.id.search_status)).setText("Đang tải chuyến…");
                } else {
                    ((TextView) findViewById(R.id.search_status)).setText(trip.message);
                }
            }
        }
        if (sameAccount && request != null) {
            ((TextView) findViewById(R.id.review_status)).setText(request.message);
        } else {
            ((TextView) findViewById(R.id.review_status)).setText("");
        }
        showDraftPlace(
                R.id.review_pickup_value,
                requestViewModel.pickup,
                sameAccount,
                "Chưa chọn điểm đón");
        showDraftPlace(
                R.id.review_dropoff_value,
                requestViewModel.dropoff,
                sameAccount,
                "Chưa chọn điểm xuống");
        messageInput.setEnabled(ready && !busy && !existing);
        findViewById(R.id.review_pickup).setEnabled(ready && !busy && !existing);
        findViewById(R.id.review_dropoff).setEnabled(ready && !busy && !existing);
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
                if ("CANCELLED".equals(request.result.status)) {
                    button.setText("Tìm chuyến khác");
                } else {
                    button.setText("Xem trạng thái yêu cầu");
                }
            } else {
                if (!checked) {
                    button.setText("Kiểm tra lại yêu cầu");
                } else {
                    button.setText("Gửi yêu cầu");
                }
            }
        }
        button.setEnabled(
                ready
                        && !busy
                        && (existing
                                || !checked
                                || (RequestCreationRepository.CONTRIBUTION_SUBMISSION_ENABLED
                                        && contributionViewModel.getConfirmedQuote() != null
                                        && reason.isEmpty())));
        if (ready && !existing && checked && !busy) {
            TripContributionViewModel.State contributionState =
                    contributionViewModel.getState().getValue();
            if (contributionState != null && contributionState.busy) {
                button.setText("Đang tính khoản đóng góp…");
            } else {
                button.setText("Gửi yêu cầu");
            }
            if (reason.isEmpty()
                    && contributionState != null
                    && contributionState.contribution != null) {
                String hint = "Vui lòng xác nhận khoản đóng góp trước khi gửi.";
                if (!RequestCreationRepository.CONTRIBUTION_SUBMISSION_ENABLED) {
                    hint = "Chưa thể gửi với khoản đóng góp mới. Vui lòng thử lại sau.";
                }
                ((TextView) findViewById(R.id.search_action_hint)).setText(hint);
            }
        }
    }

    /** Phân biệt quote mới, tiền lịch sử và yêu cầu legacy; không bù tiền từ Trip. */
    private void renderContribution(
            boolean existing,
            boolean ready,
            boolean sameAccount,
            RequestReviewViewModel.State request) {
        View card = findViewById(R.id.detail_contribution_card);
        if (!sameAccount) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        ContributionSnapshot contribution = null;
        String message;
        String title;
        Long detourMeters = null;
        if (existing) {
            contribution = request.result.contribution;
            title = "Khoản đóng góp đã xác nhận";
            message = "Giữ nguyên theo yêu cầu đã gửi, không tính lại theo giá chuyến hiện tại.";
            if (contribution == null) {
                message = "Yêu cầu cũ chưa có khoản đóng góp được xác nhận.";
            }
        } else {
            title = "Khoản bạn dự kiến đóng góp";
            TripContributionViewModel.State value = contributionViewModel.getState().getValue();
            message = "Cần kết nối Internet để tính khoản đóng góp.";
            if (value != null) {
                message = value.message;
                if (ready) {
                    contribution = value.contribution;
                    detourMeters = value.detourMeters;
                }
            }
        }
        ((TextView) findViewById(R.id.detail_contribution_title)).setText(title);
        ((TextView) findViewById(R.id.detail_contribution_status)).setText(message);
        String amount = "Chưa có khoản đóng góp được xác nhận";
        String passengerDistance = "Chặng bạn đi: chưa tính được";
        String detour = "";
        if (contribution != null) {
            amount = formatContribution(contribution.getAmountVnd());
            passengerDistance =
                    "Chặng bạn đi: "
                            + RouteDistanceFormatter.formatKilometers(
                                    contribution.getPassengerDistanceMeters());
        }
        if (detourMeters != null) {
            detour = "Tài xế đi thêm: " + RouteDistanceFormatter.formatKilometers(detourMeters);
        }
        ((TextView) findViewById(R.id.detail_contribution_amount)).setText(amount);
        ((TextView) findViewById(R.id.detail_passenger_distance)).setText(passengerDistance);
        ((TextView) findViewById(R.id.detail_driver_detour)).setText(detour);
        CheckBox confirmation = findViewById(R.id.detail_confirm_contribution);
        if (existing) {
            confirmation.setVisibility(View.GONE);
        } else {
            confirmation.setVisibility(View.VISIBLE);
        }
        confirmation.setEnabled(
                ready && !existing && contribution != null && request != null && !request.busy);
        confirmation.setChecked(!existing && contributionViewModel.getConfirmedQuote() != null);
    }

    /** Không dùng số 0 cho khoản chưa tính; chỉ gọi khi có snapshot hợp lệ. */
    private static String formatContribution(long amountVnd) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(amountVnd) + "đ";
    }

    /** Dùng chung cách hiện P/C, không lộ bản nháp khi tài khoản đã đổi. */
    private void showDraftPlace(
            int viewIdentifier, Place place, boolean sameAccount, String emptyText) {
        TextView textView = findViewById(viewIdentifier);
        if (sameAccount && place != null) {
            textView.setText(place.getName());
        } else {
            textView.setText(emptyText);
        }
    }

    /** Giữ chức năng yêu thích của Detail và thông báo cache trên cùng màn gửi yêu cầu. */
    private void renderFavorite(boolean ready, TripDetailSupportViewModel.State supportState) {
        Button favoriteButton = findViewById(R.id.detail_favorite);
        favoriteButton.setEnabled(ready && supportState != null && !supportState.favoriteBusy);
        if (supportState == null || supportState.favorite == null) {
            favoriteButton.setText("Tải trạng thái yêu thích");
        } else {
            if (supportState.favorite) {
                favoriteButton.setText("♥ Bỏ yêu thích");
            } else {
                favoriteButton.setText("♡ Thêm yêu thích");
            }
        }
        TextView status = findViewById(R.id.detail_favorite_status);
        String message = "";
        if (supportState != null) {
            message = (supportState.favoriteMessage + " " + supportState.cacheMessage).trim();
        }
        status.setText(message);
        if (message.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
        }
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
            if ("CANCELLED".equals(request.result.status)) {
                Intent homeIntent = new Intent(this, HomeActivity.class);
                homeIntent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(homeIntent);
                finish();
                return;
            }
            openRequest();
            return;
        }
        if (!request.checked) {
            requestViewModel.checkExisting();
            return;
        }
        OnlineTripViewModel.State trip = tripViewModel.getState().getValue();
        TripDetailSupportViewModel.State supportState = supportViewModel.getState().getValue();
        if (trip == null
                || !trip.online
                || supportState == null
                || !supportState.onlineReady
                || !SearchTripPolicy.blockedReason(trip.trip, userIdentifier).isEmpty()) {
            render();
            return;
        }
        RequestQuote confirmedQuote = contributionViewModel.getConfirmedQuote();
        if (confirmedQuote == null
                || !confirmedQuote.matches(
                        trip.trip,
                        userIdentifier,
                        requestViewModel.pickup,
                        requestViewModel.dropoff)) {
            contributionViewModel.retry();
            render();
            showError("Hãy xem và xác nhận lại khoản đóng góp trước khi gửi.");
            return;
        }
        if (!RequestCreationRepository.CONTRIBUTION_SUBMISSION_ENABLED) {
            showError("Chưa thể gửi với khoản đóng góp mới. Vui lòng thử lại sau.");
            return;
        }
        requestViewModel.submit(confirmedQuote);
    }

    /** Gửi thành công chỉ mở trạng thái; màn trạng thái xử lý việc quay về Home. */
    private void openRequest() {
        if (!sameUser() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return;
        }
        RequestReviewViewModel.State state = requestViewModel.getState().getValue();
        if (state == null || state.result == null) {
            return;
        }
        Result result = state.result;
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
