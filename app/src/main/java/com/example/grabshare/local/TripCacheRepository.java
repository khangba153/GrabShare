package com.example.grabshare.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.grabshare.contract.CachedTrip;
import com.example.grabshare.contract.TripCacheStore;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.google.firebase.Timestamp;

import java.util.Date;

/**
 * Triển khai TripCacheStore cho phép lưu trữ và đọc lại các chuyến đi đã xem bằng SQLite cục bộ.
 */
public class TripCacheRepository implements TripCacheStore {

    private final TripCacheDbHelper dbHelper;

    /**
     * Khởi tạo TripCacheRepository dùng Application Context.
     * @param context Ngữ cảnh ứng dụng.
     */
    public TripCacheRepository(Context context) {
        this.dbHelper = new TripCacheDbHelper(context);
    }

    /**
     * Lưu thông tin chuyến đi và hồ sơ tài xế đã xem vào SQLite.
     * Thực hiện đồng bộ trên luồng gọi (ExecutorService ngoài UI thread).
     * @param userId ID người dùng hiện tại.
     * @param trip Thông tin chuyến đi.
     * @param profile Thông tin hồ sơ công khai của tài xế (có thể null).
     */
    @Override
    public void saveViewedTrip(String userId, Trip trip, PublicProfile profile) {
        if (userId == null || userId.trim().isEmpty() || trip == null || trip.getId() == null || trip.getId().trim().isEmpty()) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TripCacheDbHelper.COLUMN_USER_ID, userId);
        values.put(TripCacheDbHelper.COLUMN_TRIP_ID, trip.getId());

        if (trip.getOrigin() != null) {
            values.put(TripCacheDbHelper.COLUMN_ORIGIN_NAME, trip.getOrigin().getName());
            values.put(TripCacheDbHelper.COLUMN_ORIGIN_LAT, trip.getOrigin().getLatitude());
            values.put(TripCacheDbHelper.COLUMN_ORIGIN_LNG, trip.getOrigin().getLongitude());
        }

        if (trip.getDestination() != null) {
            values.put(TripCacheDbHelper.COLUMN_DEST_NAME, trip.getDestination().getName());
            values.put(TripCacheDbHelper.COLUMN_DEST_LAT, trip.getDestination().getLatitude());
            values.put(TripCacheDbHelper.COLUMN_DEST_LNG, trip.getDestination().getLongitude());
        }

        values.put(TripCacheDbHelper.COLUMN_DRIVER_ID, trip.getDriverId());
        values.put(TripCacheDbHelper.COLUMN_COST_VND, trip.getCostVnd());
        values.put(TripCacheDbHelper.COLUMN_AVAILABLE_SEATS, trip.getAvailableSeats());
        values.put(TripCacheDbHelper.COLUMN_NOTE, trip.getNote());
        values.put(TripCacheDbHelper.COLUMN_STATUS, trip.getStatus());

        if (trip.getDepartureAt() != null) {
            values.put(TripCacheDbHelper.COLUMN_DEPARTURE_AT, trip.getDepartureAt().toDate().getTime());
        }

        if (profile != null) {
            values.put(TripCacheDbHelper.COLUMN_DRIVER_NAME, profile.getDisplayName());
            values.put(TripCacheDbHelper.COLUMN_VEHICLE_NAME, profile.getVehicleName());
        } else {
            values.put(TripCacheDbHelper.COLUMN_DRIVER_NAME, "");
            values.put(TripCacheDbHelper.COLUMN_VEHICLE_NAME, "");
        }

        values.put(TripCacheDbHelper.COLUMN_SAVED_AT, System.currentTimeMillis());

        db.insertWithOnConflict(
                TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    /**
     * Lấy dữ liệu chuyến đi đã lưu từ bộ nhớ tạm SQLite.
     * Thực hiện đồng bộ trên luồng gọi (ExecutorService ngoài UI thread).
     * @param userId ID người dùng hiện tại.
     * @param tripId ID chuyến đi cần xem.
     * @return CachedTrip nếu tồn tại trong SQLite, hoặc null nếu chưa lưu.
     */
    @Override
    public CachedTrip getViewedTrip(String userId, String tripId) {
        if (userId == null || userId.trim().isEmpty() || tripId == null || tripId.trim().isEmpty()) {
            return null;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = TripCacheDbHelper.COLUMN_USER_ID + " = ? AND " + TripCacheDbHelper.COLUMN_TRIP_ID + " = ?";
        String[] selectionArgs = new String[]{userId, tripId};

        Cursor cursor = db.query(
                TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor == null) {
            return null;
        }

        CachedTrip cachedTrip = null;
        if (cursor.moveToFirst()) {
            Trip trip = new Trip();
            trip.setId(tripId);

            String originName = cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_ORIGIN_NAME));
            Double originLat = cursor.getDouble(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_ORIGIN_LAT));
            Double originLng = cursor.getDouble(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_ORIGIN_LNG));
            trip.setOrigin(new Place(originName, originLat, originLng));

            String destName = cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEST_NAME));
            Double destLat = cursor.getDouble(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEST_LAT));
            Double destLng = cursor.getDouble(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEST_LNG));
            trip.setDestination(new Place(destName, destLat, destLng));

            trip.setDriverId(cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DRIVER_ID)));
            trip.setCostVnd(cursor.getLong(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_COST_VND)));
            trip.setAvailableSeats(cursor.getLong(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_AVAILABLE_SEATS)));
            trip.setNote(cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_NOTE)));
            trip.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_STATUS)));

            long depTimeMillis = cursor.getLong(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEPARTURE_AT));
            if (depTimeMillis > 0) {
                trip.setDepartureAt(new Timestamp(new Date(depTimeMillis)));
            }

            String driverName = cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DRIVER_NAME));
            String vehicleName = cursor.getString(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_VEHICLE_NAME));

            PublicProfile profile = null;
            if ((driverName != null && !driverName.trim().isEmpty()) || (vehicleName != null && !vehicleName.trim().isEmpty())) {
                profile = new PublicProfile();
                profile.setId(trip.getDriverId());
                profile.setDisplayName(driverName);
                profile.setVehicleName(vehicleName);
            }

            long savedAt = cursor.getLong(cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_SAVED_AT));
            cachedTrip = new CachedTrip(trip, profile, savedAt);
        }

        cursor.close();
        return cachedTrip;
    }

    /**
     * Xóa sạch dữ liệu bộ nhớ tạm SQLite thuộc về người dùng khi đăng xuất.
     * @param userId ID người dùng cần xóa cache.
     */
    @Override
    public void clearUserCache(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String whereClause = TripCacheDbHelper.COLUMN_USER_ID + " = ?";
        String[] whereArgs = new String[]{userId};
        db.delete(TripCacheDbHelper.TABLE_VIEWED_TRIPS, whereClause, whereArgs);
    }
}