package com.example.grabshare.activity.account;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;

/** Hiển thị quy tắc sử dụng cộng đồng từ resource, không ghi dữ liệu hoặc yêu cầu mạng. */
public class TermsActivity extends AppCompatActivity {

    //region 1. Khởi tạo giao diện và điều hướng

    /** Hiển thị nội dung cuộn được và giữ Back về đúng caller. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_terms);

        View.OnClickListener backListener = new View.OnClickListener() {
            /** Đóng trang tĩnh, không tạo lại màn Tài khoản hoặc Trợ giúp. */
            @Override
            public void onClick(View view) {
                finish();
            }
        };
        findViewById(R.id.header_back).setOnClickListener(backListener);
        findViewById(R.id.static_page_back).setOnClickListener(backListener);
    }

    //endregion
}
