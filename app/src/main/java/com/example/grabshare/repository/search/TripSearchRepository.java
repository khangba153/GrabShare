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
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

/** Lớp quản lý dữ liệu truy vấn từ Firestore cho màn hình Tìm kiếm chuyến. */
public class TripSearchRepository {

    /** Snapshot của một lượt đọc server, không cắt ngẫu nhiên 50 chuyến trước bước xếp hạng. */
    public static class SearchSnapshot {

        public final List<Trip> trips = new ArrayList<Trip>();

        public int invalidDocuments;
    }

    /** Đọc cửa sổ một giờ, gồm hai biên; dùng index status/departureAt đã có trên main. */
    public void searchEnRouteTrips(
            long startInclusive,
            long endInclusive,
            final RepositoryCallback<SearchSnapshot> callback) {
        if (startInclusive > endInclusive) {
            callback.onError(
                    new IllegalArgumentException(
                            "Khung giờ tìm đã qua. Hãy chọn lại ngày và giờ."));
            return;
        }
        firestore
                .collection("trips")
                .whereEqualTo("status", "OPEN")
                .whereGreaterThanOrEqualTo(
                        "departureAt",
                        new Timestamp(
                                startInclusive / 1000, (int) (startInclusive % 1000) * 1000000))
                .whereLessThanOrEqualTo(
                        "departureAt",
                        new Timestamp(endInclusive / 1000, (int) (endInclusive % 1000) * 1000000))
                .orderBy("departureAt", Query.Direction.ASCENDING)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<QuerySnapshot>() {

                            /** Bỏ document hỏng có thống kê, không làm hỏng toàn lượt tìm. */
                            @Override
                            public void onSuccess(QuerySnapshot snapshot) {
                                SearchSnapshot result = new SearchSnapshot();
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    try {
                                        Trip trip = document.toObject(Trip.class);
                                        if (trip == null) {
                                            result.invalidDocuments = result.invalidDocuments + 1;
                                            continue;
                                        }
                                        trip.setId(document.getId());
                                        result.trips.add(trip);
                                    } catch (RuntimeException invalid) {
                                        result.invalidDocuments = result.invalidDocuments + 1;
                                    }
                                }
                                callback.onSuccess(result);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {

                            /**
                             * Giữ lỗi server/index, không thay bằng snapshot cache rồi báo không có
                             * chuyến.
                             */
                            @Override
                            public void onFailure(Exception error) {
                                callback.onError(error);
                            }
                        });
    }

    private final FirebaseFirestore firestore;

    /** Dùng kết nối Firestore của ứng dụng cho các lượt đọc chuyến và hồ sơ công khai. */
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
    public ListenerRegistration watchOnlineTrip(
            String tripId, final RepositoryCallback<Trip> callback) {
        if (!SearchTripPolicy.validId(tripId)) {
            callback.onError(new IllegalArgumentException("Thiếu hoặc sai mã chuyến."));
            return null;
        }
        return firestore
                .collection("trips")
                .document(tripId)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        new EventListener<DocumentSnapshot>() {

                            /**
                             * Chuyển thay đổi và lỗi về caller; không xác nhận dữ liệu đang chờ
                             * ghi.
                             */
                            @Override
                            public void onEvent(
                                    DocumentSnapshot snapshot, FirebaseFirestoreException error) {
                                if (error != null) {
                                    callback.onError(error);
                                    return;
                                }
                                if (snapshot == null
                                        || snapshot.getMetadata().isFromCache()
                                        || snapshot.getMetadata().hasPendingWrites()) {
                                    callback.onError(
                                            new IllegalStateException(
                                                    "Chưa xác nhận được dữ liệu online. Kiểm tra"
                                                            + " kết nối và thử lại."));
                                    return;
                                }
                                if (!snapshot.exists()) {
                                    callback.onError(
                                            new IllegalStateException("Chuyến không còn tồn tại."));
                                    return;
                                }
                                final Trip trip;
                                try {
                                    trip = snapshot.toObject(Trip.class);
                                    if (trip != null) {
                                        trip.setId(snapshot.getId());
                                    }
                                    if (!SearchTripPolicy.validTrip(trip)) {
                                        throw new IllegalStateException(
                                                "Dữ liệu chuyến không hợp lệ.");
                                    }
                                } catch (RuntimeException invalid) {
                                    callback.onError(invalid);
                                    return;
                                }
                                callback.onSuccess(trip);
                            }
                        });
    }

    /** Đọc profile và toàn bộ rating của tài xế trên server, giữ rõ lỗi của từng phần. */
    public void getOnlineDriverInfo(
            String driverId, final RepositoryCallback<DriverInfo> callback) {
        if (!SearchTripPolicy.validId(driverId)) {
            callback.onError(new IllegalArgumentException("Thiếu mã chủ xe."));
            return;
        }
        final Task<DocumentSnapshot> profileTask =
                firestore.collection("publicProfiles").document(driverId).get(Source.SERVER);
        final Task<QuerySnapshot> ratingsTask =
                firestore
                        .collection("ratings")
                        .whereEqualTo("targetUserId", driverId)
                        .get(Source.SERVER);
        Tasks.whenAllComplete(profileTask, ratingsTask)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Task<?>>>() {

                            /** Không tạo tên, xe hoặc điểm đánh giá minh họa khi thiếu dữ liệu. */
                            @Override
                            public void onSuccess(List<Task<?>> ignored) {
                                DriverInfo driverInformation = new DriverInfo();
                                if (profileTask.isSuccessful()) {
                                    DocumentSnapshot snapshot = profileTask.getResult();
                                    try {
                                        if (snapshot.exists()) {
                                            driverInformation.profile =
                                                    snapshot.toObject(PublicProfile.class);
                                            if (driverInformation.profile != null) {
                                                driverInformation.profile.setId(snapshot.getId());
                                            }
                                        }
                                    } catch (RuntimeException invalid) {
                                        driverInformation.warning =
                                                "Hồ sơ công khai không hợp lệ. ";
                                    }
                                } else {
                                    driverInformation.warning = "Chưa tải được hồ sơ công khai. ";
                                }
                                if (ratingsTask.isSuccessful()) {
                                    long totalRatingScore = 0;
                                    driverInformation.ratingsLoaded = true;
                                    for (DocumentSnapshot snapshot : ratingsTask.getResult()) {
                                        Object rawRatingScore = snapshot.get("score");
                                        if (!(rawRatingScore instanceof Long)
                                                || (Long) rawRatingScore < 1
                                                || (Long) rawRatingScore > 5) {
                                            driverInformation.ratingsLoaded = false;
                                            break;
                                        }
                                        totalRatingScore =
                                                totalRatingScore + ((Long) rawRatingScore);
                                        driverInformation.ratingCount =
                                                driverInformation.ratingCount + 1;
                                    }
                                    if (driverInformation.ratingsLoaded
                                            && driverInformation.ratingCount > 0) {
                                        driverInformation.average =
                                                (double) totalRatingScore
                                                        / driverInformation.ratingCount;
                                    }
                                }
                                if (!driverInformation.ratingsLoaded) {
                                    driverInformation.warning =
                                            driverInformation.warning
                                                    + ("Chưa tải được đánh giá hợp lệ.");
                                }
                                callback.onSuccess(driverInformation);
                            }
                        });
    }

    /** Lấy profile công khai của tài xế. */
    public void getDriverProfile(
            String driverId, final RepositoryCallback<PublicProfile> callback) {
        if (driverId == null || driverId.isEmpty()) {
            callback.onError(new Exception("driverId rỗng"));
            return;
        }
        firestore
                .collection("publicProfiles")
                .document(driverId)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<DocumentSnapshot>() {

                            /** Ánh xạ hồ sơ công khai và giữ lỗi dữ liệu thay vì giả hồ sơ rỗng. */
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                if (documentSnapshot.exists()) {
                                    try {
                                        PublicProfile profile =
                                                documentSnapshot.toObject(PublicProfile.class);
                                        if (profile != null) {
                                            profile.setId(documentSnapshot.getId());
                                            callback.onSuccess(profile);
                                        } else {
                                            callback.onError(new Exception("Profile null"));
                                        }
                                    } catch (RuntimeException invalid) {
                                        callback.onError(invalid);
                                    }
                                } else {
                                    callback.onError(new Exception("Không tìm thấy profile"));
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {

                            /** Trả lỗi đọc hồ sơ để màn có thể cho người dùng thử lại. */
                            @Override
                            public void onFailure(Exception error) {
                                callback.onError(error);
                            }
                        });
    }
}
