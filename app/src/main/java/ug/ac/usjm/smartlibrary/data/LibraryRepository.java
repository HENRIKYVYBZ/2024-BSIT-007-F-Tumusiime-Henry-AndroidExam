package ug.ac.usjm.smartlibrary.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class the screens talk to for data. It hides the SQL from the screens.
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
}
