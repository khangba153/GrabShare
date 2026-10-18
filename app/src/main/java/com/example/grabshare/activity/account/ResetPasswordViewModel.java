package com.example.grabshare.activity.account;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.account.AuthRepository;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;

/** Giữ tác vụ khôi phục mật khẩu qua lần xoay màn hình. */
public class ResetPasswordViewModel extends ViewModel {

    public static class State {
        public final boolean busy;
        public final boolean sent;
        public final String email;
        public final String message;
        public final boolean error;

        public State(boolean busy, boolean sent, String email,
                     String message, boolean error) {
            this.busy = busy;
            this.sent = sent;
            this.email = email;
            this.message = message;
            this.error = error;
        }
    }

    private final AuthRepository repository = new AuthRepository();

    private final MutableLiveData<State> state =
            new MutableLiveData<>(
                    new State(false, false, "", "", false));

    private boolean cleared;

    public LiveData<State> getState() {
        return state;
    }

    /** Chỉ gửi khi người dùng bấm; không tự gửi lại sau khi xoay. */
    public void submit(String input) {
        State current = state.getValue();

        if (cleared || (current != null
                && (current.busy || current.sent))) {
            return;
        }

        final String email = input == null ? "" : input.trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            state.setValue(new State(
                    false, false, email,
                    "Vui lòng nhập email hợp lệ.", true));
            return;
        }

        state.setValue(new State(
                true, false, email,
                "Đang gửi yêu cầu khôi phục…", false));

        repository.sendPasswordResetEmail(
                email,
                new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (cleared) {
                            return;
                        }

                        state.setValue(new State(
                                false, true, email,
                                "Nếu email có tài khoản hỗ trợ khôi phục, "
                                        + "bạn sẽ nhận được liên kết đặt lại "
                                        + "mật khẩu. Hãy kiểm tra cả thư rác.",
                                false));
                    }

                    @Override
                    public void onError(Exception error) {
                        if (cleared) {
                            return;
                        }

                        String message =
                                "Không gửi được yêu cầu. Vui lòng thử lại.";

                        if (error instanceof FirebaseNetworkException) {
                            message =
                                    "Không kết nối được mạng. "
                                            + "Kiểm tra kết nối và thử lại.";
                        } else if (error
                                instanceof FirebaseTooManyRequestsException) {
                            message =
                                    "Đã gửi quá nhiều yêu cầu. "
                                            + "Vui lòng chờ rồi thử lại.";
                        }

                        state.setValue(new State(
                                false, false, email, message, true));
                    }
                });
    }

    @Override
    protected void onCleared() {
        cleared = true;
        super.onCleared();
    }
}