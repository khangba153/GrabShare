package com.example.grabshare;

/** Screens contributed by member 1. */
enum UiScreen {
    LOGIN(R.layout.screen_login, "Đăng nhập"),
    REGISTER(R.layout.screen_register, "Đăng ký"),
    RESET_PASSWORD(R.layout.screen_reset_password, "Đặt mật khẩu mới"),
    ACCOUNT(R.layout.screen_account, "Tài khoản"),
    EDIT_PROFILE(R.layout.screen_edit_profile, "Chỉnh sửa hồ sơ"),
    HELP(R.layout.screen_help, "Trợ giúp"),
    TERMS(R.layout.screen_terms, "Điều khoản");

    final int layout;
    final String title;
    UiScreen(int layout, String title) { this.layout = layout; this.title = title; }
}
