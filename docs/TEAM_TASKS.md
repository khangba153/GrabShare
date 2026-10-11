# Phân công và bàn giao GrabShare

Hạn 25/10/2026; 4 người, 2 giờ/ngày/người. Thành, Thuận, Thuần tự chọn A/B/C.
Đây là kế hoạch triển khai, không phải danh sách chức năng đã hoàn thành.

| Gói | Người nhận | Trọng tâm |
|---|---|---|
| Nhóm trưởng | Nhóm trưởng | Base, tài khoản, tích hợp, tiến độ |
| A | Chờ chọn | Tìm chuyến, chi tiết, gửi yêu cầu, API địa điểm |
| B | Chờ chọn | Hành trình người đi nhờ, yêu thích, đánh giá, SQLite |
| C | Chờ chọn | Đăng/quản lý chuyến, duyệt yêu cầu, thông báo |

## 1. Trạng thái xuất phát

Có 27 màn XML và các row/view/sheet chung. GrabShareUi là trình xem bằng vuốt,
chặn thao tác chạm; nhiều XML có clickable=false và blockDescendants. Các màn
chứa dữ liệu minh họa, chưa đọc Firestore. Có Firebase config/model/seed/Rules đọc;
chưa có repository, Activity nghiệp vụ, SQLite, API địa điểm, quyền ghi hoặc index.
Các Java liệt kê dưới đây là FILE CẦN TẠO sau khi giao nhiệm vụ.

Bước 3 đã chuẩn bị bộ chọn Debug local; RUN_SCREEN.md hướng dẫn mở riêng XML
hoặc Activity đúng package khi class/Manifest đã có. Build Debug đã thành công;
leader báo test bộ chọn đạt; base đã push main tại 02af6bd.

Thành viên chuẩn bị XML/đọc tài liệu ngay; phần ghi bắt đầu sau khi nhóm trưởng
Publish Rules cho thao tác đó. Không tự mở allow write: true trên database chung.

## 2. Quy ước chung

- Java root: app/src/main/java/com/example/grabshare.
- XML root: app/src/main/res/layout.
- Activity gắn XML; repository gọi Firestore/transaction; adapter cho danh sách.
- Java không lambda, không Handler; comment tiếng Việt trên hàm.
- Không thêm tầng kiến trúc hoặc file chỉ để tăng số lượng code.
- Chỉ truyền Intent extras trip_id/request_id; không truyền model Serializable.
- Loading/rỗng/lỗi, xoay máy giữ ID/form, không tự gửi lại khi xoay.
- Thành viên bỏ chặn nhập/chạm ở XML mình; Activity thật không kế thừa cơ chế
  dispatchTouchEvent chặn chạm của preview.
- Giữ palette hiện tại; chưa thiết kế lại toàn app.
- File chung Gradle/Manifest/model/theme/strings/MainActivity/GrabShareUi/UiScreen,
  activity_main.xml, row_*.xml, view_*.xml, dialog_confirm.xml, sheet_*.xml do
  leader quản lý. Đề xuất thay đổi trong PR hoặc tạo layout riêng prefix a_/b_/c_.
- Strings riêng có thể dùng values/strings_a.xml, strings_b.xml, strings_c.xml,
  tên resource prefix tương ứng để giảm xung đột.

## 3. Nhóm trưởng — base và tài khoản

### File

- Base: build.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml,
  app/google-services.json, app/src/main/AndroidManifest.xml, firestore.rules,
  scripts/*, model/*.java, docs/*; firestore.indexes.json cần tạo khi chốt query.
- Điều hướng: MainActivity.java, GrabShareUi.java, UiScreen.java.
- XML: screen_login.xml, screen_register.xml, screen_reset_password.xml,
  screen_account.xml, screen_edit_profile.xml, screen_help.xml, screen_terms.xml.
- Java cần tạo: activity/account/{LoginActivity,RegisterActivity,
  ResetPasswordActivity,AccountActivity,EditProfileActivity,HelpActivity,
  TermsActivity}.java; repository/account/{AuthRepository,ProfileRepository}.java.

### Việc làm và tiêu chí

1. Bàn giao model/JSON/query/ID; chuẩn bị Rules ghi theo thao tác và index.
   Kiểm tra chủ sở hữu/state liên quan, không chỉ mở quyền theo đăng nhập.
2. Email/password đăng ký/đăng nhập/đăng xuất/gửi email đặt lại mật khẩu;
   reset_password là gửi email, không tự đổi mật khẩu chưa xác thực.
3. Google sign-in: cấu hình provider/SHA từng máy theo hướng dẫn chính thức
   trước khi viết. Nút Google có mặt chưa được tính là hoàn thành.
4. Đọc/sửa hồ sơ; tạo users và publicProfiles cùng batch. Nếu Auth tạo thành
   công nhưng lưu hồ sơ lỗi phải có luồng tạo lại hồ sơ, không che lỗi.
5. Router thật: Login → Home; tab Home/MyTrips/Account; Account mở DriverTrips,
   Notifications/Help/Terms. Đăng xuất gọi clearUserCache của B và về Login.
6. Đăng ký Activity/permission; chuyển launcher app thật lúc tích hợp, giữ
   preview riêng; thay lambda cũ ở phần chung trước bàn giao app cuối.
7. Review/merge PR và tích hợp hàng ngày; không viết thay cả gói rồi đổi tác giả.

Đạt: nhóm pull base/Sync được, đăng nhập demo để đọc thật, hồ sơ riêng không lộ,
đăng xuất đúng, xoay màn không mất form. Báo cáo leader gồm tổng quan/kiến trúc,
database/Git và phần tài khoản; lý thuyết Android/cộng đồng gói trong 1–2 trang.

## 4. Gói A — tìm chuyến và gửi yêu cầu

### File

- XML: screen_home.xml, screen_location.xml, screen_results.xml,
  screen_detail.xml, screen_review.xml.
- activity/search/{HomeActivity,LocationActivity,ResultsActivity,
  TripDetailActivity,RequestReviewActivity}.java.
- repository/search/{TripSearchRepository,RequestCreationRepository}.java.
- adapter/search/TripAdapter.java; api/PlaceSearchClient.java.

### Việc làm

1. Home chọn điểm đi/đến/ngày/giờ; Location trả Place về đúng ô.
2. Đề xuất OSM/Nominatim và bán kính lọc, chốt với leader trước triển khai;
   xác minh chính sách/rate limit/attribution, tránh spam API. Không live tracking
   hoặc tìm đường chi tiết; API lỗi phải có thông báo.
3. Query OPEN còn giờ theo contract, tối đa 50 ứng viên, lọc hai đầu bằng Java;
   loại chuyến do mình đăng, hiện rỗng/lỗi đúng. Chưa khớp toàn bộ tuyến đường.
4. Detail đọc trip + publicProfiles + ratings thật, tiền miễn phí hoặc đóng góp,
   số chỗ/state. Chưa có rating hiển thị “Chưa có đánh giá”.
5. Review điểm đón/lời nhắn; transaction đọc trip/request ID, tạo PENDING và
   REQUEST_SENT cùng thao tác, không trừ chỗ; chặn tự đặt/trùng/quá giờ/hết chỗ.
6. Detail dùng Favorite/SQLite của B, không viết bản sao. Sau gửi mở PendingActivity
   B bằng request_id. C dùng LocationActivity của A để chọn địa điểm.

### Kết quả cần đạt

D1 đăng → P1 tìm/xem đúng dữ liệu → gửi được một request + một notification;
trip vẫn OPEN/1 chỗ. Chặn trường hợp sai điều kiện. Xoay Home/Review giữ form.
Có demo API thật. Báo cáo A: flow, UI, query/API/transaction, nguồn API,
link commit/PR, kết quả tự kiểm tra và hạn chế.

## 5. Gói B — người đi nhờ và offline

### File

- XML: screen_pending.xml, screen_accepted.xml, screen_rejected.xml,
  screen_upcoming.xml, screen_complete.xml, screen_my_trips.xml,
  screen_favorites.xml.
- activity/passenger/{PendingActivity,AcceptedActivity,RejectedActivity,
  UpcomingActivity,CompleteActivity,MyTripsActivity,FavoritesActivity}.java.
- repository/passenger/{PassengerTripsRepository,RequestCancellationRepository,
  FavoriteRepository,RatingRepository}.java.
- adapter/passenger/{RideAdapter,FavoriteAdapter}.java.
- local/{TripCacheDbHelper,TripCacheRepository}.java.

### Việc làm

1. Query requests theo passengerId và ghép trip. Trip CANCELLED ưu tiên hơn
   request state; dùng bố cục sẵn hiển thị hủy, chưa cần thêm XML.
2. Pending theo dõi ACCEPTED/REJECTED trong app, điều hướng đúng; bỏ listener
   khi màn dừng. OPEN quá giờ không tự chuyển COMPLETED.
3. Hủy PENDING trước giờ đi; hủy ACCEPTED transaction giải phóng trip OPEN/1,
   xóa acceptedRequestId và tạo REQUEST_CANCELLED. Kiểm tra lại quyền/state.
4. Favorite ID cố định, thêm/bỏ không trùng; cung cấp repository cho A Detail.
5. Rating 1–5 một lần sau COMPLETED, chỉ người được nhận đánh giá chủ xe;
   người đi nhờ không tự đánh dấu trip hoàn thành.
6. SQLite cache theo userId/tripId/savedAt, snapshot Trip + dữ liệu công khai.
   B cung cấp saveViewedTrip/getViewedTrip/clearUserCache; chốt tham số với A
   trước viết. Không cache users/password/token, không xếp hàng ghi offline.
7. Offline chỉ xem trang đã lưu, hiện nhãn và thời gian lưu, chặn thao tác ghi;
   tách tài khoản và xóa cache khi đăng xuất.

### Kết quả cần đạt

P1 chỉ thấy requests mình; nhận/từ chối/hủy hiện đúng. Hủy đã nhận trả đúng một
chỗ; favorite/rating không trùng. Online xem Detail → ngắt mạng → xem lại cache
có nhãn. Xoay giữ ID/form. Báo cáo B: state/SQLite, minh chứng offline/quyền,
commit/PR và kết quả tự kiểm tra.

## 6. Gói C — chủ xe và thông báo

### File

- XML: screen_driver_trips.xml, screen_post.xml, screen_edit_trip.xml,
  screen_requests.xml, screen_request_detail.xml, screen_manage.xml,
  screen_notifications.xml.
- activity/driver/{DriverTripsActivity,PostTripActivity,EditTripActivity,
  DriverRequestsActivity,RequestDetailActivity,ManageTripActivity,
  NotificationsActivity}.java.
- repository/driver/{DriverTripRepository,DriverRequestRepository,
  NotificationRepository}.java.
- adapter/driver/{DriverTripAdapter,RequestAdapter,NotificationAdapter}.java.

### Việc làm

1. Query driverId; đăng OPEN/1 chỗ có tuyến/giờ/đóng góp, dùng Location A;
   sửa chính chủ OPEN trước giờ đi, không sửa tuyến/giờ/tiền lúc FULL.
2. Query requests driverId và tùy chọn tripId; đọc publicProfiles người gửi.
3. Nhận PENDING transaction đọc trip/request, ghi ACCEPTED + FULL/0 + pointer
   và REQUEST_ACCEPTED. Không nhận hai người vào một chỗ.
4. Từ chối PENDING + REQUEST_REJECTED, không đổi chỗ.
5. Hủy trip trước giờ đi, giữ requests lịch sử; có người được nhận thì tạo
   TRIP_CANCELLED. Hoàn thành FULL sau giờ đi + TRIP_COMPLETED.
6. Inbox query recipientId, nội dung từ type; chính chủ đánh dấu đã đọc;
   bấm mở đúng request_id/trip_id. NotificationRepository chỉ đọc/đánh dấu;
   sự kiện được tạo trong transaction của A/B/C nơi thực hiện thao tác.

### Kết quả cần đạt

Chủ xe đăng/sửa/hủy/hoàn thành đúng điều kiện; người khác bị chặn sửa. Nhận hai
yêu cầu cạnh tranh chỉ một ACCEPTED, FULL/0 đúng pointer. B thấy trạng thái
và đánh giá được. Thông báo đúng người, không trùng; xoay Post/Edit giữ form.
Báo cáo C: flow chủ xe/transaction/inbox, UI/quyền, commit/PR và tự kiểm tra.

## 7. Ranh giới và phụ thuộc

Bước 2 đã chốt chữ ký trong HANDOFF_CONTRACT.md và package contract. B triển khai
FavoriteRepository implements FavoriteStore và TripCacheRepository implements
TripCacheStore; A/C dùng LocationKeys và Activity Result. Không tự đổi chữ ký.

| Giao nhau | Bàn giao |
|---|---|
| A Detail ↔ B Favorite/SQLite | B cung cấp repository, A gọi |
| A gửi ↔ B trạng thái/hủy | request_id thống nhất, mỗi gói một repository riêng |
| C nhận ↔ B hủy | Cùng state contract; cập nhật trip/request nguyên tử |
| A Location ↔ C Post/Edit | Chốt cơ chế trả Place trước viết |
| A/B/C notification | Gói gây sự kiện ghi cùng transaction, C quản lý inbox |
| Rules/index/Manifest/Gradle | Leader quản lý, thành viên ghi nhu cầu trong PR |

Các gói chỉ sửa XML mình. Base row/view/sheet nếu cần thay cấu trúc phải chốt
một người sửa hoặc tạo layout riêng. screen_chat.xml/row_message.xml là tham khảo,
chưa triển khai chat; sheet_avatar.xml tham khảo, dùng avatar tĩnh. Bản demo
cuối không để nút dẫn đến chức năng chưa có; không tính chat/VNeID/payment/upload.

## 8. Từ pull đến push/merge

1. Leader duyệt và push base main, gửi commit mốc/Rules đã mở, mật khẩu demo riêng.
2. Mỗi người git user.name/email của mình; email liên kết GitHub hoặc noreply
   đúng tài khoản, không dùng author leader.
3. Pull main, tạo codex/feature-search (A), codex/feature-passenger (B),
   codex/feature-driver (C); có thể giữ nhánh đã có theo thống nhất nhóm.
4. Sync, đọc DATA_CONTRACT/FIREBASE_BASE; dùng cùng JSON/project; không seed lại.
   Đăng nhập/đọc Android sau khi leader bàn giao auth, không giả đã nối thành công.
5. Tạo từng Activity/repository, nối XML và dữ liệu; gửi nhu cầu Manifest cho leader.
6. Commit phần thật đã làm, push định kỳ; không chia giả hoặc đổi author để tăng số.
7. Cập nhật main vào nhánh trước PR, giải quyết xung đột phạm vi mình; không
   force-push main. Nếu đụng file chung trao đổi leader.
8. PR main có chức năng/file/điều kiện phụ thuộc/ảnh hoặc clip/kết quả tự kiểm tra/
   hạn chế. Leader review rồi Create merge commit để giữ commit tác giả.
9. Sau merge pull main, kiểm tra liên gói; lưu link PR/commit cho báo cáo.
10. Tự ghi mức hoàn thành trung thực, phần chưa làm và minh chứng riêng.

## 9. Lịch đề xuất

| Ngày | Mốc |
|---|---|
| 11–12/10 | Chọn A/B/C, bàn giao base/query; leader auth/Rules/index/flow |
| 13–15/10 | Đọc dữ liệu thật, nối màn và responsive |
| 16–18/10 | Ghi/transaction/API/SQLite, PR nhỏ lên main |
| 19–20/10 | End-to-end hai tài khoản, sửa quyền/state, chốt chức năng |
| 21–22/10 | Rà xoay/offline, sửa lỗi; ghép báo cáo/ảnh minh chứng |
| 23/10 | PDF/check UEH <20%, slide và demo |
| 24/10 | Đóng băng tính năng, tập trình bày/hỏi đáp |
| 25/10 | Kiểm tra GitHub/APK/file nộp, nộp theo giờ giảng viên |

Quỹ danh nghĩa 11–25/10: 30 giờ/người gồm báo cáo/tích hợp. Mỗi ngày gợi ý
80 phút phần chính, 20 phút tích hợp/commit, 20 phút báo cáo/minh chứng.
Nếu trễ giảm chi tiết bản đồ/UI; ưu tiên core/Firestore/responsive, giữ API/SQLite
mức cơ bản, không mở rộng chat hoặc ngoài phạm vi.

## 10. Đạt cuối kỳ và báo cáo

- Luồng thật: login → chủ xe đăng → người khác tìm/gửi → nhận → trạng thái
  → hoàn thành → đánh giá; có từ chối/hủy và không nhận quá một chỗ.
- Firestore thật, responsive xoay, API hỗ trợ, SQLite trang đã xem.
- Thành viên tự kiểm tra màn mình và lưu ảnh/clip; leader kiểm tra liên gói.
- 40% Git: author/nhánh/commit/PR/merge thật; không coi số dòng code là toàn bộ đóng góp.
- 40% PDF: lý thuyết 1–2 trang, trọng tâm thiết kế/chức năng/kỹ thuật, ít code;
  tối đa 60 ±5 trang, phân công/mức hoàn thành, nguồn mã GitHub, UEH mặc định <20%.
- 20% slide/demo/hỏi đáp: trình bày 5 phút theo yêu cầu; chuẩn bị hỏi đáp trong
  tổng thời lượng giảng viên quy định. Không ghi đạt check/demo khi chưa thực hiện.
- Mỗi người tạo docs/report/<leader|a|b|c>.md khi làm: mô tả/UI/kỹ thuật,
  ảnh/link commit/PR, tự kiểm tra và phần chưa làm. Leader ghép theo mẫu báo cáo.

## 11. Chốt tiếp

A/B/C tự chọn, không yêu cầu điền tên. Chữ ký Location/Favorite/SQLite đã chốt;
còn bán kính tìm kiếm, cách chạy riêng màn bước 3, auth/Rules ghi và commit base.
Hợp đồng đã compile và push main 02af6bd. Base UI độc lập đã bàn giao; Auth/Rules ghi/index vẫn thuộc mốc 2.
