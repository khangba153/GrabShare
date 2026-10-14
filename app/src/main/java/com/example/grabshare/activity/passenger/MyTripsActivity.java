package com.example.grabshare.activity.passenger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.adapter.passenger.RideAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.passenger.PassengerTripsRepository;
import com.example.grabshare.repository.passenger.PassengerTripsRepository.PassengerRideItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * Activity hiển thị danh sách tất cả chuyến đi nhờ của Hành khách (Tab MyTrips).
 */
public class MyTripsActivity extends AppCompatActivity implements RideAdapter.OnRideClickListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private View layoutError;
    private Button btnRetry;

    private RideAdapter adapter;
    private PassengerTripsRepository tripsRepository;
    private ListenerRegistration tripsListener;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int layoutResId = getResources().getIdentifier("screen_my_trips", "layout", getPackageName());
        if (layoutResId != 0) {
            setContentView(layoutResId);
        }

        initViews();
        initRepository();
        setupRecyclerView();

        // Xử lý lấy ID người dùng linh hoạt (Auth, Intent Extra hoặc Demo Fallback)
        resolveCurrentUserId();

        if (btnRetry != null) {
            btnRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startListeningTrips();
                }
            });
        }
    }

    /**
     * Xác định UID người dùng: từ Firebase Auth, Intent Extra của Dev Launcher hoặc UID mặc định.
     */
    private void resolveCurrentUserId() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid() != null && !currentUser.getUid().trim().isEmpty()) {
            currentUserId = currentUser.getUid();
            return;
        }

        // Lấy từ Intent Extras do công cụ Dev Launcher truyền qua
        if (getIntent() != null && getIntent().getExtras() != null) {
            Bundle extras = getIntent().getExtras();
            if (extras.containsKey("USER_ID")) {
                currentUserId = extras.getString("USER_ID");
            } else if (extras.containsKey("userId")) {
                currentUserId = extras.getString("userId");
            } else if (extras.containsKey("user_id")) {
                currentUserId = extras.getString("user_id");
            } else if (extras.containsKey("uid")) {
                currentUserId = extras.getString("uid");
            } else if (extras.containsKey("USER_UID")) {
                currentUserId = extras.getString("USER_UID");
            }
        }

        // Fallback mặc định cho Dev Launcher tool khi chưa đăng nhập Firebase
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            currentUserId = "user-demo-1";
        }
    }

    private View findViewByName(String idName) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        return id != 0 ? findViewById(id) : null;
    }

    private void initViews() {
        recyclerView = (RecyclerView) findViewByName("recycler_my_trips");
        if (recyclerView == null) recyclerView = (RecyclerView) findViewByName("recycler_view");

        progressBar = (ProgressBar) findViewByName("progress_bar");
        tvEmptyState = (TextView) findViewByName("tv_empty_state");
        layoutError = findViewByName("layout_error");
        btnRetry = (Button) findViewByName("btn_retry");
    }

    private void initRepository() {
        tripsRepository = new PassengerTripsRepository(FirebaseFirestore.getInstance());
    }

    private void setupRecyclerView() {
        adapter = new RideAdapter(this, this);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startListeningTrips();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (tripsListener != null) {
            tripsListener.remove();
            tripsListener = null;
        }
    }

    private void startListeningTrips() {
        showLoadingState();

        if (tripsListener != null) {
            tripsListener.remove();
        }

        tripsListener = tripsRepository.listenPassengerTrips(currentUserId, new RepositoryCallback<List<PassengerRideItem>>() {
            @Override
            public void onSuccess(List<PassengerRideItem> result) {
                if (result == null || result.isEmpty()) {
                    showEmptyState();
                } else {
                    showDataState(result);
                }
            }

            @Override
            public void onError(Exception e) {
                showErrorState();
            }
        });
    }

    @Override
    public void onRideClick(PassengerRideItem rideItem) {
        if (rideItem == null || rideItem.getRequest() == null) {
            return;
        }

        TripRequest request = rideItem.getRequest();
        Trip trip = rideItem.getTrip();

        if (trip == null) {
            Toast.makeText(this, "Chuyến đi này không còn tồn tại.", Toast.LENGTH_SHORT).show();
            return;
        }

        String requestId = request.getId();
        String effectiveStatus = resolveStatus(request, trip);

        String targetActivityClass = null;
        if ("PENDING".equalsIgnoreCase(effectiveStatus)) {
            targetActivityClass = "com.example.grabshare.activity.passenger.PendingActivity";
        } else if ("REJECTED".equalsIgnoreCase(effectiveStatus)) {
            targetActivityClass = "com.example.grabshare.activity.passenger.RejectedActivity";
        } else if ("ACCEPTED".equalsIgnoreCase(effectiveStatus) || "FULL".equalsIgnoreCase(effectiveStatus)) {
            targetActivityClass = "com.example.grabshare.activity.passenger.UpcomingActivity";
        } else if ("COMPLETED".equalsIgnoreCase(effectiveStatus)) {
            targetActivityClass = "com.example.grabshare.activity.passenger.CompleteActivity";
        } else if ("CANCELLED".equalsIgnoreCase(effectiveStatus)) {
            Toast.makeText(this, "Chuyến đi này đã bị hủy.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetActivityClass != null) {
            Intent intent = new Intent();
            intent.setClassName(this, targetActivityClass);
            intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Màn hình chưa được khởi tạo.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String resolveStatus(TripRequest request, Trip trip) {
        if ("CANCELLED".equalsIgnoreCase(trip.getStatus())) {
            return "CANCELLED";
        }
        return request.getStatus();
    }

    private void showLoadingState() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
    }

    private void showDataState(List<PassengerRideItem> items) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setItems(items);
        }
    }

    private void showEmptyState() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.VISIBLE);
    }

    private void showErrorState() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
    }
}