package com.example.grabshare;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Displays complete XML screens. Only swipe navigation and reading scroll remain. */
public class GrabShareUi extends AppCompatActivity {
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
    private int pageIndex;
    private float downX, downY;
    private MotionEvent downEvent;
    private boolean verticalScroll;
    private int touchSlop;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (savedInstanceState != null) pageIndex = savedInstanceState.getInt("preview_page", 0);
        pageIndex = Math.max(0, Math.min(pageIndex, PAGES.length - 1));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        showPage();
    }

    private void showPage() {
        UiScreen page = PAGES[pageIndex];
        FrameLayout container = findViewById(R.id.screen_container);
        container.removeAllViews();
        getLayoutInflater().inflate(page.layout, container, true);
        TextView position = findViewById(R.id.preview_position);
        position.setText((pageIndex + 1) + " / " + PAGES.length + " · " + page.title
                + "\nVuốt trái / phải để xem màn");
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("preview_page", pageIndex);
        super.onSaveInstanceState(out);
    }

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
                // Forward vertical drags only so long XML pages can be read.
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

    private void clearGesture() {
        if (downEvent != null) { downEvent.recycle(); downEvent = null; }
        verticalScroll = false;
    }

    @Override protected void onDestroy() {
        clearGesture();
        super.onDestroy();
    }
}
