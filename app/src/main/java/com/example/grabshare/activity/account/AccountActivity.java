package com.example.grabshare.activity.account;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;

import android.content.Intent;
import android.view.View;

import com.example.grabshare.activity.search.HomeActivity;

import com.example.grabshare.activity.passenger.MyTripsActivity;

import com.example.grabshare.activity.driver.DriverTripsActivity;

import com.google.firebase.auth.FirebaseAuth;

/** Màn Tài khoản, đầu mối điều hướng các chức năng cá nhân. */
public class AccountActivity extends AppCompatActivity {

    /** Hiển thị giao diện Tài khoản. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_account);
        setupNavigation();
        setupMenu();
    }

    /** Kiểm tra phiên mỗi khi màn Tài khoản trở lại phía trước. */
    @Override
    protected void onStart() {
        super.onStart();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(
                    AccountActivity.this,
                    LoginActivity.class);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
            finish();
        }
    }

    /** Chuyển sang Home hoặc MyTrips, dùng lại màn đã có trong stack. */
    private void setupNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(
                                AccountActivity.this,
                                HomeActivity.class);

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                        startActivity(intent);
                        finish();
                    }
                });

        findViewById(R.id.nav_trips).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(
                                AccountActivity.this,
                                MyTripsActivity.class);

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                        startActivity(intent);
                        finish();
                    }
                });
    }

    /** Mở danh sách chuyến do tài khoản hiện tại đăng. */
    private void setupMenu() {
        findViewById(R.id.account_driver_trips).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(
                                AccountActivity.this,
                                DriverTripsActivity.class);

                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                    }
                });
    }
}