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
import com.example.grabshare.presentation.MainNavigation;
import com.example.grabshare.presentation.TripListFilter;
import com.example.grabshare.activity.account.LoginActivity;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Activity hiển thị danh sách tất cả chuyến đi nhờ của Hành khách (Tab MyTrips).
 */
public class MyTripsActivity extends AppCompatActivity implements RideAdapter.OnRideClickListener {
    private static final String EXPECTED_UID = "my_trips_expected_uid";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private View layoutError;
    private Button btnRetry;

    private RideAdapter adapter;
    private PassengerTripsRepository tripsRepository;
    private ListenerRegistration tripsListener;
    private String currentUserId;
    private PassengerSessionGuard sessionGuard;
    private int loadGeneration;
    private final List<PassengerRideItem> loadedTrips = new ArrayList<>();
    private final TripListFilter listFilter = new TripListFilter(
            new String[]{"Tất cả", "Chờ xác nhận", "Đã được nhận", "Hoàn thành", "Đã hủy", "Bị từ chối"},
            new String[]{"", "PENDING", "ACCEPTED", "COMPLETED", "CANCELLED", "REJECTED"});

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
        if (savedInstanceState != null) {
            String savedUserId = savedInstanceState.getString(EXPECTED_UID);
            if (!currentUserId.equals(savedUserId)) {
                finish();
                return;
            }
        }
        sessionGuard = new PassengerSessionGuard(this);

        setupListeners();
        listFilter.restore(savedInstanceState);
        updateFilterLabel();
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
                public void onClick(View view) {
                    startListeningTrips();
                }
            });
        }

        MainNavigation.bind(this);
        MainNavigation.bindNotifications(this, R.id.header_action);
        findViewById(R.id.trip_list_filter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listFilter.show(MyTripsActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        updateFilterLabel();
                        applyListFilter();
                    }
                });
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sessionGuard != null && sessionGuard.start(currentUserId)) {
            startListeningTrips();
        }
    }

    @Override
    protected void onStop() {
        if (sessionGuard != null) sessionGuard.stop();
        loadGeneration = loadGeneration + 1;
        if (tripsListener != null) {
            tripsListener.remove();
            tripsListener = null;
        }
        super.onStop();
    }

    /** Giữ màn gắn với đúng UID khi xoay hoặc Android tạo lại Activity. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(EXPECTED_UID, currentUserId);
        listFilter.save(state);
        super.onSaveInstanceState(state);
    }

    private void startListeningTrips() {
        if (sessionGuard == null || !sessionGuard.current(sessionGuard.token())) return;
        final int sessionToken = sessionGuard.token();
        loadGeneration = loadGeneration + 1;
        final int generation = loadGeneration;
        showLoadingState();

        if (tripsListener != null) {
            tripsListener.remove();
        }

        tripsListener = tripsRepository.listenPassengerTrips(currentUserId, new RepositoryCallback<List<PassengerRideItem>>() {
            @Override
            public void onSuccess(List<PassengerRideItem> result) {
                if (!sessionGuard.current(sessionToken) || generation != loadGeneration) return;
                findViewById(R.id.trip_list_filter).setEnabled(true);
                loadedTrips.clear();
                if (result != null) {
                    loadedTrips.addAll(result);
                }
                applyListFilter();
            }

            @Override
            public void onError(Exception exception) {
                if (!sessionGuard.current(sessionToken) || generation != loadGeneration) return;
                showErrorState();
            }
        });
    }

    @Override
    public void onRideClick(PassengerRideItem rideItem) {
        if (sessionGuard == null || !sessionGuard.current(sessionGuard.token())) return;
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
            intent = new Intent(this, CancelledRideActivity.class);
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
        findViewById(R.id.trip_list_filter).setEnabled(false);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
    }

    /** Lọc bản đã tải, giữ từng request_id và không thay đổi trạng thái trên server. */
    private void applyListFilter() {
        List<PassengerRideItem> visibleTrips = new ArrayList<>();
        for (PassengerRideItem item : loadedTrips) {
            Trip trip = item.getTrip();
            com.google.firebase.Timestamp departure = null;
            if (trip != null) {
                departure = trip.getDepartureAt();
            }
            String status = RideAdapter.RideViewHolder.resolveEffectiveStatus(item.getRequest(), trip);
            if (listFilter.matches(status, departure)) {
                visibleTrips.add(item);
            }
        }
        Collections.sort(visibleTrips, new Comparator<PassengerRideItem>() {
            @Override
            public int compare(PassengerRideItem first, PassengerRideItem second) {
                com.google.firebase.Timestamp firstTime = null;
                com.google.firebase.Timestamp secondTime = null;
                if (first.getTrip() != null) {
                    firstTime = first.getTrip().getDepartureAt();
                }
                if (second.getTrip() != null) {
                    secondTime = second.getTrip().getDepartureAt();
                }
                return listFilter.compare(firstTime, secondTime);
            }
        });
        if (visibleTrips.isEmpty()) {
            tvEmptyState.setText("Không có chuyến phù hợp bộ lọc. Chọn Bộ lọc → Đặt lại để xem tất cả.");
            showEmptyState();
        } else {
            showDataState(visibleTrips);
        }
    }

    private void updateFilterLabel() {
        ((TextView) findViewById(R.id.trip_list_filter)).setText(listFilter.summary());
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
        findViewById(R.id.trip_list_filter).setEnabled(false);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
    }
}
