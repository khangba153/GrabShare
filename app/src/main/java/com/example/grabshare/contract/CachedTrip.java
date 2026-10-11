package com.example.grabshare.contract;

import androidx.annotation.Nullable;

import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;

/** Kết quả SQLite local, không phải model hoặc collection Firestore mới. */
public final class CachedTrip {
    private final Trip trip;
    private final PublicProfile profile;
    private final long savedAt;

    /** Tạo snapshot đã lưu với savedAt là mili giây Unix của thiết bị. */
    public CachedTrip(Trip trip, @Nullable PublicProfile profile, long savedAt) {
        this.trip = trip;
        this.profile = profile;
        this.savedAt = savedAt;
    }

    /** Lấy chuyến đã lưu có ID được phục hồi từ khóa SQLite. */
    public Trip getTrip() {
        return trip;
    }

    /** Lấy hồ sơ công khai phục vụ tên và xe; có thể null nếu chưa tải được. */
    @Nullable
    public PublicProfile getProfile() {
        return profile;
    }

    /** Lấy thời điểm lưu để màn hình ghi rõ độ mới của dữ liệu offline. */
    public long getSavedAt() {
        return savedAt;
    }
}
