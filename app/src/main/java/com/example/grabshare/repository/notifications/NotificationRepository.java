package com.example.grabshare.repository.notifications;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.AppNotification;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Đọc Inbox chính chủ; không tạo notification và không xử lý event nghiệp vụ. */
public class NotificationRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final Map<String, String> actorNameCache = new HashMap<>();
    private final Map<String, String> tripSummaryCache = new HashMap<>();
    private long latestSnapshotVersion;

    /** Dùng cùng FirebaseApp với phần đăng nhập và dữ liệu của ứng dụng. */
    public NotificationRepository(FirebaseFirestore firestore, FirebaseAuth auth) {
        this.firestore = firestore;
        this.auth = auth;
    }

    /**
     * Lắng nghe notification của đúng UID, mới nhất trước. Snapshot có pending write
     * được bỏ qua để giao diện không báo đã đọc trước khi máy chủ xác nhận.
     */
    public ListenerRegistration listenInbox(
            final String expectedUid,
            final RepositoryCallback<List<AppNotification>> callback
    ) {
        if (!isExpectedUserSignedIn(expectedUid)) {
            callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            return null;
        }

        return firestore.collection("notifications")
                .whereEqualTo("recipientId", expectedUid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!isExpectedUserSignedIn(expectedUid)) {
                        callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                        return;
                    }
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot == null || snapshot.getMetadata().hasPendingWrites()) {
                        return;
                    }

                    final long version = ++latestSnapshotVersion;
                    List<AppNotification> items = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        try {
                            AppNotification item = document.toObject(AppNotification.class);
                            if (item == null || !expectedUid.equals(item.getRecipientId())) {
                                continue;
                            }
                            item.setId(document.getId());
                            items.add(item);
                        } catch (RuntimeException ignored) {
                            // Bỏ qua document sai schema; không làm hỏng toàn bộ Inbox.
                        }
                    }
                    enrichAndDeliver(expectedUid, version, items, callback);
                });
    }

    /** Đọc thêm tên hồ sơ công khai và tóm tắt tuyến; thiếu dữ liệu không chặn Inbox. */
    private void enrichAndDeliver(
            final String expectedUid,
            final long version,
            final List<AppNotification> items,
            final RepositoryCallback<List<AppNotification>> callback
    ) {
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        List<MetadataRequest> requests = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            AppNotification item = items.get(i);
            String actorId = item.getActorId();
            if (isValidDocumentId(actorId)) {
                if (actorNameCache.containsKey(actorId)) {
                    item.setActorDisplayName(actorNameCache.get(actorId));
                } else {
                    tasks.add(firestore.collection("publicProfiles").document(actorId).get(Source.SERVER));
                    requests.add(new MetadataRequest(i, actorId, true));
                }
            } else {
                item.setActorDisplayName("Người liên quan");
            }

            String tripId = item.getTripId();
            if (isValidDocumentId(tripId)) {
                if (tripSummaryCache.containsKey(tripId)) {
                    item.setTripSummary(tripSummaryCache.get(tripId));
                } else {
                    tasks.add(firestore.collection("trips").document(tripId).get(Source.SERVER));
                    requests.add(new MetadataRequest(i, tripId, false));
                }
            } else {
                item.setTripSummary("Không có thông tin chuyến");
            }
        }

        if (tasks.isEmpty()) {
            deliverIfCurrent(expectedUid, version, items, callback);
            return;
        }

        Tasks.whenAllComplete(tasks).addOnSuccessListener(completedTasks -> {
            for (int i = 0; i < completedTasks.size(); i++) {
                Task<DocumentSnapshot> task = tasks.get(i);
                MetadataRequest request = requests.get(i);
                AppNotification item = items.get(request.itemIndex);
                String fallback = request.profile ? "Người liên quan" : "Chuyến không còn tồn tại";

                if (!task.isSuccessful() || task.getResult() == null) {
                    if (request.profile) {
                        item.setActorDisplayName(fallback);
                    } else {
                        item.setTripSummary(fallback);
                    }
                    continue;
                }

                DocumentSnapshot document = task.getResult();
                String displayValue = request.profile
                        ? readActorName(document)
                        : readTripSummary(document);
                if (request.profile) {
                    actorNameCache.put(request.documentId, displayValue);
                    item.setActorDisplayName(displayValue);
                } else {
                    tripSummaryCache.put(request.documentId, displayValue);
                    item.setTripSummary(displayValue);
                }
            }
            deliverIfCurrent(expectedUid, version, items, callback);
        });
    }

    private String readActorName(DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return "Người liên quan";
        }
        String name = document.getString("displayName");
        return name == null || name.trim().isEmpty() ? "Người liên quan" : name.trim();
    }

    private String readTripSummary(DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return "Chuyến không còn tồn tại";
        }
        try {
            Trip trip = document.toObject(Trip.class);
            Place origin = trip == null ? null : trip.getOrigin();
            Place destination = trip == null ? null : trip.getDestination();
            String start = origin == null ? null : origin.getName();
            String end = destination == null ? null : destination.getName();
            if (start != null && !start.trim().isEmpty()
                    && end != null && !end.trim().isEmpty()) {
                return start.trim() + " → " + end.trim();
            }
            return "Chuyến " + document.getId();
        } catch (RuntimeException ignored) {
            return "Chuyến " + document.getId();
        }
    }

    /** Chỉ trả snapshot mới nhất và không giao dữ liệu cho phiên đăng nhập khác. */
    private void deliverIfCurrent(
            String expectedUid,
            long version,
            List<AppNotification> items,
            RepositoryCallback<List<AppNotification>> callback
    ) {
        if (!isExpectedUserSignedIn(expectedUid)) {
            callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            return;
        }
        if (version == latestSnapshotVersion) {
            callback.onSuccess(items);
        }
    }

    /**
     * Đọc bản notification từ server trước khi ghi. Chỉ isRead được cập nhật,
     * còn snapshot có pending write bị lọc tại listener cho tới khi được xác nhận.
     */
    public void markAsRead(
            final String expectedUid,
            final AppNotification notification,
            final RepositoryCallback<Void> callback
    ) {
        if (!isExpectedUserSignedIn(expectedUid)) {
            callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            return;
        }
        if (notification == null || !expectedUid.equals(notification.getRecipientId())
                || !isValidDocumentId(notification.getId())) {
            callback.onError(new IllegalArgumentException("Thông báo không hợp lệ."));
            return;
        }
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            callback.onSuccess(null);
            return;
        }

        final DocumentReference reference = firestore.collection("notifications")
                .document(notification.getId());
        reference.get(Source.SERVER).addOnCompleteListener(readTask -> {
            if (!isExpectedUserSignedIn(expectedUid)) {
                callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                return;
            }
            if (!readTask.isSuccessful() || readTask.getResult() == null) {
                callback.onError(readTask.getException() != null
                        ? readTask.getException()
                        : new IllegalStateException("Không thể xác minh thông báo trên máy chủ."));
                return;
            }

            DocumentSnapshot document = readTask.getResult();
            if (!document.exists()
                    || !expectedUid.equals(document.getString("recipientId"))) {
                callback.onError(new IllegalStateException("Thông báo không còn tồn tại."));
                return;
            }
            if (Boolean.TRUE.equals(document.getBoolean("isRead"))) {
                callback.onSuccess(null);
                return;
            }

            reference.update("isRead", true).addOnCompleteListener(writeTask -> {
                if (!isExpectedUserSignedIn(expectedUid)) {
                    callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                    return;
                }
                if (writeTask.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(writeTask.getException() != null
                            ? writeTask.getException()
                            : new IllegalStateException("Không thể đánh dấu đã đọc."));
                }
            });
        });
    }

    /**
     * Xác minh request/trip từ server và kiểm tra vai trò người nhận trước điều hướng.
     * Không đọc users/{uid} hoặc hồ sơ riêng của actor.
     */
    public void validateRelatedData(
            final String expectedUid,
            final AppNotification notification,
            final RepositoryCallback<Void> callback
    ) {
        if (!isExpectedUserSignedIn(expectedUid)) {
            callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
            return;
        }
        if (notification == null || !expectedUid.equals(notification.getRecipientId())
                || !isValidDocumentId(notification.getRequestId())
                || !isValidDocumentId(notification.getTripId())
                || !isKnownType(notification.getType())) {
            callback.onError(new IllegalArgumentException("Thông báo không có dữ liệu điều hướng hợp lệ."));
            return;
        }

        firestore.collection("requests").document(notification.getRequestId())
                .get(Source.SERVER)
                .addOnCompleteListener(requestTask -> {
                    if (!isExpectedUserSignedIn(expectedUid)) {
                        callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                        return;
                    }
                    if (!requestTask.isSuccessful() || requestTask.getResult() == null) {
                        callback.onError(requestTask.getException() != null
                                ? requestTask.getException()
                                : new IllegalStateException("Không thể tải yêu cầu liên quan."));
                        return;
                    }

                    DocumentSnapshot request = requestTask.getResult();
                    if (!request.exists()) {
                        callback.onError(new IllegalStateException("Yêu cầu không còn tồn tại."));
                        return;
                    }
                    String requestTripId = request.getString("tripId");
                    String passengerId = request.getString("passengerId");
                    String driverId = request.getString("driverId");
                    boolean driverRecipient = isDriverNotification(notification.getType());
                    String expectedActor = driverRecipient ? passengerId : driverId;
                    String expectedRecipient = driverRecipient ? driverId : passengerId;

                    if (!notification.getTripId().equals(requestTripId)
                            || !expectedUid.equals(expectedRecipient)
                            || expectedActor == null
                            || !expectedActor.equals(notification.getActorId())) {
                        callback.onError(new IllegalStateException("Dữ liệu yêu cầu không khớp thông báo."));
                        return;
                    }

                    firestore.collection("trips").document(notification.getTripId())
                            .get(Source.SERVER)
                            .addOnCompleteListener(tripTask -> {
                                if (!isExpectedUserSignedIn(expectedUid)) {
                                    callback.onError(new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                                    return;
                                }
                                if (!tripTask.isSuccessful() || tripTask.getResult() == null) {
                                    callback.onError(tripTask.getException() != null
                                            ? tripTask.getException()
                                            : new IllegalStateException("Không thể tải chuyến liên quan."));
                                    return;
                                }
                                DocumentSnapshot trip = tripTask.getResult();
                                if (!trip.exists()) {
                                    callback.onError(new IllegalStateException("Chuyến đi không còn tồn tại."));
                                    return;
                                }
                                if (!driverId.equals(trip.getString("driverId"))) {
                                    callback.onError(new IllegalStateException("Chuyến đi không khớp yêu cầu."));
                                    return;
                                }
                                callback.onSuccess(null);
                            });
                });
    }

    private boolean isExpectedUserSignedIn(String expectedUid) {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && expectedUid != null && expectedUid.equals(user.getUid());
    }

    private boolean isKnownType(String type) {
        return "REQUEST_SENT".equals(type)
                || "REQUEST_RELEASED".equals(type)
                || "REQUEST_CANCELLED".equals(type)
                || "REQUEST_ACCEPTED".equals(type)
                || "REQUEST_REJECTED".equals(type)
                || "TRIP_CANCELLED".equals(type)
                || "TRIP_COMPLETED".equals(type);
    }

    private boolean isDriverNotification(String type) {
        return "REQUEST_SENT".equals(type) || "REQUEST_CANCELLED".equals(type);
    }

    private boolean isValidDocumentId(String id) {
        return id != null && !id.trim().isEmpty() && !id.contains("/");
    }

    private static final class MetadataRequest {
        final int itemIndex;
        final String documentId;
        final boolean profile;

        MetadataRequest(int itemIndex, String documentId, boolean profile) {
            this.itemIndex = itemIndex;
            this.documentId = documentId;
            this.profile = profile;
        }
    }
}
