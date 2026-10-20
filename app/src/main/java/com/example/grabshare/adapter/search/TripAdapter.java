package com.example.grabshare.adapter.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.activity.search.RouteDistanceFormatter;
import com.example.grabshare.repository.search.EnRouteSearch.Match;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Hiển thị giá dự kiến, đường bộ A/B và mức đi thêm đã được kiểm tra. */
public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private final List<Match> trips = new ArrayList<Match>();

    private final OnTripClickListener listener;

    private final SimpleDateFormat departureTimeFormatter =
            new SimpleDateFormat("dd/MM · HH:mm", new Locale("vi", "VN"));

    /** Chọn chuyến bằng kết quả riêng, không đưa metric vào model Firestore. */
    public interface OnTripClickListener {

        /** Chuyển sự kiện về Activity để mở Detail theo ID. */
        void onTripClick(Match match);
    }

    /** Giữ callback của màn và sử dụng giờ Việt Nam khi trình bày. */
    public TripAdapter(OnTripClickListener listener) {
        this.listener = listener;
        departureTimeFormatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    /** Sao chép snapshot đã sắp xếp; không thay đổi hàng đợi của ViewModel. */
    public void setTrips(List<Match> values) {
        trips.clear();
        trips.addAll(values);
        notifyDataSetChanged();
    }

    /** Dùng layout riêng của tìm dọc tuyến, không sửa row chung của các gói khác. */
    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TripViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.row_en_route_match, parent, false));
    }

    /** Gắn toàn bộ dữ liệu để không sót nhãn của view được tái sử dụng. */
    @Override
    public void onBindViewHolder(@NonNull TripViewHolder tripViewHolder, int position) {
        final Match matchedTrip = trips.get(position);
        if (matchedTrip.profile == null || matchedTrip.profile.getDisplayName() == null) {
            tripViewHolder.driverNameTextView.setText("Chưa tải được hồ sơ tài xế");
        } else {
            tripViewHolder.driverNameTextView.setText(matchedTrip.profile.getDisplayName());
        }
        if (matchedTrip.profile == null || matchedTrip.profile.getVehicleName() == null) {
            tripViewHolder.vehicleNameTextView.setText("Chưa có thông tin xe");
        } else {
            tripViewHolder.vehicleNameTextView.setText(matchedTrip.profile.getVehicleName());
        }
        tripViewHolder.departureTimeTextView.setText(
                "Tài xế khởi hành: "
                        + departureTimeFormatter.format(
                                matchedTrip.trip.getDepartureAt().toDate()));
        if (matchedTrip.trip.getCostVnd() == 0) {
            tripViewHolder.priceTextView.setText("Miễn phí");
        } else {
            tripViewHolder.priceTextView.setText(
                    String.format(
                            new Locale("vi", "VN"),
                            "Giá dự kiến: %,dđ",
                            matchedTrip.trip.getCostVnd()));
        }
        tripViewHolder.driverRouteTextView.setText(
                "Tuyến tài xế: "
                        + matchedTrip.trip.getOrigin().getName()
                        + " → "
                        + matchedTrip.trip.getDestination().getName());
        tripViewHolder.startGapTextView.setText(
                "Từ điểm xuất phát tài xế đến điểm đón bạn: "
                        + RouteDistanceFormatter.formatKilometers(matchedTrip.startGapMeters)
                        + " đường bộ");
        tripViewHolder.detourTextView.setText(
                "Tài xế đi thêm: +"
                        + RouteDistanceFormatter.formatKilometers(matchedTrip.detourMeters));
        if (matchedTrip.baselineAdjusted) {
            tripViewHolder.informationTextView.setText(
                    "Còn 1 chỗ · Giờ trên là giờ đi của tài xế, chưa phải giờ đón bạn."
                            + (" Khoảng cách được cập nhật theo đường đi hiện tại."));
        } else {
            tripViewHolder.informationTextView.setText(
                    "Còn 1 chỗ · Giờ trên là giờ đi của tài xế, chưa phải giờ đón bạn." + (""));
        }
        tripViewHolder.itemView.setOnClickListener(
                new View.OnClickListener() {

                    /** Giữ đúng dữ liệu của dòng hiện tại khi bấm. */
                    @Override
                    public void onClick(View view) {
                        listener.onTripClick(matchedTrip);
                    }
                });
    }

    /** Số kết quả sau lọc trên RAM. */
    @Override
    public int getItemCount() {
        return trips.size();
    }

    /** Giữ các view của một thẻ ghép dọc tuyến. */
    static final class TripViewHolder extends RecyclerView.ViewHolder {

        final TextView driverNameTextView;

        final TextView vehicleNameTextView;

        final TextView departureTimeTextView;

        final TextView priceTextView;

        final TextView driverRouteTextView;

        final TextView startGapTextView;

        final TextView detourTextView;

        final TextView informationTextView;

        /** Ánh xạ layout riêng của Thành. */
        TripViewHolder(View view) {
            super(view);
            driverNameTextView = view.findViewById(R.id.match_driver);
            vehicleNameTextView = view.findViewById(R.id.match_vehicle);
            departureTimeTextView = view.findViewById(R.id.match_time);
            priceTextView = view.findViewById(R.id.match_price);
            driverRouteTextView = view.findViewById(R.id.match_route);
            startGapTextView = view.findViewById(R.id.match_gap);
            detourTextView = view.findViewById(R.id.match_detour);
            informationTextView = view.findViewById(R.id.match_note);
        }
    }
}
