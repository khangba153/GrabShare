package com.example.grabshare.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.Nullable;

import com.example.grabshare.contract.CachedTrip;
import com.example.grabshare.contract.TripCacheStore;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Cài đặt TripCacheStore bằng SQLite cho trang chuyến đi đã xem. */
public final class TripCacheRepository implements TripCacheStore {
    private final TripCacheDbHelper dbHelper;

    /** Dùng applicationContext để helper không giữ tham chiếu Activity. */
    public TripCacheRepository(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context không được null.");
        }
        dbHelper = new TripCacheDbHelper(context.getApplicationContext());
    }

    /** Lưu snapshot chuyến và hồ sơ công khai theo khóa userId + tripId. */
    @Override
    public void saveViewedTrip(String userId, Trip trip, @Nullable PublicProfile profile) {
        validateId(userId, "userId");
        if (trip == null) {
            throw new IllegalArgumentException("Trip không được null.");
        }
        validateId(trip.getId(), "tripId");
        requireCurrentUser(userId);

        ContentValues values = new ContentValues();
        values.put(TripCacheDbHelper.COL_USER_ID, userId);
        values.put(TripCacheDbHelper.COL_TRIP_ID, trip.getId());
        values.put(TripCacheDbHelper.COL_SAVED_AT, System.currentTimeMillis());
        values.put(TripCacheDbHelper.COL_DRIVER_ID, textOrEmpty(trip.getDriverId()));

        putPlace(values, true, trip.getOrigin());
        putPlace(values, false, trip.getDestination());
        putTimestamp(values, TripCacheDbHelper.COL_DEPARTURE_SECONDS,
                TripCacheDbHelper.COL_DEPARTURE_NANOS, trip.getDepartureAt());
        putNullableLong(values, TripCacheDbHelper.COL_COST_VND, trip.getCostVnd());
        values.put(TripCacheDbHelper.COL_NOTE, textOrEmpty(trip.getNote()));
        values.put(TripCacheDbHelper.COL_STATUS, textOrEmpty(trip.getStatus()));
        putNullableLong(values, TripCacheDbHelper.COL_AVAILABLE_SEATS, trip.getAvailableSeats());
        values.put(TripCacheDbHelper.COL_ACCEPTED_REQUEST_ID,
                textOrEmpty(trip.getAcceptedRequestId()));
        putTimestamp(values, TripCacheDbHelper.COL_CREATED_SECONDS,
                TripCacheDbHelper.COL_CREATED_NANOS, trip.getCreatedAt());
        putTimestamp(values, TripCacheDbHelper.COL_UPDATED_SECONDS,
                TripCacheDbHelper.COL_UPDATED_NANOS, trip.getUpdatedAt());

        values.put(TripCacheDbHelper.COL_PROFILE_PRESENT, profile == null ? 0 : 1);
        if (profile != null) {
            values.put(TripCacheDbHelper.COL_PROFILE_DISPLAY_NAME,
                    textOrEmpty(profile.getDisplayName()));
            values.put(TripCacheDbHelper.COL_PROFILE_VEHICLE_NAME,
                    textOrEmpty(profile.getVehicleName()));
            putTimestamp(values, TripCacheDbHelper.COL_PROFILE_CREATED_SECONDS,
                    TripCacheDbHelper.COL_PROFILE_CREATED_NANOS, profile.getCreatedAt());
            putTimestamp(values, TripCacheDbHelper.COL_PROFILE_UPDATED_SECONDS,
                    TripCacheDbHelper.COL_PROFILE_UPDATED_NANOS, profile.getUpdatedAt());
        } else {
            values.putNull(TripCacheDbHelper.COL_PROFILE_DISPLAY_NAME);
            values.putNull(TripCacheDbHelper.COL_PROFILE_VEHICLE_NAME);
            putTimestamp(values, TripCacheDbHelper.COL_PROFILE_CREATED_SECONDS,
                    TripCacheDbHelper.COL_PROFILE_CREATED_NANOS, null);
            putTimestamp(values, TripCacheDbHelper.COL_PROFILE_UPDATED_SECONDS,
                    TripCacheDbHelper.COL_PROFILE_UPDATED_NANOS, null);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            long rowId = db.insertWithOnConflict(
                    TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            if (rowId == -1L) {
                throw new SQLiteException("Không thể lưu cache chuyến đã xem.");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Đọc cache của đúng tài khoản; null chỉ khi không có bản ghi tương ứng. */
    @Override
    @Nullable
    public CachedTrip getViewedTrip(String userId, String tripId) {
        validateId(userId, "userId");
        validateId(tripId, "tripId");
        requireCurrentUser(userId);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = TripCacheDbHelper.COL_USER_ID + " = ? AND "
                + TripCacheDbHelper.COL_TRIP_ID + " = ?";
        String[] selectionArgs = new String[]{userId, tripId};
        try (Cursor cursor = db.query(
                TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null,
                "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readCachedTrip(cursor);
        }
    }

    /** Xóa riêng dữ liệu cache của user đăng xuất; gọi sau khi tác vụ save cũ đã dừng. */
    @Override
    public void clearUserCache(String userId) {
        validateId(userId, "userId");
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
                TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                TripCacheDbHelper.COL_USER_ID + " = ?",
                new String[]{userId});
    }

    /** Kiểm tra UID truyền vào có đúng phiên Firebase hiện tại hay không. */
    private void requireCurrentUser(String userId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || !userId.equals(currentUser.getUid())) {
            throw new SecurityException("Cache chỉ được truy cập bởi tài khoản đang đăng nhập.");
        }
    }

    /** Từ chối ID null/rỗng để không tạo cache không thể truy xuất. */
    private void validateId(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " không được rỗng.");
        }
    }

    /** Lưu điểm đi hoặc điểm đến cùng cờ phân biệt Place null với Place rỗng. */
    private void putPlace(ContentValues values, boolean isOrigin, @Nullable Place place) {
        String presentKey;
        String nameKey;
        String latitudeKey;
        String longitudeKey;
        if (isOrigin) {
            presentKey = TripCacheDbHelper.COL_ORIGIN_PRESENT;
            nameKey = TripCacheDbHelper.COL_ORIGIN_NAME;
            latitudeKey = TripCacheDbHelper.COL_ORIGIN_LATITUDE;
            longitudeKey = TripCacheDbHelper.COL_ORIGIN_LONGITUDE;
        } else {
            presentKey = TripCacheDbHelper.COL_DESTINATION_PRESENT;
            nameKey = TripCacheDbHelper.COL_DESTINATION_NAME;
            latitudeKey = TripCacheDbHelper.COL_DESTINATION_LATITUDE;
            longitudeKey = TripCacheDbHelper.COL_DESTINATION_LONGITUDE;
        }
        values.put(presentKey, place == null ? 0 : 1);
        if (place == null) {
            values.putNull(nameKey);
            values.putNull(latitudeKey);
            values.putNull(longitudeKey);
            return;
        }
        values.put(nameKey, textOrEmpty(place.getName()));
        putNullableDouble(values, latitudeKey, place.getLatitude());
        putNullableDouble(values, longitudeKey, place.getLongitude());
    }

    /** Phục hồi bản ghi SQLite thành CachedTrip và khôi phục ID document từ khóa. */
    private CachedTrip readCachedTrip(Cursor cursor) {
        String tripId = readText(cursor, TripCacheDbHelper.COL_TRIP_ID);
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setDriverId(readText(cursor, TripCacheDbHelper.COL_DRIVER_ID));
        trip.setOrigin(readPlace(cursor, true));
        trip.setDestination(readPlace(cursor, false));
        trip.setDepartureAt(readTimestamp(cursor,
                TripCacheDbHelper.COL_DEPARTURE_SECONDS,
                TripCacheDbHelper.COL_DEPARTURE_NANOS));
        trip.setCostVnd(readNullableLong(cursor, TripCacheDbHelper.COL_COST_VND));
        trip.setNote(readText(cursor, TripCacheDbHelper.COL_NOTE));
        trip.setStatus(readText(cursor, TripCacheDbHelper.COL_STATUS));
        trip.setAvailableSeats(readNullableLong(cursor, TripCacheDbHelper.COL_AVAILABLE_SEATS));
        trip.setAcceptedRequestId(readText(cursor, TripCacheDbHelper.COL_ACCEPTED_REQUEST_ID));
        trip.setCreatedAt(readTimestamp(cursor,
                TripCacheDbHelper.COL_CREATED_SECONDS,
                TripCacheDbHelper.COL_CREATED_NANOS));
        trip.setUpdatedAt(readTimestamp(cursor,
                TripCacheDbHelper.COL_UPDATED_SECONDS,
                TripCacheDbHelper.COL_UPDATED_NANOS));

        PublicProfile profile = null;
        if (cursor.getInt(cursor.getColumnIndexOrThrow(
                TripCacheDbHelper.COL_PROFILE_PRESENT)) == 1) {
            profile = new PublicProfile();
            profile.setId(trip.getDriverId());
            profile.setDisplayName(readText(cursor, TripCacheDbHelper.COL_PROFILE_DISPLAY_NAME));
            profile.setVehicleName(readText(cursor, TripCacheDbHelper.COL_PROFILE_VEHICLE_NAME));
            profile.setCreatedAt(readTimestamp(cursor,
                    TripCacheDbHelper.COL_PROFILE_CREATED_SECONDS,
                    TripCacheDbHelper.COL_PROFILE_CREATED_NANOS));
            profile.setUpdatedAt(readTimestamp(cursor,
                    TripCacheDbHelper.COL_PROFILE_UPDATED_SECONDS,
                    TripCacheDbHelper.COL_PROFILE_UPDATED_NANOS));
        }

        long savedAt = cursor.getLong(cursor.getColumnIndexOrThrow(
                TripCacheDbHelper.COL_SAVED_AT));
        return new CachedTrip(trip, profile, savedAt);
    }

    /** Đọc điểm đi/đến; tọa độ chưa có vẫn giữ null thay vì bịa giá trị 0. */
    @Nullable
    private Place readPlace(Cursor cursor, boolean isOrigin) {
        String presentKey = isOrigin
                ? TripCacheDbHelper.COL_ORIGIN_PRESENT
                : TripCacheDbHelper.COL_DESTINATION_PRESENT;
        if (cursor.getInt(cursor.getColumnIndexOrThrow(presentKey)) != 1) {
            return null;
        }
        String nameKey = isOrigin
                ? TripCacheDbHelper.COL_ORIGIN_NAME
                : TripCacheDbHelper.COL_DESTINATION_NAME;
        String latitudeKey = isOrigin
                ? TripCacheDbHelper.COL_ORIGIN_LATITUDE
                : TripCacheDbHelper.COL_DESTINATION_LATITUDE;
        String longitudeKey = isOrigin
                ? TripCacheDbHelper.COL_ORIGIN_LONGITUDE
                : TripCacheDbHelper.COL_DESTINATION_LONGITUDE;
        return new Place(
                readText(cursor, nameKey),
                readNullableDouble(cursor, latitudeKey),
                readNullableDouble(cursor, longitudeKey));
    }

    /** Ghi Timestamp thành giây và nano để không làm mất độ chính xác khi lưu cache. */
    private void putTimestamp(ContentValues values, String secondsKey, String nanosKey,
                              @Nullable Timestamp timestamp) {
        if (timestamp == null) {
            values.putNull(secondsKey);
            values.putNull(nanosKey);
            return;
        }
        values.put(secondsKey, timestamp.getSeconds());
        values.put(nanosKey, timestamp.getNanoseconds());
    }

    /** Đọc Timestamp; cặp cột thiếu được khôi phục thành null. */
    @Nullable
    private Timestamp readTimestamp(Cursor cursor, String secondsKey, String nanosKey) {
        int secondsIndex = cursor.getColumnIndexOrThrow(secondsKey);
        int nanosIndex = cursor.getColumnIndexOrThrow(nanosKey);
        if (cursor.isNull(secondsIndex) || cursor.isNull(nanosIndex)) {
            return null;
        }
        return new Timestamp(cursor.getLong(secondsIndex), cursor.getInt(nanosIndex));
    }

    /** Ghi Long nullable mà không biến dữ liệu thiếu thành số không. */
    private void putNullableLong(ContentValues values, String key, @Nullable Long value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    /** Ghi tọa độ nullable và bỏ giá trị không hữu hạn khỏi snapshot cục bộ. */
    private void putNullableDouble(ContentValues values, String key, @Nullable Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    /** Đọc Long nullable đúng theo trạng thái NULL của SQLite. */
    @Nullable
    private Long readNullableLong(Cursor cursor, String key) {
        int index = cursor.getColumnIndexOrThrow(key);
        if (cursor.isNull(index)) {
            return null;
        }
        return cursor.getLong(index);
    }

    /** Đọc tọa độ nullable, không mặc định (0,0) khi thiếu. */
    @Nullable
    private Double readNullableDouble(Cursor cursor, String key) {
        int index = cursor.getColumnIndexOrThrow(key);
        if (cursor.isNull(index)) {
            return null;
        }
        return cursor.getDouble(index);
    }

    /** Đọc chuỗi optional theo data contract, dùng chuỗi rỗng thay cho null. */
    private String readText(Cursor cursor, String key) {
        int index = cursor.getColumnIndexOrThrow(key);
        if (cursor.isNull(index)) {
            return "";
        }
        return cursor.getString(index);
    }

    /** Chuẩn hóa chuỗi optional để model cache tuân theo quy ước chuỗi rỗng. */
    private String textOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
