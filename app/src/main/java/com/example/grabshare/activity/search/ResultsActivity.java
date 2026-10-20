package com.example.grabshare.activity.search;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
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
    private ResultsViewModel model;
    private TripAdapter adapter;
    private EnRouteSearch.Criteria criteria;
    private Spinner sortSpinner;

    /** Nối điều kiện Home, hàng đợi tìm chuyến và thao tác Detail bằng trip_id. */
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_results);
        ((TextView) findViewById(R.id.route_start)).setText("Chưa chọn điểm đón");
        ((TextView) findViewById(R.id.route_end)).setText("Chưa chọn điểm trả");
        model = new ViewModelProvider(this).get(ResultsViewModel.class);
        findViewById(R.id.header_back).setOnClickListener(new View.OnClickListener() {
            /** Trở về đúng màn nhập điều kiện. */
            @Override public void onClick(View view) { finish(); }
        });
        findViewById(R.id.search_summary_card).setOnClickListener(new View.OnClickListener() {
            /** Đổi điểm hoặc ngày bắt đầu từ Home, không dùng hàng đợi cũ. */
            @Override public void onClick(View view) { finish(); }
        });
        adapter = new TripAdapter(new TripAdapter.OnTripClickListener() {
            /** Detail đọc lại server; metric kết quả không cấp quyền gửi yêu cầu. */
            @Override public void onTripClick(EnRouteSearch.Match match) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (criteria == null || user == null || !criteria.userId.equals(user.getUid())) return;
                if (match.trip.getDepartureAt().toDate().getTime() <= System.currentTimeMillis()) {
                    model.revalidate();
                    return;
                }
                Intent intent = new Intent(ResultsActivity.this, TripDetailActivity.class);
                intent.putExtra(NavigationKeys.TRIP_ID, match.trip.getId());
                startActivity(intent);
            }
        });
        RecyclerView list = findViewById(R.id.ride_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        sortSpinner = findViewById(R.id.en_route_sort);
        sortSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[] { "Đề xuất: ít lệch → gần điểm đón → giá", "Giá thấp nhất",
                        "Tài xế xuất phát gần điểm đón nhất", "Ít lệch đường nhất" }));
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            /** Sắp xếp trên RAM ngay cả khi đang kiểm tra thêm ứng viên. */
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                model.setSort(EnRouteSearch.Sort.values()[position]);
            }
            /** Không xóa lựa chọn khi Spinner chưa có dòng đang chọn. */
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        findViewById(R.id.en_route_more).setOnClickListener(new View.OnClickListener() {
            /** Lấy ứng viên tiếp theo, không đọc lại Firestore. */
            @Override public void onClick(View view) { model.loadMore(); }
        });
        findViewById(R.id.en_route_refresh).setOnClickListener(new View.OnClickListener() {
            /** Bắt đầu snapshot mới từ máy chủ, vô hiệu hóa callback cũ. */
            @Override public void onClick(View view) { model.refresh(); }
        });
        findViewById(R.id.open_filters).setOnClickListener(new View.OnClickListener() {
            /** Giới hạn giá chỉ áp dụng danh sách đã có metric. */
            @Override public void onClick(View view) { showPriceFilter(); }
        });
        try {
            criteria = readCriteria(savedInstanceState);
            ((TextView) findViewById(R.id.route_start)).setText(criteria.pickup.getName());
            ((TextView) findViewById(R.id.route_end)).setText(criteria.dropoff.getName());
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy · HH:mm", new Locale("vi", "VN"));
            format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            ((TextView) findViewById(R.id.search_summary)).setText("Bạn muốn đi: "
                    + format.format(criteria.requestedAt) + "\nTìm chuyến chưa khởi hành trong cùng ngày (giờ Việt Nam).");
            if (savedInstanceState != null && model.getState().getValue() == null) {
                int selection = savedInstanceState.getInt("results_sort", 0);
                if (selection >= 0 && selection < EnRouteSearch.Sort.values().length) {
                    model.setSort(EnRouteSearch.Sort.values()[selection]);
                    sortSpinner.setSelection(selection);
                }
                if (savedInstanceState.containsKey("results_max_price")) {
                    model.setMaxPrice(savedInstanceState.getLong("results_max_price"));
                }
            }
            model.getState().observe(this, new Observer<ResultsViewModel.State>() {
                /** LiveData chỉ vẽ khi màn ở trạng thái hoạt động. */
                @Override public void onChanged(ResultsViewModel.State state) { render(state); }
            });
            model.initialize(criteria);
        } catch (IllegalArgumentException invalid) {
            ((TextView) findViewById(R.id.status_text)).setText("Thiếu hoặc sai điều kiện. Quay lại Home để chọn điểm và thời gian.");
            findViewById(R.id.en_route_more).setEnabled(false);
            findViewById(R.id.en_route_refresh).setEnabled(false);
            findViewById(R.id.open_filters).setEnabled(false);
            sortSpinner.setEnabled(false);
        }
    }

    /** Không tự query lại khi trở về từ Detail hoặc khi xoay màn. */
    @Override protected void onResume() { super.onResume(); if (model != null) model.revalidate(); }

    /** Giữ điều kiện và lựa chọn lọc qua process death, không serialize Trip. */
    @Override protected void onSaveInstanceState(Bundle state) {
        if (criteria != null) {
            state.putString("origin_name", criteria.pickup.getName());
            state.putDouble("origin_lat", criteria.pickup.getLatitude());
            state.putDouble("origin_lng", criteria.pickup.getLongitude());
            state.putString("dest_name", criteria.dropoff.getName());
            state.putDouble("dest_lat", criteria.dropoff.getLatitude());
            state.putDouble("dest_lng", criteria.dropoff.getLongitude());
            state.putLong("departure_time", criteria.requestedAt);
            state.putString("results_uid", criteria.userId);
            ResultsViewModel.State value = model.getState().getValue();
            if (value != null) {
                state.putInt("results_sort", value.sort.ordinal());
                if (value.maxPrice != null) state.putLong("results_max_price", value.maxPrice);
            }
        }
        super.onSaveInstanceState(state);
    }

    /** Không coi extra thiếu là tọa độ 0 hoặc lấy UID khác sau khi process bị hủy. */
    private EnRouteSearch.Criteria readCriteria(Bundle saved) {
        Bundle data = saved == null ? getIntent().getExtras() : saved;
        if (data == null) throw new IllegalArgumentException("Thiếu điều kiện tìm.");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = saved != null ? saved.getString("results_uid") : user == null ? null : user.getUid();
        return new EnRouteSearch.Criteria(new Place(data.getString("origin_name"),
                data.getDouble("origin_lat", Double.NaN), data.getDouble("origin_lng", Double.NaN)),
                new Place(data.getString("dest_name"), data.getDouble("dest_lat", Double.NaN),
                        data.getDouble("dest_lng", Double.NaN)), data.getLong("departure_time", 0), uid);
    }

    /** Trình bày rõ phạm vi đã kiểm tra và không giấu kết quả đang có lúc lấy thêm. */
    private void render(ResultsViewModel.State state) {
        adapter.setTrips(state.matches);
        ((TextView) findViewById(R.id.status_text)).setText(state.message);
        findViewById(R.id.en_route_progress).setVisibility(state.busy ? View.VISIBLE : View.GONE);
        findViewById(R.id.en_route_more).setEnabled(state.sessionValid && !state.busy && state.hasMore);
        findViewById(R.id.en_route_refresh).setEnabled(state.sessionValid);
        findViewById(R.id.open_filters).setEnabled(state.sessionValid);
        sortSpinner.setEnabled(state.sessionValid);
        if (sortSpinner.getSelectedItemPosition() != state.sort.ordinal()) sortSpinner.setSelection(state.sort.ordinal());
        ((TextView) findViewById(R.id.open_filters)).setText(state.maxPrice == null
                ? "Giá dự kiến: không giới hạn" : String.format(new Locale("vi", "VN"), "Giá dự kiến tối đa: %,dđ", state.maxPrice));
    }

    /** Nhập số nguyên VNĐ; để trống gỡ lọc, giữ dialog mở nếu dữ liệu không hợp lệ. */
    private void showPriceFilter() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Để trống để bỏ giới hạn");
        ResultsViewModel.State value = model.getState().getValue();
        if (value != null && value.maxPrice != null) input.setText(String.valueOf(value.maxPrice));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Giá dự kiến tối đa (VNĐ)")
                .setView(input).setNegativeButton("Hủy", null).setPositiveButton("Áp dụng", null).create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            /** Kiểm tra tràn số trước khi lọc trên RAM. */
            @Override public void onClick(View view) {
                String text = input.getText().toString().trim();
                try {
                    Long maximum = text.isEmpty() ? null : Long.valueOf(text);
                    if (maximum != null && maximum < 0) throw new NumberFormatException();
                    model.setMaxPrice(maximum);
                    dialog.dismiss();
                } catch (NumberFormatException invalid) { input.setError("Nhập số nguyên VNĐ hợp lệ, không âm."); }
            }
        });
    }
}
