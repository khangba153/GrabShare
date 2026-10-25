package com.example.grabshare.presentation;

import android.app.Activity;
import android.view.View;
import android.widget.Button;

import com.example.grabshare.R;
import com.example.grabshare.activity.chat.ChatActivity;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.repository.chat.ChatRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

/** Lối vào chat dùng chung ở chi tiết yêu cầu và lịch sử; không tạo chat khi chỉ xem màn. */
final class ChatEntryController {
    private final Activity activity;
    private final Button button;
    private final String requestId;
    private final String userId;
    private ListenerRegistration requestListener;
    private ListenerRegistration conversationListener;
    private ListenerRegistration tripListener;
    private FirebaseAuth.AuthStateListener authListener;
    private boolean active;
    private boolean accepted;
    private boolean history;
    private boolean authorized;
    private boolean tripActive;

    private ChatEntryController(Activity activity, Button button, String requestId, String userId) {
        this.activity = activity;
        this.button = button;
        this.requestId = requestId;
        this.userId = userId;
    }

    static ChatEntryController create(Activity activity) {
        Button button = activity.findViewById(R.id.chat_open);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String requestId = activity.getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        if (button == null || user == null || !ChatRepository.validId(requestId)) {
            return null;
        }
        return new ChatEntryController(activity, button, requestId, user.getUid());
    }

    void start() {
        active = true;
        button.setVisibility(View.GONE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (active && authorized && ChatRepository.sameUser(userId)) {
                    ChatActivity.open(activity, requestId);
                }
            }
        });
        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(FirebaseAuth authentication) {
                if (!ChatRepository.sameUser(userId)) {
                    stop();
                }
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        requestListener = FirebaseFirestore.getInstance().collection("requests").document(requestId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot request, FirebaseFirestoreException error) {
                        if (!active || !ChatRepository.sameUser(userId)) {
                            return;
                        }
                        authorized = error == null && request != null && ChatRepository.participant(request, userId);
                        accepted = authorized && "ACCEPTED".equals(request.getString("status"));
                        if (authorized && tripListener == null && ChatRepository.validId(request.getString("tripId"))) {
                            listenTrip(request.getString("tripId"));
                        }
                        render();
                    }
                });
        conversationListener = FirebaseFirestore.getInstance().collection("conversations").document(requestId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot conversation, FirebaseFirestoreException error) {
                        if (!active || !ChatRepository.sameUser(userId)) {
                            return;
                        }
                        history = error == null && conversation != null && conversation.exists();
                        render();
                    }
                });
    }

    private void render() {
        button.setVisibility(View.GONE);
        if (authorized && ((accepted && tripActive) || history)) {
            button.setVisibility(View.VISIBLE);
            button.setText("Xem trò chuyện");
            if (accepted && tripActive) {
                button.setText("Nhắn tin");
            }
        }
    }

    private void listenTrip(String tripId) {
        tripListener = FirebaseFirestore.getInstance().collection("trips").document(tripId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot trip, FirebaseFirestoreException error) {
                        if (!active || !ChatRepository.sameUser(userId)) {
                            return;
                        }
                        tripActive = error == null && trip != null && trip.exists()
                                && "FULL".equals(trip.getString("status"))
                                && requestId.equals(trip.getString("acceptedRequestId"));
                        render();
                    }
                });
    }

    void stop() {
        active = false;
        button.setVisibility(View.GONE);
        if (requestListener != null) {
            requestListener.remove();
            requestListener = null;
        }
        if (conversationListener != null) {
            conversationListener.remove();
            conversationListener = null;
        }
        if (tripListener != null) {
            tripListener.remove();
            tripListener = null;
        }
        if (authListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener);
            authListener = null;
        }
    }
}
