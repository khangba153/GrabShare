package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class dữ liệu kết nối Firestore chuyên trách các chuyến đi nhờ dành cho Passenger.
 */
public class PassengerTripsRepository {

    private final FirebaseFirestore firestore;

    /**
     * Dớp dữ liệu kết hợp giữa Yêu cầu (TripRequest) và Chuyến đi (Trip) tương ứng.
     */
    public static class PassengerRideItem {
        private final TripRequest request;
        private final Trip trip;

        public PassengerRideItem(TripRequest request, Trip trip) {
            this.request = request;
            this.trip = trip;
        }

        public TripRequest getRequest() {
            return request;
        }

        public Trip getTrip() {
            return trip;
        }
    }

    /**
     * Khởi tạo Repository với FirebaseFirestore instance dùng chung.
     * @param firestore Đối tượng kết nối Firestore.
     */
    public PassengerTripsRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Lắng nghe Realtime danh sách yêu cầu đi nhờ của một passenger theo UID.
     * Query trực tiếp passengerId == userId và sắp xếp createdAt DESC (không lọc bằng Java).
     * @param passengerId ID người dùng vai trò hành khách.
     * @param callback Callback trả về danh sách PassengerRideItem hoặc lỗi.
     * @return ListenerRegistration để unregister khi Destroy/Stop Activity.
     */
    public ListenerRegistration listenPassengerTrips(final String passengerId, final RepositoryCallback<List<PassengerRideItem>> callback) {
        if (passengerId == null || passengerId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("passengerId không được rỗng."));
            }
            return null;
        }

        return firestore.collection("requests")
                .whereEqualTo("passengerId", passengerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(QuerySnapshot querySnapshot, FirebaseFirestoreException error) {
                        if (error != null) {
                            if (callback != null) {
                                callback.onError(error);
                            }
                            return;
                        }

                        if (querySnapshot == null || querySnapshot.isEmpty()) {
                            if (callback != null) {
                                callback.onSuccess(new ArrayList<PassengerRideItem>());
                            }
                            return;
                        }

                        final List<TripRequest> requestsList = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            TripRequest request = doc.toObject(TripRequest.class);
                            request.setId(doc.getId());
                            requestsList.add(request);
                        }

                        // Sau khi đọc danh sách requests, tiến hành đọc chi tiết từng trip tương ứng
                        fetchTripsForRequests(requestsList, callback);
                    }
                });
    }

    /**
     * Hàm phụ trợ nạp danh sách Trip tương ứng với từng TripRequest.
     * @param requestsList Danh sách các yêu cầu đi nhờ.
     * @param callback Callback kết quả trả về UI Thread.
     */
    private void fetchTripsForRequests(final List<TripRequest> requestsList, final RepositoryCallback<List<PassengerRideItem>> callback) {
        if (requestsList.isEmpty()) {
            if (callback != null) {
                callback.onSuccess(new ArrayList<PassengerRideItem>());
            }
            return;
        }

        final Map<String, Trip> tripMap = new HashMap<>();
        final int totalTrips = requestsList.size();
        final int[] completedCount = new int[]{0};

        for (final TripRequest request : requestsList) {
            String tripId = request.getTripId();
            if (tripId == null || tripId.trim().isEmpty()) {
                completedCount[0]++;
                checkAndCompleteFetch(requestsList, tripMap, completedCount[0], totalTrips, callback);
                continue;
            }

            firestore.collection("trips")
                    .document(tripId)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot doc) {
                            if (doc != null && doc.exists()) {
                                Trip trip = doc.toObject(Trip.class);
                                if (trip != null) {
                                    trip.setId(doc.getId());
                                    tripMap.put(doc.getId(), trip);
                                }
                            }
                            completedCount[0]++;
                            checkAndCompleteFetch(requestsList, tripMap, completedCount[0], totalTrips, callback);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            // Nếu lỗi nạp 1 trip, vẫn tiếp tục gom các trip còn lại, không để crash
                            completedCount[0]++;
                            checkAndCompleteFetch(requestsList, tripMap, completedCount[0], totalTrips, callback);
                        }
                    });
        }
    }

    /**
     * Kiểm tra điều kiện hoàn thành việc nạp danh sách chuyến đi.
     */
    private void checkAndCompleteFetch(List<TripRequest> requestsList, Map<String, Trip> tripMap, int currentCount, int totalCount, RepositoryCallback<List<PassengerRideItem>> callback) {
        if (currentCount >= totalCount) {
            List<PassengerRideItem> items = new ArrayList<>();
            for (TripRequest request : requestsList) {
                Trip trip = tripMap.get(request.getTripId());
                // trip có thể null nếu trip bị xóa hoặc không tìm thấy
                items.add(new PassengerRideItem(request, trip));
            }
            if (callback != null) {
                callback.onSuccess(items);
            }
        }
    }
}