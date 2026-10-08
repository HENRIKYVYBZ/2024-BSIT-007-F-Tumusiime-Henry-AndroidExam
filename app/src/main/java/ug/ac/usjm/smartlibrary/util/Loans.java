package ug.ac.usjm.smartlibrary.util;

import java.util.Calendar;
import java.util.Locale;

/**
 * Date rules for the circulation desk: due dates, overdue days and short reservation codes.
 * Plain Java (no Android classes) so it can be unit tested. Dates are yyyy-MM-dd strings.
 */
public final class Loans {

    private Loans() {
    }

    /** The day a book is due back: {@code days} after it was collected. */
    public static String dueDate(String collectedIso, int days) {
        Calendar c = Validator.parse(collectedIso);
        if (c == null) throw new IllegalArgumentException("not a yyyy-MM-dd date: " + collectedIso);
        c.add(Calendar.DAY_OF_MONTH, days);
        return Validator.toIso(c);
    }

    /** How many days late a loan is today; 0 when it is not late yet (or on the due day itself). */
    public static int daysOverdue(String dueIso, String todayIso) {
        Calendar due = Validator.parse(dueIso);
        Calendar today = Validator.parse(todayIso);
        if (due == null || today == null) return 0;
        long days = Math.round((today.getTimeInMillis() - due.getTimeInMillis()) / 86_400_000.0);
        return days > 0 ? (int) days : 0;
    }

    /** A hold whose pickup day has passed without being collected. */
    public static boolean pickupMissed(String pickupIso, String todayIso) {
        return pickupIso != null && todayIso != null && pickupIso.compareTo(todayIso) < 0;
    }

    /** First 6 characters of a Firestore document id in capitals, e.g. "K3F9QZ", read out at the desk. */
    public static String shortCode(String documentId) {
        if (documentId == null || documentId.isEmpty()) return "";
        String c = documentId.length() > 6 ? documentId.substring(0, 6) : documentId;
        return c.toUpperCase(Locale.ROOT);
    }
}
