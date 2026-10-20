package com.example.grabshare.activity.account;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.account.ProfileRepository;
import com.example.grabshare.repository.account.ProfileRepository.AccountProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Giữ tiến trình tải hồ sơ qua xoay; không giữ Activity hoặc View. */
public class AccountProfileViewModel extends ViewModel {

    //region 1. Trạng thái hiển thị

    /** Kết quả tải bất biến, chỉ chứa hồ sơ của một UID. */
    public static final class State {
        public final boolean loading;
        public final AccountProfile profile;
        public final Exception error;

        /** Ghi nhận tiến trình tải, hồ sơ đã xác nhận hoặc lỗi cần thử lại. */
        State(boolean loading, AccountProfile profile, Exception error) {
            this.loading = loading;
            this.profile = profile;
            this.error = error;
        }
    }

    //endregion

    //region 2. Repository và vòng đời tác vụ

    private final ProfileRepository repository = new ProfileRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(false, null, null));

    private int currentOperationGeneration;
    private String userIdentifier;
    private boolean isViewModelCleared;

    //endregion

    //region 3. Luồng tải hồ sơ

    /** Cung cấp trạng thái chỉ đọc cho màn Tài khoản quan sát theo lifecycle. */
    public LiveData<State> getState() {
        return state;
    }

    /** Tải lại từ server; không dùng dữ liệu UID cũ hoặc coi lỗi là hồ sơ trống. */
    public void loadProfile(final String expectedUserIdentifier) {
        if (isViewModelCleared
                || expectedUserIdentifier == null
                || expectedUserIdentifier.trim().isEmpty()) {
            return;
        }

        State value = state.getValue();
        if (value != null && value.loading && expectedUserIdentifier.equals(userIdentifier)) {
            return;
        }

        userIdentifier = expectedUserIdentifier;
        currentOperationGeneration = currentOperationGeneration + 1;
        final int operationGeneration = currentOperationGeneration;
        state.setValue(new State(true, null, null));

        repository.loadOwnProfile(
                expectedUserIdentifier,
                new RepositoryCallback<AccountProfile>() {
                    /** Chỉ nhận kết quả khi thế hệ tải và tài khoản vẫn đúng. */
                    @Override
                    public void onSuccess(AccountProfile profile) {
                        if (isCurrentOperation(operationGeneration)) {
                            state.setValue(new State(false, profile, null));
                        }
                    }

                    /** Giữ lỗi đọc để người dùng chủ động thử lại. */
                    @Override
                    public void onError(Exception error) {
                        if (isCurrentOperation(operationGeneration)) {
                            state.setValue(new State(false, null, error));
                        }
                    }
                });
    }

    //endregion

    //region 4. Bảo vệ phiên và giải phóng tác vụ

    /** Xóa dữ liệu riêng và vô hiệu callback khi đăng xuất hoặc phiên thay đổi. */
    public void clearProfile() {
        currentOperationGeneration = currentOperationGeneration + 1;
        userIdentifier = null;
        state.setValue(new State(false, null, null));
    }

    /** Đối chiếu UID thật và thế hệ tác vụ tại thời điểm callback trở về. */
    private boolean isCurrentOperation(int operationGeneration) {
        FirebaseUser signedInUser = FirebaseAuth.getInstance().getCurrentUser();
        return !isViewModelCleared
                && operationGeneration == currentOperationGeneration
                && userIdentifier != null
                && signedInUser != null
                && userIdentifier.equals(signedInUser.getUid());
    }

    /** Vô hiệu callback khi người dùng đóng hẳn màn hình. */
    @Override
    protected void onCleared() {
        isViewModelCleared = true;
        currentOperationGeneration = currentOperationGeneration + 1;
    }

    //endregion
}
