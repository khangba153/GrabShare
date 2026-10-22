package com.example.grabshare.activity.search;

import android.app.Activity;
import android.widget.TextView;

import com.example.grabshare.R;
import com.example.grabshare.contract.CachedTrip;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.TripSearchRepository.DriverInfo;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Định dạng dữ liệu thật cho hai layout riêng của Thành. */
final class TripDisplay {

    /** Không khởi tạo helper chỉ chứa hàm tĩnh. */
    private TripDisplay() {}

    /** Cache không có rating; không tạo điểm sao giả hoặc trạng thái đang tải vô hạn. */
    static void bindCached(Activity activity, CachedTrip cachedTrip) {
        DriverInfo driver = new DriverInfo();
        driver.profile = cachedTrip.getProfile();
        bind(activity, cachedTrip.getTrip(), driver);
        text(activity, R.id.search_driver_rating, "Đánh giá chưa được lưu offline");
        if (driver.profile == null) {
            text(activity, R.id.driver_name, "Chưa có hồ sơ trong bản lưu");
        }
    }

    /** Không dùng tên/giá/rating mẫu từ XML. */
    static void bind(Activity activity, Trip trip, DriverInfo driver) {
        text(activity, R.id.route_start, trip.getOrigin().getName());
        text(activity, R.id.route_end, trip.getDestination().getName());
        text(
                activity,
                R.id.trip_time,
                new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
                        .format(trip.getDepartureAt().toDate()));
        text(activity, R.id.trip_seats, trip.getAvailableSeats() + " chỗ trống");
        text(activity, R.id.search_trip_state, status(trip.getStatus()));
        if (trip.getNote() == null || trip.getNote().trim().isEmpty()) {
            text(activity, R.id.search_trip_note, "Chủ xe chưa có ghi chú.");
        } else {
            text(activity, R.id.search_trip_note, trip.getNote());
        }
        String cost;
        if (trip.getCostVnd() == 0) {
            cost = "Miễn phí";
        } else {
            cost =
                    NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                                    .format(trip.getCostVnd())
                            + "đ";
        }
        text(activity, R.id.search_trip_cost, cost);
        if (driver == null) {
            text(activity, R.id.driver_name, "Đang tải hồ sơ…");
        } else {
            if (driver.profile == null
                    || driver.profile.getDisplayName() == null
                    || driver.profile.getDisplayName().trim().isEmpty()) {
                text(activity, R.id.driver_name, "Chưa có tên công khai");
            } else {
                text(activity, R.id.driver_name, driver.profile.getDisplayName());
            }
        }
        if (driver == null
                || driver.profile == null
                || driver.profile.getVehicleName() == null
                || driver.profile.getVehicleName().trim().isEmpty()) {
            text(activity, R.id.driver_meta, "Chưa có thông tin xe công khai");
        } else {
            text(activity, R.id.driver_meta, driver.profile.getVehicleName());
        }
        String rating = "Đang tải đánh giá…";
        if (driver != null) {
            if (!driver.ratingsLoaded) {
                rating = "Chưa tải được đánh giá";
            } else {
                if (driver.ratingCount == 0) {
                    rating = "Chưa có đánh giá";
                } else {
                    rating =
                            String.format(
                                    Locale.getDefault(),
                                    "%.1f/5 · %d đánh giá",
                                    driver.average,
                                    driver.ratingCount);
                }
            }
        }
        text(activity, R.id.search_driver_rating, rating);
    }

    /** Đổi state thành nhãn tiếng Việt, giữ nguyên dữ liệu nghiệp vụ. */
    private static String status(String value) {
        if ("OPEN".equals(value)) {
            return "Đang nhận yêu cầu";
        }
        if ("FULL".equals(value)) {
            return "Đã đủ người";
        }
        if ("CANCELLED".equals(value)) {
            return "Đã hủy";
        }
        return "Đã hoàn thành";
    }

    /** Gắn nội dung vào layout của Thành. */
    private static void text(Activity activity, int viewIdentifier, String value) {
        ((TextView) activity.findViewById(viewIdentifier)).setText(value);
    }
}
