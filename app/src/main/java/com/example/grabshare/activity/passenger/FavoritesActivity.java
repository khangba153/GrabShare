package com.example.grabshare.activity.passenger;

import android.content.Intent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.passenger.FavoriteRepository;
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
    private PassengerSessionGuard sessionGuard;
    private int loadGeneration;
    private boolean visible;

    /** Kiểm tra đăng nhập trước khi tạo danh sách gắn với UID hiện tại. */
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
        sessionGuard = new PassengerSessionGuard(this);

        initViews();
        setupRecyclerView();
        setupListeners();
    }

    /** Gắn các view của màn yêu thích sau khi layout đã được nạp. */
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

    /** Hiển thị danh sách theo thứ tự đã trả về từ truy vấn favorites. */
    private void setupRecyclerView() {
        adapter = new FavoriteAdapter(this, this);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }
    }

    /** Nối Retry, điều hướng và nút quay lại với màn hiện tại. */
    private void setupListeners() {
        if (headerBack != null) {
            headerBack.setOnClickListener(new View.OnClickListener() {
                /** Quay về màn trước mà không thay đổi favorite. */
                @Override
                public void onClick(View view) {
                    finish();
                }
            });
        }

        if (btnRetry != null) {
            btnRetry.setOnClickListener(new View.OnClickListener() {
                /** Bắt đầu lượt đọc server mới sau lỗi. */
                @Override
                public void onClick(View view) {
                    loadFavorites();
                }
            });
        }

        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(new View.OnClickListener() {
                /** Chuyển về trang chủ theo điều hướng hiện có. */
                @Override
                public void onClick(View view) {
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
                /** Mở danh sách chuyến của hành khách. */
                @Override
                public void onClick(View view) {
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
                /** Mở tài khoản, để lifecycle vô hiệu các callback tải cũ. */
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(FavoritesActivity.this, AccountActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            });
        }
    }

    /** Mỗi lần màn hiện lại đều đọc dữ liệu server cho đúng UID. */
    @Override
    protected void onStart() {
        super.onStart();
        if (sessionGuard != null && sessionGuard.start(currentUserId)) {
            visible = true;
            loadFavorites();
        }
    }

    /** Bỏ mọi phản hồi của lượt tải cũ khi rời màn. */
    @Override
    protected void onStop() {
        visible = false;
        loadGeneration = loadGeneration + 1;
        if (sessionGuard != null) sessionGuard.stop();
        super.onStop();
    }

    /** Chỉ cập nhật UI nếu màn còn hiện và đúng tài khoản đã bắt đầu đọc. */
    private boolean currentLoad(int generation) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return visible && generation == loadGeneration && sessionGuard != null
                && sessionGuard.current(sessionGuard.token()) && user != null
                && user.getUid().equals(currentUserId);
    }

    /** Tạo thế hệ mới để các callback của lượt tải cũ không ghi đè UI. */
    private void loadFavorites() {
        if (currentUserId == null || currentUserId.trim().isEmpty() || sessionGuard == null
                || !sessionGuard.current(sessionGuard.token())) {
            return;
        }

        loadGeneration = loadGeneration + 1;
        final int generation = loadGeneration;
        adapter.setItems(new ArrayList<FavoriteItem>());
        showLoadingState();

        firestore.collection("favorites")
                .whereEqualTo("userId", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    /** Chỉ xử lý kết quả server của đúng thế hệ tải. */
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshot) {
                        if (!currentLoad(generation)) return;
                        if (querySnapshot == null) {
                            showErrorState();
                            return;
                        }
                        if (querySnapshot.isEmpty()) {
                            showEmptyState();
                            return;
                        }

                        List<Favorite> favorites = new ArrayList<>();
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Favorite favorite;
                            try {
                                favorite = document.toObject(Favorite.class);
                            } catch (RuntimeException conversionError) {
                                showErrorState();
                                return;
                            }
                            if (favorite == null) {
                                showErrorState();
                                return;
                            }
                            favorite.setId(document.getId());
                            favorites.add(favorite);
                        }

                        fetchTripsForFavorites(favorites, generation);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    /** Lỗi truy vấn favorites có trạng thái Retry riêng. */
                    @Override
                    public void onFailure(Exception error) {
                        if (currentLoad(generation)) showErrorState();
                    }
                });
    }

    /** Giữ vị trí mỗi favorite khi các chuyến và hồ sơ trả về không cùng thứ tự. */
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
                        /** Chuyến vắng vẫn giữ hàng favorite ở vị trí ban đầu. */
                        @Override
                        public void onSuccess(DocumentSnapshot tripDoc) {
                            if (!currentLoad(generation)) return;
                            if (tripDoc == null) {
                                failed[0] = true;
                                finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                return;
                            }
                            if (tripDoc.exists()) {
                                final Trip trip;
                                try {
                                    trip = tripDoc.toObject(Trip.class);
                                } catch (RuntimeException conversionError) {
                                    failed[0] = true;
                                    finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                    return;
                                }
                                if (trip == null) {
                                    failed[0] = true;
                                    finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                    return;
                                }
                                trip.setId(tripDoc.getId());

                                String driverId = trip.getDriverId();
                                if (driverId != null && !driverId.trim().isEmpty()) {
                                    firestore.collection("publicProfiles").document(driverId).get(Source.SERVER)
                                            .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                /** Hồ sơ công khai là dữ liệu phụ của chuyến. */
                                                @Override
                                                public void onSuccess(DocumentSnapshot profileDoc) {
                                                    if (!currentLoad(generation)) return;
                                                    PublicProfile profile = null;
                                                    if (profileDoc != null && profileDoc.exists()) {
                                                        try {
                                                            profile = profileDoc.toObject(PublicProfile.class);
                                                        } catch (RuntimeException conversionError) {
                                                            profile = null;
                                                        }
                                                        if (profile != null) {
                                                            profile.setId(profileDoc.getId());
                                                        }
                                                    }
                                                    favoriteItems[position] = new FavoriteItem(favorite, trip, profile);
                                                    finishFavoriteLoad(favoriteItems, loadedCount, total, failed, generation);
                                                }
                                            })
                                            .addOnFailureListener(new OnFailureListener() {
                                                /** Lỗi hồ sơ vẫn hiển thị chuyến đã đọc thành công. */
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
                        /** Lỗi đọc chuyến yêu cầu Retry thay vì giả chuyến đã xóa. */
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

    /** Chỉ mở Detail của chuyến còn tồn tại và thuộc lượt tải hiện hành. */
    @Override
    public void onFavoriteClick(final FavoriteItem favoriteItem) {
        if (!currentLoad(loadGeneration)) return;
        if (favoriteItem == null || favoriteItem.getTrip() == null) {
            Toast.makeText(this, "Chuyến đi không khả dụng.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String tripId = favoriteItem.getTrip().getId();
        if (tripId != null && !tripId.trim().isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Chuyến yêu thích")
                    .setItems(new String[]{"Xem chi tiết", "Bỏ yêu thích"}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!currentLoad(loadGeneration)) {
                                return;
                            }
                            if (which == 0) {
                                Intent intent;
                                Trip trip = favoriteItem.getTrip();
                                if ("COMPLETED".equals(trip.getStatus())
                                        && trip.getAcceptedRequestId() != null
                                        && !trip.getAcceptedRequestId().isEmpty()) {
                                    intent = new Intent(FavoritesActivity.this, CompleteActivity.class);
                                    intent.putExtra(NavigationKeys.REQUEST_ID, trip.getAcceptedRequestId());
                                } else {
                                    // Yêu thích cũ vẫn đọc được; không tự xóa hay nhận là chuyến đã đi.
                                    intent = new Intent(FavoritesActivity.this, TripDetailActivity.class);
                                    intent.putExtra(NavigationKeys.TRIP_ID, tripId);
                                }
                                startActivity(intent);
                            } else {
                                final int expectedGeneration = loadGeneration;
                                recyclerView.setEnabled(false);
                                new FavoriteRepository(firestore).removeFavorite(currentUserId, tripId,
                                        new RepositoryCallback<Void>() {
                                            @Override
                                            public void onSuccess(Void ignored) {
                                                if (currentLoad(expectedGeneration)) {
                                                    recyclerView.setEnabled(true);
                                                    loadFavorites();
                                                }
                                            }

                                            @Override
                                            public void onError(Exception error) {
                                                if (currentLoad(expectedGeneration)) {
                                                    recyclerView.setEnabled(true);
                                                    Toast.makeText(FavoritesActivity.this,
                                                            "Chưa xác nhận bỏ yêu thích. Hãy tải lại danh sách.",
                                                            Toast.LENGTH_LONG).show();
                                                }
                                            }
                                        });
                            }
                        }
                    }).show();
        }
    }

    /** Ẩn dữ liệu cũ trong khi chờ server xác nhận danh sách. */
    private void showLoadingState() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
    }

    /** Chỉ hiện danh sách khi toàn bộ lượt tải đã thành công. */
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

    /** Không dùng trạng thái rỗng cho lỗi mạng hay lỗi dữ liệu. */
    private void showEmptyState() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.VISIBLE);
    }

    /** Cho người dùng thử lại sau lỗi đọc dữ liệu. */
    private void showErrorState() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
    }
}
