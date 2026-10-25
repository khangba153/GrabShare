package com.example.grabshare.adapter.driver;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.grabshare.R;
import com.example.grabshare.presentation.TripCardText;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hiển thị danh sách chuyến của chủ xe.
 */
public class DriverTripAdapter extends BaseAdapter {

    private final LayoutInflater layoutInflater;
    private final List<Trip> trips;

    /**
     * Khởi tạo adapter dùng chung cho danh sách chuyến.
     *
     * @param context ngữ cảnh hiển thị giao diện
     */
    public DriverTripAdapter(Context context) {
        layoutInflater = LayoutInflater.from(context);
        trips = new ArrayList<>();
    }

    /**
     * Thay thế danh sách chuyến và cập nhật giao diện.
     *
     * @param updatedTrips danh sách chuyến mới lấy từ repository
     */
    public void replaceTrips(List<Trip> updatedTrips) {
        trips.clear();

        if (updatedTrips != null) {
            trips.addAll(updatedTrips);
        }

        notifyDataSetChanged();
    }

    /**
     * Trả về số chuyến đang được hiển thị.
     *
     * @return số lượng chuyến
     */
    @Override
    public int getCount() {
        return trips.size();
    }

    /**
     * Lấy chuyến theo vị trí trong danh sách.
     *
     * @param position vị trí của chuyến
     * @return dữ liệu chuyến
     */
    @Override
    public Trip getItem(int position) {
        return trips.get(position);
    }

    /**
     * Trả về mã vị trí của mục trong adapter.
     *
     * @param position vị trí của chuyến
     * @return vị trí của chuyến
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Tạo hoặc tái sử dụng giao diện một chuyến xe.
     *
     * @param position vị trí chuyến cần hiển thị
     * @param convertView giao diện của mục trước đó nếu có thể tái sử dụng
     * @param parent danh sách chứa mục
     * @return giao diện đã gắn dữ liệu chuyến
     */
    @Override
    public View getView(
            int position,
            View convertView,
            ViewGroup parent
    ) {
        View tripItemView = convertView;
        if (tripItemView == null) {
            tripItemView = layoutInflater.inflate(R.layout.item_driver_trip, parent, false);
        }

        Trip currentTrip = getItem(position);

        TextView originText = tripItemView.findViewById(
                R.id.trip_origin
        );

        TextView destinationText = tripItemView.findViewById(
                R.id.trip_destination
        );

        TextView departureTimeText = tripItemView.findViewById(
                R.id.trip_departure_time
        );

        TextView costText = tripItemView.findViewById(
                R.id.trip_cost
        );

        TextView statusText = tripItemView.findViewById(
                R.id.trip_status
        );

        originText.setText(
                "Đi: " + TripCardText.place(currentTrip.getOrigin())
        );

        destinationText.setText(
                "Đến: " + TripCardText.place(currentTrip.getDestination())
        );

        departureTimeText.setText(
                formatDepartureTime(currentTrip.getDepartureAt())
        );

        costText.setText(
                "Toàn tuyến: " + formatContributionAmount(currentTrip.getCostVnd())
        );

        if (TripCardText.pastDeparture(currentTrip)
                && ("OPEN".equals(currentTrip.getStatus()) || "FULL".equals(currentTrip.getStatus()))) {
            statusText.setText("Đã qua giờ · Chưa kết thúc");
        } else {
            statusText.setText(formatTripStatus(currentTrip.getStatus()));
        }

        return tripItemView;
    }

    /**
     * Lấy tên địa điểm hoặc thông báo khi dữ liệu chưa có.
     *
     * @param place địa điểm cần hiển thị
     * @return tên địa điểm
     */
    private String getPlaceName(Place place) {
        if (place == null
                || place.getName() == null
                || place.getName().trim().isEmpty()) {
            return "Chưa có thông tin địa điểm";
        }

        return place.getName();
    }

    /**
     * Định dạng thời gian khởi hành lấy từ Firestore.
     *
     * @param departureTimestamp thời gian khởi hành
     * @return thời gian theo định dạng ngày/tháng/năm và giờ/phút
     */
    private String formatDepartureTime(
            Timestamp departureTimestamp
    ) {
        if (departureTimestamp == null) {
            return "Chưa có thời gian khởi hành";
        }

        SimpleDateFormat dateFormatter = new SimpleDateFormat(
                "dd/MM/yyyy · HH:mm",
                Locale.forLanguageTag("vi-VN")
        );

        return dateFormatter.format(
                departureTimestamp.toDate()
        );
    }

    /**
     * Định dạng chi phí đóng góp bằng tiền Việt Nam.
     *
     * @param contributionAmount số tiền đóng góp
     * @return chi phí đã định dạng
     */
    private String formatContributionAmount(
            Long contributionAmount
    ) {
        if (contributionAmount == null) {
            return "Chưa có chi phí";
        }

        if (contributionAmount == 0L) {
            return "Miễn phí";
        }

        NumberFormat numberFormatter = NumberFormat.getIntegerInstance(
                Locale.forLanguageTag("vi-VN")
        );

        return numberFormatter.format(contributionAmount) + " đ";
    }

    /**
     * Chuyển trạng thái chuyến thành nội dung dễ đọc.
     *
     * @param tripStatus trạng thái được lưu trong Firestore
     * @return tên trạng thái để hiển thị trên giao diện
     */
    private String formatTripStatus(String tripStatus) {
        if (tripStatus == null || tripStatus.trim().isEmpty()) {
            return "Chưa có trạng thái";
        }

        String normalizedStatus = tripStatus.trim()
                .toUpperCase(Locale.ROOT);

        switch (normalizedStatus) {
            case "OPEN":
                return "Đang mở";

            case "FULL":
                return "Đã đủ người";

            case "COMPLETED":
                return "Đã hoàn thành";

            case "CANCELLED":
                return "Đã hủy";

            default:
                return "Trạng thái: " + tripStatus;
        }
    }
}
