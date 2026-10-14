package com.example.grabshare.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Quản lý schema SQLite cho các chuyến người dùng đã xem. */
public final class TripCacheDbHelper extends SQLiteOpenHelper {
    static final String DATABASE_NAME = "grabshare_viewed_trips.db";
    static final int DATABASE_VERSION = 1;

    static final String TABLE_VIEWED_TRIPS = "viewed_trips";
    static final String COL_USER_ID = "user_id";
    static final String COL_TRIP_ID = "trip_id";
    static final String COL_SAVED_AT = "saved_at";
    static final String COL_DRIVER_ID = "driver_id";

    static final String COL_ORIGIN_PRESENT = "origin_present";
    static final String COL_ORIGIN_NAME = "origin_name";
    static final String COL_ORIGIN_LATITUDE = "origin_latitude";
    static final String COL_ORIGIN_LONGITUDE = "origin_longitude";

    static final String COL_DESTINATION_PRESENT = "destination_present";
    static final String COL_DESTINATION_NAME = "destination_name";
    static final String COL_DESTINATION_LATITUDE = "destination_latitude";
    static final String COL_DESTINATION_LONGITUDE = "destination_longitude";

    static final String COL_DEPARTURE_SECONDS = "departure_seconds";
    static final String COL_DEPARTURE_NANOS = "departure_nanos";
    static final String COL_COST_VND = "cost_vnd";
    static final String COL_NOTE = "note";
    static final String COL_STATUS = "status";
    static final String COL_AVAILABLE_SEATS = "available_seats";
    static final String COL_ACCEPTED_REQUEST_ID = "accepted_request_id";
    static final String COL_CREATED_SECONDS = "created_seconds";
    static final String COL_CREATED_NANOS = "created_nanos";
    static final String COL_UPDATED_SECONDS = "updated_seconds";
    static final String COL_UPDATED_NANOS = "updated_nanos";

    static final String COL_PROFILE_PRESENT = "profile_present";
    static final String COL_PROFILE_DISPLAY_NAME = "profile_display_name";
    static final String COL_PROFILE_VEHICLE_NAME = "profile_vehicle_name";
    static final String COL_PROFILE_CREATED_SECONDS = "profile_created_seconds";
    static final String COL_PROFILE_CREATED_NANOS = "profile_created_nanos";
    static final String COL_PROFILE_UPDATED_SECONDS = "profile_updated_seconds";
    static final String COL_PROFILE_UPDATED_NANOS = "profile_updated_nanos";

    /** Khởi tạo helper SQLite; repository truyền vào applicationContext. */
    public TripCacheDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /** Tạo bảng cache với khóa ghép theo tài khoản và chuyến đi. */
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_VIEWED_TRIPS + " ("
                + COL_USER_ID + " TEXT NOT NULL, "
                + COL_TRIP_ID + " TEXT NOT NULL, "
                + COL_SAVED_AT + " INTEGER NOT NULL, "
                + COL_DRIVER_ID + " TEXT NOT NULL DEFAULT '', "
                + COL_ORIGIN_PRESENT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_ORIGIN_NAME + " TEXT, "
                + COL_ORIGIN_LATITUDE + " REAL, "
                + COL_ORIGIN_LONGITUDE + " REAL, "
                + COL_DESTINATION_PRESENT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DESTINATION_NAME + " TEXT, "
                + COL_DESTINATION_LATITUDE + " REAL, "
                + COL_DESTINATION_LONGITUDE + " REAL, "
                + COL_DEPARTURE_SECONDS + " INTEGER, "
                + COL_DEPARTURE_NANOS + " INTEGER, "
                + COL_COST_VND + " INTEGER, "
                + COL_NOTE + " TEXT, "
                + COL_STATUS + " TEXT, "
                + COL_AVAILABLE_SEATS + " INTEGER, "
                + COL_ACCEPTED_REQUEST_ID + " TEXT, "
                + COL_CREATED_SECONDS + " INTEGER, "
                + COL_CREATED_NANOS + " INTEGER, "
                + COL_UPDATED_SECONDS + " INTEGER, "
                + COL_UPDATED_NANOS + " INTEGER, "
                + COL_PROFILE_PRESENT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_PROFILE_DISPLAY_NAME + " TEXT, "
                + COL_PROFILE_VEHICLE_NAME + " TEXT, "
                + COL_PROFILE_CREATED_SECONDS + " INTEGER, "
                + COL_PROFILE_CREATED_NANOS + " INTEGER, "
                + COL_PROFILE_UPDATED_SECONDS + " INTEGER, "
                + COL_PROFILE_UPDATED_NANOS + " INTEGER, "
                + "PRIMARY KEY (" + COL_USER_ID + ", " + COL_TRIP_ID + ")"
                + ")";
        db.execSQL(createTable);
        db.execSQL("CREATE INDEX idx_viewed_trips_user_saved_at ON "
                + TABLE_VIEWED_TRIPS + " (" + COL_USER_ID + ", " + COL_SAVED_AT + " DESC)");
    }

    /** Giữ cache hiện có khi chưa có schema cũ cần chuyển đổi; không xóa bảng tùy tiện. */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema phiên bản đầu chưa có migration; khi đổi schema phải bổ sung migration tường minh.
    }
}
