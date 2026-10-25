package com.example.grabshare.presentation;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.view.ViewCompat;

/** Mở phần phụ theo nhu cầu, giữ nguyên ID và nội dung do Activity cập nhật. */
final class ScreenSections {
    private ScreenSections() {
    }

    /** Chỉ nhận các vùng gắn tag ui_section; không tự ẩn trường nhập hoặc điều kiện xác nhận. */
    static void bind(View view, Bundle state) {
        if (view == null) {
            return;
        }
        Object tag = view.getTag();
        if (view instanceof ViewGroup && tag instanceof String
                && ((String) tag).startsWith("ui_section:")) {
            final ViewGroup section = (ViewGroup) view;
            if (section.getChildCount() == 2 && section.getChildAt(0) instanceof TextView) {
                final TextView title = (TextView) section.getChildAt(0);
                final View content = section.getChildAt(1);
                final String label = title.getText().toString();
                boolean expanded = state != null && state.getBoolean((String) tag, false);
                update(title, content, label, expanded);
                title.setOnClickListener(new View.OnClickListener() {
                    /** Một điểm chạm mở cả phần, chữ dài không tràn ra danh sách chính. */
                    @Override
                    public void onClick(View clickedView) {
                        update(title, content, label, !title.isSelected());
                    }
                });
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index = index + 1) {
                bind(group.getChildAt(index), state);
            }
        }
    }

    /** Lưu riêng mỗi phần khi xoay, không đưa thông tin riêng tư vào Bundle. */
    static void save(View view, Bundle state) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            Object tag = group.getTag();
            if (tag instanceof String && ((String) tag).startsWith("ui_section:")
                    && group.getChildCount() == 2) {
                state.putBoolean((String) tag, group.getChildAt(0).isSelected());
            }
            for (int index = 0; index < group.getChildCount(); index = index + 1) {
                save(group.getChildAt(index), state);
            }
        }
    }

    private static void update(TextView title, View content, String label, boolean expanded) {
        title.setSelected(expanded);
        title.setText(label + "  +");
        content.setVisibility(View.GONE);
        ViewCompat.setStateDescription(title, "Đang thu gọn");
        if (expanded) {
            title.setText(label + "  −");
            content.setVisibility(View.VISIBLE);
            ViewCompat.setStateDescription(title, "Đang mở rộng");
        }
    }
}
