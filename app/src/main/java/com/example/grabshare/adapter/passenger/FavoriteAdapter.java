package com.example.grabshare.adapter.passenger;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.model.Favorite;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách chuyến đi yêu thích của Hành khách (Thuận-7).
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    public interface OnFavoriteClickListener {
        void onFavoriteClick(FavoriteItem favoriteItem);
    }

    public static class FavoriteItem {
        private final Favorite favorite;
        private final Trip trip;
        private final PublicProfile driverProfile;

        /** Giữ đúng favorite, chuyến và hồ sơ công khai của cùng một hàng. */
        public FavoriteItem(Favorite favorite, Trip trip, PublicProfile driverProfile) {
            this.favorite = favorite;
            this.trip = trip;
            this.driverProfile = driverProfile;
        }

        /** Trả bản ghi yêu thích để caller giữ đúng ID đã lưu. */
        public Favorite getFavorite() {
            return favorite;
        }

        /** Chuyến có thể vắng nếu document nguồn đã bị xóa. */
        public Trip getTrip() {
            return trip;
        }

        /** Hồ sơ công khai có thể vắng mà không làm mất hàng yêu thích. */
        public PublicProfile getDriverProfile() {
            return driverProfile;
        }
    }

    private final Context context;
    private final List<FavoriteItem> items;
    private final OnFavoriteClickListener listener;

    /** Nhận listener của màn danh sách; adapter không tự điều hướng. */
    public FavoriteAdapter(Context context, OnFavoriteClickListener listener) {
        this.context = context;
        this.items = new ArrayList<>();
        this.listener = listener;
    }

    /** Thay dữ liệu của lượt tải hiện tại sau khi Activity đã kiểm tra thế hệ. */
    public void setItems(List<FavoriteItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /** Tạo hàng theo layout hiện có của danh sách chuyến. */
    @NonNull
    /** Gắn đúng favorite theo vị trí hiện tại. */
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_ride, parent, false);
        return new FavoriteViewHolder(view);
    }

    /** Số hàng đã nhận từ server. */
    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        final FavoriteItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class FavoriteViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvDriverName;
        private final TextView tvDriverMeta;
        private final TextView tvRideSeats;
        private final TextView tvPickup;
        private final TextView tvDestination;
        private final TextView tvDepartureTime;
        private final TextView tvPrice;
        private final TextView tvStatus;

        /** Lấy các view của một hàng để bind nhiều lần khi RecyclerView tái sử dụng. */
        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDriverName = itemView.findViewById(R.id.driver_name);
            tvDriverMeta = itemView.findViewById(R.id.driver_meta);
            tvRideSeats = itemView.findViewById(R.id.ride_seats);
            tvPickup = itemView.findViewById(R.id.ride_pickup);
            tvDestination = itemView.findViewById(R.id.ride_destination);
            tvDepartureTime = itemView.findViewById(R.id.ride_time);
            tvPrice = itemView.findViewById(R.id.ride_price);
            tvStatus = itemView.findViewById(R.id.ride_status);
        }

        /** Không mở Detail nếu chuyến nguồn không còn tồn tại. */
        public void bind(final FavoriteItem item, final OnFavoriteClickListener listener) {
            Trip trip = item.getTrip();
            PublicProfile profile = item.getDriverProfile();

            if (trip == null) {
                if (tvDriverName != null) tvDriverName.setText("Chuyến không khả dụng");
                if (tvDriverMeta != null) tvDriverMeta.setText("");
                if (tvRideSeats != null) tvRideSeats.setText("");
                if (tvPickup != null) tvPickup.setText("Điểm đi: Không xác định");
                if (tvDestination != null) tvDestination.setText("Điểm đến: Không xác định");
                if (tvDepartureTime != null) tvDepartureTime.setText("Thời gian: --/--");
                if (tvPrice != null) tvPrice.setText("Chưa có giá");
                if (tvStatus != null) tvStatus.setText("Đã bị xóa hoặc hủy");
                itemView.setAlpha(0.5f);
                itemView.setOnClickListener(null);
                return;
            }

            itemView.setAlpha(1.0f);

            if (tvDriverName != null) {
                if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().trim().isEmpty()) {
                    tvDriverName.setText(profile.getDisplayName().trim());
                } else {
                    tvDriverName.setText("Chủ xe");
                }
            }

            if (tvDriverMeta != null) {
                if (profile != null && profile.getVehicleName() != null && !profile.getVehicleName().trim().isEmpty()) {
                    tvDriverMeta.setText(profile.getVehicleName().trim());
                } else {
                    tvDriverMeta.setText("Xe máy");
                }
            }

            if (tvRideSeats != null) {
                if (trip.getAvailableSeats() != null) {
                    tvRideSeats.setText(trip.getAvailableSeats() + " chỗ trống");
                } else {
                    tvRideSeats.setText("");
                }
            }

            if (tvPickup != null) {
                if (trip.getOrigin() != null) {
                    tvPickup.setText("Điểm đi: " + trip.getOrigin().getName());
                } else {
                    tvPickup.setText("Điểm đi chưa rõ");
                }
            }

            if (tvDestination != null) {
                if (trip.getDestination() != null) {
                    tvDestination.setText("Điểm đến: " + trip.getDestination().getName());
                } else {
                    tvDestination.setText("Điểm đến chưa rõ");
                }
            }

            if (tvDepartureTime != null) {
                if (trip.getDepartureAt() != null) {
                    CharSequence timeFormatted = DateFormat.format("HH:mm - dd/MM/yyyy", trip.getDepartureAt().toDate());
                    tvDepartureTime.setText("Khởi hành: " + timeFormatted);
                } else {
                    tvDepartureTime.setText("Không rõ thời gian");
                }
            }

            if (tvPrice != null) {
                if (trip.getCostVnd() != null) {
                    if (trip.getCostVnd() == 0) {
                        tvPrice.setText("Miễn phí");
                    } else {
                        tvPrice.setText(String.format(java.util.Locale.getDefault(), "%,d VNĐ", trip.getCostVnd()));
                    }
                } else {
                    tvPrice.setText("Chưa có giá");
                }
            }

            if (tvStatus != null) {
                String status = "OPEN";
                if (trip.getStatus() != null) {
                    status = trip.getStatus().toUpperCase();
                }
                if ("CANCELLED".equals(status)) {
                    tvStatus.setText("♥ Đã lưu · Đã hủy");
                } else if ("COMPLETED".equals(status)) {
                    tvStatus.setText("♥ Đã lưu · Hoàn thành");
                } else if ("FULL".equals(status)) {
                    tvStatus.setText("♥ Đã lưu · Hết chỗ");
                } else {
                    tvStatus.setText("♥ Đã lưu · Đang mở");
                }
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                /** Chỉ báo item cho Activity tự xác thực phiên và điều hướng. */
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onFavoriteClick(item);
                    }
                }
            });
        }
    }
}
