package ug.ac.usjm.smartlibrary.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes user profiles in Cloud Firestore (collection "users", one document per account).
 * Firestore keeps a copy on the phone, so profiles still load offline after the first time.
 * Callbacks run on the main thread.
 */
public final class UserDirectory {

    public interface Result<T> {
        void onSuccess(T value);

        void onError(String message);
    }

    private UserDirectory() {
    }

    private static CollectionReference users() {
        return FirebaseFirestore.getInstance().collection("users");
    }

    /** Saves a new account's profile (sign-up). */
    public static void create(final UserProfile p, final Result<UserProfile> result) {
        users().document(p.uid).set(p.toMap()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) result.onSuccess(p);
            else result.onError(CloudMessages.from(task.getException()));
        });
    }

    /**
     * Loads the signed-in person's profile. Accounts made before roles existed have no profile yet,
     * so one is created from {@code fallback} (the details saved on this phone).
     */
    public static void loadOrCreate(final String uid, final UserProfile fallback, final Result<UserProfile> result) {
        users().document(uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            DocumentSnapshot d = task.getResult();
            if (d != null && d.exists()) {
                result.onSuccess(UserProfile.from(d));
            } else {
                create(fallback, result);
            }
        });
    }

    /** Every account, waiting staff first, then by name (administrators only; checked by the security rules). */
    public static void listAll(final Result<List<UserProfile>> result) {
        users().get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            List<UserProfile> list = new ArrayList<>();
            for (DocumentSnapshot d : task.getResult().getDocuments()) {
                list.add(UserProfile.from(d));
            }
            Collections.sort(list, (a, b) -> {
                if (a.isWaitingForApproval() != b.isWaitingForApproval()) return a.isWaitingForApproval() ? -1 : 1;
                return a.fullName.compareToIgnoreCase(b.fullName);
            });
            result.onSuccess(list);
        });
    }

    /** Gives an account a role and approves it (administrators only). */
    public static void setRole(String uid, String role, Result<Void> result) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("role", role);
        changes.put("approved", true);
        update(uid, changes, result);
    }

    /** Suspends or restores an account (administrators only). */
    public static void setSuspended(String uid, boolean suspended, Result<Void> result) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("suspended", suspended);
        update(uid, changes, result);
    }

    private static void update(String uid, Map<String, Object> changes, final Result<Void> result) {
        users().document(uid).update(changes).addOnCompleteListener(task -> {
            if (task.isSuccessful()) result.onSuccess(null);
            else result.onError(CloudMessages.from(task.getException()));
        });
    }
}
