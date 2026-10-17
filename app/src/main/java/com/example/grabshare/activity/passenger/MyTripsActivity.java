package com.example.grabshare.activity.passenger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.activity.account.LoginActivity;
import com.example.grabshare.activity.driver.DriverTripsActivity;
import com.example.grabshare.activity.search.HomeActivity;
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

import com.example.grabshare.activity.account.AccountActivity;

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
        setContentView(R.layout.screen_my_trips);

        initViews();
        initRepository();
        setupRecyclerView();

        // Kiểm tra phiên đăng nhập bằng FirebaseAuth.getInstance().getCurrentUser()
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem chuyến đi.", Toast.LENGTH_SHORT).show();
            Intent loginIntent = new Intent(this, LoginActivity.class);
            startActivity(loginIntent);
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        setupListeners();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_my_trips);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        layoutError = findViewById(R.id.layout_error);
        btnRetry = findViewById(R.id.btn_retry);
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

    private void setupListeners() {
        if (btnRetry != null) {
            btnRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startListeningTrips();
                }
            });
        }

        View roleDriver = findViewById(R.id.role_driver);
        if (roleDriver != null) {
            roleDriver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MyTripsActivity.this, DriverTripsActivity.class);
                    startActivity(intent);
                    finish();
                }
            });
        }

        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Quay về Home đang có để giữ form và tránh tạo màn trùng.
                    Intent intent = new Intent(
                            MyTripsActivity.this,
                            HomeActivity.class);

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    startActivity(intent);
                    finish();
                }
            });
        }

        View navAccount = findViewById(R.id.nav_account);
        if (navAccount != null) {
            navAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(
                            MyTripsActivity.this,
                            AccountActivity.class);

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    startActivity(intent);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null && !currentUserId.trim().isEmpty()) {
            startListeningTrips();
        }
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
        String effectiveStatus = RideAdapter.RideViewHolder.resolveEffectiveStatus(request, trip);

        Intent intent = null;
        if ("PENDING".equalsIgnoreCase(effectiveStatus)) {
            intent = new Intent(this, PendingActivity.class);
        } else if ("REJECTED".equalsIgnoreCase(effectiveStatus)) {
            intent = new Intent(this, RejectedActivity.class);
        } else if ("ACCEPTED".equalsIgnoreCase(effectiveStatus) || "FULL".equalsIgnoreCase(effectiveStatus)) {
            intent = new Intent(this, UpcomingActivity.class);
        } else if ("COMPLETED".equalsIgnoreCase(effectiveStatus)) {
            intent = new Intent(this, CompleteActivity.class);
        } else if ("CANCELLED".equalsIgnoreCase(effectiveStatus)) {
            Toast.makeText(this, "Chuyến đi này đã bị hủy.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (intent != null) {
            intent.putExtra(NavigationKeys.REQUEST_ID, requestId);
            if (request.getTripId() != null) {
                intent.putExtra(NavigationKeys.TRIP_ID, request.getTripId());
            }
            startActivity(intent);
        }
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
