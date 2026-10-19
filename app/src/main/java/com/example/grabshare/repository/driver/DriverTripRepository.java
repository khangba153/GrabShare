package com.example.grabshare.repository.driver;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc danh sách chuyến của chủ xe và xử lý nghiệp vụ tạo chuyến mới. Tầng repository không hiển thị giao diện và không tự điều hướng Activity.
 */
public class DriverTripRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    /**
     * Khởi tạo repository với các dịch vụ Firebase đang dùng chung của ứng dụng.
     */
    public DriverTripRepository(
            FirebaseFirestore firestore,
            FirebaseAuth firebaseAuth
    ) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Tạo một ID Firestore ở phía client nhưng chưa ghi document. Activity lưu ID này để dùng lại nếu xoay màn hình hoặc phải thử lại.
     */
    public String createTripDocumentId() {
        return firestore.collection("trips").document().getId();
    }

    /**
     * Tải các chuyến thuộc tài khoản đang đăng nhập từ máy chủ. Truy vấn lọc ngay trên Firestore bằng driverId và sắp xếp theo thời gian giảm dần.
     */
    public void loadTripsForCurrentDriver(
            final RepositoryCallback<List<Trip>> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(
                    new IllegalStateException(
                            "Bạn cần đăng nhập để xem chuyến của mình."
                    )
            );
            return;
        }

        String currentDriverId = currentUser.getUid();

        firestore.collection("trips")
                .whereEqualTo("driverId", currentDriverId)
                .orderBy("departureAt", Query.Direction.DESCENDING)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<>() {
                            /**
                             * Chuyển kết quả truy vấn thành danh sách Trip.
                             */
                            @Override
                            public void onSuccess(QuerySnapshot querySnapshot) {
                                List<Trip> loadedTrips = new ArrayList<>();

                                for (DocumentSnapshot documentSnapshot
                                        : querySnapshot.getDocuments()) {
                                    Trip currentTrip;

                                    try {
                                        currentTrip = documentSnapshot.toObject(Trip.class);
                                    } catch (RuntimeException exception) {
                                        callback.onError(exception);
                                        return;
                                    }

                                    if (currentTrip == null) {
                                        callback.onError(
                                                new IllegalStateException(
                                                        "Không thể đọc dữ liệu một chuyến xe."
                                                )
                                        );
                                        return;
                                    }

                                    currentTrip.setId(documentSnapshot.getId());
                                    loadedTrips.add(currentTrip);
                                }

                                callback.onSuccess(loadedTrips);
                            }
                        }
                )
                .addOnFailureListener(
                        new OnFailureListener() {
                            /**
                             * Chuyển lỗi truy vấn về giao diện.
                             */
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                callback.onError(exception);
                            }
                        }
                );
    }

    /**
     * Kiểm tra hồ sơ của tài khoản đang đăng nhập trên máy chủ. Callback trả true khi có đủ tên xe và biển số; trả false khi thiếu dữ liệu. Lỗi đọc Firestore được trả qua onError, không bị coi nhầm là hồ sơ trống.
     */
    public void checkVehicleProfile(
            final RepositoryCallback<Boolean> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(
                    new IllegalStateException(
                            "Bạn cần đăng nhập để đăng chuyến."
                    )
            );
            return;
        }

        String currentDriverId = currentUser.getUid();

        Task<DocumentSnapshot> userTask = firestore.collection("users")
                .document(currentDriverId)
                .get(Source.SERVER);

        Task<DocumentSnapshot> publicProfileTask = firestore.collection("publicProfiles")
                .document(currentDriverId)
                .get(Source.SERVER);

        Tasks.<DocumentSnapshot>whenAllSuccess(userTask, publicProfileTask)
                .addOnSuccessListener(
                        new OnSuccessListener<>() {
                            /**
                             * Đánh giá hai document hồ sơ sau khi cả hai lần đọc thành công.
                             */
                            @Override
                            public void onSuccess(List<DocumentSnapshot> documents) {
                                if (documents.size() != 2) {
                                    callback.onError(
                                            new IllegalStateException(
                                                    "Kết quả đọc hồ sơ không hợp lệ."
                                            )
                                    );
                                    return;
                                }

                                boolean hasVehicleProfile = hasCompleteVehicleProfile(
                                        documents.get(0),
                                        documents.get(1)
                                );

                                callback.onSuccess(hasVehicleProfile);
                            }
                        }
                )
                .addOnFailureListener(
                        new OnFailureListener() {
                            /**
                             * Trả lỗi khi không đọc được một trong hai hồ sơ từ máy chủ.
                             */
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                callback.onError(exception);
                            }
                        }
                );
    }

    /**
     * Tạo chuyến mới bằng transaction để xác minh hồ sơ và chống ghi trùng ID. Transaction luôn cần máy chủ; lỗi mạng hoặc lỗi quyền sẽ đi qua onError.
     */
    public void createTrip(
            final String tripId,
            final Place origin,
            final Place destination,
            final Timestamp departureAt,
            final Long costVnd,
            final String note,
            final RepositoryCallback<Void> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(
                    new IllegalStateException(
                            "Bạn cần đăng nhập để đăng chuyến."
                    )
            );
            return;
        }

        if (tripId == null || tripId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("ID chuyến không hợp lệ.")
            );
            return;
        }

        if (isValidPlace(origin) || isValidPlace(destination)) {
            callback.onError(
                    new IllegalArgumentException(
                            "Điểm đi và điểm đến phải có tên cùng tọa độ hợp lệ."
                    )
            );
            return;
        }

        if (sameCoordinates(origin, destination)) {
            callback.onError(
                    new IllegalArgumentException(
                            "Điểm đi và điểm đến không được trùng nhau."
                    )
            );
            return;
        }

        if (departureAt == null
                || departureAt.toDate().getTime() <= System.currentTimeMillis()) {
            callback.onError(
                    new IllegalArgumentException(
                            "Thời gian khởi hành phải ở trong tương lai."
                    )
            );
            return;
        }

        if (costVnd == null || costVnd < 0L) {
            callback.onError(
                    new IllegalArgumentException(
                            "Chi phí phải là số nguyên không âm."
                    )
            );
            return;
        }

        final String driverId = currentUser.getUid();
        final String safeNote = note == null ? "" : note.trim();

        final DocumentReference tripReference = firestore.collection("trips")
                .document(tripId);

        final DocumentReference userReference = firestore.collection("users")
                .document(driverId);

        final DocumentReference publicProfileReference = firestore.collection("publicProfiles")
                .document(driverId);

        final Map<String, Object> tripData = buildTripData(
                driverId,
                origin,
                destination,
                departureAt,
                costVnd,
                safeNote
        );

        firestore.runTransaction(
                        new Transaction.Function<Void>() {
                            /**
                             * Đọc hồ sơ và document đích trước khi thực hiện bất kỳ thao tác ghi nào. Nếu cùng ID đã được tạo với đúng dữ liệu này thì lần thử lại được coi là thành công.
                             */
                            @Override
                            public Void apply(@NonNull Transaction transaction)
                                    throws FirebaseFirestoreException {
                                DocumentSnapshot userSnapshot = transaction.get(userReference);
                                DocumentSnapshot publicProfileSnapshot = transaction.get(
                                        publicProfileReference
                                );
                                DocumentSnapshot existingTripSnapshot = transaction.get(tripReference);

                                if (!hasCompleteVehicleProfile(
                                        userSnapshot,
                                        publicProfileSnapshot
                                )) {
                                    throw new IllegalStateException(
                                            "Hồ sơ xe chưa có đủ tên xe và biển số."
                                    );
                                }

                                if (existingTripSnapshot.exists()) {
                                    boolean sameTrip = isSameTrip(
                                            existingTripSnapshot,
                                            driverId,
                                            origin,
                                            destination,
                                            departureAt,
                                            costVnd,
                                            safeNote
                                    );

                                    if (sameTrip) {
                                        return null;
                                    }

                                    throw new IllegalStateException(
                                            "ID chuyến này đã tồn tại với dữ liệu khác. "
                                                    + "Hãy quay về danh sách chuyến trước khi đăng lại."
                                    );
                                }

                                transaction.set(tripReference, tripData);
                                return null;
                            }
                        }
                )
                .addOnSuccessListener(
                        new OnSuccessListener<>() {
                            /**
                             * Báo thành công sau khi Firestore xác nhận transaction.
                             */
                            @Override
                            public void onSuccess(Void ignored) {
                                callback.onSuccess(null);
                            }
                        }
                )
                .addOnFailureListener(
                        new OnFailureListener() {
                            /**
                             * Trả lỗi transaction, bao gồm mất mạng hoặc bị Rules từ chối.
                             */
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                callback.onError(exception);
                            }
                        }
                );
    }

    /**
     * Đọc một chuyến từ máy chủ để quản lý, không giới hạn trạng thái chuyến.
     * Quyền sở hữu luôn được xác minh lại từ dữ liệu Firestore.
     */
    public void loadTripForManagement(
            final String tripId,
            final RepositoryCallback<Trip> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(
                    new IllegalStateException(
                            "Bạn cần đăng nhập để quản lý chuyến."
                    )
            );
            return;
        }

        if (tripId == null || tripId.trim().isEmpty()) {
            callback.onError(
                    new IllegalArgumentException("ID chuyến không hợp lệ.")
            );
            return;
        }

        final String currentDriverId = currentUser.getUid();
        firestore.collection("trips")
                .document(tripId.trim())
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError(
                                    new IllegalStateException(
                                            "Chuyến không tồn tại hoặc đã bị xóa."
                                    )
                            );
                            return;
                        }

                        String ownerId = snapshot.getString("driverId");
                        if (!currentDriverId.equals(ownerId)) {
                            callback.onError(
                                    new SecurityException(
                                            "Bạn không có quyền quản lý chuyến này."
                                    )
                            );
                            return;
                        }

                        Trip trip;
                        try {
                            trip = snapshot.toObject(Trip.class);
                        } catch (RuntimeException exception) {
                            callback.onError(exception);
                            return;
                        }

                        if (trip == null) {
                            callback.onError(
                                    new IllegalStateException(
                                            "Không thể đọc dữ liệu chuyến."
                                    )
                            );
                            return;
                        }

                        trip.setId(snapshot.getId());
                        callback.onSuccess(trip);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        callback.onError(exception);
                    }
                });
    }

    /**
     * Đọc một chuyến từ máy chủ và chỉ cho mở màn hình sửa nếu người hiện tại
     * là chủ chuyến, chuyến đang OPEN và chưa đến giờ khởi hành.
     */
    public void loadTripForEdit(
            final String tripId,
            final RepositoryCallback<Trip> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(new IllegalStateException("Bạn cần đăng nhập để sửa chuyến."));
            return;
        }
        if (tripId == null || tripId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("ID chuyến không hợp lệ."));
            return;
        }

        final String currentDriverId = currentUser.getUid();
        firestore.collection("trips")
                .document(tripId)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError(new IllegalStateException("Chuyến không tồn tại hoặc đã bị xóa."));
                            return;
                        }

                        String ownerId = snapshot.getString("driverId");
                        if (!currentDriverId.equals(ownerId)) {
                            callback.onError(new IllegalStateException("Bạn không có quyền sửa chuyến của người khác."));
                            return;
                        }

                        String status = snapshot.getString("status");
                        if ("FULL".equals(status)) {
                            callback.onError(new IllegalStateException("Chuyến đã FULL nên không thể sửa tuyến, giờ hoặc chi phí."));
                            return;
                        }
                        if (!"OPEN".equals(status)) {
                            callback.onError(new IllegalStateException("Chỉ chuyến OPEN mới được chỉnh sửa."));
                            return;
                        }

                        Timestamp departureAt = snapshot.getTimestamp("departureAt");
                        if (departureAt == null
                                || departureAt.toDate().getTime() <= System.currentTimeMillis()) {
                            callback.onError(new IllegalStateException("Chuyến đã quá giờ khởi hành, không thể chỉnh sửa."));
                            return;
                        }

                        Trip trip;
                        try {
                            trip = snapshot.toObject(Trip.class);
                        } catch (RuntimeException exception) {
                            callback.onError(exception);
                            return;
                        }
                        if (trip == null) {
                            callback.onError(new IllegalStateException("Không thể đọc dữ liệu chuyến."));
                            return;
                        }
                        trip.setId(snapshot.getId());
                        callback.onSuccess(trip);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        callback.onError(exception);
                    }
                });
    }

    /**
     * Cập nhật đúng các trường được phép. Transaction đọc lại chuyến trước khi ghi,
     * vì trạng thái có thể đổi từ OPEN sang FULL trong lúc người dùng đang sửa form.
     */
    public void updateTrip(
            final String tripId,
            final Place origin,
            final Place destination,
            final Timestamp departureAt,
            final Long costVnd,
            final String note,
            final RepositoryCallback<Void> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            callback.onError(new IllegalStateException("Bạn cần đăng nhập để sửa chuyến."));
            return;
        }
        if (tripId == null || tripId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("ID chuyến không hợp lệ."));
            return;
        }
        // Trong repository hiện tại, isValidPlace(...) trả true khi Place không hợp lệ.
        if (isValidPlace(origin) || isValidPlace(destination)) {
            callback.onError(new IllegalArgumentException(
                    "Điểm đi và điểm đến phải được chọn từ danh sách địa điểm hợp lệ."
            ));
            return;
        }
        if (sameCoordinates(origin, destination)) {
            callback.onError(new IllegalArgumentException(
                    "Điểm đi và điểm đến không được trùng tọa độ."
            ));
            return;
        }
        if (departureAt == null
                || departureAt.toDate().getTime() <= System.currentTimeMillis()) {
            callback.onError(new IllegalArgumentException(
                    "Ngày và giờ khởi hành phải ở trong tương lai."
            ));
            return;
        }
        if (costVnd == null || costVnd < 0L) {
            callback.onError(new IllegalArgumentException(
                    "Chi phí phải là số nguyên không âm."
            ));
            return;
        }

        final String currentDriverId = currentUser.getUid();
        final String safeNote = note == null ? "" : note.trim();
        final DocumentReference tripReference = firestore.collection("trips").document(tripId);

        firestore.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction)
                    throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(tripReference);

                if (!snapshot.exists()) {
                    throw new FirebaseFirestoreException(
                            "Chuyến không tồn tại hoặc đã bị xóa.",
                            FirebaseFirestoreException.Code.NOT_FOUND
                    );
                }

                String ownerId = snapshot.getString("driverId");
                if (!currentDriverId.equals(ownerId)) {
                    throw new FirebaseFirestoreException(
                            "Bạn không có quyền sửa chuyến của người khác.",
                            FirebaseFirestoreException.Code.PERMISSION_DENIED
                    );
                }

                String status = snapshot.getString("status");
                if ("FULL".equals(status)) {
                    throw new FirebaseFirestoreException(
                            "Chuyến đã FULL nên không thể sửa tuyến, giờ hoặc chi phí.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }
                if (!"OPEN".equals(status)) {
                    throw new FirebaseFirestoreException(
                            "Chuyến không còn ở trạng thái OPEN nên không thể sửa.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }

                Timestamp oldDepartureAt = snapshot.getTimestamp("departureAt");
                if (oldDepartureAt == null
                        || oldDepartureAt.toDate().getTime() <= System.currentTimeMillis()) {
                    throw new FirebaseFirestoreException(
                            "Chuyến đã quá giờ khởi hành, không thể chỉnh sửa.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    );
                }

                // Không dùng set() với cả model Trip: chỉ ghi đúng 6 field trong hợp đồng task.
                Map<String, Object> updates = new HashMap<>();
                updates.put("origin", buildPlaceData(origin));
                updates.put("destination", buildPlaceData(destination));
                updates.put("departureAt", departureAt);
                updates.put("costVnd", costVnd);
                updates.put("note", safeNote);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(tripReference, updates);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                callback.onSuccess(null);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                callback.onError(exception);
            }
        });
    }

    /**
     * Tạo map có đúng schema Place mà Firestore Rules yêu cầu.
     */
    private Map<String, Object> buildPlaceData(Place place) {
        Map<String, Object> placeData = new HashMap<>();
        placeData.put("name", place.getName().trim());
        placeData.put("latitude", place.getLatitude());
        placeData.put("longitude", place.getLongitude());
        return placeData;
    }

    /**
     * Tạo dữ liệu chuyến theo schema dùng chung; không đưa biển số xe vào trips.
     */
    private Map<String, Object> buildTripData(
            String driverId,
            Place origin,
            Place destination,
            Timestamp departureAt,
            Long costVnd,
            String note
    ) {
        Map<String, Object> tripData = new HashMap<>();
        tripData.put("driverId", driverId);
        tripData.put("origin", buildPlaceData(origin));
        tripData.put("destination", buildPlaceData(destination));
        tripData.put("departureAt", departureAt);
        tripData.put("costVnd", costVnd);
        tripData.put("note", note);
        tripData.put("status", "OPEN");
        tripData.put("availableSeats", 1L);
        tripData.put("acceptedRequestId", "");
        tripData.put("createdAt", FieldValue.serverTimestamp());
        tripData.put("updatedAt", FieldValue.serverTimestamp());
        return tripData;
    }

    /**
     * Kiểm tra hai document hồ sơ có đủ tên xe và biển số dạng chuỗi không rỗng.
     */
    private boolean hasCompleteVehicleProfile(
            DocumentSnapshot userSnapshot,
            DocumentSnapshot publicProfileSnapshot
    ) {
        if (userSnapshot == null
                || publicProfileSnapshot == null
                || !userSnapshot.exists()
                || !publicProfileSnapshot.exists()) {
            return false;
        }

        Object vehiclePlateValue = userSnapshot.get("vehiclePlate");
        Object vehicleNameValue = publicProfileSnapshot.get("vehicleName");

        boolean hasVehiclePlate = vehiclePlateValue instanceof String
                && !((String) vehiclePlateValue).trim().isEmpty();

        boolean hasVehicleName = vehicleNameValue instanceof String
                && !((String) vehicleNameValue).trim().isEmpty();

        return hasVehiclePlate && hasVehicleName;
    }

    /**
     * Kiểm tra tên và tọa độ của một Place có hợp lệ hay không.
     */
    private boolean isValidPlace(Place place) {
        if (place == null
                || place.getName() == null
                || place.getName().trim().isEmpty()
                || place.getLatitude() == null
                || place.getLongitude() == null) {
            return true;
        }

        double latitude = place.getLatitude();
        double longitude = place.getLongitude();

        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            return true;
        }

        return !(latitude >= -90.0)
                || !(latitude <= 90.0)
                || !(longitude >= -180.0)
                || !(longitude <= 180.0);
    }

    /**
     * So sánh tọa độ hai địa điểm theo đúng điều kiện khác nhau của schema.
     */
    private boolean sameCoordinates(Place first, Place second) {
        return Double.compare(
                first.getLatitude(),
                second.getLatitude()
        ) == 0 && Double.compare(
                first.getLongitude(),
                second.getLongitude()
        ) == 0;
    }

    /**
     * Xác định document đã tồn tại có phải chính là chuyến đang được thử đăng lại hay không. Không ghi đè document đã có và không coi một chuyến khác là thành công.
     */
    private boolean isSameTrip(
            DocumentSnapshot snapshot,
            String driverId,
            Place origin,
            Place destination,
            Timestamp departureAt,
            Long costVnd,
            String note
    ) {
        String storedDriverId = snapshot.getString("driverId");
        String storedStatus = snapshot.getString("status");
        String storedNote = snapshot.getString("note");
        String storedAcceptedRequestId = snapshot.getString("acceptedRequestId");
        Long storedCost = snapshot.getLong("costVnd");
        Long storedAvailableSeats = snapshot.getLong("availableSeats");
        Timestamp storedDepartureAt = snapshot.getTimestamp("departureAt");

        if (!driverId.equals(storedDriverId)
                || !"OPEN".equals(storedStatus)
                || !safeString(note).equals(safeString(storedNote))
                || !safeString(storedAcceptedRequestId).isEmpty()
                || storedCost == null
                || storedCost.longValue() != costVnd.longValue()
                || storedAvailableSeats == null
                || storedAvailableSeats != 1L
                || storedDepartureAt == null
                || !storedDepartureAt.equals(departureAt)) {
            return false;
        }

        Object originValue = snapshot.get("origin");
        Object destinationValue = snapshot.get("destination");

        if (!(originValue instanceof Map)
                || !(destinationValue instanceof Map)) {
            return false;
        }

        return mapMatchesPlace((Map<?, ?>) originValue, origin)
                && mapMatchesPlace((Map<?, ?>) destinationValue, destination);
    }

    /**
     * So sánh map địa điểm trong Firestore với Place đang đăng.
     */
    private boolean mapMatchesPlace(
            Map<?, ?> storedPlace,
            Place expectedPlace
    ) {
        Object storedName = storedPlace.get("name");
        Object storedLatitude = storedPlace.get("latitude");
        Object storedLongitude = storedPlace.get("longitude");

        if (!(storedName instanceof String)
                || !(storedLatitude instanceof Number)
                || !(storedLongitude instanceof Number)) {
            return false;
        }

        String expectedName = expectedPlace.getName().trim();
        double expectedLatitude = expectedPlace.getLatitude();
        double expectedLongitude = expectedPlace.getLongitude();

        return expectedName.equals(storedName)
                && Double.compare(
                ((Number) storedLatitude).doubleValue(),
                expectedLatitude
        ) == 0
                && Double.compare(
                ((Number) storedLongitude).doubleValue(),
                expectedLongitude
        ) == 0;
    }

    /**
     * Chuẩn hóa chuỗi có thể null để so sánh với các field chuỗi tùy chọn.
     */
    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
