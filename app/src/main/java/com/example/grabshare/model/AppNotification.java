package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Thông báo trong ứng dụng từ collection notifications. */
public class AppNotification {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String recipientId;
    private String actorId;
    private String type;
    private String tripId;
    private String requestId;
    private Boolean isRead;
    private Timestamp createdAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public AppNotification() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public AppNotification(String recipientId, String actorId, String type, String tripId, String requestId, Boolean isRead, Timestamp createdAt) {
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type;
        this.tripId = tripId;
        this.requestId = requestId;
        this.isRead = isRead;
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

    /** Lấy UID người nhận. */
    public String getRecipientId() {
        return recipientId;
    }

    /** Gán UID người nhận. */
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    /** Lấy UID người tạo sự kiện. */
    public String getActorId() {
        return actorId;
    }

    /** Gán UID người tạo sự kiện. */
    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    /** Lấy loại thông báo. */
    public String getType() {
        return type;
    }

    /** Gán loại thông báo. */
    public void setType(String type) {
        this.type = type;
    }

    /** Lấy ID chuyến. */
    public String getTripId() {
        return tripId;
    }

    /** Gán ID chuyến. */
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    /** Lấy ID yêu cầu liên quan. */
    public String getRequestId() {
        return requestId;
    }

    /** Gán ID yêu cầu liên quan. */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /** Lấy trạng thái đã đọc. */
    public Boolean getIsRead() {
        return isRead;
    }

    /** Gán trạng thái đã đọc. */
    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
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
