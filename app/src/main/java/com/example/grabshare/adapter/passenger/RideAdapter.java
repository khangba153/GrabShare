package com.example.grabshare.adapter.passenger;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.grabshare.R;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.google.firebase.Timestamp;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Hiển thị các yêu cầu đi nhờ thật bên trong LinearLayout của màn MyTrips. */
public final class RideAdapter {
    public interface OnRideClickListener {
        /** Báo Activity mở màn trạng thái theo request_id của dòng được chọn. */
        void onRideClick(PassengerTripsRepository.RideItem item);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final LinearLayout container;
    @Nullable private final OnRideClickListener clickListener;

    /** Khởi tạo adapter gắn vào danh sách trong XML, không thêm dependency RecyclerView. */
    public RideAdapter(Context context, LinearLayout container,
                       @Nullable OnRideClickListener clickListener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.container = container;
        this.clickListener = clickListener;
    }

    /** Vẽ lại danh sách từ dữ liệu DB; danh sách rỗng sẽ xóa hoàn toàn dòng cũ. */
    public void setItems(List<PassengerTripsRepository.RideItem> items) {
        container.removeAllViews();
        if (items == null) return;

        for (final PassengerTripsRepository.RideItem item : items) {
            View row = inflater.inflate(R.layout.row_passenger_ride, container, false);
            bindRow(row, item);
            if (clickListener != null) {
                row.setOnClickListener(new View.OnClickListener() {
                    /** Chuyển giao dòng thật cho Activity; adapter không tự dựng dữ liệu. */
                    @Override
                    public void onClick(View view) {
                        clickListener.onRideClick(item);
                    }
                });
            } else {
                row.setClickable(false);
                row.setFocusable(false);
            }
            container.addView(row);
        }
    }

    /** Điền thông tin chuyến/hồ sơ; profile thiếu dùng placeholder, không tên demo. */
    private void bindRow(View row, PassengerTripsRepository.RideItem item) {
        TextView driverName = row.findViewById(R.id.driver_name);
        TextView driverMeta = row.findViewById(R.id.driver_meta);
        TextView originName = row.findViewById(R.id.route_start);
        TextView destinationName = row.findViewById(R.id.route_end);
        TextView tripTime = row.findViewById(R.id.trip_time);
        TextView tripCost = row.findViewById(R.id.trip_cost);
        TextView tripStatus = row.findViewById(R.id.trip_status);

        PublicProfile profile = item.getProfile();
        if (profile == null) {
            driverName.setText("Chủ xe");
            driverMeta.setText("Chưa có thông tin hồ sơ công khai");
        } else {
            driverName.setText(nonBlank(profile.getDisplayName(), "Chủ xe"));
            driverMeta.setText(nonBlank(profile.getVehicleName(), "Chưa có thông tin xe"));
        }

        Trip trip = item.getTrip();
        if (trip == null) {
            originName.setText("Không lấy được điểm đi");
            destinationName.setText("Không lấy được điểm đến");
            tripTime.setText("Chưa có dữ liệu chuyến");
            tripCost.setText("—");
        } else {
            Place origin = trip.getOrigin();
            Place destination = trip.getDestination();
            originName.setText(origin == null ? "Chưa có điểm đi" : nonBlank(origin.getName(), "Chưa có điểm đi"));
            destinationName.setText(destination == null ? "Chưa có điểm đến" : nonBlank(destination.getName(), "Chưa có điểm đến"));
            tripTime.setText(formatTime(trip.getDepartureAt()));
            tripCost.setText(formatCost(trip.getCostVnd()));
        }
        tripStatus.setText(item.getDisplayStatus());
    }

    /** Hiển thị thời gian theo timestamp DB, không suy ra trạng thái từ giờ thiết bị. */
    private String formatTime(@Nullable Timestamp timestamp) {
        if (timestamp == null) return "Chưa có giờ khởi hành";
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("vi-VN"));
        return formatter.format(timestamp.toDate());
    }

    /** Định dạng chi phí Long theo VNĐ hoặc để dấu gạch khi DB không có giá trị. */
    private String formatCost(@Nullable Long costVnd) {
        if (costVnd == null) return "Chưa có chi phí";
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return formatter.format(costVnd) + "đ";
    }

    /** Dùng placeholder rõ nghĩa khi một chuỗi công khai bị thiếu. */
    private String nonBlank(@Nullable String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
