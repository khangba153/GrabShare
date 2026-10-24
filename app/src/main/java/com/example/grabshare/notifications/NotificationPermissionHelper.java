package com.example.grabshare.notifications;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Xin quyền thông báo một lần khi người dùng vào màn Home. */
public final class NotificationPermissionHelper {

    private static final String PREFERENCES_NAME = "grabshare_notification_settings";
    private static final String KEY_PERMISSION_REQUESTED = "post_notifications_requested";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 7101;

    private NotificationPermissionHelper() {
    }

    public static void requestIfNeeded(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        SharedPreferences preferences = activity.getSharedPreferences(
                PREFERENCES_NAME,
                Activity.MODE_PRIVATE);

        if (preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
            return;
        }

        preferences.edit()
                .putBoolean(KEY_PERMISSION_REQUESTED, true)
                .apply();

        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_POST_NOTIFICATIONS);
    }
}
