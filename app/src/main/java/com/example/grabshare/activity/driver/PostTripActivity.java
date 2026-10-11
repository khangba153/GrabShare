
package com.example.grabshare.activity.driver;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.DatePicker;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Hiển thị và xử lý biểu mẫu đăng chuyến dành cho chủ xe.
 * <p>
 * Giai đoạn này chỉ xử lý giao diện và kiểm tra dữ liệu đầu vào.
 * Chưa ghi chuyến lên Firestore.
 */
public class PostTripActivity extends AppCompatActivity {

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

    private LinearLayout departureDateSelector;
    private LinearLayout departureTimeSelector;

    private ImageButton backButton;

    private TextView departureDateValue;
    private TextView departureTimeValue;
    private TextView originPlaceValue;
    private TextView destinationPlaceValue;

    private EditText contributionAmountInput;

    private CheckBox freeContributionCheckBox;

    private boolean hasSelectedDepartureDate;
    private int selectedDepartureYear;
    private int selectedDepartureMonth;
    private int selectedDepartureDay;

    private boolean hasSelectedDepartureTime;
    private int selectedDepartureHour;
    private int selectedDepartureMinute;

    /**
     * Khởi tạo giao diện và kết nối các thao tác của biểu mẫu.
     *
     * @param savedInstanceState trạng thái đã lưu trước khi giao diện được tạo lại
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.screen_post);

        initializeViews();
        restoreDepartureSelection(savedInstanceState);
        updateDepartureDateDisplay();
        updateDepartureTimeDisplay();

        connectBackButton();
        connectDepartureDateSelector();
        connectDepartureTimeSelector();
        connectFreeContributionOption();
        connectFormValidationButton();
    }

    /**
     * Ánh xạ các thành phần giao diện từ tệp XML.
     */
    private void initializeViews() {
        departureDateSelector = findViewById(R.id.post_date);
        departureTimeSelector = findViewById(R.id.post_time);

        backButton = findViewById(R.id.header_back);

        departureDateValue = findViewById(R.id.post_date_value);
        departureTimeValue = findViewById(R.id.post_time_value);

        originPlaceValue = findViewById(R.id.post_start_value);
        destinationPlaceValue = findViewById(R.id.post_end_value);

        contributionAmountInput = findViewById(R.id.post_cost);
        findViewById(R.id.post_note);

        freeContributionCheckBox = findViewById(R.id.post_free);
    }

    /**
     * Khôi phục ngày và giờ đã chọn khi màn hình được tạo lại.
     *
     * @param savedInstanceState trạng thái đã lưu, có thể không tồn tại
     */
    private void restoreDepartureSelection(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            hasSelectedDepartureDate = false;
            hasSelectedDepartureTime = false;
            return;
        }

        hasSelectedDepartureDate = savedInstanceState.getBoolean(
                STATE_HAS_SELECTED_DATE,
                false
        );

        selectedDepartureYear = savedInstanceState.getInt(
                STATE_SELECTED_YEAR
        );

        selectedDepartureMonth = savedInstanceState.getInt(
                STATE_SELECTED_MONTH
        );

        selectedDepartureDay = savedInstanceState.getInt(
                STATE_SELECTED_DAY
        );

        hasSelectedDepartureTime = savedInstanceState.getBoolean(
                STATE_HAS_SELECTED_TIME,
                false
        );

        selectedDepartureHour = savedInstanceState.getInt(
                STATE_SELECTED_HOUR
        );

        selectedDepartureMinute = savedInstanceState.getInt(
                STATE_SELECTED_MINUTE
        );
    }

    /**
     * Lưu ngày và giờ đã chọn khi Android tạo lại màn hình.
     *
     * @param savedInstanceState nơi lưu trạng thái giao diện
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(
                STATE_HAS_SELECTED_DATE,
                hasSelectedDepartureDate
        );

        savedInstanceState.putInt(
                STATE_SELECTED_YEAR,
                selectedDepartureYear
        );

        savedInstanceState.putInt(
                STATE_SELECTED_MONTH,
                selectedDepartureMonth
        );

        savedInstanceState.putInt(
                STATE_SELECTED_DAY,
                selectedDepartureDay
        );

        savedInstanceState.putBoolean(
                STATE_HAS_SELECTED_TIME,
                hasSelectedDepartureTime
        );

        savedInstanceState.putInt(
                STATE_SELECTED_HOUR,
                selectedDepartureHour
        );

        savedInstanceState.putInt(
                STATE_SELECTED_MINUTE,
                selectedDepartureMinute
        );
    }

    /**
     * Đóng biểu mẫu và quay về màn hình trước đó.
     */
    private void connectBackButton() {
        backButton.setOnClickListener(new View.OnClickListener() {

            /**
             * Đóng màn hình đăng chuyến khi người dùng nhấn nút quay lại.
             *
             * @param view thành phần giao diện được nhấn
             */
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    /**
     * Kết nối bộ chọn ngày và chỉ cho phép chọn ngày hiện tại hoặc ngày tương lai.
     */
    private void connectDepartureDateSelector() {
        departureDateSelector.setOnClickListener(
                new View.OnClickListener() {

                    /**
                     * Mở hộp thoại chọn ngày khởi hành.
                     *
                     * @param view thành phần giao diện được nhấn
                     */
                    @Override
                    public void onClick(View view) {
                        showDepartureDatePicker();
                    }
                }
        );
    }

    /**
     * Hiển thị hộp thoại chọn ngày khởi hành.
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
                     * Cập nhật ngày khởi hành theo ngày người dùng chọn.
                     *
                     * @param datePicker hộp chọn ngày
                     * @param selectedYear năm được chọn
                     * @param selectedMonth tháng được chọn, bắt đầu từ số không
                     * @param selectedDay ngày trong tháng được chọn
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

        minimumDepartureDate.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        minimumDepartureDate.set(
                Calendar.MINUTE,
                0
        );

        minimumDepartureDate.set(
                Calendar.SECOND,
                0
        );

        minimumDepartureDate.set(
                Calendar.MILLISECOND,
                0
        );

        datePickerDialog.getDatePicker().setMinDate(
                minimumDepartureDate.getTimeInMillis()
        );

        datePickerDialog.show();
    }

    /**
     * Hiển thị ngày khởi hành đã chọn theo định dạng ngày/tháng/năm.
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
        departureTimeSelector.setOnClickListener(
                new View.OnClickListener() {

                    /**
                     * Mở hộp thoại chọn giờ khởi hành.
                     *
                     * @param view thành phần giao diện được nhấn
                     */
                    @Override
                    public void onClick(View view) {
                        showDepartureTimePicker();
                    }
                }
        );
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
                     * Cập nhật giờ khởi hành theo giờ người dùng chọn.
                     *
                     * @param timePicker hộp chọn giờ
                     * @param selectedHour giờ được chọn
                     * @param selectedMinute phút được chọn
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
     * Hiển thị giờ khởi hành theo định dạng 24 giờ.
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
     * Xử lý lựa chọn miễn phí và đồng bộ trường chi phí.
     */
    private void connectFreeContributionOption() {
        freeContributionCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {

                    /**
                     * Cập nhật chi phí khi trạng thái miễn phí thay đổi.
                     *
                     * @param button thành phần lựa chọn miễn phí
                     * @param isChecked cho biết người dùng đã chọn miễn phí hay chưa
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
                            contributionAmountInput.setEnabled(true);
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
     * Kết nối nút kiểm tra biểu mẫu.
     * <p>
     * Nút này chỉ kiểm tra dữ liệu, chưa tạo chuyến trên Firestore.
     */
    private void connectFormValidationButton() {
        androidx.appcompat.widget.AppCompatButton validationButton =
                findViewById(R.id.primary_action);

        validationButton.setOnClickListener(new View.OnClickListener() {

            /**
             * Kiểm tra dữ liệu khi người dùng nhấn nút.
             *
             * @param view thành phần giao diện được nhấn
             */
            @Override
            public void onClick(View view) {
                validateForm();
            }
        });
    }

    /**
     * Kiểm tra các trường cần thiết trước khi có thể đăng chuyến.
     */
    private void validateForm() {
        if (!hasSelectedDepartureDate) {
            showMessage("Vui lòng chọn ngày khởi hành.");
            return;
        }

        if (!hasSelectedDepartureTime) {
            showMessage("Vui lòng chọn giờ khởi hành.");
            return;
        }

        if (!isDepartureTimeInFuture()) {
            showMessage("Ngày và giờ khởi hành phải ở trong tương lai.");
            return;
        }

        String contributionAmountText =
                contributionAmountInput.getText().toString().trim();

        if (contributionAmountText.isEmpty()) {
            showMessage("Vui lòng nhập chi phí hoặc chọn miễn phí.");
            return;
        }

        long contributionAmount;

        try {
            contributionAmount = Long.parseLong(
                    contributionAmountText
            );
        } catch (NumberFormatException exception) {
            showMessage("Chi phí phải là một số nguyên hợp lệ.");
            return;
        }

        if (contributionAmount < 0) {
            showMessage("Chi phí không được là số âm.");
            return;
        }

        String originPlaceName =
                originPlaceValue.getText().toString().trim();

        if (originPlaceName.isEmpty()
                || originPlaceName.equals("Chọn điểm đi")) {
            showMessage(
                    "Vui lòng chọn điểm đi. Chức năng chọn địa điểm "
                            + "đang chờ tích hợp."
            );
            return;
        }

        String destinationPlaceName =
                destinationPlaceValue.getText().toString().trim();

        if (destinationPlaceName.isEmpty()
                || destinationPlaceName.equals("Chọn điểm đến")) {
            showMessage(
                    "Vui lòng chọn điểm đến. Chức năng chọn địa điểm "
                            + "đang chờ tích hợp."
            );
            return;
        }

        showMessage(
                "Thông tin đầu vào hợp lệ. Chức năng ghi chuyến "
                        + "lên Firestore chưa được tích hợp."
        );
    }

    /**
     * Xác định ngày và giờ khởi hành đã chọn có nằm trong tương lai hay không.
     *
     * @return true nếu thời điểm khởi hành lớn hơn thời điểm hiện tại
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

        selectedDepartureCalendar.set(
                Calendar.MILLISECOND,
                0
        );

        Calendar currentCalendar = Calendar.getInstance();

        return selectedDepartureCalendar.after(currentCalendar);
    }

    /**
     * Hiển thị thông báo ngắn cho người dùng.
     *
     * @param message nội dung thông báo cần hiển thị
     */
    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
