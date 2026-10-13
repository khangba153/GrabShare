package com.example.grabshare.dev;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.grabshare.GrabShareUi;
import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;


/** Công cụ chạy riêng màn trong Debug; không đăng nhập hoặc ghi dữ liệu. */
public class ScreenLauncherActivity extends AppCompatActivity {
    private Spinner group;
    private SwitchCompat realMode;
    private EditText tripId;
    private EditText requestId;
    private LinearLayout screenList;

    /** Khởi tạo bộ chọn; giữ gói, chế độ và các ID khi xoay màn hình. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_launcher);
        group = findViewById(R.id.dev_group);
        realMode = findViewById(R.id.dev_real_mode);
        tripId = findViewById(R.id.dev_trip_id);
        requestId = findViewById(R.id.dev_request_id);
        View root = findViewById(R.id.dev_root);
        final int padding = root.getPaddingLeft();
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            /** Chừa chỗ cho thanh hệ thống và bàn phím, kể cả khi xoay. */
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.ime());
                view.setPadding(padding + bars.left, padding + bars.top,
                        padding + bars.right, padding + bars.bottom);
                return insets;
            }
        });
        if (savedInstanceState != null) {
            group.setSelection(savedInstanceState.getInt("dev_group", 0));
            realMode.setChecked(savedInstanceState.getBoolean("dev_real_mode", false));
            tripId.setText(savedInstanceState.getString("dev_trip_id", ""));
            requestId.setText(savedInstanceState.getString("dev_request_id", ""));
        }
        screenList = findViewById(R.id.dev_screens);
        group.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            /** Lọc danh sách theo gói vừa chọn. */
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterScreens();
            }

            /** Không đổi dữ liệu khi Spinner không có mục chọn. */
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        realMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            /** Đổi giữa xem XML và chạy Activity thật. */
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                updateMode();
            }
        });
        findViewById(R.id.dev_preview_all).setOnClickListener(new View.OnClickListener() {
            /** Mở bản xem trước toàn bộ XML bằng vuốt. */
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreenLauncherActivity.this, GrabShareUi.class));
            }
        });
        filterScreens();
        updateMode();
    }

    /** Lọc gói mà không thay đổi màn hoặc dữ liệu Firebase. */
    private void filterScreens() {
        String[] groups = {"", "L", "A", "B", "C"};
        String selected = groups[Math.max(0, group.getSelectedItemPosition())];
        screenList.removeAllViews();
        for (ScreenCatalog screen : ScreenCatalog.SCREENS) {
            if (selected.isEmpty() || selected.equals(screen.group)) {
                Button button = new Button(this);
                String description = realMode.isChecked()
                        ? getString(R.string.dev_activity_hint, screen.activity)
                        : getString(R.string.dev_preview_hint);
                button.setText(screen.group + " · " + screen.title + "\n" + description);
                button.setAllCaps(false);
                button.setOnClickListener(new View.OnClickListener() {
                    /** Mở màn đang được chọn theo chế độ của công cụ Debug. */
                    @Override
                    public void onClick(View view) {
                        openScreen(screen);
                    }
                });
                screenList.addView(button, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    /** Chỉ hiện ô ID khi thành viên muốn mở Activity thật. */
    private void updateMode() {
        findViewById(R.id.dev_ids).setVisibility(realMode.isChecked() ? View.VISIBLE : View.GONE);
        filterScreens();
    }

    /** Mở preview hoặc Activity đã đăng ký; báo thiếu thay vì giả kết quả thật. */
    private void openScreen(ScreenCatalog screen) {
        if (!realMode.isChecked()) {
            Intent preview = new Intent(this, GrabShareUi.class);
            preview.putExtra(GrabShareUi.EXTRA_PREVIEW_SCREEN, screen.preview);
            startActivity(preview);
            return;
        }
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, screen.activity));
        try {
            getPackageManager().getActivityInfo(intent.getComponent(), 0);
        } catch (PackageManager.NameNotFoundException error) {
            Toast.makeText(this, getString(R.string.dev_missing_activity, screen.activity),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!tripId.getText().toString().trim().isEmpty()) {
            intent.putExtra(NavigationKeys.TRIP_ID, tripId.getText().toString().trim());
        }
        if (!requestId.getText().toString().trim().isEmpty()) {
            intent.putExtra(NavigationKeys.REQUEST_ID, requestId.getText().toString().trim());
        }
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, getString(R.string.dev_launch_error, screen.activity),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Lưu lựa chọn và form ID để mở lại sau khi xoay. */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("dev_group", group.getSelectedItemPosition());
        outState.putBoolean("dev_real_mode", realMode.isChecked());
        outState.putString("dev_trip_id", tripId.getText().toString());
        outState.putString("dev_request_id", requestId.getText().toString());
        super.onSaveInstanceState(outState);
    }
}
