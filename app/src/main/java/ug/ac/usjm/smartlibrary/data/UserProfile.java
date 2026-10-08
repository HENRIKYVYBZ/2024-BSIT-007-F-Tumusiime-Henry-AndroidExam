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
    public final boolean approved;   // false while a staff account waits for an administrator
    public final boolean suspended;  // set by an administrator; the person cannot use the app
    public final long createdAt;     // milliseconds since 1970

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

    /** A brand-new account: students are approved straight away, staff wait for an administrator. */
    public static UserProfile newAccount(String uid, String fullName, String email, String role, String idNumber) {
        return new UserProfile(uid, fullName, email, role, idNumber, !Roles.needsApproval(role), false,
                System.currentTimeMillis());
    }

    public static UserProfile from(DocumentSnapshot d) {
        Long created = d.getLong("createdAt");
        return new UserProfile(d.getId(), d.getString("fullName"), d.getString("email"), d.getString("role"),
                d.getString("idNumber"), Boolean.TRUE.equals(d.getBoolean("approved")),
                Boolean.TRUE.equals(d.getBoolean("suspended")), created == null ? 0 : created);
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
        return m;
    }

    public UserProfile withRole(String newRole, boolean nowApproved) {
        return new UserProfile(uid, fullName, email, newRole, idNumber, nowApproved, suspended, createdAt);
    }

    /** True for a staff account an administrator has not approved yet. */
    public boolean isWaitingForApproval() {
        return !approved && Roles.needsApproval(role);
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
