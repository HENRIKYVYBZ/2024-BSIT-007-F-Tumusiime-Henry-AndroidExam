package ug.ac.usjm.smartlibrary.data;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

import ug.ac.usjm.smartlibrary.util.Roles;

/** A library user's profile, stored in Firestore at users/{uid} (the uid comes from Firebase Authentication). */
public class UserProfile {
    public final String uid;
    public final String fullName;
    public final String email;
    public final String role;        // see Roles
    public final String idNumber;    // registration number (students) or staff ID
    public final boolean approved;   // true once the ID was found on the official list (or an admin approved it)
    public final boolean suspended;  // set by an administrator; the person cannot use the app
    public final long createdAt;     // milliseconds since 1970
    public String recoveryKey;       // recovery/{key} document; null until a recovery PIN is set
    public String rosterKey;         // roster/{key} entry this account claimed; null if approved by hand

    public UserProfile(String uid, String fullName, String email, String role, String idNumber,
                       boolean approved, boolean suspended, long createdAt) {
        this.uid = uid;
        this.fullName = fullName == null ? "" : fullName;
        this.email = email == null ? "" : email;
        this.role = Roles.isKnown(role) ? role : Roles.STUDENT;
        this.idNumber = idNumber == null ? "" : idNumber;
        this.approved = approved;
        this.suspended = suspended;
        this.createdAt = createdAt;
    }

    /**
     * A brand-new account, not approved yet. Sign-up first tries {@link #verifiedBy} (ID found on the
     * official list); only if that is refused is this unapproved profile saved.
     */
    public static UserProfile newAccount(String uid, String fullName, String email, String role, String idNumber) {
        return new UserProfile(uid, fullName, email, role, idNumber, false, false, System.currentTimeMillis());
    }

    /** The same account, approved because it claimed roster/{key}. */
    public UserProfile verifiedBy(String key) {
        UserProfile p = new UserProfile(uid, fullName, email, role, idNumber, true, suspended, createdAt);
        p.recoveryKey = recoveryKey;
        p.rosterKey = key;
        return p;
    }

    public static UserProfile from(DocumentSnapshot d) {
        Long created = d.getLong("createdAt");
        UserProfile p = new UserProfile(d.getId(), d.getString("fullName"), d.getString("email"), d.getString("role"),
                d.getString("idNumber"), Boolean.TRUE.equals(d.getBoolean("approved")),
                Boolean.TRUE.equals(d.getBoolean("suspended")), created == null ? 0 : created);
        p.recoveryKey = d.getString("recoveryKey");
        p.rosterKey = d.getString("rosterKey");
        return p;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", fullName);
        m.put("email", email);
        m.put("role", role);
        m.put("idNumber", idNumber);
        m.put("approved", approved);
        m.put("suspended", suspended);
        m.put("createdAt", createdAt);
        if (rosterKey != null) m.put("rosterKey", rosterKey);
        return m;
    }

    public UserProfile withRole(String newRole, boolean nowApproved) {
        UserProfile p = new UserProfile(uid, fullName, email, newRole, idNumber, nowApproved, suspended, createdAt);
        p.recoveryKey = recoveryKey;
        p.rosterKey = rosterKey;
        return p;
    }

    public boolean hasRecoveryPin() {
        return recoveryKey != null && !recoveryKey.isEmpty();
    }

    /** True while the ID has not been confirmed (not on the official list, and no administrator approval yet). */
    public boolean isWaitingForApproval() {
        return !approved;
    }

    /** The role whose limits and screens apply right now. */
    public String effectiveRole() {
        return Roles.effective(role, approved);
    }

    public String firstName() {
        String n = fullName.trim();
        return n.isEmpty() ? "" : n.split("\\s+")[0];
    }
}
