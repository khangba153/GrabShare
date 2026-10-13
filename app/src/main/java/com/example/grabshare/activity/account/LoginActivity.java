package com.example.grabshare.activity.account;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
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

import com.example.grabshare.repository.account.LoginProfileRepository;

import android.content.Intent;
import com.example.grabshare.activity.search.HomeActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView loginStatus;
    private AuthRepository authRepository;
    private boolean submitting;
    private Button googleButton;
    private CancellationSignal googleCancellation;

    /** Mở màn chính sau khi đăng nhập và kiểm tra có hai hồ sơ. */
    private void openHome() {
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

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        emailInput.setError(null);
        passwordInput.setError(null);

        if (email.isEmpty()
                || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Nhập email hợp lệ.");
            showStatus("Nhập email hợp lệ để đăng nhập.");
            emailInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Nhập mật khẩu.");
            showStatus("Bạn chưa nhập mật khẩu.");
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
                        showStatus(message);

                        Toast.makeText(LoginActivity.this,
                                message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Hiển thị trạng thái ngay trên form để người dùng không bỏ lỡ kết quả. */
    private void showStatus(String message) {
        loginStatus.setText(message);
        loginStatus.setVisibility(View.VISIBLE);
    }

    /** Khóa form trong lúc chờ để tránh bấm gửi nhiều lần. */
    private void setSubmitting(boolean value) {
        submitting = value;
        emailInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        loginButton.setEnabled(!value);
        googleButton.setEnabled(!value);
        loginButton.setText(value ? "Đang đăng nhập..." : "Đăng nhập");
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
                                showStatus(
                                        "Không đọc được tài khoản Google. Hãy thử lại.");
                            }
                        } else {
                            setSubmitting(false);
                            showStatus("Thông tin đăng nhập Google không hợp lệ.");
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
                            showStatus(
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
                        showStatus(
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
        setSubmitting(true);
        showStatus("Đã đăng nhập. Đang kiểm tra hồ sơ...");

        LoginProfileRepository repository = new LoginProfileRepository();

        repository.checkProfile(user.getUid(),
                new RepositoryCallback<Boolean>() {

                    /** Hiển thị kết quả tồn tại của hai document hồ sơ. */
                    @Override
                    public void onSuccess(Boolean hasProfile) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSubmitting(false);
                        if (Boolean.TRUE.equals(hasProfile)) {
                            openHome();
                        } else {
                            showStatus("Đã đăng nhập với: " + user.getEmail()
                                    + "\nChưa có đủ hồ sơ trên Firestore.");
                        }
                    }

                    /** Báo lỗi đọc dữ liệu nhưng vẫn giữ phiên đăng nhập. */
                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSubmitting(false);
                        showStatus("Đã đăng nhập với: " + user.getEmail()
                                + "\nKhông đọc được hồ sơ. "
                                + "Kiểm tra mạng và quyền Firestore.");
                    }
                });
    }

    //Bổ sung AuthStateListener để nhận thay đổi phiên
    private final FirebaseAuth.AuthStateListener authStateListener =
            new FirebaseAuth.AuthStateListener() {

                /** Nhận phiên mới, kể cả khi xác thực hoàn tất sau khi xoay. */
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                    if (isFinishing() || isDestroyed() || submitting) {
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();

                    if (user != null) {
                        checkLoginProfile(user);
                    } else {
                        showStatus("Bạn chưa đăng nhập.");
                    }
                }
            };
}
