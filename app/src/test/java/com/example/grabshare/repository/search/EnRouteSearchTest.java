package com.example.grabshare.repository.search;

import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;
import org.junit.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Kiểm tra tình huống ghép dọc tuyến, dữ liệu cũ và sắp xếp không có mạng. */
public class EnRouteSearchTest {
    private static final long NOW = Instant.parse("2026-10-20T01:00:00Z").toEpochMilli();
    private final Place hcm = new Place("HCM", 10.762622, 106.660172);
    private final Place hn = new Place("Hà Nội", 21.029, 105.854);
    private final Place nt = new Place("Nha Trang", 12.24, 109.19);
    private final Place th = new Place("Thanh Hóa", 19.8, 105.77);

    /** Tạo chuyến giả lập trong RAM, không seed database chung. */
    private Trip trip(String id, Place origin, Place destination) {
        Trip trip = new Trip("driver", origin, destination, new Timestamp((NOW + 3600000) / 1000, 0),
                50000L, "", "OPEN", 1L, "", Timestamp.now(), Timestamp.now());
        trip.setId(id);
        trip.setBaseDistanceMeters(1900000L);
        trip.setMaxDetourMeters(10000L);
        return trip;
    }
    /** Ngày ở Việt Nam không phụ thuộc timezone của máy chạy kiểm tra. */
    @Test public void windowIncludesDriverBeforeRequestedPickupButNotPastTrips() {
        long requested = Instant.parse("2026-10-20T14:00:00Z").toEpochMilli();
        long[] window = EnRouteSearch.timeWindow(requested, NOW);
        assertEquals(NOW, window[0]);
        assertEquals(Instant.parse("2026-10-20T17:00:00Z").toEpochMilli(), window[1]);
        Trip driver = trip("earlier-driver", hcm, hn);
        assertEquals(1, EnRouteSearch.prepare(Collections.singletonList(driver),
                new EnRouteSearch.Criteria(nt, th, requested, "rider"), NOW).candidates.size());
    }
    /** Trước nửa đêm UTC vẫn chọn đúng ngày tại Việt Nam. */
    @Test public void futureDayStartsAtVietnamMidnight() {
        long requested = Instant.parse("2026-10-21T00:30:00Z").toEpochMilli();
        long[] window = EnRouteSearch.timeWindow(requested, NOW);
        assertEquals(Instant.parse("2026-10-20T17:00:00Z").toEpochMilli(), window[0]);
        assertEquals(86400000, window[1] - window[0]);
    }
    /** Khách ở giữa tuyến được qua vòng sơ bộ dù cách A và D nhiều hơn 5 km. */
    @Test public void intermediatePassengerIsNotRejectedByOldFiveKilometerRule() {
        assertTrue(EnRouteSearch.distance(hcm, nt) > 5000);
        assertTrue(EnRouteSearch.distance(th, hn) > 5000);
        EnRouteSearch.Prepared result = EnRouteSearch.prepare(Collections.singletonList(trip("long", hcm, hn)),
                new EnRouteSearch.Criteria(nt, th, NOW + 7200000, "rider"), NOW);
        assertEquals(1, result.candidates.size());
        assertEquals(0, result.missingMetadata);
    }
    /** Không coi mức đi thêm thiếu là giá trị 0 hoặc tự lấy mặc định. */
    @Test public void legacyMetadataIsReportedSeparately() {
        Trip legacy = trip("legacy", hcm, hn);
        legacy.setMaxDetourMeters(null);
        EnRouteSearch.Prepared result = EnRouteSearch.prepare(Collections.singletonList(legacy),
                new EnRouteSearch.Criteria(nt, th, NOW, "rider"), NOW);
        assertEquals(1, result.missingMetadata);
        assertTrue(result.candidates.isEmpty());
    }
    /** Loại chính chủ, hết chỗ, quá giờ, trùng ID và tọa độ không hợp lệ. */
    @Test public void validatesOwnershipStateTimeAndDuplicates() {
        Trip valid = trip("valid", hcm, hn);
        Trip own = trip("own", hcm, hn); own.setDriverId("rider");
        Trip expired = trip("expired", hcm, hn); expired.setDepartureAt(new Timestamp((NOW - 1) / 1000, 0));
        Trip full = trip("full", hcm, hn); full.setStatus("FULL"); full.setAvailableSeats(0L); full.setAcceptedRequestId("accepted");
        Trip invalid = trip("invalid", new Place("bad", Double.NaN, 106.0), hn);
        EnRouteSearch.Prepared result = EnRouteSearch.prepare(Arrays.asList(valid, valid, own, expired, full, invalid),
                new EnRouteSearch.Criteria(nt, th, NOW, "rider"), NOW);
        assertEquals(1, result.candidates.size());
        assertEquals(1, result.invalid);
    }
    /** Tuyến ngược chiều rõ rệt không chiếm nhóm đầu tiên. */
    @Test public void clearlyReversedTripIsFilteredPreliminarily() {
        EnRouteSearch.Prepared result = EnRouteSearch.prepare(Collections.singletonList(trip("reverse", hn, hcm)),
                new EnRouteSearch.Criteria(nt, th, NOW, "rider"), NOW);
        assertTrue(result.candidates.isEmpty());
        assertEquals(1, result.preliminaryRejected);
    }
    /** Các tiêu chí sử dụng metric thực tế và không thay danh sách gốc. */
    @Test public void sortPriceAndBudgetOnlyOperateOnLoadedResults() {
        Trip cheap = trip("cheap", hcm, hn); cheap.setCostVnd(0L);
        Trip near = trip("near", hcm, hn); near.setCostVnd(100000L);
        Trip convenient = trip("convenient", hcm, hn);
        List<EnRouteSearch.Match> data = Arrays.asList(
                new EnRouteSearch.Match(cheap, null, 10000, 1000, 1900000, false),
                new EnRouteSearch.Match(near, null, 50, 500, 1900000, false),
                new EnRouteSearch.Match(convenient, null, 5000, 0, 1900000, false));
        assertEquals("cheap", EnRouteSearch.sorted(data, EnRouteSearch.Sort.PRICE, null, NOW).get(0).trip.getId());
        assertEquals("near", EnRouteSearch.sorted(data, EnRouteSearch.Sort.START_GAP, null, NOW).get(0).trip.getId());
        assertEquals("convenient", EnRouteSearch.sorted(data, EnRouteSearch.Sort.RECOMMENDED, null, NOW).get(0).trip.getId());
        assertEquals("convenient", EnRouteSearch.sorted(data, EnRouteSearch.Sort.DETOUR, null, NOW).get(0).trip.getId());
        assertEquals(1, EnRouteSearch.sorted(data, EnRouteSearch.Sort.PRICE, 0L, NOW).size());
        assertEquals(3, data.size());
        assertTrue(EnRouteSearch.sorted(data, EnRouteSearch.Sort.PRICE, null, NOW + 7200000).isEmpty());
    }
    /** Không nhận extra thiếu hoặc hai điểm khách trùng nhau. */
    @Test public void invalidSearchInputIsRejected() {
        try { new EnRouteSearch.Criteria(nt, nt, NOW, "rider"); fail(); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
        try { new EnRouteSearch.Criteria(new Place("bad", null, null), hn, NOW, "rider"); fail(); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }
}
