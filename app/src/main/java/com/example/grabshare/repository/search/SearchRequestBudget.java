package com.example.grabshare.repository.search;

import com.example.grabshare.api.VietnamRoutingClient;

import java.util.function.LongSupplier;

/** Ngân sách dùng chung cho hai worker; tính từng HTTP, không chỉ đếm số ứng viên. */
public final class SearchRequestBudget implements VietnamRoutingClient.RequestGate {

    private final int maximumRequestCount;

    private final long requestDeadlineMilliseconds;

    private final LongSupplier monotonicClock;

    private int usedRequestCount;

    private String stopReason = "";

    /** Dùng đồng hồ đơn điệu để đổi giờ hệ thống không làm nới ngân sách. */
    public SearchRequestBudget(int maximumRequestCount, long durationMilliseconds) {
        this(
                maximumRequestCount,
                durationMilliseconds,
                new LongSupplier() {

                    /** Đọc thời gian đơn điệu bằng mili giây. */
                    @Override
                    public long getAsLong() {
                        return System.nanoTime() / 1000000;
                    }
                });
    }

    /** Cho phép kiểm tra ranh giới ngân sách mà không cần sleep hay gọi API. */
    public SearchRequestBudget(
            int maximumRequestCount, long durationMilliseconds, LongSupplier monotonicClock) {
        this.maximumRequestCount = maximumRequestCount;
        this.monotonicClock = monotonicClock;
        requestDeadlineMilliseconds = monotonicClock.getAsLong() + durationMilliseconds;
    }

    /** Giữ chỗ HTTP nguyên tử trước khi gửi để hai worker không vượt giới hạn. */
    @Override
    public synchronized void beforeRequest() throws VietnamRoutingClient.RoutingException {
        if (stopReason.isEmpty()
                && (usedRequestCount >= maximumRequestCount
                        || monotonicClock.getAsLong() >= requestDeadlineMilliseconds)) {
            stopReason =
                    "Đã tới giới hạn xử lý lượt này. Bấm Xem thêm để tiếp tục ứng viên còn chờ.";
        }
        if (!stopReason.isEmpty()) {
            throw new VietnamRoutingClient.RoutingException(
                    VietnamRoutingClient.Failure.BUDGET, stopReason);
        }
        usedRequestCount = usedRequestCount + 1;
    }

    /** Lỗi quota/quyền dừng các request tiếp theo trong cùng hành động. */
    public synchronized void stop(String reason) {
        stopReason = reason;
    }

    /** Số HTTP đã cấp phép, bao gồm các request không thành công. */
    public synchronized int getUsedRequestCount() {
        return usedRequestCount;
    }

    /** Thông báo dừng an toàn cho UI. */
    public synchronized String getStopReason() {
        return stopReason;
    }
}
