package com.example.grabshare.activity.passenger;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Chặn callback của màn đã dừng hoặc tài khoản cũ trong các thao tác passenger. */
final class PassengerSessionGuard {
    private final AppCompatActivity activity;
    private final FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authListener;
    private String expectedUserId;
    private int generation;
    private boolean active;

    /** Giữ tham chiếu màn trong vòng đời của chính màn đó, không lưu vào singleton. */
    PassengerSessionGuard(AppCompatActivity activity) {
        this.activity = activity;
        auth = FirebaseAuth.getInstance();
    }

    /** Bắt đầu thế hệ callback mới và đóng màn nếu phiên đăng nhập đã thay đổi. */
    boolean start(String userId) {
        stop();
        expectedUserId = userId;
        active = true;
        if (!current(generation)) {
            activity.finish();
            return false;
        }
        authListener = new FirebaseAuth.AuthStateListener() {
            /** Không để màn thuộc tài khoản cũ tiếp tục hiển thị hoặc thao tác. */
            @Override
            public void onAuthStateChanged(FirebaseAuth firebaseAuth) {
                if (active && !current(generation)) {
                    stop();
                    activity.finish();
                }
            }
        };
        auth.addAuthStateListener(authListener);
        return true;
    }

    /** Lấy mã thế hệ để callback chụp lại trước khi bắt đầu tác vụ bất đồng bộ. */
    int token() {
        return generation;
    }

    /** Chỉ chấp nhận callback của đúng thế hệ, UID và màn đang hoạt động. */
    boolean current(int token) {
        FirebaseUser user = auth.getCurrentUser();
        return active && token == generation && !activity.isFinishing()
                && !activity.isDestroyed() && user != null
                && user.getUid().equals(expectedUserId);
    }

    /** Vô hiệu hóa callback đã xếp hàng và tháo listener khi màn dừng. */
    void stop() {
        active = false;
        generation = generation + 1;
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
            authListener = null;
        }
    }
}
