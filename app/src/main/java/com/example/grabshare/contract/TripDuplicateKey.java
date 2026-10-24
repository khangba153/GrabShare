package com.example.grabshare.contract;

import com.example.grabshare.model.Place;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;

import java.util.LinkedHashMap;
import java.util.Map;

/** Khóa trùng chính xác A/D và Timestamp, tài xế được tách bằng cấp path. */
public final class TripDuplicateKey {

    /** Không giữ trạng thái hoặc tự tạo tài liệu Firestore. */
    private TripDuplicateKey() {
    }

    /** Không làm tròn tọa độ; Timestamp phải biểu diễn chính xác ở độ phân giải micro giây. */
    public static String create(Place origin, Place destination, Timestamp departureAt) {
        requirePlace(origin);
        requirePlace(destination);
        if (departureAt == null) {
            throw new IllegalArgumentException("Thiếu thời điểm khởi hành.");
        }
        if (departureAt.getNanoseconds() % 1000 != 0) {
            throw new IllegalArgumentException("Thời điểm chưa phù hợp độ chính xác của Firestore.");
        }
        return "v1_" + coordinate(origin.getLatitude()) + "_"
                + coordinate(origin.getLongitude()) + "_"
                + coordinate(destination.getLatitude()) + "_"
                + coordinate(destination.getLongitude()) + "_"
                + departureAt.getSeconds() + "_" + departureAt.getNanoseconds();
    }

    /** Mỗi tài xế có không gian khóa riêng, tránh ghép UID vào chuỗi khóa tọa độ. */
    public static String guardPath(String driverIdentifier, String duplicateKey) {
        requireIdentifier(driverIdentifier);
        requireIdentifier(duplicateKey);
        return "tripGuards/" + driverIdentifier + "/keys/" + duplicateKey;
    }

    /** Whitelist khóa phải ghi cùng Trip OPEN mới hoặc khi chuyển sang khóa mới. */
    public static Map<String, Object> guardFields(String tripIdentifier) {
        requireIdentifier(tripIdentifier);
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("tripId", tripIdentifier);
        fields.put("updatedAt", FieldValue.serverTimestamp());
        return fields;
    }

    /** Chuẩn hóa âm không thành dương không vì Firestore so hai tọa độ đó bằng nhau. */
    private static String coordinate(double value) {
        if (value == 0.0) {
            return "0.0";
        }
        return Double.toString(value);
    }

    /** Chỉ kiểm tọa độ phục vụ khóa; repository vẫn kiểm tên và schema Place đầy đủ. */
    private static void requirePlace(Place place) {
        if (place == null || place.getLatitude() == null || place.getLongitude() == null
                || !validCoordinate(place.getLatitude(), 90)
                || !validCoordinate(place.getLongitude(), 180)) {
            throw new IllegalArgumentException("Tọa độ khóa chuyến không hợp lệ.");
        }
    }

    /** Tọa độ phải hữu hạn trong miền hợp lệ. */
    private static boolean validCoordinate(double value, double maximum) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= -maximum && value <= maximum;
    }

    /** Không nhận ID rỗng hoặc path thay cho ID document. */
    private static void requireIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty() || identifier.contains("/")) {
            throw new IllegalArgumentException("ID khóa chuyến không hợp lệ.");
        }
    }
}
