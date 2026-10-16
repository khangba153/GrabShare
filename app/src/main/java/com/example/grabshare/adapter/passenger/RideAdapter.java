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
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.PassengerRideItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách các chuyến đi nhờ của Hành khách.
 */
public class RideAdapter extends RecyclerView.Adapter<RideAdapter.RideViewHolder> {

    public interface OnRideClickListener {
        void onRideClick(PassengerRideItem rideItem);
    }

    private final Context context;
    private final List<PassengerRideItem> rideItems;
    private final OnRideClickListener listener;

    public RideAdapter(Context context, OnRideClickListener listener) {
        this.context = context;
        this.rideItems = new ArrayList<>();
        this.listener = listener;
    }

    public void setItems(List<PassengerRideItem> newItems) {
        this.rideItems.clear();
        if (newItems != null) {
            this.rideItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_ride, parent, false);
        return new RideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RideViewHolder holder, int position) {
        final PassengerRideItem item = rideItems.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return rideItems.size();
    }

    public static class RideViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvPickup;
        private final TextView tvDestination;
        private final TextView tvDepartureTime;
        private final TextView tvPrice;
        private final TextView tvStatus;

        public RideViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPickup = itemView.findViewById(R.id.ride_pickup);
            tvDestination = itemView.findViewById(R.id.ride_destination);
            tvDepartureTime = itemView.findViewById(R.id.ride_time);
            tvPrice = itemView.findViewById(R.id.ride_price);
            tvStatus = itemView.findViewById(R.id.ride_status);
        }

        public void bind(final PassengerRideItem item, final OnRideClickListener listener) {
            TripRequest request = item.getRequest();
            Trip trip = item.getTrip();

            if (trip == null) {
                if (tvPickup != null) tvPickup.setText("Điểm đi: Không xác định");
                if (tvDestination != null) tvDestination.setText("Điểm đến: Không xác định");
                if (tvDepartureTime != null) tvDepartureTime.setText("Thời gian: --/--");
                if (tvPrice != null) tvPrice.setText("0 VNĐ");
                if (tvStatus != null) tvStatus.setText("Không khả dụng");
                itemView.setAlpha(0.5f);
                itemView.setOnClickListener(null);
                return;
            }

            itemView.setAlpha(1.0f);

            if (tvPickup != null) {
                tvPickup.setText(trip.getOrigin() != null ? "Điểm đi: " + trip.getOrigin().getName() : "Điểm đi chưa rõ");
            }

            if (tvDestination != null) {
                tvDestination.setText(trip.getDestination() != null ? "Điểm đến: " + trip.getDestination().getName() : "Điểm đến chưa rõ");
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
                tvPrice.setText(String.format("%,d VNĐ", trip.getCostVnd()));
            }

            String effectiveStatus = resolveEffectiveStatus(request, trip);
            if (tvStatus != null) {
                tvStatus.setText(getVietnameseStatusText(effectiveStatus));
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onRideClick(item);
                    }
                }
            });
        }

        /**
         * Quy tắc ghép trạng thái chuẩn theo hợp đồng review:
         * 1. Request REJECTED hoặc CANCELLED giữ trạng thái của chính request (dù trip có COMPLETED hay CANCELLED).
         * 2. Trip CANCELLED ưu tiên hiển thị CANCELLED cho các request khác.
         * 3. Request ACCEPTED + Trip COMPLETED -> COMPLETED (Hoàn thành).
         * 4. Request ACCEPTED + Trip FULL/OPEN -> ACCEPTED (Sắp đi).
         */
        public static String resolveEffectiveStatus(TripRequest request, Trip trip) {
            if (request == null) {
                return "UNKNOWN";
            }
            String reqStatus = request.getStatus() != null ? request.getStatus().toUpperCase() : "UNKNOWN";

            if ("REJECTED".equals(reqStatus) || "CANCELLED".equals(reqStatus)) {
                return reqStatus;
            }

            if (trip != null && "CANCELLED".equalsIgnoreCase(trip.getStatus())) {
                return "CANCELLED";
            }

            if ("ACCEPTED".equals(reqStatus)) {
                if (trip != null && "COMPLETED".equalsIgnoreCase(trip.getStatus())) {
                    return "COMPLETED";
                }
                return "ACCEPTED";
            }

            return reqStatus;
        }

        private String getVietnameseStatusText(String status) {
            if ("PENDING".equals(status)) return "Chờ xác nhận";
            if ("ACCEPTED".equals(status)) return "Sắp đi";
            if ("REJECTED".equals(status)) return "Bị từ chối";
            if ("COMPLETED".equals(status)) return "Hoàn thành";
            if ("CANCELLED".equals(status)) return "Đã hủy";
            return "Không xác định";
        }
    }
}
