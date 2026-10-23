package com.example.grabshare.contract;

import com.example.grabshare.model.TripRequest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/** Chính sách tiền dùng chung; không gọi HTTP hoặc tự xác nhận thay người dùng. */
public final class ContributionCalculator {

    /** Không tạo đối tượng vì calculator không giữ trạng thái. */
    private ContributionCalculator() {
    }

    /** Chỉ tính khi checker đã trả được chặng khách đi và tuyến trong giới hạn. */
    public static ContributionSnapshot calculate(Long fullTripCostVnd,
            DetourCheckResult routeResult) {
        if (routeResult == null || !routeResult.isWithinLimit()
                || routeResult.getPassengerDistanceMeters() == null) {
            throw new IllegalArgumentException("Chưa tính được khoản đóng góp.");
        }
        return calculate(fullTripCostVnd, routeResult.getDirectDistanceMeters(),
                routeResult.getPassengerDistanceMeters());
    }

    /**
     * Làm tròn đến nguyên VND bằng Math.round, nửa đồng dương làm tròn lên.
     * Tính tỷ lệ trước khi nhân giá; không làm tròn mét hoặc số km trung gian.
     * Caller phải cung cấp hai khoảng cách cùng lượt đo và đúng tuyến đã hiển thị.
     */
    public static ContributionSnapshot calculate(Long fullTripCostVnd,
            double directDistanceMeters, double passengerDistanceMeters) {
        if (fullTripCostVnd == null || fullTripCostVnd < 0
                || !validDistance(directDistanceMeters)
                || !validDistance(passengerDistanceMeters)) {
            throw new IllegalArgumentException("Chưa tính được khoản đóng góp.");
        }
        long amountVnd;
        if (passengerDistanceMeters >= directDistanceMeters) {
            amountVnd = fullTripCostVnd;
        } else {
            double passengerRatio = passengerDistanceMeters / directDistanceMeters;
            amountVnd = Math.round(fullTripCostVnd.doubleValue() * passengerRatio);
            // Giữ trần Long chính xác cả khi ép giá rất lớn sang Double mất độ chính xác.
            if (amountVnd > fullTripCostVnd) {
                amountVnd = fullTripCostVnd;
            }
        }
        return new ContributionSnapshot(fullTripCostVnd, directDistanceMeters,
                passengerDistanceMeters, amountVnd);
    }

    /**
     * Thiếu hẳn field là legacy chưa có khoản được xác nhận và trả null.
     * Có field nhưng null/sai/khác công thức phải báo lỗi; không lấy giá Trip bù vào.
     */
    public static ContributionSnapshot readConfirmed(TripRequest tripRequest) {
        if (tripRequest == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu yêu cầu.");
        }
        if (!tripRequest.hasContributionSnapshotField()) {
            return null;
        }
        return fromMap(tripRequest.getContributionSnapshot());
    }

    /** Đọc whitelist phiên bản 1, giữ nguyên snapshot lịch sử, từ chối map lỗi. */
    public static ContributionSnapshot fromMap(Map<String, Object> fields) {
        if (fields == null || !fields.keySet().equals(new HashSet<String>(Arrays.asList(
                "version", "fullTripCostVnd", "directDistanceMeters",
                "passengerDistanceMeters", "amountVnd")))
                || !(fields.get("version") instanceof Long)
                || ((Long) fields.get("version")) != 1L
                || !(fields.get("fullTripCostVnd") instanceof Long)
                || !(fields.get("amountVnd") instanceof Long)
                || !isFirestoreNumber(fields.get("directDistanceMeters"))
                || !isFirestoreNumber(fields.get("passengerDistanceMeters"))) {
            throw new IllegalArgumentException("Snapshot khoản đóng góp không hợp lệ.");
        }
        ContributionSnapshot calculated = calculate((Long) fields.get("fullTripCostVnd"),
                ((Number) fields.get("directDistanceMeters")).doubleValue(),
                ((Number) fields.get("passengerDistanceMeters")).doubleValue());
        if (calculated.getAmountVnd() != ((Long) fields.get("amountVnd"))) {
            throw new IllegalArgumentException("Khoản đóng góp không khớp snapshot.");
        }
        return calculated;
    }

    /** Firestore trả số nguyên Long hoặc số thực Double, không nhận chuỗi số. */
    private static boolean isFirestoreNumber(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    /** Mét phải hữu hạn và dương, kể cả khi chuyến được cho đi miễn phí. */
    private static boolean validDistance(double distanceMeters) {
        return !Double.isNaN(distanceMeters) && !Double.isInfinite(distanceMeters)
                && distanceMeters > 0 && distanceMeters < Long.MAX_VALUE;
    }
}
