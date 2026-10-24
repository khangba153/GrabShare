package com.example.grabshare.contract;

import com.google.firebase.firestore.FieldValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Hợp đồng ID và con trỏ lần yêu cầu; repository chịu trách nhiệm transaction và UID. */
public final class RequestAttemptContract {
    public static final String GUARD_COLLECTION = "requestGuards";
    public static final String PASSENGER_COLLECTION = "passengers";

    /** Chỉ cung cấp hàm dùng chung, không giữ dữ liệu phiên. */
    private RequestAttemptContract() {
    }

    /** Tạo một ID cho một ý định gửi; phải giữ ID này qua xoay, retry và transaction retry. */
    public static String newRequestId(String passengerIdentifier) {
        requireIdentifier(passengerIdentifier);
        return UUID.randomUUID().toString().replace("-", "") + "_" + passengerIdentifier;
    }

    /** Tách cặp khóa bằng cấp path, không ghép hai ID có thể chứa dấu gạch dưới. */
    public static String guardPath(String tripIdentifier, String passengerIdentifier) {
        requireIdentifier(tripIdentifier);
        requireIdentifier(passengerIdentifier);
        return GUARD_COLLECTION + "/" + tripIdentifier + "/"
                + PASSENGER_COLLECTION + "/" + passengerIdentifier;
    }

    /** ID cũ chỉ dùng đọc để xác định lần legacy, không dùng tạo yêu cầu mới. */
    public static String legacyRequestId(String tripIdentifier, String passengerIdentifier) {
        requireIdentifier(tripIdentifier);
        requireIdentifier(passengerIdentifier);
        return tripIdentifier + "_" + passengerIdentifier;
    }

    /** Chỉ tăng số lần khi chưa có lịch sử hoặc lần trước đã do khách hủy. */
    public static long nextAttemptNumber(Long previousAttemptNumber, String previousStatus) {
        if (previousStatus != null && !"CANCELLED".equals(previousStatus)) {
            throw new IllegalStateException("Lần yêu cầu trước chưa đủ điều kiện gửi lại.");
        }
        if (previousAttemptNumber == null) {
            return 1L;
        }
        if (previousStatus == null || previousAttemptNumber < 1
                || previousAttemptNumber == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Số lần yêu cầu không hợp lệ.");
        }
        return previousAttemptNumber + 1;
    }

    /** Whitelist con trỏ mới; chỉ ghi cùng Request mới và REQUEST_SENT trong transaction. */
    public static Map<String, Object> guardFields(String requestIdentifier, long attemptNumber) {
        requireIdentifier(requestIdentifier);
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Số lần yêu cầu phải dương.");
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("requestId", requestIdentifier);
        fields.put("attemptNumber", attemptNumber);
        fields.put("updatedAt", FieldValue.serverTimestamp());
        return fields;
    }

    /** Giữ ID event độc lập cho từng lần gửi; loại sự kiện không được tự mở rộng. */
    public static String requestEventId(String requestIdentifier, String eventType) {
        requireIdentifier(requestIdentifier);
        if (!"REQUEST_SENT".equals(eventType) && !"REQUEST_ACCEPTED".equals(eventType)
                && !"REQUEST_REJECTED".equals(eventType)
                && !"REQUEST_CANCELLED".equals(eventType)) {
            throw new IllegalArgumentException("Loại thông báo yêu cầu không hợp lệ.");
        }
        return requestIdentifier + "_" + eventType;
    }

    /** ID document phải có nội dung, không được chứa ký tự chia path. */
    private static void requireIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty() || identifier.contains("/")) {
            throw new IllegalArgumentException("Thiếu ID hợp lệ.");
        }
    }
}
