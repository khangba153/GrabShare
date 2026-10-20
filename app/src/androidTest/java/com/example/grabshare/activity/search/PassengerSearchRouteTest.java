package com.example.grabshare.activity.search;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.grabshare.R;
import com.example.grabshare.contract.NavigationKeys;
import com.example.grabshare.model.Place;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** Kiểm tra chuyển tuyến và bản nháp; không bấm gửi hoặc ghi request kiểm thử. */
@RunWith(AndroidJUnit4.class)
public class PassengerSearchRouteTest {
    private static final String FIXTURE_TRIP_IDENTIFIER = "route-display-fixture";

    /** Parcel mô phỏng Android khôi phục Bundle, giữ tọa độ và ràng buộc chuyến/tài khoản. */
    @Test
    public void routeSurvivesParcelAndRejectsAnotherTripOrAccount() {
        Intent intent = new Intent();
        PassengerSearchRoute.attach(
                intent,
                FIXTURE_TRIP_IDENTIFIER,
                "fixture-passenger",
                pickupFixture(),
                dropoffFixture());
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(intent.getExtras());
            parcel.setDataPosition(0);
            Bundle restored = parcel.readBundle(getClass().getClassLoader());
            PassengerSearchRoute route =
                    PassengerSearchRoute.read(
                            restored, FIXTURE_TRIP_IDENTIFIER, "fixture-passenger");
            assertNotNull(route);
            assertEquals(pickupFixture().getLatitude(), route.getPickup().getLatitude());
            assertEquals(dropoffFixture().getLongitude(), route.getDropoff().getLongitude());
            assertNull(PassengerSearchRoute.read(restored, "another-trip", "fixture-passenger"));
            assertNull(
                    PassengerSearchRoute.read(restored, FIXTURE_TRIP_IDENTIFIER, "another-user"));
        } finally {
            parcel.recycle();
        }
        PassengerSearchRoute.attach(
                intent,
                FIXTURE_TRIP_IDENTIFIER,
                "fixture-passenger",
                new Place("Thiếu tọa độ", Double.NaN, 106.6),
                dropoffFixture());
        assertNull(
                PassengerSearchRoute.read(
                        intent.getExtras(), FIXTURE_TRIP_IDENTIFIER, "fixture-passenger"));
    }

    /** Review nhận sẵn điểm đón dùng gửi và giữ lời nhắn/lộ trình sau xoay màn. */
    @Test
    public void reviewPrefillsPickupAndKeepsBothPointsAfterRecreation() {
        Assume.assumeNotNull(FirebaseAuth.getInstance().getCurrentUser());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, RequestReviewActivity.class);
        intent.putExtra(NavigationKeys.TRIP_ID, FIXTURE_TRIP_IDENTIFIER);
        PassengerSearchRoute.attach(
                intent,
                FIXTURE_TRIP_IDENTIFIER,
                FirebaseAuth.getInstance().getCurrentUser().getUid(),
                pickupFixture(),
                dropoffFixture());
        try (ActivityScenario<RequestReviewActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(
                    new ActivityScenario.ActivityAction<RequestReviewActivity>() {
                        /** Chỉ thay bản nháp trên RAM, không tạo request. */
                        @Override
                        public void perform(RequestReviewActivity activity) {
                            assertReviewRoute(activity);
                            ((EditText) activity.findViewById(R.id.review_message))
                                    .setText("Lời nhắn kiểm thử");
                        }
                    });
            scenario.recreate();
            scenario.onActivity(
                    new ActivityScenario.ActivityAction<RequestReviewActivity>() {
                        /** Xác nhận hai điểm và lời nhắn không mất khi màn được tạo lại. */
                        @Override
                        public void perform(RequestReviewActivity activity) {
                            assertReviewRoute(activity);
                            assertEquals(
                                    "Lời nhắn kiểm thử",
                                    ((EditText) activity.findViewById(R.id.review_message))
                                            .getText()
                                            .toString());
                        }
                    });
        }
    }

    /** Caller cũ chỉ có ID vẫn được chọn điểm đón, không tự gán điểm xuất phát chủ xe. */
    @Test
    public void directReviewWithoutSearchRouteKeepsLocationPicker() {
        Assume.assumeNotNull(FirebaseAuth.getInstance().getCurrentUser());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, RequestReviewActivity.class);
        intent.putExtra(NavigationKeys.TRIP_ID, FIXTURE_TRIP_IDENTIFIER);
        try (ActivityScenario<RequestReviewActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(
                    new ActivityScenario.ActivityAction<RequestReviewActivity>() {
                        /** Không lẫn tuyến của lần tìm trước vào caller chỉ có mã chuyến. */
                        @Override
                        public void perform(RequestReviewActivity activity) {
                            assertEquals(
                                    View.GONE,
                                    activity.findViewById(R.id.passenger_search_route)
                                            .getVisibility());
                            assertEquals(
                                    View.VISIBLE,
                                    activity.findViewById(R.id.review_pickup).getVisibility());
                            assertNull(
                                    new ViewModelProvider(activity)
                                            .get(RequestReviewViewModel.class)
                                            .pickup);
                        }
                    });
        }
    }

    /** Chạy luồng Detail → Review thật với chuyến đang mở; không bấm gửi yêu cầu. */
    @Test
    public void liveDetailForwardsRouteAfterRecreation() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live")));
        Assume.assumeNotNull(FirebaseAuth.getInstance().getCurrentUser());
        final String userIdentifier = FirebaseAuth.getInstance().getCurrentUser().getUid();
        QuerySnapshot snapshot =
                Tasks.await(
                        FirebaseFirestore.getInstance()
                                .collection("trips")
                                .whereEqualTo("status", "OPEN")
                                .whereGreaterThanOrEqualTo("departureAt", Timestamp.now())
                                .orderBy("departureAt")
                                .get(Source.SERVER),
                        30,
                        TimeUnit.SECONDS);
        String selectedTripIdentifier = null;
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            if (!userIdentifier.equals(document.getString("driverId"))) {
                selectedTripIdentifier = document.getId();
                break;
            }
        }
        Assume.assumeNotNull(selectedTripIdentifier);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), TripDetailActivity.class);
        intent.putExtra(NavigationKeys.TRIP_ID, selectedTripIdentifier);
        PassengerSearchRoute.attach(
                intent, selectedTripIdentifier, userIdentifier, pickupFixture(), dropoffFixture());
        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(RequestReviewActivity.class.getName(), null, false);
        Activity reviewActivity = null;
        try (ActivityScenario<TripDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.recreate();
            scenario.onActivity(
                    new ActivityScenario.ActivityAction<TripDetailActivity>() {
                        /** Chờ listener online rồi bấm hành động mở Review đúng một lần. */
                        @Override
                        public void perform(final TripDetailActivity activity) {
                            assertRouteLabels(activity);
                            final OnlineTripViewModel model =
                                    new ViewModelProvider(activity).get(OnlineTripViewModel.class);
                            model.getState()
                                    .observe(
                                            activity,
                                            new Observer<OnlineTripViewModel.State>() {
                                                /**
                                                 * Chỉ điều hướng sau khi dữ liệu chuyến cho phép
                                                 * thao tác.
                                                 */
                                                @Override
                                                public void onChanged(
                                                        OnlineTripViewModel.State state) {
                                                    if (activity.findViewById(R.id.primary_action)
                                                            .isEnabled()) {
                                                        model.getState().removeObserver(this);
                                                        activity.findViewById(R.id.primary_action)
                                                                .performClick();
                                                    }
                                                }
                                            });
                        }
                    });
            reviewActivity = monitor.waitForActivityWithTimeout(15000);
            assertTrue("Detail phải mở Review", reviewActivity instanceof RequestReviewActivity);
            final Activity openedReview = reviewActivity;
            instrumentation.runOnMainSync(
                    new Runnable() {
                        /** Đối chiếu form thật sau hai lần chuyển màn mà không gửi yêu cầu. */
                        @Override
                        public void run() {
                            assertReviewRoute((RequestReviewActivity) openedReview);
                        }
                    });
        } finally {
            instrumentation.removeMonitor(monitor);
            if (reviewActivity != null) {
                final Activity openedReview = reviewActivity;
                instrumentation.runOnMainSync(
                        new Runnable() {
                            /** Đóng màn do kiểm thử mở để không giữ listener. */
                            @Override
                            public void run() {
                                openedReview.finish();
                            }
                        });
            }
        }
    }

    /** Dùng nhãn fixture để không chụp hoặc ghi log địa điểm riêng của người dùng. */
    private static Place pickupFixture() {
        return new Place("Điểm đón kiểm thử", 10.7830141, 106.6948927);
    }

    /** Điểm xuống độc lập với đích chủ xe để phát hiện gán nhầm hai lộ trình. */
    private static Place dropoffFixture() {
        return new Place("Điểm xuống kiểm thử", 10.7609491, 106.6679786);
    }

    /** Form không yêu cầu nhập lại và điểm dùng submit là điểm đón đã tìm. */
    private static void assertReviewRoute(RequestReviewActivity activity) {
        assertRouteLabels(activity);
        assertEquals(View.GONE, activity.findViewById(R.id.review_pickup).getVisibility());
        Place pickup = new ViewModelProvider(activity).get(RequestReviewViewModel.class).pickup;
        assertNotNull(pickup);
        assertEquals(pickupFixture().getName(), pickup.getName());
        assertEquals(pickupFixture().getLatitude(), pickup.getLatitude());
        assertEquals(pickupFixture().getLongitude(), pickup.getLongitude());
    }

    /** Cả Detail và Review phải hiển thị cùng hai điểm của người đi nhờ. */
    private static void assertRouteLabels(Activity activity) {
        assertEquals(
                View.VISIBLE, activity.findViewById(R.id.passenger_search_route).getVisibility());
        assertEquals(
                pickupFixture().getName(),
                ((TextView) activity.findViewById(R.id.passenger_search_pickup))
                        .getText()
                        .toString());
        assertEquals(
                dropoffFixture().getName(),
                ((TextView) activity.findViewById(R.id.passenger_search_dropoff))
                        .getText()
                        .toString());
    }
}
