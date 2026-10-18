package com.example.grabshare.repository.search;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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