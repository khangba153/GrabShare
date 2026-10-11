package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Dữ liệu chuyến yêu thích trong collection favorites. */
public class Favorite {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String userId;
    private String tripId;
    private Timestamp createdAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public Favorite() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public Favorite(String userId, String tripId, Timestamp createdAt) {
        this.userId = userId;
        this.tripId = tripId;
        this.createdAt = createdAt;
    }

    /** Lấy ID document, không đưa ID vào dữ liệu ghi Firestore. */
    @Exclude
    public String getId() {
        return id;
    }

    /** Gán ID lấy từ DocumentSnapshot sau khi đọc dữ liệu. */
    public void setId(String id) {
        this.id = id;
    }

    /** Lấy UID người sở hữu. */
    public String getUserId() {
        return userId;
    }

    /** Gán UID người sở hữu. */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** Lấy ID chuyến. */
    public String getTripId() {
        return tripId;
    }

    /** Gán ID chuyến. */
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    /** Lấy thời gian tạo. */
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    /** Gán thời gian tạo. */
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
