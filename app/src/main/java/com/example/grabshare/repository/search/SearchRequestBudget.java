package com.example.grabshare.repository.search;

import com.example.grabshare.api.VietnamRoutingClient;
import java.util.function.LongSupplier;

/** Ngân sách dùng chung cho hai worker; tính từng HTTP, không chỉ đếm số ứng viên. */
public final class SearchRequestBudget implements VietnamRoutingClient.RequestGate {
    private final int maximum;
    private final long deadline;
    private final LongSupplier clock;
    private int used;
    private String stopped = "";

    /** Dùng đồng hồ đơn điệu để đổi giờ hệ thống không làm nới ngân sách. */
    public SearchRequestBudget(int maximum, long durationMs) {
        this(maximum, durationMs, new LongSupplier() {
            /** Đọc thời gian đơn điệu bằng mili giây. */
            @Override public long getAsLong() { return System.nanoTime() / 1000000; }
        });
    }

    /** Cho phép kiểm tra ranh giới ngân sách mà không cần sleep hay gọi API. */
    public SearchRequestBudget(int maximum, long durationMs, LongSupplier clock) {
        this.maximum = maximum;
        this.clock = clock;
        deadline = clock.getAsLong() + durationMs;
    }

    /** Giữ chỗ HTTP nguyên tử trước khi gửi để hai worker không vượt giới hạn. */
    @Override public synchronized void beforeRequest() throws VietnamRoutingClient.RoutingException {
        if (stopped.isEmpty() && (used >= maximum || clock.getAsLong() >= deadline)) {
            stopped = "Đã tới giới hạn xử lý lượt này. Bấm Xem thêm để tiếp tục ứng viên còn chờ.";
        }
        if (!stopped.isEmpty()) throw new VietnamRoutingClient.RoutingException(
                VietnamRoutingClient.Failure.BUDGET, stopped);
        used++;
    }

    /** Lỗi quota/quyền dừng các request tiếp theo trong cùng hành động. */
    public synchronized void stop(String reason) { stopped = reason; }
    /** Số HTTP đã cấp phép, bao gồm các request không thành công. */
    public synchronized int used() { return used; }
    /** Thông báo dừng an toàn cho UI. */
    public synchronized String stoppedReason() { return stopped; }
}
