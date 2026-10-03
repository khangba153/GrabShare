package com.example.grabshare;

/** Screens available in this clone. */
enum UiScreen {
    LOGIN(R.layout.screen_login, "Đăng nhập"),
    REGISTER(R.layout.screen_register, "Đăng ký"),
    RESET_PASSWORD(R.layout.screen_reset_password, "Đặt mật khẩu mới"),
    ACCOUNT(R.layout.screen_account, "Tài khoản"),
    EDIT_PROFILE(R.layout.screen_edit_profile, "Chỉnh sửa hồ sơ"),
    HELP(R.layout.screen_help, "Trợ giúp"),
    TERMS(R.layout.screen_terms, "Điều khoản"),
    DRIVER_TRIPS(R.layout.screen_driver_trips, "Chuyến của tôi"),
    POST(R.layout.screen_post, "Đăng chuyến"),
    EDIT_TRIP(R.layout.screen_edit_trip, "Chỉnh sửa chuyến"),
    REQUESTS(R.layout.screen_requests, "Yêu cầu đi nhờ"),
    REQUEST_DETAIL(R.layout.screen_request_detail, "Chi tiết yêu cầu"),
    MANAGE(R.layout.screen_manage, "Quản lý chuyến"),
    NOTIFICATIONS(R.layout.screen_notifications, "Thông báo");

    final int layout;
    final String title;
    UiScreen(int layout, String title) { this.layout = layout; this.title = title; }
}
