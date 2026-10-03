# GrabShare — Thành viên 1 và tài nguyên dùng chung

Giao diện cộng đồng Android native XML + Java.

## Màn của thành viên 1

- `screen_login.xml`
- `screen_register.xml`
- `screen_reset_password.xml`
- `screen_account.xml`
- `screen_edit_profile.xml`
- `screen_help.xml`
- `screen_terms.xml`

Các màn nằm trong `app/src/main/res/layout`, có nội dung tĩnh để xem trực tiếp bằng Design trong Android Studio.

## File dùng chung

Cấu hình Gradle và wrapper; manifest; launcher icon; `activity_main.xml`; `view_*.xml`, `row_*.xml`, `dialog_confirm.xml`, `sheet_*.xml`; màu, style, theme, chuỗi và vector; `MainActivity.java`, `GrabShareUi.java`, `UiScreen.java`.

## Chạy

Mở project bằng Android Studio rồi Run. Vuốt trái/phải để xem 7 màn; cuộn dọc chỉ đọc nội dung dài. Nút và ô nhập chỉ để xem, không có popup hoặc nghiệp vụ.

Màn của thành viên 2, 3 và 4 chưa được đưa lên. Khi bổ sung màn, thêm mục trong `UiScreen.java` và danh sách `PAGES` trong `GrabShareUi.java`.

Build: `gradlew.bat :app:assembleDebug`, dùng JDK đi kèm Android Studio. Mỗi máy cấu hình SDK riêng; không commit `local.properties`.
