package com.example.grabshare.repository.chat;

import com.example.grabshare.contract.RepositoryCallback;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Giao dịch chat luôn đọc lại yêu cầu/chuyến; không thay đổi trạng thái nghiệp vụ. */
public final class ChatRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public static boolean sameUser(String userId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.getUid().equals(userId);
    }

    public static boolean validId(String value) {
        return value != null && !value.trim().isEmpty() && !value.contains("/");
    }

    public static boolean participant(DocumentSnapshot request, String userId) {
        return request.exists() && (userId.equals(request.getString("driverId"))
                || userId.equals(request.getString("passengerId")));
    }

    /** Chỉ đúng yêu cầu đã nhận, đúng pointer và đúng chủ xe mới được gửi tin mới. */
    public static boolean canSend(DocumentSnapshot request, DocumentSnapshot trip, String requestId) {
        return request.exists() && trip.exists()
                && "ACCEPTED".equals(request.getString("status"))
                && "FULL".equals(trip.getString("status"))
                && Long.valueOf(0).equals(trip.getLong("availableSeats"))
                && requestId.equals(trip.getString("acceptedRequestId"))
                && trip.getId().equals(request.getString("tripId"))
                && request.getString("driverId") != null
                && request.getString("driverId").equals(trip.getString("driverId"));
    }

    public DocumentReference conversation(String requestId) {
        return firestore.collection("conversations").document(requestId);
    }

    /** Mở chat đầu tiên chỉ sau ACCEPTED; chat đã tồn tại vẫn đọc được sau khi kết thúc. */
    public void open(final String requestId, final String userId, final RepositoryCallback<Void> callback) {
        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws com.google.firebase.firestore.FirebaseFirestoreException {
                requireUser(userId);
                DocumentSnapshot request = transaction.get(firestore.collection("requests").document(requestId));
                if (!participant(request, userId)) {
                    throw new IllegalStateException("Bạn không thuộc yêu cầu này.");
                }
                String tripId = request.getString("tripId");
                if (!validId(tripId)) {
                    throw new IllegalStateException("Yêu cầu thiếu chuyến liên quan.");
                }
                DocumentSnapshot trip = transaction.get(firestore.collection("trips").document(tripId));
                DocumentReference reference = conversation(requestId);
                DocumentSnapshot existing = transaction.get(reference);
                if (existing.exists()) {
                    if (!requestId.equals(existing.getString("requestId"))
                            || !tripId.equals(existing.getString("tripId"))) {
                        throw new IllegalStateException("Dữ liệu trò chuyện không nhất quán.");
                    }
                    return null;
                }
                if (!canSend(request, trip, requestId)) {
                    throw new IllegalStateException("Chat chỉ mở sau khi chủ xe nhận yêu cầu và chuyến còn hoạt động.");
                }
                Map<String, Object> data = new HashMap<String, Object>();
                data.put("requestId", requestId);
                data.put("tripId", tripId);
                data.put("driverId", request.getString("driverId"));
                data.put("passengerId", request.getString("passengerId"));
                data.put("pickupName", request.getString("pickup.name"));
                String dropoffName = request.getString("dropoff.name");
                if (!request.contains("dropoff")) {
                    dropoffName = trip.getString("destination.name");
                }
                data.put("dropoffName", dropoffName);
                data.put("departureAt", trip.getTimestamp("departureAt"));
                data.put("participantIds", Arrays.asList(request.getString("driverId"), request.getString("passengerId")));
                data.put("createdAt", FieldValue.serverTimestamp());
                data.put("lastSentAt", FieldValue.serverTimestamp());
                data.put("lastMessageId", "");
                data.put("lastText", "");
                data.put("lastSenderId", "");
                data.put("driverReadAt", null);
                data.put("passengerReadAt", null);
                requireUser(userId);
                transaction.set(reference, data);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (sameUser(userId)) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception error) {
                if (sameUser(userId)) {
                    callback.onError(error);
                }
            }
        });
    }

    /** Một UUID cố định cho mỗi lần gửi; thử lại sau mất phản hồi không tạo thêm bản sao. */
    public void send(final String requestId, final String userId, final String messageId,
            final String content, final RepositoryCallback<Void> callback) {
        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws com.google.firebase.firestore.FirebaseFirestoreException {
                requireUser(userId);
                if (content.trim().isEmpty() || content.length() > 1000) {
                    throw new IllegalStateException("Tin nhắn cần từ 1 đến 1.000 ký tự.");
                }
                DocumentReference conversation = conversation(requestId);
                DocumentReference message = conversation.collection("messages").document(messageId);
                DocumentSnapshot request = transaction.get(firestore.collection("requests").document(requestId));
                DocumentSnapshot summary = transaction.get(conversation);
                DocumentSnapshot existing = transaction.get(message);
                if (!participant(request, userId) || !summary.exists()) {
                    throw new IllegalStateException("Bạn không có quyền gửi trong cuộc trò chuyện này.");
                }
                // Xác nhận lần gửi trước ngay cả khi chuyến vừa kết thúc sau lúc gửi thành công.
                if (existing.exists()) {
                    if (userId.equals(existing.getString("senderId")) && content.equals(existing.getString("text"))) {
                        return null;
                    }
                    throw new IllegalStateException("Mã tin nhắn đã được sử dụng.");
                }
                String tripId = request.getString("tripId");
                if (!validId(tripId)) {
                    throw new IllegalStateException("Không tìm thấy chuyến liên quan.");
                }
                DocumentSnapshot trip = transaction.get(firestore.collection("trips").document(tripId));
                if (!canSend(request, trip, requestId)) {
                    throw new IllegalStateException("Chuyến đã kết thúc hoặc bị hủy. Lịch sử chat chỉ còn để xem.");
                }
                Map<String, Object> data = new HashMap<String, Object>();
                data.put("senderId", userId);
                data.put("text", content);
                data.put("createdAt", FieldValue.serverTimestamp());
                requireUser(userId);
                transaction.set(message, data);
                Map<String, Object> changes = new HashMap<String, Object>();
                changes.put("lastMessageId", messageId);
                changes.put("lastText", content);
                changes.put("lastSenderId", userId);
                changes.put("lastSentAt", FieldValue.serverTimestamp());
                transaction.update(conversation, changes);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (sameUser(userId)) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception error) {
                if (sameUser(userId)) {
                    callback.onError(error);
                }
            }
        });
    }

    /** Không đánh dấu tin mới đến sau khi màn hình đã dựng danh sách đang xem. */
    public void markRead(final String requestId, final String userId, final String visibleMessageId,
            final RepositoryCallback<Void> callback) {
        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws com.google.firebase.firestore.FirebaseFirestoreException {
                requireUser(userId);
                DocumentReference reference = conversation(requestId);
                DocumentSnapshot summary = transaction.get(reference);
                if (!summary.exists() || !visibleMessageId.equals(summary.getString("lastMessageId"))) {
                    return null;
                }
                String field = "passengerReadAt";
                if (userId.equals(summary.getString("driverId"))) {
                    field = "driverReadAt";
                } else if (!userId.equals(summary.getString("passengerId"))) {
                    return null;
                }
                if (summary.getTimestamp(field) != null && summary.getTimestamp("lastSentAt") != null
                        && summary.getTimestamp(field).compareTo(summary.getTimestamp("lastSentAt")) >= 0) {
                    return null;
                }
                requireUser(userId);
                transaction.update(reference, field, FieldValue.serverTimestamp());
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (sameUser(userId)) {
                    callback.onSuccess(null);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception error) {
                if (sameUser(userId)) {
                    callback.onError(error);
                }
            }
        });
    }

    private static void requireUser(String userId) {
        if (!sameUser(userId)) {
            throw new IllegalStateException("Phiên đăng nhập đã thay đổi.");
        }
    }

    public static boolean unread(DocumentSnapshot summary, String userId) {
        if (userId.equals(summary.getString("lastSenderId"))
                || "".equals(summary.getString("lastMessageId"))) {
            return false;
        }
        String field = "passengerReadAt";
        if (userId.equals(summary.getString("driverId"))) {
            field = "driverReadAt";
        }
        if (summary.getTimestamp("lastSentAt") == null) {
            return false;
        }
        return summary.getTimestamp(field) == null
                || summary.getTimestamp(field).compareTo(summary.getTimestamp("lastSentAt")) < 0;
    }
}
