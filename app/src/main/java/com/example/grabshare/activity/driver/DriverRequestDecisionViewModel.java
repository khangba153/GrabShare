package com.example.grabshare.activity.driver;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.driver.DriverRequestDecisionRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/** Giữ một tiến trình phản hồi qua xoay màn, không giữ Activity hoặc tự gửi lại khi mở màn. */
public final class DriverRequestDecisionViewModel extends ViewModel {
    public static final class State {
        public final boolean busy;
        public final boolean completed;
        public final String message;
        public final String confirmedRequestStatus;

        /** Kết quả chỉ được thông báo một lần sau khi màn trở lại RESUMED. */
        State(boolean busy, boolean completed, String message) {
            this(busy, completed, message, "");
        }

        /** Chỉ giữ mã trạng thái khi repository xác nhận thao tác đã thành công. */
        State(boolean busy, boolean completed, String message, String confirmedRequestStatus) {
            this.busy = busy;
            this.completed = completed;
            this.message = message;
            this.confirmedRequestStatus = confirmedRequestStatus;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<State>(
            new State(false, false, ""));
    private final FirebaseAuth authentication = FirebaseAuth.getInstance();
    private final DriverRequestDecisionRepository repository = new DriverRequestDecisionRepository(
            FirebaseFirestore.getInstance(), authentication);
    private boolean closed;

    /** Giữ cùng tác vụ hủy nhận qua xoay và khóa nhấn kép như thao tác nhận/từ chối. */
    public void releaseAcceptedPassenger(String requestIdentifier) {
        State currentState = state.getValue();
        if (closed || (currentState != null && currentState.busy)) {
            return;
        }
        FirebaseUser currentUser = authentication.getCurrentUser();
        if (currentUser == null) {
            state.setValue(new State(false, true, "Bạn cần đăng nhập lại."));
            return;
        }
        final String driverIdentifier = currentUser.getUid();
        state.setValue(new State(true, false, "Đang hủy nhận khách và mở lại chuyến…"));
        new com.example.grabshare.repository.driver.DriverPassengerReleaseRepository()
                .release(requestIdentifier, driverIdentifier, new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        finish(driverIdentifier, "Khách này không còn giữ chỗ. Bạn có thể chọn yêu cầu khác khi chuyến còn mở.", "CANCELLED");
                    }

                    @Override
                    public void onError(Exception error) {
                        String message = "Chưa xác nhận được hủy nhận khách. Kiểm tra mạng và thử lại.";
                        if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                            message = error.getMessage();
                        }
                        finish(driverIdentifier, message);
                    }
                });
    }

    /** Activity quan sát trạng thái, không sở hữu tác vụ định tuyến hoặc transaction. */
    public LiveData<State> getState() {
        return state;
    }

    /** Chỉ bắt đầu từ thao tác xác nhận; bấm kép không khởi tạo quyết định thứ hai. */
    public void submit(String requestIdentifier, final boolean accepting) {
        State currentState = state.getValue();
        if (closed || (currentState != null && currentState.busy)) {
            return;
        }
        FirebaseUser currentUser = authentication.getCurrentUser();
        if (currentUser == null) {
            state.setValue(new State(false, true, "Bạn cần đăng nhập để phản hồi yêu cầu."));
            return;
        }
        final String driverIdentifier = currentUser.getUid();
        String progressMessage = "Đang gửi phản hồi…";
        if (accepting) {
            progressMessage = "Đang kiểm tra lại tuyến đón/trả trước khi nhận khách…";
        }
        state.setValue(new State(true, false, progressMessage));
        RepositoryCallback<Void> callback = new RepositoryCallback<Void>() {
            /** Chỉ công bố thành công sau khi server xác nhận ghi nguyên tử. */
            @Override
            public void onSuccess(Void unused) {
                String message = "Đã từ chối yêu cầu.";
                if (accepting) {
                    message = "Đã chấp nhận yêu cầu. Khách có thể xem xác nhận trong app.";
                }
                String confirmedStatus = "REJECTED";
                if (accepting) {
                    confirmedStatus = "ACCEPTED";
                }
                finish(driverIdentifier, message, confirmedStatus);
            }

            /** Không tự thử ghi lại sau lỗi mạng; lần bấm tiếp theo phải đọc lại máy chủ. */
            @Override
            public void onError(Exception error) {
                String message = "Chưa xác nhận được phản hồi. Hãy tải lại yêu cầu và thử lại.";
                if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                    message = error.getMessage();
                }
                finish(driverIdentifier, message);
            }
        };
        if (accepting) {
            repository.acceptRequest(requestIdentifier, callback);
        } else {
            repository.rejectRequest(requestIdentifier, callback);
        }
    }

    /** Callback của phiên cũ không được hiển thị kết quả nhận khách cho tài khoản mới. */
    private void finish(String driverIdentifier, String message) {
        finish(driverIdentifier, message, "");
    }

    /** Trạng thái đã được máy chủ xác nhận được giữ qua xoay, không đoán từ thông báo lỗi. */
    private void finish(String driverIdentifier, String message, String confirmedRequestStatus) {
        if (closed) {
            return;
        }
        FirebaseUser currentUser = authentication.getCurrentUser();
        if (currentUser == null || !driverIdentifier.equals(currentUser.getUid())) {
            message = "Phiên đăng nhập đã đổi. Hãy mở lại yêu cầu bằng đúng tài khoản chủ xe.";
            confirmedRequestStatus = "";
        }
        state.setValue(new State(false, true, message, confirmedRequestStatus));
    }

    /** Chỉ tiêu thụ thông báo, không khởi động lại thao tác đã hoàn tất. */
    public void acknowledgeResult() {
        State currentState = state.getValue();
        String message = "";
        String confirmedStatus = "";
        if (currentState != null) {
            message = currentState.message;
            confirmedStatus = currentState.confirmedRequestStatus;
        }
        state.setValue(new State(false, false, message, confirmedStatus));
    }

    /** Back khỏi màn giải phóng worker; xoay màn vẫn giữ cùng ViewModel. */
    @Override
    protected void onCleared() {
        closed = true;
        repository.close();
    }
}
