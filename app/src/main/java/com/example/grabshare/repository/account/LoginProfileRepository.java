package com.example.grabshare.repository.account;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.List;

/** Đọc và kiểm tra sự tồn tại của hồ sơ trong luồng đăng nhập. */
public class LoginProfileRepository {

    private final FirebaseFirestore db;

    /** Khởi tạo kết nối Firestore của dự án dùng chung. */
    public LoginProfileRepository() {
        db = FirebaseFirestore.getInstance();
    }

    /** Kiểm tra hai document hồ sơ của UID vừa đăng nhập. */
    public void checkProfile(
            String uid,
            final RepositoryCallback<Boolean> callback) {

        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("UID không hợp lệ."));
            return;
        }

        Task<DocumentSnapshot> userTask = db.collection("users")
                .document(uid)
                .get(Source.SERVER);

        Task<DocumentSnapshot> publicTask = db.collection("publicProfiles")
                .document(uid)
                .get(Source.SERVER);

        Tasks.<DocumentSnapshot>whenAllSuccess(userTask, publicTask)
                .addOnCompleteListener(
                        new OnCompleteListener<List<DocumentSnapshot>>() {

                            /** Trả kết quả khi cả hai lần đọc hoàn tất. */
                            @Override
                            public void onComplete(
                                    Task<List<DocumentSnapshot>> task) {

                                if (!task.isSuccessful()) {
                                    Exception error = task.getException();

                                    if (error == null) {
                                        error = new IllegalStateException(
                                                "Không đọc được hồ sơ.");
                                    }

                                    callback.onError(error);
                                    return;
                                }

                                List<DocumentSnapshot> documents = task.getResult();

                                boolean hasBothProfiles =
                                        documents.get(0).exists()
                                                && documents.get(1).exists();

                                callback.onSuccess(hasBothProfiles);
                            }
                        });
    }
}