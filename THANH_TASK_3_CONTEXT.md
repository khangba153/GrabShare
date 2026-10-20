# Ngữ cảnh task 3 của Thành — kế hoạch mới

Cập nhật ngày 20/10/2026 theo xác nhận trực tiếp của Thành trong chat:
Thành đã thống nhất với lead kế hoạch ghép dọc tuyến mới, thay thế kế hoạch
tìm chuyến cũ. Đây là ngữ cảnh làm việc của Thành, chưa tự thay đổi schema hoặc
tài liệu chung của cả nhóm.

## Quyết định cần giữ qua các lượt làm việc

- Không yêu cầu Thành xác nhận lại việc lead đã đồng ý kế hoạch mới.
- Task 3 mới là ghép dọc tuyến A -> B -> C -> D: A/D là điểm đi/đến của tài xế;
  B/C là điểm đón/trả của người đi nhờ.
- Bộ lọc cũ yêu cầu A gần B và D gần C trong 5 km không còn là thiết kế đích.
- Mục 15 DATA_CONTRACT.md và kế hoạch DETOUR cũ giữ lọc 5 km rồi chỉ tính đường
  vòng qua pickup sau khi chọn chuyến không còn mô tả phạm vi đích của task 3.
  Không dùng phạm vi cũ để bác bỏ hoặc thu hẹp kế hoạch Thành đã xác nhận với lead.
- Việc phê duyệt kế hoạch và việc bàn giao code/model/Rules là hai trạng thái khác
  nhau. Kế hoạch đã được duyệt; schema và implementation liên nhóm vẫn cần bàn giao.
- Yêu cầu hiện tại chỉ cập nhật ngữ cảnh, chưa triển khai thuật toán hoặc sửa code.

## Pipeline theo kế hoạch đã xác nhận

0. Khi tài xế tạo chuyến, tính đường bộ gốc A -> D để tái sử dụng và lưu mức đi thêm
   tối đa tài xế chấp nhận. Khi đổi tuyến/cấu hình phải cập nhật baseline phù hợp.
1. Query chuyến OPEN còn chỗ, loại chính chủ, theo khung thời gian rộng đã chốt.
   Không ép giờ xuất phát ở A phải khớp từng phút với giờ khách mong muốn tại B.
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
  ngân sách lấy bù/concurrency, giới hạn ứng viên và xử lý chuyến cũ.
- Chưa có DETOUR_CONTRACT.md trong Downloads tại lần kiểm tra trước. Việc thiếu
  file này không phủ nhận xác nhận kế hoạch mới của Thành.

## Phạm vi và trình tự triển khai dự kiến

Tiến độ 20/10: Home đã được bổ sung nhãn km từ Routing và dùng
VietnamRoutingClient/VietnamRouteValidator để chỉ nhận tuyến có dữ liệu quốc gia
đầy đủ và toàn bộ là VN. Đã kiểm tra 9 ca API thật, build Debug đạt; chi tiết trong
`THANH_VIETNAM_ROUTING_REPORT.md`. Chưa thay thuật toán Results hoặc schema Trip.

1. Chốt chữ ký dữ liệu/Routing với leader và Thuần.
2. Routing client và hiện km trên Home; chuẩn bị API để Thuần nối Post/Edit.
3. Nhận bàn giao baseline/mức đi vòng/model/Rules, phối hợp rollout.
4. Thay thuật toán Results bằng pipeline 5 bước và xử lý nhóm 10 chạy nền.
5. Hiển thị metric, lọc/sắp xếp trên RAM và phân biệt Xem thêm với Làm mới.
6. Kiểm tra chiều/độ lệch/thiếu dữ liệu/API lỗi/429/lifecycle/quota, bàn giao minh chứng.

Phần thương lượng giá, lưu điểm trả vào request và chuyển sang Google Maps là
các mở rộng được nêu trong ý tưởng tổng thể; chưa triển khai trong phạm vi tập
trung task 3 hiện tại. Không tự đổi task 4/5 hoặc schema request theo ngữ cảnh này.
Giữ quy tắc Java không lambda/Handler, comment tiếng Việt trên hàm, không sửa
gói khác hoặc file chung ngoài phần đã thống nhất; không tự commit/push.
