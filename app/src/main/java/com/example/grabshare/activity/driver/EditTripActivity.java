package com.example.grabshare.activity.driver;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.activity.search.LocationActivity;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.driver.DriverTripRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.Calendar;
import java.util.Locale;

/**
 * Màn hình chỉnh sửa chuyến. Activity quản lý form và điều hướng;
 * DriverTripRepository chịu trách nhiệm đọc/ghi Firestore.
 */
public class EditTripActivity extends AppCompatActivity {

    private static final int LOCATION_TARGET_NONE = 0;
    private static final int LOCATION_TARGET_ORIGIN = 1;
    private static final int LOCATION_TARGET_DESTINATION = 2;

    private static final String STATE_TRIP_ID = "edit_trip_id";
    private static final String STATE_HAS_LOADED = "edit_has_loaded";
    private static final String STATE_COST_TEXT = "edit_cost_text";
    private static final String STATE_NOTE_TEXT = "edit_note_text";
    private static final String STATE_SAVE_IN_PROGRESS = "edit_save_in_progress";
    private static final String STATE_LOCATION_TARGET = "edit_location_target";
    private static final String STATE_YEAR = "edit_departure_year";
    private static final String STATE_MONTH = "edit_departure_month";
    private static final String STATE_DAY = "edit_departure_day";
    private static final String STATE_HOUR = "edit_departure_hour";
    private static final String STATE_MINUTE = "edit_departure_minute";
    private static final String STATE_HAS_ORIGIN = "edit_has_origin";
    private static final String STATE_ORIGIN_NAME = "edit_origin_name";
    private static final String STATE_ORIGIN_LAT = "edit_origin_latitude";
    private static final String STATE_ORIGIN_LON = "edit_origin_longitude";
    private static final String STATE_HAS_DESTINATION = "edit_has_destination";
    private static final String STATE_DESTINATION_NAME = "edit_destination_name";
    private static final String STATE_DESTINATION_LAT = "edit_destination_latitude";
    private static final String STATE_DESTINATION_LON = "edit_destination_longitude";

    private LinearLayout originPicker;
    private LinearLayout destinationPicker;
    private LinearLayout departureDatePicker;
    private LinearLayout departureTimePicker;

    private ImageButton backButton;
    private TextView originValue;
    private TextView destinationValue;
    private TextView departureDateValue;
    private TextView departureTimeValue;
    private TextView statusMessage;

    private EditText costInput;
    private EditText noteInput;

    private AppCompatButton saveButton;
    private AppCompatButton cancelButton;

    private DriverTripRepository tripRepository;
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private String tripId;
    private Place selectedOrigin;
    private Place selectedDestination;

    // Calendar fields use the device's local time zone, matching the date/time pickers.
    private int departureYear;
    private int departureMonth;
    private int departureDay;
    private int departureHour;
    private int departureMinute;

    private int selectedLocationTarget = LOCATION_TARGET_NONE;
    private boolean hasLoadedTrip;
    private boolean isSaving;
    private boolean saveWasInProgressWhenRecreated;

    /** Khởi tạo View, khôi phục trạng thái và tải chuyến cần sửa. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_edit_trip);

        initializeViews();
        initializeRepository();
        registerLocationResultLauncher();
        connectEvents();
        connectSystemBackHandling();

        if (savedInstanceState != null) {
            tripId = savedInstanceState.getString(
                    STATE_TRIP_ID,
                    getIntent().getStringExtra(NavigationKeys.TRIP_ID)
            );
            restoreState(savedInstanceState);
        } else {
            tripId = getIntent().getStringExtra(NavigationKeys.TRIP_ID);
        }

        if (tripId == null || tripId.trim().isEmpty()) {
            showToast("Không tìm thấy ID chuyến cần sửa.");
            finish();
            return;
        }

        if (hasLoadedTrip) {
            updateOriginDisplay();
            updateDestinationDisplay();
            updateDepartureDateDisplay();
            updateDepartureTimeDisplay();
            setFormEnabled(true);
            if (saveWasInProgressWhenRecreated) {
                setStatusMessage(
                        "Màn hình đã xoay trong lúc lưu. Chưa xác nhận kết quả từ máy chủ; " +
                                "hãy kiểm tra chuyến trước khi thử lưu lại."
                );
            } else {
                setStatusMessage(null);
            }
        } else {
            setFormEnabled(false);
            setStatusMessage("Đang tải thông tin chuyến...");
            loadTrip();
        }
    }

    /** Ánh xạ các View trong screen_edit_trip.xml. */
    private void initializeViews() {
        originPicker = findViewById(R.id.post_start);
        destinationPicker = findViewById(R.id.post_end);
        departureDatePicker = findViewById(R.id.post_date);
        departureTimePicker = findViewById(R.id.post_time);

        backButton = findViewById(R.id.header_back);
        originValue = findViewById(R.id.post_start_value);
        destinationValue = findViewById(R.id.post_end_value);
        departureDateValue = findViewById(R.id.post_date_value);
        departureTimeValue = findViewById(R.id.post_time_value);
        statusMessage = findViewById(R.id.edit_status_message);

        costInput = findViewById(R.id.post_cost);
        noteInput = findViewById(R.id.post_note);
        saveButton = findViewById(R.id.primary_action);
        cancelButton = findViewById(R.id.secondary_action);
    }

    private void initializeRepository() {
        tripRepository = new DriverTripRepository(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance()
        );
    }

    /** Đăng ký Activity Result API theo cùng thứ tự trên mọi lần tạo Activity. */
    private void registerLocationResultLauncher() {
        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        handleLocationResult(result);
                    }
                }
        );
    }

    /** Nối các thao tác người dùng với các phương thức xử lý. */
    private void connectEvents() {
        originPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openLocationPicker(LOCATION_TARGET_ORIGIN);
            }
        });
        destinationPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openLocationPicker(LOCATION_TARGET_DESTINATION);
            }
        });
        departureDatePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDepartureDatePicker();
            }
        });
        departureTimePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDepartureTimePicker();
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmSave();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                leaveWithoutSaving();
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                leaveWithoutSaving();
            }
        });
    }

    private void connectSystemBackHandling() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        leaveWithoutSaving();
                    }
                }
        );
    }

    /** Tải từ server và kiểm tra quyền/trạng thái trước khi cho phép sửa form. */
    private void loadTrip() {
        tripRepository.loadTripForEdit(
                tripId,
                new RepositoryCallback<Trip>() {
                    @Override
                    public void onSuccess(Trip trip) {
                        if (!isActivityUsable()) {
                            return;
                        }

                        selectedOrigin = trip.getOrigin();
                        selectedDestination = trip.getDestination();

                        Calendar departureCalendar = Calendar.getInstance();
                        departureCalendar.setTime(trip.getDepartureAt().toDate());
                        departureYear = departureCalendar.get(Calendar.YEAR);
                        departureMonth = departureCalendar.get(Calendar.MONTH);
                        departureDay = departureCalendar.get(Calendar.DAY_OF_MONTH);
                        departureHour = departureCalendar.get(Calendar.HOUR_OF_DAY);
                        departureMinute = departureCalendar.get(Calendar.MINUTE);

                        costInput.setText(
                                trip.getCostVnd() == null
                                        ? ""
                                        : String.valueOf(trip.getCostVnd())
                        );
                        noteInput.setText(trip.getNote() == null ? "" : trip.getNote());

                        hasLoadedTrip = true;
                        updateOriginDisplay();
                        updateDestinationDisplay();
                        updateDepartureDateDisplay();
                        updateDepartureTimeDisplay();
                        setStatusMessage(null);
                        setFormEnabled(true);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isActivityUsable()) {
                            return;
                        }
                        showToast(readableError(error, true));
                        finish();
                    }
                }
        );
    }

    private void openLocationPicker(int target) {
        if (!hasLoadedTrip || isSaving) {
            return;
        }
        selectedLocationTarget = target;
        locationPickerLauncher.launch(new Intent(this, LocationActivity.class));
    }

    /** Nhận địa điểm kèm tọa độ; hủy chọn sẽ giữ nguyên giá trị hiện tại. */
    private void handleLocationResult(ActivityResult result) {
        int target = selectedLocationTarget;
        selectedLocationTarget = LOCATION_TARGET_NONE;

        if (result == null || result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }

        Intent data = result.getData();
        if (!data.hasExtra(LocationKeys.PLACE_NAME)
                || !data.hasExtra(LocationKeys.PLACE_LATITUDE)
                || !data.hasExtra(LocationKeys.PLACE_LONGITUDE)) {
            setStatusMessage("Kết quả chọn địa điểm không đầy đủ. Vui lòng chọn lại.");
            return;
        }

        Place place = new Place(
                data.getStringExtra(LocationKeys.PLACE_NAME),
                data.getDoubleExtra(LocationKeys.PLACE_LATITUDE, Double.NaN),
                data.getDoubleExtra(LocationKeys.PLACE_LONGITUDE, Double.NaN)
        );

        if (!isValidPlace(place)) {
            setStatusMessage("Địa điểm không hợp lệ. Vui lòng chọn lại từ danh sách.");
            return;
        }

        if (target == LOCATION_TARGET_ORIGIN) {
            selectedOrigin = place;
            updateOriginDisplay();
        } else if (target == LOCATION_TARGET_DESTINATION) {
            selectedDestination = place;
            updateDestinationDisplay();
        }
        setStatusMessage(null);
    }

    private void showDepartureDatePicker() {
        if (!hasLoadedTrip || isSaving) {
            return;
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(
                            android.widget.DatePicker picker,
                            int year,
                            int month,
                            int day
                    ) {
                        departureYear = year;
                        departureMonth = month;
                        departureDay = day;
                        updateDepartureDateDisplay();
                        setStatusMessage(null);
                    }
                },
                departureYear,
                departureMonth,
                departureDay
        );

        Calendar minimumDate = Calendar.getInstance();
        minimumDate.set(Calendar.HOUR_OF_DAY, 0);
        minimumDate.set(Calendar.MINUTE, 0);
        minimumDate.set(Calendar.SECOND, 0);
        minimumDate.set(Calendar.MILLISECOND, 0);
        dialog.getDatePicker().setMinDate(minimumDate.getTimeInMillis());
        dialog.show();
    }

    private void showDepartureTimePicker() {
        if (!hasLoadedTrip || isSaving) {
            return;
        }

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(
                            android.widget.TimePicker picker,
                            int hour,
                            int minute
                    ) {
                        departureHour = hour;
                        departureMinute = minute;
                        updateDepartureTimeDisplay();
                        setStatusMessage(null);
                    }
                },
                departureHour,
                departureMinute,
                true
        );
        dialog.show();
    }

    private void updateOriginDisplay() {
        originValue.setText(selectedOrigin == null ? "Chọn điểm đi" : selectedOrigin.getName());
    }

    private void updateDestinationDisplay() {
        destinationValue.setText(
                selectedDestination == null ? "Chọn điểm đến" : selectedDestination.getName()
        );
    }

    private void updateDepartureDateDisplay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(departureYear, departureMonth, departureDay, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        departureDateValue.setText(String.format(
                Locale.getDefault(),
                "%02d/%02d/%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
        ));
    }

    private void updateDepartureTimeDisplay() {
        departureTimeValue.setText(String.format(
                Locale.getDefault(), "%02d:%02d", departureHour, departureMinute
        ));
    }

    private void confirmSave() {
        if (!hasLoadedTrip || isSaving || !validateForm()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Lưu thay đổi?")
                .setMessage("Thông tin chuyến chỉ được cập nhật nếu chuyến vẫn OPEN và bạn còn quyền sửa.")
                .setNegativeButton("Tiếp tục sửa", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("Xác nhận", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        saveChanges();
                    }
                })
                .show();
    }

    private boolean validateForm() {
        if (!isValidPlace(selectedOrigin)) {
            setStatusMessage("Vui lòng chọn điểm đi từ danh sách địa điểm.");
            return false;
        }
        if (!isValidPlace(selectedDestination)) {
            setStatusMessage("Vui lòng chọn điểm đến từ danh sách địa điểm.");
            return false;
        }
        if (sameCoordinates(selectedOrigin, selectedDestination)) {
            setStatusMessage("Điểm đi và điểm đến không được trùng tọa độ.");
            return false;
        }

        Calendar departure = getSelectedDepartureCalendar();
        if (departure.getTimeInMillis() <= System.currentTimeMillis()) {
            setStatusMessage("Ngày và giờ khởi hành phải ở trong tương lai.");
            return false;
        }

        String costText = costInput.getText().toString().trim();
        if (costText.isEmpty()) {
            setStatusMessage("Vui lòng nhập chi phí; nhập 0 nếu miễn phí.");
            return false;
        }
        try {
            long cost = Long.parseLong(costText);
            if (cost < 0L) {
                setStatusMessage("Chi phí không được là số âm.");
                return false;
            }
        } catch (NumberFormatException exception) {
            setStatusMessage("Chi phí phải là số nguyên không âm.");
            return false;
        }

        setStatusMessage(null);
        return true;
    }

    /** Chỉ gọi repository sau khi người dùng xác nhận và dữ liệu form hợp lệ. */
    private void saveChanges() {
        if (!validateForm()) {
            return;
        }

        long cost;
        try {
            cost = Long.parseLong(costInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            setStatusMessage("Chi phí phải là số nguyên không âm.");
            return;
        }

        final Place originToSave = selectedOrigin;
        final Place destinationToSave = selectedDestination;
        final Timestamp departureToSave = new Timestamp(getSelectedDepartureCalendar().getTime());
        final Long costToSave = cost;
        final String noteToSave = noteInput.getText().toString().trim();

        isSaving = true;
        setFormEnabled(false);
        setStatusMessage("Đang lưu thay đổi...");

        tripRepository.updateTrip(
                tripId,
                originToSave,
                destinationToSave,
                departureToSave,
                costToSave,
                noteToSave,
                new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (!isActivityUsable()) {
                            return;
                        }
                        isSaving = false;
                        Intent result = new Intent();
                        result.putExtra(NavigationKeys.TRIP_ID, tripId);
                        setResult(RESULT_OK, result);
                        showToast("Đã lưu thay đổi chuyến.");
                        finish();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isActivityUsable()) {
                            return;
                        }
                        isSaving = false;
                        setFormEnabled(true);
                        setStatusMessage(readableError(error, false));
                    }
                }
        );
    }

    private Calendar getSelectedDepartureCalendar() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(departureYear, departureMonth, departureDay,
                departureHour, departureMinute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private boolean isValidPlace(Place place) {
        if (place == null || place.getName() == null || place.getName().trim().isEmpty()
                || place.getLatitude() == null || place.getLongitude() == null) {
            return false;
        }
        double latitude = place.getLatitude();
        double longitude = place.getLongitude();
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    private boolean sameCoordinates(Place first, Place second) {
        return Double.compare(first.getLatitude(), second.getLatitude()) == 0
                && Double.compare(first.getLongitude(), second.getLongitude()) == 0;
    }

    private void setFormEnabled(boolean enabled) {
        boolean canEdit = enabled && !isSaving;
        originPicker.setEnabled(canEdit);
        destinationPicker.setEnabled(canEdit);
        departureDatePicker.setEnabled(canEdit);
        departureTimePicker.setEnabled(canEdit);
        costInput.setEnabled(canEdit);
        noteInput.setEnabled(canEdit);
        saveButton.setEnabled(canEdit && hasLoadedTrip);
        saveButton.setText(isSaving ? "Đang lưu..." : "Lưu thay đổi");
        cancelButton.setEnabled(!isSaving);
        backButton.setEnabled(!isSaving);
    }

    private void setStatusMessage(String message) {
        if (statusMessage == null) {
            return;
        }
        if (message == null || message.trim().isEmpty()) {
            statusMessage.setVisibility(View.GONE);
        } else {
            statusMessage.setText(message);
            statusMessage.setVisibility(View.VISIBLE);
        }
    }

    private void leaveWithoutSaving() {
        if (isSaving) {
            showToast("Đang lưu thay đổi. Vui lòng chờ kết quả từ máy chủ.");
            return;
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private String readableError(Exception error, boolean duringLoad) {
        String message = error == null ? null : error.getMessage();

        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;
            if (firestoreError.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return duringLoad
                        ? "Không có quyền đọc chuyến này. Hãy kiểm tra tài khoản đang đăng nhập."
                        : "Máy chủ từ chối cập nhật. Hãy kiểm tra quyền sở hữu, trạng thái OPEN và Firestore Rules.";
            }
            if (firestoreError.getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "Không kết nối được Firestore. Kiểm tra mạng rồi thử lại; dữ liệu chưa được xác nhận là đã lưu.";
            }
            if (firestoreError.getCode() == FirebaseFirestoreException.Code.NOT_FOUND) {
                return "Chuyến không tồn tại hoặc đã bị xóa.";
            }
        }

        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return duringLoad
                ? "Không thể tải chuyến. Kiểm tra kết nối và quyền truy cập rồi thử lại."
                : "Không thể lưu thay đổi. Chuyến có thể đã đổi trạng thái hoặc quyền truy cập bị từ chối.";
    }

    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void restoreState(Bundle state) {
        hasLoadedTrip = state.getBoolean(STATE_HAS_LOADED, false);
        saveWasInProgressWhenRecreated = state.getBoolean(STATE_SAVE_IN_PROGRESS, false);
        selectedLocationTarget = state.getInt(STATE_LOCATION_TARGET, LOCATION_TARGET_NONE);
        departureYear = state.getInt(STATE_YEAR);
        departureMonth = state.getInt(STATE_MONTH);
        departureDay = state.getInt(STATE_DAY);
        departureHour = state.getInt(STATE_HOUR);
        departureMinute = state.getInt(STATE_MINUTE);

        selectedOrigin = restorePlace(
                state, STATE_HAS_ORIGIN, STATE_ORIGIN_NAME, STATE_ORIGIN_LAT, STATE_ORIGIN_LON
        );
        selectedDestination = restorePlace(
                state, STATE_HAS_DESTINATION, STATE_DESTINATION_NAME,
                STATE_DESTINATION_LAT, STATE_DESTINATION_LON
        );
    }

    private void savePlace(
            Bundle state,
            Place place,
            String hasPlaceKey,
            String nameKey,
            String latitudeKey,
            String longitudeKey
    ) {
        if (place == null) {
            state.putBoolean(hasPlaceKey, false);
            return;
        }
        state.putBoolean(hasPlaceKey, true);
        state.putString(nameKey, place.getName());
        state.putDouble(latitudeKey, place.getLatitude());
        state.putDouble(longitudeKey, place.getLongitude());
    }

    private Place restorePlace(
            Bundle state,
            String hasPlaceKey,
            String nameKey,
            String latitudeKey,
            String longitudeKey
    ) {
        if (!state.getBoolean(hasPlaceKey, false)) {
            return null;
        }
        Place place = new Place(
                state.getString(nameKey),
                state.getDouble(latitudeKey, Double.NaN),
                state.getDouble(longitudeKey, Double.NaN)
        );
        return isValidPlace(place) ? place : null;
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // Restore these drafts explicitly after Android restores the View hierarchy.
        if (savedInstanceState.containsKey(STATE_COST_TEXT)) {
            costInput.setText(savedInstanceState.getString(STATE_COST_TEXT, ""));
        }
        if (savedInstanceState.containsKey(STATE_NOTE_TEXT)) {
            noteInput.setText(savedInstanceState.getString(STATE_NOTE_TEXT, ""));
        }

        if (hasLoadedTrip) {
            updateOriginDisplay();
            updateDestinationDisplay();
            updateDepartureDateDisplay();
            updateDepartureTimeDisplay();
            setFormEnabled(true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_TRIP_ID, tripId);
        outState.putString(STATE_COST_TEXT, costInput.getText().toString());
        outState.putString(STATE_NOTE_TEXT, noteInput.getText().toString());
        outState.putBoolean(STATE_SAVE_IN_PROGRESS, isSaving);
        outState.putBoolean(STATE_HAS_LOADED, hasLoadedTrip);
        outState.putInt(STATE_LOCATION_TARGET, selectedLocationTarget);
        outState.putInt(STATE_YEAR, departureYear);
        outState.putInt(STATE_MONTH, departureMonth);
        outState.putInt(STATE_DAY, departureDay);
        outState.putInt(STATE_HOUR, departureHour);
        outState.putInt(STATE_MINUTE, departureMinute);
        savePlace(outState, selectedOrigin, STATE_HAS_ORIGIN,
                STATE_ORIGIN_NAME, STATE_ORIGIN_LAT, STATE_ORIGIN_LON);
        savePlace(outState, selectedDestination, STATE_HAS_DESTINATION,
                STATE_DESTINATION_NAME, STATE_DESTINATION_LAT, STATE_DESTINATION_LON);
        super.onSaveInstanceState(outState);
    }
}
