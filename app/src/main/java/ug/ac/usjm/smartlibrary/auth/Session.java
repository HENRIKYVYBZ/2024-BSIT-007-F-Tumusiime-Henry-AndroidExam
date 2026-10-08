package ug.ac.usjm.smartlibrary.auth;

import android.content.Context;
import android.content.SharedPreferences;

import ug.ac.usjm.smartlibrary.data.UserProfile;

/**
 * The signed-in person's profile (name, role, ID number), kept in memory and in SharedPreferences
 * so the right screens and limits are known straight away, even offline.
 * The copy in Firestore is the real one; MainActivity refreshes this on every launch.
 */
public final class Session {

    private static final String PREFS = "session_profile";
    private static UserProfile current;

    private Session() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void set(Context c, UserProfile p) {
        current = p;
        prefs(c).edit()
                .putString("uid", p.uid)
                .putString("name", p.fullName)
                .putString("email", p.email)
                .putString("role", p.role)
                .putString("id_number", p.idNumber)
                .putBoolean("approved", p.approved)
                .putBoolean("suspended", p.suspended)
                .putLong("created_at", p.createdAt)
                .apply();
        ProfileStore.save(c, p.uid, p.fullName, p.idNumber);
    }

    /** The saved profile for this account, or null if this phone has none yet. */
    public static synchronized UserProfile get(Context c, String uid) {
        if (current != null && current.uid.equals(uid)) return current;
        SharedPreferences sp = prefs(c);
        if (!uid.equals(sp.getString("uid", null))) return null;
        current = new UserProfile(uid, sp.getString("name", ""), sp.getString("email", ""),
                sp.getString("role", null), sp.getString("id_number", ""), sp.getBoolean("approved", false),
                sp.getBoolean("suspended", false), sp.getLong("created_at", 0));
        return current;
    }

    /** Called on sign-out. */
    public static synchronized void clear(Context c) {
        current = null;
        prefs(c).edit().clear().apply();
    }
}
