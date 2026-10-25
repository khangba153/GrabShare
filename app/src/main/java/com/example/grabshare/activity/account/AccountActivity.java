package com.example.grabshare.activity.account;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.R;
import com.example.grabshare.presentation.MainNavigation;
import com.example.grabshare.activity.driver.DriverTripsActivity;
import com.example.grabshare.activity.notifications.NotificationsActivity;
import com.example.grabshare.activity.passenger.MyTripsActivity;
import com.example.grabshare.activity.passenger.FavoritesActivity;
import com.example.grabshare.activity.search.HomeActivity;
import com.example.grabshare.repository.account.ProfileRepository.AccountProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Hiển thị hồ sơ chính chủ và điều hướng các chức năng cá nhân. */
public class AccountActivity extends AppCompatActivity {

    //region 1. ViewModel và phiên đăng nhập

    private static final String STATE_USER_IDENTIFIER = "account_profile_uid";

    private LogoutViewModel logoutViewModel;
    private AccountProfileViewModel profileViewModel;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authListener;
    private String expectedUserIdentifier;

    private final OnBackPressedCallback logoutBackCallback =
            new OnBackPressedCallback(false) {
                /** Giữ màn khi đăng xuất đang xử lý cache hoặc chờ xác nhận hoàn tất. */
                @Override
                public void handleOnBackPressed() {
                    // Observer đăng xuất sẽ điều hướng khi tác vụ kết thúc.
                }
            };

    //endregion

    //region 2. Khởi tạo và vòng đời

    /** Nối các menu đã có đích thật và quan sát tải hồ sơ/đăng xuất. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_account);

        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
        if (savedInstanceState != null) {
            expectedUserIdentifier = savedInstanceState.getString(STATE_USER_IDENTIFIER);
        } else if (signedInUser != null) {
            expectedUserIdentifier = signedInUser.getUid();
        }

        logoutViewModel = new ViewModelProvider(this).get(LogoutViewModel.class);
        profileViewModel = new ViewModelProvider(this).get(AccountProfileViewModel.class);

        setupNavigation();
        setupMenu();
        setupProfile();
        getOnBackPressedDispatcher().addCallback(this, logoutBackCallback);
        setupLogout();
    }

    /** Đọc lại hồ sơ khi trở về từ Edit; UID khác không được giữ màn riêng cũ. */
    @Override
    protected void onStart() {
        super.onStart();

        if (isFinishing()) {
            return;
        }

        authListener = new FirebaseAuth.AuthStateListener() {
            /** Không can thiệp vào luồng signOut đang được LogoutViewModel xử lý. */
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (!logoutInProgress() && !isExpectedUserSignedIn()) {
                    profileViewModel.clearProfile();
                    openLogin();
                }
            }
        };
        firebaseAuth.addAuthStateListener(authListener);

        if (!logoutInProgress()) {
            if (isExpectedUserSignedIn()) {
                profileViewModel.loadProfile(expectedUserIdentifier);
            } else {
                openLogin();
            }
        }
    }

    /** Gỡ listener của Activity cũ; ViewModel tiếp tục giữ tiến trình qua xoay. */
    @Override
    protected void onStop() {
        if (authListener != null) {
            firebaseAuth.removeAuthStateListener(authListener);
            authListener = null;
        }
        super.onStop();
    }

    /** Gắn dữ liệu phục hồi với UID đã mở màn, không tự chuyển sang tài khoản khác. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putString(STATE_USER_IDENTIFIER, expectedUserIdentifier);
        super.onSaveInstanceState(state);
    }

    //endregion

    //region 3. Điều hướng tab và menu

    /** Chuyển Home/MyTrips bằng màn có sẵn trong stack, giữ dữ liệu form Home. */
    private void setupNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(new View.OnClickListener() {
            /** Quay về Home, không nhân Activity khi đổi tab liên tục. */
            @Override
            public void onClick(View view) {
                openTab(HomeActivity.class);
            }
        });

        findViewById(R.id.nav_trips).setOnClickListener(new View.OnClickListener() {
            /** Mở danh sách chuyến đi nhờ và giữ nguyên contract của Thuận. */
            @Override
            public void onClick(View view) {
                openTab(MyTripsActivity.class);
            }
        });
    }

    /** Nối các màn đã bàn giao; quyền thao tác vẫn phụ thuộc UID và tiến trình đăng xuất. */
    private void setupMenu() {
        connectScreen(R.id.account_driver_trips, DriverTripsActivity.class);
        connectScreen(R.id.edit_profile, EditProfileActivity.class);
        connectScreen(R.id.account_vehicle, EditProfileActivity.class);
        connectScreen(R.id.account_help, HelpActivity.class);
        connectScreen(R.id.account_terms, TermsActivity.class);

        connectScreen(R.id.account_favorites, FavoritesActivity.class);
        connectScreen(R.id.account_notifications, NotificationsActivity.class);
        findViewById(R.id.header_action).setVisibility(View.GONE);
    }

    /** Mỗi menu dùng đích thật trong Manifest và chỉ mở từ phiên hợp lệ. */
    private void connectScreen(int viewIdentifier, final Class<? extends Activity> screen) {
        View row = findViewById(viewIdentifier);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            /** Không mở màn khác giữa tác vụ đăng xuất hoặc sau khi UID thay đổi. */
            @Override
            public void onClick(View view) {
                if (!logoutInProgress() && isExpectedUserSignedIn()) {
                    Intent intent = new Intent(AccountActivity.this, screen);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            }
        });
    }

    /** Dùng lại màn tab hiện có và đóng Account để tránh nhân stack. */
    private void openTab(Class<? extends Activity> screen) {
        if (logoutInProgress() || !isExpectedUserSignedIn()) {
            return;
        }
        MainNavigation.openTab(this, screen);
    }

    //endregion

    //region 4. Tải và hiển thị hồ sơ

    /** Liên kết observer và nút thử lại; không tự tạo hồ sơ từ một lần đọc lỗi. */
    private void setupProfile() {
        findViewById(R.id.account_profile_retry).setOnClickListener(new View.OnClickListener() {
            /** Chỉ tải lại khi người dùng còn đúng phiên và không đăng xuất. */
            @Override
            public void onClick(View view) {
                if (isExpectedUserSignedIn() && !logoutInProgress()) {
                    profileViewModel.loadProfile(expectedUserIdentifier);
                }
            }
        });

        profileViewModel.getState().observe(this, new Observer<AccountProfileViewModel.State>() {
            /** Chỉ render dữ liệu của UID đang mở màn hình. */
            @Override
            public void onChanged(AccountProfileViewModel.State state) {
                renderProfile(state);
            }
        });
    }

    /** Hiển thị dữ liệu thật; xóa giá trị cũ khi tải lỗi, đổi phiên hoặc đăng xuất. */
    private void renderProfile(AccountProfileViewModel.State state) {
        if (state == null || isFinishing() || isDestroyed()) {
            return;
        }

        AccountProfile profile = state.profile;
        boolean allowed = isExpectedUserSignedIn() && !logoutInProgress();
        boolean hasData = allowed
                && profile != null
                && expectedUserIdentifier.equals(profile.userIdentifier);

        TextView name = findViewById(R.id.profile_name);
        TextView email = findViewById(R.id.profile_email);
        TextView vehicle = findViewById(R.id.profile_meta);
        TextView plate = findViewById(R.id.profile_vehicle_plate);
        TextView status = findViewById(R.id.account_profile_status);

        name.setText(R.string.profile_account_placeholder);
        email.setText("");
        vehicle.setText("");
        plate.setText("");

        if (hasData) {
            if (profile.displayName.isEmpty()) {
                name.setText(R.string.profile_no_name);
            } else {
                name.setText(profile.displayName);
            }

            if (profile.email.isEmpty()) {
                email.setText(R.string.profile_no_email);
            } else {
                email.setText(profile.email);
            }

            if (profile.vehicleName.isEmpty()) {
                vehicle.setText(R.string.profile_no_vehicle);
            } else {
                vehicle.setText(profile.vehicleName);
            }

            if (profile.vehiclePlate.isEmpty()) {
                plate.setText(R.string.profile_no_plate);
            } else {
                plate.setText(getString(R.string.profile_plate_label, profile.vehiclePlate));
            }
        }

        String message = "";
        if (allowed && state.loading) {
            message = getString(R.string.profile_loading);
        } else if (allowed && state.error != null) {
            message = ProfileMessages.explain(this, state.error);
        } else if (hasData && !profile.isComplete()) {
            message = getString(R.string.profile_missing);
        }

        status.setText(message);
        if (state.error == null) {
            status.setTextColor(getColor(R.color.muted));
        } else {
            status.setTextColor(getColor(android.R.color.holo_red_dark));
        }

        if (message.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
        }

        if (allowed && state.loading) {
            findViewById(R.id.account_profile_loading).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.account_profile_loading).setVisibility(View.GONE);
        }

        if (allowed && state.error != null) {
            findViewById(R.id.account_profile_retry).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.account_profile_retry).setVisibility(View.GONE);
        }

        TextView editProfileButton = findViewById(R.id.edit_profile);
        if (hasData && !profile.isComplete()) {
            editProfileButton.setText(R.string.profile_complete_action);
        } else {
            editProfileButton.setText(R.string.profile_edit_action);
        }
        updateMenuAvailability(hasData && !state.loading);
    }

    /** Khóa cả các menu mới trong lúc đăng xuất để không mở thêm màn riêng. */
    private void updateMenuAvailability(boolean profileReady) {
        boolean available = isExpectedUserSignedIn() && !logoutInProgress();
        int[] generalMenus = {
                R.id.nav_home, R.id.nav_trips, R.id.account_driver_trips,
                R.id.account_favorites, R.id.account_notifications,
                R.id.account_help, R.id.account_terms, R.id.account_logout
        };

        for (int viewIdentifier : generalMenus) {
            findViewById(viewIdentifier).setEnabled(available);
        }

        findViewById(R.id.edit_profile).setEnabled(available && profileReady);
        findViewById(R.id.account_vehicle).setEnabled(available && profileReady);
        if (available && profileReady) {
            findViewById(R.id.account_vehicle).setAlpha(1f);
        } else {
            findViewById(R.id.account_vehicle).setAlpha(0.5f);
        }
    }

    //endregion

    //region 5. Đăng xuất

    /** Giữ nguyên cơ chế logout đã bàn giao và kết hợp khóa các mục mới. */
    private void setupLogout() {
        final TextView logoutStatus = findViewById(R.id.logout_status);

        findViewById(R.id.account_logout).setOnClickListener(new View.OnClickListener() {
            /** Tác vụ xóa cache và signOut vẫn do LogoutViewModel thực hiện. */
            @Override
            public void onClick(View view) {
                logoutViewModel.submit();
            }
        });

        logoutViewModel.getState().observe(this, new Observer<LogoutViewModel.State>() {
            /** Chỉ về Login khi tác vụ hoàn tất; lỗi vẫn giữ phiên và cho thử lại. */
            @Override
            public void onChanged(LogoutViewModel.State state) {
                if (state == null || isFinishing() || isDestroyed()) {
                    return;
                }

                logoutBackCallback.setEnabled(state.busy || state.completed);
                if (state.busy || state.completed) {
                    profileViewModel.clearProfile();
                }
                renderProfile(profileViewModel.getState().getValue());

                if (state.completed) {
                    if (state.error != null) {
                        Toast.makeText(AccountActivity.this,
                                state.error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    openLogin();
                    return;
                }

                if (state.busy) {
                    logoutStatus.setTextColor(getColor(R.color.primary));
                    logoutStatus.setText(R.string.profile_logging_out);
                    logoutStatus.setVisibility(View.VISIBLE);
                } else if (state.error != null) {
                    logoutStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                    logoutStatus.setText(ProfileMessages.explain(AccountActivity.this, state.error));
                    logoutStatus.setVisibility(View.VISIBLE);
                    if (isExpectedUserSignedIn()) {
                        profileViewModel.loadProfile(expectedUserIdentifier);
                    }
                } else {
                    logoutStatus.setVisibility(View.GONE);
                }
            }
        });
    }

    //endregion

    //region 6. Kiểm tra phiên và điều hướng Login

    /** Busy/completed thuộc luồng logout; Auth listener không được điều hướng sớm. */
    private boolean logoutInProgress() {
        LogoutViewModel.State state = logoutViewModel.getState().getValue();
        return state != null && (state.busy || state.completed);
    }

    /** Chỉ chấp nhận UID đã mở Account, kể cả khi Activity được phục hồi. */
    private boolean isExpectedUserSignedIn() {
        FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
        return expectedUserIdentifier != null
                && signedInUser != null
                && expectedUserIdentifier.equals(signedInUser.getUid());
    }

    /** Xóa stack riêng tư để Back không quay về dữ liệu của phiên trước. */
    private void openLogin() {
        if (isFinishing()) {
            return;
        }

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    //endregion
}
