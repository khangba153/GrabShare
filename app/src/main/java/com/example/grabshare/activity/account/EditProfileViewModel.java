package com.example.grabshare.activity.account;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.account.ProfileRepository;
import com.example.grabshare.repository.account.ProfileRepository.AccountProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Giữ bản nháp và một tác vụ lưu qua xoay; process death chỉ tải lại, không tự ghi. */
public class EditProfileViewModel extends AndroidViewModel {

    //region 1. Trạng thái hiển thị và bản nháp

    /** Tách tải ban đầu, tác vụ lưu và kết quả server đã xác nhận. */
    public static final class State {
        public final boolean loading;
        public final boolean saving;
        public final boolean ready;
        public final boolean completed;
        public final String message;
        public final Exception error;

        /** Tạo snapshot trạng thái để Activity mới tiếp tục quan sát. */
        State(
                boolean loading,
                boolean saving,
                boolean ready,
                boolean completed,
                String message,
                Exception error) {
            this.loading = loading;
            this.saving = saving;
            this.ready = ready;
            this.completed = completed;
            this.message = message;
            this.error = error;
        }
    }

    //endregion

    //region 2. Repository và vòng đời tác vụ

    private final SavedStateHandle savedStateHandle;
    private final ProfileRepository repository = new ProfileRepository();
    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(false, false, false, false, "", null));
    private String userIdentifier;
    private boolean isViewModelCleared;
    private int currentOperationGeneration;

    //endregion

    //region 3. Khởi tạo và phục hồi form

    /** SavedStateHandle phục hồi riêng các giá trị form, không lưu Activity/model Firestore. */
    public EditProfileViewModel(
            Application application,
            SavedStateHandle savedStateHandle) {
        super(application);
        this.savedStateHandle = savedStateHandle;
    }

    /** Cung cấp tiến trình và kết quả cho observer theo lifecycle. */
    public LiveData<State> getState() {
        return state;
    }

    /** Khởi tạo đúng tài khoản; xoay không phát sinh một lần ghi hoặc đọc trùng. */
    public void start(String expectedUserIdentifier) {
        if (isViewModelCleared) {
            return;
        }

        String restoredUserIdentifier = savedStateHandle.get("profile_uid");
        if (expectedUserIdentifier == null
                || (restoredUserIdentifier != null
                && !expectedUserIdentifier.equals(restoredUserIdentifier))) {
            report(new IllegalStateException("Phiên đăng nhập đã thay đổi."), false);
            return;
        }

        userIdentifier = expectedUserIdentifier;
        savedStateHandle.set("profile_uid", userIdentifier);

        State value = state.getValue();
        if (value != null && (value.loading || value.saving || value.ready || value.completed)) {
            return;
        }

        loadProfile();
    }

    /** Lấy giá trị bản nháp đã được lưu vào state. */
    public String getDraftValue(String key) {
        String value = savedStateHandle.get(key);
        if (value == null) {
            return "";
        }

        return value;
    }

    /** Cho phép hiển thị bản nháp đã phục hồi ngay cả khi tải lại server đang lỗi. */
    public boolean hasDraft() {
        return Boolean.TRUE.equals(savedStateHandle.get("profile_draft"));
    }

    /** Cập nhật bản nháp theo thao tác nhập, không gửi Firestore. */
    public void updateDraft(String name, String vehicle, String plate) {
        if (!isCurrentOperation()) {
            return;
        }

        savedStateHandle.set("profile_draft", true);
        savedStateHandle.set("profile_name", name);
        savedStateHandle.set("profile_vehicle", vehicle);
        savedStateHandle.set("profile_plate", plate);
    }

    //endregion

    //region 4. Tải hồ sơ từ server

    /** Tải lại server nhưng giữ thay đổi chưa lưu, kể cả sau process death. */
    public void loadProfile() {
        State value = state.getValue();
        if (!isCurrentOperation() || (value != null && (value.loading || value.saving || value.completed))) {
            return;
        }

        currentOperationGeneration = currentOperationGeneration + 1;
        final int operationGeneration = currentOperationGeneration;
        state.setValue(new State(true, false, false, false, "Đang tải hồ sơ…", null));
        repository.loadOwnProfile(
                userIdentifier,
                new RepositoryCallback<AccountProfile>() {
                    /** Chỉ lần tải đầu mới điền form; thử lại không xóa bản nháp. */
                    @Override
                    public void onSuccess(AccountProfile profile) {
                        if (!isCurrentOperation() || operationGeneration != currentOperationGeneration) {
                            return;
                        }

                        if (!Boolean.TRUE.equals(savedStateHandle.get("profile_draft"))) {
                            updateDraft(
                                    profile.displayName,
                                    profile.vehicleName,
                                    profile.vehiclePlate);
                        }

                        boolean interrupted = Boolean.TRUE.equals(
                                savedStateHandle.get("profile_save_pending"));
                        savedStateHandle.set("profile_save_pending", false);

                        String message = "";
                        if (!profile.isComplete()) {
                            message = "Hồ sơ còn thiếu. Điền thông tin và lưu để hoàn thiện.";
                        } else if (interrupted) {
                            message = "Đã tải lại hồ sơ sau gián đoạn. "
                                    + "Kiểm tra bản nháp trước khi lưu.";
                        }

                        state.setValue(new State(false, false, true, false, message, null));
                    }

                    /** Lỗi tải không được biến thành bản ghi trống hoặc tự ghi phục hồi. */
                    @Override
                    public void onError(Exception error) {
                        if (isCurrentOperation() && operationGeneration == currentOperationGeneration) {
                            report(error, false);
                        }
                    }
                });
    }

    //endregion

    //region 5. Lưu hồ sơ theo thao tác người dùng

    /** Chỉ bắt đầu lưu từ nút người dùng; khóa gửi lặp, yêu cầu mạng thật. */
    public void saveProfile() {
        State value = state.getValue();
        if (!isCurrentOperation() || value == null || !value.ready || value.loading
                || value.saving || value.completed) {
            return;
        }

        if (!online()) {
            report(new IllegalStateException("Cần kết nối Internet để lưu hồ sơ."), true);
            return;
        }

        currentOperationGeneration = currentOperationGeneration + 1;
        final int operationGeneration = currentOperationGeneration;
        savedStateHandle.set("profile_save_pending", true);
        state.setValue(new State(false, true, true, false, "Đang lưu hồ sơ…", null));
        repository.saveOwnProfile(
                userIdentifier,
                getDraftValue("profile_name"),
                getDraftValue("profile_vehicle"),
                getDraftValue("profile_plate"),
                new RepositoryCallback<Void>() {
                    /** Chỉ kết thúc form sau server commit và đúng phiên khởi tạo. */
                    @Override
                    public void onSuccess(Void ignored) {
                        if (!isCurrentOperation() || operationGeneration != currentOperationGeneration) {
                            return;
                        }

                        savedStateHandle.set("profile_save_pending", false);
                        state.setValue(new State(
                                false, false, true, true, "Đã lưu hồ sơ.", null));
                    }

                    /** Giữ form, mở lại nút lưu và để người dùng thử lại. */
                    @Override
                    public void onError(Exception error) {
                        if (isCurrentOperation() && operationGeneration == currentOperationGeneration) {
                            savedStateHandle.set("profile_save_pending", false);
                            report(error, true);
                        }
                    }
                });
    }

    //endregion

    //region 6. Mạng, phiên đăng nhập và xử lý lỗi

    /** Kiểm tra Internet trước mutation; transaction không được queue ghi offline. */
    private boolean online() {
        ConnectivityManager manager = getApplication().getSystemService(ConnectivityManager.class);
        if (manager == null) {
            return false;
        }

        NetworkCapabilities network = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return network != null
                && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Callback chỉ thuộc tài khoản và ViewModel đang hoạt động. */
    private boolean isCurrentOperation() {
        FirebaseUser signedInUser = FirebaseAuth.getInstance().getCurrentUser();
        return !isViewModelCleared
                && userIdentifier != null
                && signedInUser != null
                && userIdentifier.equals(signedInUser.getUid());
    }

    /** Giữ lỗi cho Activity mới sau xoay; không nuốt lỗi quyền hoặc dữ liệu. */
    private void report(Exception error, boolean ready) {
        state.setValue(new State(false, false, ready, false, "", error));
    }

    /** Đóng màn không để callback cũ điều hướng hoặc cập nhật UI. */
    @Override
    protected void onCleared() {
        isViewModelCleared = true;
        currentOperationGeneration = currentOperationGeneration + 1;
    }

    //endregion
}
