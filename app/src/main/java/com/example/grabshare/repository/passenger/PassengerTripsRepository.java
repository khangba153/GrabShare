package com.example.grabshare.repository.passenger;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository quản lý danh sách chuyến đi nhờ của Passenger với cơ chế Realtime song song cho cả requests và trips.
 */
public class PassengerTripsRepository {

    private final FirebaseFirestore firestore;

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

    public PassengerTripsRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Lắng nghe Realtime đồng thời cả danh sách Request và các Trip tương ứng.
     * Quản lý listener con cho trip, hủy toàn bộ listener khi remove.
     * @param passengerId ID tài khoản đăng nhập passengerId.
     * @param callback Nhận kết quả danh sách ghép RideItem.
     * @return ListenerRegistration hợp nhất để Activity gỡ khi Stop.
     */
    public ListenerRegistration listenPassengerTrips(final String passengerId, final RepositoryCallback<List<PassengerRideItem>> callback) {
        if (passengerId == null || passengerId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("passengerId không được rỗng."));
            }
            return null;
        }

        return new RealtimeTripsHandler(firestore, passengerId, callback);
    }

    /**
     * Quản lý Realtime cho Request và các Listener con của Trip.
     */
    private static class RealtimeTripsHandler implements ListenerRegistration {

        private final FirebaseFirestore firestore;
        private final String passengerId;
        private final RepositoryCallback<List<PassengerRideItem>> callback;

        private ListenerRegistration requestsListener;
        private final Map<String, ListenerRegistration> tripListenersMap;
        private final Map<String, Trip> tripDataMap;
        private List<TripRequest> currentRequestsList;

        public RealtimeTripsHandler(FirebaseFirestore firestore, String passengerId, RepositoryCallback<List<PassengerRideItem>> callback) {
            this.firestore = firestore;
            this.passengerId = passengerId;
            this.callback = callback;
            this.tripListenersMap = new HashMap<>();
            this.tripDataMap = new HashMap<>();
            this.currentRequestsList = new ArrayList<>();

            startListeningRequests();
        }

        private void startListeningRequests() {
            requestsListener = firestore.collection("requests")
                    .whereEqualTo("passengerId", passengerId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(QuerySnapshot querySnapshot, FirebaseFirestoreException error) {
                            if (error != null) {
                                if (callback != null) callback.onError(error);
                                return;
                            }

                            if (querySnapshot == null || querySnapshot.isEmpty()) {
                                currentRequestsList.clear();
                                cleanupUnusedTripListeners(new HashSet<String>());
                                if (callback != null) callback.onSuccess(new ArrayList<PassengerRideItem>());
                                return;
                            }

                            List<TripRequest> requestsList = new ArrayList<>();
                            Set<String> activeTripIds = new HashSet<>();

                            for (QueryDocumentSnapshot doc : querySnapshot) {
                                TripRequest request = doc.toObject(TripRequest.class);
                                request.setId(doc.getId());
                                requestsList.add(request);
                                if (request.getTripId() != null && !request.getTripId().trim().isEmpty()) {
                                    activeTripIds.add(request.getTripId());
                                }
                            }

                            currentRequestsList = requestsList;
                            cleanupUnusedTripListeners(activeTripIds);
                            syncTripListeners(activeTripIds);
                            notifyCallbackIfReady();
                        }
                    });
        }

        private void syncTripListeners(Set<String> activeTripIds) {
            for (final String tripId : activeTripIds) {
                if (!tripListenersMap.containsKey(tripId)) {
                    ListenerRegistration listener = firestore.collection("trips")
                            .document(tripId)
                            .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                                @Override
                                public void onEvent(DocumentSnapshot doc, FirebaseFirestoreException error) {
                                    if (error != null) return;
                                    if (doc != null && doc.exists()) {
                                        Trip trip = doc.toObject(Trip.class);
                                        if (trip != null) {
                                            trip.setId(doc.getId());
                                            tripDataMap.put(tripId, trip);
                                        }
                                    } else {
                                        tripDataMap.remove(tripId);
                                    }
                                    notifyCallbackIfReady();
                                }
                            });
                    tripListenersMap.put(tripId, listener);
                }
            }
        }

        private void cleanupUnusedTripListeners(Set<String> activeTripIds) {
            List<String> toRemove = new ArrayList<>();
            for (String tripId : tripListenersMap.keySet()) {
                if (!activeTripIds.contains(tripId)) {
                    toRemove.add(tripId);
                }
            }
            for (String tripId : toRemove) {
                ListenerRegistration reg = tripListenersMap.remove(tripId);
                if (reg != null) reg.remove();
                tripDataMap.remove(tripId);
            }
        }

        private void notifyCallbackIfReady() {
            List<PassengerRideItem> items = new ArrayList<>();
            for (TripRequest request : currentRequestsList) {
                Trip trip = tripDataMap.get(request.getTripId());
                items.add(new PassengerRideItem(request, trip));
            }
            if (callback != null) {
                callback.onSuccess(items);
            }
        }

        @Override
        public void remove() {
            if (requestsListener != null) {
                requestsListener.remove();
                requestsListener = null;
            }
            for (ListenerRegistration reg : tripListenersMap.values()) {
                if (reg != null) reg.remove();
            }
            tripListenersMap.clear();
            tripDataMap.clear();
            currentRequestsList.clear();
        }
    }
}
