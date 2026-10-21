package com.example.grabshare.activity.passenger;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Rating;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.SingleRequestDetails;
import com.example.grabshare.repository.passenger.RatingRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Màn hoàn thành chỉ cho người được nhận đánh giá, không tự hoàn thành chuyến. */
public class CompleteActivity extends AppCompatActivity {
    private enum RatingState { LOADING, ABSENT, EXISTS, ERROR, UNAVAILABLE }

    private static final String DRAFT_COMMENT = "rating_draft_comment";
    private static final String DRAFT_SCORE = "rating_draft_score";
    private static final String EXPECTED_UID = "rating_expected_uid";
    private static final String SAVED_REQUEST_ID = "rating_request_id";

    private RatingBar ratingBar;
    private EditText ratingComment;
    private TextView ratingStatus;
    private TextView commentCounter;
    private Button primaryAction;
    private Button retryAction;
    private PassengerTripsRepository tripsRepository;
    private RatingRepository ratingRepository;
    private PassengerSessionGuard sessionGuard;
    private ListenerRegistration requestListener;
    private String requestId;
    private String expectedUserId;
    private String checkedTripId;
    private SingleRequestDetails currentDetails;
    private RatingState ratingState = RatingState.LOADING;
    private int ratingReadGeneration;
    private boolean submitting;

    /** Nhận ID và phục hồi bản nháp; tuyệt đối không khôi phục thao tác gửi. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_complete);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        expectedUserId = user.getUid();
        if (savedInstanceState != null) {
            expectedUserId = savedInstanceState.getString(EXPECTED_UID);
            requestId = savedInstanceState.getString(SAVED_REQUEST_ID);
        } else if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        }
        if (!user.getUid().equals(expectedUserId) || requestId == null
                || requestId.trim().isEmpty() || requestId.contains("/")) {
            Toast.makeText(this, "Thiếu mã yêu cầu hoặc phiên đăng nhập đã thay đổi.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        tripsRepository = new PassengerTripsRepository(firestore);
        ratingRepository = new RatingRepository(firestore);
        sessionGuard = new PassengerSessionGuard(this);
        bindViews();
        if (savedInstanceState != null) {
            ratingComment.setText(savedInstanceState.getString(DRAFT_COMMENT, ""));
            ratingBar.setRating(savedInstanceState.getFloat(DRAFT_SCORE, 5.0f));
        }
        updateCommentValidation();
    }

    /** Nối form, trạng thái tải và nút thử lại; giữ lỗi đọc tách biệt với chưa đánh giá. */
    private void bindViews() {
        ratingBar = findViewById(R.id.rating);
        ratingComment = findViewById(R.id.rating_comment);
        ratingStatus = findViewById(R.id.rating_status);
        commentCounter = findViewById(R.id.rating_comment_counter);
        primaryAction = findViewById(R.id.primary_action);
        retryAction = findViewById(R.id.rating_retry);
        ImageButton back = findViewById(R.id.header_back);
        back.setOnClickListener(new View.OnClickListener() {
            /** Back không ghi đánh giá hoặc tự hoàn thành trip. */
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        primaryAction.setOnClickListener(new View.OnClickListener() {
            /** Chỉ gửi khi đã xác nhận chưa có rating và form hợp lệ. */
            @Override
            public void onClick(View view) {
                performSubmitRating();
            }
        });
        retryAction.setOnClickListener(new View.OnClickListener() {
            /** Thử đọc lại giữ nguyên bản nháp và không gọi submit. */
            @Override
            public void onClick(View view) {
                startListeningRequest();
            }
        });
        ratingComment.addTextChangedListener(new TextWatcher() {
            /** Không thay đổi dữ liệu trước khi nhập. */
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            /** Báo lỗi ngay cạnh ô nhận xét, kể cả khi dán 501 ký tự. */
            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateCommentValidation();
            }

            /** Giữ nguyên bản nháp, không tự cắt nhận xét hoặc tự gửi. */
            @Override
            public void afterTextChanged(Editable text) {
            }
        });
    }

    /** Mỗi lần mở lại phải xác nhận trạng thái trên server trước khi cho gửi. */
    @Override
    protected void onStart() {
        super.onStart();
        if (sessionGuard != null && sessionGuard.start(expectedUserId)) {
            submitting = false;
            startListeningRequest();
        }
    }

    /** Vô hiệu callback đọc/gửi của màn cũ trước khi tháo listener. */
    @Override
    protected void onStop() {
        if (sessionGuard != null) {
            sessionGuard.stop();
        }
        ratingReadGeneration++;
        removeRequestListener();
        super.onStop();
    }

    /** Lưu riêng UID, ID và bản nháp; không ghi lại lên Firestore khi xoay. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(EXPECTED_UID, expectedUserId);
        state.putString(SAVED_REQUEST_ID, requestId);
        if (ratingComment != null) {
            state.putString(DRAFT_COMMENT, ratingComment.getText().toString());
            state.putFloat(DRAFT_SCORE, ratingBar.getRating());
        }
        super.onSaveInstanceState(state);
    }

    /** Đọc request/trip và xác minh đúng passenger trước khi đọc rating của họ. */
    private void startListeningRequest() {
        final int sessionToken = sessionGuard.token();
        if (!sessionGuard.current(sessionToken) || submitting) {
            return;
        }
        removeRequestListener();
        ratingReadGeneration++;
        checkedTripId = null;
        currentDetails = null;
        showState(RatingState.LOADING, "Đang tải chuyến đi và xác nhận đánh giá…");
        requestListener = tripsRepository.listenSingleRequest(requestId,
                new RepositoryCallback<SingleRequestDetails>() {
                    /** Bỏ dữ liệu của phiên hoặc màn cũ; chỉ mở form cho đúng người được nhận. */
                    @Override
                    public void onSuccess(SingleRequestDetails details) {
                        if (!sessionGuard.current(sessionToken)) {
                            return;
                        }
                        currentDetails = details;
                        if (!eligible(details)) {
                            ratingReadGeneration++;
                            checkedTripId = null;
                            showState(RatingState.UNAVAILABLE,
                                    "Chỉ người được nhận trên chuyến đã hoàn thành mới được đánh giá.");
                            return;
                        }
                        bindTrip(details);
                        if (!details.getTrip().getId().equals(checkedTripId) && !submitting) {
                            checkExistingRating();
                        }
                    }

                    /** Cho phép thử lại lỗi tải chuyến, không mở nút gửi từ dữ liệu cũ. */
                    @Override
                    public void onError(Exception error) {
                        if (!sessionGuard.current(sessionToken)) {
                            return;
                        }
                        ratingReadGeneration++;
                        currentDetails = null;
                        showState(RatingState.ERROR, "Không tải được chuyến đi. Hãy thử lại.");
                    }
                });
    }

    /** Cùng điều kiện với state contract; Rules tiếp tục xác minh nguyên tử ở server. */
    private boolean eligible(SingleRequestDetails details) {
        if (details == null || details.getRequest() == null || details.getTrip() == null) {
            return false;
        }
        TripRequest request = details.getRequest();
        Trip trip = details.getTrip();
        return expectedUserId.equals(request.getPassengerId())
                && requestId.equals(request.getId()) && requestId.equals(trip.getAcceptedRequestId())
                && "ACCEPTED".equals(request.getStatus()) && "COMPLETED".equals(trip.getStatus())
                && trip.getId() != null && trip.getId().equals(request.getTripId())
                && trip.getDriverId() != null && trip.getDriverId().equals(request.getDriverId())
                && trip.getAvailableSeats() != null && trip.getAvailableSeats() == 0L;
    }

    /** Dùng số thế hệ riêng để lần đọc cũ không ghi đè kết quả thử lại mới hơn. */
    private void checkExistingRating() {
        if (!eligible(currentDetails)) {
            return;
        }
        final String tripId = currentDetails.getTrip().getId();
        checkedTripId = tripId;
        final int readToken = ++ratingReadGeneration;
        final int sessionToken = sessionGuard.token();
        showState(RatingState.LOADING, "Đang xác nhận đánh giá trên máy chủ…");
        ratingRepository.getRating(tripId, expectedUserId, new RepositoryCallback<Rating>() {
            /** Không xóa bản nháp khi server xác nhận chưa có rating. */
            @Override
            public void onSuccess(Rating rating) {
                if (!currentRatingRead(sessionToken, readToken, tripId)) {
                    return;
                }
                if (rating == null) {
                    showState(RatingState.ABSENT, "Bạn chưa đánh giá chuyến đi này.");
                } else {
                    showSavedRating(rating);
                }
            }

            /** Lỗi đọc luôn khóa gửi và có nút thử lại, không giả là chưa đánh giá. */
            @Override
            public void onError(Exception error) {
                if (currentRatingRead(sessionToken, readToken, tripId)) {
                    showState(RatingState.ERROR, "Không đọc được đánh giá. Hãy thử lại; bản nháp vẫn được giữ.");
                }
            }
        });
    }

    /** Kiểm tra đủ phiên, thế hệ đọc và chuyến đang hiển thị ngay trước khi cập nhật UI. */
    private boolean currentRatingRead(int sessionToken, int readToken, String tripId) {
        return sessionGuard.current(sessionToken) && readToken == ratingReadGeneration
                && eligible(currentDetails) && tripId.equals(currentDetails.getTrip().getId());
    }

    /** Hiển thị rating đã có ở chế độ chỉ xem, không thay bằng điểm mặc định giả. */
    private void showSavedRating(Rating rating) {
        if (rating.getScore() == null || rating.getScore() < 1L || rating.getScore() > 5L) {
            showState(RatingState.ERROR, "Dữ liệu đánh giá không hợp lệ. Hãy thử lại.");
            return;
        }
        ratingBar.setRating(rating.getScore().floatValue());
        String comment = "";
        if (rating.getComment() != null) {
            comment = rating.getComment();
        }
        ratingComment.setText(comment);
        showState(RatingState.EXISTS, "Bạn đã đánh giá chuyến đi này.");
    }

    /** Kiểm tra 500 ký tự trước mọi lệnh gửi, không clamp điểm sai thành điểm hợp lệ. */
    private void performSubmitRating() {
        final int sessionToken = sessionGuard.token();
        if (!sessionGuard.current(sessionToken) || submitting
                || ratingState != RatingState.ABSENT || !eligible(currentDetails)) {
            return;
        }
        String comment = ratingComment.getText().toString();
        if (!updateCommentValidation()) {
            ratingComment.requestFocus();
            return;
        }
        float selectedScore = ratingBar.getRating();
        if (selectedScore < 1.0f || selectedScore > 5.0f || selectedScore != Math.round(selectedScore)) {
            ratingStatus.setText("Vui lòng chọn từ 1 đến 5 sao nguyên.");
            return;
        }
        final Trip trip = currentDetails.getTrip();
        submitting = true;
        ratingReadGeneration++;
        showState(RatingState.ABSENT, "Đang gửi đánh giá…");
        ratingRepository.submitRating(trip.getId(), expectedUserId, trip.getDriverId(),
                Math.round(selectedScore), comment, new RepositoryCallback<Void>() {
                    /** Chỉ báo thành công trên màn thuộc đúng phiên đã gửi. */
                    @Override
                    public void onSuccess(Void unused) {
                        if (!sessionGuard.current(sessionToken)) {
                            return;
                        }
                        submitting = false;
                        showState(RatingState.EXISTS, "Đã gửi đánh giá. Cảm ơn bạn!");
                    }

                    /** Gửi lỗi phải xác minh lại rating trước khi cho gửi tiếp; giữ bản nháp. */
                    @Override
                    public void onError(Exception error) {
                        if (!sessionGuard.current(sessionToken)) {
                            return;
                        }
                        submitting = false;
                        showState(RatingState.ERROR,
                                "Chưa xác nhận gửi thành công. Thử lại để đọc trạng thái trước khi gửi tiếp.");
                    }
                });
    }

    /** Báo giới hạn tại ô nhập và đếm ký tự; không cắt mất phần bản nháp bị quá dài. */
    private boolean updateCommentValidation() {
        int length = ratingComment.getText().length();
        commentCounter.setText(length + "/" + RatingRepository.MAX_COMMENT_LENGTH + " ký tự");
        if (length > RatingRepository.MAX_COMMENT_LENGTH) {
            ratingComment.setError("Nhận xét tối đa 500 ký tự.");
            return false;
        }
        ratingComment.setError(null);
        return true;
    }

    /** Biểu diễn rõ trạng thái đọc và chỉ bật gửi ở trạng thái chưa có đã xác nhận. */
    private void showState(RatingState state, String message) {
        ratingState = state;
        ratingStatus.setText(message);
        retryAction.setVisibility(View.GONE);
        if (state == RatingState.ERROR) {
            retryAction.setVisibility(View.VISIBLE);
        }
        boolean editable = state != RatingState.EXISTS && !submitting;
        ratingBar.setIsIndicator(!editable);
        ratingComment.setEnabled(editable);
        primaryAction.setEnabled(state == RatingState.ABSENT && !submitting);
        primaryAction.setText("Gửi đánh giá");
        if (submitting) {
            primaryAction.setText("Đang gửi…");
        } else if (state == RatingState.EXISTS) {
            primaryAction.setText("Đã đánh giá");
        }
    }

    /** Hiển thị dữ liệu công khai thật; thiếu dữ liệu dùng nhãn trung tính. */
    private void bindTrip(SingleRequestDetails details) {
        Trip trip = details.getTrip();
        TripRequest request = details.getRequest();
        PublicProfile profile = details.getDriverProfile();
        String displayName = "Chủ xe";
        String vehicleName = "Chưa có tên xe";
        if (profile != null) {
            displayName = textOr(profile.getDisplayName(), displayName);
            vehicleName = textOr(profile.getVehicleName(), vehicleName);
        }
        setText(R.id.driver_name, displayName);
        setText(R.id.driver_meta, vehicleName);
        setText(R.id.trip_time, "Chưa có giờ khởi hành");
        if (trip.getDepartureAt() != null) {
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd/MM · HH:mm", new Locale("vi", "VN"));
            setText(R.id.trip_time, formatter.format(trip.getDepartureAt().toDate()));
        }
        setText(R.id.trip_seats, "Đã hoàn thành");
        setText(R.id.route_start, "Chưa có điểm đi");
        if (trip.getOrigin() != null) {
            setText(R.id.route_start, textOr(trip.getOrigin().getName(), "Chưa có điểm đi"));
        }
        setText(R.id.route_end, "Chưa có điểm đến");
        if (trip.getDestination() != null) {
            setText(R.id.route_end, textOr(trip.getDestination().getName(), "Chưa có điểm đến"));
        }
        setText(R.id.trip_pickup, "Chưa có điểm đón");
        if (request.getPickup() != null) {
            setText(R.id.trip_pickup, "Điểm đón: " + textOr(request.getPickup().getName(), "Chưa có điểm đón"));
        }
        setText(R.id.trip_cost, "Chưa có chi phí");
        if (trip.getCostVnd() != null) {
            setText(R.id.trip_cost, new DecimalFormat("#,###").format(trip.getCostVnd()) + "đ");
        }
    }

    /** Gán nội dung vào TextView của phần tóm tắt chuyến thuộc màn này. */
    private void setText(int viewId, String text) {
        TextView view = findViewById(viewId);
        if (view != null) {
            view.setText(text);
        }
    }

    /** Trả nhãn trung tính khi dữ liệu công khai bị thiếu, không tạo dữ liệu demo. */
    private String textOr(String text, String fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        return text.trim();
    }

    /** Tháo listener trước khi thử lại hoặc dừng màn để không nhân đôi cập nhật. */
    private void removeRequestListener() {
        if (requestListener != null) {
            requestListener.remove();
            requestListener = null;
        }
    }
}
