package com.example.grabshare.presentation;

import android.app.Activity;
import android.view.View;
import android.view.ViewParent;

import com.example.grabshare.R;
import com.example.grabshare.activity.notifications.NotificationsActivity;
import com.example.grabshare.activity.chat.ConversationsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;

/** Một chuông dùng chung cho màn đang RESUMED, dừng đọc khi rời màn hoặc đổi tài khoản. */
final class NotificationBellController {
    private final Activity activity;
    private final View bell;
    private final View chatButton;
    private final UnreadNotificationBadge badge;
    private final UnreadNotificationBadge chatBadge;
    private final FirebaseAuth authentication = FirebaseAuth.getInstance();
    private FirebaseAuth.AuthStateListener authenticationListener;
    private ListenerRegistration unreadListener;
    private ListenerRegistration chatListener;
    private int notificationCount;
    private int chatCount;
    private boolean active;
    private int generation;

    private NotificationBellController(Activity activity, View bell, View chatButton) {
        this.activity = activity;
        this.bell = bell;
        this.chatButton = chatButton;
        badge = new UnreadNotificationBadge(activity.getResources().getDisplayMetrics().density);
        chatBadge = new UnreadNotificationBadge(activity.getResources().getDisplayMetrics().density);
    }

    /** Hai lối vào riêng: chuông cho thông báo hệ thống, bong bóng cho danh sách chat. */
    static NotificationBellController create(Activity activity) {
        String screenName = activity.getClass().getSimpleName();
        if ("LoginActivity".equals(screenName) || "RegisterActivity".equals(screenName)
                || "ResetPasswordActivity".equals(screenName)) {
            View unusedBell = activity.findViewById(R.id.header_action);
            if (unusedBell != null) {
                unusedBell.setVisibility(View.GONE);
            }
            return null;
        }
        View bell = activity.findViewById(R.id.home_notifications);
        if (bell == null) {
            bell = activity.findViewById(R.id.header_action);
        }
        if (bell == null || !"Thông báo".contentEquals(String.valueOf(bell.getContentDescription()))) {
            return null;
        }
        View chatButton = activity.findViewById(R.id.home_chat);
        if (chatButton == null) {
            chatButton = activity.findViewById(R.id.header_chat);
        }
        if (chatButton == null) {
            return null;
        }
        return new NotificationBellController(activity, bell, chatButton);
    }

    /** Dùng cùng điều hướng trên mọi màn, kể cả màn trạng thái và màn tạo chuyến. */
    void start() {
        active = true;
        bell.setVisibility(View.VISIBLE);
        bell.setForeground(badge);
        chatButton.setVisibility(View.VISIBLE);
        chatButton.setForeground(chatBadge);
        int minimumSize = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        bell.setMinimumWidth(minimumSize);
        bell.setMinimumHeight(minimumSize);
        chatButton.setMinimumWidth(minimumSize);
        chatButton.setMinimumHeight(minimumSize);
        ViewParent parent = bell.getParent();
        while (parent instanceof View) {
            View ancestor = (View) parent;
            if (ancestor.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
                ancestor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
            parent = ancestor.getParent();
        }
        MainNavigation.bindNotifications(activity, bell.getId());
        if (activity instanceof NotificationsActivity) {
            bell.setOnClickListener(null);
            bell.setClickable(false);
            bell.setSelected(true);
        }
        chatButton.setClickable(true);
        chatButton.setFocusable(true);
        chatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clickedView) {
                if (active && authentication.getCurrentUser() != null
                        && !activity.isFinishing() && !activity.isDestroyed()) {
                    android.content.Intent intent = new android.content.Intent(activity, ConversationsActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                }
            }
        });
        if (activity instanceof ConversationsActivity) {
            chatButton.setOnClickListener(null);
            chatButton.setClickable(false);
            chatButton.setSelected(true);
        }
        authenticationListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(FirebaseAuth authentication) {
                restartForUser();
            }
        };
        authentication.addAuthStateListener(authenticationListener);
    }

    /** Query theo recipientId để không cần thêm index; không đọc hồ sơ hoặc nội dung để đếm. */
    private void restartForUser() {
        generation = generation + 1;
        if (unreadListener != null) {
            unreadListener.remove();
            unreadListener = null;
        }
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
        notificationCount = 0;
        chatCount = 0;
        renderUnknown();
        FirebaseUser user = authentication.getCurrentUser();
        if (!active || user == null) {
            bell.setVisibility(View.GONE);
            chatButton.setVisibility(View.GONE);
            return;
        }
        bell.setVisibility(View.VISIBLE);
        chatButton.setVisibility(View.VISIBLE);
        final String expectedUserIdentifier = user.getUid();
        final int expectedGeneration = generation;
        unreadListener = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("recipientId", expectedUserIdentifier)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(QuerySnapshot snapshot, FirebaseFirestoreException error) {
                        FirebaseUser currentUser = authentication.getCurrentUser();
                        if (!active || generation != expectedGeneration || currentUser == null
                                || !expectedUserIdentifier.equals(currentUser.getUid())) {
                            return;
                        }
                        if (error != null || snapshot == null || snapshot.getMetadata().isFromCache()
                                || snapshot.getMetadata().hasPendingWrites()) {
                            notificationCount = 0;
                            renderCounts();
                            return;
                        }
                        int unreadCount = 0;
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            if (Boolean.FALSE.equals(document.get("isRead"))) {
                                unreadCount = unreadCount + 1;
                            }
                        }
                        notificationCount = unreadCount;
                        renderCounts();
                    }
                });
        chatListener = FirebaseFirestore.getInstance().collection("conversations")
                .whereArrayContains("participantIds", expectedUserIdentifier)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(QuerySnapshot snapshot, FirebaseFirestoreException error) {
                        if (!active || generation != expectedGeneration
                                || !com.example.grabshare.repository.chat.ChatRepository.sameUser(expectedUserIdentifier)) {
                            return;
                        }
                        chatCount = 0;
                        if (error == null && snapshot != null && !snapshot.getMetadata().isFromCache()
                                && !snapshot.getMetadata().hasPendingWrites()) {
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                if (com.example.grabshare.repository.chat.ChatRepository.unread(document, expectedUserIdentifier)) {
                                    chatCount++;
                                }
                            }
                        }
                        renderCounts();
                    }
                });
    }

    private void renderCounts() {
        badge.setCount(notificationCount);
        chatBadge.setCount(chatCount);
        bell.setContentDescription("Thông báo, " + notificationCount + " chưa đọc");
        chatButton.setContentDescription("Tin nhắn, " + chatCount + " cuộc trò chuyện chưa đọc");
    }

    private void renderUnknown() {
        badge.setCount(0);
        chatBadge.setCount(0);
        bell.setContentDescription("Thông báo, chưa cập nhật số chưa đọc");
        chatButton.setContentDescription("Tin nhắn, chưa cập nhật số chưa đọc");
    }

    /** Xóa badge của UID cũ và hủy kết nối ngay khi màn không còn phía trước. */
    void stop() {
        active = false;
        generation = generation + 1;
        if (authenticationListener != null) {
            authentication.removeAuthStateListener(authenticationListener);
            authenticationListener = null;
        }
        if (unreadListener != null) {
            unreadListener.remove();
            unreadListener = null;
        }
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
        bell.setForeground(null);
        chatButton.setForeground(null);
        bell.setContentDescription("Thông báo");
        chatButton.setContentDescription("Tin nhắn");
    }
}
