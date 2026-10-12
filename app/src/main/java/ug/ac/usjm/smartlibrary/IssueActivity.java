package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.BookValidator;
import ug.ac.usjm.smartlibrary.util.DateText;
import ug.ac.usjm.smartlibrary.util.Loans;
import ug.ac.usjm.smartlibrary.util.Roles;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Librarians: lend a book to someone at the desk who didn't reserve it (walk-in).
 * The librarian types the reader's registration number or staff ID, checks the name that comes up,
 * and taps Lend. The loan then shows in the reader's My reservations with its due date.
 */
public class IssueActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK_ID = BookDetailActivity.EXTRA_BOOK_ID;

    private UserProfile me;
    private Book book;
    private UserProfile reader;
    private EditText readerInput;
    private TextView errorText;
    private View readerCard;
    private Button findButton, lendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        me = user == null ? null : Session.get(this, user.getUid());
        book = LibraryRepository.get(this).getBook(getIntent().getLongExtra(EXTRA_BOOK_ID, -1));
        if (me == null || !Roles.canRunDesk(me.role, me.approved) || book == null) {
            Toast.makeText(this, book == null ? R.string.error_book_not_found : R.string.librarians_only,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_issue);

        ((TextView) findViewById(R.id.issue_book_title)).setText(book.title);
        ((TextView) findViewById(R.id.issue_book_shelf)).setText(getString(R.string.by_author, book.author)
                + "  •  " + book.shelf);
        TextView availability = (TextView) findViewById(R.id.issue_book_availability);
        availability.setText(book.availabilityText());
        availability.setBackgroundResource(book.isAvailable() ? R.drawable.bg_badge_green : R.drawable.bg_badge_red);
        availability.setTextColor(ContextCompat.getColor(this, book.isAvailable() ? R.color.lib_green : R.color.lib_red));

        readerInput = (EditText) findViewById(R.id.input_reader);
        errorText = (TextView) findViewById(R.id.error_text);
        readerCard = findViewById(R.id.reader_card);
        findButton = (Button) findViewById(R.id.btn_find);
        lendButton = (Button) findViewById(R.id.btn_lend);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findButton.setOnClickListener(v -> findReader());
        lendButton.setOnClickListener(v -> lend());
        readerInput.setOnEditorActionListener((v, actionId, event) -> {
            findReader();
            return true;
        });
        // Typing a different ID forgets the reader found before.
        readerInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showReader(null);
            }
        });
        if (!book.isAvailable()) showError(getString(R.string.no_copy_to_lend));
    }

    private void findReader() {
        String id = Validator.normaliseRegNumber(readerInput.getText().toString());
        String error = BookValidator.readerIdError(id);
        readerInput.setError(error);
        if (error != null) return;
        errorText.setVisibility(View.GONE);
        findButton.setEnabled(false);
        CloudLibrary.findReader(id, new CloudLibrary.Result<UserProfile>() {
            @Override
            public void onSuccess(UserProfile found) {
                if (isFinishing() || isDestroyed()) return;
                findButton.setEnabled(true);
                showReader(found);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                findButton.setEnabled(true);
                showError(message);
            }
        });
    }

    /** @param found null hides the reader card */
    private void showReader(UserProfile found) {
        reader = found;
        readerCard.setVisibility(found == null ? View.GONE : View.VISIBLE);
        lendButton.setEnabled(found != null && book.isAvailable());
        if (found == null) return;
        RoleStyle.applyAvatar((TextView) findViewById(R.id.reader_avatar), found.fullName, found.effectiveRole());
        RoleStyle.applyBadge((TextView) findViewById(R.id.reader_role), found.role, found.isWaitingForApproval());
        ((TextView) findViewById(R.id.reader_name)).setText(found.fullName);
        String due = Loans.dueDate(Validator.today(), Roles.loanDays(found.role, found.approved));
        ((TextView) findViewById(R.id.reader_due)).setText(getString(R.string.lend_due_line,
                Roles.loanDays(found.role, found.approved), DateText.pretty(due)));
    }

    private void lend() {
        if (reader == null) return;
        lendButton.setEnabled(false);
        lendButton.setText(R.string.lending);
        CloudLibrary.issueWalkIn(book, reader, me.fullName, new CloudLibrary.Result<Reservation>() {
            @Override
            public void onSuccess(Reservation r) {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(IssueActivity.this)
                        .setTitle(R.string.lent_title)
                        .setMessage(getString(R.string.lent_message, book.title, reader.fullName,
                                DateText.pretty(r.dueDate), r.code()))
                        .setCancelable(false)
                        .setPositiveButton(R.string.done, (d, w) -> finish())
                        .show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                lendButton.setText(R.string.lend_book);
                lendButton.setEnabled(true);
                showError(message);
            }
        });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
