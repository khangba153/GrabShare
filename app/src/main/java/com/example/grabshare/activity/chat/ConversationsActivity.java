package com.example.grabshare.activity.chat;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.repository.chat.ChatRepository;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Danh sách chat theo UID; mở danh sách không tự đánh dấu tin đã đọc. */
public class ConversationsActivity extends AppCompatActivity {
    private final List<DocumentSnapshot> conversations = new ArrayList<DocumentSnapshot>();
    private final Map<String, String> names = new HashMap<String, String>();
    private final Set<String> requestedProfiles = new HashSet<String>();
    private String userId;
    private int generation;
    private boolean active;
    private ListenerRegistration registration;
    private FirebaseAuth.AuthStateListener authListener;
    private ConversationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_conversations);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || (savedInstanceState != null
                && !user.getUid().equals(savedInstanceState.getString("chat_inbox_uid")))) {
            finish();
            return;
        }
        userId = user.getUid();
        adapter = new ConversationAdapter();
        ListView list = findViewById(R.id.chat_conversations);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long identifier) {
                if (ChatRepository.sameUser(userId)) {
                    ChatActivity.open(ConversationsActivity.this, conversations.get(position).getId());
                }
            }
        });
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.chat_inbox_retry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listen();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        active = true;
        if (userId == null || !ChatRepository.sameUser(userId)) {
            finish();
            return;
        }
        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(FirebaseAuth authentication) {
                if (!ChatRepository.sameUser(userId)) {
                    stopListener();
                    conversations.clear();
                    names.clear();
                    adapter.notifyDataSetChanged();
                    finish();
                }
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        listen();
    }

    private void listen() {
        stopListener();
        final int token = generation;
        TextView status = findViewById(R.id.chat_inbox_status);
        status.setVisibility(View.VISIBLE);
        status.setText("Đang tải tin nhắn…");
        findViewById(R.id.chat_inbox_retry).setVisibility(View.GONE);
        registration = FirebaseFirestore.getInstance().collection("conversations")
                .whereArrayContains("participantIds", userId)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(QuerySnapshot snapshot, FirebaseFirestoreException error) {
                        if (!current(token)) {
                            return;
                        }
                        if (error != null || snapshot == null) {
                            status.setText(ChatViewModel.explain(error));
                            status.setVisibility(View.VISIBLE);
                            findViewById(R.id.chat_inbox_retry).setVisibility(View.VISIBLE);
                            return;
                        }
                        conversations.clear();
                        conversations.addAll(snapshot.getDocuments());
                        Collections.sort(conversations, new Comparator<DocumentSnapshot>() {
                            @Override
                            public int compare(DocumentSnapshot first, DocumentSnapshot second) {
                                if (first.getTimestamp("lastSentAt") != null && second.getTimestamp("lastSentAt") != null) {
                                    return second.getTimestamp("lastSentAt").compareTo(first.getTimestamp("lastSentAt"));
                                }
                                return first.getId().compareTo(second.getId());
                            }
                        });
                        int unread = 0;
                        for (DocumentSnapshot conversation : conversations) {
                            if (ChatRepository.unread(conversation, userId)) {
                                unread++;
                            }
                            loadProfile(conversation, token);
                        }
                        ((TextView) findViewById(R.id.chat_tab_messages)).setText("Chưa đọc: " + unread + " cuộc trò chuyện");
                        status.setVisibility(View.GONE);
                        if (conversations.isEmpty()) {
                            status.setText("Chưa có cuộc trò chuyện. Mở chi tiết chuyến đã được nhận rồi chọn Nhắn tin.");
                            status.setVisibility(View.VISIBLE);
                        }
                        if (snapshot.getMetadata().isFromCache()) {
                            status.setText("Đang hiển thị dữ liệu trên thiết bị. Chờ kết nối để cập nhật.");
                            status.setVisibility(View.VISIBLE);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private String otherId(DocumentSnapshot conversation) {
        if (userId.equals(conversation.getString("driverId"))) {
            return conversation.getString("passengerId");
        }
        return conversation.getString("driverId");
    }

    private void loadProfile(DocumentSnapshot conversation, final int token) {
        final String other = otherId(conversation);
        if (!ChatRepository.validId(other) || !requestedProfiles.add(other)) {
            return;
        }
        FirebaseFirestore.getInstance().collection("publicProfiles").document(other).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot profile) {
                        if (current(token) && profile.getString("displayName") != null) {
                            names.put(other, profile.getString("displayName"));
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    private boolean current(int token) {
        return active && token == generation && !isFinishing() && ChatRepository.sameUser(userId);
    }

    private void stopListener() {
        generation++;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        requestedProfiles.clear();
    }

    @Override
    protected void onStop() {
        active = false;
        stopListener();
        if (authListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener);
            authListener = null;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("chat_inbox_uid", userId);
        super.onSaveInstanceState(state);
    }

    private final class ConversationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return conversations.size();
        }

        @Override
        public DocumentSnapshot getItem(int position) {
            return conversations.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            View row = recycled;
            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.row_conversation, parent, false);
            }
            DocumentSnapshot conversation = getItem(position);
            String name = names.get(otherId(conversation));
            if (name == null || name.trim().isEmpty()) {
                name = "Chủ xe";
                if (userId.equals(conversation.getString("driverId"))) {
                    name = "Hành khách";
                }
            }
            boolean unread = ChatRepository.unread(conversation, userId);
            TextView title = row.findViewById(R.id.conversation_name);
            title.setText(name);
            ((TextView) row.findViewById(R.id.conversation_route)).setText(
                    com.example.grabshare.presentation.ChatSummaryText.format(conversation));
            title.setTextColor(getColor(R.color.ink));
            if (unread) {
                title.setText(name + " · Chưa đọc");
                title.setTextColor(getColor(R.color.primary));
            }
            String preview = conversation.getString("lastText");
            if (preview == null || preview.isEmpty()) {
                preview = "Chưa có tin nhắn · Bấm để trao đổi điểm đón";
            } else if (userId.equals(conversation.getString("lastSenderId"))) {
                preview = "Bạn: " + preview;
            }
            TextView body = row.findViewById(R.id.conversation_preview);
            body.setText(preview);
            body.setTypeface(null, Typeface.NORMAL);
            if (unread) {
                body.setTypeface(null, Typeface.BOLD);
            }
            TextView time = row.findViewById(R.id.conversation_time);
            time.setText("");
            if (conversation.getTimestamp("lastSentAt") != null) {
                time.setText(new SimpleDateFormat("HH:mm · dd/MM/yyyy", new Locale("vi", "VN"))
                        .format(conversation.getTimestamp("lastSentAt").toDate()));
            }
            return row;
        }
    }
}
