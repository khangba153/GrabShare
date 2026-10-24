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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Date;

/**
 * Triển khai TripCacheStore cho phép lưu trữ và đọc lại các chuyến đi đã xem bằng SQLite cục bộ.
 */
public class TripCacheRepository implements TripCacheStore {

    /** Các instance cùng dùng một khóa để ghi cache không chen giữa xóa và signOut. */
    private static final Object CACHE_WRITE_LOCK = new Object();

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
        synchronized (CACHE_WRITE_LOCK) {
            saveViewedTripWhileLocked(userId, trip, profile);
        }
    }

    /** Kiểm tra Auth bên trong khóa dùng chung, ngay trước thao tác SQLite. */
    private void saveViewedTripWhileLocked(
            String userId, Trip trip, PublicProfile profile) {
        if (userId == null || userId.trim().isEmpty() || trip == null || trip.getId() == null || trip.getId().trim().isEmpty()) {
            return;
        }

        // Chặn race condition nếu tài khoản đã đăng xuất hoặc thay đổi trước khi callback hoàn tất
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || !userId.equals(currentUser.getUid())) {
            return;
        }

        try {
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

            long insertedRowIdentifier = db.insertWithOnConflict(
                    TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
            if (insertedRowIdentifier == -1L) {
                throw new IllegalStateException("SQLite không lưu được chuyến đã xem.");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Không lưu được bản xem offline.", error);
        }
    }

    /**
     * Lấy dữ liệu chuyến đi đã lưu từ bộ nhớ tạm SQLite.
     * Sử dụng try-with-resources để đảm bảo Cursor luôn được đóng an toàn.
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

        try (Cursor cursor = db.query(
                TripCacheDbHelper.TABLE_VIEWED_TRIPS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                Trip trip = new Trip();
                trip.setId(tripId);

                int colOrigName = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_ORIGIN_NAME);
                int colOrigLat = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_ORIGIN_LAT);
                int colOrigLng = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_ORIGIN_LNG);

                String originName = cursor.isNull(colOrigName) ? "" : cursor.getString(colOrigName);
                Double originLat = cursor.isNull(colOrigLat) ? null : cursor.getDouble(colOrigLat);
                Double originLng = cursor.isNull(colOrigLng) ? null : cursor.getDouble(colOrigLng);
                trip.setOrigin(new Place(originName, originLat, originLng));

                int colDestName = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEST_NAME);
                int colDestLat = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEST_LAT);
                int colDestLng = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEST_LNG);

                String destName = cursor.isNull(colDestName) ? "" : cursor.getString(colDestName);
                Double destLat = cursor.isNull(colDestLat) ? null : cursor.getDouble(colDestLat);
                Double destLng = cursor.isNull(colDestLng) ? null : cursor.getDouble(colDestLng);
                trip.setDestination(new Place(destName, destLat, destLng));

                int colDriverId = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DRIVER_ID);
                int colCost = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_COST_VND);
                int colSeats = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_AVAILABLE_SEATS);
                int colNote = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_NOTE);
                int colStatus = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_STATUS);
                int colDepAt = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DEPARTURE_AT);

                trip.setDriverId(cursor.isNull(colDriverId) ? "" : cursor.getString(colDriverId));
                trip.setCostVnd(cursor.isNull(colCost) ? null : cursor.getLong(colCost));
                trip.setAvailableSeats(cursor.isNull(colSeats) ? null : cursor.getLong(colSeats));
                trip.setNote(cursor.isNull(colNote) ? "" : cursor.getString(colNote));
                trip.setStatus(cursor.isNull(colStatus) ? "" : cursor.getString(colStatus));

                if (!cursor.isNull(colDepAt)) {
                    long depTimeMillis = cursor.getLong(colDepAt);
                    if (depTimeMillis > 0) {
                        trip.setDepartureAt(new Timestamp(new Date(depTimeMillis)));
                    }
                }

                int colDriverName = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_DRIVER_NAME);
                int colVehicleName = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_VEHICLE_NAME);
                String driverName = cursor.isNull(colDriverName) ? "" : cursor.getString(colDriverName);
                String vehicleName = cursor.isNull(colVehicleName) ? "" : cursor.getString(colVehicleName);

                PublicProfile profile = null;
                if ((driverName != null && !driverName.trim().isEmpty()) || (vehicleName != null && !vehicleName.trim().isEmpty())) {
                    profile = new PublicProfile();
                    profile.setId(trip.getDriverId());
                    profile.setDisplayName(driverName);
                    profile.setVehicleName(vehicleName);
                }

                int colSavedAt = cursor.getColumnIndexOrThrow(TripCacheDbHelper.COLUMN_SAVED_AT);
                long savedAt = cursor.isNull(colSavedAt) ? 0L : cursor.getLong(colSavedAt);

                return new CachedTrip(trip, profile, savedAt);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Không đọc được chuyến đã lưu trong SQLite.", error);
        }

        return null;
    }

    /**
     * Xóa sạch dữ liệu bộ nhớ tạm SQLite thuộc về người dùng khi đăng xuất.
     * @param userId ID người dùng cần xóa cache.
     */
    @Override
    public void clearUserCache(String userId) {
        synchronized (CACHE_WRITE_LOCK) {
            clearUserCacheWhileLocked(userId);
        }
    }

    /**
     * Xóa cache và kết thúc Auth trong cùng khóa với mọi writer của repository.
     * Gọi ngoài UI thread; afterClear chỉ kết thúc phiên cục bộ, không chờ mạng.
     * Nếu xóa lỗi thì không gọi afterClear, để màn đăng xuất cho phép thử lại.
     */
    public void clearUserCacheAndFinishSession(String userId, Runnable afterClear) {
        if (userId == null || userId.trim().isEmpty() || afterClear == null) {
            throw new IllegalArgumentException("Thiếu tài khoản hoặc tác vụ kết thúc phiên.");
        }

        synchronized (CACHE_WRITE_LOCK) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && !userId.equals(currentUser.getUid())) {
                throw new IllegalStateException("Tài khoản đã thay đổi. Vui lòng thực hiện lại.");
            }
            clearUserCacheWhileLocked(userId);
            afterClear.run();
        }
    }

    /** Chỉ xóa đúng UID; lỗi phải được trả cho luồng đăng xuất thay vì bị bỏ qua. */
    private void clearUserCacheWhileLocked(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String whereClause = TripCacheDbHelper.COLUMN_USER_ID + " = ?";
            String[] whereArgs = new String[]{userId};
            db.delete(TripCacheDbHelper.TABLE_VIEWED_TRIPS, whereClause, whereArgs);
        } catch (Exception error) {
            throw new IllegalStateException("Không xóa được dữ liệu tạm của tài khoản.", error);
        }
    }
}
