package ug.ac.usjm.smartlibrary.util;

/**
 * Checks the librarian's Add / Edit book form. Plain Java so it can be unit tested.
 * Each method returns null when the value is fine, or a message to show next to the field.
 */
public final class BookValidator {

    public static final int MAX_COPIES = 999;

    private BookValidator() {
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    public static String titleError(String title) {
        String t = clean(title);
        if (t.isEmpty()) return "Enter the book's title";
        if (t.length() < 2) return "Title is too short";
        if (t.length() > 150) return "Title must be 150 characters or fewer";
        return null;
    }

    public static String authorError(String author) {
        String a = clean(author);
        if (a.isEmpty()) return "Enter the author (or editors)";
        if (a.length() > 120) return "Author must be 120 characters or fewer";
        return null;
    }

    public static String categoryError(String category) {
        String c = clean(category);
        if (c.isEmpty()) return "Enter a subject, e.g. Computer Science";
        if (c.length() > 60) return "Subject must be 60 characters or fewer";
        return null;
    }

    public static String shelfError(String shelf) {
        String s = clean(shelf);
        if (s.isEmpty()) return "Enter where the book is kept, e.g. Level 1, Shelf A1";
        if (s.length() > 40) return "Shelf must be 40 characters or fewer";
        return null;
    }

    /** @param currentYear e.g. 2026; next year's books may already be catalogued */
    public static String yearError(String year, int currentYear) {
        String y = clean(year);
        if (y.isEmpty()) return "Enter the year it was published";
        int value;
        try {
            value = Integer.parseInt(y);
        } catch (NumberFormatException e) {
            return "Use numbers only, e.g. 2021";
        }
        if (value < 1450 || value > currentYear + 1) return "Enter a year between 1450 and " + (currentYear + 1);
        return null;
    }

    /**
     * @param copiesOut copies currently held or on loan: the total can't go below this
     */
    public static String copiesError(String copies, int copiesOut) {
        String c = clean(copies);
        if (c.isEmpty()) return "Enter how many copies the library has";
        int value;
        try {
            value = Integer.parseInt(c);
        } catch (NumberFormatException e) {
            return "Use a whole number, e.g. 3";
        }
        if (value < 1) return "A book needs at least 1 copy";
        if (value > MAX_COPIES) return "At most " + MAX_COPIES + " copies";
        if (value < copiesOut) {
            return copiesOut + (copiesOut == 1 ? " copy is" : " copies are")
                    + " reserved or on loan, so the total can't be less than " + copiesOut;
        }
        return null;
    }

    public static String descriptionError(String description) {
        return clean(description).length() > 1000 ? "Keep the description under 1000 characters" : null;
    }

    /** Readers are found by registration number or staff ID; either format is accepted at the desk. */
    public static String readerIdError(String id) {
        String r = Validator.normaliseRegNumber(id);
        if (r.isEmpty()) return "Enter the reader's registration number or staff ID";
        if (Validator.regNumberError(r) == null || Validator.staffIdError(r) == null) return null;
        return "That is not a registration number or staff ID";
    }
}
