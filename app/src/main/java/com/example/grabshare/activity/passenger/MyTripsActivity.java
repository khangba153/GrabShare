package com.example.grabshare.activity.passenger;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;

/**
 * Gắn layout MyTrips vào Activity thật để công cụ Debug mở đúng màn.
 * Phần đọc Firestore và điều hướng sang các màn trạng thái sẽ được nối ở bước tiếp theo.
 */
public class MyTripsActivity extends AppCompatActivity {

    /** Khởi tạo giao diện danh sách chuyến đi nhờ. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_my_trips);

        ImageButton backButton = findViewById(R.id.header_back);
        if (backButton != null) {
            backButton.setVisibility(View.VISIBLE);
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });
        }

        bindTab(R.id.tab_upcoming);
        bindTab(R.id.tab_pending);
        bindTab(R.id.tab_history);
    }

    /** Nối sự kiện cơ bản cho tab; lọc dữ liệu sẽ được thêm khi repository sẵn sàng. */
    private void bindTab(int viewId) {
        Button tab = findViewById(viewId);
        if (tab == null) {
            return;
        }
        tab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Chưa lọc dữ liệu ở bước gắn Activity; tránh giả vờ tab đã nối Firestore.
                // Giữ hành vi rõ ràng cho đến khi PassengerTripsRepository được tích hợp.
            }
        });
    }
}
