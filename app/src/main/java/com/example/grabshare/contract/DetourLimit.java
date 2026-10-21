package com.example.grabshare.contract;

import java.math.BigDecimal;

/** Quy tắc giới hạn đi thêm dùng chung cho form, repository và tìm chuyến. */
public final class DetourLimit {

    //region 1. Quy ước dữ liệu

    public static final long DEFAULT_METERS = 2000L;
    public static final long MAXIMUM_METERS = 10000L;
    public static final long STEP_METERS = 100L;

    /** Không tạo đối tượng cho lớp chỉ cung cấp quy tắc tĩnh. */
    private DetourLimit() {
    }

    //endregion

    //region 2. Kiểm tra và quy đổi

    /** Nhận số km có tối đa một chữ số lẻ; null là nhập sai, không phải giới hạn 0. */
    public static Long parseKilometers(String inputText) {
        if (inputText == null) {
            return null;
        }
        String normalizedText = inputText.trim();
        if (normalizedText.length() > 16
                || !normalizedText.matches("[0-9]+([.,][0-9])?")) {
            return null;
        }
        normalizedText = normalizedText.replace(',', '.');
        try {
            long distanceMeters = new BigDecimal(normalizedText)
                    .movePointRight(3)
                    .longValueExact();
            if (!isValidMeters(distanceMeters)) {
                return null;
            }
            return distanceMeters;
        } catch (NumberFormatException invalidNumber) {
            return null;
        } catch (ArithmeticException invalidPrecision) {
            return null;
        }
    }

    /** Firestore lưu integer từ 0 đến 10000 mét, chia hết cho 100 mét. */
    public static boolean isValidMeters(Long distanceMeters) {
        return distanceMeters != null
                && distanceMeters >= 0L
                && distanceMeters <= MAXIMUM_METERS
                && distanceMeters % STEP_METERS == 0L;
    }

    /** Hiện lại đúng mức đã lưu; chuyến cũ thiếu cấu hình giữ ô trống để chủ xe bổ sung. */
    public static String formatKilometers(Long distanceMeters) {
        if (!isValidMeters(distanceMeters)) {
            return "";
        }
        return BigDecimal.valueOf(distanceMeters)
                .movePointLeft(3)
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', ',');
    }

    //endregion
}
