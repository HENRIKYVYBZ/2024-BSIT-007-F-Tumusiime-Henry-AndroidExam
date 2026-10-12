package ug.ac.usjm.smartlibrary;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import ug.ac.usjm.smartlibrary.auth.AuthMessages;
import ug.ac.usjm.smartlibrary.data.AccountRecovery;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.util.BookValidator;
import ug.ac.usjm.smartlibrary.util.RecoveryKey;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * "Forgot your email?" - two-step recovery without SMS.
 *   Step 1: registration number or staff ID.
 *   Step 2: the 6-digit recovery PIN chosen at sign-up.
 * If both match, the account's email is shown and a password-reset link can be sent to it.
 * After 5 wrong PINs the screen locks for 15 minutes.
 */
public class RecoverAccountActivity extends AppCompatActivity {

    private static final int STEP_ID = 1, STEP_PIN = 2, STEP_FOUND = 3;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;
    private static final String PREFS = "account_recovery";

    private int step = STEP_ID;
    private String idNumber;
    private String foundEmail;
    private EditText idInput, pinInput;
    private TextView heading, intro, errorText, stepOne, stepTwo;
    private View stepLine;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover);

        idInput = (EditText) findViewById(R.id.input_id);
        pinInput = (EditText) findViewById(R.id.input_pin);
        heading = (TextView) findViewById(R.id.step_heading);
        intro = (TextView) findViewById(R.id.step_intro);
        errorText = (TextView) findViewById(R.id.error_text);
        stepOne = (TextView) findViewById(R.id.step_one);
        stepTwo = (TextView) findViewById(R.id.step_two);
        stepLine = findViewById(R.id.step_line);
        nextButton = (Button) findViewById(R.id.btn_next);

        findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
        nextButton.setOnClickListener(v -> next());
        findViewById(R.id.btn_send_reset).setOnClickListener(v -> sendReset());
        pinInput.setOnEditorActionListener((v, actionId, event) -> {
            next();
            return true;
        });
        showStep(STEP_ID);
    }

    @Override
    public void onBackPressed() {
        if (step == STEP_PIN) showStep(STEP_ID);   // back goes to step 1, not out of the screen
        else super.onBackPressed();
    }

    private void showStep(int s) {
        step = s;
        errorText.setVisibility(View.GONE);
        findViewById(R.id.panel_id).setVisibility(s == STEP_ID ? View.VISIBLE : View.GONE);
        findViewById(R.id.panel_pin).setVisibility(s == STEP_PIN ? View.VISIBLE : View.GONE);
        findViewById(R.id.panel_found).setVisibility(s == STEP_FOUND ? View.VISIBLE : View.GONE);
        findViewById(R.id.no_pin_help).setVisibility(s == STEP_FOUND ? View.GONE : View.VISIBLE);

        stepOne.setText(s == STEP_ID ? "1" : "✓");
        stepOne.setBackgroundResource(s == STEP_ID ? R.drawable.bg_step_active : R.drawable.bg_step_done);
        stepTwo.setText(s == STEP_FOUND ? "✓" : "2");
        stepTwo.setBackgroundResource(s == STEP_ID ? R.drawable.bg_step_inactive
                : s == STEP_PIN ? R.drawable.bg_step_active : R.drawable.bg_step_done);
        stepTwo.setTextColor(ContextCompat.getColor(this, s == STEP_ID ? R.color.lib_muted : R.color.lib_card));
        stepLine.setBackgroundColor(ContextCompat.getColor(this, s == STEP_ID ? R.color.lib_border : R.color.lib_green));

        if (s == STEP_ID) {
            heading.setText(R.string.recover_step1_heading);
            intro.setText(R.string.recover_step1_intro);
            nextButton.setText(R.string.next);
            idInput.requestFocus();
        } else if (s == STEP_PIN) {
            heading.setText(R.string.recover_step2_heading);
            intro.setText(getString(R.string.recover_step2_intro, idNumber));
            nextButton.setText(R.string.verify);
            pinInput.setText("");
            pinInput.requestFocus();
        } else {
            heading.setText(R.string.recover_found_heading);
            intro.setText(R.string.recover_found_intro);
            nextButton.setText(R.string.back_to_sign_in);
            ((TextView) findViewById(R.id.found_email)).setText(foundEmail);
        }
    }

    private void next() {
        if (step == STEP_ID) {
            String id = Validator.normaliseRegNumber(idInput.getText().toString());
            String error = BookValidator.readerIdError(id);
            idInput.setError(error);
            if (error != null) return;
            idNumber = id;
            showStep(STEP_PIN);
        } else if (step == STEP_PIN) {
            verifyPin();
        } else {
            backToSignIn();
        }
    }

    private void verifyPin() {
        long lockedFor = prefs().getLong("locked_until", 0) - System.currentTimeMillis();
        if (lockedFor > 0) {
            showError(getString(R.string.recover_locked, (int) Math.ceil(lockedFor / 60000.0)));
            return;
        }
        String pin = pinInput.getText().toString().trim();
        if (!pin.matches("\\d{" + RecoveryKey.PIN_LENGTH + "}")) {
            pinInput.setError(getString(R.string.pin_six_digits));
            return;
        }
        nextButton.setEnabled(false);
        AccountRecovery.findEmail(idNumber, pin, new CloudLibrary.Result<String>() {
            @Override
            public void onSuccess(String email) {
                if (isFinishing() || isDestroyed()) return;
                nextButton.setEnabled(true);
                prefs().edit().clear().apply();
                foundEmail = email;
                showStep(STEP_FOUND);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                nextButton.setEnabled(true);
                if (message != null) {   // network or setup problem, not a wrong PIN
                    showError(message);
                    return;
                }
                countFailedAttempt();
            }
        });
    }

    /** Wrong ID or PIN: say so (without saying which), and lock after too many tries. */
    private void countFailedAttempt() {
        SharedPreferences p = prefs();
        int attempts = p.getInt("attempts", 0) + 1;
        if (attempts >= MAX_ATTEMPTS) {
            p.edit().putInt("attempts", 0).putLong("locked_until", System.currentTimeMillis() + LOCK_MILLIS).apply();
            showError(getString(R.string.recover_locked, (int) (LOCK_MILLIS / 60000)));
        } else {
            p.edit().putInt("attempts", attempts).apply();
            showError(getString(R.string.recover_no_match, MAX_ATTEMPTS - attempts));
        }
        pinInput.setText("");
    }

    private void sendReset() {
        final Button send = (Button) findViewById(R.id.btn_send_reset);
        send.setEnabled(false);
        FirebaseAuth.getInstance().sendPasswordResetEmail(foundEmail).addOnCompleteListener(this, task -> {
            send.setEnabled(true);
            if (task.isSuccessful()) {
                errorText.setVisibility(View.GONE);
                send.setText(R.string.reset_link_sent);
                send.setEnabled(false);
            } else {
                showError(AuthMessages.from(task.getException()));
            }
        });
    }

    /** Back to the sign-in screen with the found email already typed in. */
    private void backToSignIn() {
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, foundEmail);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }
}
