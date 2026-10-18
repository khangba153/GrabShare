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

import androidx.lifecycle.ViewModelProvider;

import android.widget.TextView;
import android.widget.Toast;
import androidx.lifecycle.Observer;

import androidx.activity.OnBackPressedCallback;

/** Màn Tài khoản, đầu mối điều hướng các chức năng cá nhân. */
public class AccountActivity extends AppCompatActivity {

    private LogoutViewModel logoutViewModel;

    /** Hiển thị giao diện Tài khoản. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_account);
        logoutViewModel = new ViewModelProvider(this)
                .get(LogoutViewModel.class);
        setupNavigation();
        setupMenu();
        getOnBackPressedDispatcher().addCallback(
                this,
                logoutBackCallback);
        setupLogout();
    }

    /** Kiểm tra phiên mỗi khi màn Tài khoản trở lại phía trước. */
    @Override
    protected void onStart() {
        super.onStart();

        LogoutViewModel.State logoutState =
                logoutViewModel.getState().getValue();

        if (logoutState != null
                && (logoutState.busy || logoutState.completed)) {
            return;
        }

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

    /** Nhận thao tác đăng xuất và hiển thị trạng thái từ ViewModel. */
    private void setupLogout() {
        final View logoutButton = findViewById(R.id.account_logout);
        final TextView logoutStatus = findViewById(R.id.logout_status);

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                logoutViewModel.submit();
            }
        });

        logoutViewModel.getState().observe(
                this,
                new Observer<LogoutViewModel.State>() {
                    /** Cập nhật giao diện; chỉ về Login sau khi xử lý hoàn tất. */
                    @Override
                    public void onChanged(LogoutViewModel.State state) {
                        if (state == null || isFinishing() || isDestroyed()) {
                            return;
                        }

                        boolean enabled = !state.busy && !state.completed;

                        logoutBackCallback.setEnabled(
                                state.busy || state.completed);

                        logoutButton.setEnabled(enabled);
                        findViewById(R.id.nav_home).setEnabled(enabled);
                        findViewById(R.id.nav_trips).setEnabled(enabled);
                        findViewById(R.id.account_driver_trips)
                                .setEnabled(enabled);

                        if (state.completed) {
                            if (state.error != null) {
                                Toast.makeText(
                                        AccountActivity.this,
                                        state.error.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }

                            Intent intent = new Intent(
                                    AccountActivity.this,
                                    LoginActivity.class);

                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                            startActivity(intent);
                            finish();
                            return;
                        }

                        if (state.busy) {
                            logoutStatus.setTextColor(
                                    getColor(R.color.primary));
                            logoutStatus.setText("Đang đăng xuất…");
                            logoutStatus.setVisibility(View.VISIBLE);
                        } else if (state.error != null) {
                            logoutStatus.setTextColor(
                                    getColor(android.R.color.holo_red_dark));
                            logoutStatus.setText(state.error.getMessage());
                            logoutStatus.setVisibility(View.VISIBLE);
                        } else {
                            logoutStatus.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private final OnBackPressedCallback logoutBackCallback =
            new OnBackPressedCallback(false) {
                /** Giữ màn hình trong lúc đăng xuất để tác vụ không bị bỏ dở. */
                @Override
                public void handleOnBackPressed() {
                    // Chờ observer xử lý kết quả đăng xuất.
                }
            };

}