package com.example.grabshare.repository.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.installations.FirebaseInstallations;

import java.util.HashMap;
import java.util.Map;

/**
 * Đồng bộ Firebase Installation ID (FID) cho tài khoản đang đăng nhập.
 *
 * Schema: fcmInstallations/{uid} -> fid, updatedAt.
 * Không ghi FID vào log và không cho phép đồng bộ sang UID khác.
 */
public class FcmInstallationRepository {

    private static final String TAG = "FcmInstallationRepository";
    private static final String COLLECTION_INSTALLATIONS = "fcmInstallations";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public FcmInstallationRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    /** Lấy FID và đồng bộ cho tài khoản hiện tại, nếu đã đăng nhập. */
    public void syncInstallationForCurrentUser() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        syncInstallationForUser(currentUser.getUid());
    }

    /** Lấy FID hiện tại từ Firebase Installations. */
    public void syncInstallationForUser(final String expectedUid) {
        if (expectedUid == null || expectedUid.trim().isEmpty()) {
            return;
        }

        if (!isCurrentUser(expectedUid)) {
            return;
        }

        FirebaseInstallations.getInstance().getId()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Không lấy được Firebase Installation ID.",
                                    task.getException());
                            return;
                        }

                        String installationId = task.getResult();

                        if (installationId == null || installationId.trim().isEmpty()) {
                            Log.w(TAG, "Firebase trả về Installation ID rỗng.");
                            return;
                        }

                        saveInstallationIfChanged(expectedUid, installationId);
                    }
                });
    }

    /**
     * Lưu FID mà onRegistered vừa xác nhận đã đăng ký với FCM.
     * Chỉ ghi nếu UID vẫn là tài khoản hiện tại.
     */
    public void syncInstallationForUser(
            final String expectedUid,
            final String installationId) {

        if (expectedUid == null
                || expectedUid.trim().isEmpty()
                || installationId == null
                || installationId.trim().isEmpty()) {
            return;
        }

        if (!isCurrentUser(expectedUid)) {
            return;
        }

        saveInstallationIfChanged(expectedUid, installationId);
    }

    /** Tránh ghi Firestore khi FID vẫn không đổi trên máy chủ. */
    private void saveInstallationIfChanged(
            final String uid,
            final String installationId) {

        if (!isCurrentUser(uid)) {
            return;
        }

        final DocumentReference installationDocument =
                firestore.collection(COLLECTION_INSTALLATIONS).document(uid);

        installationDocument.get(Source.SERVER)
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(
                            @NonNull Task<DocumentSnapshot> readTask) {

                        if (!isCurrentUser(uid)) {
                            return;
                        }

                        if (readTask.isSuccessful()) {
                            DocumentSnapshot snapshot = readTask.getResult();

                            if (snapshot != null
                                    && snapshot.exists()
                                    && installationId.equals(snapshot.getString("fid"))) {
                                return;
                            }
                        }

                        // Nếu đọc thất bại vẫn thử ghi; Firestore có thể xếp hàng
                        // khi ngoại tuyến. Security Rules vẫn phải xác thực UID.
                        Map<String, Object> installationData = new HashMap<>();
                        installationData.put("fid", installationId);
                        installationData.put("updatedAt", FieldValue.serverTimestamp());

                        installationDocument.set(installationData)
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(
                                            @NonNull Task<Void> writeTask) {

                                        if (!writeTask.isSuccessful()) {
                                            Log.w(
                                                    TAG,
                                                    "Không thể lưu Firebase Installation ID.",
                                                    writeTask.getException());
                                        }
                                    }
                                });
                    }
                });
    }

    /** Xóa FID trước khi Firebase Authentication kết thúc phiên đăng nhập. */
    public static Task<Void> deleteInstallationForUser(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("UID không được rỗng."));
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null || !uid.equals(currentUser.getUid())) {
            return Tasks.forException(
                    new IllegalStateException("Tài khoản đã thay đổi."));
        }

        return FirebaseFirestore.getInstance()
                .collection(COLLECTION_INSTALLATIONS)
                .document(uid)
                .delete();
    }

    private boolean isCurrentUser(String expectedUid) {
        FirebaseUser currentUser = auth.getCurrentUser();

        return currentUser != null
                && expectedUid != null
                && expectedUid.equals(currentUser.getUid());
    }
}
