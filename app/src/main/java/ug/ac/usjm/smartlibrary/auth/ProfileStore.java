package ug.ac.usjm.smartlibrary.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers each signed-in student's full name and registration number on the phone
 * (Firebase Authentication itself only stores the email, password and display name).
 */
public final class ProfileStore {

    private static final String PREFS = "student_profiles";

    private ProfileStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context c, String uid, String fullName, String regNumber) {
        prefs(c).edit()
                .putString(uid + ".name", fullName)
                .putString(uid + ".reg", regNumber)
                .apply();
    }

    /** @return the saved full name, or null if this phone has none for that account. */
    public static String name(Context c, String uid) {
        return prefs(c).getString(uid + ".name", null);
    }

    /** @return the saved registration number, or null. */
    public static String regNumber(Context c, String uid) {
        return prefs(c).getString(uid + ".reg", null);
    }
}
