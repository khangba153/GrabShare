# Quy ước tích hợp FCM cho GrabShare

Tài liệu này quy định cách phần Android và Cloud Function phối hợp gửi thông báo đẩy cho ứng dụng GrabShare. Hai bên cần dùng thống nhất tên collection, trường dữ liệu và payload được mô tả bên dưới.

## 1. Cấu trúc lưu Firebase Installation ID

Android lưu một Firebase Installation ID (FID) cho tài khoản đang đăng nhập tại document:

`fcmInstallations/{uid}`

Trong đó, `uid` là Firebase Authentication UID của người dùng.

Các trường của document:

- `fid`: Firebase Installation ID do Firebase Cloud Messaging (FCM) cung cấp qua callback `onRegistered()`, hoặc lấy từ Firebase Installations SDK.
- `updatedAt`: thời điểm cập nhật do Firestore tạo bằng server timestamp.

Firestore Rules chỉ cho phép người dùng đã đăng nhập đọc, tạo, cập nhật hoặc xóa document của chính UID đó. Không cho phép client liệt kê toàn bộ document FID. Cloud Function phải đọc dữ liệu bằng Firebase Admin SDK.

## 2. Sự kiện kích hoạt Cloud Function

Cloud Function chỉ lắng nghe sự kiện tạo document mới trong collection:

`notifications/{notificationId}`

Các loại thông báo hiện tại của GrabShare gồm:

- `REQUEST_SENT`: hành khách gửi yêu cầu đi chung.
- `REQUEST_ACCEPTED`: tài xế chấp nhận yêu cầu.
- `REQUEST_REJECTED`: tài xế từ chối yêu cầu.
- `REQUEST_CANCELLED`: yêu cầu đi chung bị hủy.
- `TRIP_CANCELLED`: chuyến đi bị hủy.
- `TRIP_COMPLETED`: chuyến đi hoàn thành.

Hãy dùng trigger kiểu **on-create** (chỉ chạy khi document được tạo), không dùng **on-write**. Khi người dùng đánh dấu thông báo đã đọc, document sẽ được cập nhật; cập nhật này không được gửi lại thông báo đẩy.

Cloud Function cũng không được tạo thêm document trong chính collection `notifications` để ghi kết quả gửi, vì document mới có thể kích hoạt lại function và tạo vòng lặp.

## 3. Tìm đúng thiết bị nhận

Khi có document thông báo mới, Cloud Function đọc trường `recipientId` trong document đó, sau đó tìm:

`fcmInstallations/{recipientId}`

Đọc trường `fid` trong document tìm được. Nếu document hoặc trường FID không tồn tại, kết thúc lần xử lý đó mà không gửi tin nhắn.

Sử dụng Firebase Admin SDK để gửi đến trường đích `fid`. Không dùng trường đích `token` kiểu cũ: tài liệu Firebase hiện tại đánh dấu trường đó là deprecated và khuyến nghị dùng `fid` sau khi client đăng ký bằng Firebase Installation ID.

Tài liệu chính thức: [Gửi thông báo bằng Firebase Admin SDK](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk?hl=vi).

## 4. Quy ước payload gửi xuống Android

Hãy gửi FCM dạng **data-only**: chỉ có trường `data`, không thêm đối tượng `notification`.

Lý do: service Android cần kiểm tra `recipientId` có khớp UID đang đăng nhập trên thiết bị hay không trước khi hiển thị thông báo. Nếu gửi thêm phần `notification`, Android có thể tự hiển thị thông báo khi ứng dụng chạy nền mà không đi qua bước kiểm tra UID trong `onMessageReceived()`.

Payload tối thiểu phải có các trường sau; **tất cả giá trị trong `data` đều phải là chuỗi**:

```json
{
  "recipientId": "<notifications/{id}.recipientId>",
  "notificationId": "<ID document trong notifications>",
  "type": "REQUEST_ACCEPTED",
  "title": "Yêu cầu đã được chấp nhận",
  "body": "Yêu cầu đi chung của bạn đã được chấp nhận."
}
```

Ý nghĩa:

- `recipientId`: UID người nhận, lấy từ document thông báo.
- `notificationId`: ID document thông báo mới tạo.
- `type`: loại sự kiện nghiệp vụ.
- `title`: tiêu đề hiện trên thông báo hệ thống.
- `body`: nội dung ngắn hiện trên thông báo hệ thống.

Dùng mức ưu tiên Android **high** khi thông báo thực sự cần đến kịp thời và hướng đến hành động của người dùng. Không dùng high priority cho dữ liệu không cần hiển thị thông báo. Giữ payload nhỏ và không đưa thông tin cá nhân hoặc chi tiết chuyến đi không cần thiết vào nội dung trên màn hình khóa.

## 5. Hành vi phía Android

Android kiểm tra `recipientId` trong payload với UID đang đăng nhập. Nếu không có phiên đăng nhập hoặc UID không khớp, service bỏ qua thông báo.

Nếu UID hợp lệ và ứng dụng đã được cấp quyền hiển thị thông báo, service tạo thông báo hệ thống. Khi người dùng chạm vào thông báo, ứng dụng mở `NotificationsActivity` (Inbox).

Android sử dụng `notificationId` làm ID thông báo cục bộ. Nếu cùng document được gửi lại, thông báo cục bộ có cùng ID sẽ được cập nhật/thay thế thay vì tạo thêm một mục hiển thị riêng.

## 6. Gửi trùng và lỗi gửi

Sự kiện Firestore có thể được giao nhiều lần, vì vậy một document không đảm bảo rằng Cloud Function chỉ được thực thi đúng một lần. Nếu nhóm cần hạn chế gửi trùng ở phía máy chủ, hãy bổ sung cơ chế idempotency dựa trên `notificationId` và lưu trạng thái gửi ở nơi riêng; không ghi ngược vào collection `notifications`.

FCM không đảm bảo giao tin đúng một lần. Cloud Function cần xử lý lỗi gửi và không được xem việc gọi API gửi thành công là bằng chứng rằng người nhận chắc chắn đã nhìn thấy thông báo.
