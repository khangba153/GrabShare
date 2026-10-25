package com.example.grabshare.activity.search;

import java.text.NumberFormat;
import java.util.Locale;

/** Đổi khoảng cách đường bộ từ mét sang nhãn km, không làm thay đổi dữ liệu gốc. */
public final class RouteDistanceFormatter {
    /** Không tạo đối tượng vì lớp chỉ định dạng giá trị. */
    private RouteDistanceFormatter() {}

    /** Định dạng km tiếng Việt, không hiển thị dữ liệu thiếu hoặc âm thành 0 km. */
    public static String formatKilometers(double distanceMeters) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            throw new IllegalArgumentException("Quãng đường phải là số mét hữu hạn không âm.");
        }
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        if (distanceMeters > 0 && distanceMeters < 10) {
            return "< 0,01 km";
        }
        return format.format(distanceMeters / 1000.0) + " km";
    }
}
