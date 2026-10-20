# Ngữ cảnh task 3 của Thành — kế hoạch mới

Cập nhật ngày 20/10/2026 theo xác nhận trực tiếp của Thành trong chat:
Thành đã thống nhất với lead kế hoạch ghép dọc tuyến mới, thay thế kế hoạch
tìm chuyến cũ. Đây là ngữ cảnh làm việc của Thành, chưa tự thay đổi schema hoặc
tài liệu chung của cả nhóm.

## Quyết định cần giữ qua các lượt làm việc

- Quy tắc Thành nhắc lại: viết code tường minh, không viết tắt tên biến/hằng và
  không dùng cú pháp rút gọn. Không lambda, Handler, tham chiếu hàm, toán tử ba
  ngôi, if/vòng lặp thiếu ngoặc khối, gộp nhiều khai báo hoặc nhiều lệnh một dòng,
  ++/-- hay phép gán +=/-=. Khai báo đầy đủ kiểu generic; ưu tiên tên mô tả mục
  đích và đơn vị (Milliseconds, Meters). Giữ tên cố định của Android/Firebase,
  resource và hợp đồng dữ liệu để tương thích. Áp dụng cả mã kiểm thử của gói mình.
- Không yêu cầu Thành xác nhận lại việc lead đã đồng ý kế hoạch mới.
- Task 3 mới là ghép dọc tuyến A -> B -> C -> D: A/D là điểm đi/đến của tài xế;
  B/C là điểm đón/trả của người đi nhờ.
- Bộ lọc cũ yêu cầu A gần B và D gần C trong 5 km không còn là thiết kế đích.
- Mục 15 DATA_CONTRACT.md và kế hoạch DETOUR cũ giữ lọc 5 km rồi chỉ tính đường
  vòng qua pickup sau khi chọn chuyến không còn mô tả phạm vi đích của task 3.
  Không dùng phạm vi cũ để bác bỏ hoặc thu hẹp kế hoạch Thành đã xác nhận với lead.
- Việc phê duyệt kế hoạch và việc bàn giao code/model/Rules là hai trạng thái khác
  nhau. Đã fetch GitHub ngày 20/10: HEAD thanhnc trùng origin/main c59ec02; PR #38
  đã đưa Post/Edit của Thuần và Rules cho hai field mới vào main.
- Thành đã yêu cầu triển khai task 3, tự xử lý lỗi và ghi lại lựa chọn kỹ thuật;
  không còn giới hạn chỉ cập nhật ngữ cảnh. Không tự commit/push trong lượt này.
- Thành chọn tự kiểm tra Firebase thật sau; không tiếp tục chờ hoặc hỏi lại đăng
  nhập để chốt phần code. Đã nhận PR #41 của lead bằng fast-forward tới ecf0501,
  thay đổi index favorites không trùng gói task 3 đang triển khai.
- Sau khi chốt code, Thành yêu cầu commit task 3, không để tác giả ẩn danh.
  Dùng danh tính Git Congthanh1912 đã cấu hình và đã dùng ở commit trước; chưa push.
- Thành cập nhật yêu cầu giờ: khách chọn T chỉ nhận tài xế khởi hành trong
  [T, T+1 giờ], gồm hai mốc; không nhận tài xế trước giờ khách chọn. Thay thế
  thiết kế truy vấn cả ngày trước đây. Đã sửa query, lọc RAM, UI và kiểm tra mốc biên.

## Pipeline theo kế hoạch đã xác nhận

0. Khi tài xế tạo chuyến, tính đường bộ gốc A -> D để tái sử dụng và lưu mức đi thêm
   tối đa tài xế chấp nhận. Khi đổi tuyến/cấu hình phải cập nhật baseline phù hợp.
1. Query chuyến OPEN còn chỗ, loại chính chủ: giờ tài xế thuộc [giờ khách chọn,
   giờ khách chọn +1 giờ], gồm hai biên và phải chưa khởi hành. Chuẩn hóa giờ
   khách tới phút; cửa sổ có thể đi qua nửa đêm, không lấy cả ngày như kế hoạch cũ.
2. Trên RAM tính khoảng cách tọa độ AB, BC, CD, AD và độ lệch ước lượng
   AB + BC + CD - AD; lọc sơ bộ theo ngưỡng và xếp ứng viên theo độ lệch rồi AB.
   Đây là heuristic, không tự tuyên bố bảo đảm thuận chiều hoặc không bỏ sót.
3. Cắt nhóm tối đa 10 ứng viên, giữ hàng đợi và con trỏ. Xem thêm xử lý nhóm tiếp
   theo; không query lại DB cho từng nhóm trong cùng lượt tìm. Tự lấy bù khi thiếu
   kết quả, với ngân sách request/thời gian cần chốt trước triển khai.
4. Chạy nền Routing A -> B -> C -> D; lấy tổng đường mới và đường bộ A -> B.
   actualDetour = max(0, newDistance - baseDistance).
   Lọc theo maxDetour + tolerance đã thống nhất. Baseline và tuyến ghép dùng cùng
   cấu hình xe máy/đơn vị/ràng buộc. Giữ thứ tự B trước C, không tối ưu đảo waypoint.
5. Lưu các metric vào đối tượng kết quả riêng trên RAM. Sắp xếp ngay theo đề xuất
   (độ lệch -> AB -> giá), giá thấp nhất, AB gần nhất hoặc ít lệch nhất; không gọi
   lại API khi chỉ đổi cách sắp xếp. Kết quả là trong tập ứng viên đã kiểm tra.

## Hiển thị khoảng cách

- Người đăng: quãng đường gốc A -> D và mức đi thêm tối đa đã chọn.
- Người tìm trên Home: đường bộ B -> C lấy từ lần Routing đang dùng để vẽ tuyến.
- Results: giá dự kiến, khoảng cách A -> B và quãng đường tài xế phải đi thêm.
- Phân biệt quãng đường đường bộ và khoảng cách tọa độ dự phòng; dự phòng phải
  ghi rõ ước lượng. Lưu số bằng mét, chỉ đổi sang km khi trình bày.
- Code local lúc đối chiếu tại commit 1524943 chưa lấy/hiện số km ở Home,
  Post/Edit, Results hoặc Detail. Home hiện chỉ dùng geometry để vẽ tuyến.

## Phụ thuộc và trạng thái bàn giao

Cập nhật sau khi triển khai: model Trip, Post/Edit và DriverTripRepository đã có
baseDistanceMeters/maxDetourMeters; Rules trong main đã cho phép. Không viết thay
gói Thuần hoặc đổi schema. Chưa xác minh deploy/index bằng phiên Firebase thật vì
emulator kiểm tra chưa đăng nhập. Chi tiết triển khai và kiểm chứng ở
`THANH_TASK_3_HANDOFF.md`; các ghi chú chờ bên dưới mô tả đầu vào trước bàn giao.

- Leader: chốt model/Rules/schema mới, index nếu đổi query, cách rollout chuyến
  cũ thiếu cấu hình. Rules hiện tại dùng bộ field chính xác nên không tự ghi field mới.
- Thuần: Post/Edit chọn mức đi vòng, tính/lưu baseline, cập nhật khi sửa tuyến,
  hiển thị km cho người đăng; thống nhất chữ ký Routing dùng chung với Thành.
- Thành: Routing client, bộ tính ghép, query/hàng đợi/nhóm 10, ViewModel/lifecycle,
  Results/adapter/bộ lọc và hiển thị km cho người tìm.
- Thuận: không chặn thuật toán online task 3; chỉ phối hợp khi nối cache hoặc
  hiển thị metric mới trên các màn passenger.
- DATA_CONTRACT.md đã nêu tên dự kiến baseDistanceMeters/maxDetourMeters, Java Long
  nullable; các mức 0/2000/5000/10000, mặc định 2000 cho chuyến mới. Đây là thông tin
  của tài liệu đang có; phải đối chiếu bản bàn giao theo kế hoạch mới trước khi ghi DB.
- Thiếu dữ liệu không mặc định thành 0 hoặc tự suy ra giới hạn đã được tài xế chọn.
- Các thông số cần chốt kỹ thuật: khung thời gian, ngưỡng heuristic, tolerance,
  ngân sách lấy bù/concurrency, giới hạn ứng viên và xử lý chuyến cũ. Lượt triển khai
  đã chọn mặc định và ghi rõ trong THANH_TASK_3_HANDOFF.md, không chặn chờ hỏi lại.
- Chưa có DETOUR_CONTRACT.md trong Downloads tại lần kiểm tra trước. Việc thiếu
  file này không phủ nhận xác nhận kế hoạch mới của Thành.

## Phạm vi và trình tự triển khai dự kiến

Tiến độ 20/10: Home đã được bổ sung nhãn km từ Routing và dùng
VietnamRoutingClient/VietnamRouteValidator để chỉ nhận tuyến có dữ liệu quốc gia
đầy đủ và toàn bộ là VN. Đã kiểm tra 9 ca API thật, build Debug đạt; chi tiết trong
`THANH_VIETNAM_ROUTING_REPORT.md`. Tiếp đó đã thay Results bằng pipeline dọc tuyến,
thêm ResultsViewModel/EnRouteSearch/SearchRequestBudget và giao diện metric/lọc;
không sửa schema Trip. Tham khảo THANH_TASK_3_HANDOFF.md cho trạng thái hiện tại.

1. Chốt chữ ký dữ liệu/Routing với leader và Thuần.
2. Routing client và hiện km trên Home; chuẩn bị API để Thuần nối Post/Edit.
3. Nhận bàn giao baseline/mức đi vòng/model/Rules, phối hợp rollout.
4. Thay thuật toán Results bằng pipeline 5 bước và xử lý nhóm 10 chạy nền.
5. Hiển thị metric, lọc/sắp xếp trên RAM và phân biệt Xem thêm với Làm mới.
6. Kiểm tra chiều/độ lệch/thiếu dữ liệu/API lỗi/429/lifecycle/quota, bàn giao minh chứng.

Phần thương lượng giá, lưu điểm trả vào request và chuyển sang Google Maps là
các mở rộng được nêu trong ý tưởng tổng thể; chưa triển khai trong phạm vi tập
trung task 3 hiện tại. Không tự đổi task 4/5 hoặc schema request theo ngữ cảnh này.
Ngoại lệ đã được Thành yêu cầu ngày 20/10: nối sẵn điểm đón/điểm xuống từ Results
qua Detail sang Review, hiển thị dưới tuyến chủ xe, dùng pickup đã tìm khi gửi,
không yêu cầu chọn lại. Đã thực hiện bằng Bundle riêng của gói search, ràng buộc
đúng chuyến/tài khoản và giữ qua xoay màn. Caller chỉ có trip_id vẫn chọn pickup.
Điểm xuống hiện chỉ dùng xác nhận trên giao diện; lưu xuống request và hiển thị
cho tài xế còn phụ thuộc thống nhất schema/Rules với lead. Không tự đổi schema.
Giữ quy tắc Java không lambda/Handler, comment tiếng Việt trên hàm, không sửa
gói khác hoặc file chung ngoài phần đã thống nhất; không tự commit/push.
