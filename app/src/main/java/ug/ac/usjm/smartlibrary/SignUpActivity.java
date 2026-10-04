package ug.ac.usjm.smartlibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import ug.ac.usjm.smartlibrary.auth.AuthMessages;
import ug.ac.usjm.smartlibrary.auth.PasswordToggle;
import ug.ac.usjm.smartlibrary.auth.ProfileStore;
import ug.ac.usjm.smartlibrary.util.Validator;

/** Create a student account: full name, registration number, email and password. */
public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText nameInput, regInput, emailInput, passwordInput, confirmInput;
    private TextView errorText;
    private Button createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        auth = FirebaseAuth.getInstance();

        nameInput = (EditText) findViewById(R.id.input_name);
        regInput = (EditText) findViewById(R.id.input_reg);
        emailInput = (EditText) findViewById(R.id.input_email);
        passwordInput = (EditText) findViewById(R.id.input_password);
        confirmInput = (EditText) findViewById(R.id.input_confirm);
        errorText = (TextView) findViewById(R.id.error_text);
        createButton = (Button) findViewById(R.id.btn_create);

        PasswordToggle.attach(passwordInput, (TextView) findViewById(R.id.toggle_password));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.link_sign_in).setOnClickListener(v -> finish());
        createButton.setOnClickListener(v -> createAccount());
    }

    private void createAccount() {
        final String name = nameInput.getText().toString().trim();
        final String reg = Validator.normaliseRegNumber(regInput.getText().toString());
        final String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirm = confirmInput.getText().toString();

        // Check every field and show each problem next to its field.
        EditText[] fields = {nameInput, regInput, emailInput, passwordInput, confirmInput};
        String[] errors = {
                Validator.nameError(name),
                Validator.regNumberError(reg),
                Validator.emailError(email),
                Validator.newPasswordError(password),
                Validator.confirmPasswordError(password, confirm)};
        EditText firstBad = null;
        for (int i = 0; i < fields.length; i++) {
            fields[i].setError(errors[i]);
            if (errors[i] != null && firstBad == null) firstBad = fields[i];
        }
        if (firstBad != null) {
            firstBad.requestFocus();
            return;
        }

        setBusy(true);
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                setBusy(false);
                showError(AuthMessages.from(task.getException()));
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                ProfileStore.save(this, user.getUid(), name, reg);
                user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());
                user.sendEmailVerification();
            }
            Toast.makeText(this, getString(R.string.account_created, email), Toast.LENGTH_LONG).show();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    private void setBusy(boolean busy) {
        createButton.setEnabled(!busy);
        createButton.setText(busy ? R.string.creating_account : R.string.create_account);
        if (busy) errorText.setVisibility(TextView.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(TextView.VISIBLE);
    }
}
