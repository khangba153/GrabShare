package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.FavoriteStore;
import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.TaskExecutors;
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
 * Triển khai FavoriteStore quản lý danh sách chuyến đi yêu thích trên Firestore.
 * Đảm bảo sử dụng serverTimestamp và giao dịch Firestore Transaction nguyên tử để chặn queue offline.
 */
public class FavoriteRepository implements FavoriteStore {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    /**
     * Khởi tạo FavoriteRepository với FirebaseFirestore instance từ caller.
     * @param firestore Đối tượng kết nối Firestore dùng chung.
     */
    public FavoriteRepository(FirebaseFirestore firestore) {
        if (firestore == null) {
            throw new IllegalArgumentException("Firestore không được null.");
        }
        this.firestore = firestore;
        this.auth = FirebaseAuth.getInstance(firestore.getApp());
    }

    /**
     * Kiểm tra một chuyến đi có nằm trong danh sách yêu thích của người dùng hay không.
     * Document ID có quy tắc deterministic: userId_tripId.
     * @param userId ID người dùng.
     * @param tripId ID chuyến đi.
     * @param resultCallback Nhận kết quả Boolean từ server trên UI Thread.
     */
    @Override
    public void isFavorite(final String userId, final String tripId, final RepositoryCallback<Boolean> resultCallback) {
        final RepositoryCallback<Boolean> callback = uiCallback(userId, resultCallback);
        if (!validate(userId, tripId, callback)) return;

        String favoriteDocId = userId + "_" + tripId;
        firestore.collection("favorites")
                .document(favoriteDocId)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    /** Chỉ trả trạng thái đã xác nhận trên server, không dùng ghi đang chờ. */
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (callback != null) {
                            if (documentSnapshot == null) {
                                callback.onError(new IllegalStateException("Không đọc được yêu thích trên máy chủ."));
                                return;
                            }
                            if (documentSnapshot.getMetadata().isFromCache()
                                    || documentSnapshot.getMetadata().hasPendingWrites()) {
                                callback.onError(new IllegalStateException("Chưa xác nhận yêu thích trên máy chủ."));
                            } else {
                                callback.onSuccess(documentSnapshot.exists());
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
     * Thêm chuyến đi vào danh sách yêu thích của người dùng.
     * Sử dụng Transaction để yêu cầu kết nối server, dùng FieldValue.serverTimestamp() và chặn queue offline.
     * Idempotent: Nếu đã tồn tại thì không reset createdAt của favorite đã có.
     * @param userId ID người dùng.
     * @param tripId ID chuyến đi.
     * @param resultCallback Nhận kết quả Void trên UI Thread.
     */
    @Override
    public void addFavorite(final String userId, final String tripId, final RepositoryCallback<Void> resultCallback) {
        final RepositoryCallback<Void> callback = uiCallback(userId, resultCallback);
        if (!validate(userId, tripId, callback)) return;

        final String favoriteDocId = userId + "_" + tripId;
        final DocumentReference favoriteRef = firestore.collection("favorites").document(favoriteDocId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Kiểm tra phiên ở mỗi lần thử và đọc trước mọi lệnh ghi. */
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                requireOwner(userId);
                DocumentSnapshot snapshot = transaction.get(favoriteRef);
                requireOwner(userId);
                requireMatchingFavorite(snapshot, userId, tripId);
                // Một lần yêu thích mới chỉ dành cho hành khách thực sự hoàn thành chuyến.
                DocumentSnapshot trip = transaction.get(firestore.collection("trips").document(tripId));
                String acceptedRequestId = trip.getString("acceptedRequestId");
                if (!trip.exists() || !"COMPLETED".equals(trip.getString("status"))
                        || !Long.valueOf(0).equals(trip.getLong("availableSeats"))
                        || trip.getTimestamp("departureAt") == null
                        || trip.getTimestamp("departureAt").compareTo(com.google.firebase.Timestamp.now()) > 0
                        || userId.equals(trip.getString("driverId"))
                        || acceptedRequestId == null || acceptedRequestId.isEmpty()
                        || acceptedRequestId.contains("/")) {
                    throw new FirebaseFirestoreException("Chỉ có thể yêu thích chuyến đã tham gia và hoàn thành.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                DocumentSnapshot request = transaction.get(
                        firestore.collection("requests").document(acceptedRequestId));
                if (!request.exists() || !userId.equals(request.getString("passengerId"))
                        || !tripId.equals(request.getString("tripId"))
                        || !"ACCEPTED".equals(request.getString("status"))
                        || trip.getString("driverId") == null
                        || !trip.getString("driverId").equals(request.getString("driverId"))) {
                    throw new FirebaseFirestoreException("Bạn không phải hành khách được nhận trên chuyến này.",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED);
                }
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
     * @param resultCallback Nhận kết quả Void trên UI Thread.
     */
    @Override
    public void removeFavorite(final String userId, final String tripId, final RepositoryCallback<Void> resultCallback) {
        final RepositoryCallback<Void> callback = uiCallback(userId, resultCallback);
        if (!validate(userId, tripId, callback)) return;

        final String favoriteDocId = userId + "_" + tripId;
        final DocumentReference favoriteRef = firestore.collection("favorites").document(favoriteDocId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            /** Xóa chính chủ bằng transaction để không xếp hàng ghi offline. */
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                requireOwner(userId);
                DocumentSnapshot snapshot = transaction.get(favoriteRef);
                requireOwner(userId);
                requireMatchingFavorite(snapshot, userId, tripId);
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

    /** Từ chối ID rỗng, đường dẫn lồng và UID không khớp trước khi gọi Firestore. */
    private boolean validate(String userId, String tripId, RepositoryCallback<?> callback) {
        if (!validId(userId) || !validId(tripId)) {
            callback.onError(new IllegalArgumentException("userId hoặc tripId không hợp lệ."));
            return false;
        }
        try {
            requireOwner(userId);
            return true;
        } catch (FirebaseFirestoreException error) {
            callback.onError(error);
            return false;
        }
    }

    /** Không tự trim ID thành một document khác hoặc chấp nhận đường dẫn nhiều cấp. */
    private boolean validId(String id) {
        return id != null && !id.trim().isEmpty() && id.equals(id.trim())
                && !id.contains("/") && !".".equals(id) && !"..".equals(id);
    }

    /** Dùng Auth thuộc cùng FirebaseApp; Rules server vẫn là lớp kiểm tra quyền cuối. */
    private void requireOwner(String userId) throws FirebaseFirestoreException {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !user.getUid().equals(userId)) {
            throw new FirebaseFirestoreException("Phiên đăng nhập không khớp chủ sở hữu yêu thích.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
        }
    }

    /** Không coi document có nội dung sai chủ hoặc sai chuyến là thao tác idempotent hợp lệ. */
    private void requireMatchingFavorite(DocumentSnapshot snapshot, String userId, String tripId)
            throws FirebaseFirestoreException {
        if (snapshot.exists() && (!userId.equals(snapshot.getString("userId"))
                || !tripId.equals(snapshot.getString("tripId")))) {
            throw new FirebaseFirestoreException("Yêu thích không khớp người dùng hoặc chuyến.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
        }
    }

    /** Đưa mọi kết quả, kể cả lỗi đầu vào từ worker, về UI thread mà không dùng Handler. */
    private <T> RepositoryCallback<T> uiCallback(final String userId, final RepositoryCallback<T> callback) {
        return new RepositoryCallback<T>() {
            /** Kiểm tra lại UID ngay trước khi giao kết quả cho caller. */
            @Override
            public void onSuccess(final T data) {
                TaskExecutors.MAIN_THREAD.execute(new Runnable() {
                    /** Không giao thành công của tài khoản cũ cho tài khoản mới. */
                    @Override
                    public void run() {
                        if (callback == null) return;
                        try {
                            requireOwner(userId);
                        } catch (FirebaseFirestoreException error) {
                            callback.onError(error);
                            return;
                        }
                        callback.onSuccess(data);
                    }
                });
            }

            /** Giữ nguyên lỗi mạng/quyền, không đổi lỗi thành trạng thái chưa yêu thích. */
            @Override
            public void onError(final Exception error) {
                TaskExecutors.MAIN_THREAD.execute(new Runnable() {
                    /** Trả lỗi trên UI thread cho caller xử lý theo lifecycle. */
                    @Override
                    public void run() {
                        if (callback != null) callback.onError(error);
                    }
                });
            }
        };
    }
}
