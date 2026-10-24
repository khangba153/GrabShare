# Hướng dẫn debug FCM trên Android

Tài liệu này dùng tạm trên nhánh `debug/fcm-registration-pipeline`. Không cần deploy Cloud Function để kiểm tra đến bước đăng ký FCM và ghi FID vào Firestore.

## 1. Lấy nhánh debug

Trong Android Studio, fetch các nhánh remote, rồi checkout:

`origin/debug/fcm-registration-pipeline`

Sau đó Sync Project with Gradle Files, build và cài app lên thiết bị cần kiểm tra.

## 2. Mở Logcat

Trong Android Studio:
1. Mở **View > Tool Windows > Logcat**.
2. Chọn đúng thiết bị và process của GrabShare.
3. Lọc theo tag `FCM_DEBUG` (hoặc tìm chuỗi `FCM_DEBUG`).
4. Xóa log cũ, force stop app, mở lại app và đăng nhập.

Tag log không in toàn bộ FID. UID/FID được che bớt để tránh đưa định danh đầy đủ vào log.

## 3. Các mốc thành công mong đợi

Khi mở app và đăng nhập, log thường có các mốc sau:

1. `APP_START`: package name và Firebase project ID mà APK đang dùng.
2. `AUTH_STATE`: Firebase Auth nhận ra user đã đăng nhập.
3. `FCM_REGISTER_START`: app bắt đầu đăng ký instance với FCM.
4. `FCM_REGISTER_SUCCESS`: lời gọi đăng ký hoàn tất.
5. `FCM_CALLBACK_REGISTERED`: callback xác nhận FCM đã đăng ký instance và cung cấp FID.
6. `FIRESTORE_READ_START`, sau đó `FIRESTORE_DOCUMENT_MISSING` hoặc `FIRESTORE_FID_CHANGED`.
7. `FIRESTORE_WRITE_START`, sau đó `FIRESTORE_WRITE_SUCCESS`.

Thứ tự giữa `FCM_REGISTER_SUCCESS` và `FCM_CALLBACK_REGISTERED` có thể khác nhau do tác vụ bất đồng bộ.

## 4. Đọc kết quả để khoanh vùng lỗi

| Log cuối cùng / lỗi | Vùng cần kiểm tra |
| --- | --- |
| Không có `APP_START` | APK/process chưa chạy đúng bản debug hoặc Logcat đang chọn sai process/tag. |
| `APP_START` có Firebase project ID khác project đang mở trên Firebase Console | APK dùng nhầm `google-services.json` hoặc đang trỏ sang Firebase project khác. |
| Có `APP_START` nhưng không có `AUTH_STATE` với user | Kiểm tra luồng đăng nhập và trạng thái Firebase Auth. |
| Có `AUTH_STATE` nhưng không có `FCM_REGISTER_START` | Kiểm tra `GrabShareApplication` đã khai báo trong Manifest và lỗi khởi động trước khi gọi FCM. |
| `FCM_REGISTER_FAIL` hoặc `FCM_REGISTER_EXCEPTION` | Đọc exception đầy đủ ngay dưới dòng log; kiểm tra Firebase config, Manifest metadata và kết nối mạng. |
| `FCM_REGISTER_SUCCESS` nhưng không có `FCM_CALLBACK_REGISTERED` | Kiểm tra khai báo service `GrabShareFirebaseMessagingService` trong Manifest và Firebase SDK. |
| Có `FCM_CALLBACK_REGISTERED` nhưng báo `FCM_CALLBACK_NO_USER` | Callback đến khi chưa có tài khoản đăng nhập; mở app và đăng nhập lại để kích hoạt đăng ký sau đăng nhập. |
| `FIRESTORE_READ_FAIL` | Đọc exception: có thể do Rules, mạng hoặc Firebase project. Repository vẫn thử ghi tiếp để lộ rõ lỗi write. |
| `FIRESTORE_WRITE_FAIL` với `PERMISSION_DENIED` | Kiểm tra Firestore Rules đã deploy trên Firebase Console có cho user ghi `fcmInstallations/{uid}` đúng schema chưa. File `firestore.rules` trong GitHub không tự deploy lên Firebase. |
| `FIRESTORE_WRITE_SUCCESS` nhưng không thấy document | Đối chiếu project ID ở `APP_START`, UID tài khoản và collection `fcmInstallations` trong đúng project. |
| `FCM_MESSAGE_RECEIVED` không xuất hiện | Android chưa nhận message; kiểm tra Cloud Function đã gửi thành công đến đúng FID và đúng Firebase project chưa. |
| `FCM_MESSAGE_DROPPED` | Đọc lý do: payload rỗng, chưa đăng nhập, hoặc `recipientId` không trùng UID hiện tại. |
| `FCM_DISPLAY_BLOCKED` | Message đã đến thiết bị nhưng quyền thông báo hoặc cài đặt thông báo của app đang tắt. |
| `FCM_NOTIFICATION_POSTED` | App đã gọi Android NotificationManager để hiển thị notification. |

## 5. Kiểm tra quyền thông báo

Tìm log `PERMISSION_STATE`. Hai giá trị quan trọng:
- `runtimePermissionGranted=true`: quyền Android 13+ đã được cấp hoặc hệ điều hành không yêu cầu quyền runtime này.
- `appNotificationsEnabled=true`: thông báo của GrabShare chưa bị tắt trong Android Settings.

**Quyền thông báo và đăng ký FCM là hai chuyện khác nhau.** Quyền bị từ chối có thể chặn hiển thị notification, nhưng không nên dùng việc bấm Allow làm bằng chứng duy nhất cho đăng ký FCM.

## 6. Gửi kết quả debug

Gửi lại các dòng từ `FCM_DEBUG` bắt đầu ở `APP_START` cho đến lỗi cuối cùng. Không cần gửi toàn bộ Logcat, và không gửi file `google-services.json` hoặc bất kỳ credential nào.

Chưa deploy Rules chỉ vì tài liệu này yêu cầu debug. Trước khi sửa Rules trên Firebase Console, hãy đối chiếu với nhóm và test trên Emulator/local project nếu có.
