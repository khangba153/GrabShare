# Bắt đầu từ base trên main — 11/10/2026

Base đã push tại commit `02af6bd`. Build Debug thành công; nhóm trưởng báo đã test bộ chọn màn và đạt. Đây là base để làm từng phần độc lập. Android chưa đăng nhập/đọc Firestore thật; Rules hiện chặn mọi ghi.

## 1. Nhận code và tạo nhánh

Nếu chưa clone:

```bash
git clone https://github.com/khangba153/GrabShare.git
cd GrabShare
```

Nếu đã clone, kiểm tra `git status`. Có thay đổi đang làm thì commit hoặc stash trước khi chuyển nhánh; không bỏ thay đổi bằng reset.

```bash
git switch main
git pull --ff-only origin main
git config user.name "Ten cua ban"
git config user.email "Email da xac minh tren GitHub cua ban"
```

Tự chọn một gói chưa có người nhận, báo nhóm trưởng để tránh trùng. Tạo đúng một nhánh theo gói đã chọn:

```bash
git switch -c codex/task-a
# Gói B: codex/task-b; gói C: codex/task-c
```

## 2. Mở và chạy

Mở thư mục clone bằng Android Studio, Sync Gradle, chọn build variant Debug rồi Run app. Bộ chọn màn cho xem XML hoặc mở Activity thật đã được tạo và đăng ký. Đọc `docs/RUN_SCREEN.md`.

Giữ `app/google-services.json` từ base: tất cả dùng Firebase project grabshare, database (default). Không tạo Firebase riêng hoặc tự chạy seed. Mỗi máy dùng SDK/local.properties riêng, không commit file đó. JSON cấu hình không tự đăng nhập tài khoản.

## 3. Nạp ngữ cảnh AI

Gửi `docs/ai/TASK_A.md`, `TASK_B.md` hoặc `TASK_C.md` tương ứng. File đã có quy tắc chung, schema, flow, file sở hữu, phụ thuộc và checklist. Đối chiếu `docs/TEAM_TASKS.md`, `DATA_CONTRACT.md`, `HANDOFF_CONTRACT.md` khi cần.

Prompt: “Đọc TASK của tôi và repo hiện tại. Kiểm tra nhánh, file và phần base đã có. Đề xuất thứ tự theo phụ thuộc, bắt đầu từ UI/Activity độc lập. Chỉ làm gói của tôi, Java không lambda/Handler, comment tiếng Việt trên hàm. Không giả dữ liệu XML là dữ liệu Firebase thật. Báo đầu vào còn thiếu trước khi nối chức năng phụ thuộc.”

## 4. Bắt đầu công việc

- A: Home/Location/Results/Detail/Review; chuẩn bị form, Activity Result và trạng thái hiển thị.
- B: màn trạng thái/MyTrips/Favorites; chuẩn bị adapter, interface FavoriteStore/TripCacheStore và SQLite theo hợp đồng.
- C: màn chủ xe/Post/Edit/Manage/Requests/Notifications; chuẩn bị form và các trạng thái hiển thị.
- Mỗi gói làm Activity trong package đã chốt, sửa XML của mình, giữ dữ liệu form/ID khi xoay. Dữ liệu tạm phải ghi rõ là demo.
- Đăng ký Activity trong Manifest là thay đổi file chung: gửi phần khai báo trong PR để leader review. Không đổi model/theme/router/schema riêng lẻ.
- Chờ leader bàn giao Auth, Rules ghi và index phù hợp trước demo Firestore có ghi. Màn của gói khác và repository chưa triển khai cũng là phụ thuộc, không tạo bản sao.

## 5. Commit → PR → main

Commit theo phần thực sự hoàn thành, không chia commit giả hoặc đổi author:

```bash
git status
git add duong/dan/file-cua-ban
git commit -m "feat(search): implement trip search screen"
git push -u origin codex/task-a
```

Thay nhánh/tên commit theo gói và nội dung thực tế. Tạo Pull Request vào main, ghi chức năng/file, phụ thuộc, kết quả tự kiểm tra, ảnh và phần còn thiếu. Leader review rồi dùng **Create a merge commit** để giữ lịch sử/author. Thành viên không push trực tiếp main.

Trước PR hoặc nhận base mới, trên nhánh đang làm:

```bash
git fetch origin
git merge origin/main
```

Giải quyết xung đột, xem lại phần mình rồi push. Các thành viên khác nhận code đã merge bằng cách merge origin/main vào nhánh của họ. Không cần clone lại.

## Mốc kế tiếp của nhóm trưởng

Auth/tài khoản → Rules ghi và index theo từng thao tác → bàn giao đọc/ghi thật → router liên gói. Mốc 1 đạt không có nghĩa toàn bộ nghiệp vụ đã sẵn sàng.
