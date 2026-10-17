package com.example.grabshare.activity.account;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/** Hiển thị nội dung điều khoản tại form đăng ký. */
public class RegistrationTermsDialog extends DialogFragment {

    /** Tạo hộp thoại có thể được phục hồi khi xoay màn hình. */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String content =
                "1. Phạm vi sử dụng\n"
                        + "GrabShare là ứng dụng cộng đồng chia sẻ chuyến xe máy. "
                        + "Người dùng có thể đăng chuyến hoặc gửi yêu cầu đi nhờ.\n\n"

                        + "2. Thông tin chuyến đi\n"
                        + "Cung cấp thông tin tuyến, giờ đi, điểm đón và mức đóng góp "
                        + "rõ ràng. Chỉ tham gia khi chủ xe đã chấp nhận.\n\n"

                        + "3. Tôn trọng và an toàn\n"
                        + "Trao đổi lịch sự, đến đúng giờ, sử dụng mũ bảo hiểm "
                        + "và thông báo sớm nếu cần hủy.";

        return new AlertDialog.Builder(requireContext())
                .setTitle("Điều khoản sử dụng")
                .setMessage(content)
                .setPositiveButton("Đóng", null)
                .create();
    }
}