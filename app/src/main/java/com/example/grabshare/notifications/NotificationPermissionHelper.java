package com.example.grabshare.notifications;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationManagerCompat;

/** Xin quyen va ghi log trang thai notification de debug FCM. */
public final class NotificationPermissionHelper {

    private static final String PREFERENCES_NAME = "grabshare_notification_settings";
    private static final String KEY_PERMISSION_REQUESTED = "post_notifications_requested";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 7101;

    private NotificationPermissionHelper() {
    }

    public static void requestIfNeeded(Activity activity) {
        if (activity == null) {
            FcmDebugLogger.warning(
                    "PERMISSION_REQUEST_SKIP",
                    "Activity la null.");
            return;
        }

        logCurrentState(activity, "before_request");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            FcmDebugLogger.info(
                    "PERMISSION_REQUEST_SKIP",
                    "Android API "
                            + Build.VERSION.SDK_INT
                            + " khong can runtime permission POST_NOTIFICATIONS.");
            return;
        }

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            FcmDebugLogger.info(
                    "PERMISSION_ALREADY_GRANTED",
                    "POST_NOTIFICATIONS da duoc cap.");
            return;
        }

        SharedPreferences preferences = activity.getSharedPreferences(
                PREFERENCES_NAME,
                Activity.MODE_PRIVATE);

        if (preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
            FcmDebugLogger.warning(
                    "PERMISSION_REQUEST_SKIP",
                    "App da hien thi yeu cau quyen truoc do; khong hoi lai tu dong.");
            return;
        }

        preferences.edit()
                .putBoolean(KEY_PERMISSION_REQUESTED, true)
                .apply();

        FcmDebugLogger.info(
                "PERMISSION_REQUEST",
                "Dang yeu cau POST_NOTIFICATIONS tu Android.");

        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_POST_NOTIFICATIONS);
    }

    /** Goi tu onResume de ghi lai ket qua sau khi hop thoai quyen dong. */
    public static void logCurrentState(Activity activity, String checkpoint) {
        if (activity == null) {
            FcmDebugLogger.warning(
                    "PERMISSION_STATE",
                    "Activity null tai checkpoint=" + checkpoint);
            return;
        }

        boolean runtimePermissionGranted;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runtimePermissionGranted = true;
        } else {
            runtimePermissionGranted =
                    ContextCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;
        }

        boolean appNotificationsEnabled =
                NotificationManagerCompat.from(activity).areNotificationsEnabled();

        SharedPreferences preferences = activity.getSharedPreferences(
                PREFERENCES_NAME,
                Activity.MODE_PRIVATE);

        boolean requestedBefore =
                preferences.getBoolean(KEY_PERMISSION_REQUESTED, false);

        FcmDebugLogger.info(
                "PERMISSION_STATE",
                "checkpoint=" + checkpoint
                        + ", sdk=" + Build.VERSION.SDK_INT
                        + ", runtimePermissionGranted=" + runtimePermissionGranted
                        + ", appNotificationsEnabled=" + appNotificationsEnabled
                        + ", requestedBefore=" + requestedBefore);
    }
}
