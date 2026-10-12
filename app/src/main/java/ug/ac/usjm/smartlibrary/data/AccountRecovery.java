package ug.ac.usjm.smartlibrary.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ug.ac.usjm.smartlibrary.util.RecoveryKey;

/**
 * Two-step account recovery (see {@link RecoveryKey}): saves the account's email under
 * recovery/{SHA-256(ID number + PIN)}, and finds it again from the same two answers.
 * Callbacks run on the main thread.
 */
public final class AccountRecovery {

    private AccountRecovery() {
    }

    private static DocumentReference doc(String key) {
        return FirebaseFirestore.getInstance().collection("recovery").document(key);
    }

    /**
     * Sets (or changes) the signed-in person's recovery PIN. The old record is removed in the
     * same batch, so an old PIN stops working at once.
     *
     * @return (to result) the new key, which is also saved on the profile as recoveryKey
     */
    public static void save(String uid, String email, String idNumber, String pin, String previousKey,
                            final CloudLibrary.Result<String> result) {
        final String key = RecoveryKey.of(idNumber, pin);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        if (previousKey != null && !previousKey.equals(key)) batch.delete(doc(previousKey));
        Map<String, Object> record = new HashMap<>();
        record.put("uid", uid);
        // Firebase keeps sign-in emails in lower case; the security rules compare with that.
        record.put("email", email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
        record.put("updatedAt", System.currentTimeMillis());
        batch.set(doc(key), record);
        Map<String, Object> profile = new HashMap<>();
        profile.put("recoveryKey", key);
        profile.put("idNumber", idNumber);
        batch.update(db.collection("users").document(uid), profile);
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) result.onSuccess(key);
            else result.onError(CloudMessages.from(task.getException()));
        });
    }

    /** Step 1 + step 2: returns the account's email, or an error when nothing matches. */
    public static void findEmail(String idNumber, String pin, final CloudLibrary.Result<String> result) {
        doc(RecoveryKey.of(idNumber, pin)).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                result.onError(CloudMessages.from(task.getException()));
                return;
            }
            DocumentSnapshot d = task.getResult();
            String email = d != null && d.exists() ? d.getString("email") : null;
            if (email == null) result.onError(null);   // null = no match (the screen counts the attempt)
            else result.onSuccess(email);
        });
    }
}
