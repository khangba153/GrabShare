package com.example.grabshare.repository.passenger;

import androidx.annotation.Nullable;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Đọc các yêu cầu đi nhờ của UID hiện tại và ghép chuyến/hồ sơ để hiển thị. */
public final class PassengerTripsRepository {
    public static final int TAB_UPCOMING = 0;
    public static final int TAB_PENDING = 1;
    public static final int TAB_HISTORY = 2;

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_UPCOMING = "UPCOMING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_CANCELLED = "CANCELLED";
    public static final String STATE_UNAVAILABLE = "UNAVAILABLE";
    public static final String STATE_INCONSISTENT = "INCONSISTENT";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    /** Dùng instance Firebase của ứng dụng, không gắn UID hoặc dữ liệu giả vào repository. */
    public PassengerTripsRepository(FirebaseFirestore firestore, FirebaseAuth firebaseAuth) {
        if (firestore == null || firebaseAuth == null) {
            throw new IllegalArgumentException("FirebaseFirestore và FirebaseAuth không được null.");
        }
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    /** Query request theo passengerId và ghép từng trip/profile; không query all rồi lọc UID. */
    public void loadTripsForCurrentPassenger(final RepositoryCallback<LoadResult> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback không được null.");
        }

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.getUid() == null
                || currentUser.getUid().trim().isEmpty()) {
            callback.onError(new IllegalStateException("Bạn cần đăng nhập để xem yêu cầu đi nhờ."));
            return;
        }

        final String uid = currentUser.getUid();
        firestore.collection("requests")
                .whereEqualTo("passengerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    /** Nhận kết quả truy vấn request đã giới hạn theo UID hiện tại. */
                    @Override
                    public void onComplete(Task<QuerySnapshot> task) {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            Exception error = task.getException();
                            if (error == null) {
                                error = new IllegalStateException("Không tải được danh sách yêu cầu.");
                            }
                            callback.onError(error);
                            return;
                        }

                        QuerySnapshot snapshot = task.getResult();
                        List<DocumentSnapshot> documents = snapshot.getDocuments();
                        boolean fromCache = snapshot.getMetadata().isFromCache();
                        if (documents.isEmpty()) {
                            callback.onSuccess(new LoadResult(
                                    Collections.<RideItem>emptyList(), fromCache));
                            return;
                        }

                        final LoadCollector collector = new LoadCollector(
                                documents.size(), fromCache, callback);
                        for (int i = 0; i < documents.size(); i++) {
                            DocumentSnapshot requestDocument = documents.get(i);
                            TripRequest request = requestDocument.toObject(TripRequest.class);
                            if (request == null) {
                                request = new TripRequest();
                            }
                            request.setId(requestDocument.getId());
                            loadTripAndProfile(i, request, collector);
                        }
                    }
                });
    }

    /** Đọc đúng trip được request tham chiếu; thiếu trip vẫn trả một dòng không khả dụng. */
    private void loadTripAndProfile(final int index, final TripRequest request,
                                    final LoadCollector collector) {
        String tripId = request.getTripId();
        if (tripId == null || tripId.trim().isEmpty()) {
            collector.onItemLoaded(index, new RideItem(request, null, null, false), false);
            return;
        }

        firestore.collection("trips").document(tripId).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    /** Ghép chuyến theo ID; profile được đọc theo driverId từ chính chuyến đó. */
                    @Override
                    public void onComplete(Task<DocumentSnapshot> task) {
                        if (!task.isSuccessful()) {
                            Exception error = task.getException();
                            if (error == null) {
                                error = new IllegalStateException(
                                        "Không đọc được chuyến liên quan đến yêu cầu.");
                            }
                            collector.onItemError(error);
                            return;
                        }
                        if (task.getResult() == null) {
                            collector.onItemError(new IllegalStateException(
                                    "Không nhận được kết quả khi đọc chuyến liên quan."));
                            return;
                        }
                        if (!task.getResult().exists()) {
                            collector.onItemLoaded(index,
                                    new RideItem(request, null, null, false), false);
                            return;
                        }

                        DocumentSnapshot document = task.getResult();
                        Trip trip = document.toObject(Trip.class);
                        if (trip == null) {
                            collector.onItemLoaded(index,
                                    new RideItem(request, null, null,
                                            document.getMetadata().isFromCache()),
                                    document.getMetadata().isFromCache());
                            return;
                        }
                        trip.setId(document.getId());
                        final boolean tripFromCache = document.getMetadata().isFromCache();

                        String driverId = trip.getDriverId();
                        if (driverId == null || driverId.trim().isEmpty()) {
                            collector.onItemLoaded(index,
                                    new RideItem(request, trip, null, tripFromCache), tripFromCache);
                            return;
                        }

                        firestore.collection("publicProfiles").document(driverId).get()
                                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    /** Profile thiếu/lỗi không làm mất chuyến hoặc tạo tên mẫu. */
                                    @Override
                                    public void onComplete(Task<DocumentSnapshot> profileTask) {
                                        PublicProfile profile = null;
                                        boolean profileFromCache = false;
                                        if (profileTask.isSuccessful()
                                                && profileTask.getResult() != null
                                                && profileTask.getResult().exists()) {
                                            DocumentSnapshot profileDocument = profileTask.getResult();
                                            profile = profileDocument.toObject(PublicProfile.class);
                                            if (profile != null) {
                                                profile.setId(profileDocument.getId());
                                            }
                                            profileFromCache = profileDocument.getMetadata().isFromCache();
                                        }
                                        boolean fromCache = tripFromCache || profileFromCache;
                                        collector.onItemLoaded(index,
                                                new RideItem(request, trip, profile, fromCache),
                                                fromCache);
                                    }
                                });
                    }
                });
    }

    /** Một dòng kết quả đã ghép request, trip và hồ sơ công khai nếu có. */
    public static final class RideItem {
        private final TripRequest request;
        @Nullable private final Trip trip;
        @Nullable private final PublicProfile profile;
        private final boolean fromCache;

        /** Tạo dữ liệu hiển thị; không suy diễn trạng thái hoàn thành theo giờ thiết bị. */
        public RideItem(TripRequest request, @Nullable Trip trip,
                        @Nullable PublicProfile profile, boolean fromCache) {
            this.request = request;
            this.trip = trip;
            this.profile = profile;
            this.fromCache = fromCache;
        }

        /** Lấy yêu cầu và ID document thật từ Firestore. */
        public TripRequest getRequest() { return request; }

        /** Lấy chuyến liên quan; null khi không còn đọc được chuyến. */
        @Nullable public Trip getTrip() { return trip; }

        /** Lấy hồ sơ công khai; null khi hồ sơ thiếu hoặc không tải được. */
        @Nullable public PublicProfile getProfile() { return profile; }

        /** Cho biết dữ liệu ghép có phần nào được đọc từ cache Firestore hay không. */
        public boolean isFromCache() { return fromCache; }

        /** Ưu tiên trip CANCELLED, sau đó mới xét trạng thái request và pointer. */
        public String getStateCode() {
            String requestState = normalize(request == null ? null : request.getStatus());
            String tripState = normalize(trip == null ? null : trip.getStatus());

            if ("CANCELLED".equals(tripState) || "CANCELLED".equals(requestState)) {
                return STATE_CANCELLED;
            }
            if (trip == null) {
                return STATE_UNAVAILABLE;
            }
            if ("PENDING".equals(requestState)) {
                return STATE_PENDING;
            }
            if ("REJECTED".equals(requestState)) {
                return STATE_REJECTED;
            }
            if ("ACCEPTED".equals(requestState) && pointerMatches()) {
                if ("COMPLETED".equals(tripState)) {
                    return STATE_COMPLETED;
                }
                if ("FULL".equals(tripState)) {
                    return STATE_UPCOMING;
                }
            }
            return STATE_INCONSISTENT;
        }

        /** Chọn nhóm tab theo state đã ghép, không theo tên Activity đang mở. */
        public int getTabGroup() {
            String requestState = normalize(request == null ? null : request.getStatus());
            String state = getStateCode();

            if (STATE_CANCELLED.equals(state) || STATE_REJECTED.equals(state)
                    || STATE_COMPLETED.equals(state)) {
                return TAB_HISTORY;
            }
            if (STATE_PENDING.equals(state)) {
                return TAB_PENDING;
            }
            if (STATE_UPCOMING.equals(state)) {
                return TAB_UPCOMING;
            }
            if (STATE_UNAVAILABLE.equals(state)) {
                // Giữ dòng thiếu trip trong nhóm gần nhất theo request, nhưng gắn nhãn không khả dụng.
                if ("PENDING".equals(requestState)) return TAB_PENDING;
                if ("ACCEPTED".equals(requestState)) return TAB_UPCOMING;
            }
            // State lệch pointer/status không được đưa vào nhóm Sắp đi như thể đã xác nhận.
            return TAB_HISTORY;
        }

        /** Nhãn tiếng Việt cho adapter; trạng thái trip hủy luôn được ưu tiên. */
        public String getDisplayStatus() {
            String state = getStateCode();
            if (STATE_CANCELLED.equals(state)) {
                return trip != null && "CANCELLED".equals(normalize(trip.getStatus()))
                        ? "Chuyến đã bị hủy" : "Bạn đã hủy yêu cầu";
            }
            if (STATE_UNAVAILABLE.equals(state)) return "Chuyến không khả dụng";
            if (STATE_PENDING.equals(state)) return "Đang chờ chủ xe phản hồi";
            if (STATE_REJECTED.equals(state)) return "Yêu cầu bị từ chối";
            if (STATE_UPCOMING.equals(state)) return "Đã được nhận · Sắp đi";
            if (STATE_COMPLETED.equals(state)) return "Đã hoàn thành";
            return "Trạng thái chưa khớp · hãy tải lại";
        }

        /** Xác minh cả request ID lẫn pointer của trip trước khi nhận FULL/COMPLETED. */
        private boolean pointerMatches() {
            return request != null && trip != null
                    && request.getId() != null
                    && request.getId().equals(trip.getAcceptedRequestId());
        }
    }

    /** Kết quả danh sách kèm thông tin liệu query có dùng cache Firestore hay không. */
    public static final class LoadResult {
        private final List<RideItem> items;
        private final boolean fromCache;

        /** Đóng gói kết quả đã đọc cùng trạng thái nguồn dữ liệu. */
        public LoadResult(List<RideItem> items, boolean fromCache) {
            this.items = new ArrayList<>(items);
            this.fromCache = fromCache;
        }

        /** Trả danh sách theo thứ tự request createdAt giảm dần. */
        public List<RideItem> getItems() { return new ArrayList<>(items); }

        /** Hiển thị nhãn dữ liệu cũ nếu Firestore trả kết quả từ cache. */
        public boolean isFromCache() { return fromCache; }
    }

    /** Gom các lần đọc phụ theo đúng chỉ số request và gọi callback đúng một lần. */
    private static final class LoadCollector {
        private final List<RideItem> items;
        private final RepositoryCallback<LoadResult> callback;
        private int remaining;
        private boolean fromCache;
        private boolean completed;

        /** Khởi tạo bộ gom trước khi bắt đầu đọc trip/profile. */
        private LoadCollector(int count, boolean initialFromCache,
                              RepositoryCallback<LoadResult> callback) {
            items = new ArrayList<>(Collections.nCopies(count, (RideItem) null));
            remaining = count;
            fromCache = initialFromCache;
            this.callback = callback;
        }

        /** Ghi kết quả của một request, giữ thứ tự ban đầu và chỉ trả kết quả cuối một lần. */
        private void onItemLoaded(int index, RideItem item, boolean itemFromCache) {
            LoadResult finalResult = null;
            synchronized (this) {
                if (completed) return;
                items.set(index, item);
                fromCache = fromCache || itemFromCache;
                remaining--;
                if (remaining == 0) {
                    completed = true;
                    List<RideItem> completeItems = new ArrayList<>();
                    for (RideItem loadedItem : items) {
                        if (loadedItem != null) completeItems.add(loadedItem);
                    }
                    finalResult = new LoadResult(completeItems, fromCache);
                }
            }
            if (finalResult != null) callback.onSuccess(finalResult);
        }

        /** Kết thúc bằng lỗi đọc trip thay vì nhầm thành document bị thiếu; callback chỉ chạy một lần. */
        private void onItemError(Exception error) {
            synchronized (this) {
                if (completed) return;
                completed = true;
            }
            callback.onError(error);
        }
    }

    /** Chuẩn hóa state để so sánh không phân biệt chữ hoa/thường hoặc khoảng trắng. */
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
