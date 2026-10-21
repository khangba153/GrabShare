package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Rating;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository xử lý nghiệp vụ tạo và đọc đánh giá chuyến đi (Rating) của Hành khách (Thuận-6).
 */
public class RatingRepository {

    public static final int MAX_COMMENT_LENGTH = 500;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    /** Dùng phiên Auth thuộc cùng FirebaseApp với Firestore được bàn giao. */
    public RatingRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
        auth = FirebaseAuth.getInstance(firestore.getApp());
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

        if (!isCurrentAuthor(authorId)) {
            if (callback != null) {
                callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            }
            return;
        }

        String ratingDocId = tripId + "_" + authorId;
        firestore.collection("ratings")
                .document(ratingDocId)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    /** Chỉ phân biệt đã có/chưa có sau xác nhận server của đúng UID. */
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (callback != null) {
                            if (!isCurrentAuthor(authorId)) {
                                callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                                return;
                            }
                            if (documentSnapshot.getMetadata().isFromCache()
                                    || documentSnapshot.getMetadata().hasPendingWrites()) {
                                callback.onError(new IllegalStateException("Chưa xác nhận được đánh giá trên máy chủ."));
                                return;
                            }
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
                    /** Không chuyển lỗi đọc thành kết quả chưa đánh giá. */
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

        // Kiểm tra chuỗi gốc trước trim để 501 ký tự luôn bị từ chối trước ghi.
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Nhận xét tối đa 500 ký tự."));
            }
            return;
        }
        if (!isCurrentAuthor(authorId)) {
            if (callback != null) {
                callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            }
            return;
        }
        String normalizedComment = "";
        if (comment != null) {
            normalizedComment = comment.trim();
        }
        final String savedComment = normalizedComment;

        final String ratingDocId = tripId + "_" + authorId;
        final DocumentReference ratingRef = firestore.collection("ratings").document(ratingDocId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Đọc rating trước ghi; Rules kiểm tra COMPLETED, người được nhận và đích đánh giá. */
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                requireCurrentAuthor(authorId);
                DocumentSnapshot snapshot = transaction.get(ratingRef);
                requireCurrentAuthor(authorId);
                if (snapshot.exists()) {
                    throw new FirebaseFirestoreException("Bạn đã đánh giá chuyến đi này rồi.", FirebaseFirestoreException.Code.ALREADY_EXISTS);
                }

                Map<String, Object> ratingData = new HashMap<>();
                ratingData.put("tripId", tripId);
                ratingData.put("authorId", authorId);
                ratingData.put("targetUserId", targetUserId);
                ratingData.put("score", score);
                ratingData.put("comment", savedComment);
                ratingData.put("createdAt", FieldValue.serverTimestamp());

                transaction.set(ratingRef, ratingData);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            /** Không báo thành công cho phiên khác sau khi transaction hoàn tất. */
            @Override
            public void onSuccess(Void unused) {
                if (callback != null) {
                    if (isCurrentAuthor(authorId)) {
                        callback.onSuccess(null);
                    } else {
                        callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            /** Giữ nguyên lỗi gửi để form cho phép xác minh lại trước khi thử tiếp. */
            @Override
            public void onFailure(Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    /** So sánh tác giả kỳ vọng với UID thật, không tin UID do Activity truyền vào. */
    private boolean isCurrentAuthor(String authorId) {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.getUid().equals(authorId);
    }

    /** Kiểm tra lại phiên mỗi lần transaction tự thử lại mà không tạo side effect UI. */
    private void requireCurrentAuthor(String authorId) throws FirebaseFirestoreException {
        if (!isCurrentAuthor(authorId)) {
            throw new FirebaseFirestoreException("Phiên đăng nhập đã thay đổi.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
        }
    }
}
