package ug.ac.usjm.smartlibrary.util;

import java.util.Locale;

/**
 * The text stored in a book's QR label, e.g. "usjm-library://book/7" for the book with id 7.
 * Plain Java so it can be unit tested.
 */
public final class QrPayload {

    public static final String PREFIX = "usjm-library://book/";

    private QrPayload() {
    }

    /** The QR text to print on the label of the book with this id. */
    public static String forBook(long bookId) {
        return PREFIX + bookId;
    }

    /** The book id in a scanned QR text, or -1 if it is not a USJM library book label. */
    public static long bookId(String scanned) {
        if (scanned == null) return -1;
        String s = scanned.trim();
        if (!s.toLowerCase(Locale.ROOT).startsWith(PREFIX)) return -1;
        String number = s.substring(PREFIX.length());
        if (number.endsWith("/")) number = number.substring(0, number.length() - 1);
        if (number.isEmpty() || number.length() > 9 || !number.matches("\\d+")) return -1;
        long id = Long.parseLong(number);
        return id > 0 ? id : -1;
    }
}
