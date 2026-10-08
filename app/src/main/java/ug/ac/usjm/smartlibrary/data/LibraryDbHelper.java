package ug.ac.usjm.smartlibrary.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates and opens the app's local SQLite database (smartlibrary.db).
 *
 * Table:
 *   books - the phone's copy of the library catalogue, for fast search and offline browsing.
 *           It mirrors the shared catalogue in Cloud Firestore (or holds the sample books until a
 *           librarian publishes one). Reservations live in Firestore so librarians can see them.
 */
public class LibraryDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "smartlibrary.db";
    // Version 2 linked reservations to the Firebase account; version 3 moved reservations to Firestore.
    public static final int DB_VERSION = 3;

    public static final String T_BOOKS = "books";
    /** Old table (versions 1-2), dropped on upgrade. */
    private static final String T_OLD_RESERVATIONS = "reservations";

    public LibraryDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_BOOKS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "author TEXT NOT NULL, "
                + "category TEXT NOT NULL, "
                + "shelf TEXT NOT NULL, "
                + "year INTEGER NOT NULL, "
                + "description TEXT NOT NULL, "
                + "total_copies INTEGER NOT NULL CHECK (total_copies >= 0), "
                + "available_copies INTEGER NOT NULL CHECK (available_copies >= 0))");
        seedBooks(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Prototype: rebuild the database when the schema changes.
        db.execSQL("DROP TABLE IF EXISTS " + T_OLD_RESERVATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + T_BOOKS);
        onCreate(db);
    }

    /** Sample catalogue so the app has data on first launch (and for the librarian to publish). */
    private static final Object[][] SAMPLE = {
                {"Introduction to Algorithms", "Cormen, Leiserson, Rivest & Stein", "Computer Science", "Level 1, Shelf A1", 2022,
                        "The standard reference on designing and analysing algorithms.", 3},
                {"Database System Concepts", "Silberschatz, Korth & Sudarshan", "Computer Science", "Level 1, Shelf A6", 2019,
                        "Relational databases, SQL, database design and transactions.", 4},
                {"Head First Java", "Kathy Sierra, Bert Bates & Trisha Gee", "Programming", "Level 1, Shelf A4", 2022,
                        "A visual, hands-on guide to Java and object-oriented thinking.", 3},
                {"Android Programming: The Big Nerd Ranch Guide", "Bill Phillips et al.", "Programming", "Level 1, Shelf A5", 2019,
                        "Building Android apps step by step: activities, layouts, data and more.", 2},
                {"Python Crash Course", "Eric Matthes", "Programming", "Level 1, Shelf A3", 2023,
                        "A fast, project-based introduction to programming in Python.", 5},
                {"Software Engineering", "Ian Sommerville", "Information Technology", "Level 1, Shelf B1", 2015,
                        "Requirements, design, agile methods, testing and software evolution.", 4},
                {"Human-Computer Interaction", "Dix, Finlay, Abowd & Beale", "Information Technology", "Level 1, Shelf B2", 2004,
                        "Theory and practice of designing usable interactive systems.", 2},
                {"Computer Networking: A Top-Down Approach", "James Kurose & Keith Ross", "Networking", "Level 2, Shelf C1", 2021,
                        "Computer networks explained from the application layer down.", 3},
                {"Cryptography and Network Security", "William Stallings", "Networking", "Level 2, Shelf C2", 2017,
                        "Encryption, authentication and secure network protocols.", 2},
                {"Artificial Intelligence: A Modern Approach", "Stuart Russell & Peter Norvig", "Data Science & AI", "Level 2, Shelf D1", 2020,
                        "Agents, search, knowledge, uncertainty and machine learning.", 2},
                {"Research Methodology: Methods and Techniques", "C. R. Kothari", "Research Methods", "Level 3, Shelf E1", 2004,
                        "Research design, sampling, data collection and analysis.", 5},
                {"Principles of Marketing", "Philip Kotler & Gary Armstrong", "Business", "Level 3, Shelf F1", 2020,
                        "Creating customer value through marketing strategy and branding.", 3},
                {"Financial Accounting", "Weygandt, Kimmel & Kieso", "Business", "Level 3, Shelf F2", 2018,
                        "Recording transactions and preparing financial statements.", 4},
                {"Educational Psychology", "Anita Woolfolk", "Education", "Level 3, Shelf G1", 2019,
                        "How students learn and develop, and what it means for teaching.", 3},
                {"Song of Lawino", "Okot p'Bitek", "General Collection", "Level 1, Shelf H1", 1966,
                        "The celebrated Ugandan poem on tradition and change.", 2},
        };

    /** The sample books with ids 1, 2, 3 ... and every copy on the shelf. */
    public static List<Book> sampleBooks() {
        List<Book> list = new ArrayList<>();
        for (int i = 0; i < SAMPLE.length; i++) {
            Object[] b = SAMPLE[i];
            list.add(new Book(i + 1, (String) b[0], (String) b[1], (String) b[2], (String) b[3], (Integer) b[4],
                    (String) b[5], (Integer) b[6], (Integer) b[6]));
        }
        return list;
    }

    private void seedBooks(SQLiteDatabase db) {
        for (Book b : sampleBooks()) {
            ContentValues v = new ContentValues();
            v.put("id", b.id);
            v.put("title", b.title);
            v.put("author", b.author);
            v.put("category", b.category);
            v.put("shelf", b.shelf);
            v.put("year", b.year);
            v.put("description", b.description);
            v.put("total_copies", b.totalCopies);
            v.put("available_copies", b.availableCopies);
            db.insert(T_BOOKS, null, v);
        }
    }
}
