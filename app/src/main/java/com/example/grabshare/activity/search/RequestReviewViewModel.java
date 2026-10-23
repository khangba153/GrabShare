package com.example.grabshare.activity.search;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.RequestCreationRepository;
import com.example.grabshare.repository.search.RequestCreationRepository.Result;
import com.example.grabshare.repository.search.RequestJourney;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Giữ tác vụ gửi và bản nháp qua xoay, không giữ Activity trong callback. */
public class RequestReviewViewModel extends AndroidViewModel {

    /** checked=false yêu cầu đọc lại server trước khi gửi tiếp. */
    public static class State {

        public final boolean busy;

        public final boolean checked;

        public final Result result;

        public final String message;

        /** Tạo trạng thái cho form và nút gửi. */
        State(boolean busy, boolean checked, Result result, String message) {
            this.busy = busy;
            this.checked = checked;
            this.result = result;
            this.message = message;
        }
    }

    private final RequestCreationRepository repository;

    private final MutableLiveData<State> state =
            new MutableLiveData<State>(new State(false, false, null, ""));

    private String tripId;

    private String userIdentifier;

    private boolean initialized;

    private boolean cleared;

    private boolean navigate;

    public Place pickup;

    public Place dropoff;

    public String message = "";

    /** Dùng applicationContext, không giữ màn đã bị hủy. */
    public RequestReviewViewModel(Application application) {
        super(application);
        repository = new RequestCreationRepository(application);
    }

    /** Quan sát tiến trình và kết quả theo lifecycle. */
    public LiveData<State> getState() {
        return state;
    }

    /** Khởi tạo một lần để xoay không tạo tác vụ thứ hai. */
    public void initialize(String inputTripId, String userId) {
        if (initialized) {
            return;
        }
        initialized = true;
        tripId = inputTripId;
        userIdentifier = userId;
        checkExisting();
    }

    /** Chặn callback vào phiên khác hoặc ViewModel đã đóng. */
    private boolean current() {
        return !cleared
                && FirebaseAuth.getInstance().getCurrentUser() != null
                && FirebaseAuth.getInstance().getCurrentUser().getUid().equals(userIdentifier);
    }

    /** Xác minh request, kể cả khi process chết sau khi server đã nhận lần gửi trước. */
    public void checkExisting() {
        State value = state.getValue();
        if (cleared || (value != null && value.busy)) {
            return;
        }
        state.setValue(new State(true, false, null, "Đang kiểm tra yêu cầu đã gửi…"));
        repository.findExisting(
                tripId,
                userIdentifier,
                new RepositoryCallback<Result>() {

                    /** Khôi phục trạng thái mà không tự mở màn khác. */
                    @Override
                    public void onSuccess(Result result) {
                        if (!current()) {
                            sessionChanged();
                            return;
                        }
                        if (result == null) {
                            state.setValue(new State(false, true, result, ""));
                        } else {
                            restoreJourney(result);
                            String statusMessage = "Bạn đã có yêu cầu cho chuyến này.";
                            if ("CANCELLED".equals(result.status)) {
                                statusMessage = "Bạn đã hủy yêu cầu này. Vui lòng tìm chuyến khác.";
                            }
                            state.setValue(new State(false, true, result, statusMessage));
                        }
                    }

                    /** Lỗi đọc không có nghĩa là chưa tồn tại. */
                    @Override
                    public void onError(Exception error) {
                        if (!current()) {
                            sessionChanged();
                            return;
                        }
                        if (!cleared) {
                            state.setValue(new State(false, false, null, explain(error)));
                        }
                    }
                });
    }

    /** Chỉ gửi theo thao tác người dùng, không gửi từ restore/onCreate. */
    public void submit() {
        State value = state.getValue();
        if (!current()) {
            sessionChanged();
            return;
        }
        if (value == null || value.busy || !value.checked || value.result != null) {
            return;
        }
        try {
            RequestJourney.forNewRequest(pickup, dropoff);
        } catch (IllegalArgumentException invalid) {
            state.setValue(new State(false, true, null, invalid.getMessage()));
            return;
        }
        state.setValue(
                new State(true, true, null, "Đang kiểm tra lại đường đón/trả trước khi gửi…"));
        repository.create(
                tripId,
                userIdentifier,
                pickup,
                dropoff,
                message,
                new RepositoryCallback<Result>() {

                    /** Activity đang RESUMED mới được mở kết quả này. */
                    @Override
                    public void onSuccess(Result result) {
                        if (!current()) {
                            sessionChanged();
                            return;
                        }
                        restoreJourney(result);
                        navigate = true;
                        if (result.existing) {
                            state.setValue(new State(false, true, result, "Yêu cầu đã tồn tại."));
                        } else {
                            state.setValue(
                                    new State(
                                            false,
                                            true,
                                            result,
                                            "Máy chủ đã xác nhận gửi yêu cầu."));
                        }
                    }

                    /** Sau lỗi phải kiểm tra server trước khi gửi lại; bản nháp không bị xóa. */
                    @Override
                    public void onError(Exception error) {
                        if (!current()) {
                            sessionChanged();
                            return;
                        }
                        if (!cleared) {
                            state.setValue(new State(false, false, null, explain(error)));
                        }
                    }
                });
    }

    /** Request đã tồn tại phải phục hồi P/C từ server, không dùng lại Bundle tìm kiếm. */
    private void restoreJourney(Result result) {
        pickup = result.getPickup();
        dropoff = result.getDropoff();
    }

    /** Không tiêu thụ kết quả điều hướng khi Activity đang dừng. */
    public boolean shouldNavigate() {
        return navigate && current();
    }

    /** Chỉ đánh dấu sau khi màn đích mở thành công. */
    public void navigationHandled() {
        navigate = false;
    }

    /** Giữ lỗi phiên rõ ràng, chặn thao tác của tài khoản cũ. */
    private void sessionChanged() {
        if (!cleared) {
            state.setValue(
                    new State(
                            false,
                            false,
                            null,
                            "Phiên đăng nhập đã đổi. Quay lại để mở yêu cầu đúng tài khoản."));
        }
    }

    /** Hiển thị lỗi mà không ghi dữ liệu riêng vào log. */
    private String explain(Exception error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) cause;
                switch (firestoreError.getCode()) {
                    case PERMISSION_DENIED:
                        return "Máy chủ chưa cho phép thao tác này. Vui lòng thử lại sau hoặc báo"
                                + " nhóm hỗ trợ.";
                    case UNAUTHENTICATED:
                        return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                    case UNAVAILABLE:
                    case DEADLINE_EXCEEDED:
                        return "Chưa xác nhận được yêu cầu do kết nối. Vui lòng kiểm tra lại trước"
                                + " khi gửi tiếp.";
                    default:
                        break;
                }
            }
            cause = cause.getCause();
        }
        if (error.getMessage() == null) {
            return "Không xác nhận được yêu cầu. Kiểm tra mạng và thử lại.";
        } else {
            return error.getMessage();
        }
    }

    /** Back không để callback muộn điều hướng từ màn đã đóng. */
    @Override
    protected void onCleared() {
        cleared = true;
        repository.close();
    }
}
