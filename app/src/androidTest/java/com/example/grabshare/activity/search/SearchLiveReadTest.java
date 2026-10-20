package com.example.grabshare.activity.search;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.grabshare.api.VietnamRoutingClient;
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Kiểm tra đọc thật chỉ khi bật live=true; không tạo user, trip hay đổi Rules. */
@RunWith(AndroidJUnit4.class)
public class SearchLiveReadTest {
    /** Gọi đúng HttpURLConnection trên Android thay vì chỉ dùng transport máy tính. */
    @Test public void liveRoutingHasFourOrderedVietnamPoints() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live")));
        VietnamRoutingClient client = VietnamRoutingClient.forSearch(null);
        VietnamRoutingClient.Point a = new VietnamRoutingClient.Point(10.762622, 106.660172);
        VietnamRoutingClient.Point b = new VietnamRoutingClient.Point(10.7609491, 106.6679786);
        VietnamRoutingClient.Point c = new VietnamRoutingClient.Point(10.7828045, 106.6856203);
        VietnamRoutingClient.Point d = new VietnamRoutingClient.Point(10.7830141, 106.6948927);
        VietnamRoutingClient.Route original = client.findRoute(Arrays.asList(a, d), null);
        VietnamRoutingClient.MatchRoute joined = client.findMatchRoute(Arrays.asList(a, b, c, d), Math.round(original.distanceMeters), null);
        assertEquals(3, joined.route.requiredLegDistancesMeters.size());
        assertTrue(joined.route.requiredLegDistancesMeters.get(0) > 0);
        assertTrue(joined.route.distanceMeters > 0);
        assertTrue(joined.detourMeters() >= 0);
        System.out.println("LIVE_ROUTING base_m=" + joined.baselineMeters + " joined_m=" + joined.route.distanceMeters
                + " start_gap_m=" + joined.route.requiredLegDistancesMeters.get(0) + " detour_m=" + joined.detourMeters());
    }
    /** Đọc query/index bằng phiên đang có; không tự đăng nhập hoặc seed để tạo kết quả. */
    @Test public void liveFirestoreQueryUsesExistingSessionAndIndex() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live")));
        Assume.assumeNotNull(FirebaseAuth.getInstance().getCurrentUser());
        long[] window = EnRouteSearch.timeWindow(System.currentTimeMillis(), System.currentTimeMillis());
        QuerySnapshot snapshot = Tasks.await(FirebaseFirestore.getInstance().collection("trips")
                .whereEqualTo("status", "OPEN")
                .whereGreaterThanOrEqualTo("departureAt", new Timestamp(window[0] / 1000, 0))
                .whereLessThan("departureAt", new Timestamp(window[1] / 1000, 0))
                .orderBy("departureAt").get(Source.SERVER), 30, TimeUnit.SECONDS);
        assertFalse(snapshot.getMetadata().isFromCache());
        int configured = 0;
        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
            if (doc.getLong("baseDistanceMeters") != null && doc.getLong("maxDetourMeters") != null) configured++;
        }
        System.out.println("LIVE_FIRESTORE open_future=" + snapshot.size() + " with_route_metadata=" + configured);
    }
}
