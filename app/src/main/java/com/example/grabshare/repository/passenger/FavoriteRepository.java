package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.FavoriteStore;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Favorite;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;

/**
 * Triển khai FavoriteStore quản lý danh sách chuyến đi yêu thích trên Firestore.
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
     * Tạo document với ID = userId_tripId để đảm bảo idempotent.
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

        String favoriteDocId = userId + "_" + tripId;
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTripId(tripId);
        favorite.setCreatedAt(new Timestamp(new Date()));

        firestore.collection("favorites")
                .document(favoriteDocId)
                .set(favorite)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        if (callback != null) {
                            callback.onSuccess(null);
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
     * Xóa chuyến đi khỏi danh sách yêu thích của người dùng.
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

        String favoriteDocId = userId + "_" + tripId;
        firestore.collection("favorites")
                .document(favoriteDocId)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        if (callback != null) {
                            callback.onSuccess(null);
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
}