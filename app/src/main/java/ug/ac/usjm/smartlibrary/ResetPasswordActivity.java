package ug.ac.usjm.smartlibrary;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.ActionCodeResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthActionCodeException;
import com.google.firebase.auth.FirebaseUser;

import ug.ac.usjm.smartlibrary.auth.AuthMessages;
import ug.ac.usjm.smartlibrary.auth.PasswordToggle;
import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.notify.LoanReminders;
import ug.ac.usjm.smartlibrary.notify.PickupReminders;
import ug.ac.usjm.smartlibrary.util.PasswordRules;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Opens from the link in a Firebase email, e.g.
 * https://usjm-smart-library.web.app/auth/action?mode=resetPassword&oobCode=...
 * (Authentication > Templates > "Customize action URL" points every email there; see hosting/README.md).
 *
 * resetPassword: the person types the new password twice; it is saved only when both match and
 *                follow the same rules as sign-up. Firebase checks the one-time code twice: when
 *                the screen opens (to show whose account it is) and when the password is saved.
 * verifyEmail / verifyAndChangeEmail / recoverEmail: finished straight away and the result shown.
 *
 * If the app is not installed, the same link opens hosting/public/action.html, which does the same.
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private static final String MODE_RESET = "resetPassword";
    private static final String MODE_VERIFY = "verifyEmail";
    private static final String MODE_VERIFY_CHANGE = "verifyAndChangeEmail";
    private static final String MODE_RECOVER = "recoverEmail";

    private FirebaseAuth auth;
    private String code;
    private String accountEmail;

    private View loadingPanel, formPanel, resultPanel;
    private EditText passwordInput, confirmInput;
    private TextView errorText, meterLabel, ruleLength, ruleLetter, ruleNumber, ruleMatch;
    private View[] meter;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);
        auth = FirebaseAuth.getInstance();

        loadingPanel = findViewById(R.id.panel_loading);
        formPanel = findViewById(R.id.panel_form);
        resultPanel = findViewById(R.id.panel_result);
        passwordInput = (EditText) findViewById(R.id.input_password);
        confirmInput = (EditText) findViewById(R.id.input_confirm);
        errorText = (TextView) findViewById(R.id.error_text);
        meterLabel = (TextView) findViewById(R.id.meter_label);
        ruleLength = (TextView) findViewById(R.id.rule_length);
        ruleLetter = (TextView) findViewById(R.id.rule_letter);
        ruleNumber = (TextView) findViewById(R.id.rule_number);
        ruleMatch = (TextView) findViewById(R.id.rule_match);
        saveButton = (Button) findViewById(R.id.btn_save);
        meter = new View[]{findViewById(R.id.meter_1), findViewById(R.id.meter_2),
                findViewById(R.id.meter_3), findViewById(R.id.meter_4)};

        PasswordToggle.attach(passwordInput, (TextView) findViewById(R.id.toggle_password));
        PasswordToggle.attach(confirmInput, (TextView) findViewById(R.id.toggle_confirm));
        TextWatcher live = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshChecks();
            }
        };
        passwordInput.addTextChangedListener(live);
        confirmInput.addTextChangedListener(live);
        refreshChecks();

        findViewById(R.id.btn_back).setOnClickListener(v -> goToSignIn());
        // The link may have opened this screen on its own, so "back" goes to sign-in rather than out of the app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goToSignIn();
            }
        });
        saveButton.setOnClickListener(v -> save());

        handle(getIntent());
    }

    /** A second link tapped while this screen is open replaces the first one. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        Uri link = intent == null ? null : intent.getData();
        String mode = link == null ? null : link.getQueryParameter("mode");
        code = link == null ? null : link.getQueryParameter("oobCode");
        if (mode == null || code == null || code.isEmpty()) {
            showLinkProblem(getString(R.string.link_broken));
            return;
        }
        showPanel(loadingPanel);
        if (MODE_RESET.equals(mode)) {
            checkResetLink();
        } else if (MODE_VERIFY.equals(mode) || MODE_VERIFY_CHANGE.equals(mode)) {
            ((TextView) findViewById(R.id.screen_title)).setText(R.string.email_link_title);
            verifyEmail();
        } else if (MODE_RECOVER.equals(mode)) {
            ((TextView) findViewById(R.id.screen_title)).setText(R.string.email_link_title);
            recoverEmail();
        } else {
            showLinkProblem(getString(R.string.link_broken));
        }
    }

    // ------------------------------------------------------------- reset password

    /** Step 1: is the code still valid, and which account is it for? */
    private void checkResetLink() {
        auth.verifyPasswordResetCode(code).addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful()) {
                showLinkProblem(task.getException());
                return;
            }
            accountEmail = task.getResult();
            ((TextView) findViewById(R.id.account_email)).setText(accountEmail);
            showPanel(formPanel);
            passwordInput.requestFocus();
        });
    }

    /** Step 2: both boxes filled in, the same, and following the rules. Then Firebase saves it. */
    private void save() {
        String password = passwordInput.getText().toString();
        String confirm = confirmInput.getText().toString();
        String passwordError = Validator.newPasswordError(password);
        String confirmError = passwordError == null ? Validator.confirmPasswordError(password, confirm) : null;
        passwordInput.setError(passwordError);
        confirmInput.setError(confirmError);
        if (passwordError != null) { passwordInput.requestFocus(); return; }
        if (confirmError != null) { confirmInput.requestFocus(); return; }

        setSaving(true);
        auth.confirmPasswordReset(code, password).addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;
            setSaving(false);
            if (task.isSuccessful()) {
                signOutIfSameAccount();
                showResult(true, getString(R.string.password_changed_title),
                        getString(R.string.password_changed_message), getString(R.string.sign_in_now),
                        v -> goToSignIn());
            } else if (task.getException() instanceof FirebaseAuthActionCodeException) {
                showLinkProblem(task.getException());   // e.g. used in another tab in the meantime
            } else {
                errorText.setText(AuthMessages.from(task.getException()));
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Firebase signs the account out everywhere after a reset; do the same on this phone straight away. */
    private void signOutIfSameAccount() {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null || accountEmail == null || current.getEmail() == null
                || !current.getEmail().equalsIgnoreCase(accountEmail)) return;
        auth.signOut();
        Session.clear(this);
        PickupReminders.clearAll(this);
        LoanReminders.clearAll(this);
    }

    private void setSaving(boolean saving) {
        saveButton.setEnabled(!saving);
        saveButton.setText(saving ? R.string.saving : R.string.save_new_password);
        if (saving) errorText.setVisibility(View.GONE);
    }

    /** Recolours the strength bars and ticks off the checklist while the person types. */
    private void refreshChecks() {
        String p = passwordInput.getText().toString();
        String c = confirmInput.getText().toString();
        setRule(ruleLength, R.string.rule_length, PasswordRules.longEnough(p));
        setRule(ruleLetter, R.string.rule_letter, PasswordRules.hasLetter(p));
        setRule(ruleNumber, R.string.rule_number, PasswordRules.hasNumber(p));
        setRule(ruleMatch, R.string.rule_match, PasswordRules.matches(p, c));

        int strength = PasswordRules.strength(p);
        int color = ContextCompat.getColor(this, strength <= PasswordRules.WEAK ? R.color.lib_red
                : strength == PasswordRules.FAIR ? R.color.lib_gold : R.color.lib_green);
        int empty = ContextCompat.getColor(this, R.color.lib_grey_bg);
        for (int i = 0; i < meter.length; i++) {
            ((GradientDrawable) meter[i].getBackground().mutate()).setColor(i < strength ? color : empty);
        }
        meterLabel.setText(PasswordRules.strengthLabel(strength));
        meterLabel.setTextColor(strength == 0 ? ContextCompat.getColor(this, R.color.lib_muted) : color);
    }

    private void setRule(TextView row, int label, boolean met) {
        row.setText((met ? "✓   " : "○   ") + getString(label));
        row.setTextColor(ContextCompat.getColor(this, met ? R.color.lib_green : R.color.lib_muted));
    }

    // ------------------------------------------------------------- other email links

    private void verifyEmail() {
        auth.applyActionCode(code).addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful()) {
                showLinkProblem(task.getException());
                return;
            }
            FirebaseUser current = auth.getCurrentUser();
            if (current != null) current.reload();   // so isEmailVerified() is true straight away
            String email = current != null && current.getEmail() != null ? current.getEmail() : "";
            showResult(true, getString(R.string.email_verified_title),
                    getString(R.string.email_verified_message, email.isEmpty() ? "Your email" : email),
                    getString(R.string.continue_to_app), v -> goToSignIn());
        });
    }

    /** Someone changed the account email and the owner tapped "undo" in the message sent to the old address. */
    private void recoverEmail() {
        auth.checkActionCode(code).addOnCompleteListener(this, check -> {
            if (isFinishing() || isDestroyed()) return;
            if (!check.isSuccessful()) {
                showLinkProblem(check.getException());
                return;
            }
            ActionCodeResult result = check.getResult();
            final String restored = result.getInfo() == null ? "" : result.getInfo().getEmail();
            auth.applyActionCode(code).addOnCompleteListener(this, apply -> {
                if (isFinishing() || isDestroyed()) return;
                if (!apply.isSuccessful()) {
                    showLinkProblem(apply.getException());
                    return;
                }
                showResult(true, getString(R.string.email_restored_title),
                        getString(R.string.email_restored_message, restored),
                        getString(R.string.reset_password_now), v -> openForgotPassword(restored));
            });
        });
    }

    // ------------------------------------------------------------- shared

    private void showLinkProblem(Exception e) {
        showLinkProblem(e instanceof FirebaseAuthActionCodeException
                ? getString(R.string.link_expired) : AuthMessages.from(e));
    }

    private void showLinkProblem(String message) {
        showResult(false, getString(R.string.link_problem_title), message, getString(R.string.send_new_link),
                v -> openForgotPassword(accountEmail));
        TextView back = (TextView) findViewById(R.id.link_result_secondary);
        back.setText(R.string.back_to_sign_in);
        back.setVisibility(View.VISIBLE);
        back.setOnClickListener(v -> goToSignIn());
    }

    private void showResult(boolean ok, String title, String message, String button, View.OnClickListener action) {
        TextView icon = (TextView) findViewById(R.id.result_icon);
        icon.setText(ok ? "✓" : "!");
        icon.setBackgroundResource(ok ? R.drawable.bg_step_done : R.drawable.bg_circle_red);
        ((TextView) findViewById(R.id.result_title)).setText(title);
        ((TextView) findViewById(R.id.result_message)).setText(message);
        Button b = (Button) findViewById(R.id.btn_result);
        b.setText(button);
        b.setOnClickListener(action);
        findViewById(R.id.link_result_secondary).setVisibility(View.GONE);
        showPanel(resultPanel);
    }

    private void showPanel(View panel) {
        loadingPanel.setVisibility(panel == loadingPanel ? View.VISIBLE : View.GONE);
        formPanel.setVisibility(panel == formPanel ? View.VISIBLE : View.GONE);
        resultPanel.setVisibility(panel == resultPanel ? View.VISIBLE : View.GONE);
    }

    /** Opens the sign-in screen as a fresh start of the app, with the account's email typed in. */
    private void goToSignIn() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (accountEmail != null) i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, accountEmail);
        startActivity(i);
        finish();
    }

    private void openForgotPassword(String email) {
        Intent i = new Intent(this, ForgotPasswordActivity.class);
        if (email != null) i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, email);
        startActivity(i);
        finish();
    }
}
