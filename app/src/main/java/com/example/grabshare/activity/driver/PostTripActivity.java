package com.example.grabshare.activity.driver;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.DatePicker;
import android.widget.TimePicker;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.grabshare.R;
import com.example.grabshare.activity.search.LocationActivity;
import com.example.grabshare.activity.search.RouteDistanceFormatter;
import com.example.grabshare.api.VietnamRoutingClient;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.driver.DriverTripRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hiển thị biểu mẫu và điều phối luồng đăng chuyến của chủ xe. Dữ liệu Firestore được xử lý trong DriverTripRepository.
 */
public class PostTripActivity extends AppCompatActivity {

    private static final int LOCATION_TARGET_NONE = 0;
    private static final int LOCATION_TARGET_ORIGIN = 1;
    private static final int LOCATION_TARGET_DESTINATION = 2;

    private static final String STATE_HAS_SELECTED_DATE =
            "has_selected_departure_date";
    private static final String STATE_SELECTED_YEAR =
            "selected_departure_year";
    private static final String STATE_SELECTED_MONTH =
            "selected_departure_month";
    private static final String STATE_SELECTED_DAY =
            "selected_departure_day";
    private static final String STATE_HAS_SELECTED_TIME =
            "has_selected_departure_time";
    private static final String STATE_SELECTED_HOUR =
            "selected_departure_hour";
    private static final String STATE_SELECTED_MINUTE =
            "selected_departure_minute";
    private static final String STATE_LOCATION_TARGET =
            "selected_location_target";
    private static final String STATE_TRIP_DOCUMENT_ID =
            "pending_trip_document_id";
    private static final String STATE_HAS_ORIGIN = "has_selected_origin";
    private static final String STATE_ORIGIN_NAME = "selected_origin_name";
    private static final String STATE_ORIGIN_LATITUDE = "selected_origin_latitude";
    private static final String STATE_ORIGIN_LONGITUDE = "selected_origin_longitude";
    private static final String STATE_HAS_DESTINATION = "has_selected_destination";
    private static final String STATE_DESTINATION_NAME = "selected_destination_name";
    private static final String STATE_DESTINATION_LATITUDE = "selected_destination_latitude";
    private static final String STATE_DESTINATION_LONGITUDE = "selected_destination_longitude";

    private LinearLayout originPlaceSelector;
    private LinearLayout destinationPlaceSelector;
    private LinearLayout departureDateSelector;
    private LinearLayout departureTimeSelector;

    private ImageButton backButton;
    private TextView departureDateValue;
    private TextView departureTimeValue;
    private TextView originPlaceValue;
    private TextView destinationPlaceValue;
    private TextView routeDistanceValue;
    private TextView suggestedCostValue;
    private AppCompatButton retryRouteEstimateButton;

    private EditText contributionAmountInput;
    private EditText noteInput;
    private CheckBox freeContributionCheckBox;
    private AppCompatButton postTripButton;
    private AppCompatButton useSuggestedCostButton;
    private RadioGroup maxDetourGroup;

    private DriverTripRepository tripRepository;
    private final VietnamRoutingClient routingClient =
            new VietnamRoutingClient("3ae0ba5bcf8843df8024203662e3d702");
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger routeCalculationGeneration = new AtomicInteger();
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private Place selectedOriginPlace;
    private Place selectedDestinationPlace;
    private Long baseDistanceMeters;
    private Long suggestedCostVnd;

    private boolean hasSelectedDepartureDate;
    private int selectedDepartureYear;
    private int selectedDepartureMonth;
    private int selectedDepartureDay;

    private boolean hasSelectedDepartureTime;
    private int selectedDepartureHour;
    private int selectedDepartureMinute;

    private int selectedLocationTarget = LOCATION_TARGET_NONE;
    private String tripDocumentId;
    private Long validatedContributionAmount;
    private boolean isSubmitting;

    /**
     * Khởi tạo giao diện, khôi phục trạng thái và kết nối sự kiện của biểu mẫu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_post);

        initializeViews();
        initializeRepository();
        registerLocationResultLauncher();
        restoreFormState(savedInstanceState);

        updateOriginPlaceDisplay();
        updateDestinationPlaceDisplay();
        updateDepartureDateDisplay();
        updateDepartureTimeDisplay();

        connectBackButton();
        connectOriginPlaceSelector();
        connectDestinationPlaceSelector();
        connectDepartureDateSelector();
        connectDepartureTimeSelector();
        connectFreeContributionOption();
        connectUseSuggestedCostButton();
        connectPostTripButton();
        connectSystemBackHandling();
        requestRouteEstimate();

        // Không tự gửi lại khi Activity được tạo lại sau khi xoay màn hình.
        setSubmitting(false);
    }

    /**
     * Ánh xạ các thành phần giao diện từ screen_post.xml.
     */
    private void initializeViews() {
        originPlaceSelector = findViewById(R.id.post_start);
        destinationPlaceSelector = findViewById(R.id.post_end);
        departureDateSelector = findViewById(R.id.post_date);
        departureTimeSelector = findViewById(R.id.post_time);

        backButton = findViewById(R.id.header_back);

        departureDateValue = findViewById(R.id.post_date_value);
        departureTimeValue = findViewById(R.id.post_time_value);
        originPlaceValue = findViewById(R.id.post_start_value);
        destinationPlaceValue = findViewById(R.id.post_end_value);
        routeDistanceValue = findViewById(R.id.route_distance_value);
        suggestedCostValue = findViewById(R.id.suggested_cost_value);
        retryRouteEstimateButton = findViewById(R.id.retry_route_estimate);

        contributionAmountInput = findViewById(R.id.post_cost);
        noteInput = findViewById(R.id.post_note);
        freeContributionCheckBox = findViewById(R.id.post_free);
        postTripButton = findViewById(R.id.primary_action);
        useSuggestedCostButton = findViewById(R.id.use_suggested_cost);
        maxDetourGroup = findViewById(R.id.max_detour_group);
    }

    /**
     * Tạo repository dùng chung Firebase Authentication và Firestore của ứng dụng.
     */
    private void initializeRepository() {
        tripRepository = new DriverTripRepository(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance()
        );
    }

    /**
     * Đăng ký Activity Result API trước khi Activity chuyển sang trạng thái STARTED. Mỗi lần tạo Activity đều đăng ký theo cùng một thứ tự.
     */
    private void registerLocationResultLauncher() {
        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<>() {
                    /**
                     * Nhận kết quả chọn địa điểm từ LocationActivity.
                     */
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        handleLocationResult(result);
                    }
                }
        );
    }

    /**
     * Khôi phục ngày, giờ, địa điểm và ID thao tác tạo chuyến sau khi xoay màn hình. Nội dung EditText và trạng thái CheckBox được Android tự khôi phục theo ID view.
     */
    private void restoreFormState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            hasSelectedDepartureDate = false;
            hasSelectedDepartureTime = false;
            selectedLocationTarget = LOCATION_TARGET_NONE;
            tripDocumentId = null;
            return;
        }

        hasSelectedDepartureDate = savedInstanceState.getBoolean(
                STATE_HAS_SELECTED_DATE,
                false
        );
        selectedDepartureYear = savedInstanceState.getInt(STATE_SELECTED_YEAR);
        selectedDepartureMonth = savedInstanceState.getInt(STATE_SELECTED_MONTH);
        selectedDepartureDay = savedInstanceState.getInt(STATE_SELECTED_DAY);

        hasSelectedDepartureTime = savedInstanceState.getBoolean(
                STATE_HAS_SELECTED_TIME,
                false
        );
        selectedDepartureHour = savedInstanceState.getInt(STATE_SELECTED_HOUR);
        selectedDepartureMinute = savedInstanceState.getInt(STATE_SELECTED_MINUTE);

        selectedLocationTarget = savedInstanceState.getInt(
                STATE_LOCATION_TARGET,
                LOCATION_TARGET_NONE
        );
        tripDocumentId = savedInstanceState.getString(STATE_TRIP_DOCUMENT_ID);

        selectedOriginPlace = restorePlace(
                savedInstanceState,
                STATE_HAS_ORIGIN,
                STATE_ORIGIN_NAME,
                STATE_ORIGIN_LATITUDE,
                STATE_ORIGIN_LONGITUDE
        );

        selectedDestinationPlace = restorePlace(
                savedInstanceState,
                STATE_HAS_DESTINATION,
                STATE_DESTINATION_NAME,
                STATE_DESTINATION_LATITUDE,
                STATE_DESTINATION_LONGITUDE
        );

        // Callback đang chạy ở Activity cũ không tự khởi động lại thao tác trên Activity mới.
        isSubmitting = false;
    }

    /**
     * Lưu trạng thái form để các trường đã chọn không mất khi Android tạo lại Activity.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        savedInstanceState.putBoolean(
                STATE_HAS_SELECTED_DATE,
                hasSelectedDepartureDate
        );
        savedInstanceState.putInt(STATE_SELECTED_YEAR, selectedDepartureYear);
        savedInstanceState.putInt(STATE_SELECTED_MONTH, selectedDepartureMonth);
        savedInstanceState.putInt(STATE_SELECTED_DAY, selectedDepartureDay);

        savedInstanceState.putBoolean(
                STATE_HAS_SELECTED_TIME,
                hasSelectedDepartureTime
        );
        savedInstanceState.putInt(STATE_SELECTED_HOUR, selectedDepartureHour);
        savedInstanceState.putInt(STATE_SELECTED_MINUTE, selectedDepartureMinute);

        savedInstanceState.putInt(STATE_LOCATION_TARGET, selectedLocationTarget);
        savedInstanceState.putString(STATE_TRIP_DOCUMENT_ID, tripDocumentId);

        savePlace(
                savedInstanceState,
                selectedOriginPlace,
                STATE_HAS_ORIGIN,
                STATE_ORIGIN_NAME,
                STATE_ORIGIN_LATITUDE,
                STATE_ORIGIN_LONGITUDE
        );
        savePlace(
                savedInstanceState,
                selectedDestinationPlace,
                STATE_HAS_DESTINATION,
                STATE_DESTINATION_NAME,
                STATE_DESTINATION_LATITUDE,
                STATE_DESTINATION_LONGITUDE
        );

        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Lưu một địa điểm vào Bundle bằng các kiểu dữ liệu cơ bản, không truyền model Serializable.
     */
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

    /**
     * Khôi phục địa điểm từ Bundle nếu trước đó đã có một lựa chọn hợp lệ.
     */
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

        String name = state.getString(nameKey);
        double latitude = state.getDouble(latitudeKey, Double.NaN);
        double longitude = state.getDouble(longitudeKey, Double.NaN);
        Place restoredPlace = new Place(name, latitude, longitude);

        if (isValidPlace(restoredPlace)) {
            return null;
        }

        return restoredPlace;
    }

    /**
     * Kết nối nút quay lại; không cho rời form trong lúc gửi đang được xử lý.
     */
    private void connectBackButton() {
        backButton.setOnClickListener(new View.OnClickListener() {
            /**
             * Quay lại màn hình trước nếu hiện không có thao tác ghi đang chờ.
             */
            @Override
            public void onClick(View view) {
                if (isSubmitting) {
                    showMessage("Đang đăng chuyến. Vui lòng đợi kết quả từ máy chủ.");
                    return;
                }

                finish();
            }
        });
    }

    /**
     * Chặn nút Back hệ thống trong lúc gửi để tránh rời màn hình khi chưa có kết quả.
     */
    private void connectSystemBackHandling() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    /**
                     * Xử lý Back hệ thống theo trạng thái gửi chuyến.
                     */
                    @Override
                    public void handleOnBackPressed() {
                        if (isSubmitting) {
                            showMessage(
                                    "Đang đăng chuyến. Vui lòng đợi kết quả từ máy chủ."
                            );
                            return;
                        }

                        finish();
                    }
                }
        );
    }

    /**
     * Mở LocationActivity để người dùng chọn điểm đi.
     */
    private void connectOriginPlaceSelector() {
        originPlaceSelector.setOnClickListener(new View.OnClickListener() {
            /**
             * Đánh dấu ô điểm đi rồi mở màn chọn địa điểm.
             */
            @Override
            public void onClick(View view) {
                openLocationPicker(LOCATION_TARGET_ORIGIN);
            }
        });
    }

    /**
     * Mở LocationActivity để người dùng chọn điểm đến.
     */
    private void connectDestinationPlaceSelector() {
        destinationPlaceSelector.setOnClickListener(new View.OnClickListener() {
            /**
             * Đánh dấu ô điểm đến rồi mở màn chọn địa điểm.
             */
            @Override
            public void onClick(View view) {
                openLocationPicker(LOCATION_TARGET_DESTINATION);
            }
        });
    }

    /**
     * Lưu ô đang chọn trước khi mở Activity Result API.
     */
    private void openLocationPicker(int requestedTarget) {
        if (isSubmitting) {
            showMessage("Không thể đổi địa điểm trong lúc đang đăng chuyến.");
            return;
        }

        selectedLocationTarget = requestedTarget;
        Intent locationIntent = new Intent(this, LocationActivity.class);
        locationPickerLauncher.launch(locationIntent);
    }

    /**
     * Kiểm tra kết quả chọn địa điểm trước khi thay đổi dữ liệu của form. Back, hủy hoặc kết quả thiếu field sẽ không làm thay đổi lựa chọn hiện có.
     */
    private void handleLocationResult(ActivityResult result) {
        int requestedTarget = selectedLocationTarget;
        selectedLocationTarget = LOCATION_TARGET_NONE;

        if (result == null
                || result.getResultCode() != RESULT_OK
                || result.getData() == null) {
            return;
        }

        Intent resultIntent = result.getData();

        if (!resultIntent.hasExtra(LocationKeys.PLACE_NAME)
                || !resultIntent.hasExtra(LocationKeys.PLACE_LATITUDE)
                || !resultIntent.hasExtra(LocationKeys.PLACE_LONGITUDE)) {
            showMessage("Kết quả địa điểm không đầy đủ. Vui lòng chọn lại.");
            return;
        }

        String placeName = resultIntent.getStringExtra(LocationKeys.PLACE_NAME);
        double latitude = resultIntent.getDoubleExtra(
                LocationKeys.PLACE_LATITUDE,
                Double.NaN
        );
        double longitude = resultIntent.getDoubleExtra(
                LocationKeys.PLACE_LONGITUDE,
                Double.NaN
        );

        Place selectedPlace = new Place(placeName, latitude, longitude);

        if (isValidPlace(selectedPlace)) {
            showMessage("Địa điểm trả về không hợp lệ. Vui lòng chọn lại.");
            return;
        }

        if (requestedTarget == LOCATION_TARGET_ORIGIN) {
            selectedOriginPlace = selectedPlace;
            updateOriginPlaceDisplay();
        } else if (requestedTarget == LOCATION_TARGET_DESTINATION) {
            selectedDestinationPlace = selectedPlace;
            updateDestinationPlaceDisplay();
        }

        requestRouteEstimate();
    }

    /**
     * Tính lại quãng đường bộ sau khi địa chỉ hợp lệ thay đổi. Kết quả của lượt cũ
     * bị vô hiệu hóa để không ghi đè dữ liệu của cặp điểm mới.
     */
    private void requestRouteEstimate() {
        final int requestId = routeCalculationGeneration.incrementAndGet();
        routingClient.cancelCurrentRequest();
        baseDistanceMeters = null;
        suggestedCostVnd = null;
        useSuggestedCostButton.setEnabled(false);
        retryRouteEstimateButton.setVisibility(View.GONE);
        retryRouteEstimateButton.setEnabled(!isSubmitting);

        if (selectedOriginPlace == null || selectedDestinationPlace == null) {
            routeDistanceValue.setText("Chọn điểm đi và điểm đến để tính quãng đường.");
            suggestedCostValue.setText("Chưa có ước tính.");
            return;
        }

        if (isValidPlace(selectedOriginPlace) || isValidPlace(selectedDestinationPlace)
                || sameCoordinates(selectedOriginPlace, selectedDestinationPlace)) {
            routeDistanceValue.setText("Hai địa điểm chưa hợp lệ để tính tuyến.");
            suggestedCostValue.setText("Chưa có ước tính.");
            return;
        }

        final Place origin = selectedOriginPlace;
        final Place destination = selectedDestinationPlace;
        routeDistanceValue.setText("Đang tính quãng đường đường bộ...");
        suggestedCostValue.setText("Đang tính giá tham khảo...");

        routeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final VietnamRoutingClient.Cancellation cancellation =
                        new VietnamRoutingClient.Cancellation() {
                            @Override
                            public boolean isCancelled() {
                                return requestId != routeCalculationGeneration.get()
                                        || Thread.currentThread().isInterrupted();
                            }
                        };

                try {
                    VietnamRoutingClient.Route route = routingClient.findRoute(
                            Arrays.asList(
                                    new VietnamRoutingClient.Point(
                                            origin.getLatitude(),
                                            origin.getLongitude()
                                    ),
                                    new VietnamRoutingClient.Point(
                                            destination.getLatitude(),
                                            destination.getLongitude()
                                    )
                            ),
                            cancellation
                    );

                    final long calculatedDistanceMeters = Math.round(route.distanceMeters);
                    final long calculatedSuggestedCost = Math.round(route.distanceMeters * 3.0);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityUsable()
                                    || requestId != routeCalculationGeneration.get()) {
                                return;
                            }

                            baseDistanceMeters = calculatedDistanceMeters;
                            suggestedCostVnd = calculatedSuggestedCost;
                            routeDistanceValue.setText(
                                    "Quãng đường trong Việt Nam: "
                                            + RouteDistanceFormatter.formatKilometers(
                                                    route.distanceMeters
                                            )
                            );
                            suggestedCostValue.setText(formatSuggestedCost(calculatedSuggestedCost));
                            useSuggestedCostButton.setEnabled(!isSubmitting);
                            retryRouteEstimateButton.setVisibility(View.GONE);
                        }
                    });
                } catch (IOException error) {
                    final String errorMessage = error.getMessage() == null
                            ? "Không thể tính quãng đường. Hãy kiểm tra mạng và thử lại."
                            : error.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityUsable()
                                    || requestId != routeCalculationGeneration.get()) {
                                return;
                            }

                            baseDistanceMeters = null;
                            suggestedCostVnd = null;
                            routeDistanceValue.setText("Không thể xác nhận quãng đường.");
                            suggestedCostValue.setText("Chưa có ước tính.");
                            useSuggestedCostButton.setEnabled(false);
                            retryRouteEstimateButton.setVisibility(View.VISIBLE);
                            showMessage(errorMessage);
                        }
                    });
                } catch (RuntimeException invalidRoute) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityUsable()
                                    || requestId != routeCalculationGeneration.get()) {
                                return;
                            }

                            baseDistanceMeters = null;
                            suggestedCostVnd = null;
                            routeDistanceValue.setText("Dữ liệu tuyến không hợp lệ.");
                            suggestedCostValue.setText("Chưa có ước tính.");
                            useSuggestedCostButton.setEnabled(false);
                            retryRouteEstimateButton.setVisibility(View.VISIBLE);
                            showMessage("Không xác nhận được tuyến cho hai địa điểm đã chọn.");
                        }
                    });
                }
            }
        });
    }

    /** Định dạng số tiền tham khảo, không thay đổi số tiền người dùng đã nhập. */
    private String formatSuggestedCost(long amountVnd) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
                .format(amountVnd) + " đ";
    }

    /** Chỉ điền tiền tham khảo vào ô chi phí khi chủ xe chủ động nhấn nút. */
    private void connectUseSuggestedCostButton() {
        useSuggestedCostButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (suggestedCostVnd == null || isSubmitting) {
                    return;
                }

                if (freeContributionCheckBox.isChecked()) {
                    freeContributionCheckBox.setChecked(false);
                }
                contributionAmountInput.setText(String.valueOf(suggestedCostVnd));
                contributionAmountInput.setSelection(
                        contributionAmountInput.getText().length()
                );
            }
        });

        retryRouteEstimateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isSubmitting) {
                    requestRouteEstimate();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        routeCalculationGeneration.incrementAndGet();
        routingClient.cancelCurrentRequest();
        routeExecutor.shutdownNow();
        super.onDestroy();
    }

    /**
     * Hiển thị tên điểm đi hoặc gợi ý chọn điểm đi nếu chưa có lựa chọn.
     */
    private void updateOriginPlaceDisplay() {
        if (selectedOriginPlace == null) {
            originPlaceValue.setText("Chọn điểm đi");
            return;
        }

        originPlaceValue.setText(selectedOriginPlace.getName());
    }

    /**
     * Hiển thị tên điểm đến hoặc gợi ý chọn điểm đến nếu chưa có lựa chọn.
     */
    private void updateDestinationPlaceDisplay() {
        if (selectedDestinationPlace == null) {
            destinationPlaceValue.setText("Chọn điểm đến");
            return;
        }

        destinationPlaceValue.setText(selectedDestinationPlace.getName());
    }

    /**
     * Kết nối bộ chọn ngày khởi hành.
     */
    private void connectDepartureDateSelector() {
        departureDateSelector.setOnClickListener(new View.OnClickListener() {
            /**
             * Mở hộp thoại chọn ngày khởi hành.
             */
            @Override
            public void onClick(View view) {
                showDepartureDatePicker();
            }
        });
    }

    /**
     * Hiển thị hộp thoại chọn ngày; ngày sớm nhất là ngày hiện tại.
     */
    private void showDepartureDatePicker() {
        int initialYear;
        int initialMonth;
        int initialDay;

        if (hasSelectedDepartureDate) {
            initialYear = selectedDepartureYear;
            initialMonth = selectedDepartureMonth;
            initialDay = selectedDepartureDay;
        } else {
            Calendar currentCalendar = Calendar.getInstance();
            initialYear = currentCalendar.get(Calendar.YEAR);
            initialMonth = currentCalendar.get(Calendar.MONTH);
            initialDay = currentCalendar.get(Calendar.DAY_OF_MONTH);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    /**
                     * Lưu ngày người dùng vừa chọn.
                     */
                    @Override
                    public void onDateSet(
                            DatePicker datePicker,
                            int selectedYear,
                            int selectedMonth,
                            int selectedDay
                    ) {
                        hasSelectedDepartureDate = true;
                        selectedDepartureYear = selectedYear;
                        selectedDepartureMonth = selectedMonth;
                        selectedDepartureDay = selectedDay;
                        updateDepartureDateDisplay();
                    }
                },
                initialYear,
                initialMonth,
                initialDay
        );

        Calendar minimumDepartureDate = Calendar.getInstance();
        minimumDepartureDate.set(Calendar.HOUR_OF_DAY, 0);
        minimumDepartureDate.set(Calendar.MINUTE, 0);
        minimumDepartureDate.set(Calendar.SECOND, 0);
        minimumDepartureDate.set(Calendar.MILLISECOND, 0);

        datePickerDialog.getDatePicker().setMinDate(
                minimumDepartureDate.getTimeInMillis()
        );
        datePickerDialog.show();
    }

    /**
     * Hiển thị ngày đã chọn theo định dạng ngày/tháng/năm.
     */
    private void updateDepartureDateDisplay() {
        if (!hasSelectedDepartureDate) {
            departureDateValue.setText("Chọn ngày khởi hành");
            return;
        }

        Calendar selectedDateCalendar = Calendar.getInstance();
        selectedDateCalendar.set(
                selectedDepartureYear,
                selectedDepartureMonth,
                selectedDepartureDay,
                0,
                0,
                0
        );
        selectedDateCalendar.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat dateFormatter = new SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.forLanguageTag("vi-VN")
        );

        departureDateValue.setText(
                dateFormatter.format(selectedDateCalendar.getTime())
        );
    }

    /**
     * Kết nối bộ chọn giờ khởi hành.
     */
    private void connectDepartureTimeSelector() {
        departureTimeSelector.setOnClickListener(new View.OnClickListener() {
            /**
             * Mở hộp thoại chọn giờ khởi hành.
             */
            @Override
            public void onClick(View view) {
                showDepartureTimePicker();
            }
        });
    }

    /**
     * Hiển thị hộp thoại chọn giờ theo định dạng 24 giờ.
     */
    private void showDepartureTimePicker() {
        int initialHour;
        int initialMinute;

        if (hasSelectedDepartureTime) {
            initialHour = selectedDepartureHour;
            initialMinute = selectedDepartureMinute;
        } else {
            Calendar currentCalendar = Calendar.getInstance();
            initialHour = currentCalendar.get(Calendar.HOUR_OF_DAY);
            initialMinute = currentCalendar.get(Calendar.MINUTE);
        }

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                new TimePickerDialog.OnTimeSetListener() {
                    /**
                     * Lưu giờ người dùng vừa chọn.
                     */
                    @Override
                    public void onTimeSet(
                            TimePicker timePicker,
                            int selectedHour,
                            int selectedMinute
                    ) {
                        hasSelectedDepartureTime = true;
                        selectedDepartureHour = selectedHour;
                        selectedDepartureMinute = selectedMinute;
                        updateDepartureTimeDisplay();
                    }
                },
                initialHour,
                initialMinute,
                true
        );

        timePickerDialog.show();
    }

    /**
     * Hiển thị giờ đã chọn theo định dạng 24 giờ.
     */
    private void updateDepartureTimeDisplay() {
        if (!hasSelectedDepartureTime) {
            departureTimeValue.setText("Chọn giờ khởi hành");
            return;
        }

        String formattedDepartureTime = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                selectedDepartureHour,
                selectedDepartureMinute
        );

        departureTimeValue.setText(formattedDepartureTime);
    }

    /**
     * Đồng bộ trường chi phí khi người dùng chọn hoặc bỏ chọn miễn phí.
     */
    private void connectFreeContributionOption() {
        freeContributionCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    /**
                     * Bật/tắt ô chi phí theo lựa chọn miễn phí.
                     */
                    @Override
                    public void onCheckedChanged(
                            @NonNull CompoundButton button,
                            boolean isChecked
                    ) {
                        if (isChecked) {
                            contributionAmountInput.setText("0");
                            contributionAmountInput.setEnabled(false);
                        } else {
                            contributionAmountInput.setEnabled(!isSubmitting);
                            contributionAmountInput.setText("");
                        }
                    }
                }
        );

        if (freeContributionCheckBox.isChecked()) {
            contributionAmountInput.setText("0");
            contributionAmountInput.setEnabled(false);
        } else {
            contributionAmountInput.setEnabled(true);
        }
    }

    /**
     * Kết nối nút đăng chuyến với luồng kiểm tra và ghi Firestore.
     */
    private void connectPostTripButton() {
        postTripButton.setText("Đăng chuyến");
        postTripButton.setOnClickListener(new View.OnClickListener() {
            /**
             * Bắt đầu xác thực và gửi chuyến khi người dùng nhấn nút.
             */
            @Override
            public void onClick(View view) {
                submitTrip();
            }
        });
    }

    /**
     * Kiểm tra dữ liệu phía giao diện trước khi truy cập Firestore.
     */
    private boolean validateForm() {
        if (!validateSelectedPlaces()) {
            return false;
        }

        if (baseDistanceMeters == null || baseDistanceMeters <= 0L) {
            showMessage("Vui lòng đợi hoặc tính lại quãng đường hợp lệ trước khi đăng chuyến.");
            return false;
        }
        if (getSelectedMaxDetourMeters() == null) {
            showMessage("Vui lòng chọn mức đi thêm tối đa.");
            return false;
        }

        if (!validateDepartureDateTime()) {
            return false;
        }

        return validateContributionAmount();
    }

    /**
     * Kiểm tra điểm đi và điểm đến đã được chọn, có tọa độ hợp lệ và không trùng nhau.
     */
    private boolean validateSelectedPlaces() {
        if (isValidPlace(selectedOriginPlace)) {
            showMessage("Vui lòng chọn điểm đi từ danh sách địa điểm.");
            return false;
        }

        if (isValidPlace(selectedDestinationPlace)) {
            showMessage("Vui lòng chọn điểm đến từ danh sách địa điểm.");
            return false;
        }

        if (sameCoordinates(selectedOriginPlace, selectedDestinationPlace)) {
            showMessage("Điểm đi và điểm đến không được trùng nhau.");
            return false;
        }

        return true;
    }

    /**
     * Kiểm tra ngày và giờ đã được chọn và thời điểm kết hợp nằm trong tương lai.
     */
    private boolean validateDepartureDateTime() {
        if (!hasSelectedDepartureDate) {
            showMessage("Vui lòng chọn ngày khởi hành.");
            return false;
        }

        if (!hasSelectedDepartureTime) {
            showMessage("Vui lòng chọn giờ khởi hành.");
            return false;
        }

        if (!isDepartureTimeInFuture()) {
            showMessage("Ngày và giờ khởi hành phải ở trong tương lai.");
            return false;
        }

        return true;
    }

    /**
     * Đọc chi phí thành Long và từ chối dữ liệu rỗng, sai định dạng hoặc âm.
     */
    private boolean validateContributionAmount() {
        String amountText = contributionAmountInput.getText().toString().trim();

        if (amountText.isEmpty()) {
            showMessage("Vui lòng nhập chi phí hoặc chọn miễn phí.");
            return false;
        }

        try {
            validatedContributionAmount = Long.parseLong(amountText);
        } catch (NumberFormatException exception) {
            showMessage("Chi phí phải là một số nguyên hợp lệ.");
            return false;
        }

        if (validatedContributionAmount < 0L) {
            showMessage("Chi phí không được là số âm.");
            return false;
        }

        return true;
    }

    /**
     * Xác định thời điểm khởi hành đã chọn có lớn hơn thời điểm hiện tại hay không.
     */
    private boolean isDepartureTimeInFuture() {
        if (!hasSelectedDepartureDate || !hasSelectedDepartureTime) {
            return false;
        }

        Calendar selectedDepartureCalendar = Calendar.getInstance();
        selectedDepartureCalendar.set(
                selectedDepartureYear,
                selectedDepartureMonth,
                selectedDepartureDay,
                selectedDepartureHour,
                selectedDepartureMinute,
                0
        );
        selectedDepartureCalendar.set(Calendar.MILLISECOND, 0);

        Calendar currentCalendar = Calendar.getInstance();
        return selectedDepartureCalendar.after(currentCalendar);
    }

    /** Lấy mức đi thêm đã chọn theo đơn vị mét, phù hợp hợp đồng Firestore. */
    private Long getSelectedMaxDetourMeters() {
        int selectedId = maxDetourGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.detour_0) {
            return 0L;
        }
        if (selectedId == R.id.detour_2000) {
            return 2000L;
        }
        if (selectedId == R.id.detour_5000) {
            return 5000L;
        }
        if (selectedId == R.id.detour_10000) {
            return 10000L;
        }
        return null;
    }

    /**
     * Khởi động luồng đăng chuyến, xác minh hồ sơ trước rồi mới yêu cầu transaction tạo document.
     */
    private void submitTrip() {
        if (isSubmitting) {
            return;
        }

        if (!validateForm()) {
            return;
        }

        if (isNetworkAvailable()) {
            showMessage("Không có kết nối mạng. Chuyến chưa được đăng; hãy kết nối rồi thử lại.");
            return;
        }

        if (tripDocumentId == null || tripDocumentId.trim().isEmpty()) {
            tripDocumentId = tripRepository.createTripDocumentId();
        }

        final String requestedTripId = tripDocumentId;
        final Place requestedOrigin = selectedOriginPlace;
        final Place requestedDestination = selectedDestinationPlace;
        final Timestamp requestedDepartureAt = new Timestamp(
                getSelectedDepartureCalendar().getTime()
        );
        final Long requestedCostVnd = validatedContributionAmount;
        final Long requestedBaseDistanceMeters = baseDistanceMeters;
        final Long requestedMaxDetourMeters = getSelectedMaxDetourMeters();
        final String requestedNote = noteInput.getText().toString().trim();

        setSubmitting(true);

        tripRepository.checkVehicleProfile(
                new RepositoryCallback<>() {
                    /**
                     * Tiếp tục tạo chuyến chỉ khi hồ sơ xe chính chủ đã đầy đủ.
                     */
                    @Override
                    public void onSuccess(Boolean hasVehicleProfile) {
                        if (isActivityUsable()) {
                            return;
                        }

                        if (!Boolean.TRUE.equals(hasVehicleProfile)) {
                            setSubmitting(false);
                            showMessage(
                                    "Hồ sơ xe chưa đủ tên xe hoặc biển số. "
                                            + "Vui lòng hoàn thiện hồ sơ trước khi đăng chuyến."
                            );
                            return;
                        }

                        if (isNetworkAvailable()) {
                            setSubmitting(false);
                            showMessage(
                                    "Mất kết nối mạng trước khi đăng. Chuyến chưa được tạo."
                            );
                            return;
                        }

                        createTripOnServer(
                                requestedTripId,
                                requestedOrigin,
                                requestedDestination,
                                requestedDepartureAt,
                                requestedCostVnd,
                                requestedNote,
                                requestedBaseDistanceMeters,
                                requestedMaxDetourMeters
                        );
                    }

                    /**
                     * Giữ form và cho phép thử lại nếu không đọc được hồ sơ từ máy chủ.
                     */
                    @Override
                    public void onError(Exception error) {
                        if (isActivityUsable()) {
                            return;
                        }

                        setSubmitting(false);
                        showMessage(
                                "Không thể xác minh hồ sơ xe. Hãy kiểm tra đăng nhập, "
                                        + "kết nối mạng và quyền Firestore rồi thử lại."
                        );
                    }
                }
        );
    }

    /**
     * Gọi repository tạo chuyến và chỉ điều hướng khi máy chủ xác nhận thành công.
     */
    private void createTripOnServer(
            final String requestedTripId,
            Place requestedOrigin,
            Place requestedDestination,
            Timestamp requestedDepartureAt,
            Long requestedCostVnd,
            String requestedNote,
            Long requestedBaseDistanceMeters,
            Long requestedMaxDetourMeters
    ) {
        tripRepository.createTrip(
                requestedTripId,
                requestedOrigin,
                requestedDestination,
                requestedDepartureAt,
                requestedCostVnd,
                requestedNote,
                requestedBaseDistanceMeters,
                requestedMaxDetourMeters,
                new RepositoryCallback<>() {
                    /**
                     * Mở danh sách chuyến sau khi transaction được máy chủ xác nhận.
                     */
                    @Override
                    public void onSuccess(Void ignored) {
                        if (isActivityUsable()) {
                            return;
                        }

                        setSubmitting(false);

                        Intent driverTripsIntent = new Intent(
                                PostTripActivity.this,
                                DriverTripsActivity.class
                        );
                        driverTripsIntent.putExtra(
                                NavigationKeys.TRIP_ID,
                                requestedTripId
                        );

                        startActivity(driverTripsIntent);
                        finish();
                    }

                    /**
                     * Không báo thành công khi transaction bị lỗi hoặc bị Rules từ chối.
                     */
                    @Override
                    public void onError(Exception error) {
                        if (isActivityUsable()) {
                            return;
                        }

                        setSubmitting(false);
                        showMessage(
                                "Không thể đăng chuyến lên máy chủ. "
                                        + "Biểu mẫu vẫn được giữ; hãy kiểm tra dữ liệu, "
                                        + "kết nối và quyền truy cập rồi thử lại."
                        );
                    }
                }
        );
    }

    /**
     * Bật hoặc khóa các điều khiển trong thời gian xác minh/ghi để tránh gửi kép.
     */
    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;

        originPlaceSelector.setEnabled(!submitting);
        originPlaceSelector.setClickable(!submitting);
        destinationPlaceSelector.setEnabled(!submitting);
        destinationPlaceSelector.setClickable(!submitting);
        departureDateSelector.setEnabled(!submitting);
        departureTimeSelector.setEnabled(!submitting);
        freeContributionCheckBox.setEnabled(!submitting);
        contributionAmountInput.setEnabled(
                !submitting && !freeContributionCheckBox.isChecked()
        );
        useSuggestedCostButton.setEnabled(!submitting && suggestedCostVnd != null);
        retryRouteEstimateButton.setEnabled(!submitting);
        for (int index = 0; index < maxDetourGroup.getChildCount(); index++) {
            maxDetourGroup.getChildAt(index).setEnabled(!submitting);
        }
        noteInput.setEnabled(!submitting);
        backButton.setEnabled(!submitting);
        postTripButton.setEnabled(!submitting);

        if (submitting) {
            postTripButton.setText("Đang đăng chuyến...");
        } else {
            postTripButton.setText("Đăng chuyến");
        }
    }

    /**
     * Kiểm tra kết nối mạng hiện tại trước khi bắt đầu thao tác ghi. Transaction Firestore vẫn là lớp bảo vệ cuối nếu mạng bị mất ngay sau kiểm tra.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return true;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();

        if (activeNetwork == null) {
            return true;
        }

        NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);

        if (networkCapabilities == null) {
            return true;
        }

        return !networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) || !networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
        );
    }

    /**
     * Kiểm tra địa điểm từ bộ chọn hoặc Bundle có tên và tọa độ nằm trong giới hạn hợp lệ.
     */
    private boolean isValidPlace(Place place) {
        if (place == null
                || place.getName() == null
                || place.getName().trim().isEmpty()
                || place.getLatitude() == null
                || place.getLongitude() == null) {
            return true;
        }

        double latitude = place.getLatitude();
        double longitude = place.getLongitude();

        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            return true;
        }

        return !(latitude >= -90.0)
                || !(latitude <= 90.0)
                || !(longitude >= -180.0)
                || !(longitude <= 180.0);
    }

    /**
     * So sánh tọa độ để từ chối trường hợp điểm đi và điểm đến bị trùng nhau.
     */
    private boolean sameCoordinates(Place first, Place second) {
        return Double.compare(
                first.getLatitude(),
                second.getLatitude()
        ) == 0 && Double.compare(
                first.getLongitude(),
                second.getLongitude()
        ) == 0;
    }

    /**
     * Tạo Calendar từ ngày và giờ người dùng đã chọn.
     */
    private Calendar getSelectedDepartureCalendar() {
        Calendar selectedDepartureCalendar = Calendar.getInstance();
        selectedDepartureCalendar.set(
                selectedDepartureYear,
                selectedDepartureMonth,
                selectedDepartureDay,
                selectedDepartureHour,
                selectedDepartureMinute,
                0
        );
        selectedDepartureCalendar.set(Calendar.MILLISECOND, 0);
        return selectedDepartureCalendar;
    }

    /**
     * Kiểm tra Activity còn có thể nhận callback và điều hướng hay không. Callback của Activity cũ sau khi xoay màn hình sẽ không tự điều hướng.
     */
    private boolean isActivityUsable() {
        return isFinishing() || isDestroyed() || isChangingConfigurations();
    }

    /**
     * Hiển thị thông báo ngắn cho người dùng.
     */
    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
