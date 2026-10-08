package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;

import ug.ac.usjm.smartlibrary.auth.ProfileStore;
import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.notify.PickupReminders;
import ug.ac.usjm.smartlibrary.util.DateText;
import ug.ac.usjm.smartlibrary.util.Roles;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Screen 3 - Reserve: a form that validates the person's details and saves the reservation to Cloud
 * Firestore, where the librarians see it. Taking the copy is a Firestore transaction (see CloudLibrary).
 * The person's name and registration number / staff ID are remembered (SharedPreferences) to save typing next time.
 * Limits depend on the role: e.g. students may book 7 days ahead and hold 3 books, lecturers 14 days and 10 books.
 */
public class ReserveActivity extends AppCompatActivity {

    private static final String PREFS = "student_profile";
    private static final String KEY_NAME = "name";
    private static final String KEY_REG = "reg_number";
    private static final String STATE_PICKUP = "pickup_date";

    private Book book;
    private EditText nameInput;
    private EditText regInput;
    private TextView pickupInput;
    private String pickupDate;   // yyyy-MM-dd, null until chosen
    private String role = Roles.STUDENT;
    private int maxDaysAhead;
    private int maxActive;
    private UserProfile profile;
    private android.widget.Button confirmButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reserve);

        long bookId = getIntent().getLongExtra(BookDetailActivity.EXTRA_BOOK_ID, -1);
        try {
            book = LibraryRepository.get(this).getBook(bookId);
        } catch (SQLException e) {
            book = null;
        }
        if (book == null) {
            Toast.makeText(this, R.string.error_book_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.reserve_book_title)).setText(book.title);
        ((TextView) findViewById(R.id.reserve_book_shelf)).setText(book.shelf);
        nameInput = (EditText) findViewById(R.id.input_name);
        regInput = (EditText) findViewById(R.id.input_reg);
        pickupInput = (TextView) findViewById(R.id.input_pickup);

        // The signed-in person's role decides the ID field and the limits.
        FirebaseUser signedIn = FirebaseAuth.getInstance().getCurrentUser();
        profile = signedIn == null ? null : Session.get(this, signedIn.getUid());
        boolean approved = profile == null || profile.approved;
        if (profile != null) role = profile.role;
        maxDaysAhead = Roles.maxPickupDaysAhead(role, approved);
        maxActive = Roles.maxActiveReservations(role, approved);
        boolean student = Roles.usesRegNumber(role);   // staff waiting for approval still use their staff ID
        ((TextView) findViewById(R.id.label_id)).setText(student ? R.string.label_reg : R.string.label_staff_id);
        regInput.setHint(student ? R.string.hint_reg : R.string.hint_staff_id);
        pickupInput.setHint(getString(R.string.hint_pickup_days, maxDaysAhead));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState == null) {
            // Pre-fill from the signed-in student's profile, else from the last reservation.
            String name = prefs.getString(KEY_NAME, "");
            String reg = prefs.getString(KEY_REG, "");
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String profileName = ProfileStore.name(this, user.getUid());
                String profileReg = ProfileStore.regNumber(this, user.getUid());
                if (profileReg != null && profileReg.isEmpty()) profileReg = null;
                if (profileName == null) profileName = user.getDisplayName();
                if (profileName != null && !profileName.isEmpty()) name = profileName;
                if (profileReg != null) reg = profileReg;
            }
            nameInput.setText(name);
            regInput.setText(reg);
        } else {
            setPickupDate(savedInstanceState.getString(STATE_PICKUP));   // survive screen rotation
        }

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        pickupInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });
        confirmButton = (android.widget.Button) findViewById(R.id.btn_confirm);
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PICKUP, pickupDate);
    }

    private void showDatePicker() {
        Calendar start = pickupDate != null ? Validator.parse(pickupDate) : Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                setPickupDate(Validator.toIso(year, month, day));
            }
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));

        // Only allow today ... today + 7 days in the calendar. The Validator checks again (e.g. Sundays).
        Calendar min = Calendar.getInstance();
        Calendar max = Calendar.getInstance();
        max.add(Calendar.DAY_OF_MONTH, maxDaysAhead);
        dialog.getDatePicker().setMinDate(min.getTimeInMillis() - 1000);
        dialog.getDatePicker().setMaxDate(max.getTimeInMillis());
        dialog.show();
    }

    private void setPickupDate(String iso) {
        pickupDate = iso;
        pickupInput.setError(null);
        pickupInput.setText(iso == null ? "" : DateText.pretty(iso));
    }

    /** Validate every field, show errors next to the fields, and save only if all are valid. */
    private void submit() {
        final String name = nameInput.getText().toString().trim();
        final String reg = Validator.normaliseRegNumber(regInput.getText().toString());

        String nameError = Validator.nameError(name);
        String regError = Validator.idNumberError(role, reg);
        String dateError = Validator.pickupDateError(pickupDate, Validator.today(), maxDaysAhead);

        nameInput.setError(nameError);
        regInput.setError(regError);
        pickupInput.setError(dateError);

        if (nameError != null) {
            nameInput.requestFocus();
            return;
        }
        if (regError != null) {
            regInput.requestFocus();
            return;
        }
        if (dateError != null) {
            Toast.makeText(this, dateError, Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {           // signed out in the meantime
            finish();
            return;
        }
        UserProfile person = profile != null ? profile
                : UserProfile.newAccount(user.getUid(), name, user.getEmail(), Roles.STUDENT, reg);
        setBusy(true);
        CloudLibrary.reserve(book, person, name, reg, pickupDate, maxActive, new CloudLibrary.Result<Reservation>() {
            @Override
            public void onSuccess(Reservation r) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_NAME, name)
                        .putString(KEY_REG, reg)
                        .apply();
                // Remind the person on the morning of the pickup day.
                PickupReminders.schedule(ReserveActivity.this, r);
                showSuccess(r.code());
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                showProblem(message);   // a library rule said no, or no internet
            }
        });
    }

    private void setBusy(boolean busy) {
        confirmButton.setEnabled(!busy);
        confirmButton.setText(busy ? R.string.reserving : R.string.confirm_reservation);
    }

    private void showSuccess(String code) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reserved_title)
                .setMessage(getString(R.string.reserved_message, book.title, DateText.pretty(pickupDate),
                        book.shelf, code))
                .setCancelable(false)
                .setPositiveButton(R.string.done, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void showProblem(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cannot_reserve_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
