package com.example.grabshare.api;

import org.json.JSONArray;
import org.json.JSONObject;

/** Kiểm tra quốc gia theo dữ liệu hành chính đầy đủ của Routing API. */
public final class VietnamRouteValidator {

    /** Không khởi tạo lớp chỉ chứa quy tắc kiểm tra. */
    private VietnamRouteValidator() {}

    /** Thiếu dữ liệu hoặc có bất kỳ quốc gia khác VN đều không được xác nhận. */
    public static boolean isVietnamOnly(JSONObject properties) {
        if (properties == null || !onlyVietnam(properties.optJSONArray("country_code"))) {
            return false;
        }
        JSONArray administrativeAreas = properties.optJSONArray("admin_areas");
        if (administrativeAreas == null || administrativeAreas.length() == 0) {
            return false;
        }
        for (int entryIndex = 0;
                entryIndex < administrativeAreas.length();
                entryIndex = entryIndex + 1) {
            JSONObject area = administrativeAreas.optJSONObject(entryIndex);
            if (area == null || !"vn".equalsIgnoreCase(area.optString("country_code"))) {
                return false;
            }
        }
        JSONArray legs = properties.optJSONArray("legs");
        if (legs == null || legs.length() == 0) {
            return false;
        }
        for (int entryIndex = 0; entryIndex < legs.length(); entryIndex = entryIndex + 1) {
            JSONObject routeLeg = legs.optJSONObject(entryIndex);
            if (routeLeg == null || !onlyVietnam(routeLeg.optJSONArray("country_code"))) {
                return false;
            }
        }
        // admin_area_crossings rỗng ở tuyến nhiều chặng không chứng minh tuyến nội địa.
        return true;
    }

    /** Kiểm tra tất cả phần tử, không chỉ quốc gia đầu tiên hoặc hai đầu tuyến. */
    private static boolean onlyVietnam(JSONArray countries) {
        if (countries == null || countries.length() == 0) {
            return false;
        }
        for (int entryIndex = 0; entryIndex < countries.length(); entryIndex = entryIndex + 1) {
            if (!"vn".equalsIgnoreCase(countries.optString(entryIndex))) {
                return false;
            }
        }
        return true;
    }
}
