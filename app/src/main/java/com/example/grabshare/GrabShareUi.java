package com.example.grabshare;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.OnApplyWindowInsetsListener;

/** Xem XML bằng vuốt, hỗ trợ mở thẳng màn được chọn từ công cụ Debug. */
public class GrabShareUi extends AppCompatActivity {
    public static final String EXTRA_PREVIEW_SCREEN = "preview_screen";
    private static final UiScreen[] PAGES = {
            UiScreen.LOGIN, UiScreen.REGISTER, UiScreen.RESET_PASSWORD,
            UiScreen.ACCOUNT, UiScreen.EDIT_PROFILE, UiScreen.HELP, UiScreen.TERMS,
            UiScreen.HOME, UiScreen.LOCATION, UiScreen.RESULTS,
            UiScreen.DETAIL, UiScreen.REVIEW, UiScreen.PENDING,
            UiScreen.ACCEPTED, UiScreen.REJECTED, UiScreen.UPCOMING, UiScreen.CHAT,
            UiScreen.COMPLETE, UiScreen.MY_TRIPS, UiScreen.FAVORITES,
            UiScreen.DRIVER_TRIPS, UiScreen.POST, UiScreen.EDIT_TRIP,
            UiScreen.REQUESTS, UiScreen.REQUEST_DETAIL, UiScreen.MANAGE,
            UiScreen.NOTIFICATIONS
    };
    private static int findPageIndex(String screenName) {
        if (screenName == null) {
            return 0;
        }
        for (int i = 0; i < PAGES.length; i++) {
            if (Objects.equals(PAGES[i].name(), screenName)) {
                return i;
            }
        }
        return 0;
    }

    private int pageIndex;
    private float downX, downY;
    private MotionEvent downEvent;
    private boolean verticalScroll;
    private int touchSlop;

    /** Mở trang đã lưu hoặc màn được yêu cầu; không chạy nghiệp vụ. */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (savedInstanceState != null) {
            pageIndex = savedInstanceState.getInt("preview_page", 0);
        } else {
            pageIndex = findPageIndex(getIntent().getStringExtra(EXTRA_PREVIEW_SCREEN));
        }
        pageIndex = Math.max(0, Math.min(pageIndex, PAGES.length - 1));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), new OnApplyWindowInsetsListener() {
            /** Chừa vùng thanh hệ thống khi hiển thị màn preview. */
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });
        showPage();
    }

    /** Nạp XML hiện tại và nhãn vị trí trong danh sách preview. */
    private void showPage() {
        UiScreen page = PAGES[pageIndex];
        FrameLayout container = findViewById(R.id.screen_container);
        container.removeAllViews();
        getLayoutInflater().inflate(page.layout, container, true);
        TextView position = findViewById(R.id.preview_position);
        position.setText((pageIndex + 1) + " / " + PAGES.length + " · " + page.title
                + "\nVuốt trái / phải để xem màn");
    }

    /** Giữ trang đang xem khi xoay màn hình. */
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("preview_page", pageIndex);
        super.onSaveInstanceState(out);
    }

    /** Cho phép vuốt đổi trang và cuộn nội dung, chặn thao tác nghiệp vụ trong preview. */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                clearGesture();
                downX = event.getX();
                downY = event.getY();
                downEvent = MotionEvent.obtain(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                // Chỉ chuyển kéo dọc cho XML để đọc nội dung dài.
                if (!verticalScroll && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                    verticalScroll = true;
                    if (downEvent != null) super.dispatchTouchEvent(downEvent);
                }
                if (verticalScroll) super.dispatchTouchEvent(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (verticalScroll) super.dispatchTouchEvent(event);
                else {
                    float distance = event.getX() - downX;
                    if (Math.abs(distance) > 48 * getResources().getDisplayMetrics().density
                            && Math.abs(distance) > Math.abs(event.getY() - downY) * 1.3f) {
                        pageIndex = (pageIndex + (distance < 0 ? 1 : PAGES.length - 1)) % PAGES.length;
                        showPage();
                    }
                }
                clearGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (verticalScroll) super.dispatchTouchEvent(event);
                clearGesture();
                return true;
            default: return true;
        }
    }

    /** Giải phóng sự kiện chạm đã giữ và đặt lại trạng thái kéo. */
    private void clearGesture() {
        if (downEvent != null) { downEvent.recycle(); downEvent = null; }
        verticalScroll = false;
    }

    /** Dọn sự kiện chạm khi kết thúc màn preview. */
    @Override protected void onDestroy() {
        clearGesture();
        super.onDestroy();
    }
}
