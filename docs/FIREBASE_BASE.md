# Kết nối dữ liệu chung GrabShare — giai đoạn base

## Cấu hình chung

- Thư mục dự án hiện tại: D:\GrabShare-Git.
- Firebase project: grabshare; project number: 334276006780.
- Android applicationId: com.example.grabshare.
- Database Firestore: (default).
- Mỗi thành viên dùng cùng file app/google-services.json và cấu hình Gradle trong repo.
- Không tạo Firebase project riêng, không seed lại để kết nối.
- Nhánh Git khác nhau vẫn truy cập cùng database; Git không chia database theo nhánh.
- Dùng Firebase Authentication để đăng nhập; google-services.json không thay thế đăng nhập.
- Thành viên phát triển Android không cần service account hay quyền quản trị Console.

## Rules hiện tại của base

File firestore.rules chỉ mở quyền đọc dữ liệu seed cho tài khoản đã đăng nhập.
Chưa mở đăng ký hồ sơ Firestore, sửa hồ sơ, đăng chuyến, gửi yêu cầu, đánh giá,
yêu thích hoặc đánh dấu thông báo đã đọc. Đây là bước chuẩn bị dữ liệu chung;
phải bổ sung Rules ghi theo từng chức năng trước khi thành viên triển khai phần ghi.
Rules cũ dùng model User gộp hồ sơ không phù hợp schema đã chốt.

| Collection | Quyền đọc từ ứng dụng |
|---|---|
| users | Chỉ get document có ID bằng UID đăng nhập; không list |
| publicProfiles | Tài khoản đã đăng nhập đọc hồ sơ công khai |
| trips | Tài khoản đã đăng nhập đọc chuyến và lịch sử chuyến |
| requests | Người đi nhờ hoặc chủ xe của yêu cầu |
| favorites | Người sở hữu theo userId |
| ratings | Tài khoản đã đăng nhập đọc đánh giá |
| notifications | Người nhận theo recipientId |

Chưa đăng nhập bị từ chối mọi thao tác. Quyền đọc trips bao gồm mọi trạng thái
để hiển thị lịch sử; màn tìm chuyến tự truy vấn status OPEN và thời gian phù hợp.

## Cách nhóm trưởng cập nhật Console

1. Mở Firebase Console, chọn project grabshare.
2. Vào Firestore Database → Rules.
3. Thay toàn bộ nội dung bằng file firestore.rules trong repo.
4. Bấm Publish, kiểm tra Console báo thành công và không có lỗi cú pháp.
5. Gửi ảnh nội dung đã xuất bản để đối chiếu trước khi chuyển bước.

Sửa file trong repo không tự xuất bản Rules lên Firebase. Publish Rules không sửa
hay xóa 27 document đã seed. Seed chạy bằng quyền quản trị IAM, nên việc seed
thành công không chứng minh tài khoản Android có quyền đọc dữ liệu.

## Quy ước truy vấn cho thành viên

Firestore Rules không tự lọc dữ liệu. Truy vấn phải giới hạn đúng chủ sở hữu.

- users: document(uid).get(), không gọi collection("users").get().
- requests của người đi nhờ: whereEqualTo("passengerId", uid).
- requests của chủ xe: whereEqualTo("driverId", uid).
- favorites: whereEqualTo("userId", uid).
- notifications: whereEqualTo("recipientId", uid).
- ratings theo người được đánh giá: whereEqualTo("targetUserId", uid).
- publicProfiles: document(driverId).get() để lấy tên và xe của chủ chuyến.
- Nếu bổ sung orderBy và Firestore yêu cầu composite index, nhóm trưởng ghi nhận
  truy vấn và tạo index dùng chung; không nới Rules để giải quyết lỗi index.

Không tải toàn bộ requests/favorites/notifications rồi lọc bằng Java vì truy vấn
đó bị từ chối. ID document lấy từ DocumentSnapshot.getId(), không yêu cầu field id.

## Kiểm tra sau khi Publish

Nhóm trưởng đã cung cấp ảnh Rules xuất bản và báo bốn kết quả Playground đúng:
trip cho phép khi xác thực, trip từ chối khi không xác thực, users người khác
từ chối, users chính chủ cho phép. Chưa xác nhận các collection còn lại hoặc
kết nối Android. Xem DATA_CONTRACT.md và TEAM_TASKS.md trước khi triển khai.

Dùng Rules Playground với get và UID đã biết; đây là kiểm tra riêng Rules,
chưa phải bằng chứng ứng dụng đã kết nối thành công.

| Trường hợp | Kết quả cần đạt |
|---|---|
| Không xác thực, get /trips/seed_trip_paid | Từ chối |
| D1 xác thực, get /trips/seed_trip_paid | Cho phép |
| D1 xác thực, get /users/lyl3DvFLjegzIFnggYgGxtGyQNu2 | Cho phép |
| P1 xác thực, get hồ sơ users của D1 ở trên | Từ chối |
| Tài khoản xác thực, tạo hoặc sửa trip | Từ chối trong giai đoạn base |

D1: lyl3DvFLjegzIFnggYgGxtGyQNu2.
P1: 4p8CTioum2TJwmNG30GK07gwN713.
P2: xc2sDuP38Lak32OAac5Q61mz7FG2.
Mật khẩu demo gửi riêng; không ghi mật khẩu vào repo, báo cáo hoặc ảnh minh chứng.

Nguồn: https://firebase.google.com/docs/firestore/security/rules-query
