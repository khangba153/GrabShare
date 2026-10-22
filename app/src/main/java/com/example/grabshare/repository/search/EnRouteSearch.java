package com.example.grabshare.repository.search;

import com.example.grabshare.model.Place;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Toán học và kết quả tìm dọc tuyến trên RAM; không ghi các metric vào Firestore. */
public final class EnRouteSearch {

    public static final int BATCH_SIZE = 10;

    public static final int MINIMUM_NEW_MATCH_COUNT = 3;

    public static final int MAXIMUM_CANDIDATES_PER_ACTION = 30;

    public static final int MAXIMUM_HTTP_REQUESTS_PER_ACTION = 60;

    public static final long ACTION_TIMEOUT_MILLISECONDS = 75000;

    public static final long MAXIMUM_DRIVER_DELAY_MILLISECONDS = 60 * 60 * 1000;

    public enum Sort {
        RECOMMENDED,
        PRICE,
        START_GAP,
        DETOUR
    }

    /** Không tạo lớp tiện ích. */
    private EnRouteSearch() {}

    /** Điều kiện bất biến; sao chép Place để tránh màn khác đổi điểm đang tính. */
    public static final class Criteria {

        public final Place pickup;

        public final Place dropoff;

        public final long requestedAt;

        public final String userId;

        /** Kiểm tra dữ liệu thay vì mặc định tọa độ thiếu thành (0,0). */
        public Criteria(Place pickup, Place dropoff, long requestedAt, String userId) {
            if (!SearchTripPolicy.validPlace(pickup)
                    || !SearchTripPolicy.validPlace(dropoff)
                    || distance(pickup, dropoff) < 1
                    || !SearchTripPolicy.validId(userId)
                    || requestedAt <= 0) {
                throw new IllegalArgumentException("Thiếu hoặc sai điều kiện tìm chuyến.");
            }
            this.pickup = new Place(pickup.getName(), pickup.getLatitude(), pickup.getLongitude());
            this.dropoff =
                    new Place(dropoff.getName(), dropoff.getLatitude(), dropoff.getLongitude());
            this.requestedAt = requestedAt / 60000 * 60000;
            this.userId = userId;
        }
    }

    /** Ứng viên có thứ tự sơ bộ; khoảng cách này chưa phải đường bộ. */
    public static final class Candidate {

        public final Trip trip;

        public final double estimatedStartGapMeters;

        public final double estimatedDetourMeters;

        /** Lưu metric sơ bộ riêng với model dùng chung. */
        Candidate(
                Trip trip,
                double estimatedStartDistanceMeters,
                double estimatedAdditionalDistanceMeters) {
            this.trip = trip;
            estimatedStartGapMeters = estimatedStartDistanceMeters;
            estimatedDetourMeters = estimatedAdditionalDistanceMeters;
        }
    }

    /** Kết quả đã kiểm tra đường bộ, chỉ tồn tại trong lượt tìm. */
    public static final class Match {

        public final Trip trip;

        public final PublicProfile profile;

        public final double startGapMeters;

        public final double detourMeters;

        public final double baselineMeters;

        public final boolean baselineAdjusted;

        /** Tạo kết quả bất biến để giao diện có thể sắp xếp mà không gọi API lại. */
        public Match(
                Trip trip,
                PublicProfile profile,
                double startGapMeters,
                double detourMeters,
                double baselineMeters,
                boolean baselineAdjusted) {
            this.trip = trip;
            this.profile = profile;
            this.startGapMeters = startGapMeters;
            this.detourMeters = detourMeters;
            this.baselineMeters = baselineMeters;
            this.baselineAdjusted = baselineAdjusted;
        }

        /** Bổ sung profile không thay metric đã được kiểm tra. */
        public Match withProfile(PublicProfile driverProfile) {
            return new Match(
                    trip,
                    driverProfile,
                    startGapMeters,
                    detourMeters,
                    baselineMeters,
                    baselineAdjusted);
        }
    }

    /** Thống kê thiếu dữ liệu tách khỏi các chuyến bị loại bằng heuristic. */
    public static final class Prepared {

        public final List<Candidate> candidates = new ArrayList<Candidate>();

        public int missingMetadata;

        public int invalid;

        public int preliminaryRejected;
    }

    /**
     * Khách chọn T thì nhận tài xế từ T đến T+1 giờ, không lấy trước T hoặc nới thêm khi giờ đã
     * qua.
     */
    public static long[] timeWindow(long requestedAt, long currentTimeMilliseconds) {
        long selectedMinute = requestedAt / 60000 * 60000;
        return new long[] {
            Math.max(selectedMinute, currentTimeMilliseconds),
            selectedMinute + MAXIMUM_DRIVER_DELAY_MILLISECONDS
        };
    }

    /** Sàng lọc rộng để dành Routing cho ứng viên tốt hơn, không khẳng định không bỏ sót. */
    public static Prepared prepare(
            List<Trip> trips, Criteria criteria, long currentTimeMilliseconds) {
        Prepared result = new Prepared();
        Set<String> seenTripIdentifiers = new HashSet<String>();
        long[] departureTimeWindow = timeWindow(criteria.requestedAt, currentTimeMilliseconds);
        for (Trip trip : trips) {
            if (!SearchTripPolicy.validTrip(trip)) {
                result.invalid = result.invalid + 1;
                continue;
            }
            if (!seenTripIdentifiers.add(trip.getId())
                    || criteria.userId.equals(trip.getDriverId())
                    || !"OPEN".equals(trip.getStatus())
                    || trip.getAvailableSeats() != 1
                    || trip.getDepartureAt().compareTo(timestampAt(departureTimeWindow[0])) < 0
                    || trip.getDepartureAt().compareTo(timestampAt(departureTimeWindow[1])) > 0) {
                continue;
            }
            Long storedBaselineMeters = trip.getBaseDistanceMeters();
            Long maximumDetourMeters = trip.getMaxDetourMeters();
            if (storedBaselineMeters == null
                    || storedBaselineMeters <= 0
                    || !DetourLimit.isValidMeters(maximumDetourMeters)) {
                result.missingMetadata = result.missingMetadata + 1;
                continue;
            }
            double driverOriginToPickupMeters = distance(trip.getOrigin(), criteria.pickup);
            double pickupToDropoffMeters = distance(criteria.pickup, criteria.dropoff);
            double dropoffToDriverDestinationMeters =
                    distance(criteria.dropoff, trip.getDestination());
            double driverOriginToDestinationMeters =
                    distance(trip.getOrigin(), trip.getDestination());
            double estimatedAdditionalDistanceMeters =
                    Math.max(
                            0,
                            driverOriginToPickupMeters
                                    + pickupToDropoffMeters
                                    + dropoffToDriverDestinationMeters
                                    - driverOriginToDestinationMeters);
            // Ngưỡng rộng; tuyến cong chữ S vẫn có thể bị heuristic bỏ sót, ghi rõ ở UI.
            // Bù độ cong của đường bộ gốc so với dây cung; không loại khách dọc chữ S chỉ vì xa dây
            // cung.
            double preliminaryDetourAllowanceMeters =
                    Math.max(50000, 4 * maximumDetourMeters)
                            + Math.max(0, storedBaselineMeters - driverOriginToDestinationMeters);
            boolean isClearlyReversed =
                    distance(trip.getOrigin(), criteria.pickup)
                                    > distance(trip.getOrigin(), criteria.dropoff)
                                            + preliminaryDetourAllowanceMeters
                            && distance(criteria.dropoff, trip.getDestination())
                                    > distance(criteria.pickup, trip.getDestination())
                                            + preliminaryDetourAllowanceMeters;
            if (estimatedAdditionalDistanceMeters > preliminaryDetourAllowanceMeters
                    || isClearlyReversed) {
                result.preliminaryRejected = result.preliminaryRejected + 1;
                continue;
            }
            result.candidates.add(
                    new Candidate(
                            trip, driverOriginToPickupMeters, estimatedAdditionalDistanceMeters));
        }
        Collections.sort(
                result.candidates,
                new Comparator<Candidate>() {

                    /** Xếp ứng viên theo độ lệch, AB rồi ID để phân nhóm ổn định. */
                    @Override
                    public int compare(Candidate firstCandidate, Candidate secondCandidate) {
                        int comparisonResult =
                                Double.compare(
                                        firstCandidate.estimatedDetourMeters,
                                        secondCandidate.estimatedDetourMeters);
                        if (comparisonResult == 0) {
                            comparisonResult =
                                    Double.compare(
                                            firstCandidate.estimatedStartGapMeters,
                                            secondCandidate.estimatedStartGapMeters);
                        }
                        if (comparisonResult == 0) {
                            return firstCandidate
                                    .trip
                                    .getId()
                                    .compareTo(secondCandidate.trip.getId());
                        } else {
                            return comparisonResult;
                        }
                    }
                });
        return result;
    }

    /** Giữ đúng mili giây ở hai biên, không nhận chuyến vượt biên do làm tròn Timestamp. */
    private static Timestamp timestampAt(long timestampMilliseconds) {
        return new Timestamp(
                timestampMilliseconds / 1000, (int) (timestampMilliseconds % 1000) * 1000000);
    }

    /** Khoảng cách cung lớn bằng mét, không dùng thay cho detour đường bộ. */
    public static double distance(Place firstPlace, Place secondPlace) {
        double firstLatitudeRadians = Math.toRadians(firstPlace.getLatitude());
        double secondLatitudeRadians = Math.toRadians(secondPlace.getLatitude());
        double halfLatitudeSine = Math.sin((secondLatitudeRadians - firstLatitudeRadians) / 2);
        double halfLongitudeSine =
                Math.sin(
                        Math.toRadians(secondPlace.getLongitude() - firstPlace.getLongitude()) / 2);
        double haversine =
                halfLatitudeSine * halfLatitudeSine
                        + Math.cos(firstLatitudeRadians)
                                * Math.cos(secondLatitudeRadians)
                                * halfLongitudeSine
                                * halfLongitudeSine;
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, haversine))));
    }

    /** Chỉ lọc/sắp xếp những ứng viên đã kiểm tra, không thay hàng đợi còn chờ. */
    public static List<Match> sorted(
            List<Match> matches,
            final Sort sort,
            long currentTimeMilliseconds) {
        List<Match> result = new ArrayList<Match>();
        for (Match match : matches) {
            if (match.trip.getDepartureAt().toDate().getTime() > currentTimeMilliseconds) {
                result.add(match);
            }
        }
        Collections.sort(
                result,
                new Comparator<Match>() {

                    /** Dùng metric đường bộ và ID làm tiêu chí phá hòa cuối cùng. */
                    @Override
                    public int compare(Match firstMatch, Match secondMatch) {
                        int comparisonResult;
                        if (sort == Sort.PRICE) {
                            comparisonResult =
                                    Long.compare(
                                            firstMatch.trip.getCostVnd(),
                                            secondMatch.trip.getCostVnd());
                        } else {
                            if (sort == Sort.START_GAP) {
                                comparisonResult =
                                        Double.compare(
                                                firstMatch.startGapMeters,
                                                secondMatch.startGapMeters);
                            } else {
                                comparisonResult =
                                        Double.compare(
                                                firstMatch.detourMeters, secondMatch.detourMeters);
                            }
                        }
                        if (comparisonResult == 0) {
                            comparisonResult =
                                    Double.compare(
                                            firstMatch.detourMeters, secondMatch.detourMeters);
                        }
                        if (comparisonResult == 0) {
                            comparisonResult =
                                    Double.compare(
                                            firstMatch.startGapMeters, secondMatch.startGapMeters);
                        }
                        if (comparisonResult == 0) {
                            comparisonResult =
                                    Long.compare(
                                            firstMatch.trip.getCostVnd(),
                                            secondMatch.trip.getCostVnd());
                        }
                        if (comparisonResult == 0) {
                            return firstMatch.trip.getId().compareTo(secondMatch.trip.getId());
                        } else {
                            return comparisonResult;
                        }
                    }
                });
        return result;
    }
}
