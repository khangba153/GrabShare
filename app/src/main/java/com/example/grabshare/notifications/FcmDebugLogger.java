package com.example.grabshare.notifications;

import android.util.Log;

/**
 * Logger tam thoi de kiem tra pipeline FCM.
 *
 * Loc Logcat bang tag: FCM_DEBUG.
 * Khong ghi toan bo FID/token vao log.
 */
public final class FcmDebugLogger {

    private static final String TAG = "FCM_DEBUG";

    private FcmDebugLogger() {
    }

    public static void info(String stage, String message) {
        Log.i(TAG, "[" + stage + "] " + message);
    }

    public static void warning(String stage, String message) {
        Log.w(TAG, "[" + stage + "] " + message);
    }

    public static void error(String stage, String message, Throwable error) {
        Log.e(TAG, "[" + stage + "] " + message, error);
    }

    public static String mask(String value) {
        if (value == null) {
            return "<null>";
        }

        if (value.length() <= 8) {
            return "length:" + value.length();
        }

        return value.substring(0, 4)
                + "..."
                + value.substring(value.length() - 4);
    }
}
