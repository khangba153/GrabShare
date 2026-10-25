package com.example.grabshare.presentation;

import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.firebase.Timestamp;

/** Trạng thái hiển thị của danh sách; không ghi EXPIRED hoặc tự hoàn thành lên Firestore. */
public final class TripListState {
    private TripListState() {
    }

    /** Đúng thời điểm khởi hành đã coi là quá hạn nhận yêu cầu mới. */
    public static boolean pastDeparture(Trip trip, long now) {
        return trip != null && trip.getDepartureAt() != null
                && trip.getDepartureAt().toDate().getTime() <= now;
    }

    /** Chỉ chuyến chưa nhận khách hết giờ mới vào lịch sử; FULL vẫn cần chủ xe xử lý. */
    public static String driverStatus(Trip trip, long now) {
        if (trip == null || trip.getStatus() == null) {
            return "UNKNOWN";
        }
        String status = trip.getStatus();
        if ("OPEN".equals(status) && pastDeparture(trip, now)
                && (trip.getAcceptedRequestId() == null || trip.getAcceptedRequestId().isEmpty())) {
            return "EXPIRED";
        }
        return status;
    }

    /** Giữ trạng thái hủy/từ chối của từng request, không gộp lịch sử theo trip_id. */
    public static String passengerStatus(TripRequest request, Trip trip, long now) {
        if (request == null || request.getStatus() == null) {
            return "UNKNOWN";
        }
        String status = request.getStatus();
        if ("CANCELLED".equals(status) || "REJECTED".equals(status)) {
            return status;
        }
        if (trip == null) {
            return "UNKNOWN";
        }
        if ("CANCELLED".equals(trip.getStatus())) {
            return "CANCELLED";
        }
        if ("ACCEPTED".equals(status) && "COMPLETED".equals(trip.getStatus())) {
            return "COMPLETED";
        }
        if ("PENDING".equals(status)
                && (pastDeparture(trip, now) || "COMPLETED".equals(trip.getStatus()))) {
            return "EXPIRED";
        }
        return status;
    }

    /** Mọi trạng thái kết thúc chỉ đọc; thiếu chuyến cũng không chiếm mục đang theo dõi. */
    public static boolean history(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status)
                || "REJECTED".equals(status) || "EXPIRED".equals(status)
                || "UNKNOWN".equals(status);
    }

    /** Dùng chung nhãn giữa thẻ, bộ lọc và chi tiết lịch sử. */
    public static String label(String status, Trip trip, long now) {
        if ("EXPIRED".equals(status)) {
            return "Hết hạn";
        }
        if (("ACCEPTED".equals(status) || "FULL".equals(status)) && pastDeparture(trip, now)) {
            return "Chờ hoàn thành";
        }
        if ("PENDING".equals(status)) {
            return "Chờ xác nhận";
        }
        if ("ACCEPTED".equals(status)) {
            return "Đã được nhận";
        }
        if ("FULL".equals(status)) {
            return "Đã nhận khách";
        }
        if ("OPEN".equals(status)) {
            return "Đang nhận yêu cầu";
        }
        if ("COMPLETED".equals(status)) {
            return "Hoàn thành";
        }
        if ("CANCELLED".equals(status)) {
            return "Đã hủy";
        }
        if ("REJECTED".equals(status)) {
            return "Bị từ chối";
        }
        return "Không khả dụng";
    }

    /** Ưu tiên chuyến đã nhận khách cần kết thúc trước các chuyến sắp đi. */
    public static int attentionPriority(String status, Trip trip, long now) {
        if (("ACCEPTED".equals(status) || "FULL".equals(status)) && pastDeparture(trip, now)) {
            return 0;
        }
        return 1;
    }

    /** Thiếu timestamp luôn ở cuối, dù sắp xếp tăng hoặc giảm. */
    public static int compareTime(Timestamp first, Timestamp second, boolean descending) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        if (descending) {
            return second.compareTo(first);
        }
        return first.compareTo(second);
    }

    /** Lịch sử hết hạn lấy mốc khởi hành vì không có lần ghi updatedAt khi đổi nhóm tại máy. */
    public static Timestamp historyTime(String status, Timestamp updatedAt,
            Timestamp createdAt, Trip trip) {
        if ("EXPIRED".equals(status) && trip != null && trip.getDepartureAt() != null) {
            return trip.getDepartureAt();
        }
        if (trip != null && ("CANCELLED".equals(trip.getStatus())
                || "COMPLETED".equals(trip.getStatus())) && trip.getUpdatedAt() != null
                && (updatedAt == null || trip.getUpdatedAt().compareTo(updatedAt) > 0)) {
            return trip.getUpdatedAt();
        }
        if (updatedAt != null) {
            return updatedAt;
        }
        if (createdAt != null) {
            return createdAt;
        }
        if (trip != null) {
            return trip.getDepartureAt();
        }
        return null;
    }
}
