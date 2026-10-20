package com.example.grabshare.activity.search;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.grabshare.api.VietnamRoutingClient;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.EnRouteSearch;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Kiểm tra đọc thật chỉ khi bật live=true; không tạo user, trip hay đổi Rules. */
@RunWith(AndroidJUnit4.class)
public class SearchLiveReadTest {

    /** Gọi đúng HttpURLConnection trên Android thay vì chỉ dùng transport máy tính. */
    @Test
    public void liveRoutingHasFourOrderedVietnamPoints() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live")));
        VietnamRoutingClient client = VietnamRoutingClient.forSearch(null);
        VietnamRoutingClient.Point driverOriginPoint =
                new VietnamRoutingClient.Point(10.762622, 106.660172);
        VietnamRoutingClient.Point passengerPickupPoint =
                new VietnamRoutingClient.Point(10.7609491, 106.6679786);
        VietnamRoutingClient.Point passengerDropoffPoint =
                new VietnamRoutingClient.Point(10.7828045, 106.6856203);
        VietnamRoutingClient.Point driverDestinationPoint =
                new VietnamRoutingClient.Point(10.7830141, 106.6948927);
        VietnamRoutingClient.Route original =
                client.findRoute(Arrays.asList(driverOriginPoint, driverDestinationPoint), null);
        VietnamRoutingClient.MatchRoute joined =
                client.findMatchRoute(
                        Arrays.asList(
                                driverOriginPoint,
                                passengerPickupPoint,
                                passengerDropoffPoint,
                                driverDestinationPoint),
                        Math.round(original.distanceMeters),
                        null);
        assertEquals(3, joined.route.requiredLegDistancesMeters.size());
        assertTrue(joined.route.requiredLegDistancesMeters.get(0) > 0);
        assertTrue(joined.route.distanceMeters > 0);
        assertTrue(joined.detourMeters() >= 0);
        System.out.println(
                "LIVE_ROUTING base_m="
                        + joined.baselineMeters
                        + " joined_m="
                        + joined.route.distanceMeters
                        + " start_gap_m="
                        + joined.route.requiredLegDistancesMeters.get(0)
                        + " detour_m="
                        + joined.detourMeters());
    }

    /** Đọc query/index bằng phiên đang có; không tự đăng nhập hoặc seed để tạo kết quả. */
    @Test
    public void liveFirestoreQueryUsesExistingSessionAndIndex() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live")));
        Assume.assumeNotNull(FirebaseAuth.getInstance().getCurrentUser());
        long[] window =
                EnRouteSearch.timeWindow(System.currentTimeMillis(), System.currentTimeMillis());
        QuerySnapshot snapshot =
                Tasks.await(
                        FirebaseFirestore.getInstance()
                                .collection("trips")
                                .whereEqualTo("status", "OPEN")
                                .whereGreaterThanOrEqualTo("departureAt", timestampAt(window[0]))
                                .whereLessThanOrEqualTo("departureAt", timestampAt(window[1]))
                                .orderBy("departureAt")
                                .get(Source.SERVER),
                        30,
                        TimeUnit.SECONDS);
        assertFalse(snapshot.getMetadata().isFromCache());
        int configured = 0;
        for (com.google.firebase.firestore.DocumentSnapshot tripDocument :
                snapshot.getDocuments()) {
            if (tripDocument.getLong("baseDistanceMeters") != null
                    && tripDocument.getLong("maxDetourMeters") != null) {
                configured = configured + 1;
            }
        }
        System.out.println(
                "LIVE_FIRESTORE open_future="
                        + snapshot.size()
                        + " with_route_metadata="
                        + configured);
    }

    /** Chẩn đoán chỉ đọc theo ngày được yêu cầu, không log UID/token hoặc tên địa điểm cá nhân. */
    @Test
    public void diagnoseSelectedDayWithoutWritingData() throws Exception {
        String selectedDate = InstrumentationRegistry.getArguments().getString("diagnose_date");
        Assume.assumeNotNull(selectedDate);
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live")));
        Assume.assumeNotNull(FirebaseAuth.getInstance().getCurrentUser());
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        date.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        date.setLenient(false);
        String requestedTime =
                InstrumentationRegistry.getArguments().getString("diagnose_time", "12:00");
        SimpleDateFormat inputTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        inputTime.setTimeZone(date.getTimeZone());
        inputTime.setLenient(false);
        long selected = inputTime.parse(selectedDate + " " + requestedTime).getTime();
        long now = System.currentTimeMillis();
        long[] window = EnRouteSearch.timeWindow(selected, now);
        SimpleDateFormat display = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
        display.setTimeZone(date.getTimeZone());
        System.out.println(
                "SEARCH_DIAG selected="
                        + selectedDate
                        + " now_vn="
                        + display.format(now)
                        + " lower_vn="
                        + display.format(window[0])
                        + " upper_inclusive_vn="
                        + display.format(window[1]));
        QuerySnapshot snapshot =
                Tasks.await(
                        FirebaseFirestore.getInstance()
                                .collection("trips")
                                .whereEqualTo("status", "OPEN")
                                .whereGreaterThanOrEqualTo("departureAt", timestampAt(window[0]))
                                .whereLessThanOrEqualTo("departureAt", timestampAt(window[1]))
                                .orderBy("departureAt")
                                .get(Source.SERVER),
                        30,
                        TimeUnit.SECONDS);
        String userIdentifier = FirebaseAuth.getInstance().getCurrentUser().getUid();
        ArrayList<Trip> trips = new ArrayList<Trip>();
        int own = 0;
        int row = 0;
        for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
            Trip trip = document.toObject(Trip.class);
            if (trip == null) {
                continue;
            }
            trip.setId(document.getId());
            trips.add(trip);
            boolean owner = userIdentifier.equals(trip.getDriverId());
            if (owner) {
                own = own + 1;
            }
            row = row + 1;
            System.out.println(
                    "SEARCH_DIAG row="
                            + row
                            + " departure_vn="
                            + display.format(trip.getDepartureAt().toDate())
                            + " own="
                            + owner
                            + " seats="
                            + trip.getAvailableSeats()
                            + " base_m="
                            + trip.getBaseDistanceMeters()
                            + " max_detour_m="
                            + trip.getMaxDetourMeters());
        }
        // Điểm chuẩn của cơ sở A/B chỉ để kiểm tra pipeline; không giả là tọa độ form đang mở.
        EnRouteSearch.Prepared prepared =
                EnRouteSearch.prepare(
                        trips,
                        new EnRouteSearch.Criteria(
                                new Place("UEH A chuẩn", 10.7830141, 106.6948927),
                                new Place("UEH B chuẩn", 10.7609491, 106.6679786),
                                selected,
                                userIdentifier),
                        now);
        System.out.println(
                "SEARCH_DIAG open_future="
                        + snapshot.size()
                        + " own="
                        + own
                        + " other="
                        + (snapshot.size() - own)
                        + " candidates_canonical_ueh="
                        + prepared.candidates.size()
                        + " missing_metadata="
                        + prepared.missingMetadata
                        + " invalid="
                        + prepared.invalid
                        + " preliminary_rejected="
                        + prepared.preliminaryRejected);
        assertFalse(snapshot.getMetadata().isFromCache());
    }

    /** Giữ cùng độ chính xác và hai cận bao gồm như truy vấn của ứng dụng. */
    private static Timestamp timestampAt(long timestampMilliseconds) {
        return new Timestamp(
                timestampMilliseconds / 1000, (int) (timestampMilliseconds % 1000) * 1000000);
    }
}
