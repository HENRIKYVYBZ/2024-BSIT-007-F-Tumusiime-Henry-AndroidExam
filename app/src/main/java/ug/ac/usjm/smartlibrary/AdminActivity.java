package ug.ac.usjm.smartlibrary;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ug.ac.usjm.smartlibrary.auth.Session;
import ug.ac.usjm.smartlibrary.data.UserDirectory;
import ug.ac.usjm.smartlibrary.data.UserProfile;
import ug.ac.usjm.smartlibrary.util.Roles;

/**
 * Administrators only: approve staff accounts, give people a role (e.g. make someone a librarian)
 * and suspend or restore accounts. The Firestore security rules allow these changes only for
 * administrators, so this screen cannot be misused even if someone opened it another way.
 */
public class AdminActivity extends AppCompatActivity implements UserAdapter.OnDecisionListener {

    private UserAdapter adapter;
    private TextView tabWaiting, tabAll, emptyView;
    private EditText searchBox;
    private final List<UserProfile> everyone = new ArrayList<>();
    private boolean showWaitingOnly = true;
    private String myUid;

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

    /** Reject button on a waiting card: after confirming, the account stays a student account. */
    @Override
    public void onReject(final UserProfile u) {
        if (u.uid.equals(myUid)) return;
        confirm(getString(R.string.reject_title), getString(R.string.reject_message, u.fullName),
                () -> changeRole(u, Roles.STUDENT));
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
            actions.add(() -> confirm(getString(R.string.reject_title),
                    getString(R.string.reject_message, u.fullName), () -> changeRole(u, Roles.STUDENT)));
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
