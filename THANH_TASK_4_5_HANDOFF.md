# Bàn giao Thành — task 4 và 5

Ngày 19/10/2026. Nhánh làm việc: `thanhnc`. Đã fetch và fast-forward từ
`05cddb6` lên `origin/main` tại `8d7e411`. Bàn giao phần độc lập của task 4/5;
Favorite/SQLite và nghiệm thu runtime vẫn theo mục chưa xác nhận bên dưới.

## Phạm vi đã triển khai

- Task 4 online: nhận `NavigationKeys.TRIP_ID`, listener trip theo lifecycle,
  đọc public profile và ratings từ server; hiển thị giá/giờ/tuyến/chỗ/state/note.
  Không có rating hiển thị chưa đánh giá; lỗi đọc rating giữ là lỗi đọc.
  Chặn thao tác từ cache, tài khoản khác, chính chủ, trip không OPEN/1/pointer rỗng
  hoặc đã tới giờ. Thiếu ID, trip bị xóa, lỗi quyền/mạng đều có thông báo/Back/tải lại.
- Task 5: form điểm đón/lời nhắn/một người, Activity Result đúng LocationKeys;
  Cancel không đổi form. Giữ draft qua xoay và Bundle cho process death.
- RequestReviewViewModel giữ transaction đang chạy qua xoay, chỉ submit khi nhấn.
  Sau process death/lỗi gửi phải đối soát request trên server trước lần gửi tiếp.
  Kết quả gửi chỉ mở Pending khi Activity đang RESUMED và vẫn cùng UID.
- Repository đọc lại trip và request trong transaction trước mọi ghi. ID request
  `tripId_passengerUid`; ID event `requestId_REQUEST_SENT`. Tạo PENDING và thông báo
  trong cùng transaction; giữ nguyên trip OPEN/1. Không gửi lại sau reject/cancel.
  Chặn mutation offline; transaction cũng thất bại nếu offline, không dùng write batch
  hoặc queue offline. createdAt/updatedAt dùng server timestamp.
- Home/Location/Results và thuật toán tìm chuyến hiện có không thay đổi.

## File thuộc phần Thành

- `activity/search/TripDetailActivity.java` và `RequestReviewActivity.java`.
- `activity/search/OnlineTripViewModel.java`, `RequestReviewViewModel.java`,
  `TripDisplay.java`.
- `repository/search/TripSearchRepository.java`: bổ sung API đọc Detail, giữ query Results.
- `repository/search/RequestCreationRepository.java`, `SearchTripPolicy.java`.
- `res/layout/screen_detail.xml`, `screen_review.xml`, `view_search_trip.xml`.

Các đường Java ở trên thuộc `app/src/main/java/com/example/grabshare/`.

## Khai báo Manifest đã được thống nhất

Người dùng đã đồng ý thêm đúng một khai báo Activity. Đã thêm phần sau vào
application của `app/src/main/AndroidManifest.xml`:

```xml
<activity
    android:name=".activity.search.RequestReviewActivity"
    android:exported="false"
    android:windowSoftInputMode="adjustResize" />
```

Không đổi launcher, model, contract, Rules, index, layout dùng chung hoặc package khác.
Detail -> Review -> Pending đã có class, Intent và Manifest. Chưa demo đường này
trên thiết bị bằng dữ liệu Firestore thật; build không chứng minh nghiệp vụ runtime.

## Chưa tích hợp / chưa xác nhận

- FavoriteStore/TripCacheStore: chờ Thuận xác nhận implementation đúng hợp đồng;
  chưa gọi ghi Favorite/cache và chưa hỗ trợ SQLite offline trong bước này.
- PendingActivity của Thuận được gọi bằng `NavigationKeys.REQUEST_ID`, không tạo
  bản sao. Đã có class/Manifest trong main; chưa demo luồng liên gói mới.
- Rules trong main đã có createRequest/requestEvent theo schema trên. Chưa xác nhận
  Rules đang deploy trên Firebase thật bằng một lần gửi từ Android trong lượt này.
- Chưa sửa thuật toán Results hoặc task 1/2. Chưa chạy emulator/app/API ghi thật.
- Kiểm tra đã làm: `:app:assembleDebug --offline` bằng JDK `D:/android/jbr` đạt;
  `git diff --check` đạt. Không thêm/chạy tests vì TASK chưa yêu cầu.

## Checklist tự kiểm tra trên thiết bị

### Task 4 riêng

- [ ] Mở hai trip_id thật: tên, xe, tuyến, giờ, giá, note khác nhau đúng DB.
- [ ] Thiếu ID/ID sai/trip bị xóa: không hiện XML mẫu, thông báo và Back đúng caller.
- [ ] Không rating hiện chưa đánh giá; có rating tính đúng; lỗi đọc không hiện 0 sao.
- [ ] Trip FULL/CANCELLED/COMPLETED/chính chủ/quá giờ chặn gửi; thay state khi mở
      Detail và xem UI cập nhật; kiểm tra lại ở thời điểm nhấn.
- [ ] Mất mạng chặn thao tác gửi; có thông báo và tải lại khi online. SQLite chưa nối.
- [ ] Xoay/Back/đổi tài khoản: giữ đúng ID, gỡ listener, không lộ dữ liệu phiên cũ.

### Task 5 riêng và liên gói

- [ ] Từ Detail mở Review với trip_id; thông tin thật, chọn điểm đón đúng ba extra.
- [ ] Cancel/xoay khi chọn điểm đón không xóa hay tráo form; message giữ nguyên.
- [ ] Điểm đón rỗng/sai, tên quá 200 ký tự, message quá 500 ký tự bị chặn.
- [ ] Gửi thành công: đúng một requests PENDING và một notifications REQUEST_SENT;
      actor/recipient đúng UID, ID cố định, thời gian server. Trip vẫn OPEN/1/pointer rỗng.
- [ ] Click kép/xoay khi gửi: không tạo trùng và không gửi lại từ onCreate/restore.
- [ ] Request đã PENDING/ACCEPTED/REJECTED/CANCELLED: mở trạng thái có sẵn, không resend.
- [ ] Trip bị nhận/hủy/quá giờ trước commit: transaction thất bại, giữ form và tải lại.
- [ ] Tắt mạng/lỗi quyền: không báo thành công, không queue offline; online rồi kiểm tra
      request trước retry. Process death sau commit cũng đối soát theo ID cố định.
- [ ] Sau server commit, Pending nhận đúng request_id; chuyển state do Thuận xử lý.
- [ ] Đưa app nền hoặc đổi tài khoản khi gửi: không điều hướng từ màn đã dừng/phiên cũ.

Nguồn đối chiếu kỹ thuật:
- https://firebase.google.com/docs/firestore/manage-data/transactions
- https://firebase.google.com/docs/firestore/query-data/get-data
