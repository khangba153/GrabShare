package com.example.grabshare.presentation;

import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.grabshare.R;

/** Cho phép kiểm tra mật khẩu đã nhập; không lưu hoặc gửi nội dung mật khẩu. */
public final class PasswordVisibility {
    private PasswordVisibility() {
    }

    /** Mặc định luôn che mật khẩu khi tạo lại màn; giữ vị trí con trỏ lúc bật/tắt. */
    public static void bind(final EditText input, final TextView toggle) {
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        toggle.setText(R.string.leader_password_show);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int selection = input.getSelectionStart();
                if (input.getTransformationMethod() == null) {
                    input.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    toggle.setText(R.string.leader_password_show);
                } else {
                    input.setTransformationMethod(null);
                    toggle.setText(R.string.leader_password_hide);
                }
                if (selection >= 0 && selection <= input.length()) {
                    input.setSelection(selection);
                }
            }
        });
    }
}
