package com.example.grabshare.activity.account;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;

/** Hướng dẫn sử dụng tĩnh, đọc được cả khi không có kết nối mạng. */
public class HelpActivity extends AppCompatActivity {

    //region 1. Khởi tạo giao diện và điều hướng

    /** Hiển thị hướng dẫn, nối Back và đường đọc điều khoản thật. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_help);

        View.OnClickListener backListener = new View.OnClickListener() {
            /** Trở về đúng màn đã mở Trợ giúp. */
            @Override
            public void onClick(View view) {
                finish();
            }
        };
        findViewById(R.id.header_back).setOnClickListener(backListener);
        findViewById(R.id.static_page_back).setOnClickListener(backListener);

        findViewById(R.id.help_terms).setOnClickListener(new View.OnClickListener() {
            /** Mở cùng trang điều khoản được dùng ở menu Tài khoản. */
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HelpActivity.this, TermsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
    }

    //endregion
}
