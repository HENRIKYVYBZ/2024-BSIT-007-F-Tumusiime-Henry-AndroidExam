package ug.ac.usjm.smartlibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import ug.ac.usjm.smartlibrary.auth.AuthMessages;
import ug.ac.usjm.smartlibrary.auth.PasswordToggle;
import ug.ac.usjm.smartlibrary.util.Validator;

/** First screen: sign in with email and password (Firebase Authentication). */
public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView errorText;
    private Button signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();

        // Already signed in on this phone? Go straight to the catalogue.
        if (auth.getCurrentUser() != null) {
            openCatalogue();
            return;
        }

        setContentView(R.layout.activity_login);
        emailInput = (EditText) findViewById(R.id.input_email);
        passwordInput = (EditText) findViewById(R.id.input_password);
        errorText = (TextView) findViewById(R.id.error_text);
        signInButton = (Button) findViewById(R.id.btn_sign_in);

        PasswordToggle.attach(passwordInput, (TextView) findViewById(R.id.toggle_password));
        signInButton.setOnClickListener(v -> signIn());
        findViewById(R.id.link_forgot).setOnClickListener(v -> {
            Intent i = new Intent(this, ForgotPasswordActivity.class);
            i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, emailInput.getText().toString().trim());
            startActivity(i);
        });
        findViewById(R.id.link_sign_up).setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        String emailError = Validator.emailError(email);
        String passwordError = Validator.signInPasswordError(password);
        emailInput.setError(emailError);
        passwordInput.setError(passwordError);
        if (emailError != null) { emailInput.requestFocus(); return; }
        if (passwordError != null) { passwordInput.requestFocus(); return; }

        setBusy(true);
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            setBusy(false);
            if (task.isSuccessful()) {
                openCatalogue();
            } else {
                showError(AuthMessages.from(task.getException()));
            }
        });
    }

    private void setBusy(boolean busy) {
        signInButton.setEnabled(!busy);
        signInButton.setText(busy ? R.string.signing_in : R.string.sign_in);
        if (busy) errorText.setVisibility(TextView.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(TextView.VISIBLE);
    }

    private void openCatalogue() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
