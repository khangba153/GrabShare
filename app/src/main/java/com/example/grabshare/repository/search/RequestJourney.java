package com.example.grabshare.repository.search;

import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Giữ P/C bất biến từ lúc kiểm tra đường đến khi ghi request hoặc phục hồi từ server. */
public final class RequestJourney {
    private final String pickupName;
    private final double pickupLatitude;
    private final double pickupLongitude;
    private final String dropoffName;
    private final double dropoffLatitude;
    private final double dropoffLongitude;

    /** Chỉ sao chép giá trị, không giữ Place có thể bị form sửa trong khi đang gọi API. */
    private RequestJourney(Place pickup, Place dropoff) {
        if (!SearchTripPolicy.validPlace(pickup) || !SearchTripPolicy.validPlace(dropoff)) {
            throw new IllegalArgumentException("Vui lòng chọn đủ điểm đón và điểm xuống hợp lệ.");
        }
        pickupName = pickup.getName();
        pickupLatitude = pickup.getLatitude();
        pickupLongitude = pickup.getLongitude();
        dropoffName = dropoff.getName();
        dropoffLatitude = dropoff.getLatitude();
        dropoffLongitude = dropoff.getLongitude();
    }

    /** Yêu cầu mới bắt buộc đủ P/C khác tọa độ, kể cả khi hai địa điểm có tên khác nhau. */
    public static RequestJourney forNewRequest(Place pickup, Place dropoff) {
        RequestJourney journey = new RequestJourney(pickup, dropoff);
        if (journey.pickupLatitude == journey.dropoffLatitude
                && journey.pickupLongitude == journey.dropoffLongitude) {
            throw new IllegalArgumentException("Điểm đón và điểm xuống phải khác nhau.");
        }
        return journey;
    }

    /** Chỉ request server thiếu hẳn C mới được dùng D; field null/sai không phải legacy. */
    public static RequestJourney fromExistingRequest(TripRequest request, Trip trip) {
        return new RequestJourney(
                request.getPickup(), RequestRouteResolver.resolveDropoff(request, trip));
    }

    /** Trả bản sao điểm đón, không để caller sửa ngược snapshot đang gửi. */
    public Place getPickup() {
        return new Place(pickupName, pickupLatitude, pickupLongitude);
    }

    /** Trả bản sao C đã xác nhận, không tự thay bằng D của chuyến mới. */
    public Place getDropoff() {
        return new Place(dropoffName, dropoffLatitude, dropoffLongitude);
    }

    /** Dùng đúng điểm đón đã kiểm tra để ghi schema Place của Rules. */
    public Map<String, Object> getPickupData() {
        return placeData(pickupName, pickupLatitude, pickupLongitude);
    }

    /** Dùng đúng điểm xuống đã kiểm tra để ghi schema Place của Rules. */
    public Map<String, Object> getDropoffData() {
        return placeData(dropoffName, dropoffLatitude, dropoffLongitude);
    }

    /** Map chỉ có ba field hợp đồng, không lộ cấu trúc có thể thay đổi snapshot. */
    private static Map<String, Object> placeData(String name, double latitude, double longitude) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("name", name);
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        return Collections.unmodifiableMap(data);
    }
}
