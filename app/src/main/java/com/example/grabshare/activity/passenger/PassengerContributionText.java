package com.example.grabshare.activity.passenger;

import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.model.TripRequest;

import java.text.NumberFormat;
import java.util.Locale;

/** Đọc khoản đóng góp đã xác nhận của đúng lần gửi yêu cầu. */
public final class PassengerContributionText {
    private PassengerContributionText() {
    }

    public static String format(TripRequest request) {
        if (request == null) return "Chưa có dữ liệu đóng góp";
        try {
            ContributionSnapshot snapshot = ContributionCalculator.readConfirmed(request);
            if (snapshot == null) return "Chưa xác nhận đóng góp (yêu cầu cũ)";
            if (snapshot.getAmountVnd() == 0) return "Miễn phí";
            return NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                    .format(snapshot.getAmountVnd()) + "đ";
        } catch (IllegalArgumentException error) {
            return "Dữ liệu đóng góp không hợp lệ";
        }
    }
}
