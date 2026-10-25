package com.example.grabshare.presentation;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.Timestamp;

import java.util.Calendar;

/** Bộ lọc trong danh sách đã tải; không phát sinh query hoặc thay đổi dữ liệu máy chủ. */
public final class TripListFilter {
    private final String[] statusLabels;
    private final String[] statusValues;
    private int statusIndex;
    private int periodIndex;
    private int orderIndex;

    public TripListFilter(String[] labels, String[] values) {
        statusLabels = labels;
        statusValues = values;
    }

    public void restore(Bundle state) {
        if (state != null) {
            statusIndex = Math.max(0, Math.min(state.getInt("list_status"), statusValues.length - 1));
            periodIndex = Math.max(0, Math.min(state.getInt("list_period"), 3));
            orderIndex = Math.max(0, Math.min(state.getInt("list_order"), 2));
        }
    }

    /** Lưu theo UID, vai trò và mục để chuyển màn không làm mất hoặc trộn bộ lọc. */
    public void savePreferences(Context context, String key) {
        context.getSharedPreferences("trip_list_preferences", Context.MODE_PRIVATE).edit()
                .putInt(key + "_status", statusIndex)
                .putInt(key + "_period", periodIndex)
                .putInt(key + "_order", orderIndex).apply();
    }

    /** Không dùng lựa chọn của tài khoản khác làm điều kiện lọc cho phiên mới. */
    public void restorePreferences(Context context, String key) {
        SharedPreferences preferences = context.getSharedPreferences("trip_list_preferences", Context.MODE_PRIVATE);
        statusIndex = Math.max(0, Math.min(preferences.getInt(key + "_status", 0), statusValues.length - 1));
        periodIndex = Math.max(0, Math.min(preferences.getInt(key + "_period", 0), 3));
        orderIndex = Math.max(0, Math.min(preferences.getInt(key + "_order", 0), 2));
    }

    /** Thứ tự đề xuất phụ thuộc mục đang quản lý hay lịch sử, do màn danh sách quyết định. */
    public boolean automaticOrder() {
        return orderIndex == 0;
    }

    public void save(Bundle state) {
        state.putInt("list_status", statusIndex);
        state.putInt("list_period", periodIndex);
        state.putInt("list_order", orderIndex);
    }

    public String summary() {
        int selections = 0;
        if (statusIndex > 0) {
            selections = selections + 1;
        }
        if (periodIndex > 0) {
            selections = selections + 1;
        }
        if (orderIndex > 0) {
            selections = selections + 1;
        }
        if (selections == 0) {
            return "Bộ lọc";
        }
        return "Bộ lọc (" + selections + ")";
    }

    public boolean matches(String status, Timestamp departureAt) {
        if (statusIndex > 0 && !statusValues[statusIndex].equals(status)) {
            return false;
        }
        if (periodIndex == 0) {
            return true;
        }
        if (departureAt == null) {
            return false;
        }
        Calendar beginning = Calendar.getInstance();
        beginning.set(Calendar.HOUR_OF_DAY, 0);
        beginning.set(Calendar.MINUTE, 0);
        beginning.set(Calendar.SECOND, 0);
        beginning.set(Calendar.MILLISECOND, 0);
        long minimum = beginning.getTimeInMillis();
        Calendar end = (Calendar) beginning.clone();
        if (periodIndex == 1) {
            end.add(Calendar.DAY_OF_YEAR, 1);
        } else if (periodIndex == 2) {
            end.add(Calendar.DAY_OF_YEAR, 7);
        } else {
            beginning.add(Calendar.DAY_OF_YEAR, -29);
            minimum = beginning.getTimeInMillis();
            end.add(Calendar.DAY_OF_YEAR, 1);
        }
        long departure = departureAt.toDate().getTime();
        return departure >= minimum && departure < end.getTimeInMillis();
    }

    /** Thiếu thời gian luôn ở cuối; không biến timestamp thiếu thành chuyến sớm nhất. */
    public int compare(Timestamp first, Timestamp second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        if (orderIndex == 2) {
            return second.compareTo(first);
        }
        return first.compareTo(second);
    }

    public void show(Context context, final Runnable onApplied) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        final Spinner status = addChoice(context, content, "Trạng thái", statusLabels, statusIndex);
        final Spinner period = addChoice(context, content, "Ngày khởi hành",
                new String[]{"Mọi ngày", "Hôm nay", "7 ngày từ hôm nay", "30 ngày đến hôm nay"}, periodIndex);
        final Spinner order = addChoice(context, content, "Sắp xếp",
                new String[]{"Đề xuất", "Khởi hành sớm trước", "Khởi hành muộn trước"}, orderIndex);
        new AlertDialog.Builder(context).setTitle("Lọc danh sách chuyến").setView(content)
                .setNegativeButton("Đóng", null)
                .setNeutralButton("Đặt lại", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        statusIndex = 0;
                        periodIndex = 0;
                        orderIndex = 0;
                        onApplied.run();
                    }
                })
                .setPositiveButton("Áp dụng", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        statusIndex = status.getSelectedItemPosition();
                        periodIndex = period.getSelectedItemPosition();
                        orderIndex = order.getSelectedItemPosition();
                        onApplied.run();
                    }
                }).show();
    }

    private Spinner addChoice(Context context, LinearLayout content, String label,
            String[] options, int selection) {
        TextView title = new TextView(context);
        title.setText(label);
        content.addView(title);
        Spinner choice = new Spinner(context);
        choice.setContentDescription(label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        choice.setAdapter(adapter);
        choice.setSelection(selection);
        content.addView(choice, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(52 * context.getResources().getDisplayMetrics().density)));
        return choice;
    }
}
