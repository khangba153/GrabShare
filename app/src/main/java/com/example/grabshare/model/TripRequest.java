package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dữ liệu yêu cầu đi nhờ trong collection requests. */
public class TripRequest {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String tripId;
    private String passengerId;
    private String driverId;
    private Long attemptNumber;
    private boolean attemptNumberFieldPresent;
    private Place pickup;
    private Place dropoff;
    // Phân biệt yêu cầu cũ thiếu field với dữ liệu có dropoff nhưng sai/null.
    private boolean dropoffFieldPresent;
    // Map gốc giữ được khác biệt giữa thiếu snapshot, null và dữ liệu sai kiểu.
    private Map<String, Object> contributionSnapshot;
    private boolean contributionSnapshotFieldPresent;
    private String message;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public TripRequest() {
    }

    /** Tương thích dữ liệu cũ chưa có điểm xuống; không dùng để tạo yêu cầu mới. */
    @Deprecated
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

    /** Tạo dữ liệu bốn điểm; repository vẫn phải kiểm tra P/C trước khi ghi. */
    public TripRequest(
            String tripId,
            String passengerId,
            String driverId,
            Place pickup,
            Place dropoff,
            String message,
            String status,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.dropoffFieldPresent = true;
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

    /** Null là yêu cầu legacy; chỉ repository mới đã tích hợp được ghi số lần dương. */
    public Long getAttemptNumber() {
        return attemptNumber;
    }

    /** Ánh xạ số lần đã lưu, không tự tăng hoặc backfill khi đọc dữ liệu cũ. */
    public void setAttemptNumber(Long attemptNumber) {
        this.attemptNumber = attemptNumber;
        this.attemptNumberFieldPresent = true;
    }

    /** Cờ trong bộ nhớ: có field nhưng null là lỗi, không phải yêu cầu legacy. */
    @Exclude
    public boolean hasAttemptNumberField() {
        return attemptNumberFieldPresent;
    }

    /** Lấy điểm đón. */
    public Place getPickup() {
        return pickup;
    }

    /** Gán điểm đón. */
    public void setPickup(Place pickup) {
        this.pickup = pickup;
    }

    /** Lấy C đã lưu; null không tự động có nghĩa là yêu cầu legacy hợp lệ. */
    public Place getDropoff() {
        return dropoff;
    }

    /** Firestore gọi setter cả khi field tồn tại nhưng có giá trị null. */
    public void setDropoff(Place dropoff) {
        this.dropoff = dropoff;
        this.dropoffFieldPresent = true;
    }

    /** Cờ chỉ dùng trong bộ nhớ; không tạo thêm field trong document requests. */
    @Exclude
    public boolean hasDropoffField() {
        return dropoffFieldPresent;
    }

    /** Lấy bản sao snapshot tiền; dùng ContributionCalculator để đọc và kiểm tra. */
    public Map<String, Object> getContributionSnapshot() {
        if (contributionSnapshot == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>(contributionSnapshot);
    }

    /** Field có mặt nhưng null vẫn là dữ liệu lỗi, không được coi như legacy. */
    public void setContributionSnapshot(Map<String, Object> contributionSnapshot) {
        this.contributionSnapshotFieldPresent = true;
        if (contributionSnapshot == null) {
            this.contributionSnapshot = null;
        } else {
            this.contributionSnapshot = new LinkedHashMap<String, Object>(contributionSnapshot);
        }
    }

    /** Cờ trong bộ nhớ, không được ghi vào Firestore. */
    @Exclude
    public boolean hasContributionSnapshotField() {
        return contributionSnapshotFieldPresent;
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
