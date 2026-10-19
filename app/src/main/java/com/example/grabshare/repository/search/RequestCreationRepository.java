package com.example.grabshare.repository.search;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/** Tạo một yêu cầu và thông báo tương ứng, không thay đổi chỗ của chuyến. */
public class RequestCreationRepository {
    private final Context context;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    /** Kết quả được dùng để mở trạng thái hiện có sau xác nhận từ máy chủ. */
    public static class Result {
        public final String requestId;
        public final String status;
        public final boolean existing;

        /** Lưu ID và trạng thái, không truyền model qua Intent. */
        public Result(String requestId, String status, boolean existing) {
            this.requestId = requestId;
            this.status = status;
            this.existing = existing;
        }
    }

    /** Giữ applicationContext và dùng Firebase chung của ứng dụng. */
    public RequestCreationRepository(Context context) {
        this.context = context.getApplicationContext();
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /** Chặn gửi khi chưa có kết nối Internet được hệ thống xác nhận. */
    private boolean online() {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Đảm bảo callback và mỗi lượt transaction vẫn thuộc phiên đã khởi tạo. */
    private boolean sameUser(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    /** Chuyển dữ liệu request đã tồn tại thành kết quả, không cho gửi lại. */
    private Result existingResult(DocumentSnapshot snapshot, String tripId, String uid) {
        TripRequest request = snapshot.toObject(TripRequest.class);
        if (request == null || !tripId.equals(request.getTripId())
                || !uid.equals(request.getPassengerId())
                || !("PENDING".equals(request.getStatus()) || "ACCEPTED".equals(request.getStatus())
                || "REJECTED".equals(request.getStatus()) || "CANCELLED".equals(request.getStatus()))) {
            throw new IllegalStateException("Dữ liệu yêu cầu đã có không hợp lệ.");
        }
        return new Result(snapshot.getId(), request.getStatus(), true);
    }

    /** Đọc request đúng UID trên server để khôi phục sau process death hoặc lỗi mạng. */
    public void findExisting(final String tripId, final String uid,
                             final RepositoryCallback<Result> callback) {
        if (!SearchTripPolicy.validId(tripId) || !SearchTripPolicy.validId(uid) || !sameUser(uid)) {
            callback.onError(new IllegalStateException("Thiếu mã chuyến hoặc phiên đăng nhập đã đổi."));
            return;
        }
        firestore.collection("requests").document(tripId + "_" + uid).get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    /** Chỉ trả trạng thái của phiên hiện tại; null nghĩa là server xác nhận chưa có. */
                    @Override public void onSuccess(DocumentSnapshot snapshot) {
                        if (!sameUser(uid)) {
                            callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi."));
                            return;
                        }
                        final Result result;
                        try {
                            result = snapshot.exists() ? existingResult(snapshot, tripId, uid) : null;
                        } catch (RuntimeException error) {
                            callback.onError(error);
                            return;
                        }
                        callback.onSuccess(result);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    /** Không coi lỗi đọc là chưa có request. */
                    @Override public void onFailure(@NonNull Exception error) { callback.onError(error); }
                });
    }

    /** Đọc lại trip/request trước mọi ghi; callback chỉ thành công sau server commit. */
    public void create(final String tripId, final String uid, Place pickup, String inputMessage,
                       final RepositoryCallback<Result> callback) {
        if (!SearchTripPolicy.validId(tripId) || !SearchTripPolicy.validId(uid) || !sameUser(uid)) {
            callback.onError(new IllegalStateException("Thiếu mã chuyến hoặc phiên đăng nhập đã đổi."));
            return;
        }
        if (!SearchTripPolicy.validPlace(pickup)) {
            callback.onError(new IllegalArgumentException("Điểm đón phải có tên tối đa 200 ký tự và tọa độ hợp lệ."));
            return;
        }
        final String message = inputMessage == null ? "" : inputMessage.trim();
        if (message.length() > 500) {
            callback.onError(new IllegalArgumentException("Lời nhắn tối đa 500 ký tự."));
            return;
        }
        if (!online()) {
            callback.onError(new IllegalStateException("Cần kết nối Internet để gửi yêu cầu."));
            return;
        }
        // Sao chép form để người dùng sửa giao diện không làm đổi dữ liệu transaction đang chạy.
        final Map<String, Object> pickupData = new HashMap<>();
        pickupData.put("name", pickup.getName().trim());
        pickupData.put("latitude", pickup.getLatitude());
        pickupData.put("longitude", pickup.getLongitude());
        final String requestId = tripId + "_" + uid;
        final DocumentReference tripRef = firestore.collection("trips").document(tripId);
        final DocumentReference requestRef = firestore.collection("requests").document(requestId);
        final DocumentReference eventRef = firestore.collection("notifications")
                .document(requestId + "_REQUEST_SENT");

        firestore.runTransaction(new Transaction.Function<Result>() {
            /** Hàm có thể được Firestore chạy lại; không thay UI hoặc tạo ID ngẫu nhiên ở đây. */
            @Override public Result apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                if (!sameUser(uid) || !online()) {
                    throw failure("Phiên đã đổi hoặc mất mạng. Yêu cầu chưa được xác nhận.");
                }
                DocumentSnapshot tripSnapshot = transaction.get(tripRef);
                DocumentSnapshot requestSnapshot = transaction.get(requestRef);
                if (requestSnapshot.exists()) {
                    try {
                        return existingResult(requestSnapshot, tripId, uid);
                    } catch (RuntimeException error) {
                        throw failure("Yêu cầu đã tồn tại nhưng dữ liệu không hợp lệ.");
                    }
                }
                if (!tripSnapshot.exists()) throw failure("Chuyến không còn tồn tại.");
                Trip trip = tripSnapshot.toObject(Trip.class);
                if (trip != null) trip.setId(tripSnapshot.getId());
                String reason = SearchTripPolicy.blockedReason(trip, uid);
                if (!reason.isEmpty()) throw failure(reason);

                Map<String, Object> request = new HashMap<>();
                request.put("tripId", tripId);
                request.put("passengerId", uid);
                request.put("driverId", trip.getDriverId());
                request.put("pickup", pickupData);
                request.put("message", message);
                request.put("status", "PENDING");
                request.put("createdAt", FieldValue.serverTimestamp());
                request.put("updatedAt", FieldValue.serverTimestamp());
                Map<String, Object> event = new HashMap<>();
                event.put("recipientId", trip.getDriverId());
                event.put("actorId", uid);
                event.put("type", "REQUEST_SENT");
                event.put("tripId", tripId);
                event.put("requestId", requestId);
                event.put("isRead", false);
                event.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(requestRef, request);
                transaction.set(eventRef, event);
                return new Result(requestId, "PENDING", false);
            }
        }).addOnSuccessListener(new OnSuccessListener<Result>() {
            /** Đây là xác nhận server, không phải bản ghi pending trong cache. */
            @Override public void onSuccess(Result result) {
                if (sameUser(uid)) callback.onSuccess(result);
                else callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi. Kiểm tra lại ở tài khoản cũ."));
            }
        }).addOnFailureListener(new OnFailureListener() {
            /** Giữ lỗi quyền/mạng/state để caller hiển thị và giữ form. */
            @Override public void onFailure(@NonNull Exception error) { callback.onError(error); }
        });
    }

    /** Tạo lỗi nghiệp vụ dừng transaction trước khi có thao tác ghi. */
    private static FirebaseFirestoreException failure(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.FAILED_PRECONDITION);
    }
}
