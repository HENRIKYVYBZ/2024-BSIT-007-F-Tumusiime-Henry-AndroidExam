package ug.ac.usjm.smartlibrary;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import ug.ac.usjm.smartlibrary.auth.AuthMessages;
import ug.ac.usjm.smartlibrary.util.Validator;

/** Sends a password-reset link to the student's email (Firebase sends the email). */
public class ForgotPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "ug.ac.usjm.smartlibrary.EMAIL";

    private EditText emailInput;
    private TextView errorText, successText;
    private Button sendButton;
    private boolean sent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        emailInput = (EditText) findViewById(R.id.input_email);
        errorText = (TextView) findViewById(R.id.error_text);
        successText = (TextView) findViewById(R.id.success_text);
        sendButton = (Button) findViewById(R.id.btn_send);

        String typed = getIntent().getStringExtra(EXTRA_EMAIL);
        if (typed != null) emailInput.setText(typed);   // carried over from the sign-in screen

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        sendButton.setOnClickListener(v -> {
            if (sent) finish(); else sendResetLink();
        });
    }

    private void sendResetLink() {
        final String email = emailInput.getText().toString().trim();
        String error = Validator.emailError(email);
        emailInput.setError(error);
        if (error != null) {
            emailInput.requestFocus();
            return;
        }

        sendButton.setEnabled(false);
        sendButton.setText(R.string.sending);
        errorText.setVisibility(View.GONE);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            sendButton.setEnabled(true);
            if (task.isSuccessful()) {
                sent = true;
                successText.setText(getString(R.string.reset_sent, email));
                successText.setVisibility(View.VISIBLE);
                emailInput.setEnabled(false);
                sendButton.setText(R.string.back_to_sign_in);
            } else {
                sendButton.setText(R.string.send_reset_link);
                errorText.setText(AuthMessages.from(task.getException()));
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }
}
