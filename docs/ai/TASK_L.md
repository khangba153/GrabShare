# PROJECT_CONTEXT.md — GrabShare v1.3 / 11-10-2026

## Mục tiêu
Đồ án phát triển mobile, app cộng đồng chia sẻ chuyến xe máy, không riêng sinh viên.
4 thành viên ngang kiến thức, có AI hỗ trợ, 2h/ngày/người, hạn 25/10/2026.
Leader giữ base/tài khoản, Thành/Thuận/Thuần tự chọn A/B/C. Chưa gán tên gói.

## Bắt buộc và scope
Android Java + XML; không lambda, không Handler; comment tiếng Việt trên hàm.
Firestore thật; responsive xoay màn; SQLite chỉ đọc lại trang đã xem; API hỗ trợ
Google sign-in và OSM/Nominatim cơ bản. Xác minh tài liệu/chính sách chính thức
trước triển khai API. Không VNeID/payment/live tracking/upload/chat trong scope.
Mỗi trip tối đa 1 chỗ đi nhờ, mỗi user có thể vừa driver vừa passenger.

## Hiện trạng — đừng giả đã có chức năng
Repo https://github.com/khangba153/GrabShare.git; mỗi máy dùng đường dẫn clone riêng.
27 XML + preview swipe chặn chạm. 8 model trong com.example.grabshare.model.
6 khai báo contract đã tạo local: NavigationKeys, LocationKeys, RepositoryCallback,
FavoriteStore, TripCacheStore, CachedTrip. Chưa push base Android.
Debug có ScreenLauncherActivity chọn L/A/B/C, mở XML hoặc Activity thật đã có
trong Manifest; có ô trip_id/request_id. Không auto auth/ghi hoặc bỏ qua Rules.
Release vẫn preview; router app thật chưa có. Hướng dẫn chạy chi tiết bên dưới.
Firebase project grabshare, package com.example.grabshare, database (default).
Config/dependencies đã chuẩn bị; seed 27 docs đã ghi theo báo cáo leader.
Rules hiện chỉ đọc: trips/publicProfiles/ratings khi đăng nhập; users chính chủ get;
requests driver/passenger liên quan; favorites owner; notifications recipient.
Playground kiểm tra trip/auth và users đúng chủ; chưa xác nhận Android đọc thật.
Model/contract/công cụ Debug đã compile :app:assembleDebug thành công; chưa Run
emulator hoặc tests. Chưa repository/Activity nghiệp vụ/SQLite/API/index/Rules ghi.

## Ranh giới AI
Trước khi sửa: đọc repo/AGENTS nếu có, git status/branch và code hiện tại, đối chiếu
data contract. Chỉ làm gói được giao; không tự làm toàn dự án. Nếu file chung hoặc
contract cần đổi, mô tả yêu cầu cho leader trước, không tự mở Rules hay đổi schema.
Không tạo project Firebase riêng, không seed/reset chung, không log secret.
Không commit mật khẩu/service account/local.properties. JSON lấy từ base leader.
Đừng kế thừa touch blocker của preview để làm màn thật; bỏ chặn XML gói mình.
Không thêm tests/chạy tests nếu người dùng chưa yêu cầu; đề xuất checklist tự kiểm tra.
Không tự commit/push nếu chưa được thành viên giao quyền; khi được giao, giữ author
thật của người làm, không đổi lịch sử. Không bịa kết quả build/demo/đạo văn/hoàn thành.

## Dữ liệu và nguyên tắc
ID document ở model getId @Exclude, không lưu field id. Timestamp cho thời gian;
Long cho số nguyên tiền/chỗ/điểm; Double tọa độ; Boolean isRead (getIsRead/setIsRead).
Place gồm name/latitude/longitude, map lồng. Optional string = "". Không sao fake.
createdAt giữ khi update, updatedAt server timestamp; departureAt do người chọn.
Qua màn bằng trip_id/request_id, giữ ID/form qua xoay. Không truyền model Serializable.
Rules không lọc query: where passengerId/driverId/userId/recipientId theo UID.

## State contract
Trip OPEN/1/acceptedRequestId="" → accept tx → FULL/0/pointer request.
Request PENDING → ACCEPTED/REJECTED/CANCELLED; không resend sau reject/cancel.
Hủy accepted trước giờ: tx request CANCELLED + trip OPEN/1/pointer rỗng.
Hủy pending không đổi chỗ. Driver cancel trip trước giờ: CANCELLED/0 giữ lịch sử
request/pointer, UI ưu tiên trip CANCELLED. Driver complete FULL sau giờ → COMPLETED.
Không auto complete OPEN quá giờ. Không edit tuyến/giờ/tiền FULL.
Rating chỉ accepted passenger sau COMPLETED, một lần, score 1–5 cho driver.
Nhận/hủy cạnh tranh đọc lại trip/request trong transaction, mọi đọc trước ghi,
Rules kiểm tra state nguyên tử. Không UI/side effect trong callback transaction.
Thông báo cùng transaction sự kiện, không ghi inbox tùy ý. Offline chặn tất cả ghi.

## Giới hạn tìm và offline
Tìm OPEN trong ngày, departureAt >= thời gian yêu cầu/hiện tại, ASC, limit 50;
lọc khoảng cách hai đầu trong Java, không hứa khớp toàn tuyến. Bán kính chưa chốt.
SQLite B theo userId/tripId/savedAt, Trip + public profile; A lưu khi đọc Detail online,
L clear cache khi logout; không cache users/token, không offline mutation queue.

## Bàn giao và chấm điểm
40% commit/branch/PR/merge author thật; 40% PDF; 20% slide/demo/hỏi đáp.
PDF lý thuyết 1–2 trang, trọng tâm thiết kế/chức năng/kỹ thuật, ít code, max60±5 trang,
UEH mặc định <20%. Trình bày 5 phút, hỏi đáp theo thời lượng giảng viên.
Mỗi người viết phần báo cáo + ảnh/link PR, độ hoàn thành trung thực.
Sau mỗi bước AI báo: file đã sửa, flow đã nối, phần phụ thuộc, chưa làm, cách tự kiểm tra.

# Hợp đồng bàn giao giữa các gói — chốt bước 2, v1.2

Nhóm trưởng đã đồng ý; A/B/C tự chọn, không cần nhập tên. Các khai báo ở package
com.example.grabshare.contract đã tạo local và compile Debug thành công, chưa push; repository và Activity
nghiệp vụ chưa được triển khai. Hợp đồng này không mở quyền ghi Firestore.

## 1. Mở màn theo ID

| Đích | Extra bắt buộc | Chủ file |
|---|---|---|
| activity.search.TripDetailActivity | NavigationKeys.TRIP_ID | A |
| activity.search.RequestReviewActivity | NavigationKeys.TRIP_ID | A |
| activity.passenger.PendingActivity | NavigationKeys.REQUEST_ID | B |
| activity.passenger.AcceptedActivity / RejectedActivity / UpcomingActivity | NavigationKeys.REQUEST_ID | B |
| activity.passenger.CompleteActivity | NavigationKeys.REQUEST_ID | B |
| activity.driver.EditTripActivity / ManageTripActivity / DriverRequestsActivity | NavigationKeys.TRIP_ID | C |
| activity.driver.RequestDetailActivity | NavigationKeys.REQUEST_ID | C |

Complete cũng dùng request_id để tìm trip qua request, không cần hai ID tùy ý.
Màn nhận kiểm tra ID thiếu/rỗng, đọc lại DB theo quyền; báo lỗi rồi cho Back.
MainActivity không thay đổi trong bước này. Leader đăng ký Activity/router ở bước 3.
Không truyền model Serializable/Parcelable hoặc chỉ truyền tên/giá thay dữ liệu thật.

## 2. LocationActivity A → form A/C

Chủ file: activity/search/LocationActivity.java. Caller dùng Activity Result API
với ActivityResultContracts.StartActivityForResult. Đăng ký callback mỗi lần tạo
Activity theo cùng thứ tự, trước STARTED. Không dùng onActivityResult cũ.

Đầu vào: Intent mở LocationActivity; caller lưu ô đang chọn trong state
(origin/destination/pickup) và phục hồi khi xoay; không cần thêm extra để đoán caller.
Người dùng chọn mục: LocationActivity setResult(RESULT_OK, intent) rồi finish.

| Extra đầu ra | Hằng số | Kiểu |
|---|---|---|
| place_name | LocationKeys.PLACE_NAME | String, có nội dung |
| place_latitude | LocationKeys.PLACE_LATITUDE | double, -90 đến 90 |
| place_longitude | LocationKeys.PLACE_LONGITUDE | double, -180 đến 180 |

Caller phải kiểm tra RESULT_OK, Intent khác null và hasExtra cho cả ba field
trước đọc; kiểm tra finite/range, không mặc định (0,0) khi kết quả thiếu.
Tạo Place và cập nhật đúng ô đã lưu; không đổi form nếu Cancel/Back/kết quả sai.
Màn gọi và Location giữ form/query khi xoay, không auto gọi API hoặc gửi nghiệp vụ lại.
Post/Edit của C dùng cùng Location; A không tự ghi trip/request trong màn chọn.

## 3. FavoriteStore — B triển khai, A gọi

B tạo repository/passenger/FavoriteRepository.java implements FavoriteStore.
Constructor công khai nhận FirebaseFirestore để A khởi tạo từ instance dùng chung:
FavoriteRepository(FirebaseFirestore firestore). Không hardcode user demo.

```java
void isFavorite(String userId, String tripId, RepositoryCallback<Boolean> callback);
void addFavorite(String userId, String tripId, RepositoryCallback<Void> callback);
void removeFavorite(String userId, String tripId, RepositoryCallback<Void> callback);
```

- isFavorite trả true/false khác null sau khi đọc; lỗi không tự coi là false.
- add/remove onSuccess(null) sau server xác nhận, onError(exception) khi thất bại.
- Callback chạy trên UI thread, dùng anonymous class Java, không lambda/Handler.
- Mỗi lời gọi chỉ một kết quả cuối. Nếu màn đã dừng thì caller không điều hướng mới.
- B kiểm tra userId khớp Auth hiện tại, ID không rỗng; document là userId_tripId.
- Thêm idempotent không tạo bản trùng; không reset createdAt của favorite đã có.
- Caller chặn ghi khi offline, khóa nút lúc chờ. B xử lý lỗi quyền/mạng thực tế,
  không dùng bản ghi chỉ đang chờ gửi để thông báo server đã thành công.
- A chỉ gọi; không tự tạo FavoriteRepository thứ hai hoặc tự ghi collection.

## 4. TripCacheStore — B triển khai, A và leader gọi

B tạo local/TripCacheRepository.java implements TripCacheStore, constructor
TripCacheRepository(Context context), dùng applicationContext; helper SQLite do B sở hữu.

```java
void saveViewedTrip(String userId, Trip trip, PublicProfile profile);
CachedTrip getViewedTrip(String userId, String tripId);
void clearUserCache(String userId);
```

Đây là hàm đồng bộ: caller chạy bằng ExecutorService ngoài UI thread. Nếu cần
cập nhật UI, dùng Activity.runOnUiThread với Runnable tường minh. Không Handler.
SQLite lỗi có thể phát RuntimeException; caller bắt lỗi và hiển thị phù hợp,
không báo cache đã lưu khi lỗi. get trả null chỉ khi không có dữ liệu.

- Cache theo userId + tripId; trip.getId() phải có, userId đúng tài khoản hiện tại.
- savedAt là mili giây Unix tại lúc lưu (giờ thiết bị), không phải server timestamp.
- CachedTrip gồm Trip, PublicProfile (có thể null), savedAt; không collection mới.
- B phục hồi trip.id từ khóa, profile.id từ trip.driverId nếu có profile.
- Profile thiếu: vẫn lưu trip, UI có placeholder; không dùng tên demo thay dữ liệu.
- A chỉ lưu sau đọc online thành công, đọc cache khi offline, hiện savedAt/nhãn.
- Tên xe/tên công khai có thể lưu; không users/email/biển số riêng/token/mật khẩu.
- Xóa cache đúng tài khoản khi logout; leader phối hợp ngừng tác vụ cache đang
  chạy, đợi/xếp clear sau save trước khi cho phiên khác đọc. Không xóa cache rồi
  để callback phiên cũ ghi lại. UI callback kiểm tra phiên/lifecycle hiện tại.
- SQLite không queue thao tác Firestore; offline chặn mọi mutation.

## 5. A → B: request mới; C ↔ B: state

A commit PENDING và REQUEST_SENT thành công → PendingActivity bằng request_id.
B đọc request/trip; C cập nhật ACCEPTED/REJECTED/COMPLETED/CANCELLED trên Firestore.
B không gọi trực tiếp UI C, C không gọi trực tiếp UI B. Listener sống theo màn.
B hủy ACCEPTED nguyên tử với trip OPEN/1/pointer rỗng; C đọc lại trước nhận người khác.
Hủy trip giữ requests lịch sử; B ưu tiên trip CANCELLED. Rules ghi cần kiểm tra
state/cross-document trong cùng transaction, không chỉ kiểm tra UID.

## 6. Kiểm tra bàn giao (chưa thực hiện)

- A/C dùng cùng LocationKeys và xử lý Cancel không đổi ô.
- A/B không có Favorite/SQLite API trùng tên khác chữ ký.
- Một request_id mở đúng màn B; Complete truy ra trip từ request.
- Mọi XML/function thuộc owner; thay Manifest/router qua leader.
- Khai báo hợp đồng không có dữ liệu giả, không phải chức năng đã chạy.
- Còn thiếu: Activity thật, implementation B, auth, Rules ghi/index, router, Run thực tế.

Nguồn Activity Result API: https://developer.android.com/training/basics/intents/result

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

## 5. Checklist tự kiểm tra (chưa thực hiện)

- Debug Run mở bộ chọn, Release không có ScreenLauncherActivity.
- L/A/B/C hiện đúng màn và chạm mở đúng XML.
- Preview chọn màn rồi xoay vẫn giữ đúng trang.
- Bộ chọn xoay vẫn giữ gói/chế độ/ID; landscape/bàn phím có thể cuộn nội dung.
- Activity thật chưa có → hiện thông báo rõ, không mở nhầm preview.
- Activity đã tạo/đăng ký mở trực tiếp với extra đúng, Back về bộ chọn.
- Chưa auth/Rules ghi không bị bỏ qua bằng công cụ Debug.

## 6. Bước sau

Build đã đạt; nhóm trưởng cần xác nhận Run thực tế trước bàn giao, rồi bước 4
chọn file base commit/push main. Thành viên chưa nhận được các file này qua pull.
Auth/Rules ghi/index vẫn là mốc 2 để nối chức năng thật.

## Nhiệm vụ được giao: Nhóm trưởng — Base & tài khoản
Nhánh đề xuất: main / codex/base
XML sở hữu: screen_login.xml, screen_register.xml, screen_reset_password.xml, screen_account.xml, screen_edit_profile.xml, screen_help.xml, screen_terms.xml
Java cần tạo: LoginActivity, RegisterActivity, ResetPasswordActivity, AccountActivity, EditProfileActivity, HelpActivity, TermsActivity, AuthRepository, ProfileRepository, MainActivity.java, GrabShareUi.java, UiScreen.java, app/src/main/AndroidManifest.xml, build.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml, app/google-services.json, firestore.rules, firestore.indexes.json (cần tạo), model/*.java, docs/*

## Schema model dùng chung
- AppNotification / notifications: recipientId (String), actorId (String), type (String), tripId (String), requestId (String), isRead (Boolean), createdAt (Timestamp)
- Favorite / favorites: userId (String), tripId (String), createdAt (Timestamp)
- Place / map lồng: name (String), latitude (Double), longitude (Double)
- PublicProfile / publicProfiles: displayName (String), vehicleName (String), createdAt (Timestamp), updatedAt (Timestamp)
- Rating / ratings: tripId (String), authorId (String), targetUserId (String), score (Long), comment (String), createdAt (Timestamp)
- Trip / trips: driverId (String), origin (Place), destination (Place), departureAt (Timestamp), costVnd (Long), note (String), status (String), availableSeats (Long), acceptedRequestId (String), createdAt (Timestamp), updatedAt (Timestamp)
- TripRequest / requests: tripId (String), passengerId (String), driverId (String), pickup (Place), message (String), status (String), createdAt (Timestamp), updatedAt (Timestamp)
- User / users: email (String), vehiclePlate (String), createdAt (Timestamp), updatedAt (Timestamp)

### L0 Bàn giao base & router
Điểm vào/ra: Leader làm trước; thành viên cần base để bắt đầu.
XML: activity_main.xml
Java: MainActivity.java, GrabShareUi.java, UiScreen.java, AndroidManifest.xml
Flow:
1. Kiểm tra branch, JSON, Gradle, 8 model và dữ liệu seed hiện tại.
2. Chốt hợp đồng Intent/query/API giao nhau; mở Rules ghi theo từng thao tác và tạo index cần thiết.
3. Đăng ký Activity của các gói; launcher kiểm tra Auth rồi mở Login hoặc Home.
4. Nối tab Home / MyTrips / Account; Account → DriverTrips / Notifications / Help / Terms.
5. Giữ preview riêng; Activity thật không dùng cơ chế chặn chạm của GrabShareUi.
Đọc:
- Firebase Auth currentUser
- Hợp đồng dữ liệu và query của A/B/C
Ghi:
- Rules/index/cấu hình dùng chung; chưa tạo nghiệp vụ thay thành viên
Phụ thuộc:
- A/B/C gửi tên Activity, extras và nhu cầu index
- L1 bàn giao đăng nhập
Điều kiện đạt:
- Sync được từ clone mới
- 8 model không đổi field
- Mỗi nút có đích thật; không để chat giả trong demo
- Ứng dụng thật không còn lambda/Handler
- Có commit mốc base và hướng dẫn khởi động
Lỗi/ngoại lệ:
- Không thay project theo nhánh
- Không mở allow write:true để né lỗi
- Không claim Android đã nối trước khi đọc thật

### L1 Đăng nhập email & Google
Điểm vào/ra: Launcher hoặc sau đăng xuất; thành công → Home A.
XML: screen_login.xml
Java: LoginActivity.java, AuthRepository.java
Flow:
1. Mở Login khi chưa có phiên; email/password nhập hợp lệ thì bật gửi.
2. Email/password gọi Firebase Auth; Google lấy credential từ luồng đăng nhập được cấu hình.
3. Đăng nhập thành công kiểm tra hồ sơ users/publicProfiles; thiếu hồ sơ đưa đến hoàn thiện hồ sơ.
4. Có hồ sơ thì mở Home và kết thúc Login; lỗi thì giữ form, bỏ loading.
Đọc:
- Auth
- users/{uid}
- publicProfiles/{uid}
Ghi:
- Phiên Authentication; không tự sửa trip/request
Phụ thuộc:
- L0 config
- L2/L4 hoàn thiện hồ sơ
- Google provider + SHA máy + cấu hình theo tài liệu chính thức
Điều kiện đạt:
- Email/password thật đăng nhập được
- Google hoạt động thực tế trước khi tính đạt API
- Sai mật khẩu báo lỗi dễ hiểu
- Xoay không gửi lại; form không mất
- Không log mật khẩu/token
Lỗi/ngoại lệ:
- Mạng lỗi → hiện thử lại
- Hồ sơ thiếu không giả tên/xe
- Không commit OAuth secret

### L2 Đăng ký & tạo hồ sơ
Điểm vào/ra: Login → Đăng ký → Home hoặc hoàn thiện hồ sơ.
XML: screen_register.xml
Java: RegisterActivity.java, AuthRepository.java, ProfileRepository.java
Flow:
1. Nhập tên, email, mật khẩu, xác nhận; kiểm tra form.
2. Tạo tài khoản Auth; lấy UID thật làm document ID.
3. Batch tạo users và publicProfiles theo schema, createdAt/updatedAt server timestamp.
4. Nếu batch lỗi sau Auth thành công, giữ phiên và cho hoàn thiện lại hồ sơ; không tạo Auth lần nữa.
5. Thành công mở Home; Back khi chưa tạo về Login.
Đọc:
- Auth
- users/{uid}/publicProfiles/{uid} khi cần phục hồi
Ghi:
- Auth account
- users/{uid}
- publicProfiles/{uid}
Phụ thuộc:
- Leader mở Rules profile create
- L4 profile recovery
- L0 router
Điều kiện đạt:
- Hai hồ sơ có đúng UID, không field id
- Email riêng không lộ trong publicProfiles
- Tạo hồ sơ cùng batch
- Có phục hồi sau lỗi ghi
- Chống bấm tạo liên tiếp
Lỗi/ngoại lệ:
- Email đã dùng → hướng về Login/reset
- Auth thành công nhưng batch lỗi không che lỗi
- Không tự xóa tài khoản để xử lý lỗi

### L3 Quên mật khẩu
Điểm vào/ra: Login → Quên mật khẩu → Login.
XML: screen_reset_password.xml
Java: ResetPasswordActivity.java, AuthRepository.java
Flow:
1. Login mở màn yêu cầu email đặt lại mật khẩu.
2. Kiểm tra email, gọi sendPasswordResetEmail.
3. Hiển thị hướng dẫn kiểm tra email; Back về Login.
Đọc:
- Auth provider email/password
Ghi:
- Gửi email reset qua Auth; không ghi mật khẩu Firestore
Phụ thuộc:
- Email/password provider
- L0 router
Điều kiện đạt:
- Nút gửi có loading/lỗi/thành công
- Không tự đặt mật khẩu mới trong màn chưa xác thực
- Xoay giữ email
- Thông báo không phơi bày thông tin tài khoản
Lỗi/ngoại lệ:
- Email sai định dạng
- Lỗi mạng/quota → thử lại
- Không giả email đã gửi nếu API thất bại

### L4 Tài khoản & sửa hồ sơ
Điểm vào/ra: Tab Account → Edit → Account; logout → Login.
XML: screen_account.xml / screen_edit_profile.xml
Java: AccountActivity.java, EditProfileActivity.java, ProfileRepository.java
Flow:
1. Account đọc users chính chủ và publicProfiles theo UID.
2. Mở Edit, nhập tên/xe/biển số; không tự đổi UID/email Auth.
3. Batch cập nhật hai hồ sơ, giữ createdAt và cập nhật updatedAt.
4. Lưu xong quay Account và nạp lại; thiếu hồ sơ sau Auth hỗ trợ tạo hồ sơ ban đầu.
5. Menu mở DriverTrips C, Notifications C, Help/Terms; logout gọi B clearUserCache rồi Auth signOut và về Login.
Đọc:
- users/{uid}
- publicProfiles/{uid}
Ghi:
- Hai hồ sơ chính chủ; không ghi hồ sơ người khác
Phụ thuộc:
- Rules create/update chính chủ
- B clearUserCache
- C DriverTrips/Notifications
- L0 router
Điều kiện đạt:
- Tên/xe/biển số đúng và không mất sau mở lại
- Email/biển số không đưa vào public profile
- Xoay Edit giữ form
- Logout xóa cache tài khoản và back không vào màn riêng
- Không upload avatar trong scope
Lỗi/ngoại lệ:
- Không có xe vẫn được tìm/đi nhờ
- Đăng chuyến yêu cầu hoàn thiện xe
- Lỗi ghi giữ form và không báo thành công

### L5 Trợ giúp & điều khoản
Điểm vào/ra: Account → Help/Terms → Back.
XML: screen_help.xml / screen_terms.xml
Java: HelpActivity.java, TermsActivity.java
Flow:
1. Account mở Help hoặc Terms.
2. Hiển thị nội dung cộng đồng, chia sẻ chi phí, trách nhiệm đúng giờ và cách dùng.
3. Back về màn mở; xoay và cuộn đọc bình thường.
Đọc:
- Nội dung tĩnh trong resource
Ghi:
- Không ghi Firestore
Phụ thuộc:
- L0 menu
Điều kiện đạt:
- Nội dung không giới hạn sinh viên
- Không hứa xác minh VNeID/bảo hiểm/chức năng chưa có
- Back và cuộn hoạt động
- Xoay không cắt nội dung
Lỗi/ngoại lệ:
- Không để link/nút không có tác dụng trong demo

## Prompt khởi động
Hãy đọc ngữ cảnh trên và kiểm tra repo hiện tại. Lập thứ tự làm các chức năng gói này theo phụ thuộc. Bắt đầu từ phần không bị chặn, không viết thay gói khác. Nếu thiếu base/Rules/interface, nêu rõ đầu vào cần leader bàn giao. Mỗi lần chỉ hoàn thành một bước có thể review.
