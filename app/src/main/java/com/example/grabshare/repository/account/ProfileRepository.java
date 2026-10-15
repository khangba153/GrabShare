package com.example.grabshare.repository.account;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Quản lý hai hồ sơ riêng tư và công khai của tài khoản. */
public class ProfileRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    /** Sử dụng Auth và Firestore của project dùng chung. */
    public ProfileRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    /** Kiểm tra phiên và dữ liệu trước khi tạo hồ sơ. */
    private FirebaseUser validateProfileInput(
            String displayName,
            String vehicleName,
            String vehiclePlate) {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            throw new IllegalStateException("Bạn cần đăng nhập trước.");
        }

        String email = user.getEmail();

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

        return user;
    }

    /** Tạo các hồ sơ còn thiếu, giữ nguyên document đã tồn tại. */
    public void createMissingProfiles(
            final String displayName,
            final String vehicleName,
            final String vehiclePlate,
            final RepositoryCallback<Void> callback) {

        final FirebaseUser user;

        try {
            user = validateProfileInput(
                    displayName, vehicleName, vehiclePlate);
        } catch (Exception error) {
            callback.onError(error);
            return;
        }

        final String uid = user.getUid();
        final String email = user.getEmail();
        final String cleanName = displayName.trim();
        final String cleanVehicle = vehicleName.trim();
        final String cleanPlate = vehiclePlate.trim();

        final DocumentReference userRef =
                db.collection("users").document(uid);
        final DocumentReference publicRef =
                db.collection("publicProfiles").document(uid);

        Tasks.<DocumentSnapshot>whenAllSuccess(
                        userRef.get(Source.SERVER),
                        publicRef.get(Source.SERVER))
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

                                FirebaseUser currentUser = auth.getCurrentUser();

                                if (currentUser == null
                                        || !uid.equals(currentUser.getUid())) {
                                    callback.onError(new IllegalStateException(
                                            "Phiên đăng nhập đã thay đổi."));
                                    return;
                                }

                                List<DocumentSnapshot> documents = task.getResult();
                                boolean hasUser = documents.get(0).exists();
                                boolean hasPublic = documents.get(1).exists();

                                if (hasUser && hasPublic) {
                                    callback.onSuccess(null);
                                    return;
                                }

                                WriteBatch batch = db.batch();

                                if (!hasUser) {
                                    Map<String, Object> privateData = new HashMap<>();
                                    privateData.put("email", email);
                                    privateData.put("vehiclePlate", cleanPlate);
                                    privateData.put(
                                            "createdAt", FieldValue.serverTimestamp());
                                    privateData.put(
                                            "updatedAt", FieldValue.serverTimestamp());

                                    batch.set(userRef, privateData);
                                }

                                if (!hasPublic) {
                                    Map<String, Object> publicData = new HashMap<>();
                                    publicData.put("displayName", cleanName);
                                    publicData.put("vehicleName", cleanVehicle);
                                    publicData.put(
                                            "createdAt", FieldValue.serverTimestamp());
                                    publicData.put(
                                            "updatedAt", FieldValue.serverTimestamp());

                                    batch.set(publicRef, publicData);
                                }

                                batch.commit().addOnCompleteListener(
                                        new OnCompleteListener<Void>() {

                                            /** Trả kết quả ghi sau khi server xác nhận. */
                                            @Override
                                            public void onComplete(Task<Void> writeTask) {
                                                FirebaseUser activeUser = auth.getCurrentUser();

                                                if (activeUser == null
                                                        || !uid.equals(activeUser.getUid())) {
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
}