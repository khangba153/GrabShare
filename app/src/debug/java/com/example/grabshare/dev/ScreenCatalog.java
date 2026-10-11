package com.example.grabshare.dev;

/** Danh sách màn theo hợp đồng; không tạo hoặc giả lập Activity nghiệp vụ. */
final class ScreenCatalog {
    static final ScreenCatalog[] SCREENS = {
        new ScreenCatalog("L", "LOGIN", "Đăng nhập", "account.LoginActivity"),
        new ScreenCatalog("L", "REGISTER", "Đăng ký", "account.RegisterActivity"),
        new ScreenCatalog("L", "RESET_PASSWORD", "Quên mật khẩu", "account.ResetPasswordActivity"),
        new ScreenCatalog("L", "ACCOUNT", "Tài khoản", "account.AccountActivity"),
        new ScreenCatalog("L", "EDIT_PROFILE", "Sửa hồ sơ", "account.EditProfileActivity"),
        new ScreenCatalog("L", "HELP", "Trợ giúp", "account.HelpActivity"),
        new ScreenCatalog("L", "TERMS", "Điều khoản", "account.TermsActivity"),
        new ScreenCatalog("A", "HOME", "Tìm chuyến", "search.HomeActivity"),
        new ScreenCatalog("A", "LOCATION", "Chọn địa điểm", "search.LocationActivity"),
        new ScreenCatalog("A", "RESULTS", "Kết quả tìm", "search.ResultsActivity"),
        new ScreenCatalog("A", "DETAIL", "Chi tiết chuyến", "search.TripDetailActivity"),
        new ScreenCatalog("A", "REVIEW", "Kiểm tra yêu cầu", "search.RequestReviewActivity"),
        new ScreenCatalog("B", "PENDING", "Chờ xác nhận", "passenger.PendingActivity"),
        new ScreenCatalog("B", "ACCEPTED", "Được chấp nhận", "passenger.AcceptedActivity"),
        new ScreenCatalog("B", "REJECTED", "Bị từ chối", "passenger.RejectedActivity"),
        new ScreenCatalog("B", "UPCOMING", "Chuyến sắp đi", "passenger.UpcomingActivity"),
        new ScreenCatalog("B", "COMPLETE", "Hoàn thành / đánh giá", "passenger.CompleteActivity"),
        new ScreenCatalog("B", "MY_TRIPS", "Chuyến đi nhờ của tôi", "passenger.MyTripsActivity"),
        new ScreenCatalog("B", "FAVORITES", "Yêu thích", "passenger.FavoritesActivity"),
        new ScreenCatalog("C", "DRIVER_TRIPS", "Chuyến chủ xe", "driver.DriverTripsActivity"),
        new ScreenCatalog("C", "POST", "Đăng chuyến", "driver.PostTripActivity"),
        new ScreenCatalog("C", "EDIT_TRIP", "Sửa chuyến", "driver.EditTripActivity"),
        new ScreenCatalog("C", "REQUESTS", "Yêu cầu đi nhờ", "driver.DriverRequestsActivity"),
        new ScreenCatalog("C", "REQUEST_DETAIL", "Chi tiết yêu cầu", "driver.RequestDetailActivity"),
        new ScreenCatalog("C", "MANAGE", "Quản lý chuyến", "driver.ManageTripActivity"),
        new ScreenCatalog("C", "NOTIFICATIONS", "Thông báo", "driver.NotificationsActivity")
    };

    final String group;
    final String preview;
    final String title;
    final String activity;

    /** Gắn màn preview với tên Activity mà thành viên sẽ tạo. */
    private ScreenCatalog(String group, String preview, String title, String activity) {
        this.group = group;
        this.preview = preview;
        this.title = title;
        this.activity = "com.example.grabshare.activity." + activity;
    }
}
