package com.example.grabshare.repository.driver;

import androidx.annotation.NonNull;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc danh sách chuyến do tài khoản hiện tại đăng.
 * <p>
 * Repository này chỉ đọc dữ liệu, không tạo hoặc cập nhật chuyến.
 */
public class DriverTripRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;

    /**
     * Khởi tạo repository với các dịch vụ Firebase đang dùng chung.
     *
     * @param firestore dịch vụ cơ sở dữ liệu Firestore
     * @param firebaseAuth dịch vụ xác thực Firebase
     */
    public DriverTripRepository(
            FirebaseFirestore firestore,
            FirebaseAuth firebaseAuth
    ) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Tải các chuyến thuộc tài khoản đang đăng nhập.
     * <p>
     * Truy vấn lọc ngay trên Firestore bằng driverId và sắp xếp
     * theo thời gian khởi hành giảm dần.
     *
     * @param callback nơi nhận danh sách chuyến hoặc lỗi thực tế
     */
    public void loadTripsForCurrentDriver(
            final RepositoryCallback<List<Trip>> callback
    ) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null
                || currentUser.getUid().trim().isEmpty()) {

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
                .orderBy(
                        "departureAt",
                        Query.Direction.DESCENDING
                )
                .get(Source.SERVER)
                .addOnSuccessListener(
                        new OnSuccessListener<>() {

                            /**
                             * Chuyển kết quả truy vấn thành danh sách Trip.
                             *
                             * @param querySnapshot kết quả Firestore trả về
                             */
                            @Override
                            public void onSuccess(
                                    QuerySnapshot querySnapshot
                            ) {
                                List<Trip> loadedTrips =
                                        new ArrayList<>();

                                for (DocumentSnapshot documentSnapshot
                                        : querySnapshot.getDocuments()) {

                                    Trip currentTrip;

                                    try {
                                        currentTrip =
                                                documentSnapshot.toObject(
                                                        Trip.class
                                                );
                                    } catch (RuntimeException exception) {
                                        callback.onError(exception);
                                        return;
                                    }

                                    if (currentTrip == null) {
                                        callback.onError(
                                                new IllegalStateException(
                                                        "Không thể đọc dữ liệu "
                                                                + "một chuyến xe."
                                                )
                                        );

                                        return;
                                    }

                                    currentTrip.setId(
                                            documentSnapshot.getId()
                                    );

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
                             *
                             * @param exception lỗi Firestore trả về
                             */
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                callback.onError(exception);
                            }
                        }
                );
    }
}
