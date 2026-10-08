package ug.ac.usjm.smartlibrary.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class the screens talk to for data. It hides the SQL and enforces the library rules:
 *  - a book can only be reserved while a copy is available;
 *  - a student (identified by their Firebase account) cannot reserve the same book twice;
 *  - a person can hold at most {@code maxActive} reservations at a time (3 for students, more for staff);
 *  - reserving takes one copy off the shelf count; cancelling or expiring puts it back.
 */
public class LibraryRepository {

    /** Default limit (students); other roles have their own, see Roles.maxActiveReservations. */
    public static final int MAX_ACTIVE_RESERVATIONS = 3;

    private static LibraryRepository instance;
    private final LibraryDbHelper helper;

    private LibraryRepository(Context context) {
        helper = new LibraryDbHelper(context.getApplicationContext());
    }

    /** One shared repository (and database connection) for the whole app. */
    public static synchronized LibraryRepository get(Context context) {
        if (instance == null) {
            instance = new LibraryRepository(context);
        }
        return instance;
    }

    // ------------------------------------------------------------------ books

    /** Books whose title, author or category contains {@code query}; all books when the query is empty. */
    public List<Book> getBooks(String query) {
        String q = query == null ? "" : query.trim();
        String selection = null;
        String[] args = null;
        if (!q.isEmpty()) {
            selection = "title LIKE ? OR author LIKE ? OR category LIKE ?";
            String like = "%" + q + "%";
            args = new String[]{like, like, like};
        }
        List<Book> books = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.T_BOOKS, null, selection, args,
                null, null, "title COLLATE NOCASE");
        try {
            while (c.moveToNext()) {
                books.add(readBook(c));
            }
        } finally {
            c.close();
        }
        return books;
    }

    /**
     * Saves the catalogue downloaded from the library web system (book ids are the server's ids).
     * Available copies = the server's count minus the copies reserved on this phone.
     * Books the server no longer lists are removed unless a reservation still refers to them.
     *
     * @param removeSampleData true on the first sync: the built-in sample books and their
     *                         reservations are cleared first
     * @return how many books were saved
     */
    public int replaceCatalogue(List<Book> serverBooks, boolean removeSampleData) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (removeSampleData) {
                db.delete(LibraryDbHelper.T_RESERVATIONS, null, null);
                db.delete(LibraryDbHelper.T_BOOKS, null, null);
            }
            StringBuilder keep = new StringBuilder();
            for (Book b : serverBooks) {
                int reservedHere = countActiveForBook(db, b.id);
                ContentValues v = new ContentValues();
                v.put("title", b.title);
                v.put("author", b.author);
                v.put("category", b.category);
                v.put("shelf", b.shelf);
                v.put("year", b.year);
                v.put("description", b.description);
                v.put("total_copies", Math.max(0, b.totalCopies));
                v.put("available_copies", Math.max(0, b.availableCopies - reservedHere));
                int updated = db.update(LibraryDbHelper.T_BOOKS, v, "id = ?", new String[]{String.valueOf(b.id)});
                if (updated == 0) {
                    v.put("id", b.id);
                    db.insertOrThrow(LibraryDbHelper.T_BOOKS, null, v);
                }
                if (keep.length() > 0) keep.append(',');
                keep.append(b.id);
            }
            if (keep.length() > 0) {
                db.execSQL("DELETE FROM " + LibraryDbHelper.T_BOOKS + " WHERE id NOT IN (" + keep
                        + ") AND id NOT IN (SELECT book_id FROM " + LibraryDbHelper.T_RESERVATIONS + ")");
            }
            db.setTransactionSuccessful();
            return serverBooks.size();
        } finally {
            db.endTransaction();
        }
    }

    private int countActiveForBook(SQLiteDatabase db, long bookId) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + LibraryDbHelper.T_RESERVATIONS
                + " WHERE book_id = ? AND status = ?", new String[]{String.valueOf(bookId), Reservation.ACTIVE});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** The book with this id, or null if there is none. */
    public Book getBook(long id) {
        return getBook(helper.getReadableDatabase(), id);
    }

    private Book getBook(SQLiteDatabase db, long id) {
        Cursor c = db.query(LibraryDbHelper.T_BOOKS, null, "id = ?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            return c.moveToFirst() ? readBook(c) : null;
        } finally {
            c.close();
        }
    }

    private static Book readBook(Cursor c) {
        return new Book(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("author")),
                c.getString(c.getColumnIndexOrThrow("category")),
                c.getString(c.getColumnIndexOrThrow("shelf")),
                c.getInt(c.getColumnIndexOrThrow("year")),
                c.getString(c.getColumnIndexOrThrow("description")),
                c.getInt(c.getColumnIndexOrThrow("total_copies")),
                c.getInt(c.getColumnIndexOrThrow("available_copies")));
    }

    // ----------------------------------------------------------- reservations

    /**
     * Reserves a copy of a book. Runs as one database transaction, so either everything is saved
     * or nothing is.
     *
     * @param maxActive how many active reservations this person's role allows
     * @return the new reservation's id
     * @throws ReservationException when a library rule does not allow the reservation
     */
    public long reserve(long bookId, String studentUid, String studentName, String regNumber,
                        String pickupDate, int maxActive) throws ReservationException {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Book book = getBook(db, bookId);
            if (book == null) {
                throw new ReservationException("This book is no longer in the catalogue.");
            }
            if (!book.isAvailable()) {
                throw new ReservationException("All copies of \"" + book.title
                        + "\" are out right now. Please try again later.");
            }
            if (countActive(db, studentUid, bookId) > 0) {
                throw new ReservationException("You already have an active reservation for this book.");
            }
            if (countActive(db, studentUid, -1) >= maxActive) {
                throw new ReservationException("You can hold at most " + maxActive
                        + " reservations at a time. Cancel one in My Reservations first.");
            }

            SQLiteStatement take = db.compileStatement("UPDATE " + LibraryDbHelper.T_BOOKS
                    + " SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0");
            take.bindLong(1, bookId);
            if (take.executeUpdateDelete() != 1) {
                throw new ReservationException("The last copy was just taken. Please try again later.");
            }

            ContentValues v = new ContentValues();
            v.put("book_id", bookId);
            v.put("student_uid", studentUid);
            v.put("student_name", studentName);
            v.put("reg_number", regNumber);
            v.put("pickup_date", pickupDate);
            v.put("status", Reservation.ACTIVE);
            v.put("created_at", System.currentTimeMillis());
            long id = db.insertOrThrow(LibraryDbHelper.T_RESERVATIONS, null, v);

            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    /** Active reservations for a student; for one book only when {@code bookId >= 0}. */
    private int countActive(SQLiteDatabase db, String studentUid, long bookId) {
        String sql = "SELECT COUNT(*) FROM " + LibraryDbHelper.T_RESERVATIONS
                + " WHERE student_uid = ? AND status = ?";
        String[] args;
        if (bookId >= 0) {
            sql += " AND book_id = ?";
            args = new String[]{studentUid, Reservation.ACTIVE, String.valueOf(bookId)};
        } else {
            args = new String[]{studentUid, Reservation.ACTIVE};
        }
        Cursor c = db.rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** The signed-in student's reservations: active ones first, then newest first. */
    public List<Reservation> getReservations(String studentUid) {
        List<Reservation> list = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT r.id, r.book_id, b.title, b.shelf, r.student_name, r.reg_number, r.pickup_date, "
                        + "r.status, r.created_at FROM " + LibraryDbHelper.T_RESERVATIONS + " r "
                        + "JOIN " + LibraryDbHelper.T_BOOKS + " b ON b.id = r.book_id "
                        + "WHERE r.student_uid = ? "
                        + "ORDER BY (r.status = 'ACTIVE') DESC, r.created_at DESC", new String[]{studentUid});
        try {
            while (c.moveToNext()) {
                list.add(new Reservation(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getLong(8)));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /**
     * Active reservations on this phone whose pickup date is today or later (all accounts).
     * Used to put pickup reminders back after the phone restarts.
     */
    public List<Reservation> getActiveReservations(String today) {
        List<Reservation> list = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT r.id, r.book_id, b.title, b.shelf, r.student_name, r.reg_number, r.pickup_date, "
                        + "r.status, r.created_at FROM " + LibraryDbHelper.T_RESERVATIONS + " r "
                        + "JOIN " + LibraryDbHelper.T_BOOKS + " b ON b.id = r.book_id "
                        + "WHERE r.status = 'ACTIVE' AND r.pickup_date >= ?", new String[]{today});
        try {
            while (c.moveToNext()) {
                list.add(new Reservation(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getLong(8)));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Cancels an active reservation and returns its copy to the shelf. @return true if cancelled. */
    public boolean cancelReservation(long reservationId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            SQLiteStatement cancel = db.compileStatement("UPDATE " + LibraryDbHelper.T_RESERVATIONS
                    + " SET status = ? WHERE id = ? AND status = ?");
            cancel.bindString(1, Reservation.CANCELLED);
            cancel.bindLong(2, reservationId);
            cancel.bindString(3, Reservation.ACTIVE);
            if (cancel.executeUpdateDelete() != 1) {
                return false;   // already cancelled or expired
            }
            db.execSQL("UPDATE " + LibraryDbHelper.T_BOOKS + " SET available_copies = available_copies + 1 "
                    + "WHERE id = (SELECT book_id FROM " + LibraryDbHelper.T_RESERVATIONS + " WHERE id = ?)",
                    new Object[]{reservationId});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Holds not collected by their pickup date expire, and their copies go back on the shelf.
     *
     * @param today today's date as yyyy-MM-dd
     * @return how many reservations expired
     */
    public int expireUncollected(String today) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE " + LibraryDbHelper.T_BOOKS + " SET available_copies = available_copies + "
                    + "(SELECT COUNT(*) FROM " + LibraryDbHelper.T_RESERVATIONS + " r WHERE r.book_id = "
                    + LibraryDbHelper.T_BOOKS + ".id AND r.status = 'ACTIVE' AND r.pickup_date < ?)",
                    new Object[]{today});
            SQLiteStatement expire = db.compileStatement("UPDATE " + LibraryDbHelper.T_RESERVATIONS
                    + " SET status = ? WHERE status = ? AND pickup_date < ?");
            expire.bindString(1, Reservation.EXPIRED);
            expire.bindString(2, Reservation.ACTIVE);
            expire.bindString(3, today);
            int expired = expire.executeUpdateDelete();
            db.setTransactionSuccessful();
            return expired;
        } finally {
            db.endTransaction();
        }
    }
}
