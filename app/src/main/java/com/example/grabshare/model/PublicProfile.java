package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Hồ sơ công khai trong collection publicProfiles. */
public class PublicProfile {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String displayName;
    private String vehicleName;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public PublicProfile() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public PublicProfile(String displayName, String vehicleName, Timestamp createdAt, Timestamp updatedAt) {
        this.displayName = displayName;
        this.vehicleName = vehicleName;
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

    /** Lấy tên hiển thị. */
    public String getDisplayName() {
        return displayName;
    }

    /** Gán tên hiển thị. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Lấy tên xe. */
    public String getVehicleName() {
        return vehicleName;
    }

    /** Gán tên xe. */
    public void setVehicleName(String vehicleName) {
        this.vehicleName = vehicleName;
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
