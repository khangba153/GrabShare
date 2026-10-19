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
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp quản lý dữ liệu truy vấn từ Firestore cho màn hình Tìm kiếm chuyến.
 */
public class TripSearchRepository {
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService;

    public TripSearchRepository() {
        firestore = FirebaseFirestore.getInstance();
        executorService = Executors.newSingleThreadExecutor();
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
     * Dữ liệu trả về gồm chuyến đi và thông tin profile của chủ xe.
     */
    public static class TripWithProfile {
        public Trip trip;
        public PublicProfile profile;
        public double distanceStart;
        public double distanceEnd;
    }

    /**
     * Lấy danh sách chuyến xe phù hợp.
     * @param departureTimeMillis Thời gian khởi hành mong muốn.
     * @param callback Nhận danh sách chuyến xe.
     */
    public void searchOpenTrips(long departureTimeMillis, final RepositoryCallback<List<Trip>> callback) {
        // Lấy thời điểm muộn hơn giữa giờ được chọn và giờ hiện tại
        long queryTime = Math.max(departureTimeMillis, System.currentTimeMillis());
        Timestamp departureTimestamp = new Timestamp(queryTime / 1000, 0);

        // Tính cận trên của ngày tìm kiếm (23:59:59 của ngày queryTime)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(queryTime);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        Timestamp endOfDayTimestamp = new Timestamp(cal.getTimeInMillis() / 1000, 0);

        firestore.collection("trips")
                .whereEqualTo("status", "OPEN")
                .whereGreaterThanOrEqualTo("departureAt", departureTimestamp)
                .whereLessThanOrEqualTo("departureAt", endOfDayTimestamp)
                .orderBy("departureAt", Query.Direction.ASCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        List<Trip> trips = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Trip trip = doc.toObject(Trip.class);
                            // Lọc thêm chuyến có availableSeats > 0 trước khi trả về
                            if (trip != null && trip.getAvailableSeats() != null && trip.getAvailableSeats() > 0) {
                                trip.setId(doc.getId());
                                trips.add(trip);
                            }
                        }
                        callback.onSuccess(trips);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        callback.onError(e);
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
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            PublicProfile profile = documentSnapshot.toObject(PublicProfile.class);
                            if (profile != null) {
                                profile.setId(documentSnapshot.getId());
                                callback.onSuccess(profile);
                            } else {
                                callback.onError(new Exception("Profile null"));
                            }
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
