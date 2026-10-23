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
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Đồng bộ FCM token cho tài khoản đang đăng nhập.
 *
 * Schema: fcmTokens/{uid} -> token, updatedAt.
 * Không ghi token vào log và không cho phép đồng bộ sang UID khác.
 */
public class FcmTokenRepository {

    private static final String TAG = "FcmTokenRepository";
    private static final String COLLECTION_FCM_TOKENS = "fcmTokens";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseMessaging messaging;

    public FcmTokenRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        messaging = FirebaseMessaging.getInstance();
    }

    /** Đồng bộ token cho người dùng hiện tại, nếu đã đăng nhập. */
    public void syncTokenForCurrentUser() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        syncTokenForUser(currentUser.getUid());
    }

    /** Lấy token mới nhất và chỉ ghi cho UID vẫn đang đăng nhập. */
    public void syncTokenForUser(final String expectedUid) {
        if (expectedUid == null || expectedUid.trim().isEmpty()) {
            return;
        }

        if (!isCurrentUser(expectedUid)) {
            return;
        }

        messaging.getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Không lấy được FCM token.", task.getException());
                    return;
                }

                String token = task.getResult();

                if (token == null || token.trim().isEmpty()) {
                    Log.w(TAG, "FCM trả về token rỗng.");
                    return;
                }

                if (!isCurrentUser(expectedUid)) {
                    return;
                }

                saveTokenIfChanged(expectedUid, token);
            }
        });
    }

    /** Tránh ghi Firestore khi token trên máy chủ vẫn giống token hiện tại. */
    private void saveTokenIfChanged(final String uid, final String token) {
        final DocumentReference tokenDocument =
                firestore.collection(COLLECTION_FCM_TOKENS).document(uid);

        tokenDocument.get(Source.SERVER).addOnCompleteListener(
                new OnCompleteListener<DocumentSnapshot>() {
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
                                    && token.equals(snapshot.getString("token"))) {
                                return;
                            }
                        }

                        // Vẫn thử ghi nếu lần đọc thất bại; Firestore có thể xếp hàng
                        // thao tác ghi khi ngoại tuyến. Rules vẫn phải xác thực UID.
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("token", token);
                        tokenData.put("updatedAt", FieldValue.serverTimestamp());

                        tokenDocument.set(tokenData).addOnCompleteListener(
                                new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(
                                            @NonNull Task<Void> writeTask) {
                                        if (!writeTask.isSuccessful()) {
                                            Log.w(
                                                    TAG,
                                                    "Không thể lưu FCM token.",
                                                    writeTask.getException());
                                        }
                                    }
                                });
                    }
                });
    }

    /** Xóa token trước khi Firebase Authentication kết thúc phiên đăng nhập. */
    public static Task<Void> deleteTokenForUser(String uid) {
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
                .collection(COLLECTION_FCM_TOKENS)
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
