package com.example.grabshare;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.grabshare.notifications.FcmDebugLogger;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Khoi dong FCM va lien ket FID voi tai khoan dang dang nhap.
 * Khong giu tham chieu Activity va khong thuc hien thao tac UI.
 */
public class GrabShareApplication extends Application {

    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    public void onCreate() {
        super.onCreate();

        String projectId = FirebaseApp.getInstance().getOptions().getProjectId();

        FcmDebugLogger.info(
                "APP_START",
                "package=" + getPackageName() + ", firebaseProjectId=" + projectId);

        firebaseAuth = FirebaseAuth.getInstance();

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                FirebaseUser currentUser = auth.getCurrentUser();

                if (currentUser == null) {
                    FcmDebugLogger.info(
                            "AUTH_STATE",
                            "Chua co user dang nhap; chua lien ket FID voi UID.");
                    return;
                }

                String uid = currentUser.getUid();

                FcmDebugLogger.info(
                        "AUTH_STATE",
                        "Da co user; uidMasked=" + FcmDebugLogger.mask(uid));

                registerFcmForCurrentInstance(uid);
            }
        };

        firebaseAuth.addAuthStateListener(authStateListener);
    }

    /**
     * Goi register() de xac nhan thiet bi da dang ky voi FCM.
     * FcmDebugLogger + onRegistered() se cho biet tiep buoc ghi Firestore.
     */
    private void registerFcmForCurrentInstance(final String uid) {
        FcmDebugLogger.info(
                "FCM_REGISTER_START",
                "Bat dau FirebaseMessaging.register(); uidMasked="
                        + FcmDebugLogger.mask(uid));

        try {
            FirebaseMessaging.getInstance()
                    .register()
                    .addOnCompleteListener(
                            new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    if (!task.isSuccessful()) {
                                        FcmDebugLogger.error(
                                                "FCM_REGISTER_FAIL",
                                                "FirebaseMessaging.register() that bai; uidMasked="
                                                        + FcmDebugLogger.mask(uid),
                                                task.getException());
                                        return;
                                    }

                                    FcmDebugLogger.info(
                                            "FCM_REGISTER_SUCCESS",
                                            "register() hoan tat; cho doi callback onRegistered().");
                                }
                            });
        } catch (RuntimeException exception) {
            FcmDebugLogger.error(
                    "FCM_REGISTER_EXCEPTION",
                    "Khong the bat dau FirebaseMessaging.register(). Kiem tra manifest metadata va Firebase SDK.",
                    exception);
        }
    }
}
