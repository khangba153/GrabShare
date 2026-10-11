package com.example.grabshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/** Đánh giá chủ xe trong collection ratings. */
public class Rating {
    // ID chỉ dùng trong Java, không ghi thành field Firestore.
    private String id;
    private String tripId;
    private String authorId;
    private String targetUserId;
    private Long score;
    private String comment;
    private Timestamp createdAt;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public Rating() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public Rating(String tripId, String authorId, String targetUserId, Long score, String comment, Timestamp createdAt) {
        this.tripId = tripId;
        this.authorId = authorId;
        this.targetUserId = targetUserId;
        this.score = score;
        this.comment = comment;
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

    /** Lấy ID chuyến. */
    public String getTripId() {
        return tripId;
    }

    /** Gán ID chuyến. */
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    /** Lấy UID người đánh giá. */
    public String getAuthorId() {
        return authorId;
    }

    /** Gán UID người đánh giá. */
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    /** Lấy UID người được đánh giá. */
    public String getTargetUserId() {
        return targetUserId;
    }

    /** Gán UID người được đánh giá. */
    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    /** Lấy điểm đánh giá. */
    public Long getScore() {
        return score;
    }

    /** Gán điểm đánh giá. */
    public void setScore(Long score) {
        this.score = score;
    }

    /** Lấy nhận xét. */
    public String getComment() {
        return comment;
    }

    /** Gán nhận xét. */
    public void setComment(String comment) {
        this.comment = comment;
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
