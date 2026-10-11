package com.example.grabshare.activity.driver;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;

/**
 * Hiển thị biểu mẫu đăng chuyến dành cho chủ xe.
 */
public class PostTripActivity extends AppCompatActivity {

    /**
     * Khởi tạo giao diện đăng chuyến.
     *
     * @param savedInstanceState trạng thái giao diện đã lưu trước đó
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.screen_post);

        connectBackButton();
    }

    /**
     * Kết nối nút quay lại với thao tác đóng màn hình hiện tại.
     */
    private void connectBackButton() {
        ImageButton backButton = findViewById(R.id.header_back);

        backButton.setOnClickListener(new View.OnClickListener() {

            /**
             * Đóng màn đăng chuyến và trở về màn hình trước đó.
             *
             * @param view thành phần giao diện phát sinh thao tác nhấn
             */
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
