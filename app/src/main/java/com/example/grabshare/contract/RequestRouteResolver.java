package com.example.grabshare.contract;

import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;

/** Đọc điểm xuống thống nhất cho màn hành khách, tài xế và kiểm tra trước Accept. */
public final class RequestRouteResolver {

    /** Chỉ cung cấp hàm dùng chung, không giữ phiên hoặc dữ liệu giao diện. */
    private RequestRouteResolver() {
    }

    /**
     * Yêu cầu cũ thiếu hẳn dropoff dùng D của đúng chuyến liên quan.
     * Field đã có nhưng null/sai phải báo lỗi, không che dữ liệu lỗi bằng D.
     * Caller phải gán ID từ DocumentSnapshot và đọc server trước khi mutation.
     * Hàm này không kiểm tra quyền UID, state hoặc cấp quyền gửi/nhận yêu cầu.
     */
    public static Place resolveDropoff(TripRequest tripRequest, Trip trip) {
        if (tripRequest == null || trip == null) {
            throw new IllegalArgumentException("Thiếu yêu cầu hoặc dữ liệu chuyến.");
        }
        if (tripRequest.getTripId() == null
                || trip.getId() == null
                || tripRequest.getTripId().trim().isEmpty()
                || !tripRequest.getTripId().equals(trip.getId())
                || tripRequest.getDriverId() == null
                || trip.getDriverId() == null
                || tripRequest.getDriverId().trim().isEmpty()
                || !tripRequest.getDriverId().equals(trip.getDriverId())) {
            throw new IllegalArgumentException("Yêu cầu không thuộc chuyến đang đọc.");
        }

        Place effectiveDropoff;
        if (tripRequest.hasDropoffField()) {
            effectiveDropoff = tripRequest.getDropoff();
        } else {
            effectiveDropoff = trip.getDestination();
        }
        if (!validPlace(effectiveDropoff)) {
            throw new IllegalArgumentException("Điểm xuống không có dữ liệu hợp lệ.");
        }

        // Trả bản sao để màn hiển thị không sửa ngược snapshot dùng kiểm tra route.
        return new Place(
                effectiveDropoff.getName(),
                effectiveDropoff.getLatitude(),
                effectiveDropoff.getLongitude()
        );
    }

    /** Áp cùng giới hạn Place của Rules, không thay tọa độ thiếu bằng (0,0). */
    private static boolean validPlace(Place place) {
        if (place == null
                || place.getName() == null
                || place.getName().trim().isEmpty()
                || place.getName().length() > 200
                || place.getLatitude() == null
                || place.getLongitude() == null) {
            return false;
        }

        double latitude = place.getLatitude();
        double longitude = place.getLongitude();
        return !Double.isNaN(latitude)
                && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude)
                && !Double.isInfinite(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }
}
