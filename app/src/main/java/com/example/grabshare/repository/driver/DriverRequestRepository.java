package com.example.grabshare.repository.driver;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.DriverRequestItem;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Đọc danh sách và chi tiết yêu cầu đi nhờ dành cho chủ xe. */
public class DriverRequestRepository {
    /** Theo dõi yêu cầu của đúng chủ xe; đếm tại máy để không cần index tổng hợp mới. */
    public ListenerRegistration watchPendingCounts(
            final String driverIdentifier,
            final RepositoryCallback<Map<String, Integer>> callback) {
        return firestore.collection("requests")
                .whereEqualTo("driverId", driverIdentifier)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                    /** Chỉ công bố số lượng sau xác nhận máy chủ, không giả cache thành số không. */
                    @Override
                    public void onEvent(QuerySnapshot snapshot, FirebaseFirestoreException error) {
                        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                        if (currentUser == null || !driverIdentifier.equals(currentUser.getUid())) {
                            return;
                        }
                        if (error != null) {
                            callback.onError(error);
                            return;
                        }
                        if (snapshot == null || snapshot.getMetadata().isFromCache()
                                || snapshot.getMetadata().hasPendingWrites()) {
                            callback.onError(new IllegalStateException("Chưa xác nhận số yêu cầu."));
                            return;
                        }
                        Map<String, Integer> counts = new HashMap<String, Integer>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            String tripIdentifier = document.getString("tripId");
                            if ("PENDING".equals(document.getString("status"))
                                    && driverIdentifier.equals(document.getString("driverId"))
                                    && tripIdentifier != null && !tripIdentifier.isEmpty()) {
                                Integer previousCount = counts.get(tripIdentifier);
                                int updatedCount = 1;
                                if (previousCount != null) {
                                    updatedCount = previousCount + 1;
                                }
                                counts.put(tripIdentifier, updatedCount);
                            }
                        }
                        callback.onSuccess(counts);
                    }
                });
    }

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    /** Khởi tạo repository với các dịch vụ Firebase đang dùng. */
    public DriverRequestRepository(
            FirebaseFirestore firestore,
            FirebaseAuth firebaseAuth
    ) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    /** Tải yêu cầu của tài xế hiện tại; có thể giới hạn theo một chuyến cụ thể. */
    public void loadRequestsForCurrentDriver(
            String tripId,
            final RepositoryCallback<List<DriverRequestItem>> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(new IllegalStateException(
                    "Bạn cần đăng nhập để xem yêu cầu đi nhờ."
            ));
            return;
        }

        Query requestQuery = firestore.collection("requests")
                .whereEqualTo("driverId", currentUser.getUid());

        if (tripId != null && !tripId.trim().isEmpty()) {
            requestQuery = requestQuery.whereEqualTo("tripId", tripId.trim());
        }

        requestQuery.orderBy("createdAt", Query.Direction.DESCENDING)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshot) {
                        List<TripRequest> requests = new ArrayList<>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            TripRequest request = document.toObject(TripRequest.class);
                            if (request == null) {
                                callback.onError(new IllegalStateException(
                                        "Không thể đọc một yêu cầu đi nhờ."
                                ));
                                return;
                            }
                            request.setId(document.getId());
                            requests.add(request);
                        }
                        loadRelatedData(requests, callback);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        callback.onError(error);
                    }
                });
    }

    /** Tải chi tiết yêu cầu sau khi kiểm tra quyền sở hữu của tài xế hiện tại. */
    public void loadRequestForCurrentDriver(
            String requestId,
            final RepositoryCallback<DriverRequestItem> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(new IllegalStateException(
                    "Bạn cần đăng nhập để xem chi tiết yêu cầu."
            ));
            return;
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Mã yêu cầu không hợp lệ."));
            return;
        }

        final String driverId = currentUser.getUid();
        final String requestIdentifier = requestId.trim();
        firestore.runTransaction(new Transaction.Function<DriverRequestItem>() {
            /** Đọc yêu cầu và chuyến cùng phiên bản, không ghép trạng thái trước và sau nhận khách. */
            @Override
            public DriverRequestItem apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null || !driverId.equals(user.getUid())) {
                    throw new IllegalStateException("Phiên đăng nhập đã đổi.");
                }
                DocumentSnapshot document = transaction.get(
                        firestore.collection("requests").document(requestIdentifier));
                TripRequest request = document.toObject(TripRequest.class);
                if (!document.exists() || request == null) {
                    throw new IllegalStateException("Yêu cầu không tồn tại hoặc đã bị xóa.");
                }
                if (!driverId.equals(request.getDriverId())) {
                    throw new SecurityException("Bạn không có quyền xem yêu cầu của chuyến khác.");
                }
                if (request.getTripId() == null || request.getTripId().trim().isEmpty()
                        || request.getTripId().contains("/") || request.getPassengerId() == null
                        || request.getPassengerId().trim().isEmpty()) {
                    throw new IllegalStateException("Yêu cầu thiếu mã chuyến hoặc hành khách hợp lệ.");
                }
                DocumentSnapshot tripDocument = transaction.get(
                        firestore.collection("trips").document(request.getTripId()));
                Trip trip = tripDocument.toObject(Trip.class);
                if (!tripDocument.exists() || trip == null || !driverId.equals(trip.getDriverId())) {
                    throw new IllegalStateException("Không đọc được chuyến của chủ xe hiện tại.");
                }
                request.setId(document.getId());
                trip.setId(tripDocument.getId());
                DriverRequestItem item = new DriverRequestItem(request);
                item.setTrip(trip);
                return item;
            }
        }).addOnSuccessListener(new OnSuccessListener<DriverRequestItem>() {
            @Override
            public void onSuccess(DriverRequestItem item) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null || !driverId.equals(user.getUid())) {
                    callback.onError(new IllegalStateException("Phiên đăng nhập đã đổi."));
                    return;
                }
                loadPublicProfile(item, item.getRequest().getPassengerId(), callback);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    /** Tải chuyến và hồ sơ công khai cho toàn bộ yêu cầu theo đúng thứ tự ban đầu. */
    private void loadRelatedData(
            final List<TripRequest> requests,
            final RepositoryCallback<List<DriverRequestItem>> callback
    ) {
        if (requests.isEmpty()) {
            callback.onSuccess(new ArrayList<DriverRequestItem>());
            return;
        }

        final List<DriverRequestItem> result = new ArrayList<>();
        loadRelatedDataAtIndex(requests, 0, result, callback);
    }

    /** Xử lý lần lượt từng yêu cầu để giữ thứ tự danh sách và giới hạn độ phức tạp. */
    private void loadRelatedDataAtIndex(
            final List<TripRequest> requests,
            final int index,
            final List<DriverRequestItem> result,
            final RepositoryCallback<List<DriverRequestItem>> callback
    ) {
        if (index >= requests.size()) {
            callback.onSuccess(result);
            return;
        }

        loadRelatedDataForOne(requests.get(index), new RepositoryCallback<DriverRequestItem>() {
            @Override
            public void onSuccess(DriverRequestItem item) {
                result.add(item);
                loadRelatedDataAtIndex(requests, index + 1, result, callback);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /** Tải chuyến liên quan và hồ sơ công khai, không truy cập hồ sơ riêng tư. */
    private void loadRelatedDataForOne(
            final TripRequest request,
            final RepositoryCallback<DriverRequestItem> callback
    ) {
        String tripId = request.getTripId();
        String passengerId = request.getPassengerId();
        if (tripId == null || tripId.trim().isEmpty()
                || passengerId == null || passengerId.trim().isEmpty()) {
            callback.onError(new IllegalStateException(
                    "Yêu cầu thiếu mã chuyến hoặc mã hành khách."
            ));
            return;
        }

        final DriverRequestItem item = new DriverRequestItem(request);
        firestore.collection("trips").document(tripId)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot document) {
                        if (!document.exists()) {
                            callback.onError(new IllegalStateException(
                                    "Chuyến liên quan không còn tồn tại."
                            ));
                            return;
                        }

                        Trip trip = document.toObject(Trip.class);
                        if (trip == null) {
                            callback.onError(new IllegalStateException(
                                    "Không thể đọc dữ liệu chuyến."
                            ));
                            return;
                        }

                        trip.setId(document.getId());
                        item.setTrip(trip);
                        loadPublicProfile(item, passengerId, callback);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        callback.onError(error);
                    }
                });
    }

    /** Tải hồ sơ công khai của hành khách. Không lấy email hoặc dữ liệu tài khoản riêng tư. */
    private void loadPublicProfile(
            final DriverRequestItem item,
            String passengerId,
            final RepositoryCallback<DriverRequestItem> callback
    ) {
        firestore.collection("publicProfiles").document(passengerId)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot document) {
                        if (document.exists()) {
                            PublicProfile profile = document.toObject(PublicProfile.class);
                            if (profile != null) {
                                profile.setId(document.getId());
                                item.setPassengerProfile(profile);
                            }
                        }
                        callback.onSuccess(item);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        callback.onError(error);
                    }
                });
    }
}
