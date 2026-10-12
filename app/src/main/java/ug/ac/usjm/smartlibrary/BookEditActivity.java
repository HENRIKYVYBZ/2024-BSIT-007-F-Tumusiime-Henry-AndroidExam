package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.BookValidator;
import ug.ac.usjm.smartlibrary.util.Roles;

/**
 * Librarians: add a new book to the shared catalogue, or edit / remove one.
 * Every field is checked (BookValidator). The total copies can't go below the copies that are
 * reserved or on loan; the available count is worked out from that.
 */
public class BookEditActivity extends AppCompatActivity {

    /** Leave out (or -1) to add a new book. */
    public static final String EXTRA_BOOK_ID = BookDetailActivity.EXTRA_BOOK_ID;

    private long bookId;
    private Book existing;
    private int copiesOut;
    private EditText title, author, category, shelf, year, copies, description;
    private TextView errorText;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        UserProfile me = user == null ? null : Session.get(this, user.getUid());
        if (me == null || !Roles.canRunDesk(me.role, me.approved)) {
            Toast.makeText(this, R.string.librarians_only, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_book_edit);

        bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);
        existing = bookId < 0 ? null : LibraryRepository.get(this).getBook(bookId);
        if (bookId >= 0 && existing == null) {
            Toast.makeText(this, R.string.error_book_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        title = (EditText) findViewById(R.id.input_title);
        author = (EditText) findViewById(R.id.input_author);
        category = (EditText) findViewById(R.id.input_category);
        shelf = (EditText) findViewById(R.id.input_shelf);
        year = (EditText) findViewById(R.id.input_year);
        copies = (EditText) findViewById(R.id.input_copies);
        description = (EditText) findViewById(R.id.input_description);
        errorText = (TextView) findViewById(R.id.error_text);
        saveButton = (Button) findViewById(R.id.btn_save);

        if (existing != null && savedInstanceState == null) {
            ((TextView) findViewById(R.id.edit_title)).setText(R.string.edit_book_title);
            title.setText(existing.title);
            author.setText(existing.author);
            category.setText(existing.category);
            shelf.setText(existing.shelf);
            year.setText(String.valueOf(existing.year));
            copies.setText(String.valueOf(existing.totalCopies));
            description.setText(existing.description);
        }
        if (existing != null) {
            copiesOut = existing.totalCopies - existing.availableCopies;
            TextView note = (TextView) findViewById(R.id.copies_note);
            note.setText(getResources().getQuantityString(R.plurals.copies_out_note, copiesOut, copiesOut));
            note.setVisibility(View.VISIBLE);
            View delete = findViewById(R.id.btn_delete);
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> confirmDelete());
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());
    }

    private void save() {
        EditText[] fields = {title, author, category, shelf, year, copies, description};
        String[] errors = {
                BookValidator.titleError(text(title)),
                BookValidator.authorError(text(author)),
                BookValidator.categoryError(text(category)),
                BookValidator.shelfError(text(shelf)),
                BookValidator.yearError(text(year), Calendar.getInstance().get(Calendar.YEAR)),
                BookValidator.copiesError(text(copies), copiesOut),
                BookValidator.descriptionError(text(description))};
        EditText firstBad = null;
        for (int i = 0; i < fields.length; i++) {
            fields[i].setError(errors[i]);
            if (errors[i] != null && firstBad == null) firstBad = fields[i];
        }
        if (firstBad != null) {
            firstBad.requestFocus();
            return;
        }
        int total = Integer.parseInt(text(copies));
        Book changes = new Book(bookId, text(title), text(author), text(category), text(shelf),
                Integer.parseInt(text(year)), text(description), total, total);

        saveButton.setEnabled(false);
        errorText.setVisibility(View.GONE);
        CloudLibrary.saveBook(bookId, changes, new CloudLibrary.Result<Long>() {
            @Override
            public void onSuccess(Long id) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(BookEditActivity.this, existing == null
                        ? getString(R.string.book_added, changes.title, id)
                        : getString(R.string.book_saved), Toast.LENGTH_LONG).show();
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

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_book_title)
                .setMessage(getString(R.string.delete_book_message, existing.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_book, (d, w) ->
                        CloudLibrary.deleteBook(bookId, new CloudLibrary.Result<Void>() {
                            @Override
                            public void onSuccess(Void v) {
                                Toast.makeText(BookEditActivity.this, R.string.book_deleted, Toast.LENGTH_SHORT).show();
                                setResult(RESULT_FIRST_USER);   // tells the details screen to close too
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                if (isFinishing() || isDestroyed()) return;
                                errorText.setText(message);
                                errorText.setVisibility(View.VISIBLE);
                            }
                        }))
                .show();
    }

    private static String text(EditText e) {
        return e.getText().toString().trim();
    }
}
