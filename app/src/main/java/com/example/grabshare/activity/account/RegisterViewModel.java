package com.example.grabshare.activity.account;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.account.AuthRepository;
import com.example.grabshare.repository.account.ProfileRepository;
import com.google.firebase.auth.FirebaseUser;

/** Giữ tác vụ đăng ký và trạng thái kết quả qua lần xoay màn hình. */
public class RegisterViewModel extends ViewModel {

    /** Trạng thái bất biến để Activity hiển thị, không giữ View hoặc Activity. */
    public static class State {
        public final boolean busy;
        public final boolean completed;
        public final String uid;
        public final Exception error;

        /** Tạo trạng thái cho một thời điểm của luồng đăng ký. */
        public State(
                boolean busy,
                boolean completed,
                String uid,
                Exception error) {
            this.busy = busy;
            this.completed = completed;
            this.uid = uid;
            this.error = error;
        }
    }

    private final AuthRepository authRepository = new AuthRepository();
    private final ProfileRepository profileRepository =
            new ProfileRepository();

    private final MutableLiveData<State> state =
            new MutableLiveData<>(
                    new State(false, false, null, null));

    private boolean cleared;

    /** Cung cấp trạng thái chỉ đọc cho Activity quan sát. */
    public LiveData<State> getState() {
        return state;
    }

    /** Bắt đầu theo yêu cầu người dùng; không gửi thêm khi tác vụ đang chạy. */
    public void submit(
            String email,
            String password,
            final String displayName,
            final String vehicleName,
            final String vehiclePlate) {

        State currentState = state.getValue();

        if (cleared || (currentState != null
                && (currentState.busy || currentState.completed))) {
            return;
        }

        state.setValue(new State(true, false, null, null));

        FirebaseUser currentUser = authRepository.getCurrentUser();

        if (currentUser != null) {
            saveProfiles(
                    currentUser.getUid(),
                    displayName,
                    vehicleName,
                    vehiclePlate);
            return;
        }

        authRepository.signUp(
                email,
                password,
                new RepositoryCallback<FirebaseUser>() {

                    /** Tạo hồ sơ sau khi Auth đã tạo tài khoản thành công. */
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        if (cleared) {
                            return;
                        }

                        saveProfiles(
                                user.getUid(),
                                displayName,
                                vehicleName,
                                vehiclePlate);
                    }

                    /** Giữ lỗi để Activity hiện tại có thể hiển thị. */
                    @Override
                    public void onError(Exception error) {
                        reportError(error);
                    }
                });
    }

    /** Ghi hồ sơ cho đúng phiên đã bắt đầu thao tác. */
    private void saveProfiles(
            final String expectedUid,
            String displayName,
            String vehicleName,
            String vehiclePlate) {

        if (cleared) {
            return;
        }

        FirebaseUser currentUser = authRepository.getCurrentUser();

        if (currentUser == null
                || !expectedUid.equals(currentUser.getUid())) {
            reportError(new IllegalStateException(
                    "Phiên đăng nhập đã thay đổi."));
            return;
        }

        profileRepository.createMissingProfiles(
                displayName,
                vehicleName,
                vehiclePlate,
                new RepositoryCallback<Void>() {

                    /** Chỉ đánh dấu hoàn tất sau khi hồ sơ được xác nhận. */
                    @Override
                    public void onSuccess(Void unused) {
                        if (cleared) {
                            return;
                        }

                        FirebaseUser activeUser =
                                authRepository.getCurrentUser();

                        if (activeUser == null
                                || !expectedUid.equals(activeUser.getUid())) {
                            reportError(new IllegalStateException(
                                    "Phiên đăng nhập đã thay đổi."));
                            return;
                        }

                        state.setValue(new State(
                                false, true, expectedUid, null));
                    }

                    /** Giữ phiên Auth để người dùng có thể thử bổ sung lại. */
                    @Override
                    public void onError(Exception error) {
                        reportError(error);
                    }
                });
    }

    /** Kết thúc trạng thái chờ khi gặp lỗi. */
    private void reportError(Exception error) {
        if (cleared) {
            return;
        }

        if (error == null) {
            error = new IllegalStateException(
                    "Không hoàn tất được đăng ký hoặc tạo hồ sơ.");
        }

        state.setValue(new State(false, false, null, error));
    }

    /** Không nối tác vụ tiếp theo khi màn đã được đóng vĩnh viễn. */
    @Override
    protected void onCleared() {
        cleared = true;
        super.onCleared();
    }
}