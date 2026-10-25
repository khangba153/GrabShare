package com.example.grabshare.presentation;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.example.grabshare.R;
import com.example.grabshare.activity.account.AccountActivity;
import com.example.grabshare.activity.driver.DriverTripsActivity;
import com.example.grabshare.activity.notifications.NotificationsActivity;
import com.example.grabshare.activity.passenger.MyTripsActivity;
import com.example.grabshare.activity.search.HomeActivity;
import com.google.firebase.auth.FirebaseAuth;

/** Điều hướng các tab chính; màn chi tiết vẫn giữ Back về nơi đã mở. */
public final class MainNavigation {
    private MainNavigation() {
    }

    /** Home được giữ ở đáy stack để không mất P/C và thời gian khi đổi tab. */
    public static void openTab(Activity source, Class<? extends Activity> destination) {
        if (source.isFinishing() || source.isDestroyed()
                || FirebaseAuth.getInstance().getCurrentUser() == null
                || source.getClass().equals(destination)) {
            return;
        }
        Intent intent = new Intent(source, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        source.startActivity(intent);
        if (!(source instanceof HomeActivity)) {
            source.finish();
        }
    }

    /** Nối đủ hai vai trò và ba tab, không ghi dữ liệu hoặc tự thay trạng thái chuyến. */
    public static void bind(final Activity activity) {
        connectTab(activity, R.id.nav_home, HomeActivity.class);
        connectTab(activity, R.id.nav_account, AccountActivity.class);
        if (activity instanceof DriverTripsActivity || activity instanceof MyTripsActivity) {
            markSelected(activity.findViewById(R.id.nav_trips));
        } else {
            connectTab(activity, R.id.nav_trips, MyTripsActivity.class);
        }
        connectTab(activity, R.id.role_passenger, MyTripsActivity.class);
        connectTab(activity, R.id.role_driver, DriverTripsActivity.class);
    }

    /** Nút hiện có trong layout mới được nối, màn khác không phải tạo thêm View giả. */
    private static void connectTab(final Activity activity, int viewIdentifier,
            final Class<? extends Activity> destination) {
        View button = activity.findViewById(viewIdentifier);
        if (button == null) {
            return;
        }
        if (activity.getClass().equals(destination)) {
            markSelected(button);
            return;
        }
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openTab(activity, destination);
            }
        });
    }

    /** Tab đang chọn có trạng thái truy cập rõ và không tạo thêm bản sao màn hình. */
    private static void markSelected(View button) {
        if (button != null) {
            button.setSelected(true);
            button.setOnClickListener(null);
            button.setClickable(false);
            button.setFocusable(true);
        }
    }

    /** Chuông ở các màn chính mở Inbox thật; không yêu cầu quyền thông báo nền. */
    public static void bindNotifications(final Activity activity, int viewIdentifier) {
        View button = activity.findViewById(viewIdentifier);
        if (button == null) {
            return;
        }
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!activity.isFinishing() && !activity.isDestroyed()
                        && FirebaseAuth.getInstance().getCurrentUser() != null) {
                    Intent intent = new Intent(activity, NotificationsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                }
            }
        });
    }
}
