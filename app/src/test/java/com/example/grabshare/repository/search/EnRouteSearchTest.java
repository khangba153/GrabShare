package com.example.grabshare.repository.search;

import static org.junit.Assert.*;

import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Kiểm tra tình huống ghép dọc tuyến, dữ liệu cũ và sắp xếp không có mạng. */
public class EnRouteSearchTest {

    private static final long CURRENT_TIME_MILLISECONDS =
            Instant.parse("2026-10-20T01:00:00Z").toEpochMilli();

    private final Place hoChiMinhCity = new Place("HCM", 10.762622, 106.660172);

    private final Place haNoi = new Place("Hà Nội", 21.029, 105.854);

    private final Place nhaTrang = new Place("Nha Trang", 12.24, 109.19);

    private final Place thanhHoa = new Place("Thanh Hóa", 19.8, 105.77);

    /** Tạo chuyến giả lập trong RAM, không seed database chung. */
    private Trip trip(String tripIdentifier, Place origin, Place destination) {
        Trip trip =
                new Trip(
                        "driver",
                        origin,
                        destination,
                        new Timestamp((CURRENT_TIME_MILLISECONDS + 3600000) / 1000, 0),
                        50000L,
                        "",
                        "OPEN",
                        1L,
                        "",
                        Timestamp.now(),
                        Timestamp.now());
        trip.setId(tripIdentifier);
        trip.setBaseDistanceMeters(1900000L);
        trip.setMaxDetourMeters(10000L);
        return trip;
    }

    /** Khách chọn 14:00 thì chỉ giữ 14:00 tới 15:00, gồm cả hai mốc. */
    @Test
    public void oneHourWindowIncludesBothBoundariesAndRejectsOutside() {
        long requested = Instant.parse("2026-10-20T14:00:00Z").toEpochMilli();
        long[] window = EnRouteSearch.timeWindow(requested, CURRENT_TIME_MILLISECONDS);
        assertEquals(requested, window[0]);
        assertEquals(requested + 3600000, window[1]);
        Trip before = at("before", requested - 1);
        Trip start = at("start", requested);
        Trip middle = at("middle", requested + 1800000);
        Trip end = at("end", requested + 3600000);
        Trip after = at("after", requested + 3600001);
        Trip afterNano = at("after-nano", requested + 3600000);
        afterNano.setDepartureAt(new Timestamp((requested + 3600000) / 1000, 1));
        EnRouteSearch.Prepared result =
                EnRouteSearch.prepare(
                        Arrays.asList(before, start, middle, end, after, afterNano),
                        new EnRouteSearch.Criteria(nhaTrang, thanhHoa, requested, "rider"),
                        CURRENT_TIME_MILLISECONDS);
        assertEquals(3, result.candidates.size());
        for (EnRouteSearch.Candidate candidate : result.candidates) {
            assertFalse(candidate.trip.getId().startsWith("before"));
            assertFalse(candidate.trip.getId().startsWith("after"));
        }
    }

    /** Cửa sổ 23:30 đến 00:30 không bị cắt ở nửa đêm ngày tìm. */
    @Test
    public void oneHourWindowMayCrossVietnamMidnight() {
        long requested = Instant.parse("2026-10-20T16:30:00Z").toEpochMilli();
        long[] window = EnRouteSearch.timeWindow(requested, CURRENT_TIME_MILLISECONDS);
        assertEquals(requested, window[0]);
        assertEquals(Instant.parse("2026-10-20T17:30:00Z").toEpochMilli(), window[1]);
        assertEquals(
                1,
                EnRouteSearch.prepare(
                                Collections.singletonList(at("next-day", requested + 2700000)),
                                new EnRouteSearch.Criteria(nhaTrang, thanhHoa, requested, "rider"),
                                CURRENT_TIME_MILLISECONDS)
                        .candidates
                        .size());
    }

    /** Giây ẩn của Calendar không làm mất chuyến đúng phút người dùng nhìn thấy. */
    @Test
    public void hiddenSecondsAreRemovedFromRequestedTime() {
        long requested = Instant.parse("2026-10-20T14:00:00Z").toEpochMilli();
        EnRouteSearch.Criteria criteria =
                new EnRouteSearch.Criteria(nhaTrang, thanhHoa, requested + 35789, "rider");
        assertEquals(requested, criteria.requestedAt);
        assertEquals(
                requested,
                EnRouteSearch.timeWindow(requested + 35789, CURRENT_TIME_MILLISECONDS)[0]);
        assertEquals(
                1,
                EnRouteSearch.prepare(
                                Collections.singletonList(at("exact-minute", requested)),
                                criteria,
                                CURRENT_TIME_MILLISECONDS)
                        .candidates
                        .size());
    }

    /** Chặn chuyến đã đi nhưng không dời cận trên một giờ theo thời gian hiện tại. */
    @Test
    public void currentTimeDoesNotExtendTheOriginalDeadline() {
        long[] window =
                EnRouteSearch.timeWindow(
                        CURRENT_TIME_MILLISECONDS - 1800000, CURRENT_TIME_MILLISECONDS);
        assertEquals(CURRENT_TIME_MILLISECONDS, window[0]);
        assertEquals(CURRENT_TIME_MILLISECONDS + 1800000, window[1]);
        long[] expired =
                EnRouteSearch.timeWindow(
                        CURRENT_TIME_MILLISECONDS - 7200000, CURRENT_TIME_MILLISECONDS);
        assertTrue(expired[0] > expired[1]);
    }

    /** Gán Timestamp chính xác tới mili giây cho test mốc biên. */
    private Trip at(String tripIdentifier, long timestampMilliseconds) {
        Trip trip = trip(tripIdentifier, hoChiMinhCity, haNoi);
        trip.setDepartureAt(
                new Timestamp(
                        timestampMilliseconds / 1000,
                        (int) (timestampMilliseconds % 1000) * 1000000));
        return trip;
    }

    /** Khách ở giữa tuyến được qua vòng sơ bộ dù cách A và D nhiều hơn 5 km. */
    @Test
    public void intermediatePassengerIsNotRejectedByOldFiveKilometerRule() {
        assertTrue(EnRouteSearch.distance(hoChiMinhCity, nhaTrang) > 5000);
        assertTrue(EnRouteSearch.distance(thanhHoa, haNoi) > 5000);
        EnRouteSearch.Prepared result =
                EnRouteSearch.prepare(
                        Collections.singletonList(trip("long", hoChiMinhCity, haNoi)),
                        new EnRouteSearch.Criteria(
                                nhaTrang, thanhHoa, CURRENT_TIME_MILLISECONDS + 3600000, "rider"),
                        CURRENT_TIME_MILLISECONDS);
        assertEquals(1, result.candidates.size());
        assertEquals(0, result.missingMetadata);
    }

    /** Không coi mức đi thêm thiếu là giá trị 0 hoặc tự lấy mặc định. */
    @Test
    public void legacyMetadataIsReportedSeparately() {
        Trip legacy = trip("legacy", hoChiMinhCity, haNoi);
        legacy.setMaxDetourMeters(null);
        EnRouteSearch.Prepared result =
                EnRouteSearch.prepare(
                        Collections.singletonList(legacy),
                        new EnRouteSearch.Criteria(
                                nhaTrang, thanhHoa, CURRENT_TIME_MILLISECONDS, "rider"),
                        CURRENT_TIME_MILLISECONDS);
        assertEquals(1, result.missingMetadata);
        assertTrue(result.candidates.isEmpty());
    }

    /** Loại chính chủ, hết chỗ, quá giờ, trùng ID và tọa độ không hợp lệ. */
    @Test
    public void validatesOwnershipStateTimeAndDuplicates() {
        Trip valid = trip("valid", hoChiMinhCity, haNoi);
        Trip own = trip("own", hoChiMinhCity, haNoi);
        own.setDriverId("rider");
        Trip expired = trip("expired", hoChiMinhCity, haNoi);
        expired.setDepartureAt(new Timestamp((CURRENT_TIME_MILLISECONDS - 1) / 1000, 0));
        Trip full = trip("full", hoChiMinhCity, haNoi);
        full.setStatus("FULL");
        full.setAvailableSeats(0L);
        full.setAcceptedRequestId("accepted");
        Trip invalid = trip("invalid", new Place("bad", Double.NaN, 106.0), haNoi);
        EnRouteSearch.Prepared result =
                EnRouteSearch.prepare(
                        Arrays.asList(valid, valid, own, expired, full, invalid),
                        new EnRouteSearch.Criteria(
                                nhaTrang, thanhHoa, CURRENT_TIME_MILLISECONDS, "rider"),
                        CURRENT_TIME_MILLISECONDS);
        assertEquals(1, result.candidates.size());
        assertEquals(1, result.invalid);
    }

    /** Tuyến ngược chiều rõ rệt không chiếm nhóm đầu tiên. */
    @Test
    public void clearlyReversedTripIsFilteredPreliminarily() {
        EnRouteSearch.Prepared result =
                EnRouteSearch.prepare(
                        Collections.singletonList(trip("reverse", haNoi, hoChiMinhCity)),
                        new EnRouteSearch.Criteria(
                                nhaTrang, thanhHoa, CURRENT_TIME_MILLISECONDS, "rider"),
                        CURRENT_TIME_MILLISECONDS);
        assertTrue(result.candidates.isEmpty());
        assertEquals(1, result.preliminaryRejected);
    }

    /** Các tiêu chí sử dụng metric thực tế và không thay danh sách gốc. */
    @Test
    public void sortPriceAndBudgetOnlyOperateOnLoadedResults() {
        Trip cheap = trip("cheap", hoChiMinhCity, haNoi);
        cheap.setCostVnd(0L);
        Trip near = trip("near", hoChiMinhCity, haNoi);
        near.setCostVnd(100000L);
        Trip convenient = trip("convenient", hoChiMinhCity, haNoi);
        List<EnRouteSearch.Match> data =
                Arrays.asList(
                        new EnRouteSearch.Match(cheap, null, 10000, 1000, 1900000, false),
                        new EnRouteSearch.Match(near, null, 50, 500, 1900000, false),
                        new EnRouteSearch.Match(convenient, null, 5000, 0, 1900000, false));
        assertEquals(
                "cheap",
                EnRouteSearch.sorted(
                                data, EnRouteSearch.Sort.PRICE, null, CURRENT_TIME_MILLISECONDS)
                        .get(0)
                        .trip
                        .getId());
        assertEquals(
                "near",
                EnRouteSearch.sorted(
                                data, EnRouteSearch.Sort.START_GAP, null, CURRENT_TIME_MILLISECONDS)
                        .get(0)
                        .trip
                        .getId());
        assertEquals(
                "convenient",
                EnRouteSearch.sorted(
                                data,
                                EnRouteSearch.Sort.RECOMMENDED,
                                null,
                                CURRENT_TIME_MILLISECONDS)
                        .get(0)
                        .trip
                        .getId());
        assertEquals(
                "convenient",
                EnRouteSearch.sorted(
                                data, EnRouteSearch.Sort.DETOUR, null, CURRENT_TIME_MILLISECONDS)
                        .get(0)
                        .trip
                        .getId());
        assertEquals(
                1,
                EnRouteSearch.sorted(data, EnRouteSearch.Sort.PRICE, 0L, CURRENT_TIME_MILLISECONDS)
                        .size());
        assertEquals(3, data.size());
        assertTrue(
                EnRouteSearch.sorted(
                                data,
                                EnRouteSearch.Sort.PRICE,
                                null,
                                CURRENT_TIME_MILLISECONDS + 7200000)
                        .isEmpty());
    }

    /** Không nhận extra thiếu hoặc hai điểm khách trùng nhau. */
    @Test
    public void invalidSearchInputIsRejected() {
        try {
            new EnRouteSearch.Criteria(nhaTrang, nhaTrang, CURRENT_TIME_MILLISECONDS, "rider");
            fail();
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            new EnRouteSearch.Criteria(
                    new Place("bad", null, null), haNoi, CURRENT_TIME_MILLISECONDS, "rider");
            fail();
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
