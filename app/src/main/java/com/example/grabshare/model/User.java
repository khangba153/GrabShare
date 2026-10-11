package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Hồ sơ riêng tư của tài khoản trong collection users. */
public class User {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String email;
    private String vehiclePlate;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public User() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public User(String email, String vehiclePlate, Timestamp createdAt, Timestamp updatedAt) {
        this.email = email;
        this.vehiclePlate = vehiclePlate;
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

    /** Lấy email đăng nhập. */
    public String getEmail() {
        return email;
    }

    /** Gán email đăng nhập. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Lấy biển số xe. */
    public String getVehiclePlate() {
        return vehiclePlate;
    }

    /** Gán biển số xe. */
    public void setVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
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
