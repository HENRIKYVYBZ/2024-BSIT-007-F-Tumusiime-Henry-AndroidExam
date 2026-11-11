package ug.ac.usjm.smartlibrary;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.AccountRecovery;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.RecoveryKey;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Set or change the 6-digit recovery PIN used by "Forgot your email?".
 * Together with the registration number / staff ID it finds the account's email.
 */
public class SetRecoveryPinActivity extends AppCompatActivity {

    private UserProfile me;
    private EditText idInput, pinInput, confirmInput;
    private TextView errorText;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        me = user == null ? null : Session.get(this, user.getUid());
        if (me == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_recovery_pin);

        idInput = (EditText) findViewById(R.id.input_id);
        pinInput = (EditText) findViewById(R.id.input_pin);
        confirmInput = (EditText) findViewById(R.id.input_confirm_pin);
        errorText = (TextView) findViewById(R.id.error_text);
        saveButton = (Button) findViewById(R.id.btn_save_pin);

        ((TextView) findViewById(R.id.pin_title)).setText(me.hasRecoveryPin()
                ? R.string.change_recovery_pin : R.string.set_recovery_pin);
        idInput.setText(me.idNumber);
        // The ID is part of the recovery check, so it can only be typed in if the profile has none yet.
        idInput.setEnabled(me.idNumber.isEmpty());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());
    }

    private void save() {
        final String id = Validator.normaliseRegNumber(idInput.getText().toString());
        String pin = pinInput.getText().toString().trim();
        String idError = Validator.idNumberError(me.role, id);
        String pinError = RecoveryKey.pinError(pin);
        String confirmError = RecoveryKey.confirmPinError(pin, confirmInput.getText().toString());
        idInput.setError(idError);
        pinInput.setError(pinError);
        confirmInput.setError(confirmError);
        if (idError != null || pinError != null || confirmError != null) return;

        saveButton.setEnabled(false);
        AccountRecovery.save(me.uid, me.email, id, pin, me.recoveryKey, new CloudLibrary.Result<String>() {
            @Override
            public void onSuccess(String key) {
                if (isFinishing() || isDestroyed()) return;
                UserProfile updated = new UserProfile(me.uid, me.fullName, me.email, me.role, id, me.approved,
                        me.suspended, me.createdAt);
                updated.recoveryKey = key;
                updated.rosterKey = me.rosterKey;
                Session.set(SetRecoveryPinActivity.this, updated);
                Toast.makeText(SetRecoveryPinActivity.this, R.string.recovery_pin_saved, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                saveButton.setEnabled(true);
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }
}
