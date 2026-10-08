package ug.ac.usjm.smartlibrary.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cleans up the library server address a user types, e.g. "192.168.1.10:8000". Plain Java, unit tested. */
public final class ServerAddress {

    private static final Pattern VALID = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$");

    private ServerAddress() {
    }

    /** @return e.g. "http://192.168.1.10:8000", or null if the text is not a usable address */
    public static String normalise(String typed) {
        if (typed == null) return null;
        String s = typed.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) s = "http://" + s;
        return VALID.matcher(s).matches() ? s : null;
    }

    /** The catalogue API on that server. */
    public static String booksUrl(String base) {
        return base + "/api/books/";
    }
}
