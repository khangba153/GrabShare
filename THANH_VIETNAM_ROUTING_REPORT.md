# Bàn giao tìm tuyến trong Việt Nam — Thành

Ngày 20/10/2026. Làm trên `D:/GrabShare/gs`, nhánh `thanhnc`, sau commit `1524943`.
Phần bàn giao gồm hiển thị km và tìm tuyến nội địa đã kiểm tra.
Không thay model, Rules, index hoặc gói Thuần/Thuận.

## Lỗi đã tái hiện bằng API thật

Tuyến Cà Mau -> Hà Nội không có ràng buộc trả 1.835.537 mét, metadata gồm
`vn`, `kh`, `la`. Một waypoint Đà Nẵng không bảo vệ toàn bộ các chặng còn lại.
`avoid=country_codes:la,kh,cn` được API nhận, đã xác nhận ở tuyến ngắn và
Hà Nội -> Điện Biên. Với các tuyến xuyên Việt, các lượt thử tránh quốc gia toàn
tuyến đã timeout 45/90 giây; không dùng cấu hình này làm đường xử lý đầu tiên.

## Cách xử lý đã triển khai

- Client dùng `motorcycle`, `balanced`, mét, `details=admin_areas`, stopover giữ
  đúng thứ tự waypoint. `POLICY_VERSION=vn-motorcycle-balanced-v1`.
- Lượt 1 tính trực tiếp và kiểm tra dữ liệu quốc gia. Không vẽ kết quả chưa đạt.
- Nếu chưa đạt, lượt 2 chèn các điểm dẫn hướng trong nước theo từng cặp bắt buộc,
  đi đúng chiều bắc/nam; không ép mọi chuyến qua duy nhất một điểm miền Trung.
  Bộ điểm hiện tại: TP.HCM, Nha Trang, Quy Nhơn, Đà Nẵng, Vinh, Ninh Bình; chỉ chèn
  điểm ở giữa vĩ độ hai đầu, cách đầu/cuối hơn 0,4 độ, khi chênh vĩ độ hơn 3 độ.
- Nếu vẫn chưa đạt, thử cuối cùng thêm tránh Lào/Campuchia/Trung Quốc. Trường hợp
  không có điểm hành lang thì bỏ lượt trùng lặp. Tối đa 3 HTTP; read timeout 25 giây/lượt.
- Chỉ chấp nhận khi country_code tổng, admin_areas và country_code của mọi leg
  đều có dữ liệu và chỉ chứa `vn`. Không dùng crossings rỗng làm bằng chứng nội địa.
- Kiểm tra distance/time hữu hạn, đơn vị mét, số leg, tổng khoảng cách các leg và
  geometry. Thiếu/quốc gia khác/dữ liệu hỏng thì không trả một tuyến "hợp lệ".
- Giữ riêng các đoạn geometry khi vẽ; không cắt đoạn nước ngoài hoặc nối đường
  thẳng để tạo tuyến giả. Số km trên map lấy từ chính tuyến đã qua kiểm tra.
- Giữ ánh xạ khoảng cách các chặng bắt buộc A/B/C/D dù có thêm waypoint phụ.
- Đổi điểm/đóng màn sẽ vô hiệu callback và ngắt kết nối đang chờ. 400/404, quyền
  hoặc quota được báo riêng, không tự lặp các lỗi này. Không log URL có API key.

## Kiểm tra đã thực hiện

Client Java thật được compile riêng với org.json để kiểm tra parser, validator,
fallback, mapping các chặng và cancellation. Kiểm tra HTTP thật dùng transport
PowerShell trên máy này; kế hoạch waypoint và xử lý kết quả vẫn chạy trong chính
VietnamRoutingClient. Android dùng HttpURLConnection, đã compile trong build APK.
Chưa thao tác UI/emulator để xác nhận tầng mạng/render Android trong lượt này.

| Ca API thật | Km được chấp nhận | Lượt HTTP | Kết quả |
|---|---:|---:|---|
| Cà Mau -> Hà Nội | 2192,520 | 2 | Chỉ vn, 7 leg |
| Hà Nội -> Cà Mau | 2186,490 | 2 | Chỉ vn, 7 leg |
| TP.HCM -> Điện Biên | 2244,841 | 2 | Chỉ vn, 6 leg |
| Điện Biên -> TP.HCM | 2229,812 | 2 | Chỉ vn, 6 leg |
| Cà Mau -> Điện Biên | 2537,757 | 2 | Chỉ vn, 7 leg |
| Điện Biên -> Cà Mau | 2523,181 | 2 | Chỉ vn, 7 leg |
| Hà Nội -> Điện Biên | 435,834 | 1 | Chỉ vn |
| Hai điểm tại TP.HCM | 5,102 | 1 | Chỉ vn |
| TP.HCM -> Nha Trang -> Ninh Bình -> Hà Nội | 1907,826 | 1 | Chỉ vn, đúng 3 chặng |

Đã kiểm tra local: thiếu/rỗng country_code, quốc gia khác ở tổng/area/leg,
thiếu quốc gia ở leg, null metadata, fallback sau tuyến bị loại, giới hạn số lần
thử, hủy trước request, hủy trước fallback, ánh xạ AB/BC khi có waypoint phụ.
Fixtures/script kiểm tra và phản hồi đã che credential ở `build/route-validation/`
(thư mục build bị Git ignore, không phải phần source ứng dụng).

Build `:app:assembleDebug --offline` bằng JDK `D:/android/jbr` thành công.
`git diff --check` đạt. Không gọi Firebase ghi dữ liệu hoặc thay database.

## File chính

- `app/src/main/java/com/example/grabshare/api/VietnamRoutingClient.java`
- `app/src/main/java/com/example/grabshare/api/VietnamRouteValidator.java`
- `app/src/main/java/com/example/grabshare/activity/search/HomeActivity.java`
- Nhãn km/formatter ở bước trước: `view_home_map.xml`, `RouteDistanceFormatter.java`.

## Giới hạn và bàn giao

- Đây là tuyến nội địa được xác nhận theo metadata địa giới của provider; hành lang
  dẫn hướng không bảo đảm là tuyến nội địa ngắn nhất hoặc tối ưu nhất toàn quốc.
- Nếu không xác nhận được, app báo lỗi thay vì hiển thị tuyến qua nước khác.
- Thuần cần dùng cùng policy cho baseline A->D khi nối Post/Edit; baseline tính
  theo cấu hình cũ không nên đem trừ với tuyến ghép tính theo policy mới.
- Client đã nhận 2-4 điểm bắt buộc để chuẩn bị task 3; chưa thay thuật toán Results.
- Cache/Favorite, thương lượng và mở Google Maps không nằm trong bước này.

## Checklist trên Android

- [ ] Mở Home, chọn đủ hai điểm: đường và nhãn km đúng cùng phản hồi.
- [ ] Thử các tuyến dài ở bảng, cả hai chiều; không có tuyến nước ngoài xuất hiện
      trong lúc chờ fallback và không dùng km của lượt bị loại.
- [ ] Đổi điểm nhanh, xoay/Back khi đang tải: không đè tuyến, không crash.
- [ ] Mất mạng/quota/điểm xa đường: có thông báo, không hiện 0 km giả hoặc giữ số cũ.

Nguồn đối chiếu:
- https://apidocs.geoapify.com/docs/routing/
- https://apidocs.geoapify.com/how-to/routing/identify-route-administrative-areas/
