package ug.ac.usjm.smartlibrary.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone's SQLite copy of the catalogue: fast search and offline browsing.
 * The shared catalogue and all reservations live in Cloud Firestore (see {@link CloudLibrary});
 * every change there is copied here with {@link #replaceCatalogue}.
 */
public class LibraryRepository {

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
        return query(selection, args, "title COLLATE NOCASE");
    }

    /** Books with at most one copy left on the shelf, emptiest first (the librarian's stock alerts). */
    public List<Book> getLowStock() {
        return query("available_copies <= 1", null, "available_copies, title COLLATE NOCASE");
    }

    private List<Book> query(String selection, String[] args, String orderBy) {
        List<Book> books = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.T_BOOKS, null, selection, args,
                null, null, orderBy);
        try {
            while (c.moveToNext()) {
                books.add(readBook(c));
            }
        } finally {
            c.close();
        }
        return books;
    }

    /** The book with this id, or null if there is none. */
    public Book getBook(long id) {
        Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.T_BOOKS, null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? readBook(c) : null;
        } finally {
            c.close();
        }
    }

    /**
     * Replaces the phone's catalogue with the shared one from Firestore, in one transaction
     * (so the list is never half-updated).
     */
    public void replaceCatalogue(List<Book> books) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(LibraryDbHelper.T_BOOKS, null, null);
            for (Book b : books) {
                ContentValues v = new ContentValues();
                v.put("id", b.id);
                v.put("title", b.title);
                v.put("author", b.author);
                v.put("category", b.category);
                v.put("shelf", b.shelf);
                v.put("year", b.year);
                v.put("description", b.description);
                v.put("total_copies", Math.max(0, b.totalCopies));
                v.put("available_copies", Math.max(0, Math.min(b.availableCopies, b.totalCopies)));
                db.insertOrThrow(LibraryDbHelper.T_BOOKS, null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
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
}
