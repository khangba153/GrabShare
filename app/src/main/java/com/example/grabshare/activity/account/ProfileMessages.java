package com.example.grabshare.activity.account;

import android.content.Context;

import com.example.grabshare.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Chuyển lỗi tải/lưu hồ sơ thành thông báo người dùng có thể xử lý. */
final class ProfileMessages {

    //region 1. Chuyển lỗi thành thông báo

    /** Không tạo đối tượng vì lớp chỉ cung cấp hàm định dạng lỗi. */
    private ProfileMessages() {
    }

    /** Phân biệt lỗi mạng, quyền và lỗi dữ liệu; không ghi thông tin riêng vào log. */
    static String explain(Context context, Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return context.getString(R.string.profile_network_error);
        }

        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code =
                    ((FirebaseFirestoreException) error).getCode();

            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return context.getString(R.string.profile_network_error);
            }

            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return context.getString(R.string.profile_permission_error);
            }
        }

        if (error != null && error.getMessage() != null
                && !error.getMessage().trim().isEmpty()) {
            return error.getMessage();
        }

        return context.getString(R.string.profile_unknown_error);
    }

    //endregion
}
