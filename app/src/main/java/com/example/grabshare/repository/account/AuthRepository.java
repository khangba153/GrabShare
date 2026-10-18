package com.example.grabshare.repository.account;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthRepository {

    private final FirebaseAuth auth;

    /** Sử dụng Firebase Authentication của project đã cấu hình. */
    public AuthRepository() {
        auth = FirebaseAuth.getInstance();
    }

    /** Đăng nhập bằng email và mật khẩu, trả kết quả thực tế từ Firebase. */
    public void signIn(String email, String password,
                       final RepositoryCallback<FirebaseUser> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    /** Chuyển kết quả đăng nhập về nơi gọi. */
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                callback.onSuccess(user);
                            } else {
                                callback.onError(new IllegalStateException(
                                        "Không lấy được tài khoản sau đăng nhập."));
                            }
                        } else {
                            Exception error = task.getException();
                            if (error == null) {
                                error = new IllegalStateException(
                                        "Đăng nhập không thành công.");
                            }
                            callback.onError(error);
                        }
                    }
                });
    }

    /** Xác thực Firebase bằng token Google do Credential Manager cung cấp. */
    public void signInGoogle(String idToken,
                             final RepositoryCallback<FirebaseUser> callback) {
        auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    /** Trả tài khoản thật khi Firebase xác thực thành công. */
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();

                            if (user != null) {
                                callback.onSuccess(user);
                            } else {
                                callback.onError(new IllegalStateException(
                                        "Không lấy được tài khoản Google."));
                            }
                        } else {
                            Exception error = task.getException();

                            if (error == null) {
                                error = new IllegalStateException(
                                        "Đăng nhập Google không thành công.");
                            }

                            callback.onError(error);
                        }
                    }
                });
    }

    /** Tạo tài khoản email mới; hồ sơ Firestore được xử lý ở bước tiếp theo. */
    public void signUp(
            String email,
            String password,
            final RepositoryCallback<FirebaseUser> callback) {

        if (auth.getCurrentUser() != null) {
            callback.onError(new IllegalStateException(
                    "Bạn đang đăng nhập. Hãy hoàn thiện hồ sơ hiện tại "
                            + "hoặc đăng xuất trước khi tạo tài khoản mới."));
            return;
        }

        if (email == null
                || !android.util.Patterns.EMAIL_ADDRESS
                .matcher(email.trim()).matches()) {
            callback.onError(new IllegalArgumentException(
                    "Vui lòng nhập email hợp lệ."));
            return;
        }

        if (password == null || password.isEmpty()) {
            callback.onError(new IllegalArgumentException(
                    "Vui lòng nhập mật khẩu."));
            return;
        }

        auth.createUserWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener(
                        new OnCompleteListener<AuthResult>() {

                            /** Trả tài khoản vừa tạo hoặc lỗi thực tế từ Firebase. */
                            @Override
                            public void onComplete(
                                    @NonNull Task<AuthResult> task) {

                                if (!task.isSuccessful()) {
                                    Exception error = task.getException();

                                    if (error == null) {
                                        error = new IllegalStateException(
                                                "Không tạo được tài khoản.");
                                    }

                                    callback.onError(error);
                                    return;
                                }

                                AuthResult result = task.getResult();
                                FirebaseUser createdUser =
                                        result == null ? null : result.getUser();
                                FirebaseUser currentUser = auth.getCurrentUser();

                                if (createdUser == null
                                        || currentUser == null
                                        || !createdUser.getUid()
                                        .equals(currentUser.getUid())) {
                                    callback.onError(new IllegalStateException(
                                            "Phiên đăng nhập đã thay đổi "
                                                    + "sau khi tạo tài khoản."));
                                    return;
                                }

                                callback.onSuccess(createdUser);
                            }
                        });
    }

    /** Lấy tài khoản hiện tại; trả null nếu chưa đăng nhập. */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /** Kết thúc phiên đăng nhập trên thiết bị. */
    public void signOut() {
        auth.signOut();
    }

    /** Gửi email khôi phục mật khẩu qua Firebase Authentication. */
    public void sendPasswordResetEmail(
            String email,
            final RepositoryCallback<Void> callback) {

        if (email == null
                || !android.util.Patterns.EMAIL_ADDRESS
                .matcher(email.trim()).matches()) {
            callback.onError(new IllegalArgumentException(
                    "Vui lòng nhập email hợp lệ."));
            return;
        }

        auth.sendPasswordResetEmail(email.trim())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            callback.onSuccess(null);
                            return;
                        }

                        Exception error = task.getException();

                        if (error == null) {
                            error = new IllegalStateException(
                                    "Không gửi được yêu cầu khôi phục mật khẩu.");
                        }

                        callback.onError(error);
                    }
                });
    }
}