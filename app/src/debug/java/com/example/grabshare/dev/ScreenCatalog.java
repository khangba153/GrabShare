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
        new ScreenCatalog("Thành", "HOME", "Tìm chuyến", "search.HomeActivity"),
        new ScreenCatalog("Thành", "LOCATION", "Chọn địa điểm", "search.LocationActivity"),
        new ScreenCatalog("Thành", "RESULTS", "Kết quả tìm", "search.ResultsActivity"),
        new ScreenCatalog("Thành", "DETAIL", "Chi tiết chuyến", "search.TripDetailActivity"),
        new ScreenCatalog("Thành", "REVIEW", "Kiểm tra yêu cầu", "search.RequestReviewActivity"),
        new ScreenCatalog("Thuận", "PENDING", "Chờ xác nhận", "passenger.PendingActivity"),
        new ScreenCatalog("Thuận", "ACCEPTED", "Được chấp nhận", "passenger.AcceptedActivity"),
        new ScreenCatalog("Thuận", "REJECTED", "Bị từ chối", "passenger.RejectedActivity"),
        new ScreenCatalog("Thuận", "UPCOMING", "Chuyến sắp đi", "passenger.UpcomingActivity"),
        new ScreenCatalog("Thuận", "COMPLETE", "Hoàn thành / đánh giá", "passenger.CompleteActivity"),
        new ScreenCatalog("Thuận", "MY_TRIPS", "Chuyến đi nhờ của tôi", "passenger.MyTripsActivity"),
        new ScreenCatalog("Thuận", "FAVORITES", "Yêu thích", "passenger.FavoritesActivity"),
        new ScreenCatalog("Thuần", "DRIVER_TRIPS", "Chuyến chủ xe", "driver.DriverTripsActivity"),
        new ScreenCatalog("Thuần", "POST", "Đăng chuyến", "driver.PostTripActivity"),
        new ScreenCatalog("Thuần", "EDIT_TRIP", "Sửa chuyến", "driver.EditTripActivity"),
        new ScreenCatalog("Thuần", "REQUESTS", "Yêu cầu đi nhờ", "driver.DriverRequestsActivity"),
        new ScreenCatalog("Thuần", "REQUEST_DETAIL", "Chi tiết yêu cầu", "driver.RequestDetailActivity"),
        new ScreenCatalog("Thuần", "MANAGE", "Quản lý chuyến", "driver.ManageTripActivity"),
        new ScreenCatalog("Thuần", "NOTIFICATIONS", "Thông báo", "driver.NotificationsActivity")
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
