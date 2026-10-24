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
 * Nhan FCM dang data-only.
 * Chi hien thi thong bao co recipientId trung UID dang dang nhap.
 * Dung Logcat tag FCM_DEBUG de kiem tra tung buoc.
 */
public class GrabShareFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "grabshare_updates";
    private static final String CHANNEL_NAME = "Thông báo GrabShare";

    @Override
    public void onRegistered(@NonNull String installationId) {
        super.onRegistered(installationId);

        FcmDebugLogger.info(
                "FCM_CALLBACK_REGISTERED",
                "FCM da dang ky instance; fidMasked="
                        + FcmDebugLogger.mask(installationId));

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            FcmDebugLogger.warning(
                    "FCM_CALLBACK_NO_USER",
                    "onRegistered den khi chua co user dang nhap. Application se goi register() lai sau login.");
            return;
        }

        FcmDebugLogger.info(
                "FCM_CALLBACK_MAP_USER",
                "Lien ket FID voi uidMasked="
                        + FcmDebugLogger.mask(currentUser.getUid()));

        new FcmInstallationRepository().syncInstallationForUser(
                currentUser.getUid(),
                installationId);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();

        FcmDebugLogger.info(
                "FCM_MESSAGE_RECEIVED",
                "FCM da den Android; dataKeys=" + data.keySet());

        if (data.isEmpty()) {
            FcmDebugLogger.warning(
                    "FCM_MESSAGE_DROPPED",
                    "Payload khong co data; code nay dang cho data-only.");
            return;
        }

        String recipientId = data.get("recipientId");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            FcmDebugLogger.warning(
                    "FCM_MESSAGE_DROPPED",
                    "Khong co user dang nhap khi message den.");
            return;
        }

        if (recipientId == null || !recipientId.equals(currentUser.getUid())) {
            FcmDebugLogger.warning(
                    "FCM_MESSAGE_DROPPED",
                    "recipientId khong trung UID hien tai; recipientMasked="
                            + FcmDebugLogger.mask(recipientId)
                            + ", currentUidMasked="
                            + FcmDebugLogger.mask(currentUser.getUid()));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            FcmDebugLogger.warning(
                    "FCM_DISPLAY_BLOCKED",
                    "Message da den nhung app chua duoc cap quyen POST_NOTIFICATIONS.");
            return;
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            FcmDebugLogger.warning(
                    "FCM_DISPLAY_BLOCKED",
                    "Message da den nhung thong bao cua app dang bi tat trong Android Settings.");
            return;
        }

        FcmDebugLogger.info(
                "FCM_PAYLOAD_ACCEPTED",
                "Payload hop le; type="
                        + data.get("type")
                        + ", notificationIdMasked="
                        + FcmDebugLogger.mask(data.get("notificationId")));

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

        try {
            NotificationManagerCompat.from(this)
                    .notify(systemNotificationId, builder.build());

            FcmDebugLogger.info(
                    "FCM_NOTIFICATION_POSTED",
                    "Da goi notify() cho Android; notificationIdMasked="
                            + FcmDebugLogger.mask(notificationId));
        } catch (SecurityException exception) {
            FcmDebugLogger.error(
                    "FCM_NOTIFICATION_SECURITY_ERROR",
                    "Android tu choi hien thi notification.",
                    exception);
        } catch (RuntimeException exception) {
            FcmDebugLogger.error(
                    "FCM_NOTIFICATION_ERROR",
                    "Loi khi tao/hien thi notification.",
                    exception);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            FcmDebugLogger.warning(
                    "FCM_CHANNEL_FAIL",
                    "Android NotificationManager la null.");
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
