package com.example.grabshare.presentation;

import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;

/** Tên ngắn chỉ dành cho danh sách; địa chỉ gốc vẫn giữ ở màn chi tiết. */
public final class TripCardText {
    private TripCardText() {
    }

    public static String place(Place place) {
        if (place == null || place.getName() == null || place.getName().trim().isEmpty()) {
            return "Chưa rõ địa điểm";
        }
        String name = place.getName().trim();
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        if (lowerName.contains("university of economics") && lowerName.contains("campus a")) {
            return "UEH cơ sở A";
        }
        if (lowerName.contains("ueh") && lowerName.contains("cơ sở")) {
            int separator = name.indexOf(" - ");
            if (separator > 0) {
                return name.substring(0, separator).trim();
            }
        }
        int comma = name.indexOf(',');
        if (comma > 0) {
            return name.substring(0, comma).trim();
        }
        return name;
    }

    /** Hết giờ chỉ đổi nhãn, tuyệt đối không tự đổi trạng thái Firestore. */
    public static boolean pastDeparture(Trip trip) {
        return trip != null && trip.getDepartureAt() != null
                && trip.getDepartureAt().toDate().getTime() <= System.currentTimeMillis();
    }
}
