package ug.ac.usjm.smartlibrary.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Turns stored dates (yyyy-MM-dd) into friendly text such as "Tue, 6 Oct 2026". */
public final class DateText {

    private DateText() {
    }

    public static String pretty(String iso) {
        Calendar c = Validator.parse(iso);
        if (c == null) return iso == null ? "" : iso;
        SimpleDateFormat f = new SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH);
        f.setTimeZone(c.getTimeZone());
        return f.format(c.getTime());
    }
}
