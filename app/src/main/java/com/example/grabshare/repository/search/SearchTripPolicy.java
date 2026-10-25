package com.example.grabshare.repository.search;

import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;

/** Kiểm tra đầu vào dùng trong phần Detail và Review của Thành. */
public final class SearchTripPolicy {
    /** Không tạo đối tượng chứa các hàm kiểm tra tĩnh. */
    private SearchTripPolicy() {}

    /** Kiểm tra khóa document đơn, không chấp nhận đường dẫn collection. */
    public static boolean validId(String value) {
        return value != null && !value.trim().isEmpty() && !value.contains("/");
    }

    /** Kiểm tra địa điểm theo giới hạn của Rules, không thay dữ liệu thiếu bằng (0,0). */
    public static boolean validPlace(Place place) {
        return place != null
                && place.getName() != null
                && !place.getName().trim().isEmpty()
                && place.getName().length() <= 200
                && place.getLatitude() != null
                && place.getLongitude() != null
                && Double.isFinite(place.getLatitude())
                && Double.isFinite(place.getLongitude())
                && place.getLatitude() >= -90
                && place.getLatitude() <= 90
                && place.getLongitude() >= -180
                && place.getLongitude() <= 180;
    }

    /** Chỉ cho hiển thị hành động trên chuyến có đủ dữ liệu nghiệp vụ. */
    public static boolean validTrip(Trip trip) {
        return trip != null
                && validId(trip.getId())
                && validId(trip.getDriverId())
                && validPlace(trip.getOrigin())
                && validPlace(trip.getDestination())
                && trip.getDepartureAt() != null
                && trip.getCostVnd() != null
                && trip.getCostVnd() >= 0
                && trip.getAvailableSeats() != null
                && trip.getAcceptedRequestId() != null
                && (trip.getOrigin().getLatitude().doubleValue()
                                != trip.getDestination().getLatitude().doubleValue()
                        || trip.getOrigin().getLongitude().doubleValue()
                                != trip.getDestination().getLongitude().doubleValue())
                && (("OPEN".equals(trip.getStatus())
                                && trip.getAvailableSeats() == 1L
                                && "".equals(trip.getAcceptedRequestId()))
                        || (("FULL".equals(trip.getStatus())
                                        || "COMPLETED".equals(trip.getStatus()))
                                && trip.getAvailableSeats() == 0L
                                && validId(trip.getAcceptedRequestId()))
                        || ("CANCELLED".equals(trip.getStatus())
                                && trip.getAvailableSeats() == 0L
                                && ("".equals(trip.getAcceptedRequestId())
                                        || validId(trip.getAcceptedRequestId()))));
    }

    /** Trả lý do chặn gửi; chuỗi rỗng nghĩa là có thể kiểm tra tiếp trong transaction. */
    public static String blockedReason(Trip trip, String userId) {
        if (!validId(userId)) {
            return "Bạn cần đăng nhập.";
        }
        if (!validTrip(trip)) {
            return "Dữ liệu chuyến không hợp lệ.";
        }
        if (userId.equals(trip.getDriverId())) {
            return "Bạn không thể đặt chuyến của mình.";
        }
        if (!"OPEN".equals(trip.getStatus())
                || trip.getAvailableSeats() != 1L
                || !"".equals(trip.getAcceptedRequestId())) {
            return "Chuyến không còn mở để nhận yêu cầu.";
        }
        if (trip.getDepartureAt().compareTo(Timestamp.now()) <= 0) {
            return "Chuyến đã đến giờ khởi hành.";
        }
        return "";
    }
}
