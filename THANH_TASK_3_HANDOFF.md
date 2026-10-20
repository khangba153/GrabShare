# Bàn giao task 3 — ghép dọc tuyến

Ngày 20/10/2026, workspace `D:/GrabShare/gs`, nhánh `thanhnc`.
Đã fetch GitHub; trước sửa, HEAD trùng `origin/main` tại `c59ec02`.
Khi Thành yêu cầu tiếp tục, đã fetch và fast-forward tới `ecf0501` (PR #41 chỉ
thêm index favorites của lead), giữ nguyên thay đổi task 3 chưa commit.
Theo yêu cầu mới của Thành, phần task 3 được đưa vào commit với danh tính Git
Congthanh1912; chưa push. Trong quá trình triển khai không ghi Firebase hoặc tự sửa Post/Edit, Trip, Rules, index,
request schema hoặc file cấu hình/dependency chung trong lượt này.
`THANH_GOOGLE_MAPS_COMPARISON.md` là file chưa được theo dõi đã tồn tại trước lượt này.

## Đầu vào đã đối chiếu

- PR #38: `feature/driver-trip-route-estimate`, các commit `8c2d9cd`, `a162010`,
  `3c0b99d`, `aab6e91`, `8c7d224` đã có trên main.
- `Trip`: Long nullable `baseDistanceMeters`, `maxDetourMeters`.
- Post/Edit gọi VietnamRoutingClient, tính/lưu baseline và chọn mức đi thêm;
  DriverTripRepository ghi hai field và kiểm tra dữ liệu đầu vào.
- Rules local cho phép schema mới; tạo chuyến mới yêu cầu hai field. Chuyến cũ
  được thiếu cả hai field. Index status ASC / departureAt ASC có trong repo.
- Prompt dọc tuyến và xác nhận kế hoạch của Thành là phạm vi đích. Mô tả lọc hai
  đầu 5 km / chỉ vòng qua pickup trong DATA_CONTRACT cũ không còn áp dụng cho task 3.

## Flow đã nối

Home truyền B/C và thời gian → Results → đọc snapshot chuyến OPEN từ server →
lọc dữ liệu/chính chủ/chỗ/thời gian → heuristic và hàng đợi trên RAM → Routing
theo nhóm 10 → kiểm tra detour thực → ghép public profile → thẻ kết quả → Detail
bằng `trip_id`. Detail tiếp tục đọc lại dữ liệu hiện tại theo luồng đã có.

### Bước 1: đọc chuyến theo thời gian rộng

- Toàn ngày người tìm chọn theo `Asia/Ho_Chi_Minh`; cận dưới là đầu ngày hoặc
  hiện tại, lấy mốc muộn hơn; cận trên là đầu ngày kế tiếp, loại trừ.
- Không ép departureAt của A phải sau giờ khách chọn tại B.
- Bỏ `.limit(50)` cũ. Snapshot đọc toàn bộ chuyến OPEN trong khung, rồi mới xếp
  hạng. Xem thêm không đọc DB lại. Làm mới đọc snapshot mới.
- Không nhận chuyến tài xế đã xuất phát: trạng thái hiện tại của Rules/Review
  vẫn yêu cầu gửi request trước departureAt. Ghép chuyến đã chạy/qua đêm thuộc
  thay đổi state contract khác, không tự mở rộng trong task 3.
- Chỉ đọc Source.SERVER; lỗi đọc không biến thành “không có chuyến”.

### Bước 2: sàng lọc tọa độ trên RAM

- Loại chính chủ, trùng ID, chuyến không OPEN/1, quá giờ, tọa độ hoặc dữ liệu sai.
- Chuyến thiếu baseline dương/giới hạn hợp lệ: thống kê riêng, không coi null là 0.
- `estimatedDetour = max(0, AB + BC + CD - AD)` bằng khoảng cách cung lớn.
- Ngưỡng sơ bộ: `max(50000, 4 * maxDetour) + max(0, storedBaseline - AD)` mét.
  Phần bù baseline–AD tránh loại nhầm hành lang cong chữ S. Loại ngược chiều rõ
  rệt bằng so sánh khoảng cách với cùng ngưỡng rộng; Routing vẫn là bước quyết định.
- Xếp tăng dần theo độ lệch ước lượng, AB, ID. Heuristic không bảo đảm không bỏ sót;
  giao diện nêu rõ chỉ là đề xuất trong tập đã kiểm tra.

### Bước 3: nhóm 10 và ngân sách

- Hàng đợi/con trỏ, kết quả và metric thuộc ResultsViewModel; xoay không query lại.
- Hai worker Routing; mỗi nhóm tối đa 10 ứng viên. Nếu chưa đủ 3 kết quả mới, lấy
  bù nhóm tiếp theo; mỗi hành động tối đa 30 ứng viên, 60 HTTP thực tế.
- Sau 75 giây không cấp phép HTTP mới; request đang chạy vẫn chịu connect/read
  timeout 10/25 giây. 75 giây không phải cam kết tổng độ trễ tuyệt đối.
- Hết ngân sách/quota/quyền: ứng viên chưa đánh giá được trả lại đầu hàng đợi
  theo thứ tự ban đầu. Không đánh dấu chúng thành không phù hợp.
- Lỗi mạng/đầu vào của ứng viên: ghi nhận chưa đánh giá, Làm mới để thử lại.
- Xem thêm giữ snapshot/hàng đợi hiện tại; Làm mới hủy tác vụ cũ và đọc server.

### Bước 4: đường bộ và tính nhất quán baseline

- Giữ thứ tự A → B → C → D; không tối ưu đảo điểm. Chặng trùng liên tiếp được
  gộp waypoint nhưng vẫn trả metric 0 cho chặng bắt buộc tương ứng.
- Cặp tuyến gốc và ghép phải cùng mode motorcycle / balanced / metric / stopover,
  cùng chính sách tránh quốc gia và cùng bộ mốc hành lang khi fallback.
- Metadata toàn tuyến, từng leg và admin_areas phải đầy đủ và chỉ VN.
- `actualDetour = max(0, joinedDistance - comparableBaseline)`.
- Nhận nếu `actualDetour <= maxDetourMeters + 300 m`.
- Lấy đường bộ A→B từ tổng đúng các leg tương ứng, không nhầm mốc dẫn hướng là B.
- HTTP 400/404 không còn bị khẳng định chắc chắn là điểm xa đường; thông báo yêu
  cầu chọn địa chỉ cụ thể và giữ mã lỗi. Không tự dời điểm đón/trả do người dùng chọn.

Vướng mắc đã xử lý: schema mới chỉ lưu số mét, chưa có version/cấu hình/geometry
của baseline. Chèn mốc độc lập cho từng đoạn có thể làm tuyến ghép khác hành lang
tuyến gốc, sinh detour âm giả. Vì vậy task 3 xác minh lại A/D và ghép bằng cùng
hành lang. Nếu baseline mới khác số đã lưu trên 50 m, dùng baseline mới trên RAM;
không sửa document Trip. Nếu lệch nhỏ, tiếp tục dùng số đã lưu.

Baseline đã xác minh (và phương án baseline bị kiểm tra từ chối) được cache theo
A/D, policy và chiến lược trong cùng lượt tìm. Chỉ giữ số mét, không giữ geometry
nặng. Refresh đổi cache; tác vụ cũ không thể ghi sang cache mới. Hai worker có thể
cùng xác minh lần đầu khi trùng A/D, nhưng mọi HTTP vẫn bị chặn bởi cùng ngân sách.
Giải pháp này tốn thêm API so với tin hoàn toàn số baseline DB, đổi lại tránh phép
trừ hai tuyến không tương thích mà chưa cần đổi schema chung.

### Bước 5: giao diện và lifecycle

- Thẻ hiển thị giá dự kiến, tuyến tài xế, đường bộ A→B và mức đi thêm.
- Giờ trên thẻ là giờ tài xế đi từ A; không quảng cáo đó là giờ đến B.
- Đề xuất: detour → AB → giá → ID; hoặc giá thấp nhất / AB gần nhất / ít lệch nhất.
- Giới hạn giá nguyên VNĐ; 0 chỉ giữ chuyến miễn phí; để trống bỏ lọc.
- Đổi lọc/sort không gọi API. Lọc áp dụng trên tập đã đánh giá, không đổi hàng đợi.
- Không profile thì hiện thiếu hồ sơ; không dùng ảnh/tên/giá minh họa.
- Màn thiếu extra có trạng thái an toàn, không hiện tên địa điểm mẫu trong XML.
- Logout/đổi UID, Refresh và đóng màn thật vô hiệu hóa callback, hủy future/HTTP.
  ViewModel tồn tại qua xoay. Process death giữ điều kiện và lọc, đọc lại snapshot
  vì hàng đợi RAM không còn. Quay lại từ Detail loại các kết quả đã đến giờ.

## File thay đổi

| File | Vai trò |
|---|---|
| activity/search/ResultsActivity.java | Nhận điều kiện, bộ lọc/sort, Xem thêm/Làm mới, mở Detail |
| activity/search/ResultsViewModel.java — mới | Điều phối snapshot/hàng đợi/worker/lifecycle/session |
| repository/search/EnRouteSearch.java — mới | Criteria, heuristic, metric, thời gian VN, comparator |
| repository/search/SearchRequestBudget.java — mới | Ngân sách HTTP nguyên tử, deadline và quota |
| repository/search/TripSearchRepository.java | Query ngày rộng từ server, bỏ query/DTO 5 km cũ, đọc profile an toàn |
| api/VietnamRoutingClient.java | Cặp tuyến cùng hành lang, cache baseline, lỗi có kiểu, leg trùng |
| adapter/search/TripAdapter.java | Trình bày kết quả dọc tuyến và metric |
| res/layout/screen_results.xml | UI kết quả, giá, sort, tiến trình và thao tác |
| res/layout/row_en_route_match.xml — mới | Thẻ riêng, không sửa row dùng chung |
| THANH_TASK_3_CONTEXT.md | Cập nhật ngữ cảnh sau khi nhận bàn giao và triển khai |
| app/src/test và app/src/androidTest | Kiểm chứng riêng task 3, không seed DB thật |

Các đường dẫn Java trong bảng nằm dưới app/src/main/java/com/example/grabshare/.

## Kiểm chứng

- Build Debug và APK kiểm thử: thành công.
- Unit: 10 test qua (thời gian VN, ghép giữa tuyến, thiếu dữ liệu, chính chủ/trùng/
  hết chỗ/quá giờ, chiều ngược, sort/giá, đầu vào, quota đồng thời, deadline).
- Android: 17 test được runner ghi nhận; 16 qua, 1 bỏ qua do không có phiên Firebase.
  Bao gồm 5 test pipeline (lấy bù/Xem thêm/sort, quota, callback cũ, HTTP đang chạy,
  đổi UID), 8 test Routing/planner/cache/cancellation, 2 test layout/Activity,
  1 test HttpURLConnection thật trên Android. Test query Firebase thật bị bỏ qua.
- Hình dọc/ngang được render từ layout và adapter thật với nhãn DỮ LIỆU KIỂM THỬ;
  không phải minh chứng có chuyến được đăng trên Firebase.
- API thật Android: baseline 5.102 m, ghép 6.929 m, AB 1.354 m, detour 1.827 m.
- API thật tuyến dài qua transport máy tính, dùng cùng planner/parser Java:
  HCM → Nha Trang → Thanh Hóa → Hà Nội: baseline 1.899,603 km,
  ghép 1.912,153 km, AB 413,867 km, detour 12,550 km, 3 HTTP.
  Do đó giới hạn 10 km + 300 m sẽ loại đúng trường hợp này.
- HCM → Nha Trang → Ninh Bình → Hà Nội: ghép = baseline 1.899,603 km,
  detour 0 km; cùng lượt tìm chỉ thêm 1 HTTP nhờ cache baseline.
- Các số trên là test đường đi theo tọa độ, không phải kết quả truy vấn chuyến thật.
- Lint toàn repo: không qua, 74 lỗi và 858 cảnh báo. Đã lọc báo cáo XML: không có
  Error/Fatal trong các file task 3 sửa/thêm. Các lỗi nằm ở file không đổi, ví dụ
  DuplicateIds trong screen_account.xml, UseAppTint ở layout khác và exported ở
  debug Manifest. Không sửa gói khác hoặc tạo baseline che lỗi. Báo cáo chi tiết:
  app/build/reports/lint-results-debug.html. Vẫn có cảnh báo của task 3 về text
  tiếng Việt hardcode, notifyDataSetChanged và overdraw; chưa quốc tế hóa UI.
- git diff --check: qua.

Lệnh tái hiện:

```powershell
$env:JAVA_HOME='D:\android\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --offline
.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache
```

Thêm `'-Pandroid.testInstrumentationRunnerArguments.live=true'` để bật kiểm tra
API/đọc Firebase thật. Không tự đăng nhập, tạo tài khoản hay ghi dữ liệu để làm
test xanh. Lượt kiểm tra cuối dùng adb install -r và am instrument để giữ APK
trên emulator sau kiểm tra; stdout tại build/task3-verification/instrumentation-final.txt.
Số đo và ảnh nằm cùng thư mục build/task3-verification/ (ignored, không commit).

## Phần chưa mở rộng và checklist Thành kiểm tra

Thành đã chọn “Tôi sẽ kiểm tra Firebase sau” trong chat ngày 20/10/2026.
Do đó phần xác minh bằng tài khoản/Firebase thật bên dưới được bàn giao để Thành
chạy sau, không tiếp tục chặn việc chốt code hoặc lặp yêu cầu đăng nhập. Các test
thuật toán, lifecycle và Routing đã được thực hiện như phần Kiểm chứng ở trên.

- [ ] Đăng nhập bằng tài khoản thật, tìm chuyến tương lai đã có hai field mới;
      xác nhận query/index trên môi trường đã deploy. Chưa thể khẳng định từ test giả lập.
- [ ] Dùng Post/Edit của Thuần, đổi A/D, kiểm tra baseline cập nhật và chuyến xuất
      hiện/biến mất đúng theo maxDetour đã chọn. Không tạo dữ liệu minh họa vào DB chung trong lượt này.
- [ ] Thử nhóm có ít kết quả rồi Xem thêm; Làm mới phải lấy dữ liệu mới. Đổi sort/giá
      phải giữ các chuyến đã kiểm tra và phản hồi ngay.
- [ ] Xoay màn khi đang xử lý, Back/mở lại, đổi tài khoản; không kết quả trùng hoặc
      callback cũ chen vào. Bấm dòng mở đúng Detail, Detail kiểm tra lại trạng thái hiện tại.
- [ ] Địa điểm cấp tỉnh không có điểm nối đường có thể vẫn bị Routing từ chối;
      chọn địa chỉ/điểm gần đường cụ thể. Không tự dời tọa độ để làm test đạt.
- [ ] Kiểm tra các tuyến dài thực tế trước khi chọn tolerance khác. Số km vẫn phụ
      thuộc dữ liệu/mode và hành lang; chưa cam kết tuyến VN ngắn nhất hoặc bằng Google Maps.

Task 3 không mở rộng schema request: điểm trả C, giá thương lượng và deep link
Google Maps sau chấp nhận chưa được lưu/nối trong gói này. Results mở Detail
bằng trip_id như contract; Review vẫn là flow chọn điểm đón riêng hiện có.
Muốn giữ tự động B/C xuyên suốt yêu cầu và tài xế nhận điểm trả cần bàn giao thêm
ở gói request/lead. Task 4/5 không được tự đổi trong lượt triển khai task 3.

Tài liệu API đã đối chiếu:
[Geoapify Routing](https://apidocs.geoapify.com/docs/routing/),
[Firestore get data](https://firebase.google.com/docs/firestore/query-data/get-data),
[Android ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel).
