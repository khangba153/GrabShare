#!/usr/bin/env python3
"""Tạo dữ liệu demo GrabShare bằng Firestore REST; mặc định chỉ xem trước."""

import argparse
import json
import shutil
import subprocess
import sys
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SAIGON = timezone(timedelta(hours=7))
PROJECT = "grabshare"
PROJECT_NUMBER = "334276006780"
DATABASE = "(default)"
API = "https://firestore.googleapis.com/v1"
RESOURCE = "projects/" + PROJECT + "/databases/" + DATABASE + "/documents"


def read_config():
    """Đọc cấu hình cùng thư mục và kiểm tra project, UID, ID chuyến."""
    path = Path(__file__).with_name("seed.config.json")
    config = json.loads(path.read_text(encoding="utf-8"))
    if (config["projectId"], str(config["projectNumber"]), config["databaseId"]) != (
            PROJECT, PROJECT_NUMBER, DATABASE):
        raise ValueError("Cấu hình không khớp project GrabShare đã chốt.")
    accounts = config["accounts"]
    if set(accounts) != {"D1", "P1", "P2"}:
        raise ValueError("Cần đúng ba tài khoản D1/P1/P2.")
    ids = [account["uid"] for account in accounts.values()]
    if len(set(ids)) != 3 or any(not uid or "/" in uid for uid in ids):
        raise ValueError("Ba UID phải khác nhau và không chứa dấu /.")
    trip_ids = [trip["id"] for trip in config["trips"]]
    if len(set(trip_ids)) != 7 or any(not value.startswith("seed_trip_") or "/" in value
                                    for value in trip_ids):
        raise ValueError("Chỉ cho phép bảy chuyến seed có ID riêng.")
    return config


def build_documents(config, clock, demo_date, emails=None):
    """Tạo bộ document liên kết bằng UID thật, dùng ngày mai cho chuyến tương lai."""
    accounts = config["accounts"]
    driver = accounts["D1"]["uid"]
    created = datetime.combine(clock.date() - timedelta(days=2), time(9), SAIGON)
    documents = {}

    def uid(role):
        """Tra UID theo ký hiệu demo."""
        return accounts[role]["uid"]

    def request_id(trip_id, role):
        """Tạo ID cố định chống trùng yêu cầu."""
        return trip_id + "_" + uid(role)

    for role, account in accounts.items():
        user_id = uid(role)
        email = emails[user_id] if emails else "<EMAIL_FROM_FIREBASE_AUTH>"
        documents["users/" + user_id] = {
            "email": email, "vehiclePlate": account["vehiclePlate"],
            "createdAt": created, "updatedAt": clock,
        }
        documents["publicProfiles/" + user_id] = {
            "displayName": account["displayName"], "vehicleName": account["vehicleName"],
            "createdAt": created, "updatedAt": clock,
        }

    for trip in config["trips"]:
        hour, minute = map(int, trip["time"].split(":"))
        trip_date = clock.date() - timedelta(days=1) if trip["status"] == "COMPLETED" else demo_date
        departure = datetime.combine(trip_date, time(hour, minute), SAIGON)
        passenger = trip.get("acceptedPassenger")
        accepted = request_id(trip["id"], passenger) if passenger else ""
        documents["trips/" + trip["id"]] = {
            "driverId": driver, "origin": config["origin"], "destination": config["destination"],
            "departureAt": departure, "costVnd": trip["costVnd"], "note": trip["note"],
            "status": trip["status"], "availableSeats": 1 if trip["status"] == "OPEN" else 0,
            "acceptedRequestId": accepted, "createdAt": created, "updatedAt": clock,
        }

    for entry in config["requests"]:
        documents["requests/" + request_id(entry["tripId"], entry["passenger"])] = {
            "tripId": entry["tripId"], "passengerId": uid(entry["passenger"]),
            "driverId": driver, "pickup": config["origin"],
            "message": "Mình sẽ có mặt trước 5 phút.", "status": entry["status"],
            "createdAt": created + timedelta(hours=1), "updatedAt": clock,
        }
    for entry in config["favorites"]:
        user_id = uid(entry["user"])
        documents["favorites/" + user_id + "_" + entry["tripId"]] = {
            "userId": user_id, "tripId": entry["tripId"], "createdAt": created,
        }
    for entry in config["ratings"]:
        author = uid(entry["author"])
        documents["ratings/" + entry["tripId"] + "_" + author] = {
            "tripId": entry["tripId"], "authorId": author, "targetUserId": uid(entry["target"]),
            "score": entry["score"], "comment": entry["comment"], "createdAt": clock,
        }
    for entry in config["notifications"]:
        req_id = request_id(entry["tripId"], entry["passenger"])
        key = (entry["tripId"] + "_" + uid(entry["recipient"]) + "_" + entry["type"]
               if entry["type"].startswith("TRIP_") else req_id + "_" + entry["type"])
        documents["notifications/" + key] = {
            "recipientId": uid(entry["recipient"]), "actorId": uid(entry["actor"]),
            "type": entry["type"], "tripId": entry["tripId"], "requestId": req_id,
            "isRead": entry["isRead"], "createdAt": clock,
        }
    validate_documents(documents)
    return documents


def validate_documents(documents):
    """Chặn dữ liệu seed có quan hệ hoặc số chỗ không nhất quán."""
    if len(documents) != 27:
        raise ValueError("Bộ seed phải có đúng 27 document.")
    for path, data in documents.items():
        collection = path.split("/")[0]
        if collection == "trips":
            if data["status"] not in {"OPEN", "FULL", "COMPLETED", "CANCELLED"}:
                raise ValueError("Trạng thái chuyến không hợp lệ.")
            if data["costVnd"] < 0:
                raise ValueError("Chi phí không được âm.")
            if data["acceptedRequestId"]:
                request = documents["requests/" + data["acceptedRequestId"]]
                if request["tripId"] != path.split("/")[1] or request["status"] != "ACCEPTED":
                    raise ValueError("Yêu cầu được nhận không khớp chuyến.")
            if data["status"] in {"FULL", "COMPLETED"} and not data["acceptedRequestId"]:
                raise ValueError("Chuyến FULL/COMPLETED phải có người được nhận.")
        if collection == "requests":
            trip = documents["trips/" + data["tripId"]]
            if data["driverId"] != trip["driverId"] or data["passengerId"] == trip["driverId"]:
                raise ValueError("Người gửi/chủ xe của yêu cầu không đúng.")
        if collection == "ratings":
            trip = documents["trips/" + data["tripId"]]
            req = documents["requests/" + trip["acceptedRequestId"]]
            if (trip["status"] != "COMPLETED" or req["passengerId"] != data["authorId"]
                    or not 1 <= data["score"] <= 5 or data["targetUserId"] != trip["driverId"]):
                raise ValueError("Đánh giá chưa đủ điều kiện.")


def encode_value(value):
    """Đổi kiểu Python thành kiểu field của Firestore REST."""
    if isinstance(value, bool):
        return {"booleanValue": value}
    if isinstance(value, int):
        return {"integerValue": str(value)}
    if isinstance(value, float):
        return {"doubleValue": value}
    if isinstance(value, str):
        return {"stringValue": value}
    if isinstance(value, datetime):
        return {"timestampValue": value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")}
    if isinstance(value, dict):
        return {"mapValue": {"fields": {key: encode_value(item) for key, item in value.items()}}}
    raise ValueError("Kiểu dữ liệu seed chưa được hỗ trợ.")


def printable(value):
    """Chuyển thời gian thành chuỗi có múi giờ để xem trước."""
    if isinstance(value, datetime):
        return value.isoformat()
    raise TypeError("Không thể xuất dữ liệu.")


def access_token():
    """Lấy token từ gcloud đã đăng nhập; không in hoặc lưu token."""
    executable = shutil.which("gcloud") or shutil.which("gcloud.cmd")
    if not executable:
        raise RuntimeError("Chưa có gcloud. Hãy chạy trên Google Cloud Shell theo README.")
    result = subprocess.run([executable, "auth", "print-access-token", "--quiet"],
                            capture_output=True, text=True, timeout=45)
    if result.returncode != 0 or not result.stdout.strip():
        raise RuntimeError("Không lấy được token. Đăng nhập/Authorize Cloud Shell bằng tài khoản quản lý GrabShare.")
    return result.stdout.strip()


def api_call(url, token, body=None, allow_missing=False):
    """Gọi API quản trị, không ghi thông tin xác thực vào log."""
    headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json",
               "X-Goog-User-Project": PROJECT}
    request = Request(url, data=None if body is None else json.dumps(body).encode("utf-8"),
                      headers=headers, method="GET" if body is None else "POST")
    try:
        with urlopen(request, timeout=30) as response:
            return json.load(response)
    except HTTPError as error:
        if allow_missing and error.code == 404:
            return None
        # Chỉ in mã lỗi, không in phản hồi có thể chứa thông tin tài khoản.
        if error.code in {401, 403}:
            raise RuntimeError("API trả HTTP " + str(error.code) +
                               ". Kiểm tra tài khoản Google, quyền project và API đã bật.") from None
        raise RuntimeError("API trả HTTP " + str(error.code) + "; chưa xác nhận ghi thành công.") from None
    except (URLError, TimeoutError):
        raise RuntimeError("Lỗi mạng/timeout. Nếu đang commit, kiểm tra Console trước khi chạy lại.") from None


def resolve_accounts(config, token):
    """Kiểm tra project và lấy email thật của đúng ba UID từ Authentication."""
    project = api_call("https://cloudresourcemanager.googleapis.com/v1/projects/" + PROJECT, token)
    if str(project.get("projectNumber")) != PROJECT_NUMBER:
        raise RuntimeError("Project number không khớp; dừng trước khi ghi.")
    response = api_call("https://identitytoolkit.googleapis.com/v1/projects/" + PROJECT
                        + "/accounts:lookup", token,
                        {"localId": [item["uid"] for item in config["accounts"].values()]})
    users = {item["localId"]: item for item in response.get("users", [])}
    emails = {}
    for role, account in config["accounts"].items():
        user = users.get(account["uid"])
        if not user or not user.get("email") or user.get("disabled"):
            raise RuntimeError(role + ": UID không tồn tại, thiếu email hoặc bị vô hiệu hóa.")
        emails[account["uid"]] = user["email"]
    return emails


def commit_documents(documents, token):
    """Đọc trước, xác nhận danh sách rồi ghi một lần bằng commit nguyên tử."""
    writes = []
    counts = {"create": 0, "update": 0}
    for path, data in documents.items():
        current = api_call(API + "/" + RESOURCE + "/" + path, token, allow_missing=True)
        fields = {key: encode_value(value) for key, value in data.items()}
        if current:
            # Giữ createdAt ban đầu và các field ngoài bộ seed.
            existing_created = current.get("fields", {}).get("createdAt")
            if existing_created:
                fields["createdAt"] = existing_created
            precondition = {"updateTime": current["updateTime"]}
            action = "update"
        else:
            precondition = {"exists": False}
            action = "create"
        counts[action] += 1
        print(action.upper() + " " + path)
        writes.append({"update": {"name": RESOURCE + "/" + path, "fields": fields},
                       "updateMask": {"fieldPaths": list(fields)},
                       "currentDocument": precondition})
    print("\nProject: " + PROJECT + " (" + PROJECT_NUMBER + ")")
    print("Tạo mới: " + str(counts["create"]) + "; cập nhật: " + str(counts["update"]))
    print("Cập nhật sẽ đặt lại dữ liệu demo có trong danh sách; không xóa document.")
    if input("Gõ grabshare để ghi, hoặc Enter để hủy: ").strip() != PROJECT:
        print("Đã hủy; chưa ghi dữ liệu.")
        return
    response = api_call(API + "/" + RESOURCE + ":commit", token, {"writes": writes})
    print("Server đã xác nhận commit: " + response["commitTime"])
    print("Đã ghi " + str(len(response["writeResults"])) + " document.")


def main():
    """Chạy xem trước mặc định; chỉ truy cập cloud/ghi khi có --apply."""
    parser = argparse.ArgumentParser(description="Seed demo GrabShare, mặc định chỉ xem trước.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--preview", action="store_true", help="Xem dữ liệu, không gọi cloud.")
    mode.add_argument("--apply", action="store_true", help="Đọc Auth/Firestore và hỏi xác nhận ghi.")
    parser.add_argument("--date", help="Ngày chuyến tương lai YYYY-MM-DD; mặc định ngày mai UTC+7.")
    args = parser.parse_args()
    config = read_config()
    clock = datetime.now(SAIGON).replace(microsecond=0)
    demo_date = date.fromisoformat(args.date) if args.date else clock.date() + timedelta(days=1)
    if demo_date <= clock.date():
        raise ValueError("Ngày chuyến demo cần sau hôm nay theo UTC+7.")
    if not args.apply:
        documents = build_documents(config, clock, demo_date)
        print(json.dumps({"projectId": PROJECT, "projectNumber": PROJECT_NUMBER,
                          "count": len(documents), "documents": documents},
                         ensure_ascii=False, indent=2, default=printable))
        print("\nCHỈ XEM TRƯỚC; email sẽ lấy từ Auth khi --apply; chưa gọi cloud hoặc ghi dữ liệu.")
        return
    token = access_token()
    emails = resolve_accounts(config, token)
    documents = build_documents(config, clock, demo_date, emails)
    commit_documents(documents, token)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        print("DỪNG: " + str(error), file=sys.stderr)
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        print("\nĐã dừng; nếu đang gửi commit hãy kiểm tra Console.", file=sys.stderr)
        sys.exit(1)
