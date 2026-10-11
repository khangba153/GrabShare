# Seed dữ liệu GrabShare

Đây là công cụ chuẩn bị dữ liệu demo cho nhóm trưởng; không phải chức năng Android.
Trạng thái: nhóm trưởng đã chạy trên Cloud Shell và báo “Đã ghi 27 document”.
Ảnh Console đã thể hiện 7 collection; chưa kiểm chứng truy cập từ ứng dụng Android.
Không cần firebase-admin, npm hoặc service account JSON.
Python dùng thư viện chuẩn; chế độ ghi lấy quyền từ tài khoản gcloud.

## Project và tài khoản

- Project ID: grabshare.
- Project number: 334276006780.
- Database: (default).
- D1 / Minh Demo: lyl3DvFLjegzIFnggYgGxtGyQNu2.
- P1 / An Demo: 4p8CTioum2TJwmNG30GK07gwN713.
- P2 / Bình Demo: xc2sDuP38Lak32OAac5Q61mz7FG2.

Email được lấy từ Authentication khi apply; không tự suy ra email từ tên tài khoản.
Script không tạo/xóa tài khoản Auth và không đọc hoặc lưu mật khẩu.
Tọa độ cấu hình là điểm demo, chưa được xác minh là điểm đón chính xác.

## Kết quả dự kiến

| Collection | Số document |
|---|---:|
| users | 3 |
| publicProfiles | 3 |
| trips | 7 |
| requests | 6 |
| favorites | 2 |
| ratings | 1 |
| notifications | 5 |
| Tổng | 27 |

Các chuyến: seed_trip_paid, seed_trip_free, seed_trip_pending, seed_trip_full,
seed_trip_rejected, seed_trip_cancelled, seed_trip_completed.

Yêu cầu dùng ID tripId_UID; yêu thích UID_tripId; đánh giá tripId_UID.
Chuyến FULL/COMPLETED/CANCELLED có acceptedRequestId trỏ đúng yêu cầu.
Chuyến hủy giữ yêu cầu ACCEPTED làm lịch sử; UI ưu tiên trạng thái chuyến CANCELLED.
Ngày chuyến tương lai mặc định ngày mai theo UTC+7; completed dùng ngày hôm qua.
createdAt của document đã tồn tại được giữ; ngày giờ/số chỗ/trạng thái seed được đặt lại.

## Cách chạy đề xuất trên Cloud Shell

Máy hiện tại chưa phát hiện gcloud trong PATH.
Cloud Shell giúp chạy mà không cài Google Cloud CLI trên Windows.

1. Mở https://console.cloud.google.com/?project=grabshare bằng tài khoản quản lý project.
2. Bấm Activate Cloud Shell, chờ terminal sẵn sàng.
3. Dùng Upload để đưa seed_grabshare.py và seed.config.json vào cùng thư mục.
4. Xem trước:

    python3 seed_grabshare.py --preview

5. Kiểm tra UID, document ID, ngày giờ và tổng count = 27.
6. Bắt đầu ghi:

    python3 seed_grabshare.py --apply

7. Nếu Cloud Shell hỏi Authorize, dùng tài khoản có quyền trên project GrabShare.
8. Script kiểm tra project number, ba UID và đọc các document mục tiêu.
9. Đọc danh sách CREATE/UPDATE; gõ grabshare ở terminal để xác nhận, Enter để hủy.
10. Chỉ ghi nhận thành công khi có dòng Server đã xác nhận commit và số 27.
11. Mở Firebase Console, Data; đối chiếu bảng số lượng ở trên.

Không chạy lệnh auth print-access-token bằng tay để gửi token vào chat.
Script lấy token trong bộ nhớ, không in hoặc lưu token.

## Xem trước trên Windows

Trong Terminal của dự án:

    python scripts/seed_grabshare.py --preview

Chế độ này không cần đăng nhập, không gọi cloud, email hiển thị placeholder.
Chế độ apply trên Windows cần Google Cloud CLI đã cài và đăng nhập.
Có thể chỉ định ngày cho chuyến tương lai:

    python scripts/seed_grabshare.py --preview --date 2026-10-24

Dùng cùng --date khi apply nếu muốn giữ ngày đã xem trước.

## Phạm vi cập nhật

- Chỉ đọc/ghi 27 đường dẫn được cấu hình, không quét hoặc xóa collection.
- Các field có trong bộ seed sẽ được cập nhật lại; field ngoài seed được giữ.
- Giữ createdAt của document cũ.
- Ghi toàn bộ bằng một commit nguyên tử, kèm điều kiện updateTime/exists.
- Nếu dữ liệu đổi sau lúc đọc, commit bị từ chối thay vì ghi đè thay đổi mới.
- Không tự retry commit; lỗi/timeout cần kiểm tra Console trước khi chạy lại.
- Chạy lại không tạo ID mới, nhưng sẽ đặt lại trạng thái/ngày của các chuyến seed.
- Sau khi thành viên dùng seed thử nghiệp vụ, không chạy reset giữa lúc họ thao tác.

## Quyền và lỗi

Script dùng OAuth của tài khoản Google, xét quyền IAM quản trị; không phải đăng nhập demo Android.
Tài khoản cần đọc metadata project, đọc tài khoản Auth và đọc/ghi Firestore.
Chạy bằng tài khoản chủ project là cách thuận tiện cho nhóm trưởng.
HTTP 401/403: kiểm tra tài khoản, quyền và API; không mở Firestore rules toàn bộ để sửa lỗi script.
Thiếu UID/email: kiểm tra Authentication, sửa config rồi chạy lại.
Tên file upload có hậu tố (1): sửa về đúng seed_grabshare.py / seed.config.json.
Thiếu count = 27 hoặc quan hệ sai: script dừng trước khi ghi.

Seed chạy bằng quyền quản trị không chứng minh app Android vượt qua Security Rules.
Rules cũ vẫn chưa khớp schema mới; nhóm phải chuẩn bị rules theo thiết kế đã chốt trước khi nối app.

## Git và minh chứng

Có thể commit ba file trong scripts; không commit token, mật khẩu hay service account.
Sau khi chạy thành công, ghi ngày thực hiện, người thực hiện, commit script và ảnh 7 collection vào báo cáo.
Chưa ghi kết quả hoàn thành trước khi server xác nhận.

## Tài liệu chính thức

- https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/commit
- https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/projects.accounts/lookup
- https://docs.cloud.google.com/sdk/gcloud/reference/auth/print-access-token
- https://docs.cloud.google.com/shell/docs/uploading-and-downloading-files
