package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Dữ liệu chuyến đi trong collection trips. */
public class Trip {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String driverId;
    private Place origin;
    private Place destination;
    private Timestamp departureAt;
    private Long costVnd;
    private String note;
    private String status;
    private Long availableSeats;
    private String acceptedRequestId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Long maxDetourMeters;
    private Long baseDistanceMeters;
    private String duplicateKey;
    private boolean duplicateKeyFieldPresent;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public Trip() {
    }

    /** Null ở dữ liệu legacy; không suy ra rằng chuyến cũ đã được bảo vệ chống trùng. */
    public String getDuplicateKey() {
        return duplicateKey;
    }

    /** Gán khóa theo hợp đồng; phải ghi cùng transaction chiếm/chuyển khóa. */
    public void setDuplicateKey(String duplicateKey) {
        this.duplicateKey = duplicateKey;
        this.duplicateKeyFieldPresent = true;
    }

    /** Phân biệt thiếu hẳn khóa legacy với field có mặt nhưng sai/null. */
    @Exclude
    public boolean hasDuplicateKeyField() {
        return duplicateKeyFieldPresent;
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public Trip(String driverId, Place origin, Place destination, Timestamp departureAt, Long costVnd, String note, String status, Long availableSeats, String acceptedRequestId, Timestamp createdAt, Timestamp updatedAt) {
        this.driverId = driverId;
        this.origin = origin;
        this.destination = destination;
        this.departureAt = departureAt;
        this.costVnd = costVnd;
        this.note = note;
        this.status = status;
        this.availableSeats = availableSeats;
        this.acceptedRequestId = acceptedRequestId;
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

    /** Lấy UID chủ xe. */
    public String getDriverId() {
        return driverId;
    }

    /** Gán UID chủ xe. */
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    /** Lấy điểm đi. */
    public Place getOrigin() {
        return origin;
    }

    /** Gán điểm đi. */
    public void setOrigin(Place origin) {
        this.origin = origin;
    }

    /** Lấy điểm đến. */
    public Place getDestination() {
        return destination;
    }

    /** Gán điểm đến. */
    public void setDestination(Place destination) {
        this.destination = destination;
    }

    /** Lấy thời gian khởi hành. */
    public Timestamp getDepartureAt() {
        return departureAt;
    }

    /** Gán thời gian khởi hành. */
    public void setDepartureAt(Timestamp departureAt) {
        this.departureAt = departureAt;
    }

    /** Lấy số tiền đóng góp VNĐ. */
    public Long getCostVnd() {
        return costVnd;
    }

    /** Gán số tiền đóng góp VNĐ. */
    public void setCostVnd(Long costVnd) {
        this.costVnd = costVnd;
    }

    /** Lấy ghi chú. */
    public String getNote() {
        return note;
    }

    /** Gán ghi chú. */
    public void setNote(String note) {
        this.note = note;
    }

    /** Lấy trạng thái chuyến. */
    public String getStatus() {
        return status;
    }

    /** Gán trạng thái chuyến. */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Lấy số chỗ trống. */
    public Long getAvailableSeats() {
        return availableSeats;
    }

    /** Gán số chỗ trống. */
    public void setAvailableSeats(Long availableSeats) {
        this.availableSeats = availableSeats;
    }

    /** Lấy ID yêu cầu được nhận. */
    public String getAcceptedRequestId() {
        return acceptedRequestId;
    }

    /** Gán ID yêu cầu được nhận. */
    public void setAcceptedRequestId(String acceptedRequestId) {
        this.acceptedRequestId = acceptedRequestId;
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

    /** Giới hạn đường vòng; null khi chuyến chưa thiết lập. */
    public Long getMaxDetourMeters() {
        return maxDetourMeters;
    }

    public void setMaxDetourMeters(Long maxDetourMeters) {
        this.maxDetourMeters = maxDetourMeters;
    }

    /** Khoảng cách tuyến gốc; null khi chưa có dữ liệu. */
    public Long getBaseDistanceMeters() {
        return baseDistanceMeters;
    }

    public void setBaseDistanceMeters(Long baseDistanceMeters) {
        this.baseDistanceMeters = baseDistanceMeters;
    }
}
