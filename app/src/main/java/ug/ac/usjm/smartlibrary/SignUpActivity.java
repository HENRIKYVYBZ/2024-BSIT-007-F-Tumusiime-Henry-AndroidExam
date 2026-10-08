package ug.ac.usjm.smartlibrary;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import ug.ac.usjm.smartlibrary.auth.AuthMessages;
import ug.ac.usjm.smartlibrary.auth.PasswordToggle;
import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.UserDirectory;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.Roles;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Create an account: who you are (student, teaching staff or non-teaching staff), full name,
 * registration number or staff ID, email and password. The account is created in Firebase
 * Authentication and the profile is saved in Cloud Firestore (users/{uid}).
 * Staff accounts wait for an administrator's approval; librarians and administrators are never self-chosen.
 */
public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText nameInput, idInput, emailInput, passwordInput, confirmInput;
    private TextView errorText, idLabel, staffNote;
    private RadioGroup roleGroup;
    private Button createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        auth = FirebaseAuth.getInstance();

        roleGroup = (RadioGroup) findViewById(R.id.role_group);
        staffNote = (TextView) findViewById(R.id.staff_note);
        idLabel = (TextView) findViewById(R.id.label_id);
        nameInput = (EditText) findViewById(R.id.input_name);
        idInput = (EditText) findViewById(R.id.input_reg);
        emailInput = (EditText) findViewById(R.id.input_email);
        passwordInput = (EditText) findViewById(R.id.input_password);
        confirmInput = (EditText) findViewById(R.id.input_confirm);
        errorText = (TextView) findViewById(R.id.error_text);
        createButton = (Button) findViewById(R.id.btn_create);

        PasswordToggle.attach(passwordInput, (TextView) findViewById(R.id.toggle_password));
        roleGroup.setOnCheckedChangeListener((group, checkedId) -> showFieldsForRole());
        showFieldsForRole();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.link_sign_in).setOnClickListener(v -> finish());
        createButton.setOnClickListener(v -> createAccount());
    }

    private String selectedRole() {
        int id = roleGroup.getCheckedRadioButtonId();
        if (id == R.id.role_teaching) return Roles.TEACHING_STAFF;
        if (id == R.id.role_non_teaching) return Roles.NON_TEACHING_STAFF;
        return Roles.STUDENT;
    }

    /** Students give a registration number; staff give a staff ID and see the approval note. */
    private void showFieldsForRole() {
        boolean student = Roles.usesRegNumber(selectedRole());
        idLabel.setText(student ? R.string.label_reg : R.string.label_staff_id);
        idInput.setHint(student ? R.string.hint_reg : R.string.hint_staff_id);
        idInput.setError(null);
        staffNote.setVisibility(student ? View.GONE : View.VISIBLE);
    }

    private void createAccount() {
        final String role = selectedRole();
        final String name = nameInput.getText().toString().trim();
        final String idNumber = Validator.normaliseRegNumber(idInput.getText().toString());
        final String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirm = confirmInput.getText().toString();

        // Check every field and show each problem next to its field.
        EditText[] fields = {nameInput, idInput, emailInput, passwordInput, confirmInput};
        String[] errors = {
                Validator.nameError(name),
                Validator.idNumberError(role, idNumber),
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
            if (user == null) {
                setBusy(false);
                return;
            }
            user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());
            user.sendEmailVerification();

            // Remember the profile on the phone first, so it is not lost if the cloud save fails.
            UserProfile profile = UserProfile.newAccount(user.getUid(), name, email, role, idNumber);
            Session.set(this, profile);
            UserDirectory.create(profile, new UserDirectory.Result<UserProfile>() {
                @Override
                public void onSuccess(UserProfile saved) {
                    finishSignUp(email, null);
                }

                @Override
                public void onError(String message) {
                    // The account exists; the catalogue retries saving the profile on its next launch.
                    finishSignUp(email, message);
                }
            });
        });
    }

    private void finishSignUp(String email, String cloudError) {
        String welcome = getString(R.string.account_created, email);
        Toast.makeText(this, cloudError == null ? welcome : welcome + "\n" + cloudError, Toast.LENGTH_LONG).show();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
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
