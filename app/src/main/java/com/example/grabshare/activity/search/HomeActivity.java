package com.example.grabshare.activity.search;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.DatePicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.presentation.MainNavigation;
import com.example.grabshare.notifications.NotificationPermissionHelper;
import com.example.grabshare.activity.account.LoginActivity;
import com.example.grabshare.api.VietnamRoutingClient;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.firebase.auth.FirebaseAuth;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Activity Nhập điều kiện tìm chuyến. */
public class HomeActivity extends AppCompatActivity {

    private TextView originValueTextView;

    private TextView destinationValueTextView;

    private TextView departureDateTextView;

    private TextView departureTimeTextView;

    private TextView routeDistanceTextView;

    private MapView mapView;

    private ScrollView mainScrollView;

    private Place origin;

    private Place destination;

    private Calendar searchTime;

    // 1 = origin, 2 = destination
    private int currentPickingField = 0;

    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private final VietnamRoutingClient routingClient =
            new VietnamRoutingClient("3ae0ba5bcf8843df8024203662e3d702");

    // Dùng cả ở UI và luồng Routing để bỏ tác vụ tuyến cũ
    private volatile int currentRouteRequestId = 0;

    private final java.util.concurrent.ExecutorService routeExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    /** Kiểm tra phiên khi mở hoặc quay lại màn Home. */
    @Override
    protected void onStart() {
        super.onStart();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }

    /** Khởi tạo giao diện, map các view và thiết lập dữ liệu mặc định. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_home);
        if (savedInstanceState == null) {
            NotificationPermissionHelper.requestIfNeeded(this);
        }
        originValueTextView = findViewById(R.id.pick_start_value);
        destinationValueTextView = findViewById(R.id.pick_end_value);
        departureDateTextView = findViewById(R.id.pick_date_value);
        departureTimeTextView = findViewById(R.id.pick_time_value);
        routeDistanceTextView = findViewById(R.id.home_route_distance);
        mapView = findViewById(R.id.map_view);
        mainScrollView = findViewById(R.id.main_scroll_view);
        // Khởi tạo osmdroid map (Cấu hình User-Agent để đáp ứng chính sách)
        android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(
                        getApplicationContext());
        Configuration.getInstance().load(getApplicationContext(), prefs);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        // Sử dụng Geoapify OSM-Carto thay cho MAPNIK mặc định của OSM để không bị lỗi Access
        // Blocked
        mapView.setTileSource(
                new XYTileSource(
                        "Geoapify-OSMCarto",
                        0,
                        20,
                        256,
                        ".png",
                        new String[] {"https://maps.geoapify.com/v1/tile/osm-carto/"}) {

                    @Override
                    public String getTileURLString(long pMapTileIndex) {
                        return getBaseUrl()
                                + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
                                + "/"
                                + org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
                                + "/"
                                + org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
                                + ".png?apiKey=3bd4e00744474c3890053d1bc896e77b";
                    }
                });
        mapView.setMultiTouchControls(true);
        // Map center mặc định (Ví dụ: TP.HCM)
        mapView.getController().setZoom(13.0);
        mapView.getController().setCenter(new GeoPoint(10.762622, 106.660172));
        // Ngăn chặn ScrollView cha cướp sự kiện chạm khi người dùng đang vuốt bản đồ
        mapView.setOnTouchListener(
                new View.OnTouchListener() {

                    @Override
                    public boolean onTouch(View clickedView, MotionEvent event) {
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
        locationPickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        new ActivityResultCallback<ActivityResult>() {

                            /**
                             * Chỉ nhận Place hợp lệ về đúng ô; Cancel hoặc dữ liệu sai giữ điểm cũ.
                             */
                            @Override
                            public void onActivityResult(ActivityResult result) {
                                if (result.getResultCode() == RESULT_OK
                                        && result.getData() != null) {
                                    Intent data = result.getData();
                                    if (data.hasExtra(LocationKeys.PLACE_NAME)
                                            && data.hasExtra(LocationKeys.PLACE_LATITUDE)
                                            && data.hasExtra(LocationKeys.PLACE_LONGITUDE)) {
                                        String name = data.getStringExtra(LocationKeys.PLACE_NAME);
                                        double selectedLatitude =
                                                data.getDoubleExtra(
                                                        LocationKeys.PLACE_LATITUDE, Double.NaN);
                                        double selectedLongitude =
                                                data.getDoubleExtra(
                                                        LocationKeys.PLACE_LONGITUDE, Double.NaN);
                                        Place selectedPlace =
                                                new Place(
                                                        name, selectedLatitude, selectedLongitude);
                                        if (SearchTripPolicy.validPlace(selectedPlace)) {
                                            if (currentPickingField == 1) {
                                                origin = selectedPlace;
                                            } else {
                                                if (currentPickingField == 2) {
                                                    destination = selectedPlace;
                                                }
                                            }
                                            updateUI();
                                        } else {
                                            Toast.makeText(
                                                            HomeActivity.this,
                                                            "Địa điểm trả về không hợp lệ. Vui lòng"
                                                                    + " chọn lại.",
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        }
                                    }
                                }
                                // Reset lại ô đang chọn
                                currentPickingField = 0;
                            }
                        });
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            // Mặc định là null và hiển thị hint
            originValueTextView.setText("Chọn điểm đón");
            destinationValueTextView.setText("Chọn điểm xuống");
            updateDateTimeUI();
        }
        setupListeners();
    }

    /** Gắn sự kiện lắng nghe (click listeners) cho các thành phần trên giao diện. */
    private void setupListeners() {
        findViewById(R.id.pick_start)
                .setOnClickListener(
                        new View.OnClickListener() {

                            @Override
                            public void onClick(View clickedView) {
                                currentPickingField = 1;
                                openLocationPicker();
                            }
                        });
        findViewById(R.id.pick_end)
                .setOnClickListener(
                        new View.OnClickListener() {

                            @Override
                            public void onClick(View clickedView) {
                                currentPickingField = 2;
                                openLocationPicker();
                            }
                        });
        findViewById(R.id.pick_date)
                .setOnClickListener(
                        new View.OnClickListener() {

                            @Override
                            public void onClick(View clickedView) {
                                openDatePicker();
                            }
                        });
        findViewById(R.id.pick_time)
                .setOnClickListener(
                        new View.OnClickListener() {

                            @Override
                            public void onClick(View clickedView) {
                                openTimePicker();
                            }
                        });
        findViewById(R.id.primary_action)
                .setOnClickListener(
                        new View.OnClickListener() {

                            @Override
                            public void onClick(View clickedView) {
                                validateAndSearch();
                            }
                        });
        MainNavigation.bind(this);
        MainNavigation.bindNotifications(this, R.id.home_notifications);
    }

    /** Khởi chạy LocationActivity để người dùng tìm kiếm và chọn địa điểm. */
    private void openLocationPicker() {
        Intent intent = new Intent(this, LocationActivity.class);
        locationPickerLauncher.launch(intent);
    }

    /**
     * Mở hộp thoại chọn ngày (DatePickerDialog). Ngăn chặn người dùng chọn các ngày trong quá khứ.
     */
    private void openDatePicker() {
        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        new DatePickerDialog.OnDateSetListener() {

                            @Override
                            public void onDateSet(
                                    DatePicker view, int year, int month, int dayOfMonth) {
                                searchTime.set(Calendar.YEAR, year);
                                searchTime.set(Calendar.MONTH, month);
                                searchTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                                updateDateTimeUI();
                            }
                        },
                        searchTime.get(Calendar.YEAR),
                        searchTime.get(Calendar.MONTH),
                        searchTime.get(Calendar.DAY_OF_MONTH));
        // Không cho chọn ngày quá khứ
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    /** Mở hộp thoại chọn giờ (TimePickerDialog) theo định dạng 24h. */
    private void openTimePicker() {
        TimePickerDialog dialog =
                new TimePickerDialog(
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
                        true);
        dialog.show();
    }

    /**
     * Kiểm tra tính hợp lệ của dữ liệu đầu vào (Validation) trước khi tìm kiếm. Nếu hợp lệ, đóng
     * gói dữ liệu và chuyển sang màn hình kết quả (ResultsActivity).
     */
    private void validateAndSearch() {
        // Giao diện chọn tới phút; bỏ giây ẩn để không loại nhầm chuyến đúng giờ đã chọn.
        searchTime.set(Calendar.SECOND, 0);
        searchTime.set(Calendar.MILLISECOND, 0);
        if (origin == null) {
            Toast.makeText(this, "Vui lòng chọn điểm đi!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destination == null) {
            Toast.makeText(this, "Vui lòng chọn điểm đến!", Toast.LENGTH_SHORT).show();
            return;
        }
        // Kiểm tra khoảng cách / trùng tọa độ
        if (Math.abs(origin.getLatitude() - destination.getLatitude()) < 0.0001
                && Math.abs(origin.getLongitude() - destination.getLongitude()) < 0.0001) {
            Toast.makeText(this, "Điểm đi và điểm đến không được trùng nhau!", Toast.LENGTH_SHORT)
                    .show();
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

    /** Cập nhật văn bản hiển thị cho ô chọn Điểm đi và Điểm đến trên UI. */
    private void updateUI() {
        // Đổi điểm là vô hiệu hóa cả nhãn quãng đường lẫn callback tuyến cũ.
        currentRouteRequestId = currentRouteRequestId + 1;
        final int requestId = currentRouteRequestId;
        routingClient.cancelCurrentRequest();
        routeDistanceTextView.setText("Chọn điểm đón và điểm xuống để xem quãng đường");
        // LUÔN LUÔN xóa toàn bộ ghim và đường vẽ cũ đi trước khi bắt đầu vẽ lại
        mapView.getOverlays().clear();
        GeoPoint startPoint = null;
        GeoPoint endPoint = null;
        if (origin != null) {
            originValueTextView.setText(origin.getName());
            startPoint = new GeoPoint(origin.getLatitude(), origin.getLongitude());
            Marker startMarker = new Marker(mapView);
            startMarker.setPosition(startPoint);
            startMarker.setTitle(origin.getName());
            mapView.getOverlays().add(startMarker);
        } else {
            originValueTextView.setText("Chọn điểm đón");
        }
        if (destination != null) {
            destinationValueTextView.setText(destination.getName());
            endPoint = new GeoPoint(destination.getLatitude(), destination.getLongitude());
            Marker endMarker = new Marker(mapView);
            endMarker.setPosition(endPoint);
            endMarker.setTitle(destination.getName());
            mapView.getOverlays().add(endMarker);
        } else {
            destinationValueTextView.setText("Chọn điểm xuống");
        }
        if (startPoint != null && endPoint != null) {
            // Zoom để thấy cả 2 điểm
            mapView.zoomToBoundingBox(
                    new org.osmdroid.util.BoundingBox(
                            Math.max(startPoint.getLatitude(), endPoint.getLatitude()),
                            Math.max(startPoint.getLongitude(), endPoint.getLongitude()),
                            Math.min(startPoint.getLatitude(), endPoint.getLatitude()),
                            Math.min(startPoint.getLongitude(), endPoint.getLongitude())),
                    true);
            routeDistanceTextView.setText("Đang tính quãng đường đường bộ…");
            fetchAndDrawRoute(startPoint, endPoint, requestId);
        } else {
            if (startPoint != null) {
                mapView.getController().animateTo(startPoint);
            } else {
                if (endPoint != null) {
                    mapView.getController().animateTo(endPoint);
                }
            }
        }
        mapView.invalidate();
    }

    /** Gọi Geoapify Routing API để lấy dữ liệu tuyến đường (GeoJSON) và vẽ Polyline trên bản đồ. */
    private void fetchAndDrawRoute(final GeoPoint start, final GeoPoint end, final int requestId) {
        routeExecutor.execute(
                new Runnable() {

                    /**
                     * Gọi Routing ngoài UI; kết quả chỉ được nhận nếu toàn tuyến đã qua kiểm tra
                     * VN.
                     */
                    @Override
                    public void run() {
                        final VietnamRoutingClient.Cancellation cancellation =
                                new VietnamRoutingClient.Cancellation() {

                                    /**
                                     * Lượt cũ không tiếp tục gọi API dự phòng sau khi người dùng
                                     * đổi điểm.
                                     */
                                    @Override
                                    public boolean isCancelled() {
                                        return requestId != currentRouteRequestId;
                                    }
                                };
                        if (cancellation.isCancelled()) {
                            return;
                        }
                        try {
                            java.util.List<VietnamRoutingClient.Point> endpoints =
                                    java.util.Arrays.asList(
                                            new VietnamRoutingClient.Point(
                                                    start.getLatitude(), start.getLongitude()),
                                            new VietnamRoutingClient.Point(
                                                    end.getLatitude(), end.getLongitude()));
                            final VietnamRoutingClient.Route route =
                                    routingClient.findRoute(endpoints, cancellation);
                            runOnUiThread(
                                    new Runnable() {

                                        /**
                                         * Vẽ riêng từng đoạn API, không nối tắt qua chỗ geometry bị
                                         * ngắt.
                                         */
                                        @Override
                                        public void run() {
                                            if (isFinishing()
                                                    || isDestroyed()
                                                    || cancellation.isCancelled()) {
                                                return;
                                            }
                                            for (int overlayIndex =
                                                            mapView.getOverlays().size() - 1;
                                                    overlayIndex >= 0;
                                                    overlayIndex = overlayIndex - 1) {
                                                if (mapView.getOverlays().get(overlayIndex)
                                                        instanceof
                                                        org.osmdroid.views.overlay.Polyline) {
                                                    mapView.getOverlays().remove(overlayIndex);
                                                }
                                            }
                                            for (java.util.List<VietnamRoutingClient.Point>
                                                    segment : route.segments) {
                                                java.util.List<GeoPoint> points =
                                                        new java.util.ArrayList<GeoPoint>();
                                                for (VietnamRoutingClient.Point point : segment) {
                                                    points.add(
                                                            new GeoPoint(
                                                                    point.latitude,
                                                                    point.longitude));
                                                }
                                                org.osmdroid.views.overlay.Polyline line =
                                                        new org.osmdroid.views.overlay.Polyline();
                                                line.setPoints(points);
                                                line.getOutlinePaint()
                                                        .setColor(
                                                                android.graphics.Color.parseColor(
                                                                        "#4A80F5"));
                                                line.getOutlinePaint().setStrokeWidth(12f);
                                                mapView.getOverlays().add(0, line);
                                            }
                                            showRouteDistance(route.distanceMeters);
                                            mapView.postInvalidate();
                                        }
                                    });
                        } catch (java.io.IOException error) {
                            showRouteError(requestId, error.getMessage());
                        } catch (RuntimeException invalid) {
                            showRouteError(
                                    requestId,
                                    "Không xác nhận được tuyến Việt Nam cho các điểm đã chọn.");
                        }
                    }
                });
    }

    /** Hiển thị km của đúng tuyến vừa vẽ; thiếu distance không được coi là 0 km. */
    private void showRouteDistance(double distanceMeters) {
        if (Double.isFinite(distanceMeters) && distanceMeters >= 0) {
            routeDistanceTextView.setText(
                    "Quãng đường trong Việt Nam: "
                            + RouteDistanceFormatter.formatKilometers(distanceMeters));
        } else {
            routeDistanceTextView.setText("Tuyến chưa có số liệu quãng đường đường bộ.");
        }
    }

    /** Báo lỗi cho đúng lượt tìm, tránh để nhãn mắc ở trạng thái đang tính. */
    private void showRouteError(final int requestId, final String message) {
        runOnUiThread(
                new Runnable() {

                    /** Không cập nhật màn đã đóng hoặc tuyến đã bị thay thế. */
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed() || requestId != currentRouteRequestId) {
                            return;
                        }
                        routeDistanceTextView.setText(message);
                        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Cập nhật văn bản hiển thị cho ô chọn Ngày và Giờ trên UI. */
    private void updateDateTimeUI() {
        departureDateTextView.setText(dateFormat.format(searchTime.getTime()));
        departureTimeTextView.setText(timeFormat.format(searchTime.getTime()));
    }

    /**
     * Lưu trạng thái của Form (Điểm đi, Điểm đến, Thời gian, Trường đang chọn) trước khi Activity
     * bị hủy để phục hồi sau khi xoay màn hình.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_picking_field", currentPickingField);
        outState.putLong("search_time", searchTime.getTimeInMillis());
        if (SearchTripPolicy.validPlace(origin)) {
            outState.putString("origin_name", origin.getName());
            outState.putDouble("origin_lat", origin.getLatitude());
            outState.putDouble("origin_lng", origin.getLongitude());
        }
        if (SearchTripPolicy.validPlace(destination)) {
            outState.putString("dest_name", destination.getName());
            outState.putDouble("dest_lat", destination.getLatitude());
            outState.putDouble("dest_lng", destination.getLongitude());
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
        currentRouteRequestId = currentRouteRequestId + 1;
        routingClient.cancelCurrentRequest();
        mapView.onDetach();
        // Đảm bảo luồng phụ không chạy ngầm
        routeExecutor.shutdownNow();
    }

    /**
     * Khôi phục trạng thái của Form từ Bundle sau khi Activity được tạo lại (ví dụ: sau khi xoay
     * màn hình thiết bị).
     */
    private void restoreState(Bundle savedInstanceState) {
        currentPickingField = savedInstanceState.getInt("current_picking_field", 0);
        long time = savedInstanceState.getLong("search_time", System.currentTimeMillis());
        searchTime.setTimeInMillis(time);
        updateDateTimeUI();
        if (savedInstanceState.containsKey("origin_name")) {
            String name = savedInstanceState.getString("origin_name");
            double selectedLatitude = savedInstanceState.getDouble("origin_lat", Double.NaN);
            double selectedLongitude = savedInstanceState.getDouble("origin_lng", Double.NaN);
            Place restoredPlace = new Place(name, selectedLatitude, selectedLongitude);
            if (SearchTripPolicy.validPlace(restoredPlace)) {
                origin = restoredPlace;
            }
        }
        if (savedInstanceState.containsKey("dest_name")) {
            String name = savedInstanceState.getString("dest_name");
            double selectedLatitude = savedInstanceState.getDouble("dest_lat", Double.NaN);
            double selectedLongitude = savedInstanceState.getDouble("dest_lng", Double.NaN);
            Place restoredPlace = new Place(name, selectedLatitude, selectedLongitude);
            if (SearchTripPolicy.validPlace(restoredPlace)) {
                destination = restoredPlace;
            }
        }
        updateUI();
    }
}
