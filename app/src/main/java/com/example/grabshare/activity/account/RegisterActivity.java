package com.example.grabshare.activity.account;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.repository.account.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import android.content.Intent;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.activity.search.HomeActivity;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Màn đăng ký tài khoản và bổ sung hồ sơ còn thiếu. */
public class RegisterActivity extends AppCompatActivity {

    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText vehicleNameInput;
    private EditText vehiclePlateInput;
    private CheckBox termsCheck;
    private Button submitButton;
    private Button loginButton;
    private ProgressBar progress;
    private TextView statusText;

    private AuthRepository authRepository;
    private RegisterViewModel registerViewModel;
    private boolean openingHome;

    private boolean submitting;

    /** Nối form với tác vụ được giữ qua lần xoay màn hình. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_register);

        authRepository = new AuthRepository();

        bindViews();
        configureFormMode();

        registerViewModel = new ViewModelProvider(this)
                .get(RegisterViewModel.class);

        registerViewModel.getState().observe(
                this,
                new Observer<RegisterViewModel.State>() {

                    /** Hiển thị trạng thái mới trên Activity đang hoạt động. */
                    @Override
                    public void onChanged(RegisterViewModel.State state) {
                        if (state == null || isFinishing() || isDestroyed()) {
                            return;
                        }

                        configureFormMode();
                        setSubmitting(state.busy);

                        if (state.busy) {
                            showStatus("Đang xử lý tài khoản và hồ sơ...");
                        } else if (state.completed) {
                            openHomeAfterRegistration(state.uid);
                        } else if (state.error != null) {
                            showRegistrationError(state.error);
                        }
                    }
                });

        submitButton.setOnClickListener(new View.OnClickListener() {
            /** Chỉ gửi khi người dùng chủ động bấm nút. */
            @Override
            public void onClick(View view) {
                submitRegistration();
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            /** Trở về màn trước nếu không có tác vụ đang chạy. */
            @Override
            public void onClick(View view) {
                leaveRegistration();
            }
        });

        findViewById(R.id.header_back).setOnClickListener(
                new View.OnClickListener() {

                    /** Xử lý nút quay lại trên thanh tiêu đề. */
                    @Override
                    public void onClick(View view) {
                        leaveRegistration();
                    }
                });

        findViewById(R.id.open_terms).setOnClickListener(
                new View.OnClickListener() {

                    /** Mở điều khoản mà không thay đổi form hoặc tự tích đồng ý. */
                    @Override
                    public void onClick(View view) {
                        if (submitting
                                || getSupportFragmentManager().isStateSaved()) {
                            return;
                        }

                        String tag = "registration_terms";

                        if (getSupportFragmentManager()
                                .findFragmentByTag(tag) == null) {
                            new RegistrationTermsDialog().show(
                                    getSupportFragmentManager(), tag);
                        }
                    }
                });

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    /** Đồng nhất nút Back hệ thống với nút quay lại. */
                    @Override
                    public void handleOnBackPressed() {
                        leaveRegistration();
                    }
                });
    }

    /** Lấy các View theo ID thật trong XML đăng ký. */
    private void bindViews() {
        nameInput = findViewById(R.id.register_name);
        emailInput = findViewById(R.id.register_email);
        passwordInput = findViewById(R.id.register_password);
        vehicleNameInput = findViewById(R.id.register_vehicle_name);
        vehiclePlateInput = findViewById(R.id.register_vehicle_plate);
        termsCheck = findViewById(R.id.register_terms);
        submitButton = findViewById(R.id.register_submit);
        loginButton = findViewById(R.id.go_login);
        progress = findViewById(R.id.register_progress);
        statusText = findViewById(R.id.register_status);
    }

    /** Tài khoản đã đăng nhập chỉ cần bổ sung hồ sơ, không tạo Auth mới. */
    private void configureFormMode() {
        FirebaseUser user = authRepository.getCurrentUser();
        boolean hasSession = user != null;

        TextView headerTitle = findViewById(R.id.header_title);

        headerTitle.setText(
                hasSession ? "Hoàn thiện hồ sơ" : "Đăng ký");

        submitButton.setText(
                hasSession ? "Lưu hồ sơ" : "Đăng ký");

        loginButton.setText(
                hasSession ? "Quay lại" : "Đã có tài khoản? Đăng nhập");

        emailInput.setEnabled(!hasSession);

        View passwordGroup = (View) passwordInput.getParent();
        passwordGroup.setVisibility(
                hasSession ? View.GONE : View.VISIBLE);

        if (hasSession) {
            emailInput.setText(user.getEmail());
            passwordInput.setText("");
        }
    }

    /** Hiển thị thông báo để người dùng biết kết quả thao tác. */
    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    /** Hiện lỗi tại ô cần sửa và thông báo ngắn để tránh bị khuất dưới form. */
    private boolean showValidationError(View target, String message) {
        showStatus(message);
        target.requestFocus();

        if (target instanceof EditText) {
            ((EditText) target).setError(message);
        } else if (target instanceof CheckBox) {
            ((CheckBox) target).setError(message);
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        return false;
    }

    /** Kiểm tra dữ liệu bắt buộc trước thông tin xe tùy chọn. */
    private boolean validateForm() {
        nameInput.setError(null);
        emailInput.setError(null);
        passwordInput.setError(null);
        vehicleNameInput.setError(null);
        vehiclePlateInput.setError(null);
        termsCheck.setError(null);
        statusText.setVisibility(View.GONE);

        String name = nameInput.getText().toString().trim();
        String vehicleName = vehicleNameInput.getText().toString().trim();
        String vehiclePlate = vehiclePlateInput.getText().toString().trim();

        if (name.isEmpty() || name.length() > 80) {
            return showValidationError(nameInput,
                    "Tên hiển thị phải có từ 1 đến 80 ký tự.");
        }

        FirebaseUser user = authRepository.getCurrentUser();

        if (user == null) {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (email.isEmpty()) {
                return showValidationError(emailInput,
                        "Vui lòng nhập email.");
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                return showValidationError(emailInput,
                        "Vui lòng nhập email hợp lệ.");
            }

            if (password.isEmpty()) {
                return showValidationError(passwordInput,
                        "Vui lòng nhập mật khẩu.");
            }
        } else if (user.getEmail() == null
                || user.getEmail().trim().isEmpty()) {
            return showValidationError(submitButton,
                    "Tài khoản đang đăng nhập chưa có email hợp lệ.");
        }

        if (vehicleName.length() > 80) {
            return showValidationError(vehicleNameInput,
                    "Tên xe tối đa 80 ký tự.");
        }

        if (vehiclePlate.length() > 20) {
            return showValidationError(vehiclePlateInput,
                    "Biển số tối đa 20 ký tự.");
        }

        if (!vehicleName.isEmpty() && vehiclePlate.isEmpty()) {
            return showValidationError(vehiclePlateInput,
                    "Vui lòng nhập biển số khi đã nhập tên xe, "
                            + "hoặc để trống cả hai ô.");
        }

        if (vehicleName.isEmpty() && !vehiclePlate.isEmpty()) {
            return showValidationError(vehicleNameInput,
                    "Vui lòng nhập tên xe khi đã nhập biển số, "
                            + "hoặc để trống cả hai ô.");
        }

        if (!termsCheck.isChecked()) {
            return showValidationError(termsCheck,
                    "Vui lòng đọc và đồng ý với Điều khoản sử dụng.");
        }

        return true;
    }

    /** Kiểm tra thiết bị có mạng đã được hệ thống xác nhận truy cập Internet. */
    private boolean hasInternetConnection() {
        ConnectivityManager manager =
                getSystemService(ConnectivityManager.class);

        if (manager == null) {
            return false;
        }

        Network network = manager.getActiveNetwork();

        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(network);

        return capabilities != null
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Khóa form và hiện tiến trình trong lúc đang xử lý yêu cầu. */
    private void setSubmitting(boolean value) {
        submitting = value;

        boolean hasSession =
                authRepository.getCurrentUser() != null;

        nameInput.setEnabled(!value);
        emailInput.setEnabled(!value && !hasSession);
        passwordInput.setEnabled(!value && !hasSession);
        vehicleNameInput.setEnabled(!value);
        vehiclePlateInput.setEnabled(!value);
        termsCheck.setEnabled(!value);
        submitButton.setEnabled(!value);
        loginButton.setEnabled(!value);

        findViewById(R.id.header_back).setEnabled(!value);
        findViewById(R.id.open_terms).setEnabled(!value);

        progress.setVisibility(value ? View.VISIBLE : View.GONE);

        if (value) {
            submitButton.setText("Đang xử lý...");
        } else {
            submitButton.setText(
                    hasSession ? "Lưu hồ sơ" : "Đăng ký");
        }
    }

    /** Kiểm tra form và mạng trước khi yêu cầu ViewModel xử lý. */
    private void submitRegistration() {
        if (submitting || openingHome) {
            return;
        }

        if (!validateForm()) {
            return;
        }

        if (!hasInternetConnection()) {
            showStatus(
                    "Bạn đang offline hoặc chưa truy cập được Internet. "
                            + "Hãy kết nối mạng rồi thử lại.");
            return;
        }

        registerViewModel.submit(
                emailInput.getText().toString().trim(),
                passwordInput.getText().toString(),
                nameInput.getText().toString().trim(),
                vehicleNameInput.getText().toString().trim(),
                vehiclePlateInput.getText().toString().trim());
    }

    /** Báo lỗi dễ hiểu và giữ phiên để bổ sung lại hồ sơ khi cần. */
    private void showRegistrationError(Exception error) {
        if (error instanceof FirebaseAuthWeakPasswordException) {
            showStatus(
                    "Mật khẩu chưa đáp ứng yêu cầu bảo mật. "
                            + "Hãy chọn mật khẩu mạnh hơn.");
        } else if (error instanceof FirebaseAuthUserCollisionException) {
            showStatus(
                    "Không thể tạo tài khoản bằng email này. "
                            + "Nếu đã đăng ký, hãy quay lại đăng nhập.");
        } else if (error instanceof FirebaseNetworkException) {
            showStatus("Kết nối bị gián đoạn. Hãy kiểm tra mạng rồi thử lại.");
        } else if (authRepository.getCurrentUser() != null) {
            showStatus(
                    "Bạn đã có phiên đăng nhập nhưng chưa hoàn tất hồ sơ. "
                            + "Hãy kiểm tra dữ liệu và mạng rồi bấm Lưu hồ sơ.");
        } else {
            showStatus(
                    "Chưa hoàn tất đăng ký. "
                            + "Hãy kiểm tra thông tin và thử lại.");
        }
    }

    /** Chỉ mở Home cho đúng phiên đã được xác nhận hoàn tất hồ sơ. */
    private void openHomeAfterRegistration(String expectedUid) {
        if (openingHome) {
            return;
        }

        FirebaseUser user = authRepository.getCurrentUser();

        if (user == null
                || expectedUid == null
                || !expectedUid.equals(user.getUid())) {
            showStatus("Phiên đăng nhập đã thay đổi. Hãy quay lại đăng nhập.");
            return;
        }

        openingHome = true;

        Intent intent = new Intent(
                RegisterActivity.this, HomeActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        finish();
    }

    /** Không rời form bằng nút Back khi tác vụ chưa kết thúc. */
    private void leaveRegistration() {
        if (submitting) {
            showStatus("Đang xử lý, vui lòng chờ kết quả.");
            return;
        }

        finish();
    }
}