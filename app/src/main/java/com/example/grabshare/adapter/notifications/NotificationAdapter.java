package com.example.grabshare.adapter.notifications;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.grabshare.R;
import com.example.grabshare.model.AppNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hiển thị notification thật; thao tác mở mục được xử lý bởi Activity. */
public class NotificationAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<AppNotification> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public NotificationAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    /** Thay toàn bộ danh sách bằng snapshot mới nhất của repository. */
    public void replaceItems(List<AppNotification> notifications) {
        items.clear();
        if (notifications != null) {
            items.addAll(notifications);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppNotification getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        String id = getItem(position).getId();
        return id == null ? position : id.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.row_notification, parent, false);
        }

        AppNotification item = getItem(position);
        ImageView icon = row.findViewById(R.id.notification_icon);
        TextView title = row.findViewById(R.id.notification_title);
        TextView actor = row.findViewById(R.id.notification_actor);
        TextView body = row.findViewById(R.id.notification_body);
        TextView time = row.findViewById(R.id.notification_time);
        TextView unread = row.findViewById(R.id.notification_unread);

        title.setText(titleFor(item.getType()));
        actor.setText("Liên quan: " + safeText(item.getActorDisplayName(), "Người liên quan"));
        body.setText(bodyFor(item));
        time.setText(formatTime(item));

        boolean isRead = Boolean.TRUE.equals(item.getIsRead());
        unread.setVisibility(isRead ? View.GONE : View.VISIBLE);
        row.setAlpha(isRead ? 0.78f : 1.0f);
        icon.setImageResource(iconFor(item.getType()));
        row.setContentDescription(title.getText() + ". " + body.getText()
                + (isRead ? ". Đã đọc" : ". Chưa đọc"));
        return row;
    }

    private String titleFor(String type) {
        if ("REQUEST_SENT".equals(type)) return "Có yêu cầu đi nhờ mới";
        if ("REQUEST_CANCELLED".equals(type)) return "Yêu cầu đi nhờ đã bị hủy";
        if ("REQUEST_ACCEPTED".equals(type)) return "Yêu cầu đã được chấp nhận";
        if ("REQUEST_REJECTED".equals(type)) return "Yêu cầu đã bị từ chối";
        if ("TRIP_CANCELLED".equals(type)) return "Chuyến đi đã bị hủy";
        if ("TRIP_COMPLETED".equals(type)) return "Chuyến đi đã hoàn thành";
        return "Thông báo mới";
    }

    private String bodyFor(AppNotification item) {
        String actor = safeText(item.getActorDisplayName(), "Người liên quan");
        String trip = safeText(item.getTripSummary(), "Chuyến không còn tồn tại");
        String type = item.getType();

        if ("REQUEST_SENT".equals(type)) {
            return actor + " đã gửi yêu cầu cho chuyến: " + trip;
        }
        if ("REQUEST_CANCELLED".equals(type)) {
            return actor + " đã hủy yêu cầu đi nhờ. Chuyến liên quan: " + trip;
        }
        if ("REQUEST_ACCEPTED".equals(type)) {
            return actor + " đã chấp nhận yêu cầu đi nhờ. Chuyến: " + trip;
        }
        if ("REQUEST_REJECTED".equals(type)) {
            return actor + " đã từ chối yêu cầu đi nhờ. Chuyến: " + trip;
        }
        if ("TRIP_CANCELLED".equals(type)) {
            return actor + " đã hủy chuyến. Tuyến: " + trip;
        }
        if ("TRIP_COMPLETED".equals(type)) {
            return "Hành trình " + trip + " đã được hoàn thành bởi " + actor + ".";
        }
        return "Có cập nhật liên quan đến chuyến: " + trip;
    }

    private int iconFor(String type) {
        if ("REQUEST_ACCEPTED".equals(type) || "TRIP_COMPLETED".equals(type)) {
            return R.drawable.ic_check;
        }
        if ("REQUEST_REJECTED".equals(type) || "REQUEST_CANCELLED".equals(type)
                || "TRIP_CANCELLED".equals(type)) {
            return R.drawable.ic_close;
        }
        if ("REQUEST_SENT".equals(type)) {
            return R.drawable.ic_info;
        }
        return R.drawable.ic_bell;
    }

    private String formatTime(AppNotification item) {
        if (item.getCreatedAt() == null) {
            return "Chưa rõ thời gian";
        }
        return dateFormat.format(item.getCreatedAt().toDate());
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
