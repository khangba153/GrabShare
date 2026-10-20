package com.example.grabshare.api;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.grabshare.activity.search.ResultsPipelineTest;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import static org.junit.Assert.*;

/** Kiểm tra planner/parser thật trên Android với phản hồi có kiểm soát. */
@RunWith(AndroidJUnit4.class)
public class RoutingComparisonTest {
    private final VietnamRoutingClient.Point a = new VietnamRoutingClient.Point(10.76, 106.66);
    private final VietnamRoutingClient.Point b = new VietnamRoutingClient.Point(12.24, 109.19);
    private final VietnamRoutingClient.Point c = new VietnamRoutingClient.Point(19.8, 105.77);
    private final VietnamRoutingClient.Point d = new VietnamRoutingClient.Point(21.029, 105.854);

    /** Chỉ thay transport, giữ nguyên việc phân chặng và xác minh quốc gia. */
    static class Fake extends VietnamRoutingClient {
        final List<List<Point>> calls = new ArrayList<>();
        int foreignUntil;
        boolean quota;
        boolean cancel;
        /** Credential giả không bao giờ được đưa ra mạng. */
        Fake() { super("fixture"); }
        /** Trả tuyến đủ metadata theo hình học request và kịch bản kiểm tra. */
        @Override protected JSONObject request(List<Point> points, boolean avoid, Cancellation cancellation)
                throws IOException, JSONException {
            calls.add(new ArrayList<>(points));
            if (quota) throw new RoutingException(Failure.QUOTA, "quota");
            boolean original = points.size() == 2 || (points.size() > 4 && points.size() <= 7);
            return ResultsPipelineTest.response(points, original ? 1000 : 1100,
                    calls.size() <= foreignUntil ? "la" : "vn");
        }
    }

    /** Baseline DB lệch phải hiệu chỉnh trên RAM, không làm âm detour rồi nhận nhầm chuyến. */
    @Test public void staleBaselineUsesComparableCurrentRoute() throws Exception {
        Fake fake = new Fake();
        VietnamRoutingClient.MatchRoute match = fake.findMatchRoute(Arrays.asList(a, b, c, d), 2000, null);
        assertTrue(match.baselineAdjusted);
        assertEquals(1000, match.baselineMeters, 0.001);
        assertEquals(100, match.detourMeters(), 0.001);
        assertEquals(3, match.route.requiredLegDistancesMeters.size());
        assertEquals(1100.0 / 3, match.route.requiredLegDistancesMeters.get(0), 0.001);
    }
    /** Chênh nhỏ do làm tròn vẫn dùng baseline đã lưu như hợp đồng hiện tại. */
    @Test public void matchingBaselineIsReusedWithinFiftyMeters() throws Exception {
        Fake fake = new Fake();
        VietnamRoutingClient.MatchRoute match = fake.findMatchRoute(Arrays.asList(a, b, c, d), 1020, null);
        assertFalse(match.baselineAdjusted);
        assertEquals(80, match.detourMeters(), 0.001);
    }
    /** Mốc dẫn hướng của A/D xuất hiện cùng thứ tự trong cả hai tuyến. */
    @Test public void fallbackSharesCorridorAndKeepsPickupBeforeDropoff() throws Exception {
        Fake fake = new Fake();
        fake.foreignUntil = 1;
        VietnamRoutingClient.MatchRoute match = fake.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null);
        assertTrue(match.route.guided);
        assertEquals(3, fake.calls.size());
        List<VietnamRoutingClient.Point> baseline = fake.calls.get(1);
        List<VietnamRoutingClient.Point> joined = fake.calls.get(2);
        int pickupIndex = -1;
        int dropoffIndex = -1;
        for (int i = 0; i < joined.size(); i++) {
            if (Math.abs(joined.get(i).latitude - b.latitude) < 0.000001
                    && Math.abs(joined.get(i).longitude - b.longitude) < 0.000001) pickupIndex = i;
            if (Math.abs(joined.get(i).latitude - c.latitude) < 0.000001
                    && Math.abs(joined.get(i).longitude - c.longitude) < 0.000001) dropoffIndex = i;
        }
        assertTrue(pickupIndex >= 0);
        assertTrue(dropoffIndex > pickupIndex);
        int last = -1;
        for (int i = 1; i < baseline.size() - 1; i++) {
            VietnamRoutingClient.Point anchor = baseline.get(i);
            int found = -1;
            for (int j = last + 1; j < joined.size(); j++) {
                if (Math.abs(joined.get(j).latitude - anchor.latitude) < 0.000001
                        && Math.abs(joined.get(j).longitude - anchor.longitude) < 0.000001) { found = j; break; }
            }
            assertTrue(found > last);
            last = found;
        }
        assertEquals(3, match.route.requiredLegDistancesMeters.size());
        double total = 0;
        for (double distance : match.route.requiredLegDistancesMeters) total += distance;
        assertEquals(match.route.distanceMeters, total, 0.001);
    }
    /** Các chặng có cùng tọa độ vẫn được trả về với số mét bằng 0. */
    @Test public void identicalPickupOriginDoesNotLoseRequiredLegs() throws Exception {
        Fake fake = new Fake();
        VietnamRoutingClient.MatchRoute match = fake.findMatchRoute(Arrays.asList(a, a, d, d), 1000, null);
        assertEquals(Arrays.asList(0.0, 1000.0, 0.0), match.route.requiredLegDistancesMeters);
        assertEquals(0, match.detourMeters(), 0.001);
        assertEquals(2, fake.calls.get(1).size());
    }
    /** Không nới kiểm tra quốc gia khi mọi phương án vẫn đi qua nước khác. */
    @Test public void foreignRoutesAreNeverAcceptedAndAttemptsAreBounded() throws Exception {
        Fake fake = new Fake(); fake.foreignUntil = 99;
        try { fake.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Việt Nam")); }
        assertEquals(3, fake.calls.size());
    }
    /** Quota dừng ngay, không thử thêm waypoint; hủy trước tác vụ không gửi HTTP. */
    @Test public void terminalQuotaAndCancellationDoNotRetry() throws Exception {
        Fake fake = new Fake(); fake.quota = true;
        try { fake.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null); fail(); }
        catch (VietnamRoutingClient.RoutingException expected) { assertEquals(VietnamRoutingClient.Failure.QUOTA, expected.failure); }
        assertEquals(1, fake.calls.size());
        final Fake cancelled = new Fake();
        try {
            cancelled.findMatchRoute(Arrays.asList(a, b, c, d), 1000, new VietnamRoutingClient.Cancellation() {
                /** Lượt đã bị thay thế trước khi tính tuyến. */
                @Override public boolean isCancelled() { return true; }
            });
            fail();
        } catch (IOException expected) { assertEquals(0, cancelled.calls.size()); }
    }

    /** Cùng A/D không cần tải lại baseline đã xác minh hoặc phương án ngoại quốc đã bị loại. */
    @Test public void verifiedBaselineCacheAvoidsDuplicateRequestsWithinSearch() throws Exception {
        Fake fake = new Fake();
        Map<String, Double> cache = new HashMap<>();
        fake.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null, cache);
        assertEquals(2, fake.calls.size());
        fake.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null, cache);
        assertEquals(3, fake.calls.size());
        Fake guided = new Fake(); guided.foreignUntil = 1;
        Map<String, Double> guidedCache = new HashMap<>();
        guided.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null, guidedCache);
        assertEquals(3, guided.calls.size());
        guided.findMatchRoute(Arrays.asList(a, b, c, d), 1000, null, guidedCache);
        assertEquals(4, guided.calls.size());
    }

    /** Hủy sau baseline phải chặn request đường ghép của lượt cũ. */
    @Test public void cancellationBetweenBaselineAndJoinedRouteStopsSecondRequest() throws Exception {
        final boolean[] cancelled = {false};
        Fake fake = new Fake() {
            /** Mô phỏng người dùng đổi điểm khi response đầu tiên vừa về. */
            @Override protected JSONObject request(List<Point> points, boolean avoid, Cancellation cancellation)
                    throws IOException, JSONException {
                JSONObject response = super.request(points, avoid, cancellation);
                cancelled[0] = true;
                return response;
            }
        };
        try {
            fake.findMatchRoute(Arrays.asList(a, b, c, d), 1000, new VietnamRoutingClient.Cancellation() {
                /** Đọc tín hiệu đổi lượt sau response baseline. */
                @Override public boolean isCancelled() { return cancelled[0]; }
            });
            fail();
        } catch (IOException expected) { assertEquals(1, fake.calls.size()); }
    }
}
