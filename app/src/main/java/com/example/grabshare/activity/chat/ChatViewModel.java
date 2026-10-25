package com.example.grabshare.activity.chat;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.chat.ChatRepository;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Giữ tác vụ và bản nháp qua xoay màn hình; không tự gửi lại khi mở ứng dụng. */
public final class ChatViewModel extends ViewModel {
    public final MutableLiveData<Integer> changes = new MutableLiveData<Integer>(0);
    public final List<DocumentSnapshot> messages = new ArrayList<DocumentSnapshot>();
    public String requestId;
    public String userId;
    public String title = "Trò chuyện";
    public String context = "";
    public String status = "Đang mở cuộc trò chuyện…";
    public String draft = "";
    public String pendingId = "";
    public String pendingText = "";
    public boolean sending;
    public boolean failed;
    public boolean ready;
    public boolean writable;
    public boolean sessionEnded;
    public boolean opening;
    public boolean loadingOlder;
    public boolean hasOlder;
    public boolean cached;
    public Timestamp otherReadAt;
    private boolean visible;
    private boolean latestVisible;
    private boolean viewing;
    private int generation;
    private int sendGeneration;
    private DocumentSnapshot request;
    private DocumentSnapshot trip;
    private DocumentSnapshot summary;
    private DocumentSnapshot cursor;
    private boolean requestFromServer;
    private boolean tripFromServer;
    private boolean tripAttached;
    private String readMessageId = "";
    private final Map<String, DocumentSnapshot> loaded = new LinkedHashMap<String, DocumentSnapshot>();
    private final List<ListenerRegistration> listeners = new ArrayList<ListenerRegistration>();
    private final ChatRepository repository = new ChatRepository();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private FirebaseAuth.AuthStateListener authListener;

    public void initialize(String requestId, String userId) {
        if (this.requestId != null) {
            return;
        }
        this.requestId = requestId;
        this.userId = userId;
        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(FirebaseAuth authentication) {
                if (!ChatRepository.sameUser(ChatViewModel.this.userId)) {
                    stop();
                    sendGeneration++;
                    messages.clear();
                    loaded.clear();
                    draft = "";
                    pendingText = "";
                    pendingId = "";
                    sessionEnded = true;
                    publish();
                }
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }

    public void start() {
        visible = true;
        if (!listeners.isEmpty() || opening || sessionEnded) {
            return;
        }
        if (!ChatRepository.sameUser(userId)) {
            sessionEnded = true;
            publish();
            return;
        }
        opening = true;
        ready = false;
        status = "Đang mở cuộc trò chuyện…";
        final int token = ++generation;
        publish();
        repository.open(requestId, userId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (!current(token)) {
                    return;
                }
                opening = false;
                ready = true;
                attach(token);
                publish();
            }

            @Override
            public void onError(Exception error) {
                if (!current(token)) {
                    return;
                }
                opening = false;
                writable = false;
                ready = false;
                status = explain(error);
                if (error instanceof FirebaseFirestoreException
                        && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    openCached(token);
                }
                publish();
            }
        });
    }

    /** Listener gắn với phiên màn đang hiển thị; callback cũ không được ghi đè phiên mới. */
    private void attach(final int token) {
        requestFromServer = false;
        tripFromServer = false;
        listeners.add(firestore.collection("requests").document(requestId)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot document, FirebaseFirestoreException error) {
                        if (!current(token)) {
                            return;
                        }
                        if (error != null || document == null || !ChatRepository.participant(document, userId)) {
                            unavailable(error);
                            return;
                        }
                        request = document;
                        requestFromServer = !document.getMetadata().isFromCache()
                                && !document.getMetadata().hasPendingWrites();
                        updatePolicy();
                    }
                }));
        listeners.add(repository.conversation(requestId)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot document, FirebaseFirestoreException error) {
                        if (!current(token)) {
                            return;
                        }
                        if (error != null || document == null || !document.exists()) {
                            unavailable(error);
                            return;
                        }
                        summary = document;
                        context = com.example.grabshare.presentation.ChatSummaryText.format(document);
                        String readField = "driverReadAt";
                        if (userId.equals(summary.getString("driverId"))) {
                            readField = "passengerReadAt";
                        }
                        otherReadAt = summary.getTimestamp(readField);
                        String tripId = summary.getString("tripId");
                        if (!tripAttached && ChatRepository.validId(tripId)) {
                            attachTrip(tripId, token);
                        }
                        publish();
                    }
                }));
        listeners.add(messageQuery().limit(50).addSnapshotListener(MetadataChanges.INCLUDE,
                new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(QuerySnapshot snapshot, FirebaseFirestoreException error) {
                        if (!current(token)) {
                            return;
                        }
                        if (error != null || snapshot == null) {
                            unavailable(error);
                            return;
                        }
                        cached = snapshot.getMetadata().isFromCache();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            loaded.put(document.getId(), document);
                        }
                        if (cursor == null && !snapshot.isEmpty()) {
                            cursor = snapshot.getDocuments().get(snapshot.size() - 1);
                        }
                        if (snapshot.size() == 50) {
                            hasOlder = true;
                        }
                        rebuild();
                        acknowledgePending();
                        publish();
                    }
                }));
    }

    private void attachTrip(String tripId, final int token) {
        tripAttached = true;
        listeners.add(firestore.collection("trips").document(tripId)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot document, FirebaseFirestoreException error) {
                        if (!current(token)) {
                            return;
                        }
                        if (error != null || document == null) {
                            unavailable(error);
                            return;
                        }
                        trip = document;
                        tripFromServer = !document.getMetadata().isFromCache()
                                && !document.getMetadata().hasPendingWrites();
                        updatePolicy();
                    }
                }));
        String otherId = summary.getString("driverId");
        title = "Chủ xe";
        if (userId.equals(otherId)) {
            otherId = summary.getString("passengerId");
            title = "Hành khách";
        }
        if (ChatRepository.validId(otherId)) {
            firestore.collection("publicProfiles").document(otherId).get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot profile) {
                            if (current(token) && profile.getString("displayName") != null) {
                                title = profile.getString("displayName");
                                publish();
                            }
                        }
                    });
        }
    }

    /** Ngoại tuyến chỉ mở lịch sử đã có cho đúng UID, không tạo hội thoại hoặc xếp hàng gửi. */
    private void openCached(final int token) {
        repository.conversation(requestId).get(Source.CACHE)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot document) {
                        if (!current(token) || !document.exists()
                                || !(userId.equals(document.getString("driverId"))
                                || userId.equals(document.getString("passengerId")))) {
                            return;
                        }
                        ready = true;
                        cached = true;
                        attach(token);
                        publish();
                    }
                });
    }

    private Query messageQuery() {
        return repository.conversation(requestId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING);
    }

    public void loadOlder() {
        if (loadingOlder || !hasOlder || cursor == null || !ready) {
            return;
        }
        final int token = generation;
        loadingOlder = true;
        publish();
        messageQuery().startAfter(cursor).limit(50).get(Source.SERVER)
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(Task<QuerySnapshot> task) {
                        if (!current(token)) {
                            return;
                        }
                        loadingOlder = false;
                        if (task.isSuccessful() && task.getResult() != null) {
                            QuerySnapshot snapshot = task.getResult();
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                loaded.put(document.getId(), document);
                            }
                            hasOlder = snapshot.size() == 50;
                            if (!snapshot.isEmpty()) {
                                cursor = snapshot.getDocuments().get(snapshot.size() - 1);
                            }
                            rebuild();
                        } else {
                            status = "Chưa tải được tin cũ. Kiểm tra mạng rồi bấm tải lại.";
                        }
                        publish();
                    }
                });
    }

    private void rebuild() {
        messages.clear();
        messages.addAll(loaded.values());
        Collections.sort(messages, new Comparator<DocumentSnapshot>() {
            @Override
            public int compare(DocumentSnapshot first, DocumentSnapshot second) {
                if (first.getTimestamp("createdAt") != null && second.getTimestamp("createdAt") != null) {
                    int compared = first.getTimestamp("createdAt").compareTo(second.getTimestamp("createdAt"));
                    if (compared != 0) {
                        return compared;
                    }
                }
                return first.getId().compareTo(second.getId());
            }
        });
    }

    private void updatePolicy() {
        writable = ready && request != null && trip != null && requestFromServer && tripFromServer
                && ChatRepository.participant(request, userId) && ChatRepository.canSend(request, trip, requestId);
        if (!ready) {
            publish();
            return;
        }
        status = "Trao đổi điểm đón và nhận diện xe tại đây.";
        if (!requestFromServer || !tripFromServer) {
            status = "Đang xác minh kết nối. Chỉ gửi khi xác nhận được chuyến từ máy chủ.";
        } else if (!writable) {
            status = "Chuyến đã kết thúc hoặc bị hủy. Bạn vẫn xem được lịch sử trò chuyện.";
        }
        publish();
    }

    /** Snapshot server là xác nhận gửi kể cả khi phản hồi transaction trước đó bị mất. */
    private void acknowledgePending() {
        DocumentSnapshot message = loaded.get(pendingId);
        if (message != null && !cached && !message.getMetadata().hasPendingWrites()
                && userId.equals(message.getString("senderId"))
                && pendingText.equals(message.getString("text"))) {
            sendGeneration++;
            sending = false;
            failed = false;
            pendingId = "";
            pendingText = "";
        }
    }

    public void sendDraft() {
        if (sending || !pendingId.isEmpty() || !writable) {
            return;
        }
        String content = draft.trim();
        if (content.isEmpty() || content.length() > 1000) {
            status = "Nhập từ 1 đến 1.000 ký tự trước khi gửi.";
            publish();
            return;
        }
        pendingId = UUID.randomUUID().toString().replace("-", "");
        pendingText = content;
        draft = "";
        retrySend();
    }

    /** Chỉ người dùng chủ động thử lại cùng UUID; không có hàng đợi tự gửi sau logout. */
    public void retrySend() {
        if (sending || pendingId.isEmpty() || !ChatRepository.sameUser(userId)) {
            return;
        }
        sending = true;
        failed = false;
        final int token = ++sendGeneration;
        publish();
        repository.send(requestId, userId, pendingId, pendingText, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (token != sendGeneration || sessionEnded) {
                    return;
                }
                sending = false;
                failed = false;
                pendingId = "";
                pendingText = "";
                publish();
            }

            @Override
            public void onError(Exception error) {
                if (token != sendGeneration || sessionEnded) {
                    return;
                }
                sending = false;
                failed = true;
                status = explain(error);
                publish();
            }
        });
    }

    /** Chỉ đánh dấu đã đọc khi đang thấy tin mới nhất đã xác nhận từ server. */
    private void markVisibleRead() {
        if (!visible || !viewing || !latestVisible || summary == null || summary.getMetadata().isFromCache() || cached) {
            return;
        }
        String latestId = summary.getString("lastMessageId");
        if (latestId != null && !latestId.isEmpty() && loaded.containsKey(latestId)
                && !latestId.equals(readMessageId) && ChatRepository.unread(summary, userId)) {
            readMessageId = latestId;
            final int token = generation;
            repository.markRead(requestId, userId, latestId, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void unused) {
                }

                @Override
                public void onError(Exception error) {
                    if (current(token) && latestId.equals(readMessageId)) {
                        readMessageId = "";
                    }
                }
            });
        }
    }

    private void unavailable(Exception error) {
        writable = false;
        status = explain(error);
        ready = false;
        publish();
    }

    public void retryConnection() {
        stop();
        start();
    }

    public void stop() {
        visible = false;
        latestVisible = false;
        generation++;
        opening = false;
        ready = false;
        writable = false;
        loadingOlder = false;
        readMessageId = "";
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        listeners.clear();
        request = null;
        trip = null;
        summary = null;
        tripAttached = false;
    }

    public void setLatestVisible(boolean latestVisible) {
        this.latestVisible = latestVisible;
        markVisibleRead();
    }

    public void setViewing(boolean viewing) {
        this.viewing = viewing;
        markVisibleRead();
    }

    public void discardFailed() {
        if (!sending && failed) {
            sendGeneration++;
            pendingId = "";
            pendingText = "";
            failed = false;
            publish();
        }
    }

    private boolean current(int token) {
        return visible && token == generation && !sessionEnded && ChatRepository.sameUser(userId);
    }

    private void publish() {
        Integer value = changes.getValue();
        if (value == null) {
            value = 0;
        }
        changes.setValue(value + 1);
    }

    public static String explain(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Không có quyền chat hoặc Firebase chưa cập nhật Rules chat.";
            }
            return "Chưa kết nối được máy chủ. Kiểm tra mạng rồi thử lại.";
        }
        if (error != null && error.getMessage() != null) {
            return error.getMessage();
        }
        return "Không thể tải cuộc trò chuyện. Vui lòng thử lại.";
    }

    @Override
    protected void onCleared() {
        sendGeneration++;
        stop();
        if (authListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        }
    }
}
