package com.example.grabshare.adapter.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.repository.search.TripSearchRepository.TripWithProfile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách chuyến xe phù hợp.
 */
public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private final List<TripWithProfile> tripList = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final OnTripClickListener listener;

    /**
     * Interface để xử lý sự kiện click vào một chuyến xe.
     */
    public interface OnTripClickListener {
        void onTripClick(TripWithProfile tripWithProfile);
    }

    public TripAdapter(OnTripClickListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật danh sách chuyến xe.
     */
    public void setTrips(List<TripWithProfile> trips) {
        tripList.clear();
        if (trips != null) {
            tripList.addAll(trips);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_ride, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        final TripWithProfile item = tripList.get(position);

        if (item.profile != null) {
            holder.driverName.setText(item.profile.getDisplayName() != null ? item.profile.getDisplayName() : "Tài xế ẩn danh");
            holder.driverMeta.setText(item.profile.getVehicleName() != null ? item.profile.getVehicleName() : "Không rõ xe");
        } else {
            holder.driverName.setText("Đang tải...");
            holder.driverMeta.setText("");
        }

        if (item.trip != null) {
            if (item.trip.getDepartureAt() != null) {
                holder.rideTime.setText(timeFormat.format(item.trip.getDepartureAt().toDate()));
            } else {
                holder.rideTime.setText("--:--");
            }

            long seats = item.trip.getAvailableSeats() != null ? item.trip.getAvailableSeats() : 0;
            holder.rideSeats.setText(seats + " chỗ");

            long cost = item.trip.getCostVnd() != null ? item.trip.getCostVnd() : 0;
            if (cost == 0) {
                holder.ridePrice.setText("Miễn phí");
            } else {
                holder.ridePrice.setText(String.format(Locale.getDefault(), "%,dđ", cost));
            }

            if (item.trip.getOrigin() != null) {
                holder.ridePickup.setText("Đón tại " + item.trip.getOrigin().getName());
            } else {
                holder.ridePickup.setText("Không rõ điểm đón");
            }
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onTripClick(item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return tripList.size();
    }

    /**
     * ViewHolder chứa các thành phần giao diện của một dòng chuyến xe.
     */
    static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView driverName;
        TextView driverMeta;
        TextView rideTime;
        TextView rideSeats;
        TextView ridePrice;
        TextView ridePickup;
        ImageView driverAvatar;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            driverName = itemView.findViewById(R.id.driver_name);
            driverMeta = itemView.findViewById(R.id.driver_meta);
            rideTime = itemView.findViewById(R.id.ride_time);
            rideSeats = itemView.findViewById(R.id.ride_seats);
            ridePrice = itemView.findViewById(R.id.ride_price);
            ridePickup = itemView.findViewById(R.id.ride_pickup);
            driverAvatar = itemView.findViewById(R.id.driver_avatar);
        }
    }
}