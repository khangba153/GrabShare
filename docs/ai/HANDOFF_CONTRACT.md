# Hợp đồng bàn giao giữa các gói — chốt bước 2, v1.2

Nhóm trưởng đã đồng ý; A/B/C tự chọn, không cần nhập tên. Các khai báo ở package
com.example.grabshare.contract đã compile Debug và push main tại 02af6bd; repository và Activity
nghiệp vụ chưa được triển khai. Hợp đồng này không mở quyền ghi Firestore.

## 1. Mở màn theo ID

| Đích | Extra bắt buộc | Chủ file |
|---|---|---|
| activity.search.TripDetailActivity | NavigationKeys.TRIP_ID | A |
| activity.search.RequestReviewActivity | NavigationKeys.TRIP_ID | A |
| activity.passenger.PendingActivity | NavigationKeys.REQUEST_ID | B |
| activity.passenger.AcceptedActivity / RejectedActivity / UpcomingActivity | NavigationKeys.REQUEST_ID | B |
| activity.passenger.CompleteActivity | NavigationKeys.REQUEST_ID | B |
| activity.driver.EditTripActivity / ManageTripActivity / DriverRequestsActivity | NavigationKeys.TRIP_ID | C |
| activity.driver.RequestDetailActivity | NavigationKeys.REQUEST_ID | C |

Complete cũng dùng request_id để tìm trip qua request, không cần hai ID tùy ý.
Màn nhận kiểm tra ID thiếu/rỗng, đọc lại DB theo quyền; báo lỗi rồi cho Back.
MainActivity không thay đổi trong bước này. Leader đăng ký Activity/router ở bước 3.
Không truyền model Serializable/Parcelable hoặc chỉ truyền tên/giá thay dữ liệu thật.

## 2. LocationActivity A → form A/C

Chủ file: activity/search/LocationActivity.java. Caller dùng Activity Result API
với ActivityResultContracts.StartActivityForResult. Đăng ký callback mỗi lần tạo
Activity theo cùng thứ tự, trước STARTED. Không dùng onActivityResult cũ.

Đầu vào: Intent mở LocationActivity; caller lưu ô đang chọn trong state
(origin/destination/pickup) và phục hồi khi xoay; không cần thêm extra để đoán caller.
Người dùng chọn mục: LocationActivity setResult(RESULT_OK, intent) rồi finish.

| Extra đầu ra | Hằng số | Kiểu |
|---|---|---|
| place_name | LocationKeys.PLACE_NAME | String, có nội dung |
| place_latitude | LocationKeys.PLACE_LATITUDE | double, -90 đến 90 |
| place_longitude | LocationKeys.PLACE_LONGITUDE | double, -180 đến 180 |

Caller phải kiểm tra RESULT_OK, Intent khác null và hasExtra cho cả ba field
trước đọc; kiểm tra finite/range, không mặc định (0,0) khi kết quả thiếu.
Tạo Place và cập nhật đúng ô đã lưu; không đổi form nếu Cancel/Back/kết quả sai.
Màn gọi và Location giữ form/query khi xoay, không auto gọi API hoặc gửi nghiệp vụ lại.
Post/Edit của C dùng cùng Location; A không tự ghi trip/request trong màn chọn.

## 3. FavoriteStore — B triển khai, A gọi

B tạo repository/passenger/FavoriteRepository.java implements FavoriteStore.
Constructor công khai nhận FirebaseFirestore để A khởi tạo từ instance dùng chung:
FavoriteRepository(FirebaseFirestore firestore). Không hardcode user demo.

```java
void isFavorite(String userId, String tripId, RepositoryCallback<Boolean> callback);
void addFavorite(String userId, String tripId, RepositoryCallback<Void> callback);
void removeFavorite(String userId, String tripId, RepositoryCallback<Void> callback);
```

- isFavorite trả true/false khác null sau khi đọc; lỗi không tự coi là false.
- add/remove onSuccess(null) sau server xác nhận, onError(exception) khi thất bại.
- Callback chạy trên UI thread, dùng anonymous class Java, không lambda/Handler.
- Mỗi lời gọi chỉ một kết quả cuối. Nếu màn đã dừng thì caller không điều hướng mới.
- B kiểm tra userId khớp Auth hiện tại, ID không rỗng; document là userId_tripId.
- Thêm idempotent không tạo bản trùng; không reset createdAt của favorite đã có.
- Caller chặn ghi khi offline, khóa nút lúc chờ. B xử lý lỗi quyền/mạng thực tế,
  không dùng bản ghi chỉ đang chờ gửi để thông báo server đã thành công.
- A chỉ gọi; không tự tạo FavoriteRepository thứ hai hoặc tự ghi collection.

## 4. TripCacheStore — B triển khai, A và leader gọi

B tạo local/TripCacheRepository.java implements TripCacheStore, constructor
TripCacheRepository(Context context), dùng applicationContext; helper SQLite do B sở hữu.

```java
void saveViewedTrip(String userId, Trip trip, PublicProfile profile);
CachedTrip getViewedTrip(String userId, String tripId);
void clearUserCache(String userId);
```

Đây là hàm đồng bộ: caller chạy bằng ExecutorService ngoài UI thread. Nếu cần
cập nhật UI, dùng Activity.runOnUiThread với Runnable tường minh. Không Handler.
SQLite lỗi có thể phát RuntimeException; caller bắt lỗi và hiển thị phù hợp,
không báo cache đã lưu khi lỗi. get trả null chỉ khi không có dữ liệu.

- Cache theo userId + tripId; trip.getId() phải có, userId đúng tài khoản hiện tại.
- savedAt là mili giây Unix tại lúc lưu (giờ thiết bị), không phải server timestamp.
- CachedTrip gồm Trip, PublicProfile (có thể null), savedAt; không collection mới.
- B phục hồi trip.id từ khóa, profile.id từ trip.driverId nếu có profile.
- Profile thiếu: vẫn lưu trip, UI có placeholder; không dùng tên demo thay dữ liệu.
- A chỉ lưu sau đọc online thành công, đọc cache khi offline, hiện savedAt/nhãn.
- Tên xe/tên công khai có thể lưu; không users/email/biển số riêng/token/mật khẩu.
- Xóa cache đúng tài khoản khi logout; leader phối hợp ngừng tác vụ cache đang
  chạy, đợi/xếp clear sau save trước khi cho phiên khác đọc. Không xóa cache rồi
  để callback phiên cũ ghi lại. UI callback kiểm tra phiên/lifecycle hiện tại.
- SQLite không queue thao tác Firestore; offline chặn mọi mutation.

## 5. A → B: request mới; C ↔ B: state

A commit PENDING và REQUEST_SENT thành công → PendingActivity bằng request_id.
B đọc request/trip; C cập nhật ACCEPTED/REJECTED/COMPLETED/CANCELLED trên Firestore.
B không gọi trực tiếp UI C, C không gọi trực tiếp UI B. Listener sống theo màn.
B hủy ACCEPTED nguyên tử với trip OPEN/1/pointer rỗng; C đọc lại trước nhận người khác.
Hủy trip giữ requests lịch sử; B ưu tiên trip CANCELLED. Rules ghi cần kiểm tra
state/cross-document trong cùng transaction, không chỉ kiểm tra UID.

## 6. Kiểm tra bàn giao (chưa thực hiện)

- A/C dùng cùng LocationKeys và xử lý Cancel không đổi ô.
- A/B không có Favorite/SQLite API trùng tên khác chữ ký.
- Một request_id mở đúng màn B; Complete truy ra trip từ request.
- Mọi XML/function thuộc owner; thay Manifest/router qua leader.
- Khai báo hợp đồng không có dữ liệu giả, không phải chức năng đã chạy.
- Còn thiếu: Activity thật, implementation B, auth, Rules ghi/index, router nghiệp vụ. Base đã push main 02af6bd; nhóm trưởng báo test bộ chọn đạt.

Nguồn Activity Result API: https://developer.android.com/training/basics/intents/result
