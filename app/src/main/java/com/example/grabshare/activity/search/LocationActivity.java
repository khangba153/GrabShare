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
    private int currentSearchRequestId = 0;
    private View searchButton;

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

        searchButton = findViewById(R.id.primary_action);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            loadDefaultSuggestions();
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
        }
        
        outState.putInt("status_visibility", statusText.getVisibility());
        outState.putString("status_text", statusText.getText().toString());
        outState.putInt("suggestion_visibility", suggestionTitle.getVisibility());
        outState.putInt("list_visibility", locationList.getVisibility());
        outState.putInt("search_request_id", currentSearchRequestId);
    }

    /**
     * Phục hồi trạng thái sau khi xoay.
     */
    private void restoreState(Bundle savedInstanceState) {
        currentSearchRequestId = savedInstanceState.getInt("search_request_id", 0);
        
        ArrayList<String> names = savedInstanceState.getStringArrayList("place_names");
        double[] lats = savedInstanceState.getDoubleArray("place_lats");
        double[] lons = savedInstanceState.getDoubleArray("place_lons");

        if (names != null && lats != null && lons != null && names.size() == lats.length) {
            List<Place> restoredPlaces = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                restoredPlaces.add(new Place(names.get(i), lats[i], lons[i]));
            }
            adapter.setPlaces(restoredPlaces);
        } else {
            // Nếu không có danh sách đã lưu (có thể vì chưa có kết quả), nạp gợi ý mặc định
            loadDefaultSuggestions();
        }

        String savedStatus = savedInstanceState.getString("status_text", "");
        if ("Đang tìm kiếm...".equals(savedStatus)) {
            // Nếu tác vụ trước đó đang tìm kiếm thì tự động tìm lại
            performSearch();
        } else {
            statusText.setVisibility(savedInstanceState.getInt("status_visibility", View.GONE));
            statusText.setText(savedStatus);
            suggestionTitle.setVisibility(savedInstanceState.getInt("suggestion_visibility", View.GONE));
            locationList.setVisibility(savedInstanceState.getInt("list_visibility", View.GONE));
        }
    }

    /**
     * Nạp danh sách các địa điểm gợi ý sẵn (Chủ yếu là các cơ sở UEH)
     * để người dùng chọn ngay thuận tiện mà không bắt buộc gõ phím.
     */
    private void loadDefaultSuggestions() {
        List<Place> defaultPlaces = new ArrayList<>();
        defaultPlaces.add(new Place("UEH Cơ sở A - 59C Nguyễn Đình Chiểu, Phường 6, Quận 3, TP.HCM", 10.7830141, 106.6948927));
        defaultPlaces.add(new Place("UEH Cơ sở B - 279 Nguyễn Tri Phương, Phường 5, Quận 10, TP.HCM", 10.7609491, 106.6679786));
        defaultPlaces.add(new Place("UEH Cơ sở C - 91 Đường 3 Tháng 2, Phường 11, Quận 10, TP.HCM", 10.773238, 106.677568));
        defaultPlaces.add(new Place("UEH Cơ sở N - Nguyễn Văn Linh, Phong Phú, Bình Chánh, TP.HCM", 10.705906, 106.639100));
        defaultPlaces.add(new Place("UEH Cơ sở H - 1A Hoàng Diệu, Phường 10, Phú Nhuận, TP.HCM", 10.798991, 106.678210));
        defaultPlaces.add(new Place("UEH Cơ sở V - 232/9 Võ Thị Sáu, Phường Võ Thị Sáu, Quận 3, TP.HCM", 10.7828045, 106.6856203));

        adapter.setPlaces(defaultPlaces);
        suggestionTitle.setText("Gợi ý nhanh (Các cơ sở UEH)");
        suggestionTitle.setVisibility(View.VISIBLE);
        locationList.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.GONE);
    }

    /**
     * Thực hiện tìm kiếm địa điểm từ API.
     */
    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            loadDefaultSuggestions();
            return;
        }

        // Tăng ID và khóa nút tìm kiếm
        final int requestId = ++currentSearchRequestId;
        searchButton.setEnabled(false);

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
                        if (isFinishing() || isDestroyed()) return;
                        if (requestId != currentSearchRequestId) return; // Bỏ qua kết quả cũ
                        
                        searchButton.setEnabled(true);
                        
                        statusText.setVisibility(View.GONE);
                        suggestionTitle.setText("Kết quả tìm kiếm");
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
                        if (isFinishing() || isDestroyed()) return;
                        if (requestId != currentSearchRequestId) return; // Bỏ qua kết quả cũ
                        
                        searchButton.setEnabled(true);
                        
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