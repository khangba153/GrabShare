package com.example.grabshare.activity.search;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import android.view.MotionEvent;
import android.widget.ScrollView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.model.Place;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import com.example.grabshare.activity.account.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

import com.example.grabshare.activity.passenger.MyTripsActivity;

import com.example.grabshare.activity.account.AccountActivity;

/**
 * Activity Nhập điều kiện tìm chuyến.
 */
public class HomeActivity extends AppCompatActivity {

    private TextView tvOriginValue;
    private TextView tvDestinationValue;
    private TextView tvDateValue;
    private TextView tvTimeValue;
    private MapView mapView;
    private ScrollView mainScrollView;

    private Place origin;
    private Place destination;
    private Calendar searchTime;

    private int currentPickingField = 0; // 1 = origin, 2 = destination
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private int currentRouteRequestId = 0; // Đếm thế hệ vẽ tuyến để chặn đè tuyến đường mạng trễ
    private final java.util.concurrent.ExecutorService routeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    /** Kiểm tra phiên khi mở hoặc quay lại màn Home. */
    @Override
    protected void onStart() {
        super.onStart();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(
                    HomeActivity.this,
                    LoginActivity.class
            );

            startActivity(intent);
            finish();
        }
    }

    /**
     * Khởi tạo giao diện, map các view và thiết lập dữ liệu mặc định.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_home);

        tvOriginValue = findViewById(R.id.pick_start_value);
        tvDestinationValue = findViewById(R.id.pick_end_value);
        tvDateValue = findViewById(R.id.pick_date_value);
        tvTimeValue = findViewById(R.id.pick_time_value);
        
        mapView = findViewById(R.id.map_view);
        mainScrollView = findViewById(R.id.main_scroll_view);

        // Khởi tạo osmdroid map (Cấu hình User-Agent để đáp ứng chính sách)
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Configuration.getInstance().load(getApplicationContext(), prefs);
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Sử dụng Geoapify OSM-Carto thay cho MAPNIK mặc định của OSM để không bị lỗi Access Blocked
        mapView.setTileSource(new XYTileSource(
                "Geoapify-OSMCarto",
                0, 20, 256, ".png",
                new String[]{"https://maps.geoapify.com/v1/tile/osm-carto/"}
        ) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl() + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + org.osmdroid.util.MapTileIndex.getX(pMapTileIndex) + "/"
                        + org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) + ".png?apiKey=3bd4e00744474c3890053d1bc896e77b";
            }
        });
        mapView.setMultiTouchControls(true);
        // Map center mặc định (Ví dụ: TP.HCM)
        mapView.getController().setZoom(13.0);
        mapView.getController().setCenter(new GeoPoint(10.762622, 106.660172));
        
        // Ngăn chặn ScrollView cha cướp sự kiện chạm khi người dùng đang vuốt bản đồ
        mapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        mainScrollView.requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainScrollView.requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }
        });

        searchTime = Calendar.getInstance();

        // Đăng ký callback nhận kết quả chọn địa điểm
        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            if (data.hasExtra(LocationKeys.PLACE_NAME) && 
                                data.hasExtra(LocationKeys.PLACE_LATITUDE) && 
                                data.hasExtra(LocationKeys.PLACE_LONGITUDE)) {
                                
                                String name = data.getStringExtra(LocationKeys.PLACE_NAME);
                                double lat = data.getDoubleExtra(LocationKeys.PLACE_LATITUDE, 0);
                                double lng = data.getDoubleExtra(LocationKeys.PLACE_LONGITUDE, 0);
                                
                                Place selectedPlace = new Place(name, lat, lng);
                                
                                if (currentPickingField == 1) {
                                    origin = selectedPlace;
                                } else if (currentPickingField == 2) {
                                    destination = selectedPlace;
                                }
                                updateUI();
                            }
                        }
                        // Reset lại ô đang chọn
                        currentPickingField = 0;
                    }
                }
        );

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            // Mặc định là null và hiển thị hint
            tvOriginValue.setText("Chọn điểm đi");
            tvDestinationValue.setText("Chọn điểm đến");
            updateDateTimeUI();
        }

        setupListeners();
    }

    /**
     * Gắn sự kiện lắng nghe (click listeners) cho các thành phần trên giao diện.
     */
    private void setupListeners() {
        findViewById(R.id.pick_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentPickingField = 1;
                openLocationPicker();
            }
        });

        findViewById(R.id.pick_end).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentPickingField = 2;
                openLocationPicker();
            }
        });

        findViewById(R.id.pick_date).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDatePicker();
            }
        });

        findViewById(R.id.pick_time).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTimePicker();
            }
        });

        findViewById(R.id.primary_action).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSearch();
            }
        });

        findViewById(R.id.nav_trips).setOnClickListener(
                new View.OnClickListener() {

                    /** Mở danh sách chuyến đi nhờ của tài khoản hiện tại. */
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(
                                HomeActivity.this,
                                MyTripsActivity.class);

                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                    }
                });

        findViewById(R.id.nav_account).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Sẽ chuyển sang Account của Leader
                Intent intent = new Intent(
                        HomeActivity.this,
                        AccountActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
    }

    /**
     * Khởi chạy LocationActivity để người dùng tìm kiếm và chọn địa điểm.
     */
    private void openLocationPicker() {
        Intent intent = new Intent(this, LocationActivity.class);
        locationPickerLauncher.launch(intent);
    }

    /**
     * Mở hộp thoại chọn ngày (DatePickerDialog). 
     * Ngăn chặn người dùng chọn các ngày trong quá khứ.
     */
    private void openDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        searchTime.set(Calendar.YEAR, year);
                        searchTime.set(Calendar.MONTH, month);
                        searchTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateTimeUI();
                    }
                },
                searchTime.get(Calendar.YEAR),
                searchTime.get(Calendar.MONTH),
                searchTime.get(Calendar.DAY_OF_MONTH)
        );
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000); // Không cho chọn ngày quá khứ
        dialog.show();
    }

    /**
     * Mở hộp thoại chọn giờ (TimePickerDialog) theo định dạng 24h.
     */
    private void openTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        searchTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        searchTime.set(Calendar.MINUTE, minute);
                        updateDateTimeUI();
                    }
                },
                searchTime.get(Calendar.HOUR_OF_DAY),
                searchTime.get(Calendar.MINUTE),
                true
        );
        dialog.show();
    }

    /**
     * Kiểm tra tính hợp lệ của dữ liệu đầu vào (Validation) trước khi tìm kiếm.
     * Nếu hợp lệ, đóng gói dữ liệu và chuyển sang màn hình kết quả (ResultsActivity).
     */
    private void validateAndSearch() {
        if (origin == null) {
            Toast.makeText(this, "Vui lòng chọn điểm đi!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destination == null) {
            Toast.makeText(this, "Vui lòng chọn điểm đến!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Kiểm tra khoảng cách / trùng tọa độ
        if (Math.abs(origin.getLatitude() - destination.getLatitude()) < 0.0001 &&
            Math.abs(origin.getLongitude() - destination.getLongitude()) < 0.0001) {
            Toast.makeText(this, "Điểm đi và điểm đến không được trùng nhau!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra thời gian (chặn tìm ngày đã qua)
        // Cho phép trễ 5 phút so với hiện tại để đỡ khắt khe khi thao tác
        if (searchTime.getTimeInMillis() < System.currentTimeMillis() - 5 * 60 * 1000) {
            Toast.makeText(this, "Không thể tìm chuyến trong quá khứ!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo intent sang ResultsActivity (Thành-3)
        Intent intent = new Intent(this, ResultsActivity.class);
        intent.putExtra("origin_name", origin.getName());
        intent.putExtra("origin_lat", origin.getLatitude());
        intent.putExtra("origin_lng", origin.getLongitude());
        intent.putExtra("dest_name", destination.getName());
        intent.putExtra("dest_lat", destination.getLatitude());
        intent.putExtra("dest_lng", destination.getLongitude());
        intent.putExtra("departure_time", searchTime.getTimeInMillis());
        startActivity(intent);
    }

    /**
     * Cập nhật văn bản hiển thị cho ô chọn Điểm đi và Điểm đến trên UI.
     */
    private void updateUI() {
        // LUÔN LUÔN xóa toàn bộ ghim và đường vẽ cũ đi trước khi bắt đầu vẽ lại
        mapView.getOverlays().clear();
        
        GeoPoint startPoint = null;
        GeoPoint endPoint = null;

        if (origin != null) {
            tvOriginValue.setText(origin.getName());
            
            startPoint = new GeoPoint(origin.getLatitude(), origin.getLongitude());
            Marker startMarker = new Marker(mapView);
            startMarker.setPosition(startPoint);
            startMarker.setTitle(origin.getName());
            mapView.getOverlays().add(startMarker);
        } else {
            tvOriginValue.setText("Chọn điểm đi");
        }

        if (destination != null) {
            tvDestinationValue.setText(destination.getName());
            
            endPoint = new GeoPoint(destination.getLatitude(), destination.getLongitude());
            Marker endMarker = new Marker(mapView);
            endMarker.setPosition(endPoint);
            endMarker.setTitle(destination.getName());
            mapView.getOverlays().add(endMarker);
        } else {
            tvDestinationValue.setText("Chọn điểm đến");
        }
        
        if (startPoint != null && endPoint != null) {
            // Zoom để thấy cả 2 điểm
            mapView.zoomToBoundingBox(new org.osmdroid.util.BoundingBox(
                    Math.max(startPoint.getLatitude(), endPoint.getLatitude()),
                    Math.max(startPoint.getLongitude(), endPoint.getLongitude()),
                    Math.min(startPoint.getLatitude(), endPoint.getLatitude()),
                    Math.min(startPoint.getLongitude(), endPoint.getLongitude())
            ), true);

            final int requestId = ++currentRouteRequestId;
            fetchAndDrawRoute(startPoint, endPoint, requestId);
        } else if (startPoint != null) {
            mapView.getController().animateTo(startPoint);
        } else if (endPoint != null) {
            mapView.getController().animateTo(endPoint);
        }
        
        mapView.invalidate();
    }

    /**
     * Gọi Geoapify Routing API để lấy dữ liệu tuyến đường (GeoJSON) và vẽ Polyline trên bản đồ.
     */
    private void fetchAndDrawRoute(final GeoPoint start, final GeoPoint end, final int requestId) {
        routeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                java.net.HttpURLConnection conn = null;
                java.io.BufferedReader reader = null;
                try {
                    // Kiểm tra xem chuyến đi có xuyên Việt (Bắc - Nam vượt qua eo miền Trung) hay không
                    // Vĩ độ của Đà Nẵng khoảng 16.0
                    boolean isNorthToSouth = (start.getLatitude() > 17.5 && end.getLatitude() < 14.5) ||
                                             (end.getLatitude() > 17.5 && start.getLatitude() < 14.5);

                    String urlString;
                    if (isNorthToSouth) {
                        // Chèn thêm waypoint ở Quốc lộ 1A (Đà Nẵng: 16.0044, 108.2022) để ép đường đi ôm theo hình chữ S, né cắt Lào/Campuchia
                        // Dùng mode=motorcycle cho xe máy thay vì drive (ô tô)
                        urlString = String.format(Locale.US,
                                "https://api.geoapify.com/v1/routing?waypoints=%f,%f%%7C16.0044,108.2022%%7C%f,%f&mode=motorcycle&apiKey=3ae0ba5bcf8843df8024203662e3d702",
                                start.getLatitude(), start.getLongitude(),
                                end.getLatitude(), end.getLongitude());
                    } else {
                        // Các chuyến nội tỉnh hoặc khoảng cách gần thì giữ nguyên 2 điểm
                        urlString = String.format(Locale.US,
                                "https://api.geoapify.com/v1/routing?waypoints=%f,%f%%7C%f,%f&mode=motorcycle&apiKey=3ae0ba5bcf8843df8024203662e3d702",
                                start.getLatitude(), start.getLongitude(),
                                end.getLatitude(), end.getLongitude());
                    }

                    java.net.URL url = new java.net.URL(urlString);
                    conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }

                        org.json.JSONObject jsonResponse = new org.json.JSONObject(sb.toString());
                        org.json.JSONArray features = jsonResponse.getJSONArray("features");
                        if (features.length() > 0) {
                            org.json.JSONObject feature = features.getJSONObject(0);
                            org.json.JSONObject geometry = feature.getJSONObject("geometry");
                            String geomType = geometry.optString("type", "");
                            
                            final java.util.List<GeoPoint> geoPoints = new java.util.ArrayList<>();
                            
                            // Xử lý chuẩn cả 2 định dạng LineString và MultiLineString từ Geoapify
                            if ("LineString".equalsIgnoreCase(geomType)) {
                                org.json.JSONArray coords = geometry.getJSONArray("coordinates");
                                for (int i = 0; i < coords.length(); i++) {
                                    org.json.JSONArray pt = coords.getJSONArray(i);
                                    double lon = pt.getDouble(0);
                                    double lat = pt.getDouble(1);
                                    geoPoints.add(new GeoPoint(lat, lon));
                                }
                            } else if ("MultiLineString".equalsIgnoreCase(geomType)) {
                                org.json.JSONArray lines = geometry.getJSONArray("coordinates");
                                for (int i = 0; i < lines.length(); i++) {
                                    org.json.JSONArray segment = lines.getJSONArray(i);
                                    for (int j = 0; j < segment.length(); j++) {
                                        org.json.JSONArray pt = segment.getJSONArray(j);
                                        double lon = pt.getDouble(0);
                                        double lat = pt.getDouble(1);
                                        geoPoints.add(new GeoPoint(lat, lon));
                                    }
                                }
                            }

                            if (!geoPoints.isEmpty()) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isFinishing() || isDestroyed()) return;
                                        // Kiểm tra nếu request này không còn là request mới nhất thì bỏ qua
                                        if (requestId != currentRouteRequestId) return;
                                        
                                        // Xóa mọi đường Polyline ngầm bị delay mạng dội về trễ
                                        for (int i = mapView.getOverlays().size() - 1; i >= 0; i--) {
                                            if (mapView.getOverlays().get(i) instanceof org.osmdroid.views.overlay.Polyline) {
                                                mapView.getOverlays().remove(i);
                                            }
                                        }

                                        org.osmdroid.views.overlay.Polyline line = new org.osmdroid.views.overlay.Polyline();
                                        line.setPoints(geoPoints);
                                        // Set màu xanh nét dày
                                        line.getOutlinePaint().setColor(android.graphics.Color.parseColor("#4A80F5")); 
                                        line.getOutlinePaint().setStrokeWidth(12f);
                                        // Chèn đường đi xuống dưới các Marker để không che ghim
                                        mapView.getOverlays().add(0, line); 
                                        mapView.postInvalidate(); // Ép load lại ngay lập tức
                                    }
                                });
                            }
                        } else {
                            // Không có feature trả về
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (isFinishing() || isDestroyed() || requestId != currentRouteRequestId) return;
                                    Toast.makeText(HomeActivity.this, "Không tìm thấy tuyến đường kết nối 2 địa điểm", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        // HTTP Error (VD: 429 Limit Exceeded)
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isFinishing() || isDestroyed() || requestId != currentRouteRequestId) return;
                                Toast.makeText(HomeActivity.this, "Chưa thể tải tuyến đường gợi ý (Lỗi mạng/API)", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    // Exception Error
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed() || requestId != currentRouteRequestId) return;
                            Toast.makeText(HomeActivity.this, "Chưa thể tải tuyến đường gợi ý (Lỗi mạng/API)", Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (Exception ignored) {}
                    }
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    /**
     * Cập nhật văn bản hiển thị cho ô chọn Ngày và Giờ trên UI.
     */
    private void updateDateTimeUI() {
        tvDateValue.setText(dateFormat.format(searchTime.getTime()));
        tvTimeValue.setText(timeFormat.format(searchTime.getTime()));
    }

    /**
     * Lưu trạng thái của Form (Điểm đi, Điểm đến, Thời gian, Trường đang chọn) 
     * trước khi Activity bị hủy để phục hồi sau khi xoay màn hình.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_picking_field", currentPickingField);
        outState.putLong("search_time", searchTime.getTimeInMillis());

        if (origin != null) {
            outState.putString("origin_name", origin.getName());
            outState.putDouble("origin_lat", origin.getLatitude() != null ? origin.getLatitude() : 0.0);
            outState.putDouble("origin_lng", origin.getLongitude() != null ? origin.getLongitude() : 0.0);
        }

        if (destination != null) {
            outState.putString("dest_name", destination.getName());
            outState.putDouble("dest_lat", destination.getLatitude() != null ? destination.getLatitude() : 0.0);
            outState.putDouble("dest_lng", destination.getLongitude() != null ? destination.getLongitude() : 0.0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDetach();
        routeExecutor.shutdownNow(); // Đảm bảo luồng phụ không chạy ngầm
    }

    /**
     * Khôi phục trạng thái của Form từ Bundle sau khi Activity được tạo lại 
     * (ví dụ: sau khi xoay màn hình thiết bị).
     */
    private void restoreState(Bundle savedInstanceState) {
        currentPickingField = savedInstanceState.getInt("current_picking_field", 0);
        long time = savedInstanceState.getLong("search_time", System.currentTimeMillis());
        searchTime.setTimeInMillis(time);
        updateDateTimeUI();

        if (savedInstanceState.containsKey("origin_name")) {
            String name = savedInstanceState.getString("origin_name");
            double lat = savedInstanceState.getDouble("origin_lat");
            double lng = savedInstanceState.getDouble("origin_lng");
            origin = new Place(name, lat, lng);
        }

        if (savedInstanceState.containsKey("dest_name")) {
            String name = savedInstanceState.getString("dest_name");
            double lat = savedInstanceState.getDouble("dest_lat");
            double lng = savedInstanceState.getDouble("dest_lng");
            destination = new Place(name, lat, lng);
        }
        
        updateUI();
    }
}