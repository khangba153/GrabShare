package com.example.grabshare.activity.account;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.grabshare.R;
import com.example.grabshare.presentation.ScreenFeedback;

/** Nhập email và theo dõi yêu cầu khôi phục mật khẩu. */
public class ResetPasswordActivity extends AppCompatActivity {

    private EditText emailInput;
    private Button submitButton;
    private TextView statusText;
    private ResetPasswordViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_reset_password);

        emailInput = findViewById(R.id.reset_email);
        submitButton = findViewById(R.id.reset_submit);
        statusText = findViewById(R.id.reset_status);

        viewModel = new ViewModelProvider(this)
                .get(ResetPasswordViewModel.class);

        View.OnClickListener backListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        };

        View headerBack = findViewById(R.id.header_back);
        headerBack.setFocusable(true);
        headerBack.setOnClickListener(backListener);

        findViewById(R.id.reset_back)
                .setOnClickListener(backListener);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                emailInput.setError(null);

                String email = emailInput.getText().toString().trim();

                if (!android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email).matches()) {
                    emailInput.setError("Vui lòng nhập email hợp lệ.");
                    emailInput.requestFocus();
                }

                viewModel.submit(email);
            }
        });

        viewModel.getState().observe(
                this,
                new Observer<ResetPasswordViewModel.State>() {
                    @Override
                    public void onChanged(
                            ResetPasswordViewModel.State state) {
                        if (state == null
                                || isFinishing()
                                || isDestroyed()) {
                            return;
                        }

                        boolean locked = state.busy || state.sent;
                        emailInput.setEnabled(!locked);
                        submitButton.setEnabled(!locked);

                        if (locked
                                && !state.email.equals(
                                emailInput.getText().toString())) {
                            emailInput.setText(state.email);
                        }

                        if (state.busy) {
                            submitButton.setText("Đang gửi…");
                        } else if (state.sent) {
                            submitButton.setText("Đã gửi yêu cầu");
                        } else {
                            submitButton.setText("Gửi email khôi phục");
                        }

                        ScreenFeedback.Kind feedbackKind = ScreenFeedback.Kind.INFORMATION;
                        if (state.error) {
                            feedbackKind = ScreenFeedback.Kind.ERROR;
                        } else if (state.sent) {
                            feedbackKind = ScreenFeedback.Kind.SUCCESS;
                        }
                        ScreenFeedback.show(statusText, state.message, feedbackKind);
                        if (state.busy) {
                            findViewById(R.id.reset_progress).setVisibility(View.VISIBLE);
                        } else {
                            findViewById(R.id.reset_progress).setVisibility(View.GONE);
                        }
                        if (state.sent) {
                            findViewById(R.id.reset_sent_hint).setVisibility(View.VISIBLE);
                        } else {
                            findViewById(R.id.reset_sent_hint).setVisibility(View.GONE);
                        }
                    }
                });
    }
}
