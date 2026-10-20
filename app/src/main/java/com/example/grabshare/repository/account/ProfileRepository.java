package com.example.grabshare.repository.account;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Quản lý hai hồ sơ riêng tư và công khai của tài khoản. */
public class ProfileRepository {

    //region 1. Kết nối dùng chung

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    /** Sử dụng Auth và Firestore của project dùng chung. */
    public ProfileRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    //endregion

    //region 2. Dữ liệu hồ sơ của chính chủ

    /** Dữ liệu hai hồ sơ chính chủ; không đưa email hoặc biển số vào hồ sơ công khai. */
    public static final class AccountProfile {
        public final String userIdentifier;
        public final String email;
        public final String displayName;
        public final String vehicleName;
        public final String vehiclePlate;
        public final boolean privateProfileExists;
        public final boolean publicProfileExists;

        /** Ghép hai document đã đọc từ server, giữ rõ document còn thiếu. */
        AccountProfile(
                String userIdentifier,
                DocumentSnapshot privateProfileDocument,
                DocumentSnapshot publicProfileDocument) {
            this.userIdentifier = userIdentifier;
            privateProfileExists = privateProfileDocument.exists();
            publicProfileExists = publicProfileDocument.exists();
            if (privateProfileExists) {
                email = stringValue(privateProfileDocument, "email");
                vehiclePlate = stringValue(privateProfileDocument, "vehiclePlate");
            } else {
                email = "";
                vehiclePlate = "";
            }

            if (publicProfileExists) {
                displayName = stringValue(publicProfileDocument, "displayName");
                vehicleName = stringValue(publicProfileDocument, "vehicleName");
            } else {
                displayName = "";
                vehicleName = "";
            }
        }

        /** Đọc chuỗi đúng kiểu; dữ liệu sai kiểu được chuyển thành lỗi của lần tải. */
        private static String stringValue(DocumentSnapshot document, String key) {
            String value = document.getString(key);
            if (value == null) {
                return "";
            }

            return value;
        }

        /** Hai document phải cùng có để được xem là hồ sơ đầy đủ. */
        public boolean isComplete() {
            return privateProfileExists && publicProfileExists;
        }
    }

    //endregion

    //region 3. Tải và cập nhật hồ sơ

    /** Kiểm tra UID trước mỗi bước bất đồng bộ để không trả dữ liệu của phiên cũ. */
    private boolean isExpectedUserSignedIn(String expectedUserIdentifier) {
        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
        return signedInUser != null && expectedUserIdentifier != null
                && expectedUserIdentifier.equals(signedInUser.getUid());
    }

    /** Đọc hai hồ sơ chính chủ từ server; lỗi đọc không được hiểu là thiếu hồ sơ. */
    public void loadOwnProfile(
            final String expectedUserIdentifier,
            final RepositoryCallback<AccountProfile> callback) {
        if (!isExpectedUserSignedIn(expectedUserIdentifier)) {
            callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            return;
        }
        Tasks.<DocumentSnapshot>whenAllSuccess(
                firestore.collection("users").document(expectedUserIdentifier).get(Source.SERVER),
                firestore.collection("publicProfiles").document(expectedUserIdentifier).get(Source.SERVER))
                .addOnCompleteListener(new OnCompleteListener<List<DocumentSnapshot>>() {
                    /** Chỉ trả dữ liệu khi cả hai lần đọc thành công và UID vẫn đúng. */
                    @Override
                    public void onComplete(@NonNull Task<List<DocumentSnapshot>> task) {
                        if (!isExpectedUserSignedIn(expectedUserIdentifier)) {
                            callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                            return;
                        }
                        if (!task.isSuccessful()) {
                            Exception error = task.getException();
                            if (error == null) {
                                error = new IllegalStateException(
                                        "Không tải được hồ sơ. Hãy thử lại.");
                            }
                            callback.onError(error);
                            return;
                        }
                        final AccountProfile profile;
                        try {
                            List<DocumentSnapshot> profileDocuments = task.getResult();
                            profile = new AccountProfile(
                                    expectedUserIdentifier,
                                    profileDocuments.get(0),
                                    profileDocuments.get(1));
                        } catch (RuntimeException error) {
                            callback.onError(new IllegalStateException(
                                    "Dữ liệu hồ sơ không đúng định dạng.", error));
                            return;
                        }
                        callback.onSuccess(profile);
                    }
                });
    }

    /** Lưu hai hồ sơ nguyên tử; chỉ tạo document còn thiếu, giữ createdAt của bản đã có. */
    public void saveOwnProfile(
            final String expectedUserIdentifier,
            String displayName,
            String vehicleName,
            String vehiclePlate,
            final RepositoryCallback<Void> callback) {
        final FirebaseUser signedInUser;
        try {
            if (!isExpectedUserSignedIn(expectedUserIdentifier)) {
                throw new IllegalStateException("Phiên đăng nhập đã thay đổi.");
            }
            signedInUser = validateProfileInput(displayName, vehicleName, vehiclePlate);
        } catch (Exception error) {
            callback.onError(error);
            return;
        }
        final String cleanName = displayName.trim();
        final String cleanVehicle = vehicleName.trim();
        final String cleanPlate = vehiclePlate.trim();
        final String email = signedInUser.getEmail();
        final DocumentReference privateProfileReference = firestore.collection("users")
                .document(expectedUserIdentifier);
        final DocumentReference publicProfileReference = firestore.collection("publicProfiles")
                .document(expectedUserIdentifier);
        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Mọi đọc trước ghi; không thay giao diện trong callback có thể chạy lại. */
            @Override
            public Void apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                if (!isExpectedUserSignedIn(expectedUserIdentifier)) {
                    throw new FirebaseFirestoreException("Phiên đăng nhập đã thay đổi.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                DocumentSnapshot privateProfileDocument = transaction.get(privateProfileReference);
                DocumentSnapshot publicProfileDocument = transaction.get(publicProfileReference);
                Map<String, Object> privateData = new HashMap<>();
                privateData.put("vehiclePlate", cleanPlate);
                privateData.put("updatedAt", FieldValue.serverTimestamp());
                Map<String, Object> publicData = new HashMap<>();
                publicData.put("displayName", cleanName);
                publicData.put("vehicleName", cleanVehicle);
                publicData.put("updatedAt", FieldValue.serverTimestamp());
                if (privateProfileDocument.exists()) {
                    transaction.update(privateProfileReference, privateData);
                } else {
                    privateData.put("email", email);
                    privateData.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(privateProfileReference, privateData);
                }
                if (publicProfileDocument.exists()) {
                    transaction.update(publicProfileReference, publicData);
                } else {
                    publicData.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(publicProfileReference, publicData);
                }
                return null;
            }
        }).addOnCompleteListener(new OnCompleteListener<Void>() {
            /** Chỉ báo lưu xong sau server commit, không trả kết quả vào phiên khác. */
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (!isExpectedUserSignedIn(expectedUserIdentifier)) {
                    callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                } else if (task.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    Exception error = task.getException();
                    if (error == null) {
                        error = new IllegalStateException("Không lưu được hồ sơ. Hãy thử lại.");
                    }
                    callback.onError(error);
                }
            }
        });
    }

    //endregion

    //region 4. Kiểm tra đầu vào và tạo hồ sơ còn thiếu khi đăng ký

    /** Kiểm tra phiên và dữ liệu trước khi tạo hồ sơ. */
    private FirebaseUser validateProfileInput(
            String displayName,
            String vehicleName,
            String vehiclePlate) {

        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();

        if (signedInUser == null) {
            throw new IllegalStateException("Bạn cần đăng nhập trước.");
        }

        String email = signedInUser.getEmail();

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Tài khoản chưa có email hợp lệ.");
        }

        if (displayName == null
                || displayName.trim().isEmpty()
                || displayName.trim().length() > 80) {
            throw new IllegalArgumentException(
                    "Tên hiển thị phải có từ 1 đến 80 ký tự.");
        }

        if (vehicleName == null || vehicleName.trim().length() > 80) {
            throw new IllegalArgumentException(
                    "Tên xe tối đa 80 ký tự; có thể để trống.");
        }

        if (vehiclePlate == null || vehiclePlate.trim().length() > 20) {
            throw new IllegalArgumentException(
                    "Biển số tối đa 20 ký tự; có thể để trống.");
        }

        boolean hasVehicleName = !vehicleName.trim().isEmpty();
        boolean hasVehiclePlate = !vehiclePlate.trim().isEmpty();

        if (hasVehicleName != hasVehiclePlate) {
            throw new IllegalArgumentException(
                    "Hãy nhập cả tên xe và biển số, hoặc để trống cả hai.");
        }

        return signedInUser;
    }

    /** Tạo các hồ sơ còn thiếu, giữ nguyên document đã tồn tại. */
    public void createMissingProfiles(
            final String displayName,
            final String vehicleName,
            final String vehiclePlate,
            final RepositoryCallback<Void> callback) {

        final FirebaseUser signedInUser;

        try {
            signedInUser = validateProfileInput(
                    displayName, vehicleName, vehiclePlate);
        } catch (Exception error) {
            callback.onError(error);
            return;
        }

        final String userIdentifier = signedInUser.getUid();
        final String email = signedInUser.getEmail();
        final String cleanName = displayName.trim();
        final String cleanVehicle = vehicleName.trim();
        final String cleanPlate = vehiclePlate.trim();

        final DocumentReference privateProfileReference =
                firestore.collection("users").document(userIdentifier);
        final DocumentReference publicProfileReference =
                firestore.collection("publicProfiles").document(userIdentifier);

        Tasks.<DocumentSnapshot>whenAllSuccess(
                        privateProfileReference.get(Source.SERVER),
                        publicProfileReference.get(Source.SERVER))
                .addOnCompleteListener(
                        new OnCompleteListener<List<DocumentSnapshot>>() {

                            /** Chỉ chuẩn bị ghi sau khi đọc thành công cả hai hồ sơ. */
                            @Override
                            public void onComplete(
                                    Task<List<DocumentSnapshot>> task) {

                                if (!task.isSuccessful()) {
                                    Exception error = task.getException();

                                    if (error == null) {
                                        error = new IllegalStateException(
                                                "Không đọc được hồ sơ từ server.");
                                    }

                                    callback.onError(error);
                                    return;
                                }

                                FirebaseUser currentUser = firebaseAuth.getCurrentUser();

                                if (currentUser == null
                                        || !userIdentifier.equals(currentUser.getUid())) {
                                    callback.onError(new IllegalStateException(
                                            "Phiên đăng nhập đã thay đổi."));
                                    return;
                                }

                                List<DocumentSnapshot> profileDocuments = task.getResult();
                                boolean hasUser = profileDocuments.get(0).exists();
                                boolean hasPublic = profileDocuments.get(1).exists();

                                if (hasUser && hasPublic) {
                                    callback.onSuccess(null);
                                    return;
                                }

                                WriteBatch batch = firestore.batch();

                                if (!hasUser) {
                                    Map<String, Object> privateData = new HashMap<>();
                                    privateData.put("email", email);
                                    privateData.put("vehiclePlate", cleanPlate);
                                    privateData.put(
                                            "createdAt", FieldValue.serverTimestamp());
                                    privateData.put(
                                            "updatedAt", FieldValue.serverTimestamp());

                                    batch.set(privateProfileReference, privateData);
                                }

                                if (!hasPublic) {
                                    Map<String, Object> publicData = new HashMap<>();
                                    publicData.put("displayName", cleanName);
                                    publicData.put("vehicleName", cleanVehicle);
                                    publicData.put(
                                            "createdAt", FieldValue.serverTimestamp());
                                    publicData.put(
                                            "updatedAt", FieldValue.serverTimestamp());

                                    batch.set(publicProfileReference, publicData);
                                }

                                batch.commit().addOnCompleteListener(
                                        new OnCompleteListener<Void>() {

                                            /** Trả kết quả ghi sau khi server xác nhận. */
                                            @Override
                                            public void onComplete(Task<Void> writeTask) {
                                                FirebaseUser activeUser = firebaseAuth.getCurrentUser();

                                                if (activeUser == null
                                                        || !userIdentifier.equals(activeUser.getUid())) {
                                                    callback.onError(
                                                            new IllegalStateException(
                                                                    "Phiên đăng nhập đã thay đổi."));
                                                    return;
                                                }

                                                if (writeTask.isSuccessful()) {
                                                    callback.onSuccess(null);
                                                    return;
                                                }

                                                Exception error = writeTask.getException();

                                                if (error == null) {
                                                    error = new IllegalStateException(
                                                            "Không tạo được hồ sơ.");
                                                }

                                                callback.onError(error);
                                            }
                                        });
                            }
                        });
    }

    //endregion
}
