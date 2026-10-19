package com.example.grabshare.adapter.driver;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.grabshare.R;
import com.example.grabshare.model.DriverRequestItem;
import com.example.grabshare.model.Place;

import java.util.ArrayList;
import java.util.List;

/** Hiển thị danh sách yêu cầu đi nhờ của chủ xe. */
public class RequestAdapter extends BaseAdapter {

    /** Callback mở chi tiết một yêu cầu. */
    public interface OnRequestClickListener {
        void onOpenRequest(DriverRequestItem item);
    }

    private final LayoutInflater inflater;
    private final OnRequestClickListener listener;
    private final List<DriverRequestItem> items = new ArrayList<>();

    /** Khởi tạo adapter. */
    public RequestAdapter(Context context, OnRequestClickListener listener) {
        inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    /** Thay danh sách hiện tại bằng dữ liệu mới. */
    public void replaceItems(List<DriverRequestItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public DriverRequestItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /** Điền thông tin thật vào card yêu cầu. */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.row_request, parent, false);
        }

        final DriverRequestItem item = getItem(position);
        TextView nameText = row.findViewById(R.id.driver_name);
        TextView metaText = row.findViewById(R.id.driver_meta);
        TextView pickupText = row.findViewById(R.id.request_pickup);
        TextView messageText = row.findViewById(R.id.request_message);
        TextView badgeText = row.findViewById(R.id.verified_badge);
        ImageView avatarImage = row.findViewById(R.id.driver_avatar);
        Button openButton = row.findViewById(R.id.request_open);

        String passengerName = "Hành khách";
        String passengerVehicle = "";
        if (item.getPassengerProfile() != null) {
            String storedName = item.getPassengerProfile().getDisplayName();
            if (storedName != null && !storedName.trim().isEmpty()) {
                passengerName = storedName.trim();
            }
            String storedVehicle = item.getPassengerProfile().getVehicleName();
            if (storedVehicle != null && !storedVehicle.trim().isEmpty()) {
                passengerVehicle = storedVehicle.trim();
            }
        }

        nameText.setText(passengerName);
        metaText.setText(passengerVehicle.isEmpty()
                ? "Người gửi yêu cầu đi nhờ"
                : passengerVehicle);
        badgeText.setVisibility(View.GONE);
        avatarImage.setVisibility(View.GONE);

        Place pickup = item.getRequest().getPickup();
        String pickupName = pickup == null || pickup.getName() == null
                || pickup.getName().trim().isEmpty()
                ? "Chưa có điểm đón"
                : pickup.getName().trim();
        pickupText.setText("Điểm đón: " + pickupName);

        String message = item.getRequest().getMessage();
        messageText.setText(message == null || message.trim().isEmpty()
                ? "Không có lời nhắn."
                : message.trim());

        openButton.setText("Xem chi tiết");
        openButton.setClickable(true);
        openButton.setFocusable(true);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onOpenRequest(item);
                }
            }
        });

        return row;
    }
}
