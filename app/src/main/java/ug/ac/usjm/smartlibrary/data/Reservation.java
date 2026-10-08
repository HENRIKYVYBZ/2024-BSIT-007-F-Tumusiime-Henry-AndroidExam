package ug.ac.usjm.smartlibrary.data;

import com.google.firebase.firestore.DocumentSnapshot;

import ug.ac.usjm.smartlibrary.util.Loans;

/**
 * One reservation, stored in Cloud Firestore (collection "reservations") so the person who made it
 * and the librarians all see the same record. Its life:
 * ACTIVE (copy held) -> COLLECTED (on loan, has a due date) -> RETURNED;
 * or ACTIVE -> CANCELLED (by the person) / NO_SHOW (by a librarian) / EXPIRED (pickup day passed).
 */
public class Reservation {
    public static final String ACTIVE = "ACTIVE";
    public static final String COLLECTED = "COLLECTED";
    public static final String RETURNED = "RETURNED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";
    public static final String EXPIRED = "EXPIRED";

    public final String id;            // Firestore document id
    public final long bookId;
    public final String bookTitle;
    public final String shelf;
    public final String userUid;
    public final String userName;
    public final String idNumber;      // registration number or staff ID
    public final String role;
    public final String pickupDate;    // yyyy-MM-dd
    public final String status;
    public final long createdAt;       // milliseconds since 1970
    public final String dueDate;       // yyyy-MM-dd, set when collected
    public final String handledBy;     // librarian who last changed it

    public Reservation(String id, long bookId, String bookTitle, String shelf, String userUid, String userName,
                       String idNumber, String role, String pickupDate, String status, long createdAt,
                       String dueDate, String handledBy) {
        this.id = id;
        this.bookId = bookId;
        this.bookTitle = bookTitle == null ? "" : bookTitle;
        this.shelf = shelf == null ? "" : shelf;
        this.userUid = userUid;
        this.userName = userName == null ? "" : userName;
        this.idNumber = idNumber == null ? "" : idNumber;
        this.role = role;
        this.pickupDate = pickupDate;
        this.status = status == null ? ACTIVE : status;
        this.createdAt = createdAt;
        this.dueDate = dueDate;
        this.handledBy = handledBy;
    }

    public static Reservation from(DocumentSnapshot d) {
        Long book = d.getLong("bookId");
        Long created = d.getLong("createdAt");
        return new Reservation(d.getId(), book == null ? -1 : book, d.getString("bookTitle"), d.getString("shelf"),
                d.getString("userUid"), d.getString("userName"), d.getString("idNumber"), d.getString("role"),
                d.getString("pickupDate"), d.getString("status"), created == null ? 0 : created,
                d.getString("dueDate"), d.getString("handledBy"));
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public boolean isOnLoan() {
        return COLLECTED.equals(status);
    }

    /** Short code shown to the person and read out at the desk. */
    public String code() {
        return Loans.shortCode(id);
    }

    /** Request code for this reservation's reminder alarm and notification. */
    public int alarmId() {
        return id.hashCode();
    }
}
