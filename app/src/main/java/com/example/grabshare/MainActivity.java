package com.example.grabshare;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.activity.account.LoginActivity;

/** Điểm khởi động ứng dụng, chuyển tới luồng kiểm tra đăng nhập. */
public class MainActivity extends AppCompatActivity {

    /** Mở Login để kiểm tra phiên và hồ sơ rồi đóng màn khởi động. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(
                MainActivity.this,
                LoginActivity.class
        );

        startActivity(intent);
        finish();
    }
}