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

/**
 * Activity Nhập điều kiện tìm chuyến.
 */
public class HomeActivity extends AppCompatActivity {

    private TextView tvOriginValue;
    private TextView tvDestinationValue;
    private TextView tvDateValue;
    private TextView tvTimeValue;
    private TextView mapStartLabel;
    private TextView mapEndLabel;

    private Place origin;
    private Place destination;
    private Calendar searchTime;

    private int currentPickingField = 0; // 1 = origin, 2 = destination
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

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
        
        mapStartLabel = findViewById(R.id.map_start_label);
        mapEndLabel = findViewById(R.id.map_end_label);

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

        // Tabs Bottom Nav
        findViewById(R.id.nav_trips).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Sẽ chuyển sang MyTrips của Thuận
                Toast.makeText(HomeActivity.this, "Chuyển sang Chuyến của tôi", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.nav_account).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Sẽ chuyển sang Account của Leader
                Toast.makeText(HomeActivity.this, "Chuyển sang Tài khoản", Toast.LENGTH_SHORT).show();
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
        if (origin != null) {
            tvOriginValue.setText(origin.getName());
            mapStartLabel.setText(origin.getName());
            mapStartLabel.setVisibility(View.VISIBLE);
        } else {
            tvOriginValue.setText("Chọn điểm đi");
            mapStartLabel.setVisibility(View.INVISIBLE);
        }

        if (destination != null) {
            tvDestinationValue.setText(destination.getName());
            mapEndLabel.setText(destination.getName());
            mapEndLabel.setVisibility(View.VISIBLE);
        } else {
            tvDestinationValue.setText("Chọn điểm đến");
            mapEndLabel.setVisibility(View.INVISIBLE);
        }
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