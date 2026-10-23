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
import com.google.firebase.firestore.Source;

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
    private int loadGeneration;
    private boolean visible;

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
        visible = true;
        loadFavorites();
    }

    /** Bỏ mọi phản hồi của lượt tải cũ khi rời màn. */
    @Override
    protected void onStop() {
        visible = false;
        loadGeneration = loadGeneration + 1;
        super.onStop();
    }

    /** Chỉ cập nhật UI nếu màn còn hiện và đúng tài khoản đã bắt đầu đọc. */
    private boolean currentLoad(int generation) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return visible && generation == loadGeneration && user != null
                && user.getUid().equals(currentUserId);
    }

    private void loadFavorites() {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return;
        }

        final int generation = loadGeneration = loadGeneration + 1;
        adapter.setItems(new ArrayList<FavoriteItem>());
        showLoadingState();

        firestore.collection("favorites")
                .whereEqualTo("userId", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (!currentLoad(generation)) return;
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

                        fetchTripsForFavorites(favorites, generation);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        if (currentLoad(generation)) showErrorState();
                    }
                });
    }

    private void fetchTripsForFavorites(final List<Favorite> favorites, final int generation) {
        if (favorites.isEmpty()) {
            showEmptyState();
            return;
        }

        final FavoriteItem[] favoriteItems = new FavoriteItem[favorites.size()];
        final int total = favorites.size();
        final int[] loadedCount = {0};
        final boolean[] failed = {false};

        for (int index = 0; index < total; index++) {
            final Favorite favorite = favorites.get(index);
            final int position = index;
            final String tripId = favorite.getTripId();
            if (tripId == null || tripId.trim().isEmpty()) {
                failed[0] = true;
                finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                continue;
            }

            firestore.collection("trips").document(tripId).get(Source.SERVER)
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot tripDoc) {
                            if (!currentLoad(generation)) return;
                            if (tripDoc != null && tripDoc.exists()) {
                                final Trip trip = tripDoc.toObject(Trip.class);
                                if (trip != null) {
                                    trip.setId(tripDoc.getId());
                                }

                                String driverId = trip != null ? trip.getDriverId() : null;
                                if (driverId != null && !driverId.trim().isEmpty()) {
                                    firestore.collection("publicProfiles").document(driverId).get(Source.SERVER)
                                            .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                @Override
                                                public void onSuccess(DocumentSnapshot profileDoc) {
                                                    if (!currentLoad(generation)) return;
                                                    PublicProfile profile = null;
                                                    if (profileDoc != null && profileDoc.exists()) {
                                                        profile = profileDoc.toObject(PublicProfile.class);
                                                        if (profile != null) {
                                                            profile.setId(profileDoc.getId());
                                                        }
                                                    }
                                                    favoriteItems[position] = new FavoriteItem(favorite, trip, profile);
                                                    finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                                }
                                            })
                                            .addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(Exception e) {
                                                    if (!currentLoad(generation)) return;
                                                    favoriteItems[position] = new FavoriteItem(favorite, trip, null);
                                                    finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                                }
                                            });
                                } else {
                                    favoriteItems[position] = new FavoriteItem(favorite, trip, null);
                                    finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                }
                            } else {
                                favoriteItems[position] = new FavoriteItem(favorite, null, null);
                                finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            if (!currentLoad(generation)) return;
                            failed[0] = true;
                            finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                        }
                    });
        }
    }

    /** Giữ thứ tự createdAt dù phản hồi về đảo thứ tự; lỗi đọc trip hiển thị Retry. */
    private void finishFavoriteLoad(FavoriteItem[] items, int[] count, int total,
                                    boolean[] failed, int generation) {
        if (!currentLoad(generation)) return;
        count[0] = count[0] + 1;
        if (count[0] != total) return;
        if (failed[0]) {
            showErrorState();
            return;
        }
        List<FavoriteItem> ordered = new ArrayList<>();
        for (FavoriteItem item : items) {
            if (item != null) ordered.add(item);
        }
        showDataState(ordered);
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
