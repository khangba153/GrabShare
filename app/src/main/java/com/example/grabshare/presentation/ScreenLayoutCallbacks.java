package com.example.grabshare.presentation;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Chừa vùng hệ thống cho màn thật, giữ nguyên padding thiết kế và hỗ trợ bàn phím. */
public final class ScreenLayoutCallbacks implements Application.ActivityLifecycleCallbacks {


    /** Mỗi root được gắn một lần sau khi Activity đã dựng xong giao diện. */
    @Override
    public void onActivityPostCreated(@NonNull Activity activity, Bundle savedInstanceState) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        final View screen = content.getChildAt(0);
        ScreenSections.bind(screen, savedInstanceState);
        final int initialLeft = screen.getPaddingLeft();
        final int initialTop = screen.getPaddingTop();
        final int initialRight = screen.getPaddingRight();
        final int initialBottom = screen.getPaddingBottom();

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowCompat.getInsetsController(activity.getWindow(), screen)
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(activity.getWindow(), screen)
                .setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(screen, new OnApplyWindowInsetsListener() {
            /** Tính lại từ padding ban đầu; xoay hoặc mở bàn phím không cộng dồn khoảng trống. */
            @Override
            public WindowInsetsCompat onApplyWindowInsets(
                    View view, WindowInsetsCompat windowInsets) {
                Insets systemInsets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
                Insets keyboardInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                int bottomInset = Math.max(systemInsets.bottom, keyboardInsets.bottom);
                view.setPadding(initialLeft + systemInsets.left,
                        initialTop + systemInsets.top,
                        initialRight + systemInsets.right,
                        initialBottom + bottomInset);
                return WindowInsetsCompat.CONSUMED;
            }
        });
        ViewCompat.requestApplyInsets(screen);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {
        ScreenSections.save(activity.findViewById(android.R.id.content), state);
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
