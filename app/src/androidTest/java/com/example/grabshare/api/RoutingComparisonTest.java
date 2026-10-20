package com.example.grabshare.api;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.grabshare.activity.search.ResultsPipelineTest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kiểm tra planner/parser thật trên Android với phản hồi có kiểm soát. */
@RunWith(AndroidJUnit4.class)
public class RoutingComparisonTest {

    private final VietnamRoutingClient.Point driverOriginPoint =
            new VietnamRoutingClient.Point(10.76, 106.66);

    private final VietnamRoutingClient.Point passengerPickupPoint =
            new VietnamRoutingClient.Point(12.24, 109.19);

    private final VietnamRoutingClient.Point passengerDropoffPoint =
            new VietnamRoutingClient.Point(19.8, 105.77);

    private final VietnamRoutingClient.Point driverDestinationPoint =
            new VietnamRoutingClient.Point(21.029, 105.854);

    /** Chỉ thay transport, giữ nguyên việc phân chặng và xác minh quốc gia. */
    static class Fake extends VietnamRoutingClient {

        final List<List<Point>> calls = new ArrayList<List<Point>>();

        int foreignUntil;

        boolean quota;

        boolean cancel;

        /** Credential giả không bao giờ được đưa ra mạng. */
        Fake() {
            super("fixture");
        }

        /** Trả tuyến đủ metadata theo hình học request và kịch bản kiểm tra. */
        @Override
        protected JSONObject request(List<Point> points, boolean avoid, Cancellation cancellation)
                throws IOException, JSONException {
            calls.add(new ArrayList<Point>(points));
            if (quota) {
                throw new RoutingException(Failure.QUOTA, "quota");
            }
            boolean original = points.size() == 2 || (points.size() > 4 && points.size() <= 7);
            if (original) {
                if (calls.size() <= foreignUntil) {
                    return ResultsPipelineTest.response(points, 1000, "la");
                } else {
                    return ResultsPipelineTest.response(points, 1000, "vn");
                }
            } else {
                if (calls.size() <= foreignUntil) {
                    return ResultsPipelineTest.response(points, 1100, "la");
                } else {
                    return ResultsPipelineTest.response(points, 1100, "vn");
                }
            }
        }
    }

    /** Baseline DB lệch phải hiệu chỉnh trên RAM, không làm âm detour rồi nhận nhầm chuyến. */
    @Test
    public void staleBaselineUsesComparableCurrentRoute() throws Exception {
        Fake fake = new Fake();
        VietnamRoutingClient.MatchRoute match =
                fake.findMatchRoute(
                        Arrays.asList(
                                driverOriginPoint,
                                passengerPickupPoint,
                                passengerDropoffPoint,
                                driverDestinationPoint),
                        2000,
                        null);
        assertTrue(match.baselineAdjusted);
        assertEquals(1000, match.baselineMeters, 0.001);
        assertEquals(100, match.detourMeters(), 0.001);
        assertEquals(3, match.route.requiredLegDistancesMeters.size());
        assertEquals(1100.0 / 3, match.route.requiredLegDistancesMeters.get(0), 0.001);
    }

    /** Chênh nhỏ do làm tròn vẫn dùng baseline đã lưu như hợp đồng hiện tại. */
    @Test
    public void matchingBaselineIsReusedWithinFiftyMeters() throws Exception {
        Fake fake = new Fake();
        VietnamRoutingClient.MatchRoute match =
                fake.findMatchRoute(
                        Arrays.asList(
                                driverOriginPoint,
                                passengerPickupPoint,
                                passengerDropoffPoint,
                                driverDestinationPoint),
                        1020,
                        null);
        assertFalse(match.baselineAdjusted);
        assertEquals(80, match.detourMeters(), 0.001);
    }

    /** Mốc dẫn hướng của A/D xuất hiện cùng thứ tự trong cả hai tuyến. */
    @Test
    public void fallbackSharesCorridorAndKeepsPickupBeforeDropoff() throws Exception {
        Fake fake = new Fake();
        fake.foreignUntil = 1;
        VietnamRoutingClient.MatchRoute match =
                fake.findMatchRoute(
                        Arrays.asList(
                                driverOriginPoint,
                                passengerPickupPoint,
                                passengerDropoffPoint,
                                driverDestinationPoint),
                        1000,
                        null);
        assertTrue(match.route.guided);
        assertEquals(3, fake.calls.size());
        List<VietnamRoutingClient.Point> baseline = fake.calls.get(1);
        List<VietnamRoutingClient.Point> joined = fake.calls.get(2);
        int pickupIndex = -1;
        int dropoffIndex = -1;
        for (int index = 0; index < joined.size(); index = index + 1) {
            if (Math.abs(joined.get(index).latitude - passengerPickupPoint.latitude) < 0.000001
                    && Math.abs(joined.get(index).longitude - passengerPickupPoint.longitude)
                            < 0.000001) {
                pickupIndex = index;
            }
            if (Math.abs(joined.get(index).latitude - passengerDropoffPoint.latitude) < 0.000001
                    && Math.abs(joined.get(index).longitude - passengerDropoffPoint.longitude)
                            < 0.000001) {
                dropoffIndex = index;
            }
        }
        assertTrue(pickupIndex >= 0);
        assertTrue(dropoffIndex > pickupIndex);
        int last = -1;
        for (int index = 1; index < baseline.size() - 1; index = index + 1) {
            VietnamRoutingClient.Point anchor = baseline.get(index);
            int found = -1;
            for (int followingIndex = last + 1;
                    followingIndex < joined.size();
                    followingIndex = followingIndex + 1) {
                if (Math.abs(joined.get(followingIndex).latitude - anchor.latitude) < 0.000001
                        && Math.abs(joined.get(followingIndex).longitude - anchor.longitude)
                                < 0.000001) {
                    found = followingIndex;
                    break;
                }
            }
            assertTrue(found > last);
            last = found;
        }
        assertEquals(3, match.route.requiredLegDistancesMeters.size());
        double total = 0;
        for (double distance : match.route.requiredLegDistancesMeters) {
            total = total + (distance);
        }
        assertEquals(match.route.distanceMeters, total, 0.001);
    }

    /** Các chặng có cùng tọa độ vẫn được trả về với số mét bằng 0. */
    @Test
    public void identicalPickupOriginDoesNotLoseRequiredLegs() throws Exception {
        Fake fake = new Fake();
        VietnamRoutingClient.MatchRoute match =
                fake.findMatchRoute(
                        Arrays.asList(
                                driverOriginPoint,
                                driverOriginPoint,
                                driverDestinationPoint,
                                driverDestinationPoint),
                        1000,
                        null);
        assertEquals(Arrays.asList(0.0, 1000.0, 0.0), match.route.requiredLegDistancesMeters);
        assertEquals(0, match.detourMeters(), 0.001);
        assertEquals(2, fake.calls.get(1).size());
    }

    /** Không nới kiểm tra quốc gia khi mọi phương án vẫn đi qua nước khác. */
    @Test
    public void foreignRoutesAreNeverAcceptedAndAttemptsAreBounded() throws Exception {
        Fake fake = new Fake();
        fake.foreignUntil = 99;
        try {
            fake.findMatchRoute(
                    Arrays.asList(
                            driverOriginPoint,
                            passengerPickupPoint,
                            passengerDropoffPoint,
                            driverDestinationPoint),
                    1000,
                    null);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Việt Nam"));
        }
        assertEquals(3, fake.calls.size());
    }

    /** Quota dừng ngay, không thử thêm waypoint; hủy trước tác vụ không gửi HTTP. */
    @Test
    public void terminalQuotaAndCancellationDoNotRetry() throws Exception {
        Fake fake = new Fake();
        fake.quota = true;
        try {
            fake.findMatchRoute(
                    Arrays.asList(
                            driverOriginPoint,
                            passengerPickupPoint,
                            passengerDropoffPoint,
                            driverDestinationPoint),
                    1000,
                    null);
            fail();
        } catch (VietnamRoutingClient.RoutingException expected) {
            assertEquals(VietnamRoutingClient.Failure.QUOTA, expected.failure);
        }
        assertEquals(1, fake.calls.size());
        final Fake cancelled = new Fake();
        try {
            cancelled.findMatchRoute(
                    Arrays.asList(
                            driverOriginPoint,
                            passengerPickupPoint,
                            passengerDropoffPoint,
                            driverDestinationPoint),
                    1000,
                    new VietnamRoutingClient.Cancellation() {

                        /** Lượt đã bị thay thế trước khi tính tuyến. */
                        @Override
                        public boolean isCancelled() {
                            return true;
                        }
                    });
            fail();
        } catch (IOException expected) {
            assertEquals(0, cancelled.calls.size());
        }
    }

    /** Cùng A/D không cần tải lại baseline đã xác minh hoặc phương án ngoại quốc đã bị loại. */
    @Test
    public void verifiedBaselineCacheAvoidsDuplicateRequestsWithinSearch() throws Exception {
        Fake fake = new Fake();
        Map<String, Double> cache = new HashMap<String, Double>();
        fake.findMatchRoute(
                Arrays.asList(
                        driverOriginPoint,
                        passengerPickupPoint,
                        passengerDropoffPoint,
                        driverDestinationPoint),
                1000,
                null,
                cache);
        assertEquals(2, fake.calls.size());
        fake.findMatchRoute(
                Arrays.asList(
                        driverOriginPoint,
                        passengerPickupPoint,
                        passengerDropoffPoint,
                        driverDestinationPoint),
                1000,
                null,
                cache);
        assertEquals(3, fake.calls.size());
        Fake guided = new Fake();
        guided.foreignUntil = 1;
        Map<String, Double> guidedCache = new HashMap<String, Double>();
        guided.findMatchRoute(
                Arrays.asList(
                        driverOriginPoint,
                        passengerPickupPoint,
                        passengerDropoffPoint,
                        driverDestinationPoint),
                1000,
                null,
                guidedCache);
        assertEquals(3, guided.calls.size());
        guided.findMatchRoute(
                Arrays.asList(
                        driverOriginPoint,
                        passengerPickupPoint,
                        passengerDropoffPoint,
                        driverDestinationPoint),
                1000,
                null,
                guidedCache);
        assertEquals(4, guided.calls.size());
    }

    /** Hủy sau baseline phải chặn request đường ghép của lượt cũ. */
    @Test
    public void cancellationBetweenBaselineAndJoinedRouteStopsSecondRequest() throws Exception {
        final boolean[] cancelled = {false};
        Fake fake =
                new Fake() {

                    /** Mô phỏng người dùng đổi điểm khi response đầu tiên vừa về. */
                    @Override
                    protected JSONObject request(
                            List<Point> points, boolean avoid, Cancellation cancellation)
                            throws IOException, JSONException {
                        JSONObject response = super.request(points, avoid, cancellation);
                        cancelled[0] = true;
                        return response;
                    }
                };
        try {
            fake.findMatchRoute(
                    Arrays.asList(
                            driverOriginPoint,
                            passengerPickupPoint,
                            passengerDropoffPoint,
                            driverDestinationPoint),
                    1000,
                    new VietnamRoutingClient.Cancellation() {

                        /** Đọc tín hiệu đổi lượt sau response baseline. */
                        @Override
                        public boolean isCancelled() {
                            return cancelled[0];
                        }
                    });
            fail();
        } catch (IOException expected) {
            assertEquals(1, fake.calls.size());
        }
    }
}
