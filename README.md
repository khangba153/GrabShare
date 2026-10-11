# GrabShare — dự án cộng đồng đi chung xe

Android Java/XML, nhóm 4 người, Firebase dùng chung project grabshare.
Hạn nộp 25/10/2026; mỗi chuyến xe máy có tối đa một chỗ đi nhờ.

## Trạng thái hiện tại

- Đã có 27 màn XML và trình xem bằng vuốt, chưa có nghiệp vụ Android.
- Đã chuẩn bị cấu hình Authentication/Firestore và model dữ liệu chung.
- Nhóm trưởng đã seed 27 document và Publish Rules đọc giai đoạn base.
- Playground đã kiểm tra quyền đọc trip/users; chưa kiểm chứng kết nối Android.
- Build Debug thành công; nhóm trưởng báo đã test bộ chọn màn và đạt. Auth/router thật, quyền ghi, SQLite và API còn phải làm.

Base đã push lên main tại commit `02af6bd`.

## Tài liệu bắt đầu

Hướng dẫn trực quan public: https://grabshare-team-roadmap.snowy-frost-6677.chatgpt.site
Ngữ cảnh nạp AI theo gói ở docs/ai; mỗi file TASK đã chứa cả quy tắc chung.
Hợp đồng bàn giao đã chốt ở docs/HANDOFF_CONTRACT.md; package contract chứa
hằng số/callback/interface. Chưa có triển khai repository hoặc router thật.

- [Bắt đầu từ main](docs/START_HERE.md): pull, chọn gói, nhánh, context AI và PR.
- [Hợp đồng dữ liệu](docs/DATA_CONTRACT.md): field, model, ID, trạng thái, query.
- [Phân công A/B/C](docs/TEAM_TASKS.md): file, nhiệm vụ, tiêu chí, lịch và Git.
- [Firebase base](docs/FIREBASE_BASE.md): cấu hình, Rules và quyền đọc.
- [Seed demo](scripts/README.md): dành cho nhóm trưởng, không tự reset khi đang làm.

Thành, Thuận, Thuần tự chọn A/B/C. Leader quản lý base/tài khoản/tích hợp.
Java mới không lambda, không Handler, comment tiếng Việt trên hàm.

## Mở dự án

Mở thư mục clone bằng Android Studio, Sync Gradle. Mỗi máy cấu hình SDK riêng;
không commit local.properties, mật khẩu hoặc service account. Dùng cùng
app/google-services.json, không tạo Firebase riêng theo nhánh Git.

Debug đã có bộ chọn màn theo L/A/B/C, xem XML hoặc mở Activity đã triển khai;
xem docs/RUN_SCREEN.md. Công cụ đã build Debug; nhóm trưởng báo đã test và đạt. Release vẫn mở preview.
Preview vẫn vuốt qua 27 XML, nút/ô nhập chưa có chức năng.
Không tính dữ liệu minh họa trên XML là dữ liệu Firestore thật.
