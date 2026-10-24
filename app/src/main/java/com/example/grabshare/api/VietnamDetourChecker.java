package com.example.grabshare.api;

import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.DetourChecker;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.android.gms.tasks.TaskExecutors;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adapter hợp đồng chung; API chạy nền, mọi callback trả trên UI thread, không dùng Handler. */
public final class VietnamDetourChecker implements DetourChecker, AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile VietnamRoutingClient activeClient;

    /** Tách phép tính khỏi điều phối worker để không lặp logic callback/lỗi. */
    private interface Calculation<T> {
        /** Chỉ tính bằng client chung, không ghi dữ liệu hoặc giữ Activity. */
        T calculate(VietnamRoutingClient client) throws Exception;
    }

    /** Constructor không tạo request mạng trước thao tác của caller. */
    public VietnamDetourChecker() {}

    /** Tính A→D bằng cùng provider, xe máy và policy với kiểm tra đường vòng. */
    @Override
    public void calculateBaseDistance(
            Place origin, Place destination, RepositoryCallback<Long> callback) {
        final Place requestedOrigin = copyPlace(origin);
        final Place requestedDestination = copyPlace(destination);
        execute(
                new Calculation<Long>() {
                    /** Sai tọa độ hoặc không có đường là lỗi, không thay bằng khoảng cách 0. */
                    @Override
                    public Long calculate(VietnamRoutingClient client) throws Exception {
                        requirePlace(requestedOrigin);
                        requirePlace(requestedDestination);
                        VietnamRoutingClient.Route route =
                                client.findRoute(
                                        Arrays.asList(
                                                point(requestedOrigin),
                                                point(requestedDestination)),
                                        cancellation());
                        long distanceMeters = (long) Math.ceil(route.distanceMeters);
                        if (distanceMeters <= 0) {
                            throw new IOException("Tuyến gốc phải có quãng đường lớn hơn 0 mét.");
                        }
                        return distanceMeters;
                    }
                },
                callback);
    }

    /** Kiểm tra đủ A/P/C/D theo hợp đồng chung; caller cũ dùng adapter C=D của interface. */
    @Override
    public void checkDetour(
            Place origin,
            Place pickup,
            Place dropoff,
            Place destination,
            final long maxDetourMeters,
            RepositoryCallback<DetourCheckResult> callback) {
        final Place requestedOrigin = copyPlace(origin);
        final Place requestedPickup = copyPlace(pickup);
        final Place requestedDropoff = copyPlace(dropoff);
        final Place requestedDestination = copyPlace(destination);
        execute(
                new Calculation<DetourCheckResult>() {
                    /** Baseline và tuyến ghép được đo mới trong cùng một phương án định tuyến. */
                    @Override
                    public DetourCheckResult calculate(VietnamRoutingClient client)
                            throws Exception {
                        requirePlace(requestedOrigin);
                        requirePlace(requestedPickup);
                        requirePlace(requestedDropoff);
                        requirePlace(requestedDestination);
                        if (!DetourLimit.isValidMeters(maxDetourMeters)) {
                            throw new IllegalArgumentException("Giới hạn đi vòng không hợp lệ.");
                        }
                        java.util.List<VietnamRoutingClient.Point> points =
                                Arrays.asList(
                                        point(requestedOrigin),
                                        point(requestedPickup),
                                        point(requestedDropoff),
                                        point(requestedDestination));
                        // Chữ ký chung không nhận baseline lưu; giá trị tham chiếu không tham gia
                        // quyết định.
                        VietnamRoutingClient.MatchRoute routes =
                                client.findMatchRoute(points, 1L, cancellation());
                        return routes.toDetourCheckResult(maxDetourMeters);
                    }
                },
                callback);
    }

    /**
     * Mỗi checker sở hữu một executor, không chạy HTTP trên UI hoặc trong Firestore transaction.
     */
    private <T> void execute(
            final Calculation<T> calculation, final RepositoryCallback<T> callback) {
        if (closed) {
            return;
        }
        executor.execute(
                new Runnable() {
                    /** Client được tạo cho đúng thao tác, hủy kết nối khi checker đóng. */
                    @Override
                    public void run() {
                        if (closed) {
                            return;
                        }
                        VietnamRoutingClient client = VietnamRoutingClient.forSearch(null);
                        activeClient = client;
                        try {
                            final T result = calculation.calculate(client);
                            TaskExecutors.MAIN_THREAD.execute(
                                    new Runnable() {
                                        /** Không trả kết quả cho caller đã đóng ViewModel. */
                                        @Override
                                        public void run() {
                                            if (!closed) {
                                                callback.onSuccess(result);
                                            }
                                        }
                                    });
                        } catch (final Exception error) {
                            TaskExecutors.MAIN_THREAD.execute(
                                    new Runnable() {
                                        /**
                                         * Giữ lỗi quota/input/network; không biến lỗi thành kết
                                         * luận phù hợp.
                                         */
                                        @Override
                                        public void run() {
                                            if (!closed) {
                                                callback.onError(error);
                                            }
                                        }
                                    });
                        } finally {
                            activeClient = null;
                            client.cancelCurrentRequest();
                        }
                    }
                });
    }

    /** Hủy theo vòng đời checker thay vì dùng Activity trong worker. */
    private VietnamRoutingClient.Cancellation cancellation() {
        return new VietnamRoutingClient.Cancellation() {
            /** Việc đóng checker hoặc ngắt worker phải dừng mọi lần thử tiếp theo. */
            @Override
            public boolean isCancelled() {
                return closed || Thread.currentThread().isInterrupted();
            }
        };
    }

    /** Không chấp nhận Place thiếu dữ liệu rồi tự dùng tọa độ mặc định. */
    private static void requirePlace(Place place) {
        if (!SearchTripPolicy.validPlace(place)) {
            throw new IllegalArgumentException("Địa điểm cần có tên và tọa độ hợp lệ.");
        }
    }

    /** Chụp đầu vào trước khi xếp worker, tránh caller đổi Place trong lúc chờ API. */
    private static Place copyPlace(Place place) {
        if (place == null) {
            return null;
        }
        return new Place(place.getName(), place.getLatitude(), place.getLongitude());
    }

    /** Chỉ chuyển kiểu sau khi đã kiểm tra Place. */
    private static VietnamRoutingClient.Point point(Place place) {
        return new VietnamRoutingClient.Point(place.getLatitude(), place.getLongitude());
    }

    /** Caller đóng trong onCleared; xoay màn vẫn giữ checker trên ViewModel. */
    @Override
    public void close() {
        closed = true;
        VietnamRoutingClient client = activeClient;
        if (client != null) {
            client.cancelCurrentRequest();
        }
        executor.shutdownNow();
    }
}
