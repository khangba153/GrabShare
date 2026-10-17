package com.example.grabshare.activity.passenger;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.grabshare.R;
import com.example.grabshare.activity.account.LoginActivity;
import com.example.grabshare.activity.search.HomeActivity;
import com.example.grabshare.repository.passenger.FavoriteRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Activity hiển thị danh sách các chuyến đi yêu thích đã lưu của Hành khách (Thuận-7).
 */
public class FavoritesActivity extends AppCompatActivity {

    private ImageButton headerBack;
    private TextView headerTitle;
    private View favoriteList;

    private FavoriteRepository favoriteRepository;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_favorites);

        // Kiểm tra phiên đăng nhập
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid() != null) {
            currentUserId = currentUser.getUid();
        } else {
            Toast.makeText(this, "Vui lòng đăng nhập để xem danh sách yêu thích.", Toast.LENGTH_SHORT).show();
        }

        initRepository();
        initViews();
        setupListeners();
    }

    private void initRepository() {
        favoriteRepository = new FavoriteRepository(FirebaseFirestore.getInstance());
    }

    private void initViews() {
        headerBack = findViewById(R.id.header_back);
        headerTitle = findViewById(R.id.header_title);
        favoriteList = findViewById(R.id.favorite_list);

        if (headerBack != null) {
            headerBack.setClickable(true);
            headerBack.setFocusable(true);
        }

        if (headerTitle != null) {
            headerTitle.setText("Chuyến yêu thích");
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

        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(FavoritesActivity.this, HomeActivity.class);
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
                    Intent intent = new Intent(FavoritesActivity.this, LoginActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    public FavoriteRepository getFavoriteRepository() {
        return favoriteRepository;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }
}
