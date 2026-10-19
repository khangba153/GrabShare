package com.example.grabshare.contract;

import com.example.grabshare.model.Place;

/** Hợp đồng kiểm tra đường vòng dùng chung cho gửi và nhận yêu cầu. */
public interface DetourChecker {

    /**
     * So sánh tuyến origin → destination với origin → pickup → destination.
     * Hai tuyến phải dùng cùng dịch vụ, phương tiện và cấu hình định tuyến.
     * Callback trả trên luồng UI.
     * Lỗi API hoặc không tìm được tuyến phải gọi onError.
     * Vượt giới hạn vẫn gọi onSuccess với withinLimit bằng false.
     */
    void checkDetour(
            Place origin,
            Place pickup,
            Place destination,
            long maxDetourMeters,
            RepositoryCallback<DetourCheckResult> callback
    );

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