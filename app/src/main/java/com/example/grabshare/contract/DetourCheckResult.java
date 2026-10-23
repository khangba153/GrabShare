package com.example.grabshare.contract;

/** Kết quả kiểm tra đường vòng, tất cả khoảng cách tính bằng mét. */
public final class DetourCheckResult {
    private final double directDistanceMeters;
    private final double viaPassengerRouteDistanceMeters;
    private final Double passengerDistanceMeters;
    private final long detourMeters;
    private final boolean withinLimit;

    /** Tính đường vòng từ A→D và A→P→C→D theo khoảng cách API chưa làm tròn. */
    public DetourCheckResult(
            double directDistanceMeters,
            double viaPassengerRouteDistanceMeters,
            long maxDetourMeters
    ) {
        this(directDistanceMeters, viaPassengerRouteDistanceMeters, null, maxDetourMeters);
    }

    /**
     * Nhận thêm độ dài P→C trên chính tuyến ghép; null nghĩa là chưa đo được.
     * Không dùng số mét đi thêm hoặc một tuyến P→C độc lập thay cho chặng khách đi.
     */
    public DetourCheckResult(
            double directDistanceMeters,
            double viaPassengerRouteDistanceMeters,
            Double passengerDistanceMeters,
            long maxDetourMeters
    ) {
        if (!isValidDistance(directDistanceMeters)
                || !isValidDistance(viaPassengerRouteDistanceMeters)
                || maxDetourMeters < 0) {
            throw new IllegalArgumentException("Khoảng cách không hợp lệ.");
        }

        if (passengerDistanceMeters != null
                && (!isValidDistance(passengerDistanceMeters)
                || passengerDistanceMeters <= 0
                || passengerDistanceMeters > viaPassengerRouteDistanceMeters)) {
            throw new IllegalArgumentException("Khoảng cách khách đi không hợp lệ.");
        }

        this.directDistanceMeters = directDistanceMeters;
        this.viaPassengerRouteDistanceMeters = viaPassengerRouteDistanceMeters;
        this.passengerDistanceMeters = passengerDistanceMeters;

        double extraDistance = Math.max(
                0.0,
                viaPassengerRouteDistanceMeters - directDistanceMeters
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

    /** Khoảng cách tuyến ghép bốn điểm, giữ nguyên mét chưa làm tròn. */
    public double getViaPassengerRouteDistanceMeters() {
        return viaPassengerRouteDistanceMeters;
    }

    /** Null chỉ trạng thái chưa có chặng P→C, không được đổi thành 0 để tính tiền. */
    public Double getPassengerDistanceMeters() {
        return passengerDistanceMeters;
    }

    /** Giữ tương thích tên getter cũ; giá trị vẫn là toàn tuyến ghép. */
    @Deprecated
    public double getViaPickupDistanceMeters() {
        return viaPassengerRouteDistanceMeters;
    }

    public long getDetourMeters() {
        return detourMeters;
    }

    public boolean isWithinLimit() {
        return withinLimit;
    }
}
