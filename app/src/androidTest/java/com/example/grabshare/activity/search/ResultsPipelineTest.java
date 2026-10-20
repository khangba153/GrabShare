package com.example.grabshare.activity.search;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.grabshare.api.VietnamRoutingClient;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.EnRouteSearch;
import com.example.grabshare.repository.search.SearchRequestBudget;
import com.example.grabshare.repository.search.TripSearchRepository;
import com.google.firebase.Timestamp;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Chạy pipeline thật với nguồn đọc và Routing giả lập; không ghi dữ liệu Firebase. */
@RunWith(AndroidJUnit4.class)
public class ResultsPipelineTest {
    /** Nguồn đọc có thể giữ callback để tái hiện callback cũ sau Refresh. */
    static class Model extends ResultsViewModel {
        final AtomicInteger queryCount = new AtomicInteger();
        final AtomicInteger routeCount = new AtomicInteger();
        final List<RepositoryCallback<TripSearchRepository.SearchSnapshot>> callbacks = new ArrayList<>();
        volatile String user = "rider";
        volatile boolean quota;
        volatile boolean hold;
        volatile boolean holdRoutes;
        final CountDownLatch routeEntered = new CountDownLatch(1);
        final CountDownLatch routeReleased = new CountDownLatch(1);
        int count = 25;
        /** UID kiểm tra không được dùng để đăng nhập Firebase. */
        @Override protected String currentUserId() { return user; }
        /** Trả fixture trong RAM, không gọi DB. */
        @Override protected void queryTrips(long start, long end, RepositoryCallback<TripSearchRepository.SearchSnapshot> callback) {
            queryCount.incrementAndGet();
            callbacks.add(callback);
            if (!hold) callback.onSuccess(snapshot(count));
        }
        /** Profile lỗi không được xóa chuyến đã đạt kiểm tra tuyến. */
        @Override protected void queryProfile(String id, RepositoryCallback<PublicProfile> callback) {
            callback.onError(new IOException("fixture: no profile"));
        }
        /** Giữ nguyên planner/parser thật, chỉ thay transport HTTP. */
        @Override protected VietnamRoutingClient createRoutingClient(final SearchRequestBudget budget) {
            return new VietnamRoutingClient("fixture") {
                /** Mô phỏng baseline và đường ghép, vẫn đếm từng HTTP vào ngân sách thật. */
                @Override protected JSONObject request(List<Point> points, boolean avoid, Cancellation cancellation)
                        throws IOException, JSONException {
                    budget.beforeRequest();
                    routeCount.incrementAndGet();
                    if (holdRoutes) {
                        routeEntered.countDown();
                        try { routeReleased.await(10, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("fixture: cancelled");
                        }
                    }
                    if (quota) throw new RoutingException(Failure.QUOTA, "fixture: quota");
                    int id = (int) Math.round((points.get(0).latitude - 10.75) * 100000);
                    double total = points.size() == 2 ? 1000 : id < 12 ? 3000 : 1100;
                    return response(points, total, "vn");
                }
            };
        }
    }

    /** Tạo tuyến JSON đủ metadata và geometry theo đúng số chặng của request. */
    public static JSONObject response(List<VietnamRoutingClient.Point> points, double total, String country) throws JSONException {
        JSONArray legs = new JSONArray();
        JSONArray segments = new JSONArray();
        for (int i = 1; i < points.size(); i++) {
            legs.put(new JSONObject().put("distance", total / (points.size() - 1))
                    .put("country_code", new JSONArray().put(country)));
            segments.put(new JSONArray()
                    .put(new JSONArray().put(points.get(i - 1).longitude).put(points.get(i - 1).latitude))
                    .put(new JSONArray().put(points.get(i).longitude).put(points.get(i).latitude)));
        }
        JSONObject properties = new JSONObject().put("distance", total).put("time", 100)
                .put("distance_units", "meters").put("legs", legs)
                .put("country_code", new JSONArray().put(country))
                .put("admin_areas", new JSONArray().put(new JSONObject().put("country_code", country)));
        return new JSONObject().put("features", new JSONArray().put(new JSONObject().put("properties", properties)
                .put("geometry", new JSONObject().put("type", "MultiLineString").put("coordinates", segments))));
    }

    /** Tạo chuyến có giờ tương lai đủ lâu để chạy kiểm tra, không ghi server. */
    static TripSearchRepository.SearchSnapshot snapshot(int count) {
        TripSearchRepository.SearchSnapshot result = new TripSearchRepository.SearchSnapshot();
        for (int i = 0; i < count; i++) {
            Trip trip = new Trip("driver", new Place("A", 10.75 + i / 100000.0, 106.65),
                    new Place("D", 10.9, 106.8), new Timestamp((System.currentTimeMillis() + 86400000) / 1000, 0),
                    (long) (10000 + i * 1000), "", "OPEN", 1L, "", Timestamp.now(), Timestamp.now());
            trip.setId("trip-" + i);
            trip.setBaseDistanceMeters(1000L);
            trip.setMaxDetourMeters(0L);
            result.trips.add(trip);
        }
        return result;
    }

    /** Cùng ngày với fixture chuyến, không phụ thuộc giờ chạy hiện tại. */
    static EnRouteSearch.Criteria criteria() {
        return new EnRouteSearch.Criteria(new Place("B", 10.8, 106.7), new Place("C", 10.85, 106.75),
                System.currentTimeMillis() + 86400000, "rider");
    }
    /** Thực thi thao tác UI trên main looper mà không dùng Handler. */
    static void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    /** Chờ trạng thái tác vụ thật kết thúc, có deadline thay vì sleep cố định dài. */
    static ResultsViewModel.State await(Model model) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        ResultsViewModel.State state;
        do {
            state = model.getState().getValue();
            if (state != null && !state.busy) return state;
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Pipeline did not finish");
    }

    /** Hai nhóm đầu lấy bù, Xem thêm không query lại, sort/giá không gọi Routing. */
    @Test public void batchesAutofillAndSortReuseSameSnapshot() throws Exception {
        final Model[] box = new Model[1];
        main(new Runnable() {
            /** Tạo ViewModel trên main như Activity. */
            @Override public void run() { box[0] = new Model(); box[0].initialize(criteria()); }
        });
        final Model model = box[0];
        try {
            ResultsViewModel.State first = await(model);
            assertEquals(8, first.matches.size());
            assertTrue(first.hasMore);
            assertEquals(1, model.queryCount.get());
            assertEquals(40, model.routeCount.get());
            main(new Runnable() {
                /** Xoay gọi initialize lại và đổi sắp xếp không tạo lượt đọc khác. */
                @Override public void run() {
                    model.initialize(criteria());
                    model.setSort(EnRouteSearch.Sort.PRICE);
                    model.setMaxPrice(0L);
                }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(model.getState().getValue().matches.isEmpty());
            assertEquals(1, model.queryCount.get());
            assertEquals(40, model.routeCount.get());
            main(new Runnable() {
                /** Gỡ lọc và xử lý phần còn chờ. */
                @Override public void run() { model.setMaxPrice(null); model.loadMore(); }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            ResultsViewModel.State second = await(model);
            assertEquals(13, second.matches.size());
            assertFalse(second.hasMore);
            assertEquals(1, model.queryCount.get());
            HashSet<String> ids = new HashSet<>();
            for (EnRouteSearch.Match match : second.matches) assertTrue(ids.add(match.trip.getId()));
        } finally { main(new Runnable() { /** Giải phóng executor. */ @Override public void run() { model.onCleared(); } }); }
    }

    /** Quota giữ nguyên ứng viên chưa đánh giá và không giả thành không phù hợp. */
    @Test public void quotaDefersCandidatesInsteadOfRejectingThem() throws Exception {
        final Model[] box = new Model[1];
        main(new Runnable() {
            /** Bật lỗi quota từ request đầu. */
            @Override public void run() { box[0] = new Model(); box[0].quota = true; box[0].count = 10; box[0].initialize(criteria()); }
        });
        final Model model = box[0];
        try {
            ResultsViewModel.State result = await(model);
            assertTrue(result.matches.isEmpty());
            assertTrue(result.hasMore);
            assertTrue(result.message.contains("quota"));
            assertTrue(model.routeCount.get() <= 2);
        } finally { main(new Runnable() { /** Giải phóng executor. */ @Override public void run() { model.onCleared(); } }); }
    }

    /** Callback của snapshot cũ sau Refresh không được thay hàng đợi mới. */
    @Test public void refreshIgnoresOldSnapshot() throws Exception {
        final Model[] box = new Model[1];
        main(new Runnable() {
            /** Giữ snapshot đầu rồi mở lượt mới trước khi trả callback. */
            @Override public void run() {
                box[0] = new Model(); box[0].hold = true; box[0].initialize(criteria()); box[0].refresh();
                box[0].callbacks.get(1).onSuccess(snapshot(0));
            }
        });
        final Model model = box[0];
        try {
            await(model);
            main(new Runnable() {
                /** Trả callback cũ có 25 chuyến sau khi lượt mới đã kết thúc. */
                @Override public void run() { model.callbacks.get(0).onSuccess(snapshot(25)); }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(0, model.routeCount.get());
            assertTrue(model.getState().getValue().matches.isEmpty());
            assertFalse(model.getState().getValue().hasMore);
            assertEquals(2, model.queryCount.get());
        } finally { main(new Runnable() { /** Giải phóng executor. */ @Override public void run() { model.onCleared(); } }); }
    }

    /** Đổi lượt trong lúc HTTP chưa về không được nhận kết quả của lượt trước. */
    @Test public void refreshCancelsInFlightRouting() throws Exception {
        final Model[] box = new Model[1];
        main(new Runnable() {
            /** Tạm giữ transport để có thể Refresh giữa hai response. */
            @Override public void run() { box[0] = new Model(); box[0].holdRoutes = true; box[0].initialize(criteria()); }
        });
        final Model model = box[0];
        try {
            assertTrue(model.routeEntered.await(5, TimeUnit.SECONDS));
            main(new Runnable() {
                /** Lượt mới không có chuyến, callback cũ có chuyến vẫn không được ghi vào. */
                @Override public void run() { model.count = 0; model.refresh(); }
            });
            model.routeReleased.countDown();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            ResultsViewModel.State result = await(model);
            assertTrue(result.matches.isEmpty());
            assertFalse(result.hasMore);
            assertEquals(2, model.queryCount.get());
        } finally { main(new Runnable() { /** Giải phóng executor. */ @Override public void run() { model.onCleared(); } }); }
    }

    /** Tài khoản mới không nhìn kết quả và không dùng nút Xem thêm của tài khoản cũ. */
    @Test public void accountChangeInvalidatesSessionAndPendingWork() throws Exception {
        final Model[] box = new Model[1];
        main(new Runnable() {
            /** Chuẩn bị phiên rider có kết quả và hàng đợi. */
            @Override public void run() { box[0] = new Model(); box[0].initialize(criteria()); }
        });
        final Model model = box[0];
        try {
            assertFalse(await(model).matches.isEmpty());
            final int requests = model.routeCount.get();
            main(new Runnable() {
                /** Chỉ đổi session giả lập, không đăng nhập hoặc logout Firebase thật. */
                @Override public void run() { model.user = "another-user"; model.revalidate(); model.loadMore(); model.refresh(); }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            ResultsViewModel.State result = model.getState().getValue();
            assertFalse(result.sessionValid);
            assertTrue(result.matches.isEmpty());
            assertFalse(result.hasMore);
            assertFalse(result.busy);
            assertEquals(requests, model.routeCount.get());
            assertEquals(1, model.queryCount.get());
        } finally { main(new Runnable() { /** Giải phóng executor. */ @Override public void run() { model.onCleared(); } }); }
    }
}
