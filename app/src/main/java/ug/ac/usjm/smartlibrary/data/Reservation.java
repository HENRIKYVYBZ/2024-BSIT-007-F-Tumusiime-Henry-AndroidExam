package ug.ac.usjm.smartlibrary.data;

/** A book a student has reserved for pickup at the library's express counter. */
public class Reservation {
    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    public final long id;
    public final long bookId;
    public final String bookTitle;
    public final String shelf;
    public final String studentName;
    public final String regNumber;
    public final String pickupDate;   // yyyy-MM-dd
    public final String status;
    public final long createdAt;      // milliseconds since 1970

    public Reservation(long id, long bookId, String bookTitle, String shelf, String studentName,
                       String regNumber, String pickupDate, String status, long createdAt) {
        this.id = id;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.shelf = shelf;
        this.studentName = studentName;
        this.regNumber = regNumber;
        this.pickupDate = pickupDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
