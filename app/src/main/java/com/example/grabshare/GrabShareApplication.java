package com.example.grabshare;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.grabshare.presentation.ScreenLayoutCallbacks;
import com.example.grabshare.repository.notifications.FcmInstallationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Đăng ký bố cục dùng chung và đồng bộ Firebase Installation ID theo phiên.
 * Không lưu Activity hoặc View trong Application.
 */
public class GrabShareApplication extends Application {

    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FcmInstallationRepository fcmInstallationRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ScreenLayoutCallbacks());

        firebaseAuth = FirebaseAuth.getInstance();
        fcmInstallationRepository = new FcmInstallationRepository();

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                FirebaseUser currentUser = auth.getCurrentUser();

                if (currentUser == null) {
                    return;
                }

                fcmInstallationRepository.syncInstallationForUser(currentUser.getUid());
            }
        };

        firebaseAuth.addAuthStateListener(authStateListener);
    }
}
