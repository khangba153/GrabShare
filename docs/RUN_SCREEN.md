# Chạy riêng từng màn — bước 3, v1.3

Đã build APK bằng :app:assembleDebug thành công (43 giây, 11/10/2026).
Chưa chạy emulator hoặc push main. Merged Manifest Debug chỉ có bộ chọn làm launcher.
Không coi việc xem XML là chức năng thật hoặc kết nối Firebase thành công.

## 1. Mở công cụ

1. Android Studio mở thư mục clone, Sync Gradle.
2. Build Variants chọn app = debug.
3. Cấu hình chạy app: Launch = Default Activity, không cố định MainActivity cũ.
4. Run mở ScreenLauncherActivity: chọn Tất cả/L/A/B/C.
5. Công tắc Activity thật mặc định tắt; chạm tên màn để mở đúng XML đó.
6. Back hệ thống quay về bộ chọn; trong preview vẫn vuốt trái/phải qua 27 XML.
7. Nút “Xem toàn bộ 27 XML” mở preview từ đầu, có chat tham khảo chưa triển khai.

Debug Manifest thay launcher bằng bộ chọn. MainActivity vẫn là preview và không
bị xóa. Release không có công cụ Debug và hiện vẫn mở preview; router app thật
là bước riêng của leader, bản Release chưa phải sản phẩm hoàn thiện.

## 2. Thành viên bắt đầu màn mình

1. Chọn một màn theo TASK_A/B/C.md, dùng tên/package trong HANDOFF_CONTRACT.
2. Tạo Activity trong app/src/main/java/com/example/grabshare/activity/<gói>.
3. Activity kế thừa AppCompatActivity, setContentView(screen_* của mình);
   không kế thừa GrabShareUi vì preview chặn thao tác chạm.
4. Sửa XML của mình: bỏ clickable=false/focusable=false/blockDescendants ở
   thành phần cần thao tác, nối View.OnClickListener tường minh khi làm chức năng.
   Không gỡ toàn bộ chặn khỏi preview để giả app đã có flow.
5. Gửi leader một khai báo Activity để đăng ký ở Manifest. Trên nhánh riêng,
   có thể thêm đúng dòng cần thiết theo thỏa thuận leader; không sửa launcher.
6. Bật “Mở Activity thật”, nhập ID cần thiết, chạm đúng tên màn.
7. Nếu báo thiếu Activity/Manifest: kiểm tra package/tên và khai báo, không
   tạo class giả chỉ để công cụ hết báo thiếu.
8. Tự kiểm tra UI/Back/xoay/form rồi nối đọc dữ liệu sau khi có auth.

Ví dụ Activity màn Home A (chỉ gắn XML, chưa có chức năng):

```java
package com.example.grabshare.activity.search;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.grabshare.R;

public class HomeActivity extends AppCompatActivity {
    /** Gắn giao diện màn tìm chuyến của gói A. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_home);
    }
}
```

Khai báo trong application của app/src/main/AndroidManifest.xml khi class đã có:

```xml
<activity android:name=".activity.search.HomeActivity"
    android:exported="false" android:windowSoftInputMode="adjustResize" />
```

Không khai báo cả 26 Activity chưa có sẵn vì đó không phải bàn giao thực tế.
Manifest chung leader review; gói khác chỉ cần đề xuất dòng đúng tên class mình.

## 3. ID và dữ liệu

- Detail/Review/EditTrip/Manage/DriverRequests: trip_id.
- Pending/Accepted/Rejected/Upcoming/Complete/RequestDetail: request_id.
- ID lấy đúng từ Firestore, ví dụ trip seed_trip_paid. request ID có UID thật;
  xem Console/scripts/seed.config.json, không tự đoán hoặc hardcode một user vào repo.
- Home/Login/Post/Account/list không bắt buộc hai ID này.
- Bộ chọn truyền cả ô có nội dung; Activity chỉ đọc extra thuộc hợp đồng mình.
- Không có ID cần thiết thì Activity báo thiếu và cho Back, không lấy dữ liệu giả.
- Bộ chọn không tự Auth, seed, tạo request, hoàn thành chuyến hay mở Rules.
- Chưa có auth: vẫn làm UI nhưng chưa kiểm chứng đọc Firestore. ID không thay quyền.

## 4. File chung thuộc leader

- app/src/debug/AndroidManifest.xml.
- app/src/debug/java/com/example/grabshare/dev/ScreenLauncherActivity.java.
- app/src/debug/java/com/example/grabshare/dev/ScreenCatalog.java.
- app/src/debug/res/layout/activity_screen_launcher.xml.
- app/src/debug/res/values/dev_strings.xml.
- GrabShareUi.java: đọc preview_screen để mở đúng XML, vẫn giữ chế độ preview.
- MainActivity.java/UiScreen.java: comment Việt; preview đã thay lambda cũ.

Catalog có 26 màn thuộc gói, không có chat scope phụ. Activity được tìm theo
Manifest, chưa có thì báo thiếu. Catalog không gọi reflection hoặc sinh Activity.
Tên class đã chốt; nếu đổi phải gửi leader sửa catalog/context cùng lúc.

## 5. Checklist tự kiểm tra (nhóm trưởng báo test bộ chọn đạt; không xác nhận toàn bộ checklist)

- Debug Run mở bộ chọn, Release không có ScreenLauncherActivity.
- L/A/B/C hiện đúng màn và chạm mở đúng XML.
- Preview chọn màn rồi xoay vẫn giữ đúng trang.
- Bộ chọn xoay vẫn giữ gói/chế độ/ID; landscape/bàn phím có thể cuộn nội dung.
- Activity thật chưa có → hiện thông báo rõ, không mở nhầm preview.
- Activity đã tạo/đăng ký mở trực tiếp với extra đúng, Back về bộ chọn.
- Chưa auth/Rules ghi không bị bỏ qua bằng công cụ Debug.

## 6. Bước sau

Build đã đạt; nhóm trưởng báo đã test bộ chọn màn và đạt. Base đã push main
tại commit 02af6bd; thành viên làm theo docs/START_HERE.md để pull và chọn nhánh.
Auth/Rules ghi/index vẫn là mốc 2 để nối chức năng thật.
