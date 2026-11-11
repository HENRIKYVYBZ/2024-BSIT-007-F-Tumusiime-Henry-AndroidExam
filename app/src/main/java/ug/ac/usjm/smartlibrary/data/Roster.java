package ug.ac.usjm.smartlibrary.data;

import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ug.ac.usjm.smartlibrary.util.RosterCsv;
import ug.ac.usjm.smartlibrary.util.RosterKey;

/**
 * The university's official ID list, stored as roster/{key} (key = ID with "/" as "-", see RosterKey):
 * { id, role, name, importedAt, claimedBy, claimedAt }.
 *
 * Automatic approval: at sign-up the app saves the profile as approved AND marks the roster entry as
 * claimed by this account, in one batch. The Firestore security rules accept that batch only when
 * the entry exists, has the same role, and nobody has claimed it yet. If the rules refuse, the
 * profile is saved unapproved instead and waits for an administrator. The app never reads the list
 * itself (only administrators can), so nobody can browse other people's IDs or names.
 *
 * Callbacks run on the main thread.
 */
public final class Roster {

    /** Firestore allows up to 500 writes per batch; stay below it. */
    private static final int BATCH_SIZE = 400;

    /** What an administrator sees about one ID. */
    public static final class Info {
        public final boolean onList;
        public final String role;       // role on the list, or null
        public final String claimedBy;  // uid of the account that claimed it, or null

        Info(boolean onList, String role, String claimedBy) {
            this.onList = onList;
            this.role = role;
            this.claimedBy = claimedBy;
        }
    }

    public interface Progress {
        void onProgress(int written, int total);

        void onDone(int written);

        void onError(String message, int written);
    }

    private Roster() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static CollectionReference roster() {
        return db().collection("roster");
    }

    private static DocumentReference user(String uid) {
        return db().collection("users").document(uid);
    }

    private static Map<String, Object> claim(String uid) {
        Map<String, Object> m = new HashMap<>();
        m.put("claimedBy", uid);
        m.put("claimedAt", System.currentTimeMillis());
        return m;
    }

    /** True when the rules refused the claim (ID not on the list, other role, or already used). */
    private static boolean notOnList(Exception e) {
        if (!(e instanceof FirebaseFirestoreException)) return false;
        FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
        return code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                || code == FirebaseFirestoreException.Code.NOT_FOUND;
    }

    /**
     * Saves a new account's profile: approved at once if its ID is on the official list,
     * otherwise unapproved (waiting for an administrator).
     *
     * @param pending the profile from {@link UserProfile#newAccount}
     * @return (to result) the profile that was saved; check {@code approved}
     */
    public static void saveNewAccount(final UserProfile pending, final UserDirectory.Result<UserProfile> result) {
        final String key = RosterKey.of(pending.idNumber);
        if (key.isEmpty()) {   // no ID to look up
            UserDirectory.create(pending, result);
            return;
        }
        final UserProfile verified = pending.verifiedBy(key);
        WriteBatch batch = db().batch();
        batch.set(user(pending.uid), verified.toMap());
        batch.update(roster().document(key), claim(pending.uid));
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.onSuccess(verified);
            } else if (notOnList(task.getException())) {
                UserDirectory.create(pending, result);   // waits for an administrator
            } else {
                result.onError(CloudMessages.from(task.getException()));
            }
        });
    }

    /**
     * For an account that is still waiting: tries the list again (e.g. the administrator imported it
     * after this person signed up).
     *
     * @return (to result) the approved profile, or null if the ID is still not on the list
     */
    public static void checkAgain(final UserProfile waiting, final UserDirectory.Result<UserProfile> result) {
        final String key = RosterKey.of(waiting.idNumber);
        if (key.isEmpty()) {
            result.onSuccess(null);
            return;
        }
        Map<String, Object> changes = new HashMap<>();
        changes.put("approved", true);
        changes.put("rosterKey", key);
        WriteBatch batch = db().batch();
        batch.update(user(waiting.uid), changes);
        batch.update(roster().document(key), claim(waiting.uid));
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) result.onSuccess(waiting.verifiedBy(key));
            else if (notOnList(task.getException())) result.onSuccess(null);
            else result.onError(CloudMessages.from(task.getException()));
        });
    }

    // ------------------------------------------------------------- administrators only

    /**
     * Adds (or updates) every entry of an imported CSV file. Entries already claimed keep their claim,
     * because only id, role and name are written (merge).
     */
    public static void importEntries(final List<RosterCsv.Entry> entries, final Progress progress) {
        writeFrom(entries, 0, System.currentTimeMillis(), progress);
    }

    private static void writeFrom(final List<RosterCsv.Entry> entries, final int start, final long importedAt,
                                  final Progress progress) {
        if (start >= entries.size()) {
            progress.onDone(entries.size());
            return;
        }
        final int end = Math.min(start + BATCH_SIZE, entries.size());
        WriteBatch batch = db().batch();
        for (int i = start; i < end; i++) {
            RosterCsv.Entry e = entries.get(i);
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.id);
            m.put("role", e.role);
            m.put("name", e.name);
            m.put("importedAt", importedAt);
            batch.set(roster().document(e.key), m, SetOptions.merge());
        }
        batch.commit().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                progress.onError(CloudMessages.from(task.getException()), start);
                return;
            }
            progress.onProgress(end, entries.size());
            writeFrom(entries, end, importedAt, progress);
        });
    }

    /** How many IDs are on the list, and how many have been used to create an account. */
    public static void counts(final UserDirectory.Result<long[]> result) {
        roster().count().get(AggregateSource.SERVER).addOnCompleteListener(all -> {
            if (!all.isSuccessful()) {
                result.onError(CloudMessages.from(all.getException()));
                return;
            }
            final long total = all.getResult().getCount();
            roster().whereGreaterThan("claimedAt", 0).count().get(AggregateSource.SERVER)
                    .addOnCompleteListener(claimed -> {
                        long used = claimed.isSuccessful() ? claimed.getResult().getCount() : -1;
                        result.onSuccess(new long[]{total, used});
                    });
        });
    }

    /** Looks up several IDs at once (for the waiting accounts in Manage users). Never fails: unknown = not on list. */
    public static void lookup(final List<String> idNumbers, final UserDirectory.Result<Map<String, Info>> result) {
        final Map<String, Info> found = new HashMap<>();
        if (idNumbers.isEmpty()) {
            result.onSuccess(found);
            return;
        }
        final int[] remaining = {idNumbers.size()};
        for (final String id : idNumbers) {
            final String key = RosterKey.of(id);
            if (key.isEmpty()) {
                if (--remaining[0] == 0) result.onSuccess(found);
                continue;
            }
            roster().document(key).get().addOnCompleteListener(task -> {
                DocumentSnapshot d = task.isSuccessful() ? task.getResult() : null;
                if (d != null && d.exists()) {
                    found.put(key, new Info(true, d.getString("role"), d.getString("claimedBy")));
                } else if (task.isSuccessful()) {
                    found.put(key, new Info(false, null, null));
                }
                if (--remaining[0] == 0) result.onSuccess(found);
            });
        }
    }
}
