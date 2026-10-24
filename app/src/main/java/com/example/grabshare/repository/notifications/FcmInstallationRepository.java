package com.example.grabshare.repository.notifications;

import androidx.annotation.NonNull;

import com.example.grabshare.notifications.FcmDebugLogger;
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
 * Lien ket Firebase Installation ID (FID) voi tai khoan dang dang nhap.
 *
 * Schema: fcmInstallations/{uid} -> fid, updatedAt.
 * Khong ghi toan bo FID vao Logcat va khong ghi sang UID khac.
 */
public class FcmInstallationRepository {

    private static final String COLLECTION_INSTALLATIONS = "fcmInstallations";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public FcmInstallationRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    /** Chi dung khi can lay FID tu Firebase Installations de kiem tra rieng. */
    public void syncInstallationForCurrentUser() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            FcmDebugLogger.warning(
                    "FID_LOOKUP_SKIP",
                    "Khong co user dang nhap.");
            return;
        }

        syncInstallationForUser(currentUser.getUid());
    }

    /**
     * Lay FID tu Firebase Installations. Buoc nay rieng le khong khang dinh FCM
     * da dang ky; pipeline chinh dung callback onRegistered() ben duoi.
     */
    public void syncInstallationForUser(final String expectedUid) {
        if (expectedUid == null || expectedUid.trim().isEmpty()) {
            FcmDebugLogger.warning(
                    "FID_LOOKUP_SKIP",
                    "UID rong.");
            return;
        }

        if (!isCurrentUser(expectedUid)) {
            FcmDebugLogger.warning(
                    "FID_LOOKUP_SKIP",
                    "UID da thay doi; expectedUidMasked="
                            + FcmDebugLogger.mask(expectedUid));
            return;
        }

        FcmDebugLogger.info(
                "FID_LOOKUP_START",
                "Lay FID tu Firebase Installations; uidMasked="
                        + FcmDebugLogger.mask(expectedUid));

        FirebaseInstallations.getInstance().getId()
                .addOnCompleteListener(
                        new OnCompleteListener<String>() {
                            @Override
                            public void onComplete(@NonNull Task<String> task) {
                                if (!task.isSuccessful()) {
                                    FcmDebugLogger.error(
                                            "FID_LOOKUP_FAIL",
                                            "Khong lay duoc Firebase Installation ID.",
                                            task.getException());
                                    return;
                                }

                                String installationId = task.getResult();

                                if (installationId == null
                                        || installationId.trim().isEmpty()) {
                                    FcmDebugLogger.warning(
                                            "FID_LOOKUP_FAIL",
                                            "Firebase tra ve FID rong.");
                                    return;
                                }

                                FcmDebugLogger.info(
                                        "FID_LOOKUP_SUCCESS",
                                        "Lay duoc FID; fidMasked="
                                                + FcmDebugLogger.mask(installationId));

                                saveInstallationIfChanged(
                                        expectedUid,
                                        installationId);
                            }
                        });
    }

    /**
     * Luu FID tu callback onRegistered() - moc xac nhan app da dang ky voi FCM.
     */
    public void syncInstallationForUser(
            final String expectedUid,
            final String installationId) {

        if (expectedUid == null
                || expectedUid.trim().isEmpty()
                || installationId == null
                || installationId.trim().isEmpty()) {
            FcmDebugLogger.warning(
                    "FID_CALLBACK_SKIP",
                    "UID hoac FID trong callback bi rong.");
            return;
        }

        FcmDebugLogger.info(
                "FID_CALLBACK",
                "Nhan callback onRegistered(); uidMasked="
                        + FcmDebugLogger.mask(expectedUid)
                        + ", fidMasked="
                        + FcmDebugLogger.mask(installationId));

        if (!isCurrentUser(expectedUid)) {
            FcmDebugLogger.warning(
                    "FID_CALLBACK_SKIP",
                    "UID khong con la tai khoan hien tai; uidMasked="
                            + FcmDebugLogger.mask(expectedUid));
            return;
        }

        saveInstallationIfChanged(expectedUid, installationId);
    }

    /** Chi ghi Firestore neu FID tren server khac voi FID hien tai. */
    private void saveInstallationIfChanged(
            final String uid,
            final String installationId) {

        if (!isCurrentUser(uid)) {
            FcmDebugLogger.warning(
                    "FIRESTORE_SKIP",
                    "Tai khoan da thay doi truoc khi doc Firestore; uidMasked="
                            + FcmDebugLogger.mask(uid));
            return;
        }

        final DocumentReference installationDocument =
                firestore.collection(COLLECTION_INSTALLATIONS).document(uid);

        FcmDebugLogger.info(
                "FIRESTORE_READ_START",
                "Doc fcmInstallations/{uid} tu server; uidMasked="
                        + FcmDebugLogger.mask(uid));

        installationDocument.get(Source.SERVER)
                .addOnCompleteListener(
                        new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(
                                    @NonNull Task<DocumentSnapshot> readTask) {

                                if (!isCurrentUser(uid)) {
                                    FcmDebugLogger.warning(
                                            "FIRESTORE_SKIP",
                                            "Tai khoan da thay doi trong luc doc Firestore; uidMasked="
                                                    + FcmDebugLogger.mask(uid));
                                    return;
                                }

                                if (readTask.isSuccessful()) {
                                    DocumentSnapshot snapshot = readTask.getResult();
                                    boolean documentExists =
                                            snapshot != null && snapshot.exists();

                                    FcmDebugLogger.info(
                                            "FIRESTORE_READ_SUCCESS",
                                            "readServer thanh cong; documentExists="
                                                    + documentExists
                                                    + ", uidMasked="
                                                    + FcmDebugLogger.mask(uid));

                                    if (documentExists
                                            && installationId.equals(
                                                    snapshot.getString("fid"))) {
                                        FcmDebugLogger.info(
                                                "FIRESTORE_WRITE_SKIP",
                                                "FID khong doi; document da dung.");
                                        return;
                                    }

                                    if (documentExists) {
                                        FcmDebugLogger.info(
                                                "FIRESTORE_FID_CHANGED",
                                                "Document da ton tai nhung FID khac; se cap nhat.");
                                    } else {
                                        FcmDebugLogger.info(
                                                "FIRESTORE_DOCUMENT_MISSING",
                                                "Chua co document; se tao moi.");
                                    }
                                } else {
                                    FcmDebugLogger.error(
                                            "FIRESTORE_READ_FAIL",
                                            "Khong doc duoc document tu server; van thu ghi. "
                                                    + "Kiem tra loi READ va Security Rules.",
                                            readTask.getException());
                                }

                                Map<String, Object> installationData =
                                        new HashMap<String, Object>();
                                installationData.put("fid", installationId);
                                installationData.put(
                                        "updatedAt",
                                        FieldValue.serverTimestamp());

                                FcmDebugLogger.info(
                                        "FIRESTORE_WRITE_START",
                                        "Bat dau ghi fcmInstallations; uidMasked="
                                                + FcmDebugLogger.mask(uid)
                                                + ", fidMasked="
                                                + FcmDebugLogger.mask(installationId));

                                installationDocument.set(installationData)
                                        .addOnCompleteListener(
                                                new OnCompleteListener<Void>() {
                                                    @Override
                                                    public void onComplete(
                                                            @NonNull Task<Void> writeTask) {
                                                        if (!writeTask.isSuccessful()) {
                                                            FcmDebugLogger.error(
                                                                    "FIRESTORE_WRITE_FAIL",
                                                                    "Ghi fcmInstallations that bai; "
                                                                            + "kiem tra Security Rules, "
                                                                            + "Firebase project va mang.",
                                                                    writeTask.getException());
                                                            return;
                                                        }

                                                        FcmDebugLogger.info(
                                                                "FIRESTORE_WRITE_SUCCESS",
                                                                "Da ghi fcmInstallations/{uid}; uidMasked="
                                                                        + FcmDebugLogger.mask(uid));
                                                    }
                                                });
                            }
                        });
    }

    /** Xoa lien ket cua tai khoan truoc khi Firebase Authentication dang xuat. */
    public static Task<Void> deleteInstallationForUser(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("UID khong duoc rong."));
        }

        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null || !uid.equals(currentUser.getUid())) {
            return Tasks.forException(
                    new IllegalStateException("Tai khoan da thay doi."));
        }

        FcmDebugLogger.info(
                "FIRESTORE_DELETE_START",
                "Xoa fcmInstallations/{uid}; uidMasked="
                        + FcmDebugLogger.mask(uid));

        Task<Void> deleteTask = FirebaseFirestore.getInstance()
                .collection(COLLECTION_INSTALLATIONS)
                .document(uid)
                .delete();

        deleteTask.addOnCompleteListener(
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!task.isSuccessful()) {
                            FcmDebugLogger.error(
                                    "FIRESTORE_DELETE_FAIL",
                                    "Khong xoa duoc lien ket FID khi dang xuat.",
                                    task.getException());
                            return;
                        }

                        FcmDebugLogger.info(
                                "FIRESTORE_DELETE_SUCCESS",
                                "Da xoa lien ket FID.");
                    }
                });

        return deleteTask;
    }

    private boolean isCurrentUser(String expectedUid) {
        FirebaseUser currentUser = auth.getCurrentUser();

        return currentUser != null
                && expectedUid != null
                && expectedUid.equals(currentUser.getUid());
    }
}
