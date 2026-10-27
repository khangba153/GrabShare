# GrabShare

Ứng dụng Android hỗ trợ chia sẻ một chỗ đi nhờ trên chuyến xe máy, xây dựng trong học phần **Phát triển ứng dụng Mobile — UEH**.

Chủ xe đăng tuyến, thời gian và giới hạn đi thêm. Người đi nhờ chọn điểm đón/trả, tìm chuyến và xác nhận khoản đóng góp trước khi gửi yêu cầu. Sau khi chủ xe chấp nhận, hai bên theo dõi chuyến và trao đổi qua chat.

Ứng dụng phục vụ cộng đồng có nhu cầu đi chung xe; các tuyến UEH là dữ liệu minh họa, không phải điều kiện giới hạn tài khoản.

## Chức năng

| Nhóm | Nội dung |
| --- | --- |
| Tài khoản | Đăng ký, đăng nhập email/mật khẩu hoặc Google, khôi phục mật khẩu, hoàn thiện/chỉnh sửa hồ sơ và đăng xuất. |
| Chủ xe | Đăng/sửa chuyến, nhập giới hạn đi thêm, nhận/từ chối khách, hủy nhận khách, hủy và hoàn thành chuyến khi đủ điều kiện. |
| Người đi nhờ | Chọn địa điểm, tìm/sắp xếp chuyến, xem tuyến và khoản đóng góp, gửi/theo dõi/hủy yêu cầu, xem lịch sử. |
| Sau chuyến | Đánh giá và lưu yêu thích đối với chuyến hoàn thành mà hành khách đã tham gia. |
| Trao đổi | Thông báo nghiệp vụ trong app, chat theo yêu cầu được nhận, bộ đếm thông báo và hội thoại chưa đọc riêng. |
| Ngoại tuyến | Xem lại thông tin chuyến đã lưu bằng SQLite, phân tách theo tài khoản. Các thao tác nghiệp vụ vẫn cần mạng. |

Danh sách mô tả chức năng có trong mã nguồn, không thay kết quả nghiệm thu mọi tình huống trên mọi thiết bị.

## Công nghệ và cấu hình

| Thành phần | Cấu hình trong dự án |
| --- | --- |
| Ngôn ngữ / giao diện | Java, XML; mức tương thích mã Java 11 |
| Android SDK | `minSdk 29`, `compileSdk 37`, `targetSdk 37` |
| Android Gradle Plugin / Gradle Wrapper | `9.3.3` / `9.5.0` |
| JVM chạy Gradle daemon | Toolchain Java 25, khai báo trong `gradle/gradle-daemon-jvm.properties` |
| Firebase | Authentication, Cloud Firestore; Firebase BoM `35.0.0` |
| Google Sign-In | Credential Manager và Google ID |
| Lưu cục bộ | SQLite: `grabshare_cache.db`, bảng `viewed_trips` |
| Địa điểm / định tuyến / bản đồ | LocationIQ, Geoapify, osmdroid `6.1.18` |
| Package | `com.example.grabshare` |

Mức Java 11 dùng để biên dịch mã ứng dụng khác với JVM chạy Gradle. Khi Sync lỗi, kiểm tra toolchain trước khi thay phiên bản Java hoặc Gradle của cả nhóm.

## Cài đặt và chạy

### 1. Lấy mã nguồn

```powershell
git clone https://github.com/khangba153/GrabShare.git
cd GrabShare
```

Mở **thư mục vừa clone** bằng Android Studio. Đây là thư mục chứa `settings.gradle.kts`, `gradlew.bat` và thư mục `app`.

### 2. Chuẩn bị môi trường

1. Dùng Android Studio hỗ trợ phiên bản AGP của dự án.
2. Cài Android SDK Platform 37 và các thành phần SDK mà Gradle yêu cầu.
3. Chạy **Sync Project with Gradle Files**, cho phép tải phụ thuộc/toolchain cần thiết.
4. Chọn điện thoại hoặc máy ảo Android API 29 trở lên. Kiểm thử Google Sign-In nên dùng thiết bị có Google Play services.

Android Studio tạo `local.properties` theo SDK trên từng máy. Không sao chép đường dẫn SDK của thành viên khác hoặc commit tệp này.

### 3. Firebase và các dịch vụ API

- Cấu hình Android nằm tại `app/google-services.json`, được quản lý trong repository để nhóm dùng chung Firebase project. Package phải khớp `com.example.grabshare`; không tạo Firebase riêng theo nhánh Git.
- Firebase Authentication cần bật **Email/Password** và **Google**. Clone repository không tự cấp quyền Firebase Console; quyền quản trị được cấp riêng.
- Firestore cần Rules tương thích client và các index trong `firestore.indexes.json`. Clone hoặc Run app không tự Publish `firestore.rules` hay tạo index.
- Nếu đổi Firebase project, cần cấu hình lại Auth, Firestore, Rules/index và tải `google-services.json` tương ứng.
- Tìm địa điểm/tính tuyến cần khóa LocationIQ/Geoapify còn hiệu lực. Bản hiện tại cấu hình khóa trong mã API và các nơi khởi tạo routing client; chưa có cơ chế đọc khóa từ biến môi trường hoặc `local.properties`.

Không đưa mật khẩu demo, service-account JSON, token hoặc khóa ký riêng vào README, báo cáo hay commit.

### 4. Google Sign-In trên từng máy phát triển

Mỗi máy có thể dùng debug keystore khác nhau. Thành viên tự build cần đăng ký SHA-1 của chứng chỉ ký APK đó vào ứng dụng Android trong Firebase.

Tại thư mục gốc có `gradlew.bat`, chạy:

```powershell
.\gradlew.bat signingReport
```

1. Lấy **SHA-1 của variant debug**.
2. Gửi SHA-1 cho người quản lý Firebase, hoặc tự thêm nếu có quyền: **Project settings → Your apps → ứng dụng Android → Add fingerprint**.
3. Tải `google-services.json` mới sau khi cập nhật và thay tại `app/google-services.json` khi cần.
4. Sync Gradle, build/cài lại app và thử đăng nhập Google.

Đây là cấu hình chứng chỉ bản build. Người dùng cuối cùng cài một APK không phải tự gửi SHA-1; họ dùng chung chứng chỉ ký của APK đó.

### 5. Chạy hoặc build APK

Trong Android Studio, chọn cấu hình **app**, chọn thiết bị rồi nhấn **Run**. Launcher chính là `MainActivity`, mở luồng đăng nhập/kiểm tra hồ sơ. Bộ chọn màn riêng đã bị vô hiệu hóa trong manifest Debug.

Build Debug bằng PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`.

Khi thiết bị đã kết nối qua ADB, có thể cài bằng:

```powershell
.\gradlew.bat installDebug
```

Build thành công chỉ xác nhận biên dịch/đóng gói, chưa xác nhận Firebase, API hoặc toàn bộ luồng hoạt động đúng.

## Luồng sử dụng chính

Chuẩn bị **hai tài khoản khác nhau** trên hai thiết bị hoặc hai phiên chạy riêng để thử hai vai trò.

1. **Chủ xe:** đăng nhập, bổ sung thông tin xe, vào **Chuyến của tôi → Chủ xe → Đăng chuyến**. Nhập tuyến, giờ tương lai, giới hạn đi thêm và mức đóng góp toàn tuyến.
2. **Người đi nhờ:** vào **Tìm chuyến**, chọn điểm đón/trả và thời gian. Mở kết quả, xem khoản dự kiến, tích xác nhận rồi gửi yêu cầu.
3. **Chủ xe:** mở chuyến, vào **Yêu cầu đi nhờ**, chọn yêu cầu rồi **Chấp nhận** hoặc **Từ chối**. Khách đã nhận được xem lại tại **Khách đã nhận**.
4. **Hai bên:** theo dõi trạng thái/thông báo và mở chat của yêu cầu đã nhận để thống nhất đón/trả.
5. **Kết thúc:** chủ xe xác nhận hoàn thành khi chuyến thực tế đã kết thúc và ứng dụng cho phép; khách xem kết quả, đánh giá và lưu yêu thích nếu đủ điều kiện.

Hủy nhận một khách và hủy cả chuyến là hai thao tác riêng. Hủy nhận đúng khách trước giờ khởi hành trả chỗ để xét khách khác, không cần tạo lại toàn chuyến.

## Quy tắc nghiệp vụ

- Một chuyến chỉ có **một chỗ đi nhờ**. Gửi yêu cầu chưa giữ chỗ; nhận thành công mới chuyển Trip sang `FULL`, còn 0 chỗ và liên kết đúng Request.
- Chủ xe không xin đi chuyến của mình. Nhận/hủy phải kiểm quyền, thời gian và trạng thái hiện tại.
- Giới hạn đi thêm nhập **0–10 km**, tối đa một chữ số thập phân; 0 là không chấp nhận đi thêm.
- A/D là điểm xuất phát/đích của chủ xe; P/C là điểm đón/trả khách. Tuyến ghép theo **A → P → C → D**. Phần đi thêm là chênh lệch không âm so với tuyến gốc mới tính.
- Khoản đóng góp dựa trên **quãng đường khách đi**, khác với phần chủ xe đi thêm:

  ```text
  Khoản khách = mức toàn tuyến × min(quãng đường khách / quãng đường gốc, 1)
  ```

  Dùng mét chưa làm tròn; tiền làm tròn đến đồng. Request lưu `contributionSnapshot` của khoản đã xác nhận, không lấy giá Trip hiện tại thay tiền lịch sử. Lỗi tuyến hoặc thiếu dữ liệu không được hiển thị thành miễn phí.
- Hoàn thành chuyến không xác nhận đã thanh toán. Hai bên thanh toán ngoài ứng dụng.
- Chat chỉ cho hai bên liên quan gửi khi yêu cầu đã được nhận và chuyến còn hợp lệ; sau hủy/hoàn thành giữ lịch sử và khóa gửi.
- Đánh giá/yêu thích chỉ dành cho khách của chuyến hoàn thành. Yêu thích không giữ chỗ.
- SQLite chỉ phục vụ xem lại; các thao tác nghiệp vụ cần máy chủ. Đăng xuất xóa cache của đúng UID.

## Giới hạn hiện tại

- **Gửi lại sau hủy theo từng lần:** có hợp đồng hỗ trợ, nhưng `REQUEST_ATTEMPTS_ENABLED` trong `RequestCreationRepository` hiện là `false`; chưa coi là luồng đã bật.
- **Khóa chống đăng chuyến trùng:** cần tích hợp/kích hoạt đồng bộ client, Rules và dữ liệu; chưa khẳng định mọi chuyến trùng đã bị chặn.
- **Thông báo ngoài ứng dụng:** đã gỡ FCM/Cloud Functions; hiện chỉ có thông báo trong app và bộ đếm chưa đọc.
- Không có thanh toán trực tuyến, theo dõi vị trí liên tục hoặc xác minh danh tính.
- Các ngoại lệ chat, phục hồi, mất mạng và tranh chấp cần nghiệm thu theo cùng APK; ảnh giao diện không thay toàn bộ kết quả kiểm thử.

## Cấu trúc mã nguồn

```text
app/src/main/
├── java/com/example/grabshare/
│   ├── activity/       # Tài khoản, tìm chuyến, chủ xe, hành khách, chat, thông báo
│   ├── adapter/        # Hiển thị danh sách
│   ├── api/            # Tìm địa điểm và định tuyến
│   ├── contract/       # Hợp đồng dữ liệu, callback và xử lý dùng chung
│   ├── local/          # SQLite và bản xem chuyến theo UID
│   ├── model/          # Đối tượng dữ liệu
│   ├── notifications/  # Sự kiện thông báo trong ứng dụng
│   ├── presentation/   # Điều hướng và trạng thái giao diện dùng chung
│   └── repository/     # Truy cập dữ liệu và phối hợp nghiệp vụ
└── res/                # Layout, chuỗi, biểu tượng và tài nguyên XML

firestore.rules         # Security Rules được quản lý trong mã nguồn
firestore.indexes.json  # Composite index của dự án
```

Một số luồng dùng ViewModel giữ trạng thái qua thay đổi cấu hình; không phải mọi Activity đều đã tách theo cùng một mẫu kiến trúc.

## Phối hợp trong nhóm

| Thành viên | Phạm vi phân công |
| --- | --- |
| Khang — trưởng nhóm | Nền dùng chung, tài khoản/hồ sơ, điều hướng, hợp đồng dữ liệu, Rules và tích hợp. |
| Thành | Địa điểm, tìm/kiểm tuyến, chi tiết, khoản đóng góp và gửi yêu cầu. |
| Thuận | Hành khách, hủy/đánh giá/yêu thích và bản xem SQLite. |
| Thuần | Chủ xe, xử lý yêu cầu và thông báo trong ứng dụng. |

Đây là phân công chức năng; lịch sử commit/PR phản ánh cả các sửa tích hợp sau bàn giao.

- Xem nhiệm vụ/nhận xét hiện tại tại [web task của nhóm](https://grabshare-team-roadmap.snowy-frost-6677.chatgpt.site/).
- Làm trên nhánh cá nhân từ nền đã thống nhất, commit theo nhóm thay đổi và tạo PR để review trước khi merge.
- Java mới phải tường minh, không lambda, không viết tắt khó hiểu; comment tiếng Việt giải thích trách nhiệm hàm và xử lý quan trọng. Theo quy ước nhóm, không dùng `Handler` trong code mới.
- Không commit `local.properties`, thư mục build, tài khoản bí mật, prototype/ZIP không liên quan hoặc tệp kiểm thử vào project. Các bộ kiểm tra và tài liệu triển khai riêng của leader được bàn giao ngoài repository khi cần.

## Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra |
| --- | --- |
| Sync/build lỗi | SDK 37, Android Studio, Gradle Wrapper và toolchain Java 25; không nhầm với mức mã Java 11. |
| Google Sign-In thất bại | SHA-1 bản build, package, OAuth/Google provider, `google-services.json` và Logcat. |
| `PERMISSION_DENIED` | UID, quyền sở hữu, schema, trạng thái và Rules đã Publish; không mở `allow write: if true` để che lỗi. |
| Query thiếu index | Field/thứ tự index trong `firestore.indexes.json` và trạng thái Enabled trên Firebase. |
| Không tìm thấy chuyến | Giờ tương lai, khung giờ khách chọn, OPEN/1 chỗ, tọa độ và giới hạn đi thêm; phân biệt rỗng với lỗi API. |
| Nút gửi/nhận bị khóa | Hướng dẫn trên màn, mạng, phiên, hồ sơ, xác nhận khoản đóng góp, giờ đi, ghế và trạng thái mới nhất. |
| Không xem được bản lưu offline | Đã xem chuyến trực tuyến bằng đúng tài khoản hay chưa; đăng xuất xóa cache UID đó. |
| Máy ảo chậm/màn hình đen | Thiết bị đã khởi động/mở khóa, app đã cài và Logcat; thử điện thoại thật để phân biệt lỗi máy ảo với lỗi ứng dụng. |
