package com.example.grabshare;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.grabshare.repository.notifications.FcmInstallationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Khởi tạo đồng bộ FCM token khi trạng thái đăng nhập thay đổi.
 * Không giữ tham chiếu Activity và không thực hiện thao tác UI.
 */
public class GrabShareApplication extends Application {

    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FcmInstallationRepository fcmInstallationRepository;

    @Override
    public void onCreate() {
        super.onCreate();

        firebaseAuth = FirebaseAuth.getInstance();
        fcmInstallationRepository = new FcmInstallationRepository();

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                FirebaseUser currentUser = auth.getCurrentUser();

                if (currentUser == null) {
                    return;
                }

                fcmTokenRepository.syncTokenForUser(currentUser.getUid());
            }
        };

        firebaseAuth.addAuthStateListener(authStateListener);
    }
}
