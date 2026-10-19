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

/** Tính tuyến xe máy và chỉ công bố tuyến đã được API xác nhận nằm trong Việt Nam. */
public class VietnamRoutingClient {
    public static final String POLICY_VERSION = "vn-motorcycle-balanced-v1";
    private final String apiKey;
    private volatile HttpURLConnection activeConnection;

    /** Điểm tuyến độc lập với Activity, Place và thư viện hiển thị bản đồ. */
    public static final class Point {
        public final double latitude;
        public final double longitude;
        /** Kiểm tra tọa độ trước khi tạo waypoint. */
        public Point(double latitude, double longitude) {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
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
        private Route(double meters, double seconds, List<List<Point>> segments,
                      List<Double> requiredLegDistances, boolean guided, int requestCount) {
            distanceMeters = meters;
            durationSeconds = seconds;
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

    /** Điểm dẫn hướng trong nước; chỉ dùng khi tuyến trực tiếp chưa đạt. */
    private static final List<Point> CORRIDOR = Arrays.asList(
            new Point(10.762622, 106.660172), new Point(12.24, 109.19),
            new Point(13.77, 109.22), new Point(16.05, 108.21),
            new Point(18.67, 105.68), new Point(20.25, 105.97));

    /** Kế hoạch waypoint giữ cả vị trí kết thúc chặng bắt buộc A/B/C/D. */
    private static final class Plan {
        final List<Point> points = new ArrayList<>();
        final List<Integer> requiredLegEnds = new ArrayList<>();
    }

    /** Dùng credential Routing đã có của project, không tạo tài khoản hoặc API mới. */
    public VietnamRoutingClient(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("Thiếu cấu hình Routing.");
        this.apiKey = apiKey;
    }

    /** Hủy kết nối đang chờ khi người dùng đổi điểm hoặc đóng màn. */
    public void cancelCurrentRequest() {
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    /** Hàm đồng bộ, bắt buộc gọi từ executor; tối đa ba lượt HTTP cho mỗi tuyến. */
    public Route findRoute(List<Point> requiredPoints, Cancellation cancellation) throws IOException {
        if (requiredPoints == null || requiredPoints.size() < 2 || requiredPoints.size() > 4
                || requiredPoints.contains(null)) throw new IllegalArgumentException("Cần từ 2 đến 4 điểm hợp lệ.");
        List<Point> input = new ArrayList<>(requiredPoints);
        Plan direct = plan(input, false);
        Plan guided = plan(input, true);
        String reason = "Chưa xác nhận được tuyến đường chỉ trong Việt Nam.";
        int requests = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            checkCancelled(cancellation);
            Plan selected = attempt == 0 ? direct : guided;
            // Không gửi hai lần cùng bộ điểm; dành lượt cuối cho tránh quốc gia.
            if (attempt == 1 && guided.points.size() == direct.points.size()) continue;
            boolean avoidCountries = attempt == 2;
            requests++;
            try {
                JSONObject response = request(selected.points, avoidCountries, cancellation);
                checkCancelled(cancellation);
                return parse(response, selected, attempt != 0, requests);
            } catch (RejectedRoute rejected) {
                reason = rejected.getMessage();
            } catch (TerminalFailure failure) {
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

    /** Chèn hành lang theo từng cặp bắt buộc, không đổi thứ tự điểm đón/trả. */
    private static Plan plan(List<Point> required, boolean guided) {
        Plan result = new Plan();
        result.points.add(required.get(0));
        for (int leg = 1; leg < required.size(); leg++) {
            Point start = required.get(leg - 1);
            Point end = required.get(leg);
            if (guided && Math.abs(end.latitude - start.latitude) > 3.0) {
                boolean north = end.latitude > start.latitude;
                for (int offset = 0; offset < CORRIDOR.size(); offset++) {
                    Point point = CORRIDOR.get(north ? offset : CORRIDOR.size() - 1 - offset);
                    if (point.latitude > Math.min(start.latitude, end.latitude) + 0.4
                            && point.latitude < Math.max(start.latitude, end.latitude) - 0.4) {
                        result.points.add(point);
                    }
                }
            }
            result.points.add(end);
            result.requiredLegEnds.add(result.points.size() - 1);
        }
        return result;
    }

    /** Gọi API có metadata quốc gia, đơn vị mét và profile xe máy thống nhất. */
    protected JSONObject request(List<Point> points, boolean avoidCountries, Cancellation cancellation)
            throws IOException, JSONException {
        StringBuilder waypoints = new StringBuilder();
        for (Point point : points) {
            if (waypoints.length() > 0) waypoints.append('|');
            waypoints.append(String.format(Locale.US, "%.7f,%.7f", point.latitude, point.longitude));
        }
        String query = "waypoints=" + URLEncoder.encode(waypoints.toString(), "UTF-8")
                + "&mode=motorcycle&type=balanced&units=metric&details=admin_areas"
                + "&intermediate_waypoint_mode=stopover"
                + (avoidCountries ? "&avoid=country_codes%3Ala%2Ckh%2Ccn" : "")
                + "&apiKey=" + URLEncoder.encode(apiKey, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection)
                new URL("https://api.geoapify.com/v1/routing?" + query).openConnection();
        activeConnection = connection;
        try {
            checkCancelled(cancellation);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(25000);
            int code = connection.getResponseCode();
            if (code == 429) throw new TerminalFailure("Routing đã đạt giới hạn sử dụng. Vui lòng thử lại sau.");
            if (code == 401 || code == 403) throw new TerminalFailure("Routing từ chối truy cập. Cần kiểm tra cấu hình API.");
            if (code == 400 || code == 404) throw new TerminalFailure(
                    "Không tìm được đường xe máy gần điểm đã chọn. Hãy chọn điểm gần đường và thử lại.");
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("Routing tạm thời không phản hồi.");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    checkCancelled(cancellation);
                    body.append(line);
                }
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
            if (activeConnection == connection) activeConnection = null;
        }
    }

    /** Kiểm tra toàn bộ route/leg và chỉ lấy geometry cùng khoảng cách của tuyến đạt. */
    private static Route parse(JSONObject response, Plan plan, boolean guided, int requestCount)
            throws JSONException, RejectedRoute {
        JSONArray features = response.optJSONArray("features");
        if (features == null || features.length() == 0) throw new RejectedRoute("Không tìm thấy tuyến Việt Nam phù hợp.");
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
        if (legs.length() != plan.points.size() - 1) throw new RejectedRoute("Tuyến trả về thiếu chặng bắt buộc.");
        List<Double> requiredDistances = new ArrayList<>();
        double legTotal = 0;
        int legIndex = 0;
        for (int endIndex : plan.requiredLegEnds) {
            double requiredDistance = 0;
            while (legIndex < endIndex) {
                double value = number(legs.getJSONObject(legIndex++), "distance");
                requiredDistance += value;
                legTotal += value;
            }
            requiredDistances.add(requiredDistance);
        }
        if (Math.abs(legTotal - distance) > Math.max(5, legs.length() * 2)) {
            throw new RejectedRoute("Quãng đường tổng không khớp các chặng.");
        }
        JSONObject geometry = feature.getJSONObject("geometry");
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        List<List<Point>> segments = new ArrayList<>();
        String type = geometry.optString("type");
        if ("LineString".equals(type)) segments.add(points(coordinates));
        else if ("MultiLineString".equals(type)) {
            for (int i = 0; i < coordinates.length(); i++) segments.add(points(coordinates.getJSONArray(i)));
        } else throw new RejectedRoute("Routing trả hình học tuyến không hợp lệ.");
        if (segments.isEmpty() || segments.size() != legs.length()) throw new RejectedRoute("Tuyến thiếu đường vẽ của chặng.");
        return new Route(distance, duration, segments, requiredDistances, guided, requestCount);
    }

    /** Không coi số thiếu, chuỗi, NaN hoặc số âm là quãng đường/thời gian hợp lệ. */
    private static double number(JSONObject value, String key) throws RejectedRoute {
        Object raw = value.opt(key);
        if (!(raw instanceof Number)) throw new RejectedRoute("Tuyến thiếu số liệu đường bộ.");
        double number = ((Number) raw).doubleValue();
        if (!Double.isFinite(number) || number < 0) throw new RejectedRoute("Số liệu đường bộ không hợp lệ.");
        return number;
    }

    /** Giữ thứ tự lon/lat GeoJSON và tách từng đoạn, không nối đường qua khoảng trống. */
    private static List<Point> points(JSONArray coordinates) throws JSONException, RejectedRoute {
        if (coordinates.length() < 2) throw new RejectedRoute("Tuyến thiếu đường vẽ hợp lệ.");
        List<Point> result = new ArrayList<>();
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray coordinate = coordinates.getJSONArray(i);
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
        if (Thread.currentThread().isInterrupted() || (cancellation != null && cancellation.isCancelled())) {
            throw new IOException("Đã hủy lượt tìm tuyến cũ.");
        }
    }

    /** Tuyến có phản hồi nhưng chưa đáp ứng điều kiện; có thể thử hành lang tiếp. */
    private static final class RejectedRoute extends IOException {
        /** Lưu lỗi kiểm tra, không chứa URL hoặc credential. */
        RejectedRoute(String message) { super(message); }
    }
    /** Lỗi quyền/quota/đầu vào không được tự lặp để tiêu tốn thêm API. */
    private static final class TerminalFailure extends IOException {
        /** Lưu lỗi người dùng có thể xử lý trước lần thử mới. */
        TerminalFailure(String message) { super(message); }
    }
}
