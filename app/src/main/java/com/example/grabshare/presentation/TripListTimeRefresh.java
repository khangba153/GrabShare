package com.example.grabshare.presentation;

import com.google.android.gms.tasks.TaskExecutors;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Cập nhật nhóm khi tới giờ đi hoặc đổi ngày, không polling và không dùng Handler. */
public final class TripListTimeRefresh {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingRefresh;
    private int generation;

    /** Mỗi màn chỉ giữ một lịch; cả bộ lọc Hôm nay cũng được cập nhật qua nửa đêm. */
    public void schedule(long nextDepartureMillis, final Runnable refresh) {
        stop();
        final int expectedGeneration = generation;
        Calendar midnight = Calendar.getInstance();
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        long nextRefresh = midnight.getTimeInMillis();
        long now = System.currentTimeMillis();
        if (nextDepartureMillis > 0L && nextDepartureMillis < nextRefresh) {
            nextRefresh = Math.max(now, nextDepartureMillis);
        }
        pendingRefresh = executor.schedule(new Runnable() {
            /** Chuyển cập nhật giao diện về main thread, bỏ callback của màn đã dừng. */
            @Override
            public void run() {
                TaskExecutors.MAIN_THREAD.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (expectedGeneration == generation) {
                            refresh.run();
                        }
                    }
                });
            }
        }, Math.max(1L, nextRefresh - now + 20L), TimeUnit.MILLISECONDS);
    }

    /** Hủy lịch khi màn không còn ở phía trước. */
    public void stop() {
        generation = generation + 1;
        if (pendingRefresh != null) {
            pendingRefresh.cancel(false);
            pendingRefresh = null;
        }
    }

    /** Giải phóng executor khi Activity bị hủy. */
    public void close() {
        stop();
        executor.shutdownNow();
    }
}
