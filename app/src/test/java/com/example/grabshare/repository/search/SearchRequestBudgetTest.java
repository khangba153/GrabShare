package com.example.grabshare.repository.search;

import static org.junit.Assert.*;

import com.example.grabshare.api.VietnamRoutingClient;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Kiểm tra ngân sách nguyên tử khi hai worker cùng xin gửi HTTP. */
public class SearchRequestBudgetTest {

    /** Nhiều worker không thể vượt quota cục bộ ngay cả khi tranh chấp. */
    @Test
    public void concurrentReservationsNeverExceedLimit() throws Exception {
        final SearchRequestBudget budget = new SearchRequestBudget(7, 10000);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> values = new ArrayList<Future<Boolean>>();
            for (int index = 0; index < 30; index = index + 1) {
                values.add(
                        workers.submit(
                                new Callable<Boolean>() {

                                    /** Mô phỏng quyền gửi một request, không gọi API thật. */
                                    @Override
                                    public Boolean call() throws Exception {
                                        try {
                                            budget.beforeRequest();
                                            return true;
                                        } catch (VietnamRoutingClient.RoutingException limit) {
                                            assertEquals(
                                                    VietnamRoutingClient.Failure.BUDGET,
                                                    limit.failure);
                                            return false;
                                        }
                                    }
                                }));
            }
            int accepted = 0;
            for (Future<Boolean> value : values) {
                if (value.get()) {
                    accepted = accepted + 1;
                }
            }
            assertEquals(7, accepted);
            assertEquals(7, budget.getUsedRequestCount());
        } finally {
            workers.shutdownNow();
        }
    }

    /** Chạm deadline chặn request mới mà không cần đợi thời gian thật. */
    @Test
    public void deadlineAndProviderStopPreventNewRequests() throws Exception {
        final AtomicLong time = new AtomicLong(10);
        SearchRequestBudget budget =
                new SearchRequestBudget(
                        60,
                        75,
                        new LongSupplier() {

                            /** Đồng hồ kiểm tra có thể điều khiển. */
                            @Override
                            public long getAsLong() {
                                return time.get();
                            }
                        });
        budget.beforeRequest();
        time.set(85);
        try {
            budget.beforeRequest();
            fail();
        } catch (VietnamRoutingClient.RoutingException expected) {
            assertEquals(1, budget.getUsedRequestCount());
        }
        SearchRequestBudget stopped = new SearchRequestBudget(60, 10000);
        stopped.stop("Quota");
        try {
            stopped.beforeRequest();
            fail();
        } catch (VietnamRoutingClient.RoutingException expected) {
            assertEquals("Quota", expected.getMessage());
        }
        assertEquals(0, stopped.getUsedRequestCount());
    }
}
