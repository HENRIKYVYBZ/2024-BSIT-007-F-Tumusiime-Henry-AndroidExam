package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.Book;
import ug.ac.usjm.smartlibrary.data.CloudLibrary;
import ug.ac.usjm.smartlibrary.data.LibraryDbHelper;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.sync.CatalogueSync;
import ug.ac.usjm.smartlibrary.util.DateText;
import ug.ac.usjm.smartlibrary.util.Loans;
import ug.ac.usjm.smartlibrary.util.QrPayload;
import ug.ac.usjm.smartlibrary.util.Roles;
import ug.ac.usjm.smartlibrary.util.ServerAddress;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Librarians (and administrators): the circulation desk.
 *   Pickups  - holds waiting to be collected: Collected or No-show
 *   On loan  - books people have, with due dates: Returned
 *   Overdue  - loans past their due date: Returned
 *   Stock    - books with one copy or none left
 * Scanning a book's QR label shows only that book's holds and loans. The Catalogue menu publishes
 * the shared catalogue (sample books, or the web system's books via its REST API).
 * Everything updates live from Cloud Firestore.
 */
public class DeskActivity extends AppCompatActivity implements DeskAdapter.OnDeskAction {

    private static final int TAB_PICKUPS = 0, TAB_LOANS = 1, TAB_OVERDUE = 2, TAB_STOCK = 3;

    private UserProfile me;
    private DeskAdapter deskAdapter;
    private BookAdapter stockAdapter;
    private ListView list;
    private TextView emptyView, filterChip;
    private TextView[] tabs;
    private View catalogueBanner;
    private int tab = TAB_PICKUPS;

    private final List<Reservation> holds = new ArrayList<>();
    private final List<Reservation> loans = new ArrayList<>();
    private final Set<String> expiring = new HashSet<>();
    private boolean holdsLoaded, loansLoaded;
    private ListenerRegistration holdsListener, loansListener, catalogueListener;

    /** Set after scanning a book: only its holds and loans are shown (-1 = all books). */
    private long filterBookId = -1;

    private final ActivityResultLauncher<ScanOptions> qrScanner =
            registerForActivityResult(new ScanContract(), result -> onQrScanned(result.getContents()));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        me = user == null ? null : Session.get(this, user.getUid());
        if (me == null || !Roles.canRunDesk(me.role, me.approved)) {
            Toast.makeText(this, R.string.librarians_only, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_desk);

        ((TextView) findViewById(R.id.desk_date)).setText(DateText.pretty(Validator.today()));
        list = (ListView) findViewById(R.id.desk_list);
        emptyView = (TextView) findViewById(R.id.empty_view);
        filterChip = (TextView) findViewById(R.id.filter_chip);
        catalogueBanner = findViewById(R.id.catalogue_banner);
        list.setEmptyView(emptyView);
        deskAdapter = new DeskAdapter(this, this);
        stockAdapter = new BookAdapter(this);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (tab == TAB_STOCK) {
                Intent i = new Intent(this, BookDetailActivity.class);
                i.putExtra(BookDetailActivity.EXTRA_BOOK_ID, id);
                startActivity(i);
            }
        });

        tabs = new TextView[]{(TextView) findViewById(R.id.tab_pickups), (TextView) findViewById(R.id.tab_loans),
                (TextView) findViewById(R.id.tab_overdue), (TextView) findViewById(R.id.tab_stock)};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> selectTab(index));
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_scan).setOnClickListener(v -> qrScanner.launch(new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_prompt_desk))
                .setBeepEnabled(true)
                .setOrientationLocked(false)));
        findViewById(R.id.btn_catalogue).setOnClickListener(v -> showCatalogueMenu());
        findViewById(R.id.btn_publish_samples).setOnClickListener(v -> confirmPublishSamples());
        findViewById(R.id.btn_import).setOnClickListener(v -> startImport());
        filterChip.setOnClickListener(v -> {
            filterBookId = -1;
            show();
        });
        selectTab(TAB_PICKUPS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (me == null) return;
        holdsListener = CloudLibrary.listenToStatus(Reservation.ACTIVE, listener(true));
        loansListener = CloudLibrary.listenToStatus(Reservation.COLLECTED, listener(false));
        catalogueListener = CloudLibrary.listenToCatalogue(new CloudLibrary.Listener<List<Book>>() {
            @Override
            public void onChange(List<Book> books) {
                catalogueBanner.setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
                if (!books.isEmpty()) {
                    try {
                        LibraryRepository.get(DeskActivity.this).replaceCatalogue(books);
                    } catch (android.database.SQLException e) {
                        // keep the previous copy
                    }
                }
                if (tab == TAB_STOCK) show();
                updateTabLabels();
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (ListenerRegistration r : new ListenerRegistration[]{holdsListener, loansListener, catalogueListener}) {
            if (r != null) r.remove();
        }
    }

    private CloudLibrary.Listener<List<Reservation>> listener(final boolean isHolds) {
        return new CloudLibrary.Listener<List<Reservation>>() {
            @Override
            public void onChange(List<Reservation> value) {
                if (isHolds) {
                    holds.clear();
                    String today = Validator.today();
                    for (Reservation r : value) {
                        // Pickup day passed without collection: expire it so the copy goes back on the shelf.
                        if (Loans.pickupMissed(r.pickupDate, today)) {
                            if (expiring.add(r.id)) CloudLibrary.expire(r, retryLater(r.id));
                        } else {
                            holds.add(r);
                        }
                    }
                    Collections.sort(holds, (a, b) -> String.valueOf(a.pickupDate).compareTo(String.valueOf(b.pickupDate)));
                    holdsLoaded = true;
                } else {
                    loans.clear();
                    loans.addAll(value);
                    Collections.sort(loans, (a, b) -> String.valueOf(a.dueDate).compareTo(String.valueOf(b.dueDate)));
                    loansLoaded = true;
                }
                show();
            }

            @Override
            public void onError(String message) {
                emptyView.setText(message);
            }
        };
    }

    // ------------------------------------------------------------------ showing

    private void selectTab(int index) {
        tab = index;
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_tab);
            tabs[i].setTextColor(ContextCompat.getColor(this, selected ? R.color.lib_card : R.color.lib_navy));
        }
        show();
    }

    private List<Reservation> forTab(int which) {
        String today = Validator.today();
        List<Reservation> out = new ArrayList<>();
        List<Reservation> source = which == TAB_PICKUPS ? holds : loans;
        for (Reservation r : source) {
            if (filterBookId >= 0 && r.bookId != filterBookId) continue;
            if (which == TAB_LOANS && r.dueDate != null && Loans.daysOverdue(r.dueDate, today) > 0) continue;
            if (which == TAB_OVERDUE && (r.dueDate == null || Loans.daysOverdue(r.dueDate, today) == 0)) continue;
            out.add(r);
        }
        return out;
    }

    private void show() {
        updateTabLabels();
        if (filterBookId >= 0) {
            Book b = LibraryRepository.get(this).getBook(filterBookId);
            filterChip.setText(getString(R.string.filter_chip, b == null ? "#" + filterBookId : b.title));
            filterChip.setVisibility(View.VISIBLE);
        } else {
            filterChip.setVisibility(View.GONE);
        }

        if (tab == TAB_STOCK) {
            if (list.getAdapter() != stockAdapter) list.setAdapter(stockAdapter);   // keeps the scroll position
            stockAdapter.setBooks(LibraryRepository.get(this).getLowStock());
            emptyView.setText(R.string.empty_stock);
            return;
        }
        if (list.getAdapter() != deskAdapter) list.setAdapter(deskAdapter);
        deskAdapter.setItems(forTab(tab));
        boolean loaded = tab == TAB_PICKUPS ? holdsLoaded : loansLoaded;
        int empty = tab == TAB_PICKUPS ? R.string.empty_pickups
                : tab == TAB_LOANS ? R.string.empty_loans : R.string.empty_overdue;
        emptyView.setText(!loaded ? R.string.loading : filterBookId >= 0 ? R.string.empty_for_book : empty);
    }

    private void updateTabLabels() {
        tabs[TAB_PICKUPS].setText(getString(R.string.tab_pickups, forTab(TAB_PICKUPS).size()));
        tabs[TAB_LOANS].setText(getString(R.string.tab_loans, forTab(TAB_LOANS).size()));
        tabs[TAB_OVERDUE].setText(getString(R.string.tab_overdue, forTab(TAB_OVERDUE).size()));
        tabs[TAB_STOCK].setText(getString(R.string.tab_stock, LibraryRepository.get(this).getLowStock().size()));
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void onCollected(final Reservation r) {
        CloudLibrary.markCollected(r, me.fullName, done(getString(R.string.collected_toast, r.userName,
                DateText.pretty(Loans.dueDate(Validator.today(), Roles.loanDays(r.role, true))))));
    }

    @Override
    public void onNoShow(final Reservation r) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.no_show_title)
                .setMessage(getString(R.string.no_show_message, r.userName, r.bookTitle))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.no_show, (d, w) ->
                        CloudLibrary.markNoShow(r, me.fullName, done(getString(R.string.no_show_toast))))
                .show();
    }

    @Override
    public void onReturned(final Reservation r) {
        CloudLibrary.markReturned(r, me.fullName, done(getString(R.string.returned_toast, r.bookTitle)));
    }

    private CloudLibrary.Result<Void> done(final String successMessage) {
        return new CloudLibrary.Result<Void>() {
            @Override
            public void onSuccess(Void v) {
                Toast.makeText(DeskActivity.this, successMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                showMessage(getString(R.string.could_not_save), message);
            }
        };
    }

    /** If expiring fails (e.g. offline), forget it so the next update tries again. */
    private CloudLibrary.Result<Void> retryLater(final String reservationId) {
        return new CloudLibrary.Result<Void>() {
            @Override
            public void onSuccess(Void v) {
            }

            @Override
            public void onError(String message) {
                expiring.remove(reservationId);
            }
        };
    }

    /** After scanning a label: show that book's holds and loans, on the tab that has them. */
    private void onQrScanned(String text) {
        if (text == null) return;
        long id = QrPayload.bookId(text);
        if (id <= 0) {
            showMessage(getString(R.string.scan_unknown_title), getString(R.string.scan_unknown_message));
            return;
        }
        filterBookId = id;
        if (!forTab(TAB_PICKUPS).isEmpty()) selectTab(TAB_PICKUPS);
        else if (!forTab(TAB_OVERDUE).isEmpty()) selectTab(TAB_OVERDUE);
        else if (!forTab(TAB_LOANS).isEmpty()) selectTab(TAB_LOANS);
        else selectTab(tab == TAB_STOCK ? TAB_PICKUPS : tab);
    }

    // --------------------------------------------------------------- catalogue

    private void showCatalogueMenu() {
        String[] items = {getString(R.string.import_from_server), getString(R.string.publish_samples),
                getString(R.string.change_server)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.catalogue_menu_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) startImport();
                    else if (which == 1) confirmPublishSamples();
                    else askForServer(false);
                })
                .show();
    }

    private void confirmPublishSamples() {
        final List<Book> samples = LibraryDbHelper.sampleBooks();
        new AlertDialog.Builder(this)
                .setTitle(R.string.publish_samples)
                .setMessage(getString(R.string.publish_samples_message, samples.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.publish, (d, w) ->
                        CloudLibrary.publishCatalogue(samples, published()))
                .show();
    }

    private void startImport() {
        if (CatalogueSync.server(this) == null) {
            askForServer(true);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_from_server)
                .setMessage(getString(R.string.import_message, CatalogueSync.server(this)))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.change_server, (d, w) -> askForServer(true))
                .setPositiveButton(R.string.import_now, (d, w) -> runImport())
                .show();
    }

    private void runImport() {
        Toast.makeText(this, R.string.importing, Toast.LENGTH_SHORT).show();
        CatalogueSync.run(this, new CatalogueSync.Callback() {
            @Override
            public void onSynced(int bookCount) {
                published().onSuccess(bookCount);
            }

            @Override
            public void onFailed(String message) {
                published().onError(message);
            }
        });
    }

    private CloudLibrary.Result<Integer> published() {
        return new CloudLibrary.Result<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(DeskActivity.this, getString(R.string.published_toast, count), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                showMessage(getString(R.string.sync_failed_title), message);
            }
        };
    }

    /** Dialog to type the web system's address, e.g. 192.168.1.10:8000. */
    private void askForServer(final boolean thenImport) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.server_hint);
        String saved = CatalogueSync.server(this);
        if (saved != null) input.setText(saved.replaceFirst("^http://", ""));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.server_title)
                .setMessage(R.string.server_message)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(thenImport ? R.string.save_and_import : R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String base = ServerAddress.normalise(input.getText().toString());
            if (base == null) {
                input.setError(getString(R.string.server_invalid));
                return;
            }
            CatalogueSync.setServer(this, base);
            dialog.dismiss();
            if (thenImport) runImport();
        }));
        dialog.show();
    }

    private void showMessage(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
