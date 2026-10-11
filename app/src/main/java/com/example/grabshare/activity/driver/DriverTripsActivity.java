package com.example.grabshare.activity.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;

/**
 * Hiển thị danh sách chuyến đi của người dùng với vai trò chủ xe.
 */
public class DriverTripsActivity extends AppCompatActivity {

    /**
     * Khởi tạo giao diện danh sách chuyến của chủ xe.
     *
     * @param savedInstanceState trạng thái giao diện đã lưu trước đó
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.screen_driver_trips);

        connectPostTripButton();
    }

    /**
     * Kết nối nút đăng chuyến với màn hình đăng chuyến.
     */
    private void connectPostTripButton() {
        AppCompatButton postTripButton =
                findViewById(R.id.primary_action);

        postTripButton.setOnClickListener(new View.OnClickListener() {

            /**
             * Mở màn hình đăng chuyến khi người dùng nhấn nút.
             *
             * @param view thành phần giao diện phát sinh thao tác nhấn
             */
            @Override
            public void onClick(View view) {
                Intent postTripIntent = new Intent(
                        DriverTripsActivity.this,
                        PostTripActivity.class
                );

                startActivity(postTripIntent);
            }
        });
    }
}
