package ug.ac.usjm.smartlibrary.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and opens the app's local SQLite database (smartlibrary.db).
 *
 * Tables:
 *   books        - the catalogue, with total and available copies
 *   reservations - books reserved by students for pickup at the library desk
 */
public class LibraryDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "smartlibrary.db";
    public static final int DB_VERSION = 1;

    public static final String T_BOOKS = "books";
    public static final String T_RESERVATIONS = "reservations";

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
        db.execSQL("CREATE TABLE " + T_RESERVATIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "book_id INTEGER NOT NULL REFERENCES " + T_BOOKS + "(id), "
                + "student_name TEXT NOT NULL, "
                + "reg_number TEXT NOT NULL, "
                + "pickup_date TEXT NOT NULL, "   // yyyy-MM-dd
                + "status TEXT NOT NULL DEFAULT 'ACTIVE', "
                + "created_at INTEGER NOT NULL)");
        seedBooks(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Prototype: rebuild the database when the schema changes.
        db.execSQL("DROP TABLE IF EXISTS " + T_RESERVATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + T_BOOKS);
        onCreate(db);
    }

    /** Sample catalogue so the app has data on first launch. */
    private void seedBooks(SQLiteDatabase db) {
        Object[][] books = {
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
        for (Object[] b : books) {
            ContentValues v = new ContentValues();
            v.put("title", (String) b[0]);
            v.put("author", (String) b[1]);
            v.put("category", (String) b[2]);
            v.put("shelf", (String) b[3]);
            v.put("year", (Integer) b[4]);
            v.put("description", (String) b[5]);
            v.put("total_copies", (Integer) b[6]);
            v.put("available_copies", (Integer) b[6]);
            db.insert(T_BOOKS, null, v);
        }
    }
}
