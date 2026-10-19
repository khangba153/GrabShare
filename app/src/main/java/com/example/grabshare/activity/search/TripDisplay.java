package com.example.grabshare.activity.search;

import android.app.Activity;
import android.widget.TextView;
import com.example.grabshare.R;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.TripSearchRepository.DriverInfo;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Định dạng dữ liệu thật cho hai layout riêng của Thành. */
final class TripDisplay {
    /** Không khởi tạo helper chỉ chứa hàm tĩnh. */
    private TripDisplay() { }
    /** Không dùng tên/giá/rating mẫu từ XML. */
    static void bind(Activity activity, Trip trip, DriverInfo driver) {
        text(activity, R.id.route_start, trip.getOrigin().getName());
        text(activity, R.id.route_end, trip.getDestination().getName());
        text(activity, R.id.trip_time, new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
                .format(trip.getDepartureAt().toDate()));
        text(activity, R.id.trip_seats, trip.getAvailableSeats() + " chỗ trống");
        text(activity, R.id.search_trip_state, status(trip.getStatus()));
        text(activity, R.id.search_trip_note, trip.getNote() == null || trip.getNote().trim().isEmpty()
                ? "Chủ xe chưa có ghi chú." : trip.getNote());
        String cost = trip.getCostVnd() == 0 ? "Miễn phí"
                : NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(trip.getCostVnd()) + "đ";
        text(activity, R.id.search_trip_cost, cost);
        text(activity, R.id.driver_name, driver == null ? "Đang tải hồ sơ…"
                : driver.profile == null || driver.profile.getDisplayName() == null
                || driver.profile.getDisplayName().trim().isEmpty() ? "Chưa có tên công khai"
                : driver.profile.getDisplayName());
        text(activity, R.id.driver_meta, driver == null || driver.profile == null
                || driver.profile.getVehicleName() == null || driver.profile.getVehicleName().trim().isEmpty()
                ? "Chưa có thông tin xe công khai" : driver.profile.getVehicleName());
        String rating = "Đang tải đánh giá…";
        if (driver != null) {
            rating = !driver.ratingsLoaded ? "Chưa tải được đánh giá"
                    : driver.ratingCount == 0 ? "Chưa có đánh giá"
                    : String.format(Locale.getDefault(), "%.1f/5 · %d đánh giá", driver.average, driver.ratingCount);
        }
        text(activity, R.id.search_driver_rating, rating);
    }
    /** Đổi state thành nhãn tiếng Việt, giữ nguyên dữ liệu nghiệp vụ. */
    private static String status(String value) {
        if ("OPEN".equals(value)) return "Đang nhận yêu cầu";
        if ("FULL".equals(value)) return "Đã đủ người";
        if ("CANCELLED".equals(value)) return "Đã hủy";
        return "Đã hoàn thành";
    }
    /** Gắn nội dung vào layout của Thành. */
    private static void text(Activity activity, int id, String value) {
        ((TextView) activity.findViewById(id)).setText(value);
    }
}
