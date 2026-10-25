package com.example.grabshare.presentation;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.grabshare.R;

/** Hiển thị phản hồi tại chỗ; chỉ đổi giao diện, không gọi lại tác vụ nghiệp vụ. */
public final class ScreenFeedback {
    public enum Kind {
        INFORMATION,
        ERROR,
        SUCCESS
    }

    private ScreenFeedback() {
    }

    /** Nội dung rỗng ẩn vùng trạng thái; lỗi luôn có chữ giải thích bên cạnh màu sắc. */
    public static void show(TextView target, String message, Kind kind) {
        if (message == null || message.trim().isEmpty()) {
            target.setText("");
            target.setVisibility(View.GONE);
            return;
        }

        int backgroundResource = R.drawable.bg_feedback_information;
        int textColorResource = R.color.ink;
        if (kind == Kind.ERROR) {
            backgroundResource = R.drawable.bg_feedback_error;
            textColorResource = R.color.feedback_error_text;
        } else if (kind == Kind.SUCCESS) {
            backgroundResource = R.drawable.bg_feedback_success;
            textColorResource = R.color.feedback_success_text;
        }
        target.setBackgroundResource(backgroundResource);
        target.setTextColor(ContextCompat.getColor(target.getContext(), textColorResource));
        target.setText(message);
        target.setVisibility(View.VISIBLE);
    }
}
