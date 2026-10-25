package com.example.grabshare.presentation;

import android.app.Activity;
import android.view.View;
import android.view.ViewParent;

import com.example.grabshare.R;
import com.example.grabshare.activity.notifications.NotificationsActivity;
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
    private final UnreadNotificationBadge badge;
    private final FirebaseAuth authentication = FirebaseAuth.getInstance();
    private FirebaseAuth.AuthStateListener authenticationListener;
    private ListenerRegistration unreadListener;
    private int notificationCount;
    private boolean active;
    private int generation;

    private NotificationBellController(Activity activity, View bell) {
        this.activity = activity;
        this.bell = bell;
        badge = new UnreadNotificationBadge(activity.getResources().getDisplayMetrics().density);
    }

    /** Chuông dùng chung chỉ đọc thông báo hệ thống của UID đang đăng nhập. */
    static NotificationBellController create(Activity activity) {
        String screenName = activity.getClass().getSimpleName();
        if (activity instanceof NotificationsActivity || "LoginActivity".equals(screenName) || "RegisterActivity".equals(screenName)
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
        return new NotificationBellController(activity, bell);
    }

    /** Dùng cùng điều hướng trên mọi màn, kể cả màn trạng thái và màn tạo chuyến. */
    void start() {
        active = true;
        bell.setVisibility(View.VISIBLE);
        bell.setForeground(badge);
        int minimumSize = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        bell.setMinimumWidth(minimumSize);
        bell.setMinimumHeight(minimumSize);
        ViewParent parent = bell.getParent();
        while (parent instanceof View) {
            View ancestor = (View) parent;
            if (ancestor.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
                ancestor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
            parent = ancestor.getParent();
        }
        MainNavigation.bindNotifications(activity, bell.getId());
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
        notificationCount = 0;
        renderUnknown();
        FirebaseUser user = authentication.getCurrentUser();
        if (!active || user == null) {
            bell.setVisibility(View.GONE);
            return;
        }
        bell.setVisibility(View.VISIBLE);
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

    }

    private void renderCounts() {
        badge.setCount(notificationCount);
        bell.setContentDescription("Thông báo, " + notificationCount + " chưa đọc");
    }

    private void renderUnknown() {
        badge.setCount(0);
        bell.setContentDescription("Thông báo, chưa cập nhật số chưa đọc");
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
        bell.setForeground(null);
        bell.setContentDescription("Thông báo");
    }
}
