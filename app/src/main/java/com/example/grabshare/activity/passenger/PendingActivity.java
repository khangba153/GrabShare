package com.example.grabshare.activity.passenger;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.activity.search.HomeActivity;
import com.example.grabshare.adapter.passenger.RideAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.SingleRequestDetails;
import com.example.grabshare.repository.passenger.RequestCancellationRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity hiển thị trạng thái Chờ chủ xe xác nhận (Pending) của Passenger (Thuận-2).
 */
public class PendingActivity extends AppCompatActivity {

    private ImageButton headerBack;
    private TextView headerTitle;
    private TextView statusTitle;
    private TextView statusDescription;
    private Button primaryAction;
    private Button secondaryAction;

    // View trong view_trip_summary.xml và view_map.xml
    private TextView driverName;
    private TextView driverMeta;
    private TextView tripTime;
    private TextView tripSeats;
    private TextView routeStart;
    private TextView routeEnd;
    private TextView tripPickup;
    private TextView tripCost;
    private TextView mapStartLabel;
    private TextView mapEndLabel;

    private PassengerTripsRepository tripsRepository;
    private RequestCancellationRepository cancellationRepository;
    private ListenerRegistration singleRequestListener;

    private String requestId;
    private String currentUserId;
    private boolean hasNavigated = false;
    private PassengerSessionGuard sessionGuard;
    private SingleRequestDetails currentDetails;
    private boolean cancellationInFlight;
    private boolean loadFailed;
    private Dialog cancelDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_pending);

        // Đọc requestId từ Intent Extra theo NavigationKeys.REQUEST_ID
        if (getIntent() != null) {
            requestId = getIntent().getStringExtra(NavigationKeys.REQUEST_ID);
        }

        // Kiểm tra Auth trước khi thực hiện bất kỳ thao tác hay khởi tạo listener nào
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem thông tin.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        if (savedInstanceState != null
                && !currentUserId.equals(savedInstanceState.getString("passenger_expected_uid"))) {
            finish();
            return;
        }
        sessionGuard = new PassengerSessionGuard(this);

        if (requestId == null || requestId.trim().isEmpty()) {
            Toast.makeText(this, "Yêu cầu không hợp lệ hoặc thiếu mã nhận dạng.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initRepositories();
        initViews();
        setupListeners();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            /** Back hệ thống cũng trở về trang chủ, không mở lại form đã gửi. */
            @Override
            public void handleOnBackPressed() {
                returnHome();
            }
        });
    }

    private void initRepositories() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        tripsRepository = new PassengerTripsRepository(firestore);
        cancellationRepository = new RequestCancellationRepository(firestore);
    }

    private void initViews() {
        headerBack = findViewById(R.id.header_back);
        headerTitle = findViewById(R.id.header_title);
        statusTitle = findViewById(R.id.status_title);
        statusDescription = findViewById(R.id.status_description);
        primaryAction = findViewById(R.id.primary_action);
        secondaryAction = findViewById(R.id.secondary_action);

        if (headerBack != null) {
            headerBack.setClickable(true);
            headerBack.setFocusable(true);
        }
        if (primaryAction != null) {
            primaryAction.setClickable(true);
            primaryAction.setFocusable(true);
            primaryAction.setText("Hủy yêu cầu");
        }
        if (secondaryAction != null) {
            secondaryAction.setText("Về trang chủ");
            secondaryAction.setVisibility(View.VISIBLE);
            secondaryAction.setClickable(true);
            secondaryAction.setFocusable(true);
        }

        if (headerTitle != null) {
            headerTitle.setText("Chờ xác nhận");
        }

        // View chi tiết
        driverName = findViewById(R.id.driver_name);
        driverMeta = findViewById(R.id.driver_meta);
        tripTime = findViewById(R.id.trip_time);
        tripSeats = findViewById(R.id.trip_seats);
        routeStart = findViewById(R.id.route_start);
        routeEnd = findViewById(R.id.route_end);
        tripPickup = findViewById(R.id.trip_pickup);
        tripCost = findViewById(R.id.trip_cost);
        mapStartLabel = findViewById(R.id.map_start_label);
        mapEndLabel = findViewById(R.id.map_end_label);
    }

    private void setupListeners() {
        if (secondaryAction != null) {
            secondaryAction.setOnClickListener(new View.OnClickListener() {
                /** Cho phép về trang chủ trực tiếp trong khi chờ chủ xe phản hồi. */
                @Override
                public void onClick(View view) {
                    returnHome();
                }
            });
        }
        if (headerBack != null) {
            headerBack.setOnClickListener(new View.OnClickListener() {
                /** Thoát màn trạng thái về trang chủ thay vì kết quả tìm cũ. */
                @Override
                public void onClick(View view) {
                    returnHome();
                }
            });
        }

        if (primaryAction != null) {
            primaryAction.setOnClickListener(new View.OnClickListener() {
                /** Lỗi chỉ tải lại trạng thái; yêu cầu đã hủy chỉ cho trở về trang chủ. */
                @Override
                public void onClick(View view) {
                    if (loadFailed) {
                        startListeningRequest();
                        return;
                    }
                    if (currentDetails != null
                            && currentDetails.getRequest() != null
                            && currentDetails.getTrip() != null
                            && ("CANCELLED".equals(currentDetails.getRequest().getStatus())
                                || "CANCELLED".equals(currentDetails.getTrip().getStatus()))) {
                        returnHome();
                        return;
                    }
                    showCancelConfirmDialog();
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sessionGuard != null && sessionGuard.start(currentUserId)) {
            cancellationInFlight = false;
            currentDetails = null;
            primaryAction.setEnabled(false);
            startListeningRequest();
        }
    }

    @Override
    protected void onStop() {
        if (sessionGuard != null) {
            sessionGuard.stop();
        }
        if (cancelDialog != null) {
            cancelDialog.dismiss();
            cancelDialog = null;
        }
        if (singleRequestListener != null) {
            singleRequestListener.remove();
            singleRequestListener = null;
        }
        super.onStop();
    }

    /** Giữ UID kỳ vọng qua xoay, không khôi phục thao tác hủy tự động. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("passenger_expected_uid", currentUserId);
        super.onSaveInstanceState(state);
    }

    private void startListeningRequest() {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }

        if (singleRequestListener != null) {
            singleRequestListener.remove();
            singleRequestListener = null;
        }
        loadFailed = false;
        currentDetails = null;
        statusTitle.setText("Đang tải trạng thái yêu cầu…");
        statusDescription.setText("Vui lòng chờ trong giây lát.");
        primaryAction.setText("Đang tải…");
        primaryAction.setEnabled(false);
        findViewById(R.id.pending_trip_summary).setVisibility(View.GONE);
        findViewById(R.id.pending_route_summary).setVisibility(View.GONE);

        final int sessionToken = sessionGuard.token();
        singleRequestListener = tripsRepository.listenSingleRequest(requestId, new RepositoryCallback<SingleRequestDetails>() {
            /** Chỉ xử lý cập nhật của đúng phiên và màn đang hiển thị. */
            @Override
            public void onSuccess(SingleRequestDetails details) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                if (details == null || details.getRequest() == null) {
                    showLoadFailure(new IllegalStateException("Không tìm thấy dữ liệu yêu cầu."));
                    return;
                }

                TripRequest request = details.getRequest();

                // Kiểm tra quyền truy cập chính chủ
                if (!currentUserId.equals(request.getPassengerId())) {
                    Toast.makeText(PendingActivity.this, "Bạn không có quyền truy cập yêu cầu này.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                Trip trip = details.getTrip();
                PassengerRouteText.bind(findViewById(R.id.passenger_route_summary), request, trip);
                if (trip == null) {
                    showLoadFailure(new IllegalStateException("Chưa tải được thông tin chuyến đi."));
                    return;
                }
                loadFailed = false;
                currentDetails = details;
                findViewById(R.id.pending_trip_summary).setVisibility(View.VISIBLE);
                findViewById(R.id.pending_route_summary).setVisibility(View.VISIBLE);
                String effectiveStatus = RideAdapter.RideViewHolder.resolveEffectiveStatus(request, trip);

                // Ưu tiên kiểm tra chuyến đi đã bị hủy bởi chủ xe
                if ("CANCELLED".equalsIgnoreCase(effectiveStatus)) {
                    if (trip != null && "CANCELLED".equals(trip.getStatus())) {
                        headerTitle.setText("Chuyến đã hủy");
                        statusTitle.setText("Chuyến đi đã bị hủy");
                        statusDescription.setText("Chủ xe đã hủy chuyến đi này.");
                    } else {
                        headerTitle.setText("Yêu cầu đã hủy");
                        statusTitle.setText("Yêu cầu đi nhờ đã hủy");
                        statusDescription.setText("Bạn đã hủy yêu cầu đi nhờ này.");
                    }
                    primaryAction.setText("Về trang chủ");
                    primaryAction.setEnabled(true);
                    secondaryAction.setVisibility(View.GONE);
                    bindDetailsToViews(details);
                    return;
                }

                // Chuyển đổi trạng thái khi chủ xe chấp nhận / từ chối
                if ("ACCEPTED".equalsIgnoreCase(effectiveStatus)) {
                    if (!hasNavigated) {
                        hasNavigated = true;
                        Toast.makeText(PendingActivity.this, "Chủ xe đã chấp nhận yêu cầu của bạn!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(PendingActivity.this, AcceptedActivity.class);
                        intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
                        startActivity(intent);
                        finish();
                    }
                    return;
                } else if ("REJECTED".equalsIgnoreCase(effectiveStatus)) {
                    if (!hasNavigated) {
                        hasNavigated = true;
                        Toast.makeText(PendingActivity.this, "Chủ xe đã từ chối yêu cầu.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(PendingActivity.this, RejectedActivity.class);
                        intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
                        startActivity(intent);
                        finish();
                    }
                    return;
                } else if ("COMPLETED".equalsIgnoreCase(effectiveStatus)) {
                    if (!hasNavigated) {
                        hasNavigated = true;
                        Intent intent = new Intent(PendingActivity.this, UpcomingActivity.class);
                        intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
                        startActivity(intent);
                        finish();
                    }
                    return;
                }

                // Nếu vẫn ở PENDING, cập nhật dữ liệu hiển thị
                headerTitle.setText("Chờ xác nhận");
                statusTitle.setText("Đang chờ chủ xe xác nhận");
                statusDescription.setText("Yêu cầu của bạn đã được gửi đến chủ xe.");
                primaryAction.setText("Hủy yêu cầu");
                secondaryAction.setVisibility(View.VISIBLE);
                bindDetailsToViews(details);
                primaryAction.setEnabled(!cancellationInFlight && canCancelPending());
            }

            /** Không cập nhật hoặc đóng màn thuộc phiên đã hết hiệu lực. */
            @Override
            public void onError(Exception error) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                showLoadFailure(error);
            }
        });
    }

    /** Giữ lỗi trên màn trạng thái, chỉ thử đọc lại; không gửi thêm request. */
    private void showLoadFailure(Exception error) {
        loadFailed = true;
        currentDetails = null;
        String description =
                "Chưa tải được dữ liệu. Bạn có thể thử tải lại, không cần gửi lại yêu cầu.";
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;
            if (firestoreError.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                description = "Máy chủ từ chối quyền xem yêu cầu. Vui lòng báo nhóm hỗ trợ.";
            } else {
                if (firestoreError.getCode() == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                    description = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                }
            }
        } else {
            if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                description = error.getMessage();
            }
        }
        statusTitle.setText("Không thể tải trạng thái yêu cầu");
        statusDescription.setText(description);
        primaryAction.setText("Thử tải lại");
        primaryAction.setEnabled(true);
        secondaryAction.setVisibility(View.VISIBLE);
        findViewById(R.id.pending_trip_summary).setVisibility(View.GONE);
        findViewById(R.id.pending_route_summary).setVisibility(View.GONE);
    }

    private void bindDetailsToViews(SingleRequestDetails details) {
        if (details == null) return;

        TripRequest request = details.getRequest();
        Trip trip = details.getTrip();
        PublicProfile profile = details.getDriverProfile();

        if (profile != null) {
            if (driverName != null) {
                driverName.setText(profile.getDisplayName() != null && !profile.getDisplayName().trim().isEmpty()
                        ? profile.getDisplayName().trim() : "Chủ xe");
            }
            if (driverMeta != null) {
                String vehicle = (profile.getVehicleName() != null && !profile.getVehicleName().trim().isEmpty())
                        ? profile.getVehicleName().trim() : "Xe máy";
                driverMeta.setText(vehicle);
            }
        } else {
            if (driverName != null) driverName.setText("Chủ xe");
            if (driverMeta != null) driverMeta.setText("Xe máy");
        }

        if (trip != null) {
            if (tripTime != null && trip.getDepartureAt() != null) {
                tripTime.setText(formatTimestamp(trip.getDepartureAt()));
            }

            if (tripSeats != null) {
                long seats = trip.getAvailableSeats() != null ? trip.getAvailableSeats() : 0;
                tripSeats.setText(seats + " chỗ trống");
            }

            if (routeStart != null && trip.getOrigin() != null) {
                routeStart.setText(trip.getOrigin().getName() != null ? trip.getOrigin().getName() : "Điểm đi");
            }

            if (routeEnd != null && trip.getDestination() != null) {
                routeEnd.setText(trip.getDestination().getName() != null ? trip.getDestination().getName() : "Điểm đến");
            }

            if (mapStartLabel != null && trip.getOrigin() != null) {
                mapStartLabel.setText(trip.getOrigin().getName() != null ? trip.getOrigin().getName() : "Điểm đi");
            }

            if (mapEndLabel != null && trip.getDestination() != null) {
                mapEndLabel.setText(trip.getDestination().getName() != null ? trip.getDestination().getName() : "Điểm đến");
            }

            if (tripCost != null && trip.getCostVnd() != null) {
                DecimalFormat df = new DecimalFormat("#,###");
                tripCost.setText(df.format(trip.getCostVnd()) + "đ");
            }
        }

        if (request != null && request.getPickup() != null) {
            Place pickupPlace = request.getPickup();
            if (tripPickup != null) {
                tripPickup.setText("Điểm đón: " + (pickupPlace.getName() != null ? pickupPlace.getName() : "Điểm hẹn đã chọn"));
            }
        }
    }

    private void showCancelConfirmDialog() {
        if (!sessionGuard.current(sessionGuard.token()) || cancellationInFlight || !canCancelPending()) {
            return;
        }
        if (cancelDialog != null && cancelDialog.isShowing()) {
            return;
        }
        final Dialog dialog = new Dialog(this);
        cancelDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        TextView dialogBody = dialog.findViewById(R.id.dialog_body);
        Button btnCancel = dialog.findViewById(R.id.dialog_cancel);
        Button btnConfirm = dialog.findViewById(R.id.dialog_confirm);

        if (dialogTitle != null) dialogTitle.setText("Hủy yêu cầu đi nhờ");
        if (dialogBody != null) dialogBody.setText("Bạn có chắc chắn muốn hủy yêu cầu đi nhờ này không?");

        if (btnCancel != null) {
            btnCancel.setClickable(true);
            btnCancel.setFocusable(true);
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnConfirm != null) {
            btnConfirm.setClickable(true);
            btnConfirm.setFocusable(true);
            btnConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    performCancelRequest();
                }
            });
        }

        dialog.show();
    }

    private void performCancelRequest() {
        final int sessionToken = sessionGuard.token();
        if (!sessionGuard.current(sessionToken) || cancellationInFlight || !canCancelPending()) {
            return;
        }
        cancellationInFlight = true;
        if (primaryAction != null) primaryAction.setEnabled(false);

        cancellationRepository.cancelPendingRequest(requestId, currentUserId, new RepositoryCallback<Void>() {
            /** Chỉ báo thành công và đóng màn của phiên đã yêu cầu hủy. */
            @Override
            public void onSuccess(Void unused) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                cancellationInFlight = false;
                Toast.makeText(PendingActivity.this, "Đã hủy yêu cầu thành công.", Toast.LENGTH_SHORT).show();
                returnHome();
            }

            /** Sau lỗi chỉ bật lại nút nếu dữ liệu hiện tại vẫn cho phép hủy. */
            @Override
            public void onError(Exception error) {
                if (!sessionGuard.current(sessionToken)) {
                    return;
                }
                cancellationInFlight = false;
                if (primaryAction != null) primaryAction.setEnabled(canCancelPending());
                Toast.makeText(PendingActivity.this, "Hủy yêu cầu thất bại: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Xóa các màn tìm/chi tiết cũ phía trên Home khi thoát hoặc hủy yêu cầu. */
    private void returnHome() {
        Intent homeIntent = new Intent(this, HomeActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(homeIntent);
        finish();
    }

    /** Kiểm tra dữ liệu đang hiển thị trước khi mở/xác nhận dialog; transaction vẫn đọc lại. */
    private boolean canCancelPending() {
        if (currentDetails == null || currentDetails.getRequest() == null || currentDetails.getTrip() == null) {
            return false;
        }
        TripRequest request = currentDetails.getRequest();
        Trip trip = currentDetails.getTrip();
        return currentUserId.equals(request.getPassengerId()) && "PENDING".equals(request.getStatus())
                && ("OPEN".equals(trip.getStatus()) || "FULL".equals(trip.getStatus()))
                && trip.getDepartureAt() != null && trip.getDepartureAt().toDate().after(new Date());
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Hôm nay";
        Date date = timestamp.toDate();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd/MM · HH:mm", new Locale("vi", "VN"));
        return sdf.format(date);
    }
}
