package com.example.grabshare.adapter.search;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.grabshare.R;
import com.example.grabshare.activity.search.ResultsActivity;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.repository.search.EnRouteSearch;
import com.google.firebase.Timestamp;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import static org.junit.Assert.*;

/** Kiểm tra layout dọc/ngang bằng fixture có nhãn rõ ràng, không cần tài khoản Firebase. */
@RunWith(AndroidJUnit4.class)
public class ResultsLayoutTest {
    /** Vẽ layout thật và metric thật của adapter, không chụp dữ liệu người dùng trên thiết bị. */
    @Test public void portraitAndLandscapeRenderRouteMetrics() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            /** Mỗi hướng đo lại kích thước, nội dung dài được cuộn thay vì cắt cố định. */
            @Override public void run() {
                Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
                Context context = new ContextThemeWrapper(base, R.style.Theme_GrabShare);
                for (int orientation = 0; orientation < 2; orientation++) {
                    View root = LayoutInflater.from(context).inflate(R.layout.screen_results, null, false);
                    ((TextView) root.findViewById(R.id.route_start)).setText("Nha Trang — điểm đón khách kiểm thử");
                    ((TextView) root.findViewById(R.id.route_end)).setText("Thanh Hóa — điểm trả khách kiểm thử");
                    ((TextView) root.findViewById(R.id.search_summary)).setText("Bạn muốn đi: 21/10/2026 · 10:00\nTìm chuyến chưa khởi hành trong cùng ngày (giờ Việt Nam).");
                    ((TextView) root.findViewById(R.id.status_text)).setText("DỮ LIỆU KIỂM THỬ — 1 kết quả minh họa giao diện, không phải chuyến thật trên Firebase.");
                    ((Spinner) root.findViewById(R.id.en_route_sort)).setAdapter(new ArrayAdapter<String>(context,
                            android.R.layout.simple_spinner_dropdown_item, new String[] {"Đề xuất: ít lệch → gần điểm đón → giá"}));
                    Trip trip = new Trip("fixture-driver", new Place("TP.HCM", 10.76, 106.66),
                            new Place("Hà Nội", 21.029, 105.854), Timestamp.now(), 50000L, "", "OPEN", 1L, "", Timestamp.now(), Timestamp.now());
                    trip.setId("fixture-trip");
                    TripAdapter adapter = new TripAdapter(new TripAdapter.OnTripClickListener() {
                        /** Không điều hướng khi vẽ fixture. */
                        @Override public void onTripClick(EnRouteSearch.Match match) { }
                    });
                    adapter.setTrips(Collections.singletonList(new EnRouteSearch.Match(trip, null, 400000, 350, 1900000, false)));
                    RecyclerView list = root.findViewById(R.id.ride_list);
                    list.setLayoutManager(new LinearLayoutManager(context));
                    list.setAdapter(adapter);
                    int width = orientation == 0 ? 1080 : 2200;
                    int height = orientation == 0 ? 2200 : 1080;
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, width, height);
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    root.draw(new Canvas(bitmap));
                    assertEquals(1, list.getChildCount());
                    TextView gap = list.getChildAt(0).findViewById(R.id.match_gap);
                    assertTrue(gap.getText().toString().contains("400 km"));
                    assertTrue(gap.getText().toString().contains("đường bộ"));
                    TextView detour = list.getChildAt(0).findViewById(R.id.match_detour);
                    assertTrue(detour.getText().toString().contains("0,35 km"));
                    assertTrue(gap.getMeasuredWidth() > 0);
                    File folder = base.getExternalFilesDir("task3-qa");
                    if (folder == null) throw new AssertionError("Missing QA output folder");
                    File output = new File(folder, orientation == 0 ? "portrait.png" : "landscape.png");
                    try (FileOutputStream stream = new FileOutputStream(output)) {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
                    } catch (java.io.IOException error) { throw new AssertionError(error); }
                    bitmap.recycle();
                }
            }
        });
    }

    /** Mở thiếu extra và xoay màn không hiển thị địa điểm mẫu hoặc chạy query ngầm. */
    @Test public void missingCriteriaAndRecreationShowSafeState() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<ResultsActivity> scenario = ActivityScenario.launch(new Intent(context, ResultsActivity.class))) {
            scenario.recreate();
            scenario.onActivity(new ActivityScenario.ActivityAction<ResultsActivity>() {
                /** Kiểm tra trạng thái thật của Activity sau tạo lại. */
                @Override public void perform(ResultsActivity activity) {
                    assertFalse(activity.findViewById(R.id.en_route_more).isEnabled());
                    assertFalse(activity.findViewById(R.id.en_route_refresh).isEnabled());
                    assertTrue(((TextView) activity.findViewById(R.id.status_text)).getText().toString().contains("Thiếu"));
                    assertTrue(((TextView) activity.findViewById(R.id.route_start)).getText().toString().contains("Chưa chọn"));
                }
            });
        }
    }
}
