package com.example.grabshare.contract;

import com.example.grabshare.model.Place;

/** Hợp đồng kiểm tra đường vòng dùng chung cho gửi và nhận yêu cầu. */
public interface DetourChecker {

    /**
     * Hợp đồng bốn điểm: so origin → destination với
     * origin → pickup → dropoff → destination, giữ đúng thứ tự.
     * Hai tuyến phải dùng cùng dịch vụ, phương tiện và cấu hình định tuyến.
     * Đọc mới cả hai tuyến, không dùng baseline lưu để quyết định.
     * Trừ khoảng cách mét chưa làm tròn rồi ceil phần chênh không âm.
     * dropoff bắt buộc hợp lệ; không ngầm chuyển null thành tuyến ba điểm.
     * Nếu các điểm kề nhau trùng tọa độ (ví dụ C=D), implementation có thể
     * gộp waypoint trùng nhưng phải giữ đủ thứ tự các điểm còn lại.
     * Callback trả trên luồng UI.
     * Lỗi API hoặc không tìm được tuyến phải gọi onError.
     * Vượt giới hạn vẫn gọi onSuccess với withinLimit bằng false.
     */
    void checkDetour(
            Place origin,
            Place pickup,
            Place dropoff,
            Place destination,
            long maxDetourMeters,
            RepositoryCallback<DetourCheckResult> callback
    );

    /**
     * Adapter cho caller cũ chưa có điểm xuống riêng, tương đương C=D.
     * Caller tạo yêu cầu mới phải gọi chữ ký bốn điểm và cung cấp C rõ ràng.
     * Không giữ implementation ba điểm làm mặc định cho một tuyến có C khác D.
     */
    @Deprecated
    default void checkDetour(
            Place origin,
            Place pickup,
            Place destination,
            long maxDetourMeters,
            RepositoryCallback<DetourCheckResult> callback
    ) {
        checkDetour(
                origin,
                pickup,
                destination,
                destination,
                maxDetourMeters,
                callback
        );
    }

    /**
     * Tính khoảng cách tuyến origin → destination để lưu khi đăng/sửa chuyến.
     * Dùng cùng dịch vụ, phương tiện và cấu hình với checkDetour.
     * Thành công trả khoảng cách mét, làm tròn lên từ kết quả API.
     * Callback trả trên luồng UI.
     * Đầu vào không hợp lệ, lỗi API hoặc không có tuyến phải gọi onError.
     * Không trả 0 để thay thế lỗi.
     */
    void calculateBaseDistance(
            Place origin,
            Place destination,
            RepositoryCallback<Long> callback
    );
}
