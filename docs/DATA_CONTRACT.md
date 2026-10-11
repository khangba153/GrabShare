# Hợp đồng dữ liệu GrabShare — v1

Schema được nhóm trưởng xác nhận ngày 11/10/2026, khớp bộ seed hiện tại.
Áp dụng cho cả nhóm; thay đổi field, ID hoặc trạng thái phải cập nhật tài liệu và
Rules trước khi merge. Đây là hợp đồng triển khai, chưa phải các chức năng đã làm.

## 1. Quy ước chung

- Project grabshare (334276006780), database (default), package com.example.grabshare.
- Một người dùng có thể vừa đăng chuyến vừa đi nhờ. Không có role cố định.
- Mỗi chuyến xe máy có tối đa một chỗ đi nhờ. Không thanh toán trực tuyến.
- Tên field phân biệt hoa/thường, không tự đổi sang tiếng Việt hoặc snake_case.
- ID nằm trên document; model Java có id chỉ để UI sử dụng, getter có @Exclude.
- Dùng String cho ID và trạng thái, Timestamp cho thời gian, Long cho integer,
  Double cho tọa độ và Boolean cho isRead. Không lưu giờ hoặc tiền dạng chuỗi.
- Kiểu wrapper có thể null khi field bị thiếu hoặc timestamp chưa trả về;
  màn hình phải kiểm tra null, không tự coi dữ liệu thiếu là 0 hoặc hợp lệ.
- Tạo/cập nhật bằng server timestamp tại lớp ghi dữ liệu sau này; giữ createdAt
  khi cập nhật. departureAt là thời gian người dùng chọn, phải ở tương lai khi đăng.
- Chuỗi tùy chọn dùng ""; các field đã khai báo vẫn phải có trong document hợp lệ.
- Không lưu mật khẩu, token, điểm đánh giá giả hoặc dữ liệu bí mật trong model.

## 2. Địa điểm dùng chung — Place

Là map lồng trong document, không có collection Place và không có id.

| Field | Firestore / Java | Điều kiện |
|---|---|---|
| name | string / String | Có nội dung, tối đa 200 ký tự |
| latitude | number / Double | Từ -90 đến 90 |
| longitude | number / Double | Từ -180 đến 180 |

## 3. users/{uid} — User (riêng tư)

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| email | String | Khớp email tài khoản Authentication |
| vehiclePlate | String | Tối đa 20 ký tự; có thể rỗng nếu chưa đăng xe |
| createdAt | Timestamp | Thời gian tạo |
| updatedAt | Timestamp | Thời gian cập nhật |

Chỉ chủ tài khoản đọc document này, không list users. Tên hiển thị và tên xe
lấy từ publicProfiles. Đăng ký Auth thành công nhưng lưu hồ sơ thất bại phải có
luồng tạo lại hồ sơ; không tự xóa Auth để che lỗi.

## 4. publicProfiles/{uid} — PublicProfile

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| displayName | String | 1–80 ký tự sau khi trim |
| vehicleName | String | Tối đa 80 ký tự; có thể rỗng |
| createdAt | Timestamp | Thời gian tạo |
| updatedAt | Timestamp | Thời gian cập nhật |

Người đã đăng nhập đọc được. Chỉ chính chủ sửa khi có Rules ghi.
Đăng chuyến cần tên xe và biển số hợp lệ; dùng avatar tĩnh, chưa upload ảnh.
Không đưa email hoặc biển số của chủ xe vào publicProfiles.

## 5. trips/{tripId} — Trip

tripId của chuyến mới là auto ID Firestore. Chuyến seed có ID cố định seed_trip_*.

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| driverId | String | UID chủ xe, không đổi sau tạo |
| origin | Place | Điểm đi |
| destination | Place | Điểm đến, khác điểm đi |
| departureAt | Timestamp | Thời gian khởi hành |
| costVnd | Long | Số nguyên VNĐ >= 0; 0 là miễn phí |
| note | String | Tối đa 500 ký tự, có thể rỗng |
| status | String | OPEN / FULL / COMPLETED / CANCELLED |
| availableSeats | Long | Chỉ 0 hoặc 1 |
| acceptedRequestId | String | "" khi chưa nhận; ID request khi đã nhận |
| createdAt | Timestamp | Thời gian tạo |
| updatedAt | Timestamp | Thời gian cập nhật |

## 6. requests/{tripId}_{passengerUid} — TripRequest

ID cố định ngăn một người tạo nhiều yêu cầu cho một chuyến; không gửi lại sau
REJECTED hoặc CANCELLED trong phạm vi đồ án. Chủ xe không gửi yêu cầu cho chuyến mình.

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| tripId | String | ID chuyến, không đổi |
| passengerId | String | UID người gửi, không đổi |
| driverId | String | Khớp driverId của chuyến, không đổi |
| pickup | Place | Điểm đón thỏa thuận |
| message | String | Tối đa 500 ký tự, có thể rỗng |
| status | String | PENDING / ACCEPTED / REJECTED / CANCELLED |
| createdAt | Timestamp | Thời gian tạo |
| updatedAt | Timestamp | Thời gian cập nhật |

## 7. favorites/{userUid}_{tripId} — Favorite

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| userId | String | Người sở hữu |
| tripId | String | Chuyến được yêu thích |
| createdAt | Timestamp | Thời gian thêm |

Thêm theo ID cố định, bỏ yêu thích bằng delete; không tạo bản trùng.

## 8. ratings/{tripId}_{authorUid} — Rating

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| tripId | String | Chuyến đã COMPLETED |
| authorId | String | Người đi nhờ thuộc acceptedRequestId |
| targetUserId | String | Chủ xe của chuyến |
| score | Long | Số nguyên 1–5 |
| comment | String | Tối đa 500 ký tự, có thể rỗng |
| createdAt | Timestamp | Thời gian đánh giá |

Một đánh giá/chuyến/người, chưa sửa hoặc xóa. Điểm trung bình tính từ ratings
theo targetUserId; chưa có đánh giá thì hiển thị “Chưa có đánh giá”.

## 9. notifications/{eventId} — AppNotification

| Field | Kiểu Java | Điều kiện |
|---|---|---|
| recipientId | String | Người nhận |
| actorId | String | Người thực hiện sự kiện |
| type | String | Một trong các loại bên dưới |
| tripId | String | Chuyến liên quan |
| requestId | String | Yêu cầu liên quan hoặc "" nếu không có |
| isRead | Boolean | false lúc tạo, người nhận đổi thành true |
| createdAt | Timestamp | Thời gian sự kiện |

| type | Người tạo → người nhận | ID cố định |
|---|---|---|
| REQUEST_SENT | Người đi nhờ → chủ xe | requestId_REQUEST_SENT |
| REQUEST_ACCEPTED | Chủ xe → người đi nhờ | requestId_REQUEST_ACCEPTED |
| REQUEST_REJECTED | Chủ xe → người đi nhờ | requestId_REQUEST_REJECTED |
| REQUEST_CANCELLED | Người đi nhờ → chủ xe | requestId_REQUEST_CANCELLED |
| TRIP_CANCELLED | Chủ xe → người đã được nhận | tripId_recipientUid_TRIP_CANCELLED |
| TRIP_COMPLETED | Chủ xe → người đã được nhận | tripId_recipientUid_TRIP_COMPLETED |

Thông báo trong app; UI tạo nội dung từ type, không có push khi app đóng.
Với chuyến hủy chưa nhận ai, người có PENDING thấy trạng thái hủy qua dữ liệu trip;
không tạo thông báo hàng loạt. Phải ghi thông báo cùng batch/transaction với sự kiện,
kiểm tra actor/recipient qua Rules; không ghi tùy ý vào inbox người khác.

## 10. Trạng thái và transaction

| Thao tác | Điều kiện | Kết quả |
|---|---|---|
| Đăng chuyến | Đăng nhập, có xe, giờ tương lai | OPEN, 1 chỗ, acceptedRequestId="" |
| Sửa chuyến | Chính chủ, OPEN, trước giờ đi | Đổi tuyến/giờ/chi phí/ghi chú, giữ ID |
| Gửi yêu cầu | OPEN, 1 chỗ, trước giờ đi, chưa có request ID | PENDING, không trừ chỗ |
| Chấp nhận | Chính chủ, request PENDING, trip OPEN và trước giờ đi | Request ACCEPTED + trip FULL, 0 chỗ, gán acceptedRequestId |
| Từ chối | Chính chủ, request PENDING, trip OPEN/FULL, trước giờ đi | Request REJECTED, không đổi số chỗ |
| Hủy yêu cầu chờ | Người gửi, PENDING, trip OPEN/FULL, trước giờ đi | Request CANCELLED, không đổi chỗ |
| Hủy yêu cầu đã nhận | Người gửi, ACCEPTED, trip FULL, đúng acceptedRequestId, trước giờ đi | Request CANCELLED + trip OPEN, 1 chỗ, xóa acceptedRequestId |
| Hủy chuyến | Chính chủ, OPEN/FULL, trước giờ đi | Trip CANCELLED, 0 chỗ, giữ acceptedRequestId lịch sử |
| Hoàn thành | Chính chủ, FULL, đã đến giờ đi | Trip COMPLETED, 0 chỗ, giữ acceptedRequestId |
| Đánh giá | Người đã được nhận, trip COMPLETED, chưa có rating | Tạo Rating |

Chấp nhận/hủy yêu cầu đã nhận phải transaction đọc lại trip và request trước ghi;
không lấy dữ liệu hiển thị cũ để quyết định. Gửi yêu cầu/hoàn thành/hủy chuyến cũng
đọc lại điều kiện trong transaction. Đọc mọi document cần thiết trước các lệnh ghi.
Không đặt thao tác UI hoặc gửi mạng phụ trong callback transaction có thể chạy lại.
Rules ghi sau này phải kiểm tra cả trạng thái liên quan, không chỉ kiểm tra UID.

Các PENDING khác giữ nguyên khi nhận một người; không thể nhận thêm lúc FULL.
Sau khi mở lại chỗ, chủ xe có thể nhận PENDING khác nếu còn trước giờ đi.
Hủy chuyến không cập nhật hàng loạt request: UI ưu tiên trip CANCELLED, giữ lịch sử.
COMPLETED/CANCELLED là trạng thái cuối. OPEN quá giờ vẫn là OPEN trong lịch sử,
không hiện trên tìm chuyến, không có tác vụ tự chuyển trạng thái.

## 11. Hợp đồng đọc và index

| Màn / mục đích | Truy vấn | Người phụ trách |
|---|---|---|
| Hồ sơ riêng | users.document(uid) | Nhóm trưởng |
| Tên/xe chủ chuyến | publicProfiles.document(driverId) | Mọi người dùng model chung |
| Tìm chuyến | trips: status=OPEN, departureAt trong ngày và >= hiện tại, orderBy departureAt ASC, limit 50 | A |
| Chuyến chủ xe | trips: driverId=uid, orderBy departureAt DESC | C |
| Yêu cầu đã gửi | requests: passengerId=uid, orderBy createdAt DESC | B |
| Yêu cầu nhận vào | requests: driverId=uid, tùy chọn tripId, orderBy createdAt DESC | C |
| Yêu thích | favorites: userId=uid, sau đó đọc từng tripId | B |
| Điểm đánh giá | ratings: targetUserId=driverId | A đọc / B ghi |
| Thông báo | notifications: recipientId=uid, orderBy createdAt DESC | C |

Kết quả tìm chuyến là tập tối đa 50 ứng viên; lọc khoảng cách điểm đi/đến trong
Java, không tuyên bố khớp toàn bộ tuyến. Bán kính/lựa chọn bộ lọc A đề xuất rồi
nhóm trưởng chốt trước khi viết tìm kiếm. Không ghép query tất cả requests rồi
lọc theo UID trong Java. Index tổng hợp lập từ query đã chốt và lưu vào Git;
chưa tạo/deploy index trong bước này. Không mở Rules để khắc phục lỗi thiếu index.

## 12. Cách dùng model

Thư mục app/src/main/java/com/example/grabshare/model có 8 model.
Constructor rỗng để SDK ánh xạ; constructor đủ field để tạo dữ liệu;
getter/setter không kiểm tra nghiệp vụ, không gọi Firebase hoặc điều hướng.

```java
Trip trip = snapshot.toObject(Trip.class);
if (trip != null) {
    trip.setId(snapshot.getId());
}
```

AppNotification dùng getIsRead()/setIsRead() để giữ đúng field isRead.
Không thêm isRead() hoặc getRead() tạo property read khác tên.
Chưa ghi dữ liệu bằng các model trong Rules base. Việc gán server timestamp
thực hiện tại repository bằng map/batch/transaction, không bằng Timestamp.now().

## 13. Offline và thông tin qua màn

Chữ ký đã chốt ở HANDOFF_CONTRACT.md; package contract cung cấp NavigationKeys,
LocationKeys, RepositoryCallback, FavoriteStore, TripCacheStore và CachedTrip.
Đây là khai báo local đã compile Debug; Activity/repository thật và việc push còn chưa làm.

- B sở hữu SQLite cache các chuyến đã xem, khóa (userId, tripId), savedAt.
- Lưu snapshot trip, tên/xe công khai cần hiển thị; không lưu hồ sơ users hoặc token.
- Mở chi tiết online thành công → A chuyển snapshot cho cache dùng chung của B.
- Offline chỉ xem cache, hiện nhãn dữ liệu đã lưu và thời gian lưu.
- Không đặt/hủy/nhận/đánh giá offline, không tạo hàng đợi ghi SQLite.
- Tách cache theo tài khoản; xóa cache tài khoản khi đăng xuất theo hàm B cung cấp.
- Qua Activity bằng Intent extras trip_id hoặc request_id; không truyền model
  Parcelable/Serializable ở bước này. Xoay màn hình giữ ID và dữ liệu form cần thiết.

## 14. Dữ liệu demo và trạng thái kiểm chứng

Seed: users 3, publicProfiles 3, trips 7, requests 6, favorites 2, ratings 1,
notifications 5 = 27 document. Tài khoản/ID chi tiết ở scripts/seed.config.json.
Ngày chuyến seed là ngày cố định lúc seed; khi đã quá giờ tìm kiếm sẽ không hiện.
Nhóm trưởng reset có phối hợp khi cần demo; thành viên không tự chạy --apply.

User đã báo seed thành công và cung cấp ảnh Console. Rules đọc đã Publish theo
ảnh; user báo Playground cho phép đọc trip khi đăng nhập, từ chối khi chưa đăng
nhập, từ chối đọc users của người khác, cho phép users chính chủ.
Chưa kiểm tra toàn bộ quyền của 7 collection, chưa kiểm chứng đọc từ Android.
Rules base vẫn chặn mọi ghi. Model/contract compile Debug thành công; chưa chạy emulator hoặc kiểm thử.

## Tài liệu tham khảo

- https://firebase.google.com/docs/firestore/manage-data/add-data
- https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Exclude
- https://firebase.google.com/docs/firestore/security/rules-query
