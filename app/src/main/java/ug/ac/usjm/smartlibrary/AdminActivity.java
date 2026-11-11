package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.Roster;
import ug.ac.usjm.smartlibrary.data.UserDirectory;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.Roles;
import ug.ac.usjm.smartlibrary.util.RosterCsv;
import ug.ac.usjm.smartlibrary.util.RosterKey;

/**
 * Administrators only: import the university's official ID list (CSV), approve accounts whose ID was
 * not on it, give people a role (e.g. make someone a librarian) and suspend or restore accounts.
 * Accounts whose ID is on the list are approved automatically at sign-up (see Roster), so the
 * Waiting tab only holds the exceptions. The Firestore security rules allow these changes only for
 * administrators, so this screen cannot be misused even if someone opened it another way.
 */
public class AdminActivity extends AppCompatActivity implements UserAdapter.OnDecisionListener {

    private UserAdapter adapter;
    private TextView tabWaiting, tabAll, emptyView;
    private EditText searchBox;
    private final List<UserProfile> everyone = new ArrayList<>();
    private boolean showWaitingOnly = true;
    private String myUid;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.ENGLISH);

    /** System file picker for the CSV file (Downloads, Google Drive, WhatsApp documents...). */
    private final ActivityResultLauncher<String[]> pickCsv =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::readCsv);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        UserProfile me = user == null ? null : Session.get(this, user.getUid());
        if (me == null || !Roles.canManageUsers(me.role, me.approved)) {
            Toast.makeText(this, R.string.admins_only, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        myUid = me.uid;
        setContentView(R.layout.activity_admin);

        tabWaiting = (TextView) findViewById(R.id.tab_waiting);
        tabAll = (TextView) findViewById(R.id.tab_all);
        emptyView = (TextView) findViewById(R.id.empty_view);
        searchBox = (EditText) findViewById(R.id.search_box);
        ListView list = (ListView) findViewById(R.id.user_list);
        adapter = new UserAdapter(this, this);
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);
        list.setOnItemClickListener((parent, view, position, id) -> showActions(adapter.getItem(position)));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> load());
        findViewById(R.id.btn_id_list).setOnClickListener(v -> showIdList());
        tabWaiting.setOnClickListener(v -> selectTab(true));
        tabAll.setOnClickListener(v -> selectTab(false));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showList();
            }
        });
        selectTab(true);
        load();
    }

    private void load() {
        emptyView.setText(R.string.loading);
        UserDirectory.listAll(new UserDirectory.Result<List<UserProfile>>() {
            @Override
            public void onSuccess(List<UserProfile> users) {
                if (isFinishing() || isDestroyed()) return;
                everyone.clear();
                everyone.addAll(users);
                showList();
                explainWaiting();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                emptyView.setText(message);
                adapter.setUsers(new ArrayList<UserProfile>());
            }
        });
    }

    private void selectTab(boolean waiting) {
        showWaitingOnly = waiting;
        styleTab(tabWaiting, waiting);
        styleTab(tabAll, !waiting);
        showList();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_tab);
        tab.setTextColor(ContextCompat.getColor(this, selected ? R.color.lib_card : R.color.lib_navy));
    }

    /** Applies the tab and the search box to the downloaded accounts. */
    private void showList() {
        int waitingCount = 0;
        for (UserProfile u : everyone) {
            if (u.isWaitingForApproval() && !u.suspended) waitingCount++;
        }
        tabWaiting.setText(getString(R.string.tab_waiting, waitingCount));
        tabAll.setText(getString(R.string.tab_all_users, everyone.size()));

        String q = searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<UserProfile> shown = new ArrayList<>();
        for (UserProfile u : everyone) {
            if (showWaitingOnly && (!u.isWaitingForApproval() || u.suspended)) continue;
            if (!q.isEmpty() && !(u.fullName.toLowerCase(Locale.ROOT).contains(q)
                    || u.email.toLowerCase(Locale.ROOT).contains(q)
                    || u.idNumber.toLowerCase(Locale.ROOT).contains(q))) continue;
            shown.add(u);
        }
        adapter.setUsers(shown);
        if (shown.isEmpty() && !everyone.isEmpty()) {
            emptyView.setText(showWaitingOnly ? R.string.no_waiting_accounts : R.string.no_matching_users);
        }
    }

    /** Approve button on a waiting card: the person keeps the staff role they asked for. */
    @Override
    public void onApprove(UserProfile u) {
        if (u.uid.equals(myUid)) return;
        changeRole(u, u.role);
    }

    /** Reject button on a waiting card: the ID is not a real USJM one, so the account is suspended. */
    @Override
    public void onReject(final UserProfile u) {
        if (u.uid.equals(myUid)) return;
        confirm(getString(R.string.reject_title), getString(R.string.reject_message, u.fullName),
                () -> setSuspended(u, true));
    }

    /** What the administrator can do with one account. */
    private void showActions(final UserProfile u) {
        if (u.uid.equals(myUid)) {
            // Protects against an administrator accidentally locking themselves out.
            Toast.makeText(this, R.string.cannot_change_self, Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (u.isWaitingForApproval()) {
            labels.add(getString(R.string.action_approve, Roles.label(u.role).toLowerCase(Locale.ENGLISH)));
            actions.add(() -> changeRole(u, u.role));
            labels.add(getString(R.string.action_reject));
            actions.add(() -> onReject(u));
        }
        labels.add(getString(R.string.action_change_role));
        actions.add(() -> pickRole(u));
        if (u.suspended) {
            labels.add(getString(R.string.action_restore));
            actions.add(() -> setSuspended(u, false));
        } else {
            labels.add(getString(R.string.action_suspend));
            actions.add(() -> confirm(getString(R.string.suspend_title),
                    getString(R.string.suspend_message, u.fullName), () -> setSuspended(u, true)));
        }

        new AlertDialog.Builder(this)
                .setTitle(u.fullName.isEmpty() ? u.email : u.fullName)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickRole(final UserProfile u) {
        String[] names = new String[Roles.ALL.length];
        int current = 0;
        for (int i = 0; i < Roles.ALL.length; i++) {
            names[i] = Roles.label(Roles.ALL[i]);
            if (Roles.ALL[i].equals(u.role)) current = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_role_for, u.fullName))
                .setSingleChoiceItems(names, current, (d, which) -> {
                    d.dismiss();
                    changeRole(u, Roles.ALL[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void changeRole(final UserProfile u, final String role) {
        UserDirectory.setRole(u.uid, role, new UserDirectory.Result<Void>() {
            @Override
            public void onSuccess(Void v) {
                String message = u.isWaitingForApproval() && role.equals(u.role)
                        ? getString(R.string.approved_toast, u.fullName, Roles.label(role))
                        : getString(R.string.role_changed, u.fullName, Roles.label(role));
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private void setSuspended(final UserProfile u, final boolean suspend) {
        UserDirectory.setSuspended(u.uid, suspend, new UserDirectory.Result<Void>() {
            @Override
            public void onSuccess(Void v) {
                Toast.makeText(AdminActivity.this, suspend ? R.string.account_suspended : R.string.account_restored,
                        Toast.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    // ------------------------------------------------------------- official ID list

    /** Adds a short note to each waiting card: not on the list, on it with another role, or already used. */
    private void explainWaiting() {
        final List<String> ids = new ArrayList<>();
        for (UserProfile u : everyone) {
            if (u.isWaitingForApproval() && !u.suspended && !u.idNumber.isEmpty()) ids.add(u.idNumber);
        }
        Roster.lookup(ids, new UserDirectory.Result<Map<String, Roster.Info>>() {
            @Override
            public void onSuccess(Map<String, Roster.Info> byKey) {
                if (isFinishing() || isDestroyed()) return;
                Map<String, String> notes = new HashMap<>();
                for (UserProfile u : everyone) {
                    if (!u.isWaitingForApproval()) continue;
                    if (u.idNumber.isEmpty()) {
                        notes.put(u.uid, getString(R.string.note_no_id));
                        continue;
                    }
                    Roster.Info info = byKey.get(RosterKey.of(u.idNumber));
                    if (info == null) continue;   // lookup failed (offline): say nothing
                    if (!info.onList) {
                        notes.put(u.uid, getString(R.string.note_not_on_list));
                    } else if (info.claimedBy != null && !info.claimedBy.equals(u.uid)) {
                        notes.put(u.uid, getString(R.string.note_id_used));
                    } else if (!u.role.equals(info.role)) {
                        notes.put(u.uid, getString(R.string.note_other_role, Roles.label(info.role)));
                    } else {
                        notes.put(u.uid, getString(R.string.note_on_list));
                    }
                }
                adapter.setNotes(notes);
            }

            @Override
            public void onError(String message) {
                // Notes are a help only.
            }
        });
    }

    /** How many IDs are loaded, and a button to import a CSV file. */
    private void showIdList() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.id_list_title)
                .setMessage(getString(R.string.id_list_loading) + "\n\n" + getString(R.string.id_list_format))
                .setPositiveButton(R.string.import_csv, (d, w) -> pickCsv.launch(new String[]{
                        "text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"}))
                .setNegativeButton(R.string.close, null)
                .show();
        Roster.counts(new UserDirectory.Result<long[]>() {
            @Override
            public void onSuccess(long[] c) {
                if (!dialog.isShowing()) return;
                String used = c[1] < 0 ? "?" : numbers.format(c[1]);
                dialog.setMessage(getString(R.string.id_list_counts, numbers.format(c[0]), used)
                        + "\n\n" + getString(R.string.id_list_format));
            }

            @Override
            public void onError(String message) {
                if (dialog.isShowing()) dialog.setMessage(message + "\n\n" + getString(R.string.id_list_format));
            }
        });
    }

    /** Reads the chosen file off the main thread, then shows what was found before importing. */
    private void readCsv(final Uri uri) {
        if (uri == null) return;   // picker closed without a file
        io.execute(() -> {
            String text = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 5_000_000) break;   // a list of IDs is never this big
                    }
                    text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                text = null;
            }
            final RosterCsv.Result parsed = text == null ? null : RosterCsv.parse(text);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (parsed == null) showError(getString(R.string.csv_unreadable));
                else confirmImport(parsed);
            });
        });
    }

    private void confirmImport(final RosterCsv.Result r) {
        if (r.entries.isEmpty()) {
            showError(getString(R.string.csv_nothing_found) + problemsText(r));
            return;
        }
        String summary = getString(R.string.csv_summary, numbers.format(r.entries.size()),
                numbers.format(r.students), numbers.format(r.teachingStaff), numbers.format(r.nonTeachingStaff));
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_title)
                .setMessage(summary + problemsText(r))
                .setPositiveButton(R.string.import_now, (d, w) -> runImport(r.entries))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The first few skipped lines, so the administrator can fix the file. */
    private String problemsText(RosterCsv.Result r) {
        if (r.problems.isEmpty()) return "";
        StringBuilder b = new StringBuilder("\n\n").append(getString(R.string.csv_skipped, r.problems.size()));
        for (int i = 0; i < Math.min(5, r.problems.size()); i++) b.append("\n• ").append(r.problems.get(i));
        if (r.problems.size() > 5) b.append("\n…");
        return b.toString();
    }

    private void runImport(final List<RosterCsv.Entry> entries) {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle(R.string.importing)
                .setMessage(getString(R.string.import_progress, "0", numbers.format(entries.size())))
                .setCancelable(false)
                .show();
        Roster.importEntries(entries, new Roster.Progress() {
            @Override
            public void onProgress(int written, int total) {
                if (progress.isShowing()) {
                    progress.setMessage(getString(R.string.import_progress, numbers.format(written), numbers.format(total)));
                }
            }

            @Override
            public void onDone(int written) {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                Toast.makeText(AdminActivity.this, getString(R.string.import_done, numbers.format(written)),
                        Toast.LENGTH_LONG).show();
                load();   // waiting accounts may now match the list
            }

            @Override
            public void onError(String message, int written) {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                showError(getString(R.string.import_failed, numbers.format(written)) + "\n\n" + message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void confirm(String title, String message, final Runnable onYes) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> onYes.run())
                .show();
    }

    private void showError(String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.could_not_save)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
