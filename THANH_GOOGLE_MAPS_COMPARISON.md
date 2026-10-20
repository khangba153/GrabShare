# Đối chiếu khoảng cách với Google Maps

Ngày kiểm tra: 20/10/2026. Repo D:/GrabShare/gs, nhánh thanhnc, code eb40e77.
Chỉ kiểm tra và ghi báo cáo; không sửa code hay flow ứng dụng.

## Cách đo và giới hạn

- Gọi API thật qua harness Java dùng chính VietnamRoutingClient của ứng dụng; transport PowerShell thay HttpURLConnection để chạy trên máy tính. Chưa kiểm tra UI Android trong lượt này.
- App: Geoapify motorcycle, balanced, chính sách vn-motorcycle-balanced-v1; mọi kết quả dưới đây vượt kiểm tra metadata chỉ có vn.
- Google Maps web không trả chỉ đường xe máy trong môi trường kiểm tra. Dùng driving + avoid=highways, giữ cùng tọa độ đầu/cuối. Đây là so sánh tham khảo giữa hai chế độ, chưa phải đo sai số xe máy tương đương.
- Google có làm tròn km và xét tình trạng giao thông/đóng đường. Tuyến đề xuất không nhất thiết ngắn nhất. Chưa xác minh toàn bộ từng bước và quốc gia của các tuyến Google; không dùng các phương án qua Tuyến đường 13 làm chuẩn nội địa.
- Chênh lệch = app - Google; phần trăm chia cho Google. Số dương nghĩa là app dài hơn, không tự động chứng minh lỗi đo khoảng cách.

## Kết quả

| Tuyến | App (km) | Google (km) | Chênh lệch | Ghi chú |
|---|---:|---:|---:|---|
| Nội thành TP.HCM | 5,102 | 5,6 đề xuất; 5,1 và 5,0 thay thế | -0,498 km (-8,89%) so tuyến đề xuất | App gần trùng phương án Google 5,1 km; không suy ra độ chính xác 2 mét từ số Google đã làm tròn |
| Hà Nội → Điện Biên | 435,834 | 437 đề xuất; 461 và 477 thay thế | -1,166 km (-0,27%) | Google đề xuất qua QL37, có cảnh báo tránh đường đóng QL279 |
| TP.HCM → Điện Biên | 2.244,841 | 1.951 đề xuất; 1.970 và 1.809 thay thế | +293,841 km (+15,06%) | Google đề xuất Hồ Chí Minh/CT02/QL14; app cần fallback dẫn hướng, 2 lần gọi API |
| Cà Mau → Hà Nội | 2.192,520 | 1.936 đề xuất; 1.794 và 1.743 thay thế | +256,520 km (+13,25%) | Google đề xuất QL1A, cảnh báo tránh đường đóng CT01; app cần fallback, 2 lần gọi API |
| Cà Mau → Hà Nội, cùng 6 mốc trung gian | 2.192,520 | 2.021 | +171,520 km (+8,49%) | Cùng HCM, Nha Trang, Quy Nhơn, Đà Nẵng, Vinh, Ninh Bình; vẫn khác chế độ xe |

Tất cả bốn case app được gọi lại trong lượt kiểm tra và trả cùng khoảng cách như trên.

## Điểm kiểm tra và nguồn trực tiếp

- Cà Mau: 9.1769,105.1524.
- TP.HCM: 10.762622,106.660172; điểm cuối tuyến nội thành: 10.783014,106.694893.
- Hà Nội: 21.029,105.854.
- Điện Biên: 21.386,103.023.
- [Google nội thành TP.HCM](https://www.google.com/maps/dir/?api=1&origin=10.762622%2C106.660172&destination=10.783014%2C106.694893&travelmode=driving&avoid=highways&hl=vi).
- [Google Hà Nội → Điện Biên](https://www.google.com/maps/dir/?api=1&origin=21.029%2C105.854&destination=21.386%2C103.023&travelmode=driving&avoid=highways&hl=vi).
- [Google TP.HCM → Điện Biên](https://www.google.com/maps/dir/?api=1&origin=10.762622%2C106.660172&destination=21.386%2C103.023&travelmode=driving&avoid=highways&hl=vi).
- [Google Cà Mau → Hà Nội](https://www.google.com/maps/dir/?api=1&origin=9.1769%2C105.1524&destination=21.029%2C105.854&travelmode=driving&avoid=highways&hl=vi).
- [Google Cà Mau → Hà Nội cùng 6 mốc](https://www.google.com/maps/dir/?api=1&origin=9.1769%2C105.1524&destination=21.029%2C105.854&waypoints=10.762622%2C106.660172%7C12.24%2C109.19%7C13.77%2C109.22%7C16.05%2C108.21%7C18.67%2C105.68%7C20.25%2C105.97&travelmode=driving&avoid=highways&hl=vi).

Các link tính lại theo thời điểm mở nên có thể khác kết quả đã quan sát.

## Nhận định và việc tiếp theo

Mẫu ngắn và Hà Nội → Điện Biên gần một phương án Google. Hai mẫu xuyên Việt chênh đáng kể, khoảng 13–15% so tuyến Google đề xuất. Chưa thể kết luận toàn bộ độ chênh là sai số API vì chế độ xe, tuyến và ràng buộc khác nhau.

Thử cùng waypoint làm Google Cà Mau → Hà Nội tăng từ 1.936 lên 2.021 km: mốc cố định góp phần gây đường vòng, nhưng không giải thích hết 171,520 km còn lại. Cần so từng chặng và đối chiếu Google Maps Android chế độ xe máy trước khi quy phần còn lại cho dữ liệu hoặc thuật toán.

Ưu tiên cải thiện nếu được triển khai tiếp: thử ít mốc dẫn hướng hơn/đặt mốc trên trục đường thay vì tâm đô thị, kiểm tra vn sau mỗi ứng viên, so sánh các ứng viên hợp lệ trong giới hạn request. Chỉ nhận ứng viên đã qua kiểm tra quốc gia; không nới điều kiện để đổi lấy km ngắn hơn. Chưa thực hiện những thay đổi này trong lượt test.

Checklist cho Thành: mở các link đúng tọa độ; đối chiếu thêm trên Google Maps Android xe máy; kiểm tra chặng nào vòng nhiều; thống nhất ngưỡng chênh chấp nhận trước khi dùng khoảng cách tuyến dài cho lọc detour task 3.
