# PROJECT_CONTEXT.md — GrabShare v1.4 / 11-10-2026

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
6 khai báo contract đã có trên main: NavigationKeys, LocationKeys, RepositoryCallback,
FavoriteStore, TripCacheStore, CachedTrip. Base Android đã push main tại commit 02af6bd.
Debug có ScreenLauncherActivity chọn L/A/B/C, mở XML hoặc Activity thật đã có
trong Manifest; có ô trip_id/request_id. Không auto auth/ghi hoặc bỏ qua Rules.
Release vẫn preview; router app thật chưa có. Hướng dẫn chạy chi tiết bên dưới.
Firebase project grabshare, package com.example.grabshare, database (default).
Config/dependencies đã chuẩn bị; seed 27 docs đã ghi theo báo cáo leader.
Rules hiện chỉ đọc: trips/publicProfiles/ratings khi đăng nhập; users chính chủ get;
requests driver/passenger liên quan; favorites owner; notifications recipient.
Playground kiểm tra trip/auth và users đúng chủ; chưa xác nhận Android đọc thật.
Model/contract/công cụ Debug đã compile :app:assembleDebug thành công; leader
báo đã test bộ chọn màn và đạt. Chưa xác nhận đọc/ghi Firebase Android; chưa chạy tests. Chưa repository/Activity nghiệp vụ/SQLite/API/index/Rules ghi.

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
com.example.grabshare.contract đã compile Debug và push main tại 02af6bd; repository và Activity
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
- Còn thiếu: Activity thật, implementation B, auth, Rules ghi/index, router nghiệp vụ. Base đã push main 02af6bd; nhóm trưởng báo test bộ chọn đạt.

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
