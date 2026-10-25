package com.example.grabshare.presentation;

import com.google.firebase.firestore.DocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Ngữ cảnh chuyến nằm trong snapshot chat để phân biệt nhiều chuyến với cùng một người. */
public final class ChatSummaryText {
    private ChatSummaryText() {
    }

    public static String format(DocumentSnapshot conversation) {
        String pickup = conversation.getString("pickupName");
        String dropoff = conversation.getString("dropoffName");
        String result = shortName(pickup) + " → " + shortName(dropoff);
        if (conversation.getTimestamp("departureAt") != null) {
            result += "\nKhởi hành: " + new SimpleDateFormat("HH:mm · dd/MM/yyyy", new Locale("vi", "VN"))
                    .format(conversation.getTimestamp("departureAt").toDate());
        }
        return result;
    }

    private static String shortName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Chưa rõ địa điểm";
        }
        com.example.grabshare.model.Place place = new com.example.grabshare.model.Place();
        place.setName(name);
        return TripCardText.place(place);
    }
}
