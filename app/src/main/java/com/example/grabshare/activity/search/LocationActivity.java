package com.example.grabshare.activity.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.api.PlaceSearchClient;
import com.example.grabshare.contract.LocationKeys;
import com.example.grabshare.model.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity tìm kiếm và chọn địa điểm.
 */
public class LocationActivity extends AppCompatActivity {
    private EditText searchInput;
    private RecyclerView locationList;
    private TextView suggestionTitle;
    private TextView statusText;
    private PlaceSearchClient searchClient;
    private PlaceAdapter adapter;

    /**
     * Khởi tạo giao diện và kết nối các thành phần.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_location);

        searchInput = findViewById(R.id.location_search);
        locationList = findViewById(R.id.location_list);
        suggestionTitle = findViewById(R.id.location_suggestion_title);
        statusText = findViewById(R.id.location_status_text);
        
        searchClient = new PlaceSearchClient();

        adapter = new PlaceAdapter(new ArrayList<Place>());
        locationList.setLayoutManager(new LinearLayoutManager(this));
        locationList.setAdapter(adapter);

        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.primary_action).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
    }

    /**
     * Lưu trạng thái màn hình khi xoay.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (adapter != null && adapter.places != null && !adapter.places.isEmpty()) {
            ArrayList<String> names = new ArrayList<>();
            double[] lats = new double[adapter.places.size()];
            double[] lons = new double[adapter.places.size()];
            for (int i = 0; i < adapter.places.size(); i++) {
                Place p = adapter.places.get(i);
                names.add(p.getName());
                lats[i] = p.getLatitude() != null ? p.getLatitude() : 0.0;
                lons[i] = p.getLongitude() != null ? p.getLongitude() : 0.0;
            }
            outState.putStringArrayList("place_names", names);
            outState.putDoubleArray("place_lats", lats);
            outState.putDoubleArray("place_lons", lons);
            
            outState.putInt("status_visibility", statusText.getVisibility());
            outState.putString("status_text", statusText.getText().toString());
            outState.putInt("suggestion_visibility", suggestionTitle.getVisibility());
            outState.putInt("list_visibility", locationList.getVisibility());
        }
    }

    /**
     * Phục hồi trạng thái sau khi xoay.
     */
    private void restoreState(Bundle savedInstanceState) {
        ArrayList<String> names = savedInstanceState.getStringArrayList("place_names");
        double[] lats = savedInstanceState.getDoubleArray("place_lats");
        double[] lons = savedInstanceState.getDoubleArray("place_lons");

        if (names != null && lats != null && lons != null && names.size() == lats.length) {
            List<Place> restoredPlaces = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                restoredPlaces.add(new Place(names.get(i), lats[i], lons[i]));
            }
            adapter.setPlaces(restoredPlaces);
            
            statusText.setVisibility(savedInstanceState.getInt("status_visibility", View.GONE));
            statusText.setText(savedInstanceState.getString("status_text", ""));
            suggestionTitle.setVisibility(savedInstanceState.getInt("suggestion_visibility", View.GONE));
            locationList.setVisibility(savedInstanceState.getInt("list_visibility", View.GONE));
        }
    }

    /**
     * Thực hiện tìm kiếm địa điểm từ API.
     */
    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(this, "Vui lòng nhập địa điểm cần tìm", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Đang tìm kiếm...");
        suggestionTitle.setVisibility(View.GONE);
        locationList.setVisibility(View.GONE);

        searchClient.searchPlaces(query, new PlaceSearchClient.PlaceSearchCallback() {
            @Override
            public void onSuccess(final List<Place> places) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setVisibility(View.GONE);
                        suggestionTitle.setVisibility(View.VISIBLE);
                        locationList.setVisibility(View.VISIBLE);
                        
                        if (places.isEmpty()) {
                            statusText.setVisibility(View.VISIBLE);
                            statusText.setText("Không tìm thấy kết quả phù hợp");
                        }
                        
                        adapter.setPlaces(places);
                    }
                });
            }

            @Override
            public void onError(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("Lỗi khi tìm kiếm: " + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * Xử lý khi người dùng chọn một địa điểm.
     */
    private void selectPlace(Place place) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(LocationKeys.PLACE_NAME, place.getName());
        resultIntent.putExtra(LocationKeys.PLACE_LATITUDE, place.getLatitude());
        resultIntent.putExtra(LocationKeys.PLACE_LONGITUDE, place.getLongitude());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchClient != null) {
            searchClient.shutdown();
        }
    }

    /**
     * Adapter hiển thị danh sách địa điểm gợi ý.
     */
    private class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder> {
        private List<Place> places;

        /**
         * Khởi tạo adapter.
         */
        PlaceAdapter(List<Place> places) {
            this.places = places;
        }

        /**
         * Cập nhật danh sách mới.
         */
        void setPlaces(List<Place> places) {
            this.places = places;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_place, parent, false);
            return new PlaceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
            final Place place = places.get(position);
            holder.placeName.setText(place.getName());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectPlace(place);
                }
            });
        }

        @Override
        public int getItemCount() {
            return places.size();
        }

        /**
         * ViewHolder chứa các thành phần giao diện của mỗi dòng.
         */
        class PlaceViewHolder extends RecyclerView.ViewHolder {
            TextView placeName;

            PlaceViewHolder(@NonNull View itemView) {
                super(itemView);
                placeName = itemView.findViewById(R.id.place_name);
            }
        }
    }
}