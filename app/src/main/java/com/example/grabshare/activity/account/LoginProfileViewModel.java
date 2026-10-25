package com.example.grabshare.activity.account;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.account.LoginProfileRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Giữ một lần kiểm tra hồ sơ qua xoay màn hình, không giữ Activity hoặc View. */
public class LoginProfileViewModel extends ViewModel {

    public static final long PROFILE_TIMEOUT_MILLISECONDS = 8000L;

    /** Chỉ chứa kết quả của tài khoản đã yêu cầu kiểm tra. */
    public static final class State {
        public final String userIdentifier;
        public final boolean loading;
        public final Boolean hasBothProfiles;
        public final Exception error;

        State(String userIdentifier, boolean loading,
                Boolean hasBothProfiles, Exception error) {
            this.userIdentifier = userIdentifier;
            this.loading = loading;
            this.hasBothProfiles = hasBothProfiles;
            this.error = error;
        }
    }

    private final LoginProfileRepository repository = new LoginProfileRepository();
    private final Executor mainExecutor = ContextCompat.getMainExecutor(
            FirebaseApp.getInstance().getApplicationContext());
    private final ScheduledExecutorService timeoutExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(null, false, null, null));

    private int operationGeneration;
    private ScheduledFuture<?> pendingTimeout;
    private boolean cleared;

    /** Cho Activity quan sát kết quả theo lifecycle. */
    public LiveData<State> getState() {
        return state;
    }

    /** Tái sử dụng lần kiểm tra của màn hiện tại; lỗi chỉ thử lại khi người dùng yêu cầu. */
    public void checkProfile(String expectedUserIdentifier, boolean retryRequested) {
        if (cleared || expectedUserIdentifier == null
                || expectedUserIdentifier.trim().isEmpty()) {
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || !expectedUserIdentifier.equals(currentUser.getUid())) {
            clearProfile();
            return;
        }

        State currentState = state.getValue();
        if (currentState != null
                && expectedUserIdentifier.equals(currentState.userIdentifier)) {
            if (currentState.loading || !retryRequested) {
                return;
            }
        }

        removeTimeout();
        operationGeneration = operationGeneration + 1;
        final int requestGeneration = operationGeneration;
        final String requestedUserIdentifier = expectedUserIdentifier;
        state.setValue(new State(requestedUserIdentifier, true, null, null));

        pendingTimeout = timeoutExecutor.schedule(new Runnable() {
            /** Chỉ hẹn giờ ở luồng nền; mọi thay đổi LiveData trở về luồng giao diện. */
            @Override
            public void run() {
                mainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        finishRequest(requestGeneration, requestedUserIdentifier, null,
                                new TimeoutException("Kiểm tra hồ sơ quá thời gian chờ."));
                    }
                });
            }
        }, PROFILE_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);

        repository.checkProfile(requestedUserIdentifier,
                new RepositoryCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean hasBothProfiles) {
                        finishRequest(requestGeneration, requestedUserIdentifier,
                                hasBothProfiles, null);
                    }

                    @Override
                    public void onError(Exception error) {
                        finishRequest(requestGeneration, requestedUserIdentifier,
                                null, error);
                    }
                });
    }

    /** Một lần kiểm tra chỉ kết thúc một lần; kết quả muộn không mở Home. */
    private void finishRequest(int requestGeneration, String requestedUserIdentifier,
            Boolean hasBothProfiles, Exception error) {
        if (cleared || requestGeneration != operationGeneration) {
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || !requestedUserIdentifier.equals(currentUser.getUid())) {
            clearProfile();
            return;
        }

        operationGeneration = operationGeneration + 1;
        removeTimeout();
        state.setValue(new State(requestedUserIdentifier, false, hasBothProfiles, error));
    }

    /** Bỏ kết quả cũ khi đổi phiên hoặc chuyển sang hoàn thiện hồ sơ. */
    public void clearProfile() {
        operationGeneration = operationGeneration + 1;
        removeTimeout();
        state.setValue(new State(null, false, null, null));
    }

    /** Dọn lịch chờ; tác vụ Firestore đã gửi có thể còn chạy nhưng callback bị vô hiệu. */
    private void removeTimeout() {
        if (pendingTimeout != null) {
            pendingTimeout.cancel(false);
            pendingTimeout = null;
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
        operationGeneration = operationGeneration + 1;
        removeTimeout();
        timeoutExecutor.shutdownNow();
    }
}
