package com.example.grabshare.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.grabshare.R;
import com.example.grabshare.activity.notifications.NotificationsActivity;
import com.example.grabshare.repository.notifications.FcmInstallationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Nhận FCM dạng data-only. Chỉ hiển thị thông báo dành cho UID đang đăng nhập.
 * Cloud Function cần gửi recipientId, notificationId, type, title và body trong data.
 */
public class GrabShareFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "grabshare_updates";
    private static final String CHANNEL_NAME = "Thông báo GrabShare";

    @Override
    public void onRegistered(@NonNull String installationId) {
        super.onRegistered(installationId);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            return;
        }

        new FcmInstallationRepository().syncInstallationForUser(
                currentUser.getUid(),
                installationId);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();

        if (data.isEmpty()) {
            return;
        }

        String recipientId = data.get("recipientId");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Tin nhắn cũ của tài khoản khác không được hiện trên thiết bị này.
        if (currentUser == null
                || recipientId == null
                || !recipientId.equals(currentUser.getUid())) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        showNotification(data);
    }

    private void showNotification(Map<String, String> data) {
        createNotificationChannel();

        String type = data.get("type");
        String title = data.get("title");
        String body = data.get("body");
        String notificationId = data.get("notificationId");

        if (title == null || title.trim().isEmpty()) {
            title = getTitleForType(type);
        }

        if (body == null || body.trim().isEmpty()) {
            body = getBodyForType(type);
        }

        Intent openInboxIntent =
                new Intent(this, NotificationsActivity.class);
        openInboxIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = notificationId == null
                ? (int) System.currentTimeMillis()
                : notificationId.hashCode();

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                requestCode,
                openInboxIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setContentIntent(contentIntent);

        int systemNotificationId = notificationId == null
                ? requestCode
                : notificationId.hashCode();

        NotificationManagerCompat.from(this)
                .notify(systemNotificationId, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT);

        channel.setDescription("Thông báo về yêu cầu và chuyến đi GrabShare");
        manager.createNotificationChannel(channel);
    }

    private String getTitleForType(String type) {
        if ("REQUEST_SENT".equals(type)) {
            return "Có yêu cầu đi chung mới";
        }
        if ("REQUEST_ACCEPTED".equals(type)) {
            return "Yêu cầu đã được chấp nhận";
        }
        if ("REQUEST_REJECTED".equals(type)) {
            return "Yêu cầu bị từ chối";
        }
        if ("REQUEST_CANCELLED".equals(type)) {
            return "Yêu cầu đã bị hủy";
        }
        if ("TRIP_CANCELLED".equals(type)) {
            return "Chuyến đi đã bị hủy";
        }
        if ("TRIP_COMPLETED".equals(type)) {
            return "Chuyến đi đã hoàn thành";
        }

        return "Bạn có thông báo mới từ GrabShare";
    }

    private String getBodyForType(String type) {
        if ("REQUEST_SENT".equals(type)) {
            return "Bạn có một yêu cầu đi chung cần xem.";
        }
        if ("REQUEST_ACCEPTED".equals(type)) {
            return "Yêu cầu đi chung của bạn đã được chấp nhận.";
        }
        if ("REQUEST_REJECTED".equals(type)) {
            return "Yêu cầu đi chung của bạn đã bị từ chối.";
        }
        if ("REQUEST_CANCELLED".equals(type)) {
            return "Một yêu cầu đi chung đã bị hủy.";
        }
        if ("TRIP_CANCELLED".equals(type)) {
            return "Chuyến đi liên quan đến yêu cầu của bạn đã bị hủy.";
        }
        if ("TRIP_COMPLETED".equals(type)) {
            return "Chuyến đi liên quan đến yêu cầu của bạn đã hoàn thành.";
        }

        return "Mở GrabShare để xem chi tiết.";
    }
}
