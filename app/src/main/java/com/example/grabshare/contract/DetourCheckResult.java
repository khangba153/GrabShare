package com.example.grabshare.contract;

/** Kết quả kiểm tra đường vòng, tất cả khoảng cách tính bằng mét. */
public final class DetourCheckResult {
    private final double directDistanceMeters;
    private final double viaPickupDistanceMeters;
    private final long detourMeters;
    private final boolean withinLimit;

    /** Tính đường vòng từ khoảng cách API chưa làm tròn. */
    public DetourCheckResult(
            double directDistanceMeters,
            double viaPickupDistanceMeters,
            long maxDetourMeters
    ) {
        if (!isValidDistance(directDistanceMeters)
                || !isValidDistance(viaPickupDistanceMeters)
                || maxDetourMeters < 0) {
            throw new IllegalArgumentException("Khoảng cách không hợp lệ.");
        }

        this.directDistanceMeters = directDistanceMeters;
        this.viaPickupDistanceMeters = viaPickupDistanceMeters;

        double extraDistance = Math.max(
                0.0,
                viaPickupDistanceMeters - directDistanceMeters
        );
        this.detourMeters = (long) Math.ceil(extraDistance);
        this.withinLimit = this.detourMeters <= maxDetourMeters;
    }

    /** Chỉ chấp nhận khoảng cách hữu hạn và không âm. */
    private static boolean isValidDistance(double distance) {
        return !Double.isNaN(distance)
                && !Double.isInfinite(distance)
                && distance >= 0
                && distance < Long.MAX_VALUE;
    }

    public double getDirectDistanceMeters() {
        return directDistanceMeters;
    }

    public double getViaPickupDistanceMeters() {
        return viaPickupDistanceMeters;
    }

    public long getDetourMeters() {
        return detourMeters;
    }

    public boolean isWithinLimit() {
        return withinLimit;
    }
}