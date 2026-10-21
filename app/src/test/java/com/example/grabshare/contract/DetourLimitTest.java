package com.example.grabshare.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Kiểm tra quy đổi nhập tay; không đọc hoặc ghi Firebase. */
public class DetourLimitTest {

    /** Hai dấu thập phân đều quy đổi chính xác, không dùng số thực để làm tròn. */
    @Test
    public void acceptsBothDecimalSeparatorsAndBoundaryValues() {
        assertEquals(Long.valueOf(0L), DetourLimit.parseKilometers("0"));
        assertEquals(Long.valueOf(100L), DetourLimit.parseKilometers("0.1"));
        assertEquals(Long.valueOf(2500L), DetourLimit.parseKilometers("2.5"));
        assertEquals(Long.valueOf(2500L), DetourLimit.parseKilometers("2,5"));
        assertEquals(Long.valueOf(3700L), DetourLimit.parseKilometers(" 3,7 "));
        assertEquals(Long.valueOf(10000L), DetourLimit.parseKilometers("10.0"));
    }

    /** Dữ liệu sai phải bị từ chối, không biến thành mặc định hoặc giới hạn 0. */
    @Test
    public void rejectsMissingMalformedAndOutOfRangeValues() {
        String[] invalidInputs = {
                null, "", " ", "-1", "+2", "abc", "2.55", "2,50", "2,5.0",
                "10.1", "11", "2.", ".5", "2e0", "2 5", "99999999999999999"
        };
        for (String invalidInput : invalidInputs) {
            assertNull("Không được nhận: " + invalidInput,
                    DetourLimit.parseKilometers(invalidInput));
        }
    }

    /** Dữ liệu lưu phải là số mét đúng bước 100 và nằm trong giới hạn. */
    @Test
    public void validatesStoredMetersAndKeepsLegacyChoices() {
        long[] validDistances = {0L, 100L, 2000L, 2500L, 3700L, 5000L, 10000L};
        for (long validDistance : validDistances) {
            assertTrue(DetourLimit.isValidMeters(validDistance));
        }
        assertFalse(DetourLimit.isValidMeters(null));
        assertFalse(DetourLimit.isValidMeters(-100L));
        assertFalse(DetourLimit.isValidMeters(2501L));
        assertFalse(DetourLimit.isValidMeters(10100L));
    }

    /** Định dạng không tự bổ sung cấu hình cho chuyến cũ thiếu dữ liệu. */
    @Test
    public void formatsStoredValuesWithoutBackfillingLegacyTrips() {
        assertEquals("2", DetourLimit.formatKilometers(DetourLimit.DEFAULT_METERS));
        assertEquals("0", DetourLimit.formatKilometers(0L));
        assertEquals("2,5", DetourLimit.formatKilometers(2500L));
        assertEquals("10", DetourLimit.formatKilometers(10000L));
        assertEquals("", DetourLimit.formatKilometers(null));
        assertEquals("", DetourLimit.formatKilometers(2501L));
    }

    /** Mọi mức hợp lệ đều giữ nguyên sau vòng định dạng và đọc lại. */
    @Test
    public void roundTripsEverySupportedDistance() {
        for (long distanceMeters = 0L; distanceMeters <= 10000L;
                distanceMeters = distanceMeters + 100L) {
            assertEquals(Long.valueOf(distanceMeters),
                    DetourLimit.parseKilometers(DetourLimit.formatKilometers(distanceMeters)));
        }
    }
}
