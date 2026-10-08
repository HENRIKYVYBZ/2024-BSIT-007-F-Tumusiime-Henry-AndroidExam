package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ug.ac.usjm.smartlibrary.auth.ProfileStore;
import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.notify.PickupReminders;
import ug.ac.usjm.smartlibrary.sync.CatalogueSync;
import ug.ac.usjm.smartlibrary.util.ServerAddress;
import ug.ac.usjm.smartlibrary.util.QrPayload;
import ug.ac.usjm.smartlibrary.util.Validator;

/** Screen 1 - Catalogue: every book from the phone's SQLite database, with live search. Requires sign-in. */
public class MainActivity extends AppCompatActivity {

    private LibraryRepository repo;
    private BookAdapter adapter;
    private EditText searchBox;
    private TextView resultCount;
    private TextView syncButton;
    private boolean syncing = false;

    /** Opens the camera scanner and receives the scanned text (ZXing library). */
    private final ActivityResultLauncher<ScanOptions> qrScanner =
            registerForActivityResult(new ScanContract(), result -> onQrScanned(result.getContents()));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Only signed-in students may use the catalogue.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        setContentView(R.layout.activity_main);

        ((TextView) findViewById(R.id.greeting)).setText(getString(R.string.greeting, firstName(user)));
        findViewById(R.id.btn_sign_out).setOnClickListener(v -> confirmSignOut());

        PickupReminders.createChannel(this);
        askForNotificationPermissionOnce();

        repo = LibraryRepository.get(this);
        searchBox = (EditText) findViewById(R.id.search_box);
        resultCount = (TextView) findViewById(R.id.result_count);

        ListView list = (ListView) findViewById(R.id.book_list);
        adapter = new BookAdapter(this);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty_view));

        // Navigation: tap a book -> Book Details screen (the book id travels in the Intent).
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(MainActivity.this, BookDetailActivity.class);
                i.putExtra(BookDetailActivity.EXTRA_BOOK_ID, id);
                startActivity(i);
            }
        });

        // Navigation: header button -> My Reservations screen.
        findViewById(R.id.btn_my_reservations).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MyReservationsActivity.class));
            }
        });

        // Sync the catalogue with the library web system (long-press to change the server).
        syncButton = (TextView) findViewById(R.id.btn_sync);
        syncButton.setOnClickListener(v -> onSyncTapped());
        syncButton.setOnLongClickListener(v -> {
            askForServer();
            return true;
        });
        if (CatalogueSync.server(this) != null && CatalogueSync.hasServerCatalogue(this)) {
            sync(false);   // quietly refresh copy counts on every launch
        }

        // Scan a book's QR label -> its details screen.
        findViewById(R.id.btn_scan).setOnClickListener(v -> startQrScan());

        // Search as the user types.
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadBooks();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter == null) return;   // redirected to the sign-in screen
        // Runs on first open and every time the user comes back, so copy counts are always current.
        try {
            repo.expireUncollected(Validator.today());
        } catch (SQLException e) {
            // Not critical: the list still loads.
        }
        loadBooks();
    }

    private void onSyncTapped() {
        if (CatalogueSync.server(this) == null) {
            askForServer();
        } else if (!CatalogueSync.hasServerCatalogue(this)) {
            confirmFirstSync();
        } else {
            sync(true);
        }
    }

    /** Dialog to type the library server's address, e.g. 192.168.1.10:8000. */
    private void askForServer() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.server_hint);
        String saved = CatalogueSync.server(this);
        if (saved != null) input.setText(saved.replaceFirst("^http://", ""));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.server_title)
                .setMessage(R.string.server_message)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_and_sync, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String base = ServerAddress.normalise(input.getText().toString());
            if (base == null) {
                input.setError(getString(R.string.server_invalid));
                return;
            }
            CatalogueSync.setServer(this, base);
            dialog.dismiss();
            onSyncTapped();
        }));
        dialog.show();
    }

    /** The first sync replaces the sample books, so the student confirms it once. */
    private void confirmFirstSync() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.first_sync_title)
                .setMessage(R.string.first_sync_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sync_now, (d, w) -> sync(true))
                .show();
    }

    /** @param loud true when the student tapped Sync: show the outcome; false for the quiet launch refresh */
    private void sync(final boolean loud) {
        if (syncing) return;
        syncing = true;
        syncButton.setText(R.string.syncing);
        CatalogueSync.run(this, new CatalogueSync.Callback() {
            @Override
            public void onSynced(int bookCount) {
                syncing = false;
                syncButton.setText(R.string.sync);
                loadBooks();
                if (loud) {
                    Toast.makeText(MainActivity.this, getString(R.string.sync_done, bookCount),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailed(String message) {
                syncing = false;
                syncButton.setText(R.string.sync);
                if (loud) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.sync_failed_title)
                            .setMessage(message + "\n\n" + getString(R.string.sync_offline_note))
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.change_server, (d, w) -> askForServer())
                            .show();
                }
            }
        });
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false);
        qrScanner.launch(options);   // the scanner asks for camera permission itself
    }

    /** @param text what the QR code contains, or null if the student cancelled */
    private void onQrScanned(String text) {
        if (text == null) return;
        long id = QrPayload.bookId(text);
        Book book = id > 0 ? repo.getBook(id) : null;
        if (book == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.scan_unknown_title)
                    .setMessage(R.string.scan_unknown_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        Intent i = new Intent(this, BookDetailActivity.class);
        i.putExtra(BookDetailActivity.EXTRA_BOOK_ID, book.id);
        startActivity(i);
    }

    /** Android 13+ asks the user before an app may show notifications; we ask once, on first use. */
    private void askForNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return;
        String permission = "android.permission.POST_NOTIFICATIONS";
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences prefs = getSharedPreferences("app_state", MODE_PRIVATE);
        if (prefs.getBoolean("asked_notifications", false)) return;
        prefs.edit().putBoolean("asked_notifications", true).apply();
        ActivityCompat.requestPermissions(this, new String[]{permission}, 1);
    }

    /** First name from the saved profile, else the Firebase display name, else the email. */
    private String firstName(FirebaseUser user) {
        String name = ProfileStore.name(this, user.getUid());
        if (name == null) name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            String email = user.getEmail();
            return email == null ? "student" : email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
        }
        return name.trim().split("\\s+")[0];
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sign_out_title)
                .setMessage(R.string.sign_out_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sign_out, (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    goToLogin();
                })
                .show();
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void loadBooks() {
        try {
            List<Book> books = repo.getBooks(searchBox.getText().toString());
            adapter.setBooks(books);
            String count = getResources().getQuantityString(R.plurals.books_found, books.size(), books.size());
            long last = CatalogueSync.lastSync(this);
            resultCount.setText(last == 0 ? count : getString(R.string.count_with_sync, count,
                    new SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(new Date(last))));
        } catch (SQLException e) {
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_LONG).show();
        }
    }
}
