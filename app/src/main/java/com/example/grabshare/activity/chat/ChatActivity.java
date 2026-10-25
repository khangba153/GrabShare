package com.example.grabshare.activity.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.repository.chat.ChatRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** Chat đúng một lần yêu cầu; trạng thái chuyến quyết định quyền gửi ở cả client và Rules. */
public class ChatActivity extends AppCompatActivity {
    private static volatile String visibleRequestId;
    private static volatile String visibleUserId;
    private ChatViewModel model;
    private EditText input;
    private ListView list;
    private MessageAdapter adapter;
    private boolean rendering;

    /** Push không bật banner lặp lại khi người nhận đang đọc chính cuộc trò chuyện đó. */
    public static boolean isViewing(String userId, String requestId) {
        return userId != null && userId.equals(visibleUserId)
                && requestId != null && requestId.equals(visibleRequestId);
    }

    public static void open(Context source, String requestId) {
        if (ChatRepository.validId(requestId)) {
            Intent intent = new Intent(source, ChatActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
            source.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_chat);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        if (user == null || !ChatRepository.validId(requestId)
                || (savedInstanceState != null && !user.getUid().equals(savedInstanceState.getString("chat_uid")))) {
            finish();
            return;
        }
        model = new ViewModelProvider(this).get(ChatViewModel.class);
        boolean newModel = model.requestId == null;
        model.initialize(requestId, user.getUid());
        if (newModel && savedInstanceState != null) {
            model.draft = savedInstanceState.getString("chat_draft", "");
            model.pendingId = savedInstanceState.getString("chat_pending_id", "");
            model.pendingText = savedInstanceState.getString("chat_pending_text", "");
            model.failed = !model.pendingId.isEmpty();
        }
        input = findViewById(R.id.chat_input);
        list = findViewById(R.id.chat_message_list);
        adapter = new MessageAdapter();
        list.setAdapter(adapter);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                model.setLatestVisible(totalCount > 0 && firstVisible + visibleCount >= totalCount);
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence content, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence content, int start, int before, int count) {
                if (!rendering) {
                    model.draft = content.toString();
                }
            }

            @Override
            public void afterTextChanged(Editable content) {
            }
        });
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.send_message).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                model.sendDraft();
                if (!model.pendingId.isEmpty()) {
                    input.clearFocus();
                    android.view.inputmethod.InputMethodManager keyboard =
                            getSystemService(android.view.inputmethod.InputMethodManager.class);
                    if (keyboard != null) {
                        keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    }
                }
            }
        });
        findViewById(R.id.chat_retry_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                model.retrySend();
            }
        });
        findViewById(R.id.chat_discard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new androidx.appcompat.app.AlertDialog.Builder(ChatActivity.this)
                        .setTitle("Bỏ trạng thái chờ gửi?")
                        .setMessage("Nếu máy chủ đã nhận tin, tin đó vẫn còn trong lịch sử. Thao tác này chỉ bỏ trạng thái chờ trên máy.")
                        .setNegativeButton("Giữ lại", null)
                        .setPositiveButton("Bỏ trạng thái chờ", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                model.discardFailed();
                            }
                        }).show();
            }
        });
        findViewById(R.id.chat_retry_connection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                model.retryConnection();
            }
        });
        findViewById(R.id.chat_load_older).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                model.loadOlder();
            }
        });
        model.changes.observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer revision) {
                render();
            }
        });
    }

    private void render() {
        if (model.sessionEnded) {
            input.setText("");
            finish();
            return;
        }
        rendering = true;
        if (!model.draft.equals(input.getText().toString())) {
            input.setText(model.draft);
            input.setSelection(input.length());
        }
        rendering = false;
        ((TextView) findViewById(R.id.header_title)).setText(model.title);
        ((TextView) findViewById(R.id.chat_context)).setText(model.context);
        findViewById(R.id.chat_context).setVisibility(View.VISIBLE);
        if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            findViewById(R.id.chat_context).setVisibility(View.GONE);
        }
        ((TextView) findViewById(R.id.chat_status)).setText(model.status);
        ((TextView) findViewById(R.id.chat_cache_hint)).setText("Lịch sử đã lưu trên thiết bị · đang chờ kết nối");
        findViewById(R.id.chat_cache_hint).setVisibility(View.GONE);
        if (model.cached) {
            findViewById(R.id.chat_cache_hint).setVisibility(View.VISIBLE);
        }
        input.setEnabled(model.writable && model.pendingId.isEmpty());
        findViewById(R.id.send_message).setEnabled(model.writable && model.pendingId.isEmpty());
        findViewById(R.id.chat_retry_send).setVisibility(View.GONE);
        findViewById(R.id.chat_discard).setVisibility(View.GONE);
        TextView pending = findViewById(R.id.chat_pending);
        pending.setVisibility(View.GONE);
        if (!model.pendingId.isEmpty()) {
            pending.setVisibility(View.VISIBLE);
            String label = "Đang gửi…";
            if (model.failed) {
                label = "Chưa gửi được. Bấm Thử gửi lại để kiểm tra và gửi cùng tin này.";
                findViewById(R.id.chat_retry_send).setVisibility(View.VISIBLE);
                findViewById(R.id.chat_discard).setVisibility(View.VISIBLE);
            }
            pending.setText(model.pendingText + "\n" + label);
        }
        findViewById(R.id.chat_retry_connection).setVisibility(View.GONE);
        if (!model.ready && !model.opening) {
            findViewById(R.id.chat_retry_connection).setVisibility(View.VISIBLE);
        }
        Button older = findViewById(R.id.chat_load_older);
        older.setVisibility(View.GONE);
        if (model.hasOlder) {
            older.setVisibility(View.VISIBLE);
        }
        older.setEnabled(!model.loadingOlder && model.ready);
        older.setText("Tải tin cũ hơn");
        if (model.loadingOlder) {
            older.setText("Đang tải…");
        }
        TextView empty = findViewById(R.id.chat_empty);
        empty.setVisibility(View.GONE);
        if (model.ready && model.messages.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (model != null) {
            model.start();
        }
    }

    @Override
    protected void onStop() {
        if (model != null) {
            model.stop();
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (model != null) {
            visibleRequestId = model.requestId;
            visibleUserId = model.userId;
            model.setViewing(true);
        }
    }

    @Override
    protected void onPause() {
        if (model != null) {
            model.setViewing(false);
            if (isViewing(model.userId, model.requestId)) {
                visibleRequestId = null;
                visibleUserId = null;
            }
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (model != null) {
            state.putString("chat_uid", model.userId);
            state.putString("chat_draft", model.draft);
            state.putString("chat_pending_id", model.pendingId);
            state.putString("chat_pending_text", model.pendingText);
        }
        super.onSaveInstanceState(state);
    }

    private final class MessageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return model.messages.size();
        }

        @Override
        public DocumentSnapshot getItem(int position) {
            return model.messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            View row = recycled;
            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.row_chat_message, parent, false);
            }
            DocumentSnapshot message = getItem(position);
            boolean mine = model.userId.equals(message.getString("senderId"));
            LinearLayout container = (LinearLayout) row;
            TextView body = row.findViewById(R.id.chat_message_body);
            TextView time = row.findViewById(R.id.chat_message_time);
            container.setGravity(Gravity.START);
            body.setBackgroundResource(R.drawable.bg_bubble);
            body.setTextColor(getColor(R.color.ink));
            if (mine) {
                container.setGravity(Gravity.END);
                body.setBackgroundResource(R.drawable.bg_button);
                body.setTextColor(getColor(R.color.white));
            }
            body.setText(message.getString("text"));
            Timestamp sentAt = message.getTimestamp("createdAt");
            time.setText("");
            if (sentAt != null) {
                time.setText(new SimpleDateFormat("HH:mm · dd/MM", new Locale("vi", "VN")).format(sentAt.toDate()));
                if (mine) {
                    String delivery = " · Đã gửi";
                    if (model.otherReadAt != null && model.otherReadAt.compareTo(sentAt) >= 0) {
                        delivery = " · Đã đọc";
                    }
                    time.append(delivery);
                }
            }
            return row;
        }
    }
}
