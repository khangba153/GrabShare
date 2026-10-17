package com.example.grabshare.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Helper quản lý cơ sở dữ liệu SQLite lưu trữ bộ nhớ tạm (cache) các chuyến đi đã xem.
 */
public class TripCacheDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "grabshare_cache.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_VIEWED_TRIPS = "viewed_trips";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_TRIP_ID = "trip_id";
    public static final String COLUMN_ORIGIN_NAME = "origin_name";
    public static final String COLUMN_ORIGIN_LAT = "origin_lat";
    public static final String COLUMN_ORIGIN_LNG = "origin_lng";
    public static final String COLUMN_DEST_NAME = "dest_name";
    public static final String COLUMN_DEST_LAT = "dest_lat";
    public static final String COLUMN_DEST_LNG = "dest_lng";
    public static final String COLUMN_DRIVER_ID = "driver_id";
    public static final String COLUMN_COST_VND = "cost_vnd";
    public static final String COLUMN_AVAILABLE_SEATS = "available_seats";
    public static final String COLUMN_NOTE = "note";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_DEPARTURE_AT = "departure_at";
    public static final String COLUMN_DRIVER_NAME = "driver_name";
    public static final String COLUMN_VEHICLE_NAME = "vehicle_name";
    public static final String COLUMN_SAVED_AT = "saved_at";

    /**
     * Khởi tạo helper SQLite cho cache ứng dụng.
     * @param context Ngữ cảnh ứng dụng.
     */
    public TripCacheDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Tạo bảng lưu cache chuyến đi đã xem nếu chưa tồn tại.
     * @param db Cơ sở dữ liệu SQLite.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_VIEWED_TRIPS + " ("
                + COLUMN_USER_ID + " TEXT NOT NULL, "
                + COLUMN_TRIP_ID + " TEXT NOT NULL, "
                + COLUMN_ORIGIN_NAME + " TEXT, "
                + COLUMN_ORIGIN_LAT + " REAL, "
                + COLUMN_ORIGIN_LNG + " REAL, "
                + COLUMN_DEST_NAME + " TEXT, "
                + COLUMN_DEST_LAT + " REAL, "
                + COLUMN_DEST_LNG + " REAL, "
                + COLUMN_DRIVER_ID + " TEXT, "
                + COLUMN_COST_VND + " INTEGER, "
                + COLUMN_AVAILABLE_SEATS + " INTEGER, "
                + COLUMN_NOTE + " TEXT, "
                + COLUMN_STATUS + " TEXT, "
                + COLUMN_DEPARTURE_AT + " INTEGER, "
                + COLUMN_DRIVER_NAME + " TEXT, "
                + COLUMN_VEHICLE_NAME + " TEXT, "
                + COLUMN_SAVED_AT + " INTEGER, "
                + "PRIMARY KEY (" + COLUMN_USER_ID + ", " + COLUMN_TRIP_ID + ")"
                + ");";
        db.execSQL(createTableQuery);
    }

    /**
     * Nâng cấp phiên bản cơ sở dữ liệu khi có thay đổi cấu trúc bảng.
     * @param db Cơ sở dữ liệu SQLite.
     * @param oldVersion Phiên bản cũ.
     * @param newVersion Phiên bản mới.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VIEWED_TRIPS);
        onCreate(db);
    }
}