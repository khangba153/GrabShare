package com.example.grabshare.api;

import com.example.grabshare.model.Place;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp gọi API tìm kiếm địa điểm từ LocationIQ (dựa trên dữ liệu OpenStreetMap).
 */
public class PlaceSearchClient {
    private static final String LOCATIONIQ_API_KEY = "pk.ab7750edc9a2663ac3c3da50653ad680";
    private final ExecutorService executor;

    /**
     * Khởi tạo client tìm kiếm.
     */
    public PlaceSearchClient() {
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Interface nhận kết quả hoặc lỗi khi tìm kiếm.
     */
    public interface PlaceSearchCallback {
        /**
         * Xử lý kết quả trả về thành công.
         */
        void onSuccess(List<Place> places);

        /**
         * Xử lý khi có lỗi mạng hoặc phân tích dữ liệu.
         */
        void onError(Exception e);
    }

    /**
     * Tìm kiếm địa điểm theo từ khóa query qua LocationIQ API.
     */
    public void searchPlaces(final String query, final PlaceSearchCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String encodedQuery = URLEncoder.encode(query, "UTF-8");
                    String urlString = "https://us1.locationiq.com/v1/search?key=" + LOCATIONIQ_API_KEY + "&format=json&limit=10&countrycodes=vn&q=" + encodedQuery;
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONArray jsonArray = new JSONArray(response.toString());
                        List<Place> places = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String name = jsonObject.getString("display_name");
                            double lat = jsonObject.getDouble("lat");
                            double lon = jsonObject.getDouble("lon");
                            places.add(new Place(name, lat, lon));
                        }

                        // Tối ưu hóa từ khóa UEH / Cơ sở đại học cho sinh viên
                        enrichUehCampuses(query, places);

                        callback.onSuccess(places);
                    } else if (responseCode == 429) {
                        callback.onError(new Exception("Vượt quá giới hạn tìm kiếm (429). Vui lòng thử lại sau."));
                    } else {
                        callback.onError(new Exception("Mã lỗi HTTP: " + responseCode));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * Bổ sung địa điểm chuẩn cho các cơ sở UEH khi người dùng gõ từ khóa cụ thể.
     * Tránh khớp nhầm các ký tự đơn lẻ (ví dụ: chữ "h" trong "hẻm").
     */
    private void enrichUehCampuses(String query, List<Place> places) {
        if (query == null) return;
        
        // Loại bỏ dấu câu cơ bản và chuyển thành chữ thường để so khớp
        String normalizedQuery = query.toLowerCase()
                .replace(".", " ").replace(",", " ").trim()
                .replaceAll("\\s+", " ");
        
        // Phân tích từ khóa để nhận diện đúng cơ sở
        boolean isA = normalizedQuery.contains("ueh a") || normalizedQuery.contains("co so a") || normalizedQuery.contains("cơ sở a");
        boolean isB = normalizedQuery.contains("ueh b") || normalizedQuery.contains("co so b") || normalizedQuery.contains("cơ sở b");
        boolean isC = normalizedQuery.contains("ueh c") || normalizedQuery.contains("co so c") || normalizedQuery.contains("cơ sở c");
        boolean isN = normalizedQuery.contains("ueh n") || normalizedQuery.contains("co so n") || normalizedQuery.contains("cơ sở n") || normalizedQuery.contains("nguyen van linh") || normalizedQuery.contains("nguyễn văn linh");
        boolean isH = normalizedQuery.contains("ueh h") || normalizedQuery.contains("co so h") || normalizedQuery.contains("cơ sở h") || normalizedQuery.contains("hoang dieu") || normalizedQuery.contains("hoàng diệu");
        boolean isV = normalizedQuery.contains("ueh v") || normalizedQuery.contains("co so v") || normalizedQuery.contains("cơ sở v") || normalizedQuery.contains("vo thi sau") || normalizedQuery.contains("võ thị sáu");
        
        // Nếu người dùng chỉ gõ chung chung "ueh", ưu tiên đẩy cơ sở chính lên đầu thay vì đẩy toàn bộ
        if (!isA && !isB && !isC && !isN && !isH && !isV) {
            if (normalizedQuery.equals("ueh") || normalizedQuery.equals("kinh tế") || normalizedQuery.equals("kinh te")) {
                isA = true;
                isB = true;
            }
        }

        // Chèn vào kết quả (ưu tiên đưa lên đầu danh sách API trả về)
        if (isV) {
            insertIfNotExists(places, new Place("UEH Cơ sở V - 232/9 Võ Thị Sáu, Phường Võ Thị Sáu, Quận 3, TP.HCM", 10.7828045, 106.6856203));
        }
        if (isH) {
            insertIfNotExists(places, new Place("UEH Cơ sở H - 1A Hoàng Diệu, Phường 10, Phú Nhuận, TP.HCM", 10.798991, 106.678210));
        }
        if (isN) {
            insertIfNotExists(places, new Place("UEH Cơ sở N - Nguyễn Văn Linh, Phong Phú, Bình Chánh, TP.HCM", 10.705906, 106.639100));
        }
        if (isC) {
            insertIfNotExists(places, new Place("UEH Cơ sở C - 91 Đường 3 Tháng 2, Phường 11, Quận 10, TP.HCM", 10.773238, 106.677568));
        }
        if (isB) {
            insertIfNotExists(places, new Place("UEH Cơ sở B - 279 Nguyễn Tri Phương, Phường 5, Quận 10, TP.HCM", 10.7609491, 106.6679786));
        }
        if (isA) {
            insertIfNotExists(places, new Place("UEH Cơ sở A - 59C Nguyễn Đình Chiểu, Phường 6, Quận 3, TP.HCM", 10.7830141, 106.6948927));
        }
    }
    
    /**
     * Chèn địa điểm gợi ý vào danh sách, chống trùng lặp nếu API đã trả về kết quả tương tự.
     */
    private void insertIfNotExists(List<Place> places, Place uehPlace) {
        boolean exists = false;
        for (Place p : places) {
            if (Math.abs(p.getLatitude() - uehPlace.getLatitude()) < 0.001 && 
                Math.abs(p.getLongitude() - uehPlace.getLongitude()) < 0.001) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            places.add(0, uehPlace);
        }
    }

    /**
     * Dừng các tiến trình ngầm khi không dùng.
     */
    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }
}