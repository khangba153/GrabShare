package com.example.grabshare.activity.account;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.grabshare.R;

/** Hiển thị nội dung điều khoản tại form đăng ký. */
public class RegistrationTermsDialog extends DialogFragment {

    //region 1. Nội dung và vòng đời hộp thoại

    /** Tạo hộp thoại có thể được phục hồi khi xoay màn hình. */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.account_terms_title)
                .setMessage(R.string.account_terms_content)
                .setPositiveButton("Đóng", null)
                .create();
    }

    //endregion
}
