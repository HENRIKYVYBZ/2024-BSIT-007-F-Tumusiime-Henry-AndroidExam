package ug.ac.usjm.smartlibrary.util;

/**
 * The five kinds of library user and what each may do. Plain Java (no Android classes) so it can be
 * unit tested. The role is stored in Firestore (users/{uid}.role); the Firestore security rules
 * enforce the same rules on the server, so changing this file alone cannot give anyone more rights.
 *
 * Staff accounts start "waiting for approval": until an administrator approves them they have
 * student limits.
 */
public final class Roles {

    public static final String STUDENT = "student";
    public static final String TEACHING_STAFF = "teaching_staff";
    public static final String NON_TEACHING_STAFF = "non_teaching_staff";
    public static final String LIBRARIAN = "librarian";
    public static final String ADMIN = "admin";

    /** Every role, in the order shown to administrators. */
    public static final String[] ALL = {STUDENT, TEACHING_STAFF, NON_TEACHING_STAFF, LIBRARIAN, ADMIN};

    /** Roles a person may pick when creating their own account. Librarian and admin are granted only. */
    public static final String[] SELF_SIGN_UP = {STUDENT, TEACHING_STAFF, NON_TEACHING_STAFF};

    private Roles() {
    }

    public static boolean isKnown(String role) {
        for (String r : ALL) {
            if (r.equals(role)) return true;
        }
        return false;
    }

    public static boolean canSelfSignUp(String role) {
        for (String r : SELF_SIGN_UP) {
            if (r.equals(role)) return true;
        }
        return false;
    }

    /** Students are approved at once; every other role needs an administrator's approval. */
    public static boolean needsApproval(String role) {
        return !STUDENT.equals(role);
    }

    /** Students identify with a registration number; everyone else with a staff ID. */
    public static boolean usesRegNumber(String role) {
        return STUDENT.equals(role);
    }

    /** The role whose limits apply: staff waiting for approval are treated as students. */
    public static String effective(String role, boolean approved) {
        if (!isKnown(role)) return STUDENT;
        return approved ? role : STUDENT;
    }

    public static String label(String role) {
        if (TEACHING_STAFF.equals(role)) return "Teaching staff";
        if (NON_TEACHING_STAFF.equals(role)) return "Non-teaching staff";
        if (LIBRARIAN.equals(role)) return "Librarian";
        if (ADMIN.equals(role)) return "Administrator";
        return "Student";
    }

    /** How many books a person may have reserved at the same time. */
    public static int maxActiveReservations(String role, boolean approved) {
        String r = effective(role, approved);
        if (TEACHING_STAFF.equals(r)) return 10;
        if (STUDENT.equals(r)) return 3;
        return 5;   // non-teaching staff, librarians, administrators
    }

    /** How many days ahead a person may book a pickup. */
    public static int maxPickupDaysAhead(String role, boolean approved) {
        String r = effective(role, approved);
        if (TEACHING_STAFF.equals(r) || LIBRARIAN.equals(r) || ADMIN.equals(r)) return 14;
        return 7;
    }

    /** How many days a collected book may be kept: 14 for students, 30 for staff. */
    public static int loanDays(String role, boolean approved) {
        return STUDENT.equals(effective(role, approved)) ? 14 : 30;
    }

    /** Librarians (and administrators) run the circulation desk. */
    public static boolean canRunDesk(String role, boolean approved) {
        String r = effective(role, approved);
        return LIBRARIAN.equals(r) || ADMIN.equals(r);
    }

    /** Only administrators approve accounts and change roles. */
    public static boolean canManageUsers(String role, boolean approved) {
        return ADMIN.equals(effective(role, approved));
    }

    /** Lecturers recommend books for purchase and publish course reading lists. */
    public static boolean canRecommend(String role, boolean approved) {
        String r = effective(role, approved);
        return TEACHING_STAFF.equals(r) || ADMIN.equals(r);
    }
}
