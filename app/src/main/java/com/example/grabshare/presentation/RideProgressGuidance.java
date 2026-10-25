package com.example.grabshare.presentation;

import android.widget.TextView;

import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;

/** Hướng dẫn theo trạng thái thật; không suy ra giờ đón, đã thanh toán hoặc đã đến nơi. */
public final class RideProgressGuidance {
    private RideProgressGuidance() {
    }

    /** Chỉ người đã được nhận trên đúng chuyến mới thấy hướng dẫn đi và đánh giá. */
    public static void bindPassenger(TextView textView, TripRequest request, Trip trip) {
        if (textView == null) {
            return;
        }
        String message = "Chưa xác nhận được trạng thái chuyến. Hãy kiểm tra lại kết nối.";
        if (request != null && trip != null && trip.getId() != null
                && trip.getId().equals(request.getTripId())) {
            if ("CANCELLED".equals(request.getStatus()) || "CANCELLED".equals(trip.getStatus())) {
                message = "Đã hủy. Thông tin được giữ trong lịch sử.";
            } else if ("ACCEPTED".equals(request.getStatus()) && request.getId() != null
                    && request.getId().equals(trip.getAcceptedRequestId())) {
                if ("COMPLETED".equals(trip.getStatus())) {
                    message = "Bạn có thể đánh giá và thêm yêu thích. Hai bên tự thanh toán;"
                            + " app không xác nhận đã thu tiền.";
                } else if ("FULL".equals(trip.getStatus())) {
                    message = "Hai bên tự thanh toán. Chủ xe xác nhận khi chuyến kết thúc.";
                }
            }
        }
        textView.setText(message);
    }

    /** Nhắc chủ xe thực hiện đúng thứ tự, không coi việc quá giờ là chuyến đã kết thúc. */
    public static void bindDriver(TextView textView, Trip trip) {
        if (textView == null) {
            return;
        }
        String message = "Đang xác nhận trạng thái chuyến…";
        if (trip != null) {
            if ("OPEN".equals(trip.getStatus())) {
                message = "Chọn Yêu cầu đi nhờ để xem và nhận khách.";
            } else if ("FULL".equals(trip.getStatus())) {
                message = "Đã giữ chỗ cho khách. Chỉ hoàn thành khi chuyến thực sự kết thúc.";
            } else if ("COMPLETED".equals(trip.getStatus())) {
                message = "Chuyến đã kết thúc. Hai bên tự thanh toán khoản đóng góp đã xác nhận.";
            } else if ("CANCELLED".equals(trip.getStatus())) {
                message = "Chuyến đã hủy và được giữ trong lịch sử.";
            }
        }
        textView.setText(message);
    }
}
