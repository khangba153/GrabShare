package com.example.grabshare.repository.search;

import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** Toán học và kết quả tìm dọc tuyến trên RAM; không ghi các metric vào Firestore. */
public final class EnRouteSearch {
    public static final int BATCH_SIZE = 10;
    public static final int MIN_NEW_MATCHES = 3;
    public static final int MAX_CANDIDATES_PER_ACTION = 30;
    public static final int MAX_HTTP_PER_ACTION = 60;
    public static final long ACTION_TIMEOUT_MS = 75000;
    public static final double TOLERANCE_METERS = 300;
    public enum Sort { RECOMMENDED, PRICE, START_GAP, DETOUR }

    /** Không tạo lớp tiện ích. */
    private EnRouteSearch() { }

    /** Điều kiện bất biến; sao chép Place để tránh màn khác đổi điểm đang tính. */
    public static final class Criteria {
        public final Place pickup;
        public final Place dropoff;
        public final long requestedAt;
        public final String userId;
        /** Kiểm tra dữ liệu thay vì mặc định tọa độ thiếu thành (0,0). */
        public Criteria(Place pickup, Place dropoff, long requestedAt, String userId) {
            if (!SearchTripPolicy.validPlace(pickup) || !SearchTripPolicy.validPlace(dropoff)
                    || distance(pickup, dropoff) < 1 || !SearchTripPolicy.validId(userId)
                    || requestedAt <= 0) throw new IllegalArgumentException("Thiếu hoặc sai điều kiện tìm chuyến.");
            this.pickup = new Place(pickup.getName(), pickup.getLatitude(), pickup.getLongitude());
            this.dropoff = new Place(dropoff.getName(), dropoff.getLatitude(), dropoff.getLongitude());
            this.requestedAt = requestedAt;
            this.userId = userId;
        }
    }

    /** Ứng viên có thứ tự sơ bộ; khoảng cách này chưa phải đường bộ. */
    public static final class Candidate {
        public final Trip trip;
        public final double estimatedStartGapMeters;
        public final double estimatedDetourMeters;
        /** Lưu metric sơ bộ riêng với model dùng chung. */
        Candidate(Trip trip, double start, double detour) {
            this.trip = trip;
            estimatedStartGapMeters = start;
            estimatedDetourMeters = detour;
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
        public Match(Trip trip, PublicProfile profile, double startGapMeters, double detourMeters,
                     double baselineMeters, boolean baselineAdjusted) {
            this.trip = trip;
            this.profile = profile;
            this.startGapMeters = startGapMeters;
            this.detourMeters = detourMeters;
            this.baselineMeters = baselineMeters;
            this.baselineAdjusted = baselineAdjusted;
        }
        /** Bổ sung profile không thay metric đã được kiểm tra. */
        public Match withProfile(PublicProfile value) {
            return new Match(trip, value, startGapMeters, detourMeters, baselineMeters, baselineAdjusted);
        }
    }

    /** Thống kê thiếu dữ liệu tách khỏi các chuyến bị loại bằng heuristic. */
    public static final class Prepared {
        public final List<Candidate> candidates = new ArrayList<>();
        public int missingMetadata;
        public int invalid;
        public int preliminaryRejected;
    }

    /** Lấy toàn ngày Việt Nam nhưng giữ điều kiện chuyến chưa khởi hành của Rules. */
    public static long[] timeWindow(long requestedAt, long now) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        calendar.setTimeInMillis(requestedAt);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long start = Math.max(calendar.getTimeInMillis(), now);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return new long[] { start, calendar.getTimeInMillis() };
    }

    /** Sàng lọc rộng để dành Routing cho ứng viên tốt hơn, không khẳng định không bỏ sót. */
    public static Prepared prepare(List<Trip> trips, Criteria criteria, long now) {
        Prepared result = new Prepared();
        Set<String> seen = new HashSet<>();
        long[] window = timeWindow(criteria.requestedAt, now);
        for (Trip trip : trips) {
            if (!SearchTripPolicy.validTrip(trip)) { result.invalid++; continue; }
            if (!seen.add(trip.getId()) || criteria.userId.equals(trip.getDriverId())
                    || !"OPEN".equals(trip.getStatus()) || trip.getAvailableSeats() != 1
                    || trip.getDepartureAt().toDate().getTime() < window[0]
                    || trip.getDepartureAt().toDate().getTime() >= window[1]) continue;
            Long base = trip.getBaseDistanceMeters();
            Long limit = trip.getMaxDetourMeters();
            if (base == null || base <= 0 || limit == null
                    || (limit != 0 && limit != 2000 && limit != 5000 && limit != 10000)) {
                result.missingMetadata++;
                continue;
            }
            double ab = distance(trip.getOrigin(), criteria.pickup);
            double bc = distance(criteria.pickup, criteria.dropoff);
            double cd = distance(criteria.dropoff, trip.getDestination());
            double ad = distance(trip.getOrigin(), trip.getDestination());
            double detour = Math.max(0, ab + bc + cd - ad);
            // Ngưỡng rộng; tuyến cong chữ S vẫn có thể bị heuristic bỏ sót, ghi rõ ở UI.
            // Bù độ cong của đường bộ gốc so với dây cung; không loại khách dọc chữ S chỉ vì xa dây cung.
            double allowance = Math.max(50000, 4 * limit) + Math.max(0, base - ad);
            boolean reversed = distance(trip.getOrigin(), criteria.pickup)
                    > distance(trip.getOrigin(), criteria.dropoff) + allowance
                    && distance(criteria.dropoff, trip.getDestination())
                    > distance(criteria.pickup, trip.getDestination()) + allowance;
            if (detour > allowance || reversed) { result.preliminaryRejected++; continue; }
            result.candidates.add(new Candidate(trip, ab, detour));
        }
        Collections.sort(result.candidates, new Comparator<Candidate>() {
            /** Xếp ứng viên theo độ lệch, AB rồi ID để phân nhóm ổn định. */
            @Override public int compare(Candidate a, Candidate b) {
                int value = Double.compare(a.estimatedDetourMeters, b.estimatedDetourMeters);
                if (value == 0) value = Double.compare(a.estimatedStartGapMeters, b.estimatedStartGapMeters);
                return value == 0 ? a.trip.getId().compareTo(b.trip.getId()) : value;
            }
        });
        return result;
    }

    /** Khoảng cách cung lớn bằng mét, không dùng thay cho detour đường bộ. */
    public static double distance(Place a, Place b) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double halfLat = Math.sin((lat2 - lat1) / 2);
        double halfLon = Math.sin(Math.toRadians(b.getLongitude() - a.getLongitude()) / 2);
        double haversine = halfLat * halfLat + Math.cos(lat1) * Math.cos(lat2) * halfLon * halfLon;
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, haversine))));
    }

    /** Chỉ lọc/sắp xếp những ứng viên đã kiểm tra, không thay hàng đợi còn chờ. */
    public static List<Match> sorted(List<Match> matches, final Sort sort, Long maxPrice, long now) {
        List<Match> result = new ArrayList<>();
        for (Match match : matches) {
            if (match.trip.getDepartureAt().toDate().getTime() > now
                    && (maxPrice == null || match.trip.getCostVnd() <= maxPrice)) result.add(match);
        }
        Collections.sort(result, new Comparator<Match>() {
            /** Dùng metric đường bộ và ID làm tiêu chí phá hòa cuối cùng. */
            @Override public int compare(Match a, Match b) {
                int value;
                if (sort == Sort.PRICE) value = Long.compare(a.trip.getCostVnd(), b.trip.getCostVnd());
                else if (sort == Sort.START_GAP) value = Double.compare(a.startGapMeters, b.startGapMeters);
                else value = Double.compare(a.detourMeters, b.detourMeters);
                if (value == 0) value = Double.compare(a.detourMeters, b.detourMeters);
                if (value == 0) value = Double.compare(a.startGapMeters, b.startGapMeters);
                if (value == 0) value = Long.compare(a.trip.getCostVnd(), b.trip.getCostVnd());
                return value == 0 ? a.trip.getId().compareTo(b.trip.getId()) : value;
            }
        });
        return result;
    }
}
