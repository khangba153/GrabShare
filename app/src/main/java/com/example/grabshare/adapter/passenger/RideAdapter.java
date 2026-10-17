package com.example.grabshare.adapter.passenger;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        int layoutId = context.getResources().getIdentifier("row_ride", "layout", context.getPackageName());
        if (layoutId == 0) {
            layoutId = context.getResources().getIdentifier("item_trip", "layout", context.getPackageName());
        }
        View view = LayoutInflater.from(context).inflate(layoutId != 0 ? layoutId : android.R.layout.simple_list_item_2, parent, false);
        return new RideViewHolder(view, context.getPackageName());
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

        private final TextView tvOrigin;
        private final TextView tvDestination;
        private final TextView tvDepartureTime;
        private final TextView tvCost;
        private final TextView tvStatus;

        public RideViewHolder(@NonNull View itemView, String packageName) {
            super(itemView);
            tvOrigin = (TextView) findViewByIdName(itemView, "tv_origin", packageName);
            tvDestination = (TextView) findViewByIdName(itemView, "tv_destination", packageName);
            tvDepartureTime = (TextView) findViewByIdName(itemView, "tv_departure_time", packageName);
            tvCost = (TextView) findViewByIdName(itemView, "tv_cost", packageName);
            tvStatus = (TextView) findViewByIdName(itemView, "tv_status", packageName);
        }

        private View findViewByIdName(View parent, String idName, String packageName) {
            int id = parent.getResources().getIdentifier(idName, "id", packageName);
            return id != 0 ? parent.findViewById(id) : null;
        }

        public void bind(final PassengerRideItem item, final OnRideClickListener listener) {
            TripRequest request = item.getRequest();
            Trip trip = item.getTrip();

            if (trip == null) {
                if (tvOrigin != null) tvOrigin.setText("Điểm đi: Không xác định");
                if (tvDestination != null) tvDestination.setText("Điểm đến: Không xác định");
                if (tvDepartureTime != null) tvDepartureTime.setText("Thời gian: --/--");
                if (tvCost != null) tvCost.setText("0 VNĐ");
                if (tvStatus != null) tvStatus.setText("Không khả dụng");
                itemView.setAlpha(0.5f);
                itemView.setOnClickListener(null);
                return;
            }

            itemView.setAlpha(1.0f);

            if (tvOrigin != null) {
                tvOrigin.setText(trip.getOrigin() != null ? trip.getOrigin().getName() : "Điểm đi chưa rõ");
            }

            if (tvDestination != null) {
                tvDestination.setText(trip.getDestination() != null ? trip.getDestination().getName() : "Điểm đến chưa rõ");
            }

            if (tvDepartureTime != null) {
                if (trip.getDepartureAt() != null) {
                    CharSequence timeFormatted = DateFormat.format("HH:mm - dd/MM/yyyy", trip.getDepartureAt().toDate());
                    tvDepartureTime.setText(timeFormatted);
                } else {
                    tvDepartureTime.setText("Không rõ thời gian");
                }
            }

            if (tvCost != null) {
                tvCost.setText(String.format("%,d VNĐ", trip.getCostVnd()));
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

        private String resolveEffectiveStatus(TripRequest request, Trip trip) {
            if ("CANCELLED".equalsIgnoreCase(trip.getStatus())) {
                return "CANCELLED";
            }
            if (request != null && request.getStatus() != null) {
                return request.getStatus().toUpperCase();
            }
            return "UNKNOWN";
        }

        private String getVietnameseStatusText(String status) {
            if ("PENDING".equals(status)) return "Chờ xác nhận";
            if ("ACCEPTED".equals(status)) return "Đã nhận (Sắp đi)";
            if ("REJECTED".equals(status)) return "Bị từ chối";
            if ("COMPLETED".equals(status)) return "Hoàn thành";
            if ("CANCELLED".equals(status)) return "Đã hủy";
            return "Không xác định";
        }
    }
}