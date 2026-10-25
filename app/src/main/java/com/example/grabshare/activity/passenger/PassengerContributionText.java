package com.example.grabshare.activity.passenger;

import android.widget.TextView;
import com.example.grabshare.R;

import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.model.TripRequest;

import java.text.NumberFormat;
import java.util.Locale;

/** Đọc khoản đóng góp đã xác nhận của đúng lần gửi yêu cầu. */
public final class PassengerContributionText {
    private PassengerContributionText() {
    }

    /** Thiếu tiền là ghi chú, không trình bày như số tiền nổi bật hoặc thay bằng 0đ. */
    public static void bindAmount(TextView textView, TripRequest request) {
        if (textView == null) {
            return;
        }
        textView.setText(format(request));
        textView.setTextSize(14);
        textView.setTextColor(textView.getContext().getColor(R.color.muted));
        try {
            ContributionSnapshot snapshot = ContributionCalculator.readConfirmed(request);
            if (snapshot != null) {
                textView.setTextSize(22);
                textView.setTextColor(textView.getContext().getColor(R.color.primary));
            }
        } catch (IllegalArgumentException error) {
            textView.setTextColor(textView.getContext().getColor(R.color.feedback_error_text));
        }
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
