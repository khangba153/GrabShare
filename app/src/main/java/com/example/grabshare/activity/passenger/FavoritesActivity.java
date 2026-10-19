package com.example.grabshare.activity.passenger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.activity.account.AccountActivity;
import com.example.grabshare.activity.account.LoginActivity;
import com.example.grabshare.activity.search.HomeActivity;
import com.example.grabshare.activity.search.TripDetailActivity;
import com.example.grabshare.adapter.passenger.FavoriteAdapter;
import com.example.grabshare.adapter.passenger.FavoriteAdapter.FavoriteItem;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.model.Favorite;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity hiển thị danh sách các chuyến đi yêu thích đã lưu của Hành khách (Thuận-7).
 */
public class FavoritesActivity extends AppCompatActivity implements FavoriteAdapter.OnFavoriteClickListener {

    private ImageButton headerBack;
    private TextView headerTitle;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private View layoutError;
    private Button btnRetry;

    private FavoriteAdapter adapter;
    private FirebaseFirestore firestore;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_favorites);

        firestore = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem danh sách yêu thích.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        initViews();
        setupRecyclerView();
        setupListeners();
    }

    private void initViews() {
        headerBack = findViewById(R.id.header_back);
        headerTitle = findViewById(R.id.header_title);

        recyclerView = findViewById(R.id.recycler_favorites);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        layoutError = findViewById(R.id.layout_error);
        btnRetry = findViewById(R.id.btn_retry);

        if (headerBack != null) {
            headerBack.setClickable(true);
            headerBack.setFocusable(true);
        }

        if (headerTitle != null) {
            headerTitle.setText("Chuyến yêu thích");
        }
    }

    private void setupRecyclerView() {
        adapter = new FavoriteAdapter(this, this);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }
    }

    private void setupListeners() {
        if (headerBack != null) {
            headerBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnRetry != null) {
            btnRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadFavorites();
                }
            });
        }

        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(FavoritesActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }
            });
        }

        View navTrips = findViewById(R.id.nav_trips);
        if (navTrips != null) {
            navTrips.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(FavoritesActivity.this, MyTripsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
                    Intent intent = new Intent(FavoritesActivity.this, AccountActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadFavorites();
    }

    private void loadFavorites() {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return;
        }

        showLoadingState();

        firestore.collection("favorites")
                .whereEqualTo("userId", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (querySnapshot == null || querySnapshot.isEmpty()) {
                            showEmptyState();
                            return;
                        }

                        List<Favorite> favorites = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            Favorite favorite = doc.toObject(Favorite.class);
                            favorite.setId(doc.getId());
                            favorites.add(favorite);
                        }

                        fetchTripsForFavorites(favorites);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showErrorState();
                    }
                });
    }

    private void fetchTripsForFavorites(final List<Favorite> favorites) {
        if (favorites.isEmpty()) {
            showEmptyState();
            return;
        }

        final List<FavoriteItem> favoriteItems = new ArrayList<>();
        final int total = favorites.size();
        final int[] loadedCount = {0};

        for (final Favorite favorite : favorites) {
            final String tripId = favorite.getTripId();
            if (tripId == null || tripId.trim().isEmpty()) {
                loadedCount[0]++;
                if (loadedCount[0] == total) {
                    showDataState(favoriteItems);
                }
                continue;
            }

            firestore.collection("trips").document(tripId).get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot tripDoc) {
                            if (tripDoc != null && tripDoc.exists()) {
                                final Trip trip = tripDoc.toObject(Trip.class);
                                if (trip != null) {
                                    trip.setId(tripDoc.getId());
                                }

                                String driverId = trip != null ? trip.getDriverId() : null;
                                if (driverId != null && !driverId.trim().isEmpty()) {
                                    firestore.collection("publicProfiles").document(driverId).get()
                                            .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                @Override
                                                public void onSuccess(DocumentSnapshot profileDoc) {
                                                    PublicProfile profile = null;
                                                    if (profileDoc != null && profileDoc.exists()) {
                                                        profile = profileDoc.toObject(PublicProfile.class);
                                                        if (profile != null) {
                                                            profile.setId(profileDoc.getId());
                                                        }
                                                    }
                                                    favoriteItems.add(new FavoriteItem(favorite, trip, profile));
                                                    loadedCount[0]++;
                                                    if (loadedCount[0] == total) {
                                                        showDataState(favoriteItems);
                                                    }
                                                }
                                            })
                                            .addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(Exception e) {
                                                    favoriteItems.add(new FavoriteItem(favorite, trip, null));
                                                    loadedCount[0]++;
                                                    if (loadedCount[0] == total) {
                                                        showDataState(favoriteItems);
                                                    }
                                                }
                                            });
                                } else {
                                    favoriteItems.add(new FavoriteItem(favorite, trip, null));
                                    loadedCount[0]++;
                                    if (loadedCount[0] == total) {
                                        showDataState(favoriteItems);
                                    }
                                }
                            } else {
                                favoriteItems.add(new FavoriteItem(favorite, null, null));
                                loadedCount[0]++;
                                if (loadedCount[0] == total) {
                                    showDataState(favoriteItems);
                                }
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            favoriteItems.add(new FavoriteItem(favorite, null, null));
                            loadedCount[0]++;
                            if (loadedCount[0] == total) {
                                showDataState(favoriteItems);
                            }
                        }
                    });
        }
    }

    @Override
    public void onFavoriteClick(FavoriteItem favoriteItem) {
        if (favoriteItem == null || favoriteItem.getTrip() == null) {
            Toast.makeText(this, "Chuyến đi không khả dụng.", Toast.LENGTH_SHORT).show();
            return;
        }

        String tripId = favoriteItem.getTrip().getId();
        if (tripId != null && !tripId.trim().isEmpty()) {
            Intent intent = new Intent(this, TripDetailActivity.class);
            intent.putExtra(NavigationKeys.TRIP_ID, tripId);
            startActivity(intent);
        }
    }

    private void showLoadingState() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
    }

    private void showDataState(List<FavoriteItem> items) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
        if (items == null || items.isEmpty()) {
            if (tvEmptyState != null) tvEmptyState.setVisibility(View.VISIBLE);
            if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        } else {
            if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
            if (recyclerView != null) {
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setItems(items);
            }
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
