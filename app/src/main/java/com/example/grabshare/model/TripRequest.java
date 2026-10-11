package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Dữ liệu yêu cầu đi nhờ trong collection requests. */
public class TripRequest {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String tripId;
    private String passengerId;
    private String driverId;
    private Place pickup;
    private String message;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public TripRequest() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public TripRequest(String tripId, String passengerId, String driverId, Place pickup, String message, String status, Timestamp createdAt, Timestamp updatedAt) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.pickup = pickup;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    /** Lấy ID chuyến. */
    public String getTripId() {
        return tripId;
    }

    /** Gán ID chuyến. */
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    /** Lấy UID người đi nhờ. */
    public String getPassengerId() {
        return passengerId;
    }

    /** Gán UID người đi nhờ. */
    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    /** Lấy UID chủ xe. */
    public String getDriverId() {
        return driverId;
    }

    /** Gán UID chủ xe. */
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    /** Lấy điểm đón. */
    public Place getPickup() {
        return pickup;
    }

    /** Gán điểm đón. */
    public void setPickup(Place pickup) {
        this.pickup = pickup;
    }

    /** Lấy lời nhắn. */
    public String getMessage() {
        return message;
    }

    /** Gán lời nhắn. */
    public void setMessage(String message) {
        this.message = message;
    }

    /** Lấy trạng thái yêu cầu. */
    public String getStatus() {
        return status;
    }

    /** Gán trạng thái yêu cầu. */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Lấy thời gian tạo. */
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    /** Gán thời gian tạo. */
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    /** Lấy thời gian cập nhật. */
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    /** Gán thời gian cập nhật. */
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
