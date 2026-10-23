package com.example.grabshare.activity.passenger;

import android.widget.TextView;

import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;

/** Hiển thị P/C của khách riêng với A/D của tài xế, kể cả request legacy. */
public final class PassengerRouteText {
    /** Chỉ chứa hàm định dạng, không lưu dữ liệu phiên hoặc chuyến. */
    private PassengerRouteText() {
    }

    /** C của request cũ thiếu field dùng D qua resolver chung; C sai hiện lỗi rõ. */
    public static String format(TripRequest request, Trip trip) {
        if (request == null || trip == null) return "Điểm khách: Chưa có dữ liệu chuyến.";
        Place pickup = request.getPickup();
        if (!validPickup(pickup)) {
            return "Điểm khách: Điểm đón không hợp lệ.";
        }
        try {
            Place dropoff = RequestRouteResolver.resolveDropoff(request, trip);
            return "Khách: " + pickup.getName().trim() + " → " + dropoff.getName().trim();
        } catch (IllegalArgumentException error) {
            return "Điểm khách: Điểm xuống không hợp lệ; hãy tải lại dữ liệu.";
        }
    }

    /** Không hiển thị P có tên nhưng tọa độ thiếu hoặc nằm ngoài miền hợp lệ. */
    private static boolean validPickup(Place pickup) {
        if (pickup == null || pickup.getName() == null || pickup.getName().trim().isEmpty()
                || pickup.getName().length() > 200
                || pickup.getLatitude() == null || pickup.getLongitude() == null) return false;
        double latitude = pickup.getLatitude();
        double longitude = pickup.getLongitude();
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    /** Gắn nội dung vào phần dành riêng cho P/C, không ghi đè View A/D. */
    public static void bind(TextView view, TripRequest request, Trip trip) {
        if (view != null) view.setText(format(request, trip));
    }
}
