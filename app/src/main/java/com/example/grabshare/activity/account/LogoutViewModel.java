package com.example.grabshare.activity.account;

import android.app.Application;
import android.os.CancellationSignal;

import androidx.lifecycle.AndroidViewModel;

import com.example.grabshare.local.TripCacheRepository;
import com.example.grabshare.repository.account.AuthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import androidx.core.content.ContextCompat;

import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.firebase.auth.FirebaseUser;

/** Giữ tác vụ đăng xuất qua lần xoay màn hình. */
public class LogoutViewModel extends AndroidViewModel {

    /** Trạng thái để Activity hiển thị, không giữ View hoặc Activity. */
    public static class State {
        public final boolean busy;
        public final boolean completed;
        public final Exception error;

        /** Ghi nhận tiến trình và lỗi của tác vụ đăng xuất. */
        public State(
                boolean busy,
                boolean completed,
                Exception error) {
            this.busy = busy;
            this.completed = completed;
            this.error = error;
        }
    }

    private final MutableLiveData<State> state =
            new MutableLiveData<>(
                    new State(false, false, null));

    private final AuthRepository authRepository =
            new AuthRepository();

    private final TripCacheRepository cacheRepository;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private static final long CREDENTIAL_CLEAR_TIMEOUT_SECONDS = 3L;

    private final ScheduledExecutorService timeoutExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> credentialTimeout;
    private CancellationSignal credentialCancellation;
    private int credentialRequestNumber;
    private volatile boolean cleared;

    /** Dùng Application Context để không giữ tham chiếu Activity. */
    public LogoutViewModel(Application application) {
        super(application);

        cacheRepository = new TripCacheRepository(
                application.getApplicationContext());
    }

    /** Cho Activity quan sát trạng thái đăng xuất qua vòng đời màn hình. */
    public LiveData<State> getState() {
        return state;
    }

    /** Xóa cache và kết thúc Auth cùng khóa với tiến trình ghi dữ liệu. */
    private void clearCacheAndSignOut(final String userId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (cleared) {
                    return;
                }
                try {
                    cacheRepository.clearUserCacheAndFinishSession(userId, new Runnable() {
                        /** Không nhả khóa ghi cache trước khi Auth kết thúc đúng UID. */
                        @Override
                        public void run() {
                            signOutLocally(userId);
                        }
                    });
                } catch (final Exception error) {
                    ContextCompat.getMainExecutor(getApplication()).execute(new Runnable() {
                        @Override
                        public void run() {
                            if (!cleared) {
                                state.setValue(new State(false, false,
                                        new IllegalStateException(
                                                "Không thể hoàn tất đăng xuất. Vui lòng thử lại.",
                                                error)));
                            }
                        }
                    });
                    return;
                }
                ContextCompat.getMainExecutor(getApplication()).execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!cleared) {
                            clearCredentialSession();
                        }
                    }
                });
            }
        });
    }

    /** Kết thúc đúng phiên đang xóa cache, kể cả khi thiết bị ngoại tuyến. */
    private void signOutLocally(String userId) {
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser != null && !userId.equals(currentUser.getUid())) {
            throw new IllegalStateException("Tài khoản đã thay đổi. Vui lòng thực hiện lại.");
        }
        if (currentUser == null) {
            return;
        }
        authRepository.signOut();
    }

    /** Giới hạn thời gian xóa phiên Google để UI không bị khóa khi nhà cung cấp chậm. */
    private void clearCredentialSession() {
        if (cleared) {
            return;
        }
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser != null) {
            state.setValue(new State(false, false, new IllegalStateException(
                    "Tài khoản đã thay đổi. Không xóa trạng thái phiên mới.")));
            return;
        }
        credentialRequestNumber++;
        final int expectedRequestNumber = credentialRequestNumber;
        credentialCancellation = new CancellationSignal();
        credentialTimeout = timeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                ContextCompat.getMainExecutor(getApplication()).execute(new Runnable() {
                    @Override
                    public void run() {
                        finishCredentialClear(expectedRequestNumber,
                                new IllegalStateException(
                                        "Đã đăng xuất. Chưa xác nhận việc xóa phiên Google."));
                    }
                });
            }
        }, CREDENTIAL_CLEAR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            CredentialManager.create(getApplication()).clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    credentialCancellation,
                    ContextCompat.getMainExecutor(getApplication()),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) {
                            finishCredentialClear(expectedRequestNumber, null);
                        }

                        @Override
                        public void onError(ClearCredentialException error) {
                            finishCredentialClear(expectedRequestNumber,
                                    new IllegalStateException(
                                            "Đã đăng xuất. Không thể xóa trạng thái phiên Google.",
                                            error));
                        }
                    });
        } catch (Exception error) {
            finishCredentialClear(expectedRequestNumber, new IllegalStateException(
                    "Đã đăng xuất. Không thể xóa trạng thái phiên Google.", error));
        }
    }

    /** Nhà cung cấp có thể trả callback qua Binder, phải chuyển về UI trước khi đổi State. */
    private void finishCredentialClear(
            final int expectedRequestNumber, final Exception error) {
        ContextCompat.getMainExecutor(getApplication()).execute(new Runnable() {
            @Override
            public void run() {
                completeCredentialClearOnMain(expectedRequestNumber, error);
            }
        });
    }

    /** Chỉ nhận một kết quả trên UI; timeout và callback muộn không đổi Auth. */
    private void completeCredentialClearOnMain(int expectedRequestNumber, Exception error) {
        if (cleared || expectedRequestNumber != credentialRequestNumber) {
            return;
        }
        credentialRequestNumber++;
        if (credentialTimeout != null) {
            credentialTimeout.cancel(false);
            credentialTimeout = null;
        }
        if (credentialCancellation != null) {
            credentialCancellation.cancel();
            credentialCancellation = null;
        }
        if (authRepository.getCurrentUser() != null) {
            state.setValue(new State(false, false, new IllegalStateException(
                    "Tài khoản đã thay đổi. Không điều hướng phiên mới về đăng nhập.")));
            return;
        }
        state.setValue(new State(false, true, error));
    }

    /** Xóa cache rồi đăng xuất; không nhận thêm yêu cầu khi đang xử lý. */
    public void submit() {
        State currentState = state.getValue();

        if (cleared || (currentState != null
                && (currentState.busy || currentState.completed))) {
            return;
        }

        state.setValue(new State(true, false, null));

        FirebaseUser user = authRepository.getCurrentUser();

        if (user == null) {
            clearCredentialSession();
            return;
        }

        final String userId = user.getUid();

        if (userId == null || userId.trim().isEmpty()) {
            state.setValue(new State(
                    false,
                    false,
                    new IllegalStateException(
                            "Không xác định được tài khoản hiện tại.")));
            return;
        }

        clearCacheAndSignOut(userId);
    }

    /** Dừng nhận tác vụ mới khi ViewModel không còn được sử dụng. */
    @Override
    protected void onCleared() {
        cleared = true;
        credentialRequestNumber++;
        if (credentialTimeout != null) {
            credentialTimeout.cancel(false);
        }
        if (credentialCancellation != null) {
            credentialCancellation.cancel();
        }
        timeoutExecutor.shutdownNow();
        executor.shutdown();
        super.onCleared();
    }
}
