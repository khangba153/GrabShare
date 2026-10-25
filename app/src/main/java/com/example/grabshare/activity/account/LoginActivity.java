package com.example.grabshare.activity.account;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.FragmentResultListener;
import androidx.activity.OnBackPressedCallback;

import com.example.grabshare.R;
import com.example.grabshare.presentation.ScreenFeedback;
import com.example.grabshare.presentation.PasswordVisibility;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.account.AuthRepository;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseUser;

import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CustomCredential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.TimeoutException;

import android.content.Intent;
import com.example.grabshare.activity.search.HomeActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button registerButton;
    private TextView loginStatus;
    private AuthRepository authRepository;
    private boolean submitting;
    private Button googleButton;
    private CancellationSignal googleCancellation;
    private LoginProfileViewModel profileViewModel;
    private boolean homeOpened;
    private LogoutViewModel logoutViewModel;

    /** Mở màn chính sau khi đăng nhập và kiểm tra có hai hồ sơ. */
    private void openHome() {
        if (homeOpened) {
            return;
        }
        homeOpened = true;
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    /** Hiển thị form và nối nút đăng nhập với Firebase. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_login);

        emailInput = findViewById(R.id.login_email);
        passwordInput = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_submit);
        loginStatus = findViewById(R.id.login_status);
        authRepository = new AuthRepository();

        googleButton = findViewById(R.id.login_google);

        registerButton = findViewById(R.id.go_register);
        profileViewModel = new ViewModelProvider(this).get(LoginProfileViewModel.class);
        logoutViewModel = new ViewModelProvider(this).get(LogoutViewModel.class);
        PasswordVisibility.bind(passwordInput, (TextView) findViewById(R.id.login_toggle_password));
        connectSessionLogout();
        profileViewModel.getState().observe(this,
                new Observer<LoginProfileViewModel.State>() {
                    @Override
                    public void onChanged(LoginProfileViewModel.State state) {
                        showProfileState(state);
                    }
                });

        findViewById(R.id.forgot_password).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (submitting) {
                            return;
                        }

                        passwordInput.setText("");

                        Intent intent = new Intent(
                                LoginActivity.this,
                                ResetPasswordActivity.class);
                        startActivity(intent);
                    }
                });

        registerButton.setOnClickListener(new View.OnClickListener() {
            /** Mở đăng ký mới hoặc bổ sung hồ sơ cho phiên hiện tại. */
            @Override
            public void onClick(View view) {
                if (submitting) {
                    return;
                }

                passwordInput.setText("");

                profileViewModel.clearProfile();
                Intent intent = new Intent(
                        LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        googleButton.setOnClickListener(new View.OnClickListener() {
            /** Mở chọn tài khoản khi người dùng yêu cầu đăng nhập Google. */
            @Override
            public void onClick(View view) {
                startGoogleLogin();
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            /** Kiểm tra form khi người dùng bấm đăng nhập. */
            @Override
            public void onClick(View view) {
                submitLogin();
            }
        });
    }

    /** Kiểm tra dữ liệu rồi gửi yêu cầu đăng nhập một lần. */
    private void submitLogin() {
        if (submitting) {
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            profileViewModel.checkProfile(currentUser.getUid(), true);
            return;
        }

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        emailInput.setError(null);
        passwordInput.setError(null);

        if (email.isEmpty()
                || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Nhập email hợp lệ.");
            showError("Nhập email hợp lệ để đăng nhập.");
            emailInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Nhập mật khẩu.");
            showError("Bạn chưa nhập mật khẩu.");
            passwordInput.requestFocus();
            return;
        }

        setSubmitting(true);
        showStatus("Đang kết nối Firebase để đăng nhập...");

        authRepository.signIn(email, password,
                new RepositoryCallback<FirebaseUser>() {
                    /** Hiển thị thành công khi Firebase đã xác thực tài khoản. */
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        passwordInput.setText("");
                        checkLoginProfile(user);
                    }

                    /** Hiển thị lỗi và cho phép người dùng thử lại. */
                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSubmitting(false);

                        String message =
                                "Đăng nhập thất bại. Kiểm tra thông tin và thử lại.";

                        if (error instanceof FirebaseNetworkException) {
                            message = "Không kết nối được mạng. Hãy thử lại.";
                        }
                        showError(message);

                        Toast.makeText(LoginActivity.this,
                                message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Hiển thị trạng thái ngay trên form để người dùng không bỏ lỡ kết quả. */
    private void showStatus(String message) {
        ScreenFeedback.show(loginStatus, message, ScreenFeedback.Kind.INFORMATION);
    }

    /** Lỗi có màu và nội dung hướng dẫn, không chỉ xuất hiện trong Toast. */
    private void showError(String message) {
        ScreenFeedback.show(loginStatus, message, ScreenFeedback.Kind.ERROR);
    }

    /** Khóa form trong lúc chờ để tránh bấm gửi nhiều lần. */
    private void setSubmitting(boolean value) {
        submitting = value;
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean hasSession = currentUser != null;
        emailInput.setEnabled(!value && !hasSession);
        passwordInput.setEnabled(!value && !hasSession);
        loginButton.setEnabled(!value);
        googleButton.setEnabled(!value && !hasSession);
        registerButton.setEnabled(!value);
        findViewById(R.id.forgot_password).setEnabled(!value);
        findViewById(R.id.login_toggle_password).setEnabled(!value && !hasSession);
        findViewById(R.id.login_logout).setEnabled(!value);
        if (value) {
            findViewById(R.id.login_progress).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.login_progress).setVisibility(View.GONE);
        }
        TextView sessionAccount = findViewById(R.id.login_session_account);
        if (hasSession) {
            findViewById(R.id.login_credentials).setVisibility(View.GONE);
            googleButton.setVisibility(View.GONE);
            findViewById(R.id.login_alternative).setVisibility(View.GONE);
            findViewById(R.id.login_logout).setVisibility(View.VISIBLE);
            registerButton.setVisibility(View.GONE);
            String accountLabel = currentUser.getEmail();
            if (accountLabel == null || accountLabel.isEmpty()) {
                accountLabel = getString(R.string.profile_account_placeholder);
            }
            sessionAccount.setText(getString(R.string.leader_session_account, accountLabel));
            sessionAccount.setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.login_credentials).setVisibility(View.VISIBLE);
            googleButton.setVisibility(View.VISIBLE);
            findViewById(R.id.login_alternative).setVisibility(View.VISIBLE);
            findViewById(R.id.login_logout).setVisibility(View.GONE);
            registerButton.setVisibility(View.VISIBLE);
            sessionAccount.setVisibility(View.GONE);
        }
        if (value) {
            loginButton.setText("Đang xử lý...");
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            loginButton.setText("Kiểm tra lại hồ sơ");
        } else {
            loginButton.setText("Đăng nhập");
        }
    }

    /** Mở trình chọn tài khoản Google bằng Credential Manager. */
    private void startGoogleLogin() {
        if (submitting) {
            return;
        }

        setSubmitting(true);
        showStatus("Đang chọn tài khoản Google...");

        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(
                        getString(R.string.default_web_client_id))
                        .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        googleCancellation = new CancellationSignal();

        CredentialManager.create(this).getCredentialAsync(
                this,
                request,
                googleCancellation,
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<
                        GetCredentialResponse, GetCredentialException>() {

                    /** Nhận token Google rồi chuyển sang xác thực Firebase. */
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        Credential credential = result.getCredential();

                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential
                                .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                .equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential googleCredential =
                                        GoogleIdTokenCredential.createFrom(
                                                credential.getData());

                                authenticateGoogle(
                                        googleCredential.getIdToken());
                            } catch (Exception error) {
                                setSubmitting(false);
                                showError(
                                        "Không đọc được tài khoản Google. Hãy thử lại.");
                            }
                        } else {
                            setSubmitting(false);
                            showError("Thông tin đăng nhập Google không hợp lệ.");
                        }
                    }

                    /** Cho phép thử lại sau khi hủy hoặc gặp lỗi chọn tài khoản. */
                    @Override
                    public void onError(@NonNull GetCredentialException error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSubmitting(false);

                        if (error instanceof GetCredentialCancellationException) {
                            showStatus("Bạn đã hủy đăng nhập Google.");
                        } else {
                            showError(
                                    "Không mở được đăng nhập Google. Kiểm tra mạng "
                                            + "và tài khoản Google trên thiết bị.");
                        }
                    }
                });
    }

    /** Dùng token Google để tạo phiên đăng nhập Firebase thật. */
    private void authenticateGoogle(String idToken) {
        showStatus("Đang xác thực với Firebase...");

        authRepository.signInGoogle(
                idToken,
                new RepositoryCallback<FirebaseUser>() {

                    /** Chỉ báo thành công khi Firebase đã xác thực. */
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        passwordInput.setText("");
                        checkLoginProfile(user);
                    }

                    /** Hiển thị lỗi Firebase và cho người dùng thử lại. */
                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSubmitting(false);
                        showError(
                                "Firebase chưa xác thực được Google. "
                                        + "Kiểm tra mạng hoặc cấu hình đăng nhập.");
                    }
                });
    }

    /** Hủy trình chọn Google khi màn bị đóng, không tự mở lại khi xoay. */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (googleCancellation != null) {
            googleCancellation.cancel();
        }
    }

    /** Theo dõi phiên khi màn đăng nhập đang hiển thị. */
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    /** Gỡ listener khi rời màn để tránh giữ Activity và nhận cập nhật trùng. */
    @Override
    protected void onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        super.onStop();
    }

    /** Đọc hồ sơ sau khi Firebase đã xác thực tài khoản. */
    private void checkLoginProfile(final FirebaseUser user) {
        profileViewModel.checkProfile(user.getUid(), true);
    }

    /** Hiển thị tiến trình giữ trong ViewModel; không dùng kết quả của UID cũ. */
    private void showProfileState(LoginProfileViewModel.State state) {
        if (state == null || state.userIdentifier == null
                || isFinishing() || isDestroyed() || logoutInProgress()) {
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || !state.userIdentifier.equals(currentUser.getUid())) {
            return;
        }

        setSubmitting(state.loading);
        if (state.loading) {
            showStatus("Đã đăng nhập. Đang kiểm tra hồ sơ...");
        } else if (state.error != null) {
            if (state.error instanceof TimeoutException) {
                showError("Kiểm tra hồ sơ quá thời gian chờ. "
                        + "Kiểm tra mạng rồi bấm Kiểm tra lại hồ sơ.");
            } else {
                showError("Không đọc được hồ sơ. Kiểm tra mạng và quyền Firestore, "
                        + "rồi bấm Kiểm tra lại hồ sơ.");
            }
        } else if (Boolean.TRUE.equals(state.hasBothProfiles)) {
            openHome();
        } else {
            registerButton.setText("Hoàn thiện hồ sơ");
            registerButton.setVisibility(View.VISIBLE);
            showStatus("Bạn đã đăng nhập nhưng chưa có đủ hồ sơ. "
                    + "Bấm Hoàn thiện hồ sơ để tiếp tục.");
        }
    }

    private final FirebaseAuth.AuthStateListener authStateListener =
            new FirebaseAuth.AuthStateListener() {

                /** Nhận phiên mới, kể cả khi xác thực hoàn tất sau khi xoay. */
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                    if (isFinishing() || isDestroyed() || logoutInProgress()) {
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    LoginProfileViewModel.State profileState =
                            profileViewModel.getState().getValue();

                    if (user != null) {
                        if (submitting && (profileState == null
                                || profileState.userIdentifier == null)) {
                            return;
                        }
                        profileViewModel.checkProfile(user.getUid(), false);
                    } else {
                        if (profileState != null && profileState.userIdentifier != null) {
                            profileViewModel.clearProfile();
                            setSubmitting(false);
                        }
                        if (!submitting) {
                            setSubmitting(false);
                            registerButton.setText("Đăng ký");
                            showStatus("Bạn chưa đăng nhập.");
                        }
                    }
                }
            };

    /** Cùng cơ chế dọn cache với Account; không đổi tài khoản bằng cách bỏ qua logout. */
    private void connectSessionLogout() {
        getSupportFragmentManager().setFragmentResultListener(
                LogoutConfirmationDialog.RESULT_KEY, this, new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (!submitting && currentUser != null && currentUser.getUid().equals(
                                result.getString(LogoutConfirmationDialog.USER_IDENTIFIER))) {
                            profileViewModel.clearProfile();
                            logoutViewModel.submit();
                        }
                    }
                });
        findViewById(R.id.login_logout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (!submitting && currentUser != null
                        && !getSupportFragmentManager().isStateSaved()
                        && getSupportFragmentManager().findFragmentByTag(
                                LogoutConfirmationDialog.TAG) == null) {
                    LogoutConfirmationDialog.create(currentUser.getUid()).show(
                            getSupportFragmentManager(), LogoutConfirmationDialog.TAG);
                }
            }
        });
        final OnBackPressedCallback logoutBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showStatus(getString(R.string.profile_logging_out));
            }
        };
        getOnBackPressedDispatcher().addCallback(this, logoutBackCallback);
        logoutViewModel.getState().observe(this, new Observer<LogoutViewModel.State>() {
            @Override
            public void onChanged(LogoutViewModel.State state) {
                if (state == null || isFinishing() || isDestroyed()) {
                    return;
                }
                logoutBackCallback.setEnabled(state.busy || state.completed);
                if (state.completed) {
                    Intent intent = new Intent(LoginActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else if (state.busy) {
                    setSubmitting(true);
                    showStatus(getString(R.string.profile_logging_out));
                } else if (state.error != null) {
                    setSubmitting(false);
                    showError(ProfileMessages.explain(LoginActivity.this, state.error));
                }
            }
        });
    }

    /** Observer Auth không được mở Home khi đang kết thúc phiên cũ. */
    private boolean logoutInProgress() {
        if (logoutViewModel == null) {
            return false;
        }
        LogoutViewModel.State state = logoutViewModel.getState().getValue();
        return state != null && (state.busy || state.completed);
    }
}
