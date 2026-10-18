package com.example.grabshare.activity.account;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.example.grabshare.local.TripCacheRepository;
import com.example.grabshare.repository.account.AuthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private boolean cleared;

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

    /** Xóa cache đúng UID ngoài UI thread, chỉ tiếp tục khi thành công. */
    private void clearCache(
            final String userId,
            final Runnable afterClear) {

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    cacheRepository.clearUserCache(userId);
                } catch (final Exception error) {
                    ContextCompat.getMainExecutor(getApplication())
                            .execute(new Runnable() {
                                @Override
                                public void run() {
                                    state.setValue(new State(
                                            false,
                                            false,
                                            new IllegalStateException(
                                                    "Không thể xóa dữ liệu tạm. "
                                                            + "Vui lòng thử lại.",
                                                    error)));
                                }
                            });
                    return;
                }

                ContextCompat.getMainExecutor(getApplication())
                        .execute(new Runnable() {
                            @Override
                            public void run() {
                                afterClear.run();
                            }
                        });
            }
        });
    }

    /** Xóa trạng thái phiên của nhà cung cấp sau khi Firebase đã đăng xuất. */
    private void clearCredentialSession() {
        try {
            CredentialManager.create(getApplication())
                    .clearCredentialStateAsync(
                            new ClearCredentialStateRequest(),
                            null,
                            ContextCompat.getMainExecutor(getApplication()),
                            new CredentialManagerCallback<
                                    Void, ClearCredentialException>() {

                                /** Chuyển kết quả về UI kể cả khi nhà cung cấp gọi trên luồng nền. */
                                @Override
                                public void onResult(Void result) {
                                    state.postValue(
                                            new State(false, true, null));
                                }

                                /** Firebase đã đăng xuất; báo việc xóa phiên chưa thành công. */
                                @Override
                                public void onError(
                                        ClearCredentialException error) {
                                    state.postValue(new State(
                                            false,
                                            true,
                                            new IllegalStateException(
                                                    "Đã đăng xuất. Không thể xóa "
                                                            + "trạng thái phiên Google.",
                                                    error)));
                                }
                            });
        } catch (Exception error) {
            state.setValue(new State(
                    false,
                    true,
                    new IllegalStateException(
                            "Đã đăng xuất. Không thể xóa "
                                    + "trạng thái phiên Google.",
                            error)));
        }
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

        clearCache(userId, new Runnable() {
            /** Chỉ đăng xuất sau khi cache của đúng tài khoản đã được xóa. */
            @Override
            public void run() {
                if (cleared) {
                    return;
                }

                FirebaseUser currentUser = authRepository.getCurrentUser();

                if (currentUser != null
                        && !userId.equals(currentUser.getUid())) {
                    state.setValue(new State(
                            false,
                            false,
                            new IllegalStateException(
                                    "Tài khoản đã thay đổi. "
                                            + "Vui lòng thực hiện lại.")));
                    return;
                }

                try {
                    authRepository.signOut();
                } catch (Exception error) {
                    state.setValue(new State(
                            false,
                            false,
                            new IllegalStateException(
                                    "Không thể đăng xuất. Vui lòng thử lại.",
                                    error)));
                    return;
                }

                clearCredentialSession();
            }
        });
    }

    /** Dừng nhận tác vụ mới khi ViewModel không còn được sử dụng. */
    @Override
    protected void onCleared() {
        cleared = true;
        executor.shutdown();
        super.onCleared();
    }
}
