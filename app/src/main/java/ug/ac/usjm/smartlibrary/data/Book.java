package ug.ac.usjm.smartlibrary.data;

/** A book (title) in the USJM library catalogue. */
public class Book {
    public final long id;
    public final String title;
    public final String author;
    public final String category;
    public final String shelf;
    public final int year;
    public final String description;
    public final int totalCopies;
    public final int availableCopies;

    public Book(long id, String title, String author, String category, String shelf, int year,
                String description, int totalCopies, int availableCopies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.shelf = shelf;
        this.year = year;
        this.description = description;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public boolean isAvailable() {
        return availableCopies > 0;
    }

    /** e.g. "2 of 3 available" or "All copies out". */
    public String availabilityText() {
        return isAvailable() ? availableCopies + " of " + totalCopies + " available" : "All copies out";
    }
}
