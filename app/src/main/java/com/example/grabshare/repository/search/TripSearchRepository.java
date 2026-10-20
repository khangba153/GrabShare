package com.example.grabshare.repository.search;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Lớp quản lý dữ liệu truy vấn từ Firestore cho màn hình Tìm kiếm chuyến.
 */
public class TripSearchRepository {
    /** Snapshot của một lượt đọc server, không cắt ngẫu nhiên 50 chuyến trước bước xếp hạng. */
    public static class SearchSnapshot {
        public final List<Trip> trips = new ArrayList<>();
        public int invalidDocuments;
    }

    /** Đọc một lần khung ngày rộng, dùng index status/departureAt đã có trên main. */
    public void searchEnRouteTrips(long startInclusive, long endExclusive,
                                  final RepositoryCallback<SearchSnapshot> callback) {
        if (startInclusive >= endExclusive) {
            callback.onError(new IllegalArgumentException("Ngày tìm đã qua. Hãy chọn lại ngày."));
            return;
        }
        firestore.collection("trips").whereEqualTo("status", "OPEN")
                .whereGreaterThanOrEqualTo("departureAt", new Timestamp(startInclusive / 1000,
                        (int) (startInclusive % 1000) * 1000000))
                .whereLessThan("departureAt", new Timestamp(endExclusive / 1000, 0))
                .orderBy("departureAt", Query.Direction.ASCENDING)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    /** Bỏ document hỏng có thống kê, không làm hỏng toàn lượt tìm. */
                    @Override public void onSuccess(QuerySnapshot snapshot) {
                        SearchSnapshot result = new SearchSnapshot();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                Trip trip = document.toObject(Trip.class);
                                if (trip == null) { result.invalidDocuments++; continue; }
                                trip.setId(document.getId());
                                result.trips.add(trip);
                            } catch (RuntimeException invalid) { result.invalidDocuments++; }
                        }
                        callback.onSuccess(result);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    /** Giữ lỗi server/index, không thay bằng snapshot cache rồi báo không có chuyến. */
                    @Override public void onFailure(Exception error) { callback.onError(error); }
                });
    }
    private final FirebaseFirestore firestore;

    public TripSearchRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    /** Thông tin công khai; lỗi đọc rating không bị biến thành chưa có đánh giá. */
    public static class DriverInfo {
        public PublicProfile profile;
        public double average;
        public int ratingCount;
        public boolean ratingsLoaded;
        public String warning = "";
    }

    /** Theo dõi chuyến; chỉ xác nhận dữ liệu server, không dùng snapshot cache cho nút gửi. */
    public ListenerRegistration watchOnlineTrip(String tripId, final RepositoryCallback<Trip> callback) {
        if (!SearchTripPolicy.validId(tripId)) {
            callback.onError(new IllegalArgumentException("Thiếu hoặc sai mã chuyến."));
            return null;
        }
        return firestore.collection("trips").document(tripId)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<DocumentSnapshot>() {
                    /** Chuyển thay đổi và lỗi về caller; không xác nhận dữ liệu đang chờ ghi. */
                    @Override public void onEvent(DocumentSnapshot snapshot, FirebaseFirestoreException error) {
                        if (error != null) { callback.onError(error); return; }
                        if (snapshot == null || snapshot.getMetadata().isFromCache()
                                || snapshot.getMetadata().hasPendingWrites()) {
                            callback.onError(new IllegalStateException(
                                    "Chưa xác nhận được dữ liệu online. Kiểm tra kết nối và thử lại."));
                            return;
                        }
                        if (!snapshot.exists()) {
                            callback.onError(new IllegalStateException("Chuyến không còn tồn tại."));
                            return;
                        }
                        final Trip trip;
                        try {
                            trip = snapshot.toObject(Trip.class);
                            if (trip != null) trip.setId(snapshot.getId());
                            if (!SearchTripPolicy.validTrip(trip)) {
                                throw new IllegalStateException("Dữ liệu chuyến không hợp lệ.");
                            }
                        } catch (RuntimeException invalid) { callback.onError(invalid); return; }
                        callback.onSuccess(trip);
                    }
                });
    }

    /** Đọc profile và toàn bộ rating của tài xế trên server, giữ rõ lỗi của từng phần. */
    public void getOnlineDriverInfo(String driverId, final RepositoryCallback<DriverInfo> callback) {
        if (!SearchTripPolicy.validId(driverId)) {
            callback.onError(new IllegalArgumentException("Thiếu mã chủ xe."));
            return;
        }
        final Task<DocumentSnapshot> profileTask = firestore.collection("publicProfiles")
                .document(driverId).get(Source.SERVER);
        final Task<QuerySnapshot> ratingsTask = firestore.collection("ratings")
                .whereEqualTo("targetUserId", driverId).get(Source.SERVER);
        Tasks.whenAllComplete(profileTask, ratingsTask).addOnSuccessListener(
                new OnSuccessListener<List<Task<?>>>() {
                    /** Không tạo tên, xe hoặc điểm đánh giá minh họa khi thiếu dữ liệu. */
                    @Override public void onSuccess(List<Task<?>> ignored) {
                        DriverInfo info = new DriverInfo();
                        if (profileTask.isSuccessful()) {
                            DocumentSnapshot snapshot = profileTask.getResult();
                            try {
                                if (snapshot.exists()) {
                                    info.profile = snapshot.toObject(PublicProfile.class);
                                    if (info.profile != null) info.profile.setId(snapshot.getId());
                                }
                            } catch (RuntimeException invalid) {
                                info.warning = "Hồ sơ công khai không hợp lệ. ";
                            }
                        } else info.warning = "Chưa tải được hồ sơ công khai. ";
                        if (ratingsTask.isSuccessful()) {
                            long total = 0;
                            info.ratingsLoaded = true;
                            for (DocumentSnapshot snapshot : ratingsTask.getResult()) {
                                Object raw = snapshot.get("score");
                                if (!(raw instanceof Long) || (Long) raw < 1 || (Long) raw > 5) {
                                    info.ratingsLoaded = false;
                                    break;
                                }
                                total += (Long) raw;
                                info.ratingCount++;
                            }
                            if (info.ratingsLoaded && info.ratingCount > 0) {
                                info.average = (double) total / info.ratingCount;
                            }
                        }
                        if (!info.ratingsLoaded) info.warning += "Chưa tải được đánh giá hợp lệ.";
                        callback.onSuccess(info);
                    }
                });
    }

    /**
     * Lấy profile công khai của tài xế.
     */
    public void getDriverProfile(String driverId, final RepositoryCallback<PublicProfile> callback) {
        if (driverId == null || driverId.isEmpty()) {
            callback.onError(new Exception("driverId rỗng"));
            return;
        }

        firestore.collection("publicProfiles").document(driverId)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            try {
                                PublicProfile profile = documentSnapshot.toObject(PublicProfile.class);
                                if (profile != null) {
                                    profile.setId(documentSnapshot.getId());
                                    callback.onSuccess(profile);
                                } else callback.onError(new Exception("Profile null"));
                            } catch (RuntimeException invalid) { callback.onError(invalid); }
                        } else {
                            callback.onError(new Exception("Không tìm thấy profile"));
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        callback.onError(e);
                    }
                });
    }
}
