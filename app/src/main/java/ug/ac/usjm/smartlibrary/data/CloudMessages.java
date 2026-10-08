package ug.ac.usjm.smartlibrary.data;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestoreException;

/** Turns Cloud Firestore errors into short messages people understand. */
public final class CloudMessages {

    private static final String TAG = "SmartLibraryCloud";

    private CloudMessages() {
    }

    public static String from(Exception e) {
        // Full details go to Logcat (filter by "SmartLibraryCloud").
        Log.w(TAG, "Cloud Firestore error", e);
        String detail = e == null || e.getMessage() == null ? "" : e.getMessage();

        if (detail.contains("does not exist") && detail.contains("database")) {
            return "The cloud database has not been created yet. In the Firebase console open "
                    + "Firestore Database and click Create database.";
        }
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED:
                    return "You don't have permission to do that. If you think you should, ask the library administrator.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "No internet connection. Check your data or Wi-Fi and try again.";
                case NOT_FOUND:
                    return "That record no longer exists. Refresh and try again.";
                case ABORTED:
                    return "Someone else changed this at the same moment. Please try again.";
                default:
                    break;
            }
        }
        return "Something went wrong: " + (detail.isEmpty() ? "unknown error" : detail);
    }
}
