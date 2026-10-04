package ug.ac.usjm.smartlibrary.auth;

import android.util.Log;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Turns Firebase errors into short messages a student understands. */
public final class AuthMessages {

    private static final String TAG = "SmartLibraryAuth";

    private AuthMessages() {
    }

    public static String from(Exception e) {
        // Full details go to Logcat (filter by "SmartLibraryAuth") for debugging.
        Log.w(TAG, "Firebase Authentication error", e);

        String code = e instanceof FirebaseAuthException ? ((FirebaseAuthException) e).getErrorCode() : "";
        String detail = e == null || e.getMessage() == null ? "" : e.getMessage();

        // Setup problems in the Firebase console (not the student's fault).
        if ("ERROR_OPERATION_NOT_ALLOWED".equals(code) || detail.contains("OPERATION_NOT_ALLOWED")
                || detail.contains("CONFIGURATION_NOT_FOUND")) {
            return "Email sign-in is not switched on for this app yet. In the Firebase console open "
                    + "Authentication > Sign-in method and enable Email/Password.";
        }
        if (detail.contains("API key") || detail.contains("API_KEY")) {
            return "This app's Firebase key was rejected. Download google-services.json again into the app folder.";
        }

        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Check your data or Wi-Fi and try again.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Sign in instead.";
        }
        // Weak-password must be checked before invalid-credentials (it is a subclass of it).
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "That password is too weak. Use at least 8 characters with letters and numbers.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No active account was found for this email.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        return detail.isEmpty() ? "Something went wrong. Please try again."
                : "Something went wrong: " + detail;
    }
}
