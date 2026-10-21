package com.example.grabshare.activity.search;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.grabshare.R;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.SearchTripPolicy;

/** Giữ hai điểm đã dùng ghép tuyến qua Detail và Review, không thay schema request. */
public final class PassengerSearchRoute {
    private static final String ROUTE_ARGUMENT = "search_passenger_route";
    private final Bundle routeArguments;

    /** Sao chép Bundle để màn sau không sửa ngược điều kiện đã dùng tìm chuyến. */
    private PassengerSearchRoute(Bundle arguments) {
        routeArguments = new Bundle(arguments);
    }

    /** Gắn tuyến tìm kiếm với đúng chuyến được chọn và tài khoản hiện tại. */
    public static void attach(
            Intent intent,
            String tripIdentifier,
            String userIdentifier,
            Place pickup,
            Place dropoff) {
        Bundle arguments = new Bundle();
        arguments.putString("trip_identifier", tripIdentifier);
        arguments.putString("user_identifier", userIdentifier);
        writePlace(arguments, "pickup", pickup);
        writePlace(arguments, "dropoff", dropoff);
        intent.putExtra(ROUTE_ARGUMENT, arguments);
    }

    /** Chỉ phục hồi đủ hai điểm và đúng chủ phiên, không dùng tọa độ mặc định bằng 0. */
    public static PassengerSearchRoute read(
            Bundle arguments, String tripIdentifier, String userIdentifier) {
        if (arguments == null || userIdentifier == null || tripIdentifier == null) {
            return null;
        }
        Bundle route = arguments.getBundle(ROUTE_ARGUMENT);
        if (route == null
                || !tripIdentifier.equals(route.getString("trip_identifier"))
                || !userIdentifier.equals(route.getString("user_identifier"))) {
            return null;
        }
        Place pickup = readPlace(route, "pickup");
        Place dropoff = readPlace(route, "dropoff");
        if (!SearchTripPolicy.validPlace(pickup) || !SearchTripPolicy.validPlace(dropoff)) {
            return null;
        }
        return new PassengerSearchRoute(route);
    }

    /** Giữ tuyến khi xoay màn hoặc Android hủy tiến trình. */
    public void saveTo(Bundle savedState) {
        savedState.putBundle(ROUTE_ARGUMENT, new Bundle(routeArguments));
    }

    /** Trả bản sao điểm đón đã dùng khi ghép tuyến. */
    public Place getPickup() {
        return readPlace(routeArguments, "pickup");
    }

    /** Trả bản sao điểm xuống đã dùng khi ghép tuyến. */
    public Place getDropoff() {
        return readPlace(routeArguments, "dropoff");
    }

    /** Hiện lộ trình người đi nhờ dưới lộ trình chủ xe, chỉ trong phiên phù hợp. */
    public static void bind(Activity activity, PassengerSearchRoute route, boolean sameUser) {
        if (route == null) {
            bind(activity, null, null, false);
            return;
        }
        bind(activity, route.getPickup(), route.getDropoff(), sameUser);
    }

    /** Hiện P/C của request server mà không gắn nhãn đây là bản nháp từ lần tìm kiếm. */
    public static void bind(Activity activity, Place pickup, Place dropoff, boolean sameUser) {
        View routeCard = activity.findViewById(R.id.passenger_search_route);
        if (!sameUser
                || !SearchTripPolicy.validPlace(pickup)
                || !SearchTripPolicy.validPlace(dropoff)) {
            routeCard.setVisibility(View.GONE);
            return;
        }
        routeCard.setVisibility(View.VISIBLE);
        TextView pickupTextView = activity.findViewById(R.id.passenger_search_pickup);
        TextView dropoffTextView = activity.findViewById(R.id.passenger_search_dropoff);
        pickupTextView.setText(pickup.getName());
        dropoffTextView.setText(dropoff.getName());
    }

    /** Chỉ ghi kiểu nguyên thủy vào Bundle riêng của gói tìm chuyến. */
    private static void writePlace(Bundle arguments, String prefix, Place place) {
        if (place == null) {
            return;
        }
        arguments.putString(prefix + "_name", place.getName());
        if (place.getLatitude() != null && place.getLongitude() != null) {
            arguments.putDouble(prefix + "_latitude", place.getLatitude());
            arguments.putDouble(prefix + "_longitude", place.getLongitude());
        }
    }

    /** Điểm thiếu tọa độ phải không hợp lệ thay vì bị hiểu nhầm là vị trí thực. */
    private static Place readPlace(Bundle arguments, String prefix) {
        return new Place(
                arguments.getString(prefix + "_name"),
                arguments.getDouble(prefix + "_latitude", Double.NaN),
                arguments.getDouble(prefix + "_longitude", Double.NaN));
    }
}
