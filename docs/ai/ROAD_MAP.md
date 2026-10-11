# ROAD_MAP.md

## 11–12/10 — Chọn gói & bàn giao base
Chốt interface, auth, Rules/index theo thao tác.

## 13–15/10 — Đọc thật & nối màn
Mỗi gói có loading/rỗng/lỗi, form xoay không mất.

## 16–18/10 — Ghi & kỹ thuật
Transaction, API, SQLite; PR nhỏ theo chức năng.

## 19–20/10 — Ghép end-to-end
Hai tài khoản, tranh chỗ, hủy/từ chối/hoàn thành.

## 21–22/10 — Rà soát & báo cáo
Responsive/offline/quyền, ghép ảnh/commit/minh chứng.

## 23/10 — PDF & slide
UEH mặc định <20%, demo kỹ thuật.

## 24/10 — Đóng băng & tập demo
Chỉ sửa lỗi cần thiết; thử kịch bản dự phòng.

## 25/10 — Nộp
Kiểm tra GitHub/APK/PDF/slide theo giờ giảng viên.

## Bàn giao
- L → A/B/C: Đăng nhập, model, Rules & router. UID từ Auth; trip_id/request_id trong Intent; quyền ghi mở theo từng thao tác. Cần triển khai; hiện chỉ config/model/Rules đọc.
- A → C: Chọn địa điểm. Activity Result trả place_name/place_latitude/place_longitude; caller giữ ô đang chọn qua xoay. Back không đổi ô. Đã chốt LocationKeys; A chưa triển khai LocationActivity.
- B → A/L: Favorite & cache SQLite. B implements FavoriteStore/TripCacheStore; A gọi repo B, L gọi clearUserCache(uid). Cache trả CachedTrip hoặc null. Đã chốt interface/constructor/callback; B chưa triển khai repository.
- A → B: Yêu cầu vừa tạo. A commit PENDING thành công → mở PendingActivity bằng request_id. Không tạo màn Pending trùng trong A.
- C → B: Phản hồi chủ xe. C commit ACCEPTED/REJECTED/COMPLETED/CANCELLED; B đọc state từ DB. Rules atomic trước khi ghi.
- B → C: Hủy yêu cầu đã nhận. B transaction CANCELLED + trip OPEN/1/pointer rỗng; C reload và nhận người khác. Cùng state contract, không tăng chỗ hai lần.
- A/B/C → C: Sự kiện → inbox. Gói gây sự kiện ghi notification cùng transaction; C đọc/mark isRead. ID deterministic. Không gọi ghi notification tách rời sau commit.