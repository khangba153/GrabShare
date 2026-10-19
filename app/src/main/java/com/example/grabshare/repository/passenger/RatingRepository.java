package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Rating;
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
 * Repository xử lý nghiệp vụ tạo và đọc đánh giá chuyến đi (Rating) của Hành khách (Thuận-6).
 */
public class RatingRepository {

    private final FirebaseFirestore firestore;

    public RatingRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Kiểm tra xem Hành khách đã đánh giá chuyến đi này hay chưa.
     * Document ID có quy tắc: tripId_authorUid.
     * @param tripId ID chuyến đi.
     * @param authorId UID người đánh giá (hành khách).
     * @param callback Nhận đối tượng Rating nếu đã đánh giá, hoặc null nếu chưa.
     */
    public void getRating(final String tripId, final String authorId, final RepositoryCallback<Rating> callback) {
        if (tripId == null || tripId.trim().isEmpty() || authorId == null || authorId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("tripId hoặc authorId không hợp lệ."));
            }
            return;
        }

        String ratingDocId = tripId + "_" + authorId;
        firestore.collection("ratings")
                .document(ratingDocId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (callback != null) {
                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                Rating rating = documentSnapshot.toObject(Rating.class);
                                if (rating != null) {
                                    rating.setId(documentSnapshot.getId());
                                }
                                callback.onSuccess(rating);
                            } else {
                                callback.onSuccess(null);
                            }
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
     * Gửi đánh giá cho tài xế của chuyến đi đã COMPLETED.
     * Sử dụng Transaction để đảm bảo tính nguyên tử và kiểm tra điều kiện không tạo đánh giá trùng.
     * Document ID quy tắc: tripId_authorUid.
     * @param tripId ID chuyến đi.
     * @param authorId UID hành khách đánh giá.
     * @param targetUserId UID tài xế nhận đánh giá.
     * @param score Điểm số từ 1 đến 5.
     * @param comment Nhận xét tùy chọn.
     * @param callback Nhận kết quả Void trên UI Thread.
     */
    public void submitRating(final String tripId, final String authorId, final String targetUserId, final long score, final String comment, final RepositoryCallback<Void> callback) {
        if (tripId == null || tripId.trim().isEmpty() || authorId == null || authorId.trim().isEmpty() || targetUserId == null || targetUserId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Thông tin đánh giá không hợp lệ."));
            }
            return;
        }

        if (score < 1 || score > 5) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5 sao."));
            }
            return;
        }

        final String ratingDocId = tripId + "_" + authorId;
        final DocumentReference ratingRef = firestore.collection("ratings").document(ratingDocId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ratingRef);
                if (snapshot.exists()) {
                    throw new FirebaseFirestoreException("Bạn đã đánh giá chuyến đi này rồi.", FirebaseFirestoreException.Code.ALREADY_EXISTS);
                }

                Map<String, Object> ratingData = new HashMap<>();
                ratingData.put("tripId", tripId);
                ratingData.put("authorId", authorId);
                ratingData.put("targetUserId", targetUserId);
                ratingData.put("score", score);
                ratingData.put("comment", comment != null ? comment.trim() : "");
                ratingData.put("createdAt", FieldValue.serverTimestamp());

                transaction.set(ratingRef, ratingData);
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
