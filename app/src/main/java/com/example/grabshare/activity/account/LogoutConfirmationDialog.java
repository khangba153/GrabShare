package com.example.grabshare.activity.account;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.grabshare.R;

/** Chỉ xác nhận ý định; màn gọi kiểm tra lại UID trước khi chạy LogoutViewModel. */
public class LogoutConfirmationDialog extends DialogFragment {
    public static final String TAG = "logout_confirmation";
    public static final String RESULT_KEY = "logout_confirmed";
    public static final String USER_IDENTIFIER = "logout_user_identifier";

    /** Gắn hộp thoại với tài khoản hiện tại, giữ được khi xoay màn hình. */
    public static LogoutConfirmationDialog create(String userIdentifier) {
        LogoutConfirmationDialog dialog = new LogoutConfirmationDialog();
        Bundle arguments = new Bundle();
        arguments.putString(USER_IDENTIFIER, userIdentifier);
        dialog.setArguments(arguments);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.leader_logout_title)
                .setMessage(R.string.leader_logout_message)
                .setNegativeButton(R.string.leader_stay_signed_in, null)
                .setPositiveButton(R.string.leader_logout_action,
                        new DialogInterface.OnClickListener() {
                            /** Chưa xóa cache hoặc phiên tại đây; chuyển xác nhận về đúng màn. */
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Bundle result = new Bundle();
                                result.putString(USER_IDENTIFIER,
                                        requireArguments().getString(USER_IDENTIFIER));
                                getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
                            }
                        })
                .create();
    }
}
