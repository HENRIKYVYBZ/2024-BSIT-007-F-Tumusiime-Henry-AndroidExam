package ug.ac.usjm.smartlibrary.auth;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/** Makes a "Show / Hide" label reveal or mask the password typed in a field. */
public final class PasswordToggle {

    private PasswordToggle() {
    }

    public static void attach(final EditText field, final TextView toggle) {
        toggle.setOnClickListener(new View.OnClickListener() {
            private boolean visible = false;

            @Override
            public void onClick(View v) {
                visible = !visible;
                field.setInputType(InputType.TYPE_CLASS_TEXT | (visible
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                field.setSelection(field.getText().length());   // keep the cursor at the end
                toggle.setText(visible ? "Hide" : "Show");
            }
        });
    }
}
