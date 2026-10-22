package com.example.grabshare.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tính tuyến xe máy và chỉ công bố tuyến đã được API xác nhận nằm trong Việt Nam. */
public class VietnamRoutingClient {

    /** Tái sử dụng cấu hình Routing hiện có của ứng dụng cho worker tìm chuyến. */
    public static VietnamRoutingClient forSearch(RequestGate gate) {
        return new VietnamRoutingClient("3ae0ba5bcf8843df8024203662e3d702", gate);
    }

    public static final String POLICY_VERSION = "vn-motorcycle-balanced-v1";

    private final String apiKey;

    private final RequestGate requestGate;

    private volatile HttpURLConnection activeConnection;

    /** Điểm tuyến độc lập với Activity, Place và thư viện hiển thị bản đồ. */
    public static final class Point {

        public final double latitude;

        public final double longitude;

        /** Kiểm tra tọa độ trước khi tạo waypoint. */
        public Point(double latitude, double longitude) {
            if (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || latitude < -90
                    || latitude > 90
                    || longitude < -180
                    || longitude > 180) {
                throw new IllegalArgumentException("Tọa độ tìm đường không hợp lệ.");
            }
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** Kết quả đã kiểm tra; không nối thẳng các đoạn geometry bị tách rời. */
    public static final class Route {

        public final double distanceMeters;

        public final double durationSeconds;

        public final List<List<Point>> segments;

        public final List<Double> requiredLegDistancesMeters;

        public final boolean guided;

        public final int requestCount;

        /** Lưu kết quả bất biến để callback không đổi dữ liệu sau khi đã hiển thị. */
        private Route(
                double totalDistanceMeters,
                double totalDurationSeconds,
                List<List<Point>> segments,
                List<Double> requiredLegDistances,
                boolean guided,
                int requestCount) {
            distanceMeters = totalDistanceMeters;
            durationSeconds = totalDurationSeconds;
            this.segments = Collections.unmodifiableList(segments);
            requiredLegDistancesMeters = Collections.unmodifiableList(requiredLegDistances);
            this.guided = guided;
            this.requestCount = requestCount;
        }
    }

    /** Caller cung cấp thế hệ yêu cầu để hủy tác vụ đã bị thay thế. */
    public interface Cancellation {

        /** Trả true khi màn đóng hoặc các điểm đã thay đổi. */
        boolean isCancelled();
    }

    /** Bộ điều tiết của lượt tìm, đếm từng HTTP kể cả các lần thử hành lang. */
    public interface RequestGate {

        /** Ném lỗi có kiểu khi hết ngân sách hoặc lượt tìm đã bị hủy. */
        void beforeRequest() throws RoutingException;
    }

    public enum Failure {
        QUOTA,
        ACCESS,
        INPUT,
        BUDGET
    }

    /** Lỗi có kiểu để Results dừng cả nhóm khi quota/quyền bị từ chối. */
    public static final class RoutingException extends IOException {

        public final Failure failure;

        /** Chỉ giữ thông báo an toàn, không chứa URL hoặc credential. */
        public RoutingException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }
    }

    /** Cặp tuyến sử dụng cùng cấu hình và cùng bộ mốc hành lang. */
    public static final class MatchRoute {

        public final Route route;

        public final double baselineMeters;

        public final boolean baselineAdjusted;

        /** Quyết định luôn dùng baseline vừa đo cùng policy; số lưu chỉ để báo thay đổi. */
        private MatchRoute(Route route, double freshBaseline, long storedBaseline) {
            this.route = route;
            baselineAdjusted = Math.abs(freshBaseline - storedBaseline) > 50;
            baselineMeters = freshBaseline;
        }

        /** Tính mức đi thêm không âm từ hai tuyến đã được kiểm tra tương thích. */
        public double detourMeters() {
            return Math.max(0, route.distanceMeters - baselineMeters);
        }
    }

    /** Điểm dẫn hướng trong nước; chỉ dùng khi tuyến trực tiếp chưa đạt. */
    private static final List<Point> CORRIDOR =
            Arrays.asList(
                    new Point(10.762622, 106.660172),
                    new Point(12.24, 109.19),
                    new Point(13.77, 109.22),
                    new Point(16.05, 108.21),
                    new Point(18.67, 105.68),
                    new Point(20.25, 105.97));

    /** Kế hoạch waypoint giữ cả vị trí kết thúc chặng bắt buộc A/B/C/D. */
    private static final class Plan {

        final List<Point> points = new ArrayList<Point>();

        final List<Integer> requiredLegEnds = new ArrayList<Integer>();
    }

    /** Dùng credential Routing đã có của project, không tạo tài khoản hoặc API mới. */
    public VietnamRoutingClient(String apiKey) {
        this(apiKey, null);
    }

    /** Tạo client riêng cho mỗi worker, dùng chung giới hạn HTTP của lượt tìm. */
    public VietnamRoutingClient(String apiKey, RequestGate requestGate) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu cấu hình Routing.");
        }
        this.apiKey = apiKey;
        this.requestGate = requestGate;
    }

    /** Hủy kết nối đang chờ khi người dùng đổi điểm hoặc đóng màn. */
    public void cancelCurrentRequest() {
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    /** Hàm đồng bộ, bắt buộc gọi từ executor; tối đa ba lượt HTTP cho mỗi tuyến. */
    public Route findRoute(List<Point> requiredPoints, Cancellation cancellation)
            throws IOException {
        if (requiredPoints == null
                || requiredPoints.size() < 2
                || requiredPoints.size() > 4
                || requiredPoints.contains(null)) {
            throw new IllegalArgumentException("Cần từ 2 đến 4 điểm hợp lệ.");
        }
        List<Point> input = new ArrayList<Point>(requiredPoints);
        Plan direct = plan(input, false);
        Plan guided = plan(input, true);
        String reason = "Chưa xác nhận được tuyến đường chỉ trong Việt Nam.";
        int requests = 0;
        for (int attempt = 0; attempt < 3; attempt = attempt + 1) {
            checkCancelled(cancellation);
            Plan selected;
            if (attempt == 0) {
                selected = direct;
            } else {
                selected = guided;
            }
            // Không gửi hai lần cùng bộ điểm; dành lượt cuối cho tránh quốc gia.
            if (attempt == 1 && guided.points.size() == direct.points.size()) {
                continue;
            }
            boolean avoidCountries = attempt == 2;
            requests = requests + 1;
            try {
                JSONObject response = request(selected.points, avoidCountries, cancellation);
                checkCancelled(cancellation);
                return parse(response, selected, attempt != 0, requests);
            } catch (RejectedRoute rejected) {
                reason = rejected.getMessage();
            } catch (RoutingException failure) {
                throw failure;
            } catch (JSONException invalid) {
                reason = "Routing trả dữ liệu không hợp lệ; chưa thể xác nhận tuyến Việt Nam.";
            } catch (IOException network) {
                checkCancelled(cancellation);
                reason = "Không tải được tuyến Việt Nam. Kiểm tra mạng và thử lại.";
            }
        }
        throw new IOException(reason);
    }

    /**
     * So tuyến ghép và tuyến gốc cùng hành lang; hiệu chỉnh trên RAM khi DB chưa có version tuyến.
     */
    public MatchRoute findMatchRoute(
            List<Point> requiredPoints, long storedBaseline, Cancellation cancellation)
            throws IOException {
        return findMatchRoute(requiredPoints, storedBaseline, cancellation, null);
    }

    /**
     * Tái sử dụng baseline đã xác minh trong cùng lượt tìm, không giữ geometry nặng trong cache.
     */
    public MatchRoute findMatchRoute(
            List<Point> requiredPoints,
            long storedBaseline,
            Cancellation cancellation,
            Map<String, Double> verifiedBaselines)
            throws IOException {
        if (requiredPoints == null
                || requiredPoints.size() < 3
                || requiredPoints.size() > 4
                || requiredPoints.contains(null)
                || storedBaseline <= 0) {
            throw new IllegalArgumentException("Thiếu điểm đón/trả hoặc tuyến gốc.");
        }
        List<Point> driverEndpoints =
                Arrays.asList(requiredPoints.get(0), requiredPoints.get(requiredPoints.size() - 1));
        Plan directBase = plan(driverEndpoints, false);
        Plan guidedBase = plan(driverEndpoints, true);
        int requestCount = 0;
        String reason = "Chưa kiểm tra được cặp tuyến trong Việt Nam.";
        for (int attempt = 0; attempt < 3; attempt = attempt + 1) {
            checkCancelled(cancellation);
            if (attempt == 1 && guidedBase.points.size() == directBase.points.size()) {
                continue;
            }
            Plan baseline;
            if (attempt == 0) {
                baseline = directBase;
            } else {
                baseline = guidedBase;
            }
            Plan combined;
            if (attempt == 0) {
                combined = plan(requiredPoints, false);
            } else {
                combined = sharedCorridorPlan(requiredPoints, guidedBase);
            }
            boolean shouldAvoidForeignCountries = attempt == 2;
            try {
                Point segmentStartPoint = driverEndpoints.get(0);
                Point segmentEndPoint = driverEndpoints.get(1);
                String cacheKey =
                        String.format(
                                Locale.US,
                                "%s|%d|%.7f,%.7f|%.7f,%.7f",
                                POLICY_VERSION,
                                attempt,
                                segmentStartPoint.latitude,
                                segmentStartPoint.longitude,
                                segmentEndPoint.latitude,
                                segmentEndPoint.longitude);
                Double originalMeters;
                if (verifiedBaselines == null) {
                    originalMeters = null;
                } else {
                    originalMeters = verifiedBaselines.get(cacheKey);
                }
                if (originalMeters != null && Double.isNaN(originalMeters)) {
                    throw new RejectedRoute(
                            "Tuyến gốc của phương án này chưa được xác nhận trong Việt Nam.");
                }
                if (originalMeters == null) {
                    try {
                        JSONObject baselineResponse =
                                request(baseline.points, shouldAvoidForeignCountries, cancellation);
                        requestCount = requestCount + 1;
                        Route baselineRoute =
                                parse(baselineResponse, baseline, attempt != 0, requestCount);
                        originalMeters = baselineRoute.distanceMeters;
                    } catch (RejectedRoute rejected) {
                        // Chỉ giữ trong lượt tìm, không lặp phương án baseline đã bị kiểm tra từ
                        // chối.
                        if (verifiedBaselines != null) {
                            verifiedBaselines.put(cacheKey, Double.NaN);
                        }
                        throw rejected;
                    }
                    if (verifiedBaselines != null) {
                        verifiedBaselines.put(cacheKey, originalMeters);
                    }
                }
                checkCancelled(cancellation);
                JSONObject joinedResponse =
                        request(combined.points, shouldAvoidForeignCountries, cancellation);
                requestCount = requestCount + 1;
                Route joined = parse(joinedResponse, combined, attempt != 0, requestCount);
                checkCancelled(cancellation);
                return new MatchRoute(joined, originalMeters, storedBaseline);
            } catch (RoutingException terminal) {
                throw terminal;
            } catch (RejectedRoute rejected) {
                reason = rejected.getMessage();
            } catch (JSONException invalid) {
                reason = "Routing trả dữ liệu không hợp lệ; chưa đánh giá được chuyến.";
            } catch (IOException network) {
                checkCancelled(cancellation);
                reason =
                        "Không tải được đường bộ; một số chuyến chưa được đánh giá. Hãy làm mới để"
                                + " thử lại.";
            }
        }
        throw new IOException(reason);
    }

    /** Gắn đúng các mốc của A/D vào các chặng A/B/C/D, không sinh bộ mốc khác cho từng chặng. */
    private static Plan sharedCorridorPlan(List<Point> required, Plan baseline) {
        Plan result = new Plan();
        result.points.add(required.get(0));
        Point segmentStartPoint = required.get(0);
        Point segmentEndPoint = required.get(required.size() - 1);
        double latitudeSpan = segmentEndPoint.latitude - segmentStartPoint.latitude;
        int corridorPointIndex = 1;
        double previousProgress = 0;
        for (int requiredLegIndex = 1;
                requiredLegIndex < required.size();
                requiredLegIndex = requiredLegIndex + 1) {
            double progress;
            if (requiredLegIndex == required.size() - 1) {
                progress = 1;
            } else {
                if (latitudeSpan == 0) {
                    progress = 0;
                } else {
                    progress =
                            Math.max(
                                    0,
                                    Math.min(
                                            1,
                                            (required.get(requiredLegIndex).latitude
                                                            - segmentStartPoint.latitude)
                                                    / latitudeSpan));
                }
            }
            progress = Math.max(previousProgress, progress);
            while (corridorPointIndex < baseline.points.size() - 1) {
                Point point = baseline.points.get(corridorPointIndex);
                double anchorProgress;
                if (latitudeSpan == 0) {
                    anchorProgress = 0;
                } else {
                    anchorProgress = (point.latitude - segmentStartPoint.latitude) / latitudeSpan;
                }
                if (anchorProgress > progress) {
                    break;
                }
                appendPoint(result, point);
                corridorPointIndex = corridorPointIndex + 1;
            }
            appendPoint(result, required.get(requiredLegIndex));
            result.requiredLegEnds.add(result.points.size() - 1);
            previousProgress = progress;
        }
        return result;
    }

    /** Bỏ waypoint trùng liên tiếp nhưng vẫn giữ mốc kết thúc chặng có khoảng cách bằng 0. */
    private static void appendPoint(Plan plan, Point point) {
        Point previousPoint = plan.points.get(plan.points.size() - 1);
        if (Math.abs(previousPoint.latitude - point.latitude) > 0.00000001
                || Math.abs(previousPoint.longitude - point.longitude) > 0.00000001) {
            plan.points.add(point);
        }
    }

    /** Chèn hành lang theo từng cặp bắt buộc, không đổi thứ tự điểm đón/trả. */
    private static Plan plan(List<Point> required, boolean guided) {
        Plan result = new Plan();
        result.points.add(required.get(0));
        for (int requiredLegIndex = 1;
                requiredLegIndex < required.size();
                requiredLegIndex = requiredLegIndex + 1) {
            Point segmentStartPoint = required.get(requiredLegIndex - 1);
            Point segmentEndPoint = required.get(requiredLegIndex);
            if (guided && Math.abs(segmentEndPoint.latitude - segmentStartPoint.latitude) > 3.0) {
                boolean isNorthbound = segmentEndPoint.latitude > segmentStartPoint.latitude;
                for (int corridorPointOffset = 0;
                        corridorPointOffset < CORRIDOR.size();
                        corridorPointOffset = corridorPointOffset + 1) {
                    Point point;
                    if (isNorthbound) {
                        point = CORRIDOR.get(corridorPointOffset);
                    } else {
                        point = CORRIDOR.get(CORRIDOR.size() - 1 - corridorPointOffset);
                    }
                    if (point.latitude
                                    > Math.min(segmentStartPoint.latitude, segmentEndPoint.latitude)
                                            + 0.4
                            && point.latitude
                                    < Math.max(segmentStartPoint.latitude, segmentEndPoint.latitude)
                                            - 0.4) {
                        appendPoint(result, point);
                    }
                }
            }
            appendPoint(result, segmentEndPoint);
            result.requiredLegEnds.add(result.points.size() - 1);
        }
        return result;
    }

    /** Gọi API có metadata quốc gia, đơn vị mét và profile xe máy thống nhất. */
    protected JSONObject request(
            List<Point> points, boolean avoidCountries, Cancellation cancellation)
            throws IOException, JSONException {
        checkCancelled(cancellation);
        if (requestGate != null) {
            requestGate.beforeRequest();
        }
        StringBuilder waypoints = new StringBuilder();
        for (Point point : points) {
            if (waypoints.length() > 0) {
                waypoints.append('|');
            }
            waypoints.append(
                    String.format(Locale.US, "%.7f,%.7f", point.latitude, point.longitude));
        }
        String routingQueryString;
        if (avoidCountries) {
            routingQueryString =
                    "waypoints="
                            + URLEncoder.encode(waypoints.toString(), "UTF-8")
                            + "&mode=motorcycle&type=balanced&units=metric&details=admin_areas"
                            + "&intermediate_waypoint_mode=stopover"
                            + ("&avoid=country_codes%3Ala%2Ckh%2Ccn")
                            + "&apiKey="
                            + URLEncoder.encode(apiKey, "UTF-8");
        } else {
            routingQueryString =
                    "waypoints="
                            + URLEncoder.encode(waypoints.toString(), "UTF-8")
                            + "&mode=motorcycle&type=balanced&units=metric&details=admin_areas"
                            + "&intermediate_waypoint_mode=stopover"
                            + ("")
                            + "&apiKey="
                            + URLEncoder.encode(apiKey, "UTF-8");
        }
        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL("https://api.geoapify.com/v1/routing?" + routingQueryString)
                                .openConnection();
        activeConnection = connection;
        try {
            checkCancelled(cancellation);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(25000);
            int httpStatusCode = connection.getResponseCode();
            if (httpStatusCode == 429) {
                throw new RoutingException(
                        Failure.QUOTA, "Routing đã đạt giới hạn sử dụng. Vui lòng thử lại sau.");
            }
            if (httpStatusCode == 401 || httpStatusCode == 403) {
                throw new RoutingException(
                        Failure.ACCESS, "Routing từ chối truy cập. Cần kiểm tra cấu hình API.");
            }
            if (httpStatusCode == 400 || httpStatusCode == 404) {
                throw new RoutingException(
                        Failure.INPUT,
                        "Routing không xử lý được các điểm đã chọn (HTTP "
                                + httpStatusCode
                                + "). Hãy chọn địa chỉ cụ thể gần đường thay vì tên tỉnh và thử"
                                + " lại.");
            }
            if (httpStatusCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Routing tạm thời không phản hồi.");
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    checkCancelled(cancellation);
                    body.append(line);
                }
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
            if (activeConnection == connection) {
                activeConnection = null;
            }
        }
    }

    /** Kiểm tra toàn bộ route/leg và chỉ lấy geometry cùng khoảng cách của tuyến đạt. */
    private static Route parse(JSONObject response, Plan plan, boolean guided, int requestCount)
            throws JSONException, RejectedRoute {
        JSONArray features = response.optJSONArray("features");
        if (features == null || features.length() == 0) {
            throw new RejectedRoute("Không tìm thấy tuyến Việt Nam phù hợp.");
        }
        // Request không bật alternatives: chỉ có một tuyến, không tự chọn dữ liệu từ feature khác.
        JSONObject feature = features.getJSONObject(0);
        JSONObject properties = feature.optJSONObject("properties");
        if (!VietnamRouteValidator.isVietnamOnly(properties)) {
            throw new RejectedRoute("Chưa tìm được tuyến đã xác nhận hoàn toàn trong Việt Nam.");
        }
        if (!"meters".equalsIgnoreCase(properties.optString("distance_units"))) {
            throw new RejectedRoute("Tuyến thiếu đơn vị quãng đường hợp lệ.");
        }
        double distance = number(properties, "distance");
        double duration = number(properties, "time");
        JSONArray legs = properties.getJSONArray("legs");
        if (legs.length() != plan.points.size() - 1) {
            throw new RejectedRoute("Tuyến trả về thiếu chặng bắt buộc.");
        }
        List<Double> requiredDistances = new ArrayList<Double>();
        double legTotal = 0;
        int legIndex = 0;
        for (int endIndex : plan.requiredLegEnds) {
            double requiredDistance = 0;
            while (legIndex < endIndex) {
                JSONObject currentLeg = legs.getJSONObject(legIndex);
                legIndex = legIndex + 1;
                double value = number(currentLeg, "distance");
                requiredDistance = requiredDistance + (value);
                legTotal = legTotal + (value);
            }
            requiredDistances.add(requiredDistance);
        }
        if (Math.abs(legTotal - distance) > Math.max(5, legs.length() * 2)) {
            throw new RejectedRoute("Quãng đường tổng không khớp các chặng.");
        }
        JSONObject geometry = feature.getJSONObject("geometry");
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        List<List<Point>> segments = new ArrayList<List<Point>>();
        String type = geometry.optString("type");
        if ("LineString".equals(type)) {
            segments.add(points(coordinates));
        } else {
            if ("MultiLineString".equals(type)) {
                for (int coordinateIndex = 0;
                        coordinateIndex < coordinates.length();
                        coordinateIndex = coordinateIndex + 1) {
                    segments.add(points(coordinates.getJSONArray(coordinateIndex)));
                }
            } else {
                throw new RejectedRoute("Routing trả hình học tuyến không hợp lệ.");
            }
        }
        if (segments.isEmpty() || segments.size() != legs.length()) {
            throw new RejectedRoute("Tuyến thiếu đường vẽ của chặng.");
        }
        return new Route(distance, duration, segments, requiredDistances, guided, requestCount);
    }

    /** Không coi số thiếu, chuỗi, NaN hoặc số âm là quãng đường/thời gian hợp lệ. */
    private static double number(JSONObject value, String key) throws RejectedRoute {
        Object rawValue = value.opt(key);
        if (!(rawValue instanceof Number)) {
            throw new RejectedRoute("Tuyến thiếu số liệu đường bộ.");
        }
        double number = ((Number) rawValue).doubleValue();
        if (!Double.isFinite(number) || number < 0) {
            throw new RejectedRoute("Số liệu đường bộ không hợp lệ.");
        }
        return number;
    }

    /** Giữ thứ tự lon/lat GeoJSON và tách từng đoạn, không nối đường qua khoảng trống. */
    private static List<Point> points(JSONArray coordinates) throws JSONException, RejectedRoute {
        if (coordinates.length() < 2) {
            throw new RejectedRoute("Tuyến thiếu đường vẽ hợp lệ.");
        }
        List<Point> result = new ArrayList<Point>();
        for (int coordinateIndex = 0;
                coordinateIndex < coordinates.length();
                coordinateIndex = coordinateIndex + 1) {
            JSONArray coordinate = coordinates.getJSONArray(coordinateIndex);
            try {
                result.add(new Point(coordinate.getDouble(1), coordinate.getDouble(0)));
            } catch (IllegalArgumentException invalid) {
                throw new RejectedRoute("Tuyến có tọa độ không hợp lệ.");
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Tác vụ cũ không được tự thử thêm API hoặc trả kết quả vào lượt tìm mới. */
    private static void checkCancelled(Cancellation cancellation) throws IOException {
        if (Thread.currentThread().isInterrupted()
                || (cancellation != null && cancellation.isCancelled())) {
            throw new IOException("Đã hủy lượt tìm tuyến cũ.");
        }
    }

    /** Tuyến có phản hồi nhưng chưa đáp ứng điều kiện; có thể thử hành lang tiếp. */
    private static final class RejectedRoute extends IOException {

        /** Lưu lỗi kiểm tra, không chứa URL hoặc credential. */
        RejectedRoute(String message) {
            super(message);
        }
    }
}
