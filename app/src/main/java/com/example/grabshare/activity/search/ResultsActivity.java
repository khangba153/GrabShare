package com.example.grabshare.activity.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grabshare.R;
import com.example.grabshare.adapter.search.TripAdapter;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.model.Place;
import com.example.grabshare.repository.search.EnRouteSearch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Kết quả ghép dọc tuyến; Activity chỉ xử lý giao diện, tác vụ nằm trong ViewModel. */
public class ResultsActivity extends AppCompatActivity {

    private ResultsViewModel resultsViewModel;

    private TripAdapter adapter;

    private EnRouteSearch.Criteria criteria;

    private Spinner sortSpinner;

    /** Nối điều kiện Home, hàng đợi tìm chuyến và thao tác Detail bằng trip_id. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_results);
        ((TextView) findViewById(R.id.route_start)).setText("Chưa chọn điểm đón");
        ((TextView) findViewById(R.id.route_end)).setText("Chưa chọn điểm trả");
        resultsViewModel = new ViewModelProvider(this).get(ResultsViewModel.class);
        findViewById(R.id.header_back)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Trở về đúng màn nhập điều kiện. */
                            @Override
                            public void onClick(View view) {
                                finish();
                            }
                        });
        findViewById(R.id.search_summary_card)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Đổi điểm hoặc ngày bắt đầu từ Home, không dùng hàng đợi cũ. */
                            @Override
                            public void onClick(View view) {
                                finish();
                            }
                        });
        adapter =
                new TripAdapter(
                        new TripAdapter.OnTripClickListener() {

                            /**
                             * Detail đọc lại server; metric kết quả không cấp quyền gửi yêu cầu.
                             */
                            @Override
                            public void onTripClick(EnRouteSearch.Match match) {
                                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                if (criteria == null
                                        || match.criteria == null
                                        || user == null
                                        || !criteria.userId.equals(user.getUid())
                                        || !match.criteria.userId.equals(user.getUid())) {
                                    return;
                                }
                                if (match.trip.getDepartureAt().toDate().getTime()
                                        <= System.currentTimeMillis()) {
                                    resultsViewModel.revalidate();
                                    return;
                                }
                                Intent intent =
                                        new Intent(ResultsActivity.this, TripDetailActivity.class);
                                intent.putExtra(NavigationKeys.TRIP_ID, match.trip.getId());
                                PassengerSearchRoute.attach(
                                        intent,
                                        match.trip.getId(),
                                        criteria.userId,
                                        match.criteria.pickup,
                                        match.criteria.dropoff);
                                startActivity(intent);
                            }
                        });
        RecyclerView list = findViewById(R.id.ride_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        sortSpinner = findViewById(R.id.en_route_sort);
        sortSpinner.setAdapter(
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[] {
                            "Đề xuất",
                            "Khoản bạn đóng góp thấp nhất",
                            "Tài xế xuất phát gần điểm đón nhất",
                            "Ít lệch đường nhất"
                        }));
        sortSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    /** Sắp xếp trên RAM ngay cả khi đang kiểm tra thêm ứng viên. */
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long selectedItemIdentifier) {
                        resultsViewModel.setSort(EnRouteSearch.Sort.values()[position]);
                    }

                    /** Không xóa lựa chọn khi Spinner chưa có dòng đang chọn. */
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        findViewById(R.id.en_route_more)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Lấy ứng viên tiếp theo, không đọc lại Firestore. */
                            @Override
                            public void onClick(View view) {
                                resultsViewModel.loadMore();
                            }
                        });
        findViewById(R.id.en_route_refresh)
                .setOnClickListener(
                        new View.OnClickListener() {

                            /** Bắt đầu snapshot mới từ máy chủ, vô hiệu hóa callback cũ. */
                            @Override
                            public void onClick(View view) {
                                resultsViewModel.refresh();
                            }
                        });
        try {
            criteria = readCriteria(savedInstanceState);
            ((TextView) findViewById(R.id.route_start)).setText(criteria.pickup.getName());
            ((TextView) findViewById(R.id.route_end)).setText(criteria.dropoff.getName());
            SimpleDateFormat format =
                    new SimpleDateFormat("dd/MM/yyyy · HH:mm", new Locale("vi", "VN"));
            format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            ((TextView) findViewById(R.id.search_summary))
                    .setText(format.format(criteria.requestedAt));
            if (savedInstanceState != null && resultsViewModel.getState().getValue() == null) {
                int selection = savedInstanceState.getInt("results_sort", 0);
                if (selection >= 0 && selection < EnRouteSearch.Sort.values().length) {
                    resultsViewModel.setSort(EnRouteSearch.Sort.values()[selection]);
                    sortSpinner.setSelection(selection);
                }
            }
            resultsViewModel
                    .getState()
                    .observe(
                            this,
                            new Observer<ResultsViewModel.State>() {

                                /** LiveData chỉ vẽ khi màn ở trạng thái hoạt động. */
                                @Override
                                public void onChanged(ResultsViewModel.State state) {
                                    render(state);
                                }
                            });
            resultsViewModel.initialize(criteria);
        } catch (IllegalArgumentException invalid) {
            ((TextView) findViewById(R.id.en_route_notice))
                    .setText("Thiếu hoặc sai điều kiện. Quay lại Home để chọn điểm và thời gian.");
            findViewById(R.id.en_route_notice).setVisibility(View.VISIBLE);
            findViewById(R.id.en_route_more).setEnabled(false);
            findViewById(R.id.en_route_refresh).setEnabled(false);
            sortSpinner.setEnabled(false);
        }
    }

    /** Không tự query lại khi trở về từ Detail hoặc khi xoay màn. */
    @Override
    protected void onResume() {
        super.onResume();
        if (resultsViewModel != null) {
            resultsViewModel.revalidate();
        }
    }

    /** Giữ điều kiện và cách sắp xếp qua process death, không serialize Trip. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (criteria != null) {
            state.putString("origin_name", criteria.pickup.getName());
            state.putDouble("origin_lat", criteria.pickup.getLatitude());
            state.putDouble("origin_lng", criteria.pickup.getLongitude());
            state.putString("dest_name", criteria.dropoff.getName());
            state.putDouble("dest_lat", criteria.dropoff.getLatitude());
            state.putDouble("dest_lng", criteria.dropoff.getLongitude());
            state.putLong("departure_time", criteria.requestedAt);
            state.putString("results_uid", criteria.userId);
            ResultsViewModel.State value = resultsViewModel.getState().getValue();
            if (value != null) {
                state.putInt("results_sort", value.sort.ordinal());
            }
        }
        super.onSaveInstanceState(state);
    }

    /** Không coi extra thiếu là tọa độ 0 hoặc lấy UID khác sau khi process bị hủy. */
    private EnRouteSearch.Criteria readCriteria(Bundle saved) {
        Bundle searchArguments;
        if (saved == null) {
            searchArguments = getIntent().getExtras();
        } else {
            searchArguments = saved;
        }
        if (searchArguments == null) {
            throw new IllegalArgumentException("Thiếu điều kiện tìm.");
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userIdentifier;
        if (saved != null) {
            userIdentifier = saved.getString("results_uid");
        } else {
            if (user == null) {
                userIdentifier = null;
            } else {
                userIdentifier = user.getUid();
            }
        }
        return new EnRouteSearch.Criteria(
                new Place(
                        searchArguments.getString("origin_name"),
                        searchArguments.getDouble("origin_lat", Double.NaN),
                        searchArguments.getDouble("origin_lng", Double.NaN)),
                new Place(
                        searchArguments.getString("dest_name"),
                        searchArguments.getDouble("dest_lat", Double.NaN),
                        searchArguments.getDouble("dest_lng", Double.NaN)),
                searchArguments.getLong("departure_time", 0),
                userIdentifier);
    }

    /** Hiện số chuyến sau lọc và giữ kết quả đang có khi tải thêm. */
    private void render(ResultsViewModel.State state) {
        adapter.setTrips(state.matches);
        ((TextView) findViewById(R.id.status_text))
                .setText("Chuyến xe phù hợp: " + state.matches.size());
        TextView noticeTextView = findViewById(R.id.en_route_notice);
        noticeTextView.setText(state.message);
        if (state.message.isEmpty()) {
            noticeTextView.setVisibility(View.GONE);
        } else {
            noticeTextView.setVisibility(View.VISIBLE);
        }
        if (state.busy) {
            findViewById(R.id.en_route_progress).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.en_route_progress).setVisibility(View.GONE);
        }
        findViewById(R.id.en_route_more)
                .setEnabled(state.sessionValid && !state.busy && state.hasMore);
        findViewById(R.id.en_route_refresh).setEnabled(state.sessionValid);
        sortSpinner.setEnabled(state.sessionValid);
        if (sortSpinner.getSelectedItemPosition() != state.sort.ordinal()) {
            sortSpinner.setSelection(state.sort.ordinal());
        }
    }
}
