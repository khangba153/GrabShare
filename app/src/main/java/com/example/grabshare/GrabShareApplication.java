package com.example.grabshare;

import android.app.Application;

import com.example.grabshare.presentation.ScreenLayoutCallbacks;

/**
 * Đăng ký bố cục dùng chung cho các màn hình.
 * Không lưu Activity hoặc View trong Application.
 */
public class GrabShareApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ScreenLayoutCallbacks());
    }
}
