package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.FavoriteStore;
import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Triển khai FavoriteStore quản lý danh sách chuyến đi yêu thích trên Firestore.
 * Đảm bảo sử dụng serverTimestamp và giao dịch Firestore Transaction nguyên tử để chặn queue offline.
 */
public class FavoriteRepository implements FavoriteStore {

    private final FirebaseFirestore firestore;

    /**
     * Khởi tạo FavoriteRepository với FirebaseFirestore instance từ caller.
     * @param firestore Đối tượng kết nối Firestore dùng chung.
     */
    public FavoriteRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Kiểm tra một chuyến đi có nằm trong danh sách yêu thích của người dùng hay không.
     * Document ID có quy tắc deterministic: userId_tripId.
     * @param userId ID người dùng.
     * @param tripId ID chuyến đi.
     * @param callback Nhận kết quả Boolean trên UI Thread.
     */
    @Override
    public void isFavorite(final String userId, final String tripId, final RepositoryCallback<Boolean> callback) {
        if (userId == null || userId.trim().isEmpty() || tripId == null || tripId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("userId hoặc tripId không hợp lệ."));
            }
            return;
        }

        String favoriteDocId = userId + "_" + tripId;
        firestore.collection("favorites")
                .document(favoriteDocId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (callback != null) {
                            boolean exists = documentSnapshot != null && documentSnapshot.exists();
                            callback.onSuccess(exists);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        if (callback != null) {
                            callback.onError(e);
                        }
                    }
                });
    }

    /**
     * Thêm chuyến đi vào danh sách yêu thích của người dùng.
     * Sử dụng Transaction để yêu cầu kết nối server, dùng FieldValue.serverTimestamp() và chặn queue offline.
     * Idempotent: Nếu đã tồn tại thì không reset createdAt của favorite đã có.
     * @param userId ID người dùng.
     * @param tripId ID chuyến đi.
     * @param callback Nhận kết quả Void trên UI Thread.
     */
    @Override
    public void addFavorite(final String userId, final String tripId, final RepositoryCallback<Void> callback) {
        if (userId == null || userId.trim().isEmpty() || tripId == null || tripId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("userId hoặc tripId không hợp lệ."));
            }
            return;
        }

        final String favoriteDocId = userId + "_" + tripId;
        final DocumentReference favoriteRef = firestore.collection("favorites").document(favoriteDocId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(favoriteRef);
                // Nếu chưa tồn tại thì tạo mới với createdAt bằng FieldValue.serverTimestamp()
                if (!snapshot.exists()) {
                    Map<String, Object> favoriteData = new HashMap<>();
                    favoriteData.put("userId", userId);
                    favoriteData.put("tripId", tripId);
                    favoriteData.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(favoriteRef, favoriteData);
                }
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * Xóa chuyến đi khỏi danh sách yêu thích của người dùng.
     * Sử dụng Transaction để yêu cầu kết nối server và chặn queue offline.
     * @param userId ID người dùng.
     * @param tripId ID chuyến đi.
     * @param callback Nhận kết quả Void trên UI Thread.
     */
    @Override
    public void removeFavorite(final String userId, final String tripId, final RepositoryCallback<Void> callback) {
        if (userId == null || userId.trim().isEmpty() || tripId == null || tripId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("userId hoặc tripId không hợp lệ."));
            }
            return;
        }

        final String favoriteDocId = userId + "_" + tripId;
        final DocumentReference favoriteRef = firestore.collection("favorites").document(favoriteDocId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(favoriteRef);
                if (snapshot.exists()) {
                    transaction.delete(favoriteRef);
                }
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }
}
