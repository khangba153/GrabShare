package com.example.grabshare.activity.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.grabshare.R;
import com.example.grabshare.contract.DetourLimit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

/** Kiểm tra form trên Android; không bấm đăng chuyến hoặc ghi dữ liệu máy chủ. */
@RunWith(AndroidJUnit4.class)
public class ManualDetourInputTest {

    /** Form mới điền 2 km, hai cách viết thập phân đều giữ nguyên khi nhập. */
    @Test
    public void newFormUsesDefaultAndAcceptsBothSeparators() {
        try (ActivityScenario<PostTripActivity> scenario =
                     ActivityScenario.launch(PostTripActivity.class)) {
            scenario.onActivity(new ActivityScenario.ActivityAction<PostTripActivity>() {
                /** Chỉ thay dữ liệu form trong RAM, không gọi submitTrip. */
                @Override
                public void perform(PostTripActivity activity) {
                    EditText distanceInput = activity.findViewById(R.id.max_detour_input);
                    assertEquals("2", distanceInput.getText().toString());
                    distanceInput.setText("2,5");
                    assertTrue(validateDistance(activity));
                    assertEquals(Long.valueOf(2500L),
                            DetourLimit.parseKilometers(distanceInput.getText().toString()));
                    distanceInput.setText("3.7");
                    assertTrue(validateDistance(activity));
                    assertNull(distanceInput.getError());
                }
            });
        }
    }

    /** Dữ liệu sai không bị bộ lọc bàn phím biến thành số hợp lệ; lỗi hiện tại ô nhập. */
    @Test
    public void invalidInputStaysVisibleAndShowsInlineError() {
        try (ActivityScenario<PostTripActivity> scenario =
                     ActivityScenario.launch(PostTripActivity.class)) {
            scenario.onActivity(new ActivityScenario.ActivityAction<PostTripActivity>() {
                /** Gọi riêng validation để không phụ thuộc các trường tuyến đường. */
                @Override
                public void perform(PostTripActivity activity) {
                    EditText distanceInput = activity.findViewById(R.id.max_detour_input);
                    String[] invalidInputs = {"", "-1", "abc", "2.55", "10.1"};
                    for (String invalidInput : invalidInputs) {
                        distanceInput.setText(invalidInput);
                        assertEquals(invalidInput, distanceInput.getText().toString());
                        assertFalse(validateDistance(activity));
                        assertNotNull(distanceInput.getError());
                    }
                    distanceInput.setText("0");
                    assertTrue(validateDistance(activity));
                    assertNull(distanceInput.getError());
                }
            });
        }
    }

    /** Android tạo lại Activity vẫn giữ nguyên cả bản nháp hợp lệ, rỗng và sai. */
    @Test
    public void recreationKeepsExactDraftInsteadOfResettingDefault() {
        String[] draftValues = {"2,5", "3.7", "", "-1"};
        try (ActivityScenario<PostTripActivity> scenario =
                     ActivityScenario.launch(PostTripActivity.class)) {
            for (String draftValue : draftValues) {
                final String expectedDraft = draftValue;
                scenario.onActivity(new ActivityScenario.ActivityAction<PostTripActivity>() {
                    /** Đặt bản nháp trước khi mô phỏng Activity bị tạo lại. */
                    @Override
                    public void perform(PostTripActivity activity) {
                        EditText distanceInput = activity.findViewById(R.id.max_detour_input);
                        distanceInput.setText(expectedDraft);
                    }
                });
                scenario.recreate();
                scenario.onActivity(new ActivityScenario.ActivityAction<PostTripActivity>() {
                    /** Đọc lại nội dung từ Activity mới để kiểm tra restore thật. */
                    @Override
                    public void perform(PostTripActivity activity) {
                        EditText distanceInput = activity.findViewById(R.id.max_detour_input);
                        assertEquals(expectedDraft, distanceInput.getText().toString());
                    }
                });
            }
        }
    }

    /** Kiểm tra đúng validation của form, không gọi luồng mạng hoặc tạo chuyến. */
    private boolean validateDistance(PostTripActivity activity) {
        try {
            Method validationMethod =
                    PostTripActivity.class.getDeclaredMethod("validateMaximumDetour");
            validationMethod.setAccessible(true);
            return (Boolean) validationMethod.invoke(activity);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError("Không gọi được validation giới hạn đi thêm", reflectionFailure);
        }
    }
}
