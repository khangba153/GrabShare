package com.example.grabshare.contract;

import androidx.annotation.Nullable;

import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;

/** Hợp đồng SQLite của B; các hàm đồng bộ cần gọi ngoài luồng UI. */
public interface TripCacheStore {
    /** Lưu chuyến đã đọc online theo userId và trip.getId(), tự ghi savedAt. */
    void saveViewedTrip(String userId, Trip trip, @Nullable PublicProfile profile);

    /** Lấy cache đúng tài khoản; trả null nếu chưa có trang đã lưu. */
    @Nullable
    CachedTrip getViewedTrip(String userId, String tripId);

    /** Xóa cache của tài khoản đăng xuất, không xóa dữ liệu tài khoản khác. */
    void clearUserCache(String userId);
}
