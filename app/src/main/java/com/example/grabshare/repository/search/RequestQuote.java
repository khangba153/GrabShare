package com.example.grabshare.repository.search;

import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Khoản khách xác nhận gắn với bản chụp đầu vào; không dùng Bundle để cấp quyền ghi. */
public final class RequestQuote {
    /** Đầu vào chụp trước HTTP, chỉ giữ giá trị bất biến và bản sao P/C. */
    public static final class Inputs {
        private final List<Object> values;
        private final Long fullTripCostVnd;
        private final long maximumDetourMeters;
        private final RequestJourney journey;

        /** Kiểm tra đủ dữ liệu server dùng tính quote, không giữ model có thể bị sửa. */
        public Inputs(Trip trip, String userIdentifier, Place pickup, Place dropoff) {
            String reason = SearchTripPolicy.blockedReason(trip, userIdentifier);
            if (!reason.isEmpty()) {
                throw new IllegalArgumentException(reason);
            }
            if (trip.getBaseDistanceMeters() == null
                    || trip.getBaseDistanceMeters() <= 0
                    || !DetourLimit.isValidMeters(trip.getMaxDetourMeters())) {
                throw new IllegalArgumentException("Chuyến chưa có đủ cấu hình đi thêm.");
            }
            journey = RequestJourney.forNewRequest(pickup, dropoff);
            fullTripCostVnd = trip.getCostVnd();
            maximumDetourMeters = trip.getMaxDetourMeters();
            List<Object> fields =
                    new ArrayList<Object>(
                            Arrays.<Object>asList(
                                    userIdentifier,
                                    trip.getId(),
                                    trip.getDriverId(),
                                    trip.getDepartureAt(),
                                    fullTripCostVnd,
                                    trip.getBaseDistanceMeters(),
                                    trip.getMaxDetourMeters()));
            appendPlace(fields, trip.getOrigin());
            appendPlace(fields, trip.getDestination());
            appendPlace(fields, journey.getPickup());
            appendPlace(fields, journey.getDropoff());
            values = Collections.unmodifiableList(fields);
        }

        /** So cùng Trip/UID/P/C và các đầu vào khách đã thấy trước khi tính đường. */
        public boolean matches(Trip trip, String userIdentifier, Place pickup, Place dropoff) {
            try {
                return values.equals(new Inputs(trip, userIdentifier, pickup, dropoff).values);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }

        /** Chỉ tạo khoản dự kiến sau khi checker xác nhận tuyến và giới hạn hợp lệ. */
        public RequestQuote withRoute(DetourCheckResult route) {
            return new RequestQuote(this, route);
        }

        /** So dữ liệu chụp để render lặp không tự gọi HTTP lần nữa. */
        public boolean sameAs(Inputs other) {
            return other != null && values.equals(other.values);
        }
    }

    /** Yêu cầu khách xem và xác nhận lại; không tự ghi khoản thay thế. */
    public static final class ChangedException extends IllegalStateException {
        /** Thông báo an toàn, không chứa credential hoặc dữ liệu tài khoản. */
        public ChangedException() {
            super("Chuyến hoặc khoản đóng góp đã thay đổi. Hãy tải lại và xác nhận khoản mới.");
        }
    }

    private final Inputs inputs;
    private final ContributionSnapshot contribution;
    private final double combinedDistanceMeters;
    private final long detourMeters;

    /** Giữ cùng lượt đo A/D, P/C và toàn tuyến ghép; không làm tròn mét trung gian. */
    private RequestQuote(Inputs inputs, DetourCheckResult route) {
        if (route == null || route.getDetourMeters() > inputs.maximumDetourMeters) {
            throw new IllegalArgumentException(
                    "Tuyến vượt giới hạn đi thêm của chuyến đã xác nhận.");
        }
        this.inputs = inputs;
        contribution = ContributionCalculator.calculate(inputs.fullTripCostVnd, route);
        combinedDistanceMeters = route.getViaPassengerRouteDistanceMeters();
        detourMeters = route.getDetourMeters();
    }

    /** Trả snapshot bất biến để hiển thị hoặc ghi whitelist sau khi xác nhận. */
    public ContributionSnapshot getContribution() {
        return contribution;
    }

    /** Phần đi thêm chỉ dùng giải thích và xét giới hạn, không dùng tính tiền. */
    public long getDetourMeters() {
        return detourMeters;
    }

    /** Đối chiếu lại server và bản nháp tại Send cũng như trong transaction. */
    public boolean matches(Trip trip, String userIdentifier, Place pickup, Place dropoff) {
        return inputs.matches(trip, userIdentifier, pickup, dropoff);
    }

    /** Đường hoặc tiền mới khác bản đã xác nhận phải dừng, kể cả cùng số tiền làm tròn. */
    public boolean matchesRoute(DetourCheckResult route) {
        try {
            ContributionSnapshot recalculated =
                    ContributionCalculator.calculate(contribution.getFullTripCostVnd(), route);
            return contribution.toMap().equals(recalculated.toMap())
                    && Double.compare(
                                    combinedDistanceMeters,
                                    route.getViaPassengerRouteDistanceMeters())
                            == 0
                    && detourMeters == route.getDetourMeters();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Giữ nhãn và tọa độ, không coi hai điểm cùng tên là một điểm. */
    private static void appendPlace(List<Object> values, Place place) {
        values.add(place.getName());
        values.add(place.getLatitude());
        values.add(place.getLongitude());
    }
}
