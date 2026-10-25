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
import com.example.grabshare.presentation.PlaceNameFormatter;
import com.example.grabshare.presentation.TripListState;
import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.activity.passenger.PassengerContributionText;
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
        metaText.setVisibility(View.GONE);
        badgeText.setVisibility(View.GONE);
        avatarImage.setVisibility(View.GONE);

        Place pickup = item.getRequest().getPickup();
        String pickupName = pickup == null || pickup.getName() == null
                || pickup.getName().trim().isEmpty()
                ? "Chưa có điểm đón"
                : pickup.getName().trim();
        pickupText.setText("Đón: " + PlaceNameFormatter.format(pickupName));
        pickupText.setMaxLines(1);
        pickupText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView dropoffText = row.findViewById(R.id.request_dropoff);
        try {
            Place dropoff = RequestRouteResolver.resolveDropoff(item.getRequest(), item.getTrip());
            dropoffText.setText("Trả: " + PlaceNameFormatter.format(dropoff.getName()));
        } catch (IllegalArgumentException error) {
            dropoffText.setText("Điểm xuống chưa hợp lệ · Xem chi tiết");
        }
        ((TextView) row.findViewById(R.id.request_contribution))
                .setText(PassengerContributionText.format(item.getRequest()));
        String status = TripListState.passengerStatus(item.getRequest(), item.getTrip(), System.currentTimeMillis());
        ((TextView) row.findViewById(R.id.request_card_status)).setText(
                TripListState.label(status, item.getTrip(), System.currentTimeMillis()));

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
        row.setContentDescription(passengerName + ", " + pickupText.getText() + ", "
                + dropoffText.getText() + ". Xem yêu cầu");
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setOnClickListener(new View.OnClickListener() {
            /** Cả thẻ là một điểm chạm, không lặp thêm nút mở trên từng khách. */
            @Override
            public void onClick(View clickedView) {
                if (listener != null) {
                    listener.onOpenRequest(item);
                }
            }
        });

        return row;
    }
}
