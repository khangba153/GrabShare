package com.example.grabshare.activity.search;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.adapter.search.TripAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.PublicProfile;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.TripSearchRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Activity Hiển thị kết quả chuyến phù hợp (Thành-3).
 */
public class ResultsActivity extends AppCompatActivity {

    private TextView searchSummary;
    private TextView statusText;
    private RecyclerView rideList;
    private TextView routeStart;
    private TextView routeEnd;
    
    private TripSearchRepository repository;
    private TripAdapter adapter;
    private final List<TripSearchRepository.TripWithProfile> matchedTrips = new ArrayList<>();
    
    private Place searchOrigin;
    private Place searchDestination;
    private long searchDepartureTime;
    
    // Khoảng cách tối đa (mét) để coi là "khớp" tuyến đường (ví dụ 5km)
    private static final float MAX_MATCH_DISTANCE_METERS = 5000f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_results);

        searchSummary = findViewById(R.id.search_summary);
        statusText = findViewById(R.id.status_text);
        rideList = findViewById(R.id.ride_list);
        routeStart = findViewById(R.id.route_start);
        routeEnd = findViewById(R.id.route_end);
        
        repository = new TripSearchRepository();
        
        adapter = new TripAdapter(new TripAdapter.OnTripClickListener() {
            @Override
            public void onTripClick(TripSearchRepository.TripWithProfile tripWithProfile) {
                // Mở Detail bằng trip_id
                Intent intent = new Intent(ResultsActivity.this, TripDetailActivity.class);
                intent.putExtra(NavigationKeys.TRIP_ID, tripWithProfile.trip.getId());
                startActivity(intent);
            }
        });
        rideList.setLayoutManager(new LinearLayoutManager(this));
        rideList.setAdapter(adapter);

        setupListeners();
        
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            loadSearchCriteria();
        }
        
        executeSearch();
    }

    private void setupListeners() {
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        // Bấm vào thẻ tìm kiếm để đổi điều kiện (Quay lại Home)
        findViewById(R.id.search_summary_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * Nhận điều kiện tìm kiếm từ HomeActivity
     */
    private void loadSearchCriteria() {
        Intent intent = getIntent();
        if (intent != null) {
            searchOrigin = new Place(
                    intent.getStringExtra("origin_name"),
                    intent.getDoubleExtra("origin_lat", 0),
                    intent.getDoubleExtra("origin_lng", 0)
            );
            
            searchDestination = new Place(
                    intent.getStringExtra("dest_name"),
                    intent.getDoubleExtra("dest_lat", 0),
                    intent.getDoubleExtra("dest_lng", 0)
            );
            
            searchDepartureTime = intent.getLongExtra("departure_time", System.currentTimeMillis());
            
            updateHeaderUI();
        }
    }

    private void updateHeaderUI() {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault());
        searchSummary.setText(df.format(searchDepartureTime));

        if (searchOrigin != null && searchOrigin.getName() != null) {
            routeStart.setText(searchOrigin.getName());
        }
        if (searchDestination != null && searchDestination.getName() != null) {
            routeEnd.setText(searchDestination.getName());
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("departure_time", searchDepartureTime);
        if (searchOrigin != null) {
            outState.putString("origin_name", searchOrigin.getName());
            outState.putDouble("origin_lat", searchOrigin.getLatitude() != null ? searchOrigin.getLatitude() : 0);
            outState.putDouble("origin_lng", searchOrigin.getLongitude() != null ? searchOrigin.getLongitude() : 0);
        }
        if (searchDestination != null) {
            outState.putString("dest_name", searchDestination.getName());
            outState.putDouble("dest_lat", searchDestination.getLatitude() != null ? searchDestination.getLatitude() : 0);
            outState.putDouble("dest_lng", searchDestination.getLongitude() != null ? searchDestination.getLongitude() : 0);
        }
    }

    private void restoreState(Bundle savedInstanceState) {
        searchDepartureTime = savedInstanceState.getLong("departure_time", System.currentTimeMillis());
        if (savedInstanceState.containsKey("origin_name")) {
            searchOrigin = new Place(
                    savedInstanceState.getString("origin_name"),
                    savedInstanceState.getDouble("origin_lat"),
                    savedInstanceState.getDouble("origin_lng")
            );
        }
        if (savedInstanceState.containsKey("dest_name")) {
            searchDestination = new Place(
                    savedInstanceState.getString("dest_name"),
                    savedInstanceState.getDouble("dest_lat"),
                    savedInstanceState.getDouble("dest_lng")
            );
        }
        updateHeaderUI();
    }

    /**
     * Thực hiện truy vấn và lọc dữ liệu
     */
    private void executeSearch() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để tìm chuyến!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Đang tìm kiếm chuyến xe...");
        rideList.setVisibility(View.GONE);
        matchedTrips.clear();

        repository.searchOpenTrips(searchDepartureTime, new RepositoryCallback<List<Trip>>() {
            @Override
            public void onSuccess(List<Trip> trips) {
                filterTrips(trips);
            }

            @Override
            public void onError(Exception e) {
                statusText.setVisibility(View.VISIBLE);
                statusText.setText("Lỗi khi tải dữ liệu: " + e.getMessage());
            }
        });
    }

    /**
     * Lọc chuyến xe theo khoảng cách và loại bỏ chuyến của chính mình
     */
    private void filterTrips(List<Trip> trips) {
        // Fix: Lấy FirebaseAuth.currentUser thật; dùng UID thật khi loại chuyến chính mình
        String currentUid = "";
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUid = currentUser.getUid();
        }

        final List<Trip> validTrips = new ArrayList<>();
        
        for (Trip trip : trips) {
            // Lọc driverId chính mình
            if (currentUid.equals(trip.getDriverId())) {
                continue;
            }
            
            // Lọc khoảng cách hai đầu trong Java (Bán kính chốt: 5km)
            if (trip.getOrigin() != null && trip.getDestination() != null) {
                float startDist = calculateDistance(searchOrigin, trip.getOrigin());
                float endDist = calculateDistance(searchDestination, trip.getDestination());
                
                if (startDist <= MAX_MATCH_DISTANCE_METERS && endDist <= MAX_MATCH_DISTANCE_METERS) {
                    validTrips.add(trip);
                }
            }
        }

        if (validTrips.isEmpty()) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Không tìm thấy chuyến xe phù hợp. Vui lòng thử tìm lại!");
            adapter.setTrips(new ArrayList<TripSearchRepository.TripWithProfile>());
            return;
        }

        // Tải profile cho các chuyến xe hợp lệ
        fetchProfilesForTrips(validTrips);
    }

    /**
     * Lấy profile cho từng tài xế để hiển thị
     */
    private void fetchProfilesForTrips(final List<Trip> validTrips) {
        final int totalTrips = validTrips.size();
        final int[] loadedCount = {0};

        for (final Trip trip : validTrips) {
            repository.getDriverProfile(trip.getDriverId(), new RepositoryCallback<PublicProfile>() {
                @Override
                public void onSuccess(PublicProfile profile) {
                    addMatchedTrip(trip, profile, totalTrips, loadedCount);
                }

                @Override
                public void onError(Exception e) {
                    // Vẫn hiển thị chuyến dù không lấy được profile
                    addMatchedTrip(trip, null, totalTrips, loadedCount);
                }
            });
        }
    }
    
    private synchronized void addMatchedTrip(Trip trip, PublicProfile profile, int totalTrips, int[] loadedCount) {
        TripSearchRepository.TripWithProfile twp = new TripSearchRepository.TripWithProfile();
        twp.trip = trip;
        twp.profile = profile;
        matchedTrips.add(twp);
        
        loadedCount[0]++;
        if (loadedCount[0] == totalTrips) {
            // Đã tải xong tất cả, sắp xếp lại theo giờ đi và hiển thị
            Collections.sort(matchedTrips, new Comparator<TripSearchRepository.TripWithProfile>() {
                @Override
                public int compare(TripSearchRepository.TripWithProfile t1, TripSearchRepository.TripWithProfile t2) {
                    if (t1.trip.getDepartureAt() != null && t2.trip.getDepartureAt() != null) {
                        return t1.trip.getDepartureAt().compareTo(t2.trip.getDepartureAt());
                    }
                    return 0;
                }
            });
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    statusText.setVisibility(View.GONE);
                    rideList.setVisibility(View.VISIBLE);
                    adapter.setTrips(matchedTrips);
                }
            });
        }
    }

    /**
     * Hàm tiện ích tính khoảng cách (mét) giữa 2 tọa độ (Sử dụng công thức của Android Location)
     */
    private float calculateDistance(Place p1, Place p2) {
        if (p1 == null || p2 == null) return Float.MAX_VALUE;
        float[] results = new float[1];
        Location.distanceBetween(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude(),
                results
        );
        return results[0];
    }
}