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
import com.example.grabshare.repository.search.SearchTripPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/** Activity tìm kiếm và chọn địa điểm. */
public class LocationActivity extends AppCompatActivity {

    private EditText searchInput;

    private RecyclerView locationList;

    private TextView suggestionTitle;

    private TextView statusText;

    private PlaceSearchClient searchClient;

    private PlaceAdapter adapter;

    private int currentSearchRequestId = 0;

    private boolean restoringFormState;

    private boolean searchInProgress;

    private String activeSearchQuery = "";

    private Timer searchTimer;

    private static final long SEARCH_DELAY_MILLISECONDS = 1000;

    private View searchButton;

    /** Khởi tạo giao diện và kết nối các thành phần. */
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
        findViewById(R.id.header_back)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Back chỉ trả hủy, không sửa điểm của màn gọi. */
                            @Override
                            public void onClick(View clickedView) {
                                finish();
                            }
                        });
        searchButton = findViewById(R.id.primary_action);
        findViewById(R.id.action_area).setVisibility(View.VISIBLE);
        searchButton.setOnClickListener(
                new View.OnClickListener() {

                    /** Tìm thủ công thay thế bộ hẹn giờ đang chờ. */
                    @Override
                    public void onClick(View clickedView) {
                        performSearch();
                    }
                });
        searchInput.addTextChangedListener(
                new android.text.TextWatcher() {

                    /** Không gửi tìm kiếm ở sự kiện trước thay đổi văn bản. */
                    @Override
                    public void beforeTextChanged(
                            CharSequence changedText, int start, int count, int after) {}

                    /** Dồn thay đổi vào afterTextChanged để mỗi lần nhập chỉ lập một bộ hẹn. */
                    @Override
                    public void onTextChanged(
                            CharSequence changedText, int start, int before, int count) {}

                    /** Đổi query vô hiệu phản hồi cũ ngay, khôi phục form không lập bộ hẹn. */
                    @Override
                    public void afterTextChanged(android.text.Editable changedText) {
                        if (restoringFormState) {
                            return;
                        }
                        cancelPendingSearch();
                        currentSearchRequestId = currentSearchRequestId + 1;
                        searchInProgress = false;
                        searchButton.setEnabled(true);
                        adapter.setPlaces(new ArrayList<Place>());
                        statusText.setVisibility(View.GONE);
                        suggestionTitle.setVisibility(View.GONE);
                        locationList.setVisibility(View.GONE);
                        if (TextUtils.isEmpty(searchInput.getText().toString().trim())) {
                            return;
                        }
                        final int scheduledRequestIdentifier = currentSearchRequestId;
                        searchTimer = new Timer();
                        searchTimer.schedule(
                                new TimerTask() {

                                    /**
                                     * Chỉ chuyển tác vụ hẹn giờ sang luồng giao diện, không gọi API
                                     * tại đây.
                                     */
                                    @Override
                                    public void run() {
                                        runOnUiThread(
                                                new Runnable() {

                                                    /**
                                                     * Bỏ lượt hẹn đã bị đổi query, Tìm thủ công
                                                     * hoặc đóng màn vô hiệu.
                                                     */
                                                    @Override
                                                    public void run() {
                                                        if (isFinishing()
                                                                || isDestroyed()
                                                                || scheduledRequestIdentifier
                                                                        != currentSearchRequestId) {
                                                            return;
                                                        }
                                                        performSearch();
                                                    }
                                                });
                                    }
                                },
                                SEARCH_DELAY_MILLISECONDS);
                    }
                });
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
    }

    /** Lưu trạng thái màn hình khi xoay. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (adapter != null && adapter.places != null && !adapter.places.isEmpty()) {
            ArrayList<String> names = new ArrayList<String>();
            double[] placeLatitudes = new double[adapter.places.size()];
            double[] placeLongitudes = new double[adapter.places.size()];
            for (int placeIndex = 0;
                    placeIndex < adapter.places.size();
                    placeIndex = placeIndex + 1) {
                Place savedPlace = adapter.places.get(placeIndex);
                names.add(savedPlace.getName());
                if (savedPlace.getLatitude() != null) {
                    placeLatitudes[placeIndex] = savedPlace.getLatitude();
                } else {
                    placeLatitudes[placeIndex] = Double.NaN;
                }
                if (savedPlace.getLongitude() != null) {
                    placeLongitudes[placeIndex] = savedPlace.getLongitude();
                } else {
                    placeLongitudes[placeIndex] = Double.NaN;
                }
            }
            outState.putStringArrayList("place_names", names);
            outState.putDoubleArray("place_lats", placeLatitudes);
            outState.putDoubleArray("place_lons", placeLongitudes);
        }
        outState.putInt("status_visibility", statusText.getVisibility());
        outState.putString("status_text", statusText.getText().toString());
        outState.putInt("suggestion_visibility", suggestionTitle.getVisibility());
        outState.putInt("list_visibility", locationList.getVisibility());
        outState.putInt("search_request_id", currentSearchRequestId);
        outState.putString("search_query", searchInput.getText().toString());
        outState.putBoolean("search_in_progress", searchInProgress);
    }

    /** Android tự khôi phục EditText cũng không được kích hoạt lại bộ hẹn tìm kiếm. */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        restoringFormState = true;
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } finally {
            restoringFormState = false;
        }
    }

    /** Phục hồi trạng thái sau khi xoay. */
    private void restoreState(Bundle savedInstanceState) {
        currentSearchRequestId = savedInstanceState.getInt("search_request_id", 0);
        // Phục hồi và đặt lại query từ instance state
        String savedQuery = savedInstanceState.getString("search_query", "");
        restoringFormState = true;
        try {
            searchInput.setText(savedQuery);
        } finally {
            restoringFormState = false;
        }
        ArrayList<String> names = savedInstanceState.getStringArrayList("place_names");
        double[] placeLatitudes = savedInstanceState.getDoubleArray("place_lats");
        double[] placeLongitudes = savedInstanceState.getDoubleArray("place_lons");
        if (names != null
                && placeLatitudes != null
                && placeLongitudes != null
                && names.size() == placeLatitudes.length
                && names.size() == placeLongitudes.length) {
            List<Place> restoredPlaces = new ArrayList<Place>();
            for (int placeIndex = 0; placeIndex < names.size(); placeIndex = placeIndex + 1) {
                Place restoredPlace =
                        new Place(
                                names.get(placeIndex),
                                placeLatitudes[placeIndex],
                                placeLongitudes[placeIndex]);
                if (SearchTripPolicy.validPlace(restoredPlace)) {
                    restoredPlaces.add(restoredPlace);
                }
            }
            adapter.setPlaces(restoredPlaces);
        }
        String savedStatus = savedInstanceState.getString("status_text", "");
        searchInProgress = false;
        searchButton.setEnabled(true);
        if (savedInstanceState.getBoolean("search_in_progress", false)) {
            // Tác vụ cũ thuộc Activity đã đóng; khôi phục form không tự gửi lại API.
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("Lượt tìm trước đã gián đoạn. Nhấn Tìm kiếm để thử lại.");
            suggestionTitle.setVisibility(View.GONE);
            locationList.setVisibility(View.GONE);
        } else {
            statusText.setVisibility(savedInstanceState.getInt("status_visibility", View.GONE));
            statusText.setText(savedStatus);
            suggestionTitle.setVisibility(
                    savedInstanceState.getInt("suggestion_visibility", View.GONE));
            locationList.setVisibility(savedInstanceState.getInt("list_visibility", View.GONE));
        }
    }

    /** Thực hiện tìm kiếm địa điểm từ API. */
    private void performSearch() {
        cancelPendingSearch();
        if (isFinishing() || isDestroyed()) {
            return;
        }
        String query = searchInput.getText().toString().trim();
        if (searchInProgress && query.equals(activeSearchQuery)) {
            return;
        }
        currentSearchRequestId = currentSearchRequestId + 1;
        final int searchRequestIdentifier = currentSearchRequestId;
        if (TextUtils.isEmpty(query)) {
            searchInProgress = false;
            searchButton.setEnabled(true);
            adapter.setPlaces(new ArrayList<Place>());
            statusText.setVisibility(View.GONE);
            suggestionTitle.setVisibility(View.GONE);
            locationList.setVisibility(View.GONE);
            return;
        }
        // Tăng ID và khóa nút tìm kiếm
        searchInProgress = true;
        activeSearchQuery = query;
        searchButton.setEnabled(false);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Đang tìm kiếm...");
        suggestionTitle.setVisibility(View.GONE);
        locationList.setVisibility(View.GONE);
        searchClient.searchPlaces(
                query,
                new PlaceSearchClient.PlaceSearchCallback() {

                    /** Client trả từ luồng nền; chỉ màn và query hiện tại mới được nhận kết quả. */
                    @Override
                    public void onSuccess(final List<Place> places) {
                        runOnUiThread(
                                new Runnable() {

                                    /** Không để kết quả query cũ ghi đè query đang hiển thị. */
                                    @Override
                                    public void run() {
                                        if (isFinishing() || isDestroyed()) {
                                            return;
                                        }
                                        // Bỏ qua kết quả cũ
                                        if (searchRequestIdentifier != currentSearchRequestId) {
                                            return;
                                        }
                                        searchInProgress = false;
                                        searchButton.setEnabled(true);
                                        statusText.setVisibility(View.GONE);
                                        suggestionTitle.setText("Kết quả tìm kiếm");
                                        suggestionTitle.setVisibility(View.VISIBLE);
                                        locationList.setVisibility(View.VISIBLE);
                                        if (places.isEmpty()) {
                                            statusText.setVisibility(View.VISIBLE);
                                            statusText.setText("Không tìm thấy kết quả phù hợp");
                                        }
                                        List<Place> validPlaces = new ArrayList<Place>();
                                        for (Place place : places) {
                                            if (SearchTripPolicy.validPlace(place)) {
                                                validPlaces.add(place);
                                            }
                                        }
                                        adapter.setPlaces(validPlaces);
                                        if (validPlaces.isEmpty()) {
                                            statusText.setVisibility(View.VISIBLE);
                                            statusText.setText("Không tìm thấy kết quả phù hợp");
                                        }
                                    }
                                });
                    }

                    /**
                     * Lỗi cũng phải gắn với đúng lượt tìm, không đổi sang tọa độ hoặc dữ liệu mẫu.
                     */
                    @Override
                    public void onError(final Exception error) {
                        runOnUiThread(
                                new Runnable() {

                                    /** Mở lại nút thử tìm nhưng không tự gọi lại API sau lỗi. */
                                    @Override
                                    public void run() {
                                        if (isFinishing() || isDestroyed()) {
                                            return;
                                        }
                                        // Bỏ qua kết quả cũ
                                        if (searchRequestIdentifier != currentSearchRequestId) {
                                            return;
                                        }
                                        searchInProgress = false;
                                        searchButton.setEnabled(true);
                                        statusText.setVisibility(View.VISIBLE);
                                        statusText.setText(
                                                "Lỗi khi tìm kiếm: " + error.getMessage());
                                    }
                                });
                    }
                });
    }

    /** Xử lý khi người dùng chọn một địa điểm. */
    private void selectPlace(Place place) {
        if (!SearchTripPolicy.validPlace(place)) {
            Toast.makeText(this, "Địa điểm thiếu tên hoặc tọa độ hợp lệ.", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        Intent resultIntent = new Intent();
        resultIntent.putExtra(LocationKeys.PLACE_NAME, place.getName());
        resultIntent.putExtra(LocationKeys.PLACE_LATITUDE, place.getLatitude());
        resultIntent.putExtra(LocationKeys.PLACE_LONGITUDE, place.getLongitude());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /** Hủy bộ hẹn giờ; mã lượt tìm còn chặn cả Runnable đã được đưa lên luồng UI. */
    private void cancelPendingSearch() {
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer = null;
        }
    }

    /** Vô hiệu phản hồi trước khi đóng client để không cập nhật Activity đã hủy. */
    @Override
    protected void onDestroy() {
        currentSearchRequestId = currentSearchRequestId + 1;
        cancelPendingSearch();
        if (searchClient != null) {
            searchClient.shutdown();
        }
        super.onDestroy();
    }

    /** Adapter hiển thị danh sách địa điểm gợi ý. */
    private class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder> {

        private List<Place> places;

        /** Khởi tạo adapter. */
        PlaceAdapter(List<Place> places) {
            this.places = places;
        }

        /** Cập nhật danh sách mới. */
        void setPlaces(List<Place> places) {
            this.places = places;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        /** Tạo dòng địa điểm từ layout của Thành. */
        public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.row_place, parent, false);
            return new PlaceViewHolder(view);
        }

        /** Hiển thị dữ liệu thật và chỉ trả kết quả khi người dùng chọn dòng. */
        @Override
        public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
            final Place place = places.get(position);
            holder.placeName.setText(place.getName());
            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {

                        /** Chuyển đủ ba trường LocationKeys về màn đã mở bộ chọn. */
                        @Override
                        public void onClick(View clickedView) {
                            selectPlace(place);
                        }
                    });
        }

        /** Không tạo dòng mẫu khi chưa có kết quả. */
        @Override
        public int getItemCount() {
            return places.size();
        }

        /** ViewHolder chứa các thành phần giao diện của mỗi dòng. */
        class PlaceViewHolder extends RecyclerView.ViewHolder {

            TextView placeName;

            /** Giữ TextView của đúng dòng đang hiển thị. */
            PlaceViewHolder(@NonNull View itemView) {
                super(itemView);
                placeName = itemView.findViewById(R.id.place_name);
            }
        }
    }
}
