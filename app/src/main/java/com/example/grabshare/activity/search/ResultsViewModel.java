package com.example.grabshare.activity.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.api.VietnamRoutingClient;
import com.example.grabshare.api.VietnamRoutingClient.Point;
import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.ContributionSnapshot;
import com.example.grabshare.contract.DetourCheckResult;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.repository.search.EnRouteSearch;
import com.example.grabshare.repository.search.EnRouteSearch.Candidate;
import com.example.grabshare.repository.search.EnRouteSearch.Criteria;
import com.example.grabshare.repository.search.EnRouteSearch.Match;
import com.example.grabshare.repository.search.SearchRequestBudget;
import com.example.grabshare.repository.search.TripSearchRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Sở hữu hàng đợi, metric và tác vụ tìm chuyến qua xoay màn; không giữ Activity. */
public class ResultsViewModel extends ViewModel {

    /** Snapshot bất biến được LiveData chuyển về UI theo lifecycle. */
    public static final class State {

        public final List<Match> matches;

        public final boolean busy;

        public final boolean hasMore;

        public final boolean sessionValid;

        public final EnRouteSearch.Sort sort;

        public final String message;

        /** Giữ dữ liệu hiển thị riêng với hàng đợi đang được worker xử lý. */
        State(
                List<Match> matches,
                boolean busy,
                boolean hasMore,
                boolean sessionValid,
                EnRouteSearch.Sort sort,
                String message) {
            this.matches = Collections.unmodifiableList(matches);
            this.busy = busy;
            this.hasMore = hasMore;
            this.sessionValid = sessionValid;
            this.sort = sort;
            this.message = message;
        }
    }

    /** Một ứng viên chỉ được kết luận khi cặp đường bộ đã kiểm tra thành công. */
    private static final class Evaluation {

        final Candidate candidate;

        Match match;

        boolean deferred;

        String error = "";

        /** Lưu danh tính ứng viên để trả lại hàng đợi khi hết ngân sách. */
        Evaluation(Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<State>();

    private final TripSearchRepository repository = new TripSearchRepository();

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private final ExecutorService coordinator = Executors.newSingleThreadExecutor();

    private final ExecutorService workers = Executors.newFixedThreadPool(2);

    private final Deque<Candidate> pending = new ArrayDeque<Candidate>();

    private final List<Match> matches = new ArrayList<Match>();

    private final List<VietnamRoutingClient> clients = new ArrayList<VietnamRoutingClient>();

    private final Map<String, PublicProfile> profiles = new HashMap<String, PublicProfile>();

    private final Set<String> requestedProfiles = new HashSet<String>();

    private final List<Future<Evaluation>> futures = new ArrayList<Future<Evaluation>>();

    private Map<String, Double> verifiedBaselines = new ConcurrentHashMap<String, Double>();

    private final FirebaseAuth.AuthStateListener authListener;

    private volatile int generation;

    private boolean closed;

    private boolean busy;

    private Criteria criteria;

    private EnRouteSearch.Sort sort = EnRouteSearch.Sort.RECOMMENDED;

    private String notice = "";

    private String metadataNotice = "";

    private String evaluationNotice = "";

    /** Theo dõi phiên đăng nhập để hủy dữ liệu đang tìm của người dùng cũ. */
    public ResultsViewModel() {
        authListener =
                new FirebaseAuth.AuthStateListener() {

                    /** Không để callback phiên cũ thêm chuyến vào tài khoản mới. */
                    @Override
                    public void onAuthStateChanged(FirebaseAuth firebaseAuth) {
                        synchronized (ResultsViewModel.this) {
                            if (criteria != null && !sameUser()) {
                                cancelWork();
                                pending.clear();
                                matches.clear();
                                profiles.clear();
                                requestedProfiles.clear();
                                verifiedBaselines.clear();
                                busy = false;
                                notice = "Phiên đăng nhập đã đổi. Quay lại Home để tìm lại.";
                                publish();
                            }
                        }
                    }
                };
        auth.addAuthStateListener(authListener);
    }

    /** Cho màn hình quan sát mà không sở hữu tác vụ mạng. */
    public LiveData<State> getState() {
        return state;
    }

    /** Chỉ query lần đầu; xoay màn dùng lại ViewModel và hàng đợi hiện có. */
    public synchronized void initialize(Criteria value) {
        if (criteria != null) {
            publish();
            return;
        }
        criteria = value;
        if (!sameUser()) {
            notice = "Phiên đăng nhập đã đổi. Quay lại Home để tìm lại.";
            publish();
            return;
        }
        refresh();
    }

    /** Làm mới đọc lại server; không được nhầm với Xem thêm của hàng đợi hiện tại. */
    public synchronized void refresh() {
        if (closed || criteria == null || !sameUser()) {
            return;
        }
        cancelWork();
        pending.clear();
        matches.clear();
        profiles.clear();
        requestedProfiles.clear();
        verifiedBaselines = new ConcurrentHashMap<String, Double>();
        metadataNotice = "";
        evaluationNotice = "";
        busy = true;
        notice = "Đang tìm chuyến từ giờ bạn chọn đến tối đa một giờ sau…";
        publish();
        final int requestGeneration = generation;
        final Criteria criteriaSnapshot = criteria;
        long[] window =
                EnRouteSearch.timeWindow(criteriaSnapshot.requestedAt, System.currentTimeMillis());
        queryTrips(
                window[0],
                window[1],
                new RepositoryCallback<TripSearchRepository.SearchSnapshot>() {

                    /** Lọc toán học trên executor, không chặn UI với danh sách đọc từ Firebase. */
                    @Override
                    public void onSuccess(final TripSearchRepository.SearchSnapshot snapshot) {
                        synchronized (ResultsViewModel.this) {
                            if (!current(requestGeneration)) {
                                return;
                            }
                            coordinator.execute(
                                    new Runnable() {

                                        /** Chuẩn bị hàng đợi một lần rồi bắt đầu cụm đầu tiên. */
                                        @Override
                                        public void run() {
                                            EnRouteSearch.Prepared prepared =
                                                    EnRouteSearch.prepare(
                                                            snapshot.trips,
                                                            criteriaSnapshot,
                                                            System.currentTimeMillis());
                                            synchronized (ResultsViewModel.this) {
                                                if (!current(requestGeneration)) {
                                                    return;
                                                }
                                                pending.addAll(prepared.candidates);
                                                if (prepared.missingMetadata > 0) {
                                                    metadataNotice =
                                                            prepared.missingMetadata
                                                                    + " chuyến thiếu cấu hình đi"
                                                                    + " vòng nên chưa được kiểm"
                                                                    + " tra.";
                                                }
                                                int invalidDocuments =
                                                        prepared.invalid
                                                                + snapshot.invalidDocuments;
                                                if (invalidDocuments > 0) {
                                                    metadataNotice =
                                                            (metadataNotice
                                                                            + " "
                                                                            + invalidDocuments
                                                                            + " chuyến có dữ liệu"
                                                                            + " không hợp lệ.")
                                                                    .trim();
                                                }
                                            }
                                            runAction(requestGeneration, criteriaSnapshot);
                                        }
                                    });
                        }
                    }

                    /** Lỗi quyền/index/mạng được hiển thị, không báo nhầm danh sách rỗng. */
                    @Override
                    public void onError(Exception error) {
                        synchronized (ResultsViewModel.this) {
                            if (!current(requestGeneration)) {
                                return;
                            }
                            busy = false;
                            notice =
                                    "Không đọc được chuyến từ máy chủ. Kiểm tra mạng rồi bấm Làm"
                                            + " mới; nếu vẫn lỗi, báo nhóm hỗ trợ.";
                            publish();
                        }
                    }
                });
    }

    /** Lấy cụm tiếp theo từ RAM, khóa nút khi một hành động đang chạy. */
    public synchronized void loadMore() {
        if (closed || busy || pending.isEmpty() || !sameUser()) {
            return;
        }
        busy = true;
        notice = "Đang kiểm tra nhóm ứng viên tiếp theo…";
        publish();
        final int requestGeneration = generation;
        final Criteria criteriaSnapshot = criteria;
        coordinator.execute(
                new Runnable() {

                    /** Điều phối ngoài UI, chỉ cho hai worker gọi Routing cùng lúc. */
                    @Override
                    public void run() {
                        runAction(requestGeneration, criteriaSnapshot);
                    }
                });
    }

    /** Đổi tiêu chí trên metric đã tải; không query Firestore hoặc Routing. */
    public synchronized void setSort(EnRouteSearch.Sort value) {
        sort = value;
        publish();
    }

    /** Loại kết quả đã đến giờ khi quay lại màn, không tự khởi động lượt mạng mới. */
    public synchronized void revalidate() {
        if (criteria == null) {
            return;
        }
        if (!sameUser()) {
            cancelWork();
            pending.clear();
            matches.clear();
            profiles.clear();
            requestedProfiles.clear();
            verifiedBaselines.clear();
            busy = false;
            notice = "Phiên đăng nhập đã đổi. Quay lại Home để tìm lại.";
        }
        publish();
    }

    /** Xử lý tối đa ba cụm để lấy bù tới ba kết quả mới, luôn giữ phần chưa đánh giá. */
    private void runAction(final int requestGeneration, final Criteria criteriaSnapshot) {
        final Map<String, Double> baselineCache;
        synchronized (this) {
            if (!current(requestGeneration)) {
                return;
            }
            baselineCache = verifiedBaselines;
        }
        final SearchRequestBudget budget =
                new SearchRequestBudget(
                        EnRouteSearch.MAXIMUM_HTTP_REQUESTS_PER_ACTION,
                        EnRouteSearch.ACTION_TIMEOUT_MILLISECONDS);
        int actionCandidates = 0;
        int newMatches = 0;
        String lastError = "";
        try {
            while (current(requestGeneration)
                    && actionCandidates < EnRouteSearch.MAXIMUM_CANDIDATES_PER_ACTION) {
                final List<Candidate> batch = new ArrayList<Candidate>();
                CompletionService<Evaluation> completion =
                        new ExecutorCompletionService<Evaluation>(workers);
                synchronized (this) {
                    if (!current(requestGeneration)) {
                        return;
                    }
                    while (batch.size() < EnRouteSearch.BATCH_SIZE && !pending.isEmpty()) {
                        batch.add(pending.removeFirst());
                    }
                    if (batch.isEmpty()) {
                        break;
                    }
                    notice = "Đang kiểm tra đường đón và trả khách…";
                    for (final Candidate candidate : batch) {
                        Future<Evaluation> future =
                                completion.submit(
                                        new Callable<Evaluation>() {

                                            /**
                                             * Mỗi ứng viên có client riêng để hủy kết nối không ảnh
                                             * hưởng worker khác.
                                             */
                                            @Override
                                            public Evaluation call() {
                                                return evaluate(
                                                        candidate,
                                                        criteriaSnapshot,
                                                        requestGeneration,
                                                        budget,
                                                        baselineCache);
                                            }
                                        });
                        futures.add(future);
                    }
                    publish();
                }
                List<Candidate> deferred = new ArrayList<Candidate>();
                int completed = 0;
                while (completed < batch.size() && current(requestGeneration)) {
                    Future<Evaluation> completedEvaluationFuture =
                            completion.poll(1, TimeUnit.SECONDS);
                    if (completedEvaluationFuture == null) {
                        continue;
                    }
                    Evaluation result = completedEvaluationFuture.get();
                    completed = completed + 1;
                    synchronized (this) {
                        if (!current(requestGeneration)) {
                            return;
                        }
                        futures.remove(completedEvaluationFuture);
                        if (result.deferred) {
                            deferred.add(result.candidate);
                        } else {
                            if (result.match != null) {
                                matches.add(result.match);
                                newMatches = newMatches + 1;
                                loadProfile(result.match.trip.getDriverId(), requestGeneration);
                            }
                        }
                        if (!result.error.isEmpty()) {
                            lastError = result.error;
                            evaluationNotice = result.error;
                        }
                        publish();
                    }
                }
                synchronized (this) {
                    if (!current(requestGeneration)) {
                        return;
                    }
                    // Trả theo thứ tự gốc của batch, không theo thứ tự mạng hoàn thành.
                    for (int index = batch.size() - 1; index >= 0; index = index - 1) {
                        if (deferred.contains(batch.get(index))) {
                            pending.addFirst(batch.get(index));
                        }
                    }
                }
                actionCandidates = actionCandidates + (batch.size());
                if (!deferred.isEmpty()
                        || !budget.getStopReason().isEmpty()
                        || newMatches >= EnRouteSearch.MINIMUM_NEW_MATCH_COUNT) {
                    break;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception unexpected) {
            lastError = "Chưa hoàn tất lượt kiểm tra. Bấm Làm mới để thử lại.";
        } finally {
            synchronized (this) {
                if (current(requestGeneration)) {
                    busy = false;
                    if (!budget.getStopReason().isEmpty()) {
                        notice = budget.getStopReason();
                    } else {
                        notice = lastError;
                    }
                    publish();
                }
            }
        }
    }

    /** Chỉ kết luận phù hợp sau cặp tuyến VN tương thích và ngưỡng tài xế chấp nhận. */
    private Evaluation evaluate(
            Candidate candidate,
            Criteria criteriaSnapshot,
            final int requestGeneration,
            SearchRequestBudget budget,
            Map<String, Double> baselineCache) {
        Evaluation result = new Evaluation(candidate);
        VietnamRoutingClient client = createRoutingClient(budget);
        synchronized (this) {
            if (!current(requestGeneration)) {
                result.deferred = true;
                return result;
            }
            clients.add(client);
        }
        try {
            if (candidate.trip.getDepartureAt().toDate().getTime() <= System.currentTimeMillis()) {
                return result;
            }
            VietnamRoutingClient.MatchRoute route =
                    client.findMatchRoute(
                            Arrays.asList(
                                    point(candidate.trip.getOrigin()),
                                    point(criteriaSnapshot.pickup),
                                    point(criteriaSnapshot.dropoff),
                                    point(candidate.trip.getDestination())),
                            candidate.trip.getBaseDistanceMeters(),
                            new VietnamRoutingClient.Cancellation() {

                                /** Từ chối toàn bộ callback của lượt đã thay hoặc phiên đã đổi. */
                                @Override
                                public boolean isCancelled() {
                                    return !current(requestGeneration);
                                }
                            },
                            baselineCache);
            // Số km chủ xe nhập là giới hạn thật; chỉ làm tròn lên mét, không nới thêm sai số.
            DetourCheckResult routeResult =
                    route.toDetourCheckResult(candidate.trip.getMaxDetourMeters());
            if (routeResult.isWithinLimit()
                    && candidate.trip.getDepartureAt().toDate().getTime()
                            > System.currentTimeMillis()) {
                ContributionSnapshot contribution =
                        ContributionCalculator.calculate(candidate.trip.getCostVnd(), routeResult);
                result.match =
                        new Match(
                                candidate.trip,
                                null,
                                route.route.requiredLegDistancesMeters.get(0),
                                route.detourMeters(),
                                route.baselineMeters,
                                route.baselineAdjusted,
                                contribution,
                                criteriaSnapshot);
            }
        } catch (VietnamRoutingClient.RoutingException error) {
            result.error = error.getMessage();
            if (error.failure == VietnamRoutingClient.Failure.BUDGET) {
                result.deferred = true;
            } else {
                if (error.failure == VietnamRoutingClient.Failure.QUOTA
                        || error.failure == VietnamRoutingClient.Failure.ACCESS) {
                    budget.stop(error.getMessage());
                    result.deferred = true;
                }
            }
        } catch (IllegalArgumentException invalidContribution) {
            result.error =
                    "Có chuyến chưa tính được chặng khách đi hoặc khoản đóng góp. Làm mới để thử"
                            + " lại.";
        } catch (IOException | RuntimeException error) {
            result.error = "Một số chuyến chưa đánh giá được đường bộ. Làm mới để thử lại.";
        } finally {
            synchronized (this) {
                clients.remove(client);
            }
        }
        return result;
    }

    /** Đọc profile một lần cho mỗi chủ xe, lỗi không xóa kết quả tuyến hợp lệ. */
    private void loadProfile(final String driverId, final int requestGeneration) {
        if (profiles.containsKey(driverId)) {
            applyProfile(driverId, profiles.get(driverId));
            return;
        }
        if (!requestedProfiles.add(driverId)) {
            return;
        }
        queryProfile(
                driverId,
                new RepositoryCallback<PublicProfile>() {

                    /** Gắn profile thật, bỏ callback cũ sau làm mới hoặc logout. */
                    @Override
                    public void onSuccess(PublicProfile profile) {
                        synchronized (ResultsViewModel.this) {
                            if (!current(requestGeneration)) {
                                return;
                            }
                            profiles.put(driverId, profile);
                            applyProfile(driverId, profile);
                            publish();
                        }
                    }

                    /** Giữ nhãn thiếu hồ sơ thay vì dữ liệu giả hoặc tải mãi. */
                    @Override
                    public void onError(Exception error) {
                        synchronized (ResultsViewModel.this) {
                            if (current(requestGeneration)) {
                                profiles.put(driverId, null);
                                publish();
                            }
                        }
                    }
                });
    }

    /** Cập nhật profile trên mọi kết quả của cùng tài xế. */
    private void applyProfile(String driverId, PublicProfile profile) {
        for (int matchIndex = 0; matchIndex < matches.size(); matchIndex = matchIndex + 1) {
            if (driverId.equals(matches.get(matchIndex).trip.getDriverId())) {
                matches.set(matchIndex, matches.get(matchIndex).withProfile(profile));
            }
        }
    }

    /** Chuyển model địa điểm sang kiểu của Routing sau bước kiểm tra dữ liệu. */
    private static Point point(com.example.grabshare.model.Place place) {
        return new Point(place.getLatitude(), place.getLongitude());
    }

    /** Kiểm tra UID hiện tại ở cả luồng UI và worker. */
    private boolean sameUser() {
        return criteria != null && criteria.userId.equals(currentUserId());
    }

    /** Điểm nối session để có thể kiểm tra đổi UID mà không đăng nhập tài khoản giả. */
    protected String currentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return null;
        } else {
            return user.getUid();
        }
    }

    /** Điểm nối nguồn đọc; bản chạy thật luôn dùng repository và server. */
    protected void queryTrips(
            long startInclusiveMilliseconds,
            long endInclusiveMilliseconds,
            RepositoryCallback<TripSearchRepository.SearchSnapshot> callback) {
        repository.searchEnRouteTrips(
                startInclusiveMilliseconds, endInclusiveMilliseconds, callback);
    }

    /** Điểm nối hồ sơ công khai, không chặn việc kiểm tra đường bộ. */
    protected void queryProfile(String driverId, RepositoryCallback<PublicProfile> callback) {
        repository.getDriverProfile(driverId, callback);
    }

    /** Điểm nối Routing cho worker; kiểm thử có thể mô phỏng lỗi quota và độ trễ. */
    protected VietnamRoutingClient createRoutingClient(SearchRequestBudget budget) {
        return VietnamRoutingClient.forSearch(budget);
    }

    /** Chỉ cho thế hệ hiện tại ghi kết quả; volatile generation bảo vệ qua executor. */
    private boolean current(int requestGeneration) {
        return requestGeneration == generation && !closed && sameUser();
    }

    /** Công bố kết quả đã kiểm tra, trạng thái tải và thông báo của lượt hiện tại. */
    private void publish() {
        if (closed || criteria == null) {
            return;
        }
        List<Match> visible;
        if (sameUser()) {
            visible = EnRouteSearch.sorted(matches, sort, System.currentTimeMillis());
        } else {
            visible = new ArrayList<Match>();
        }
        String message = notice;
        if (sameUser()) {
            if (!metadataNotice.isEmpty()) {
                message = (message + " " + metadataNotice).trim();
            }
            if (!evaluationNotice.isEmpty() && !message.contains(evaluationNotice)) {
                message = (message + " " + evaluationNotice).trim();
            }
            if (!busy && !pending.isEmpty()) {
                message = (message + " Còn chuyến chưa kiểm tra. Bấm Xem thêm để tiếp tục.").trim();
            } else {
                if (!busy && visible.isEmpty() && message.isEmpty()) {
                    message = "Chưa tìm thấy chuyến phù hợp với điều kiện đã chọn.";
                }
            }
        }
        state.postValue(new State(visible, busy, !pending.isEmpty(), sameUser(), sort, message));
    }

    /** Hủy HTTP và future cũ; token ngăn snapshot Firebase trễ khởi động lượt khác. */
    private void cancelWork() {
        generation = generation + 1;
        for (VietnamRoutingClient client : clients) {
            client.cancelCurrentRequest();
        }
        for (Future<Evaluation> future : futures) {
            future.cancel(true);
        }
        futures.clear();
    }

    /** Màn bị đóng thật sự mới giải phóng executor; xoay không đi qua onCleared. */
    @Override
    protected synchronized void onCleared() {
        closed = true;
        cancelWork();
        auth.removeAuthStateListener(authListener);
        coordinator.shutdownNow();
        workers.shutdownNow();
        super.onCleared();
    }
}
