package com.example.grabshare.activity.passenger;

import android.widget.TextView;
import android.view.View;
import com.example.grabshare.presentation.PlaceNameFormatter;

import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;

/** Hiển thị P/C của khách riêng với A/D của tài xế, kể cả request legacy. */
public final class PassengerRouteText {
    /** Chỉ chứa hàm định dạng, không lưu dữ liệu phiên hoặc chuyến. */
    private PassengerRouteText() {
    }

    /** C của request cũ thiếu field dùng D qua resolver chung; C sai hiện lỗi rõ. */
    public static String format(TripRequest request, Trip trip) {
        if (request == null || trip == null) return "Điểm khách: Chưa có dữ liệu chuyến.";
        Place pickup = request.getPickup();
        if (!validPickup(pickup)) {
            return "Điểm khách: Điểm đón không hợp lệ.";
        }
        try {
            Place dropoff = RequestRouteResolver.resolveDropoff(request, trip);
            return "Khách: " + pickup.getName().trim() + " → " + dropoff.getName().trim();
        } catch (IllegalArgumentException error) {
            return "Điểm khách: Điểm xuống không hợp lệ; hãy tải lại dữ liệu.";
        }
    }

    /** Không hiển thị P có tên nhưng tọa độ thiếu hoặc nằm ngoài miền hợp lệ. */
    private static boolean validPickup(Place pickup) {
        if (pickup == null || pickup.getName() == null || pickup.getName().trim().isEmpty()
                || pickup.getName().length() > 200
                || pickup.getLatitude() == null || pickup.getLongitude() == null) return false;
        double latitude = pickup.getLatitude();
        double longitude = pickup.getLongitude();
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    /** Gắn nội dung vào phần dành riêng cho P/C, không ghi đè View A/D. */
    public static void bind(TextView view, TripRequest request, Trip trip) {
        if (view == null) {
            return;
        }
        final String fullText = format(request, trip);
        String compact = fullText;
        try {
            if (request != null && trip != null && validPickup(request.getPickup())) {
                Place dropoff = RequestRouteResolver.resolveDropoff(request, trip);
                compact = "Đón: " + PlaceNameFormatter.format(request.getPickup().getName())
                        + "\nTrả: " + PlaceNameFormatter.format(dropoff.getName());
            }
        } catch (IllegalArgumentException error) {
            compact = fullText;
        }
        final String shortText = compact;
        view.setText(shortText);
        view.setContentDescription(fullText);
        // Màn theo dõi đã có nút địa chỉ riêng; vùng mở rộng giữ nguyên văn địa chỉ.
        if (view.getContext() instanceof UpcomingActivity) {
            view.setText(fullText);
            return;
        }
        view.setText(shortText + "\nXem địa chỉ đầy đủ +");
        view.setOnClickListener(new View.OnClickListener() {
            private boolean expanded;

            /** Không mất địa chỉ gốc khi rút gọn tên trên màn trạng thái. */
            @Override
            public void onClick(View clickedView) {
                expanded = !expanded;
                TextView textView = (TextView) clickedView;
                textView.setText(shortText + "\nXem địa chỉ đầy đủ +");
                if (expanded) {
                    textView.setText(fullText + "\nThu gọn −");
                }
            }
        });
    }
}
