package com.example.grabshare.activity.account;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Chỉnh sửa tên và thông tin xe của chính chủ, giữ bản nháp và tác vụ qua xoay. */
public class EditProfileActivity extends AppCompatActivity {

    //region 1. View và trạng thái của màn hình

    private static final String STATE_USER_IDENTIFIER = "edit_profile_uid";

    private EditText nameInput;
    private EditText vehicleInput;
    private EditText plateInput;
    private TextView statusText;

    private EditProfileViewModel editProfileViewModel;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authListener;
    private String expectedUserIdentifier;
    private boolean bindingDraft;
    private boolean closing;
    private Exception lastPresentedError;

    private final OnBackPressedCallback savingBackCallback =
            new OnBackPressedCallback(false) {
                /** Giữ màn khi transaction đang chạy để kết quả không bị bỏ dở. */
                @Override
                public void handleOnBackPressed() {
                    Toast.makeText(
                            EditProfileActivity.this,
                            R.string.profile_wait_for_save,
                            Toast.LENGTH_SHORT).show();
                }
            };

    //endregion

    //region 2. Khởi tạo và vòng đời

    /** Nối form với ViewModel, phục hồi đúng UID và đăng ký observer. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_edit_profile);

        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
        if (savedInstanceState != null) {
            expectedUserIdentifier = savedInstanceState.getString(STATE_USER_IDENTIFIER);
        } else if (signedInUser != null) {
            expectedUserIdentifier = signedInUser.getUid();
        }

        editProfileViewModel = new ViewModelProvider(this).get(EditProfileViewModel.class);
        initializeViews();
        connectEvents();
        getOnBackPressedDispatcher().addCallback(this, savingBackCallback);

        editProfileViewModel.getState().observe(this, new Observer<EditProfileViewModel.State>() {
            /** Activity mới sau xoay nhận lại tiến trình và kết quả server hiện tại. */
            @Override
            public void onChanged(EditProfileViewModel.State state) {
                render(state);
                showOperationError(state);
                finishAfterSave();
            }
        });

        editProfileViewModel.start(expectedUserIdentifier);
    }

    /** Đăng ký kiểm tra phiên trong thời gian màn chỉnh sửa đang hiển thị. */
    @Override
    protected void onStart() {
        super.onStart();

        if (!isExpectedUserSignedIn()) {
            openLogin();
            return;
        }

        authListener = new FirebaseAuth.AuthStateListener() {
            /** Không giữ form riêng tư của tài khoản cũ sau khi Auth thay đổi. */
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (!isExpectedUserSignedIn()) {
                    clearPrivateForm();
                    openLogin();
                }
            }
        };
        firebaseAuth.addAuthStateListener(authListener);
    }

    /** Chỉ kết thúc màn sau lưu khi Activity thật sự quay lại foreground. */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        render(editProfileViewModel.getState().getValue());
        finishAfterSave();
    }

    /** Gỡ listener Auth của Activity cũ; ViewModel vẫn giữ tác vụ khi xoay. */
    @Override
    protected void onStop() {
        if (authListener != null) {
            firebaseAuth.removeAuthStateListener(authListener);
            authListener = null;
        }
        super.onStop();
    }

    /** UID phục hồi phải trùng phiên; bản nháp được SavedStateHandle quản lý riêng. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putString(STATE_USER_IDENTIFIER, expectedUserIdentifier);
        super.onSaveInstanceState(state);
    }

    //endregion

    //region 3. Liên kết form và thao tác người dùng

    /** Ánh xạ đúng các field được phép sửa trong schema hiện hành. */
    private void initializeViews() {
        nameInput = findViewById(R.id.profile_name_input);
        vehicleInput = findViewById(R.id.profile_vehicle_input);
        plateInput = findViewById(R.id.profile_plate_input);
        statusText = findViewById(R.id.profile_edit_status);

        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
        String email = "";
        if (signedInUser != null && signedInUser.getEmail() != null) {
            email = signedInUser.getEmail();
        }
        ((TextView) findViewById(R.id.profile_email)).setText(email);
    }

    /** Mỗi nút có một thao tác rõ ràng; chỉ nút Lưu khởi tạo mutation. */
    private void connectEvents() {
        View.OnClickListener cancelListener = new View.OnClickListener() {
            /** Hủy form không gửi dữ liệu; không rời màn khi đang lưu. */
            @Override
            public void onClick(View view) {
                EditProfileViewModel.State state = editProfileViewModel.getState().getValue();
                if (state == null || !state.saving) {
                    finish();
                }
            }
        };
        findViewById(R.id.header_back).setOnClickListener(cancelListener);
        findViewById(R.id.secondary_action).setOnClickListener(cancelListener);

        findViewById(R.id.primary_action).setOnClickListener(new View.OnClickListener() {
            /** Kiểm tra dữ liệu trước khi bắt đầu một lần lưu trên server. */
            @Override
            public void onClick(View view) {
                if (isExpectedUserSignedIn() && validateForm()) {
                    rememberDraft();
                    editProfileViewModel.saveProfile();
                }
            }
        });

        findViewById(R.id.profile_edit_retry).setOnClickListener(new View.OnClickListener() {
            /** Tải lại server; không tự gửi lại lần lưu trước hoặc xóa bản nháp. */
            @Override
            public void onClick(View view) {
                editProfileViewModel.loadProfile();
            }
        });

        TextWatcher watcher = new TextWatcher() {
            /** Không phát sinh yêu cầu mạng trước khi nội dung thay đổi. */
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            /** Việc gõ văn bản không tự ghi Firestore. */
            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            /** Cập nhật bản nháp để xoay hoặc process death không mất nội dung đã nhập. */
            @Override
            public void afterTextChanged(Editable value) {
                if (!bindingDraft) {
                    rememberDraft();
                }
            }
        };
        nameInput.addTextChangedListener(watcher);
        vehicleInput.addTextChangedListener(watcher);
        plateInput.addTextChangedListener(watcher);
    }

    /** Lưu ba field của form theo UID hiện tại vào SavedStateHandle. */
    private void rememberDraft() {
        editProfileViewModel.updateDraft(
                nameInput.getText().toString(),
                vehicleInput.getText().toString(),
                plateInput.getText().toString());
    }

    //endregion

    //region 4. Kiểm tra đầu vào

    /** Tên bắt buộc; thông tin xe phải đi theo cặp hoặc cùng để trống. */
    private boolean validateForm() {
        nameInput.setError(null);
        vehicleInput.setError(null);
        plateInput.setError(null);

        String name = nameInput.getText().toString().trim();
        String vehicle = vehicleInput.getText().toString().trim();
        String plate = plateInput.getText().toString().trim();

        if (name.isEmpty() || name.length() > 80) {
            return showFieldError(nameInput, R.string.profile_name_error);
        }
        if (vehicle.length() > 80) {
            return showFieldError(vehicleInput, R.string.profile_vehicle_error);
        }
        if (plate.length() > 20) {
            return showFieldError(plateInput, R.string.profile_plate_error);
        }
        if (vehicle.isEmpty() != plate.isEmpty()) {
            if (vehicle.isEmpty()) {
                return showFieldError(vehicleInput, R.string.profile_vehicle_pair_error);
            } else {
                return showFieldError(plateInput, R.string.profile_vehicle_pair_error);
            }
        }

        return true;
    }

    /** Báo lỗi tại field và bằng thông báo ngắn, tránh lỗi bị khuất dưới bàn phím. */
    private boolean showFieldError(EditText field, int messageResourceIdentifier) {
        String message = getString(messageResourceIdentifier);
        field.setError(message);
        field.requestFocus();
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        statusText.setTextColor(getColor(android.R.color.holo_red_dark));
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        return false;
    }

    /** Báo lỗi tải/lưu ngay cả khi bàn phím hoặc vị trí cuộn đang che dòng trạng thái. */
    private void showOperationError(EditProfileViewModel.State state) {
        if (state != null && state.error != null
                && state.error != lastPresentedError && isExpectedUserSignedIn()) {
            lastPresentedError = state.error;
            Toast.makeText(this, ProfileMessages.explain(this, state.error),
                    Toast.LENGTH_LONG).show();
        }
    }

    //endregion

    //region 5. Hiển thị và điều hướng

    /** Hiển thị đúng trạng thái và khóa form trong một tác vụ lưu đang chạy. */
    private void render(EditProfileViewModel.State state) {
        if (state == null || isFinishing() || isDestroyed()) {
            return;
        }

        boolean editable = isExpectedUserSignedIn() && state.ready && !state.loading
                && !state.saving && !state.completed;
        if (editProfileViewModel.hasDraft() && isExpectedUserSignedIn()) {
            bindingDraft = true;
            bindInput(nameInput, editProfileViewModel.getDraftValue("profile_name"));
            bindInput(vehicleInput, editProfileViewModel.getDraftValue("profile_vehicle"));
            bindInput(plateInput, editProfileViewModel.getDraftValue("profile_plate"));
            bindingDraft = false;
        }

        nameInput.setEnabled(editable);
        vehicleInput.setEnabled(editable);
        plateInput.setEnabled(editable);
        findViewById(R.id.primary_action).setEnabled(editable);
        findViewById(R.id.secondary_action).setEnabled(!state.saving && !state.completed);
        findViewById(R.id.header_back).setEnabled(!state.saving && !state.completed);
        if (state.loading || state.saving) {
            findViewById(R.id.profile_edit_loading).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.profile_edit_loading).setVisibility(View.GONE);
        }

        if (state.error != null && !state.ready) {
            findViewById(R.id.profile_edit_retry).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.profile_edit_retry).setVisibility(View.GONE);
        }
        savingBackCallback.setEnabled(state.saving || state.completed);

        String message = state.message;
        if (state.error != null) {
            message = ProfileMessages.explain(this, state.error);
        }
        statusText.setText(message);
        if (state.error == null) {
            statusText.setTextColor(getColor(R.color.muted));
        } else {
            statusText.setTextColor(getColor(android.R.color.holo_red_dark));
        }

        if (message.isEmpty()) {
            statusText.setVisibility(View.GONE);
        } else {
            statusText.setVisibility(View.VISIBLE);
        }

        TextView saveProfileButton = findViewById(R.id.primary_action);
        if (state.saving) {
            saveProfileButton.setText(R.string.profile_saving);
        } else {
            saveProfileButton.setText(R.string.profile_save);
        }
    }

    /** Chỉ thay text khi thật sự khác để không đẩy con trỏ lúc đang nhập. */
    private void bindInput(EditText input, String value) {
        if (!input.getText().toString().equals(value)) {
            input.setText(value);
        }
    }

    /** Trả RESULT_OK để Account đọc lại server sau khi lưu đã được xác nhận. */
    private void finishAfterSave() {
        EditProfileViewModel.State state = editProfileViewModel.getState().getValue();
        if (!closing && isExpectedUserSignedIn() && state != null && state.completed
                && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            closing = true;
            setResult(RESULT_OK);
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** So UID mỗi lần thao tác; không nhận UID do caller truyền trong Intent. */
    private boolean isExpectedUserSignedIn() {
        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
        return expectedUserIdentifier != null
                && signedInUser != null
                && expectedUserIdentifier.equals(signedInUser.getUid());
    }

    /** Xóa giá trị riêng trước khi điều hướng khỏi phiên đã thay đổi. */
    private void clearPrivateForm() {
        bindingDraft = true;
        nameInput.setText("");
        vehicleInput.setText("");
        plateInput.setText("");
        ((TextView) findViewById(R.id.profile_email)).setText("");
        bindingDraft = false;
    }

    /** Về Login và xóa stack để Back không mở form của tài khoản trước. */
    private void openLogin() {
        if (isFinishing()) {
            return;
        }
        clearPrivateForm();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    //endregion
}
