package com.example.grabshare.contract;

import java.util.LinkedHashMap;
import java.util.Map;

/** Khoản đóng góp đã tính theo chính sách phiên bản 1, đơn vị mét và VND. */
public final class ContributionSnapshot {
    private final long fullTripCostVnd;
    private final double directDistanceMeters;
    private final double passengerDistanceMeters;
    private final long amountVnd;

    /** Chỉ calculator cùng package tạo snapshot sau khi kiểm tra dữ liệu. */
    ContributionSnapshot(long fullTripCostVnd, double directDistanceMeters,
            double passengerDistanceMeters, long amountVnd) {
        this.fullTripCostVnd = fullTripCostVnd;
        this.directDistanceMeters = directDistanceMeters;
        this.passengerDistanceMeters = passengerDistanceMeters;
        this.amountVnd = amountVnd;
    }

    /** Lấy mức đóng góp tham khảo của toàn tuyến tại lúc khách xác nhận. */
    public long getFullTripCostVnd() {
        return fullTripCostVnd;
    }

    /** Lấy khoảng cách A→D vừa đo, không dùng baseline đã lưu để thay thế. */
    public double getDirectDistanceMeters() {
        return directDistanceMeters;
    }

    /** Lấy chặng P→C trên tuyến ghép A→P→C→D. */
    public double getPassengerDistanceMeters() {
        return passengerDistanceMeters;
    }

    /** Lấy số nguyên VND đã làm tròn và chặn ở mức toàn tuyến. */
    public long getAmountVnd() {
        return amountVnd;
    }

    /** Tạo whitelist để repository ghi map lồng, không ghi cả model Request. */
    public Map<String, Object> toMap() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("version", 1L);
        fields.put("fullTripCostVnd", fullTripCostVnd);
        fields.put("directDistanceMeters", directDistanceMeters);
        fields.put("passengerDistanceMeters", passengerDistanceMeters);
        fields.put("amountVnd", amountVnd);
        return fields;
    }
}
