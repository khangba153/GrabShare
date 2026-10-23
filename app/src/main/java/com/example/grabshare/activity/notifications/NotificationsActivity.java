package com.example.grabshare.activity.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.activity.driver.DriverRequestsActivity;
import com.example.grabshare.activity.driver.RequestDetailActivity;
import com.example.grabshare.activity.passenger.CompleteActivity;
import com.example.grabshare.activity.passenger.RejectedActivity;
import com.example.grabshare.activity.passenger.UpcomingActivity;
import com.example.grabshare.adapter.notifications.NotificationAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.AppNotification;
import com.example.grabshare.repository.notifications.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Inbox cá nhân; không tạo event và không giả lập push khi ứng dụng đóng. */
public class NotificationsActivity extends AppCompatActivity {

    private static final String STATE_EXPECTED_UID = "notifications_expected_uid";

    private FirebaseAuth auth;
    private NotificationRepository repository;
    private NotificationAdapter adapter;
    private ListenerRegistration inboxListener;

    private ImageButton backButton;
    private ListView notificationList;
    private ProgressBar loadingIndicator;
    private TextView statusText;
    private Button retryButton;

    private String expectedUid;
    private boolean isActive;
    private boolean routeInProgress;
    private int actionToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_notifications);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem thông báo.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        expectedUid = currentUser.getUid();
        if (savedInstanceState != null
                && !expectedUid.equals(savedInstanceState.getString(STATE_EXPECTED_UID))) {
            finish();
            return;
        }

        repository = new NotificationRepository(FirebaseFirestore.getInstance(), auth);
        initializeViews();
        initializeList();
        connectActions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActive = true;
        if (!isExpectedUserSignedIn()) {
            finish();
            return;
        }
        startListening();
    }

    @Override
    protected void onStop() {
        isActive = false;
        actionToken++;
        routeInProgress = false;
        if (notificationList != null) {
            notificationList.setEnabled(true);
        }
        removeInboxListener();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_EXPECTED_UID, expectedUid);
        super.onSaveInstanceState(outState);
    }

    private void initializeViews() {
        backButton = findViewById(R.id.header_back);
        notificationList = findViewById(R.id.notification_list);
        loadingIndicator = findViewById(R.id.notification_loading);
        statusText = findViewById(R.id.notification_status);
        retryButton = findViewById(R.id.notification_retry);
    }

    private void initializeList() {
        adapter = new NotificationAdapter(this);
        notificationList.setAdapter(adapter);
        notificationList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < adapter.getCount()) {
                openNotification(adapter.getItem(position));
            }
        });
    }

    private void connectActions() {
        backButton.setOnClickListener(view -> finish());
        retryButton.setOnClickListener(view -> startListening());
    }

    /** Chỉ có một listener đang hoạt động cho mỗi Activity. */
    private void startListening() {
        if (!isActive || !isExpectedUserSignedIn()) {
            return;
        }
        removeInboxListener();
        showLoading();

        inboxListener = repository.listenInbox(expectedUid, new RepositoryCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> notifications) {
                if (!canUpdateUi()) {
                    return;
                }
                loadingIndicator.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                if (notifications == null || notifications.isEmpty()) {
                    notificationList.setVisibility(View.GONE);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText("Bạn chưa có thông báo.");
                    return;
                }

                statusText.setVisibility(View.GONE);
                notificationList.setVisibility(View.VISIBLE);
                notificationList.setEnabled(true);
                adapter.replaceItems(notifications);
            }

            @Override
            public void onError(Exception error) {
                if (!canUpdateUi()) {
                    return;
                }
                loadingIndicator.setVisibility(View.GONE);
                statusText.setVisibility(View.VISIBLE);
                statusText.setText(adapter.getCount() > 0
                        ? "Không thể cập nhật danh sách thông báo."
                        : "Không thể tải thông báo. Hãy kiểm tra kết nối.");
                notificationList.setVisibility(adapter.getCount() > 0 ? View.VISIBLE : View.GONE);
                retryButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showLoading() {
        loadingIndicator.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        if (adapter.getCount() == 0) {
            notificationList.setVisibility(View.GONE);
        }
    }

    private void removeInboxListener() {
        if (inboxListener != null) {
            inboxListener.remove();
            inboxListener = null;
        }
    }

    /**
     * Xác minh request/trip từ server trước. Khi đã hợp lệ, cập nhật isRead rồi mới
     * mở màn đích theo ID ổn định, tránh mở chi tiết trống khi document đã bị xóa.
     */
    private void openNotification(final AppNotification item) {
        if (routeInProgress || item == null || !isExpectedUserSignedIn()) {
            return;
        }
        routeInProgress = true;
        notificationList.setEnabled(false);
        final int token = ++actionToken;

        repository.validateRelatedData(expectedUid, item, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (!isCurrentAction(token)) {
                    return;
                }
                repository.markAsRead(expectedUid, item, new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (!isCurrentAction(token)) {
                            return;
                        }
                        item.setIsRead(true);
                        adapter.notifyDataSetChanged();
                        routeToRelatedScreen(item);
                    }

                    @Override
                    public void onError(Exception error) {
                        showActionError(token, true);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                showActionError(token, false);
            }
        });
    }

    private void routeToRelatedScreen(AppNotification item) {
        Intent intent;
        String type = item.getType();

        if ("REQUEST_SENT".equals(type)) {
            intent = new Intent(this, RequestDetailActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, item.getRequestId());
            intent.putExtra(NavigationKeys.TRIP_ID, item.getTripId());
        } else if ("REQUEST_CANCELLED".equals(type)) {
            intent = new Intent(this, DriverRequestsActivity.class);
            intent.putExtra(NavigationKeys.TRIP_ID, item.getTripId());
        } else if ("REQUEST_ACCEPTED".equals(type)) {
            intent = new Intent(this, UpcomingActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, item.getRequestId());
        } else if ("REQUEST_REJECTED".equals(type)) {
            intent = new Intent(this, RejectedActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, item.getRequestId());
        } else if ("TRIP_CANCELLED".equals(type)) {
            intent = new Intent(this, UpcomingActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, item.getRequestId());
        } else if ("TRIP_COMPLETED".equals(type)) {
            intent = new Intent(this, CompleteActivity.class);
            intent.putExtra(NavigationKeys.REQUEST_ID, item.getRequestId());
        } else {
            routeInProgress = false;
            notificationList.setEnabled(true);
            Toast.makeText(this, "Loại thông báo này chưa được hỗ trợ.", Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    private void showActionError(int token, boolean markingRead) {
        if (!isCurrentAction(token)) {
            return;
        }
        routeInProgress = false;
        notificationList.setEnabled(true);

        String message = markingRead
                ? "Chưa xác nhận được trạng thái đã đọc. Kiểm tra mạng rồi thử lại."
                : "Thông báo không còn dữ liệu liên quan hoặc chưa thể xác minh.";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean isCurrentAction(int token) {
        return canUpdateUi() && actionToken == token && isExpectedUserSignedIn();
    }

    private boolean canUpdateUi() {
        return isActive && !isFinishing() && !isDestroyed();
    }

    private boolean isExpectedUserSignedIn() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        return user != null && expectedUid != null && expectedUid.equals(user.getUid());
    }
}
