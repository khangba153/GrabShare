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
    private final List<Match> trips = new ArrayList<>();
    private final OnTripClickListener listener;
    private final SimpleDateFormat time = new SimpleDateFormat("dd/MM · HH:mm", new Locale("vi", "VN"));
    /** Chọn chuyến bằng kết quả riêng, không đưa metric vào model Firestore. */
    public interface OnTripClickListener {
        /** Chuyển sự kiện về Activity để mở Detail theo ID. */
        void onTripClick(Match match);
    }
    /** Giữ callback của màn và sử dụng giờ Việt Nam khi trình bày. */
    public TripAdapter(OnTripClickListener listener) {
        this.listener = listener;
        time.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }
    /** Sao chép snapshot đã sắp xếp; không thay đổi hàng đợi của ViewModel. */
    public void setTrips(List<Match> values) {
        trips.clear();
        trips.addAll(values);
        notifyDataSetChanged();
    }
    /** Dùng layout riêng của tìm dọc tuyến, không sửa row chung của các gói khác. */
    @NonNull @Override public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new TripViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_en_route_match, parent, false));
    }
    /** Gắn toàn bộ dữ liệu để không sót nhãn của view được tái sử dụng. */
    @Override public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        final Match item = trips.get(position);
        holder.name.setText(item.profile == null || item.profile.getDisplayName() == null
                ? "Chưa tải được hồ sơ tài xế" : item.profile.getDisplayName());
        holder.vehicle.setText(item.profile == null || item.profile.getVehicleName() == null
                ? "Chưa có thông tin xe" : item.profile.getVehicleName());
        holder.time.setText("Tài xế khởi hành: " + time.format(item.trip.getDepartureAt().toDate()));
        holder.price.setText(item.trip.getCostVnd() == 0 ? "Miễn phí" : String.format(new Locale("vi", "VN"),
                "Giá dự kiến: %,dđ", item.trip.getCostVnd()));
        holder.route.setText("Tuyến tài xế: " + item.trip.getOrigin().getName() + " → " + item.trip.getDestination().getName());
        holder.gap.setText("Từ điểm xuất phát tài xế đến điểm đón bạn: "
                + RouteDistanceFormatter.formatKilometers(item.startGapMeters) + " đường bộ");
        holder.detour.setText("Tài xế đi thêm: +" + RouteDistanceFormatter.formatKilometers(item.detourMeters));
        holder.note.setText("Còn 1 chỗ · Giờ trên là giờ đi của tài xế, chưa phải giờ đón bạn."
                + (item.baselineAdjusted ? " Khoảng cách được cập nhật theo đường đi hiện tại." : ""));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            /** Giữ đúng dữ liệu của dòng hiện tại khi bấm. */
            @Override public void onClick(View view) { listener.onTripClick(item); }
        });
    }
    /** Số kết quả sau lọc trên RAM. */
    @Override public int getItemCount() { return trips.size(); }
    /** Giữ các view của một thẻ ghép dọc tuyến. */
    static final class TripViewHolder extends RecyclerView.ViewHolder {
        final TextView name, vehicle, time, price, route, gap, detour, note;
        /** Ánh xạ layout riêng của Thành. */
        TripViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.match_driver);
            vehicle = view.findViewById(R.id.match_vehicle);
            time = view.findViewById(R.id.match_time);
            price = view.findViewById(R.id.match_price);
            route = view.findViewById(R.id.match_route);
            gap = view.findViewById(R.id.match_gap);
            detour = view.findViewById(R.id.match_detour);
            note = view.findViewById(R.id.match_note);
        }
    }
}
