package ug.ac.usjm.smartlibrary.util;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Checks the sign-up, sign-in and reservation forms. Plain Java (no Android classes) so it can be unit tested on the
 * computer with JUnit. Each method returns null when the value is fine, or a message to show.
 */
public final class Validator {

    /** How many days ahead a student may book a pickup. */
    public static final int MAX_PICKUP_DAYS_AHEAD = 7;

    /**
     * Registration number format, e.g. 2023/BIT/0457 or USJM/23/BIT/045.
     * Change this pattern if your university's format is different.
     */
    private static final Pattern REG_NUMBER =
            Pattern.compile("^([A-Z]{2,6}/)?\\d{2,4}/[A-Z]{1,6}/\\d{1,6}(/[A-Z]{1,3})?$");

    /** Letters (any language), spaces, apostrophes, hyphens and full stops, e.g. "Okot p'Bitek". */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'-]*[\\p{L}.]$");

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** name@domain.tld; deliberately simple, Firebase does the final check. */
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** Minimum password length for new accounts (Firebase itself requires 6). */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private Validator() {
    }

    public static String nameError(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "Enter your full name";
        if (n.length() < 3) return "Name is too short";
        if (n.length() > 60) return "Name must be 60 characters or fewer";
        if (!NAME.matcher(n).matches()) return "Use letters only (spaces, ' and - are allowed)";
        if (!n.contains(" ")) return "Enter both your first name and surname";
        return null;
    }

    /** Upper-cases and trims a registration number, so "2023/bit/0457 " becomes "2023/BIT/0457". */
    public static String normaliseRegNumber(String reg) {
        return reg == null ? "" : reg.trim().toUpperCase(Locale.ROOT);
    }

    public static String regNumberError(String reg) {
        String r = normaliseRegNumber(reg);
        if (r.isEmpty()) return "Enter your registration number";
        if (!REG_NUMBER.matcher(r).matches()) return "Use the format 2023/BIT/0457";
        return null;
    }

    // ------------------------------------------------------------- accounts

    public static String emailError(String email) {
        String e = email == null ? "" : email.trim();
        if (e.isEmpty()) return "Enter your email address";
        if (!EMAIL.matcher(e).matches()) return "Enter a valid email, e.g. name@gmail.com";
        return null;
    }

    /** Rules for a NEW password: at least 8 characters, with letters and numbers. */
    public static String newPasswordError(String password) {
        String p = password == null ? "" : password;
        if (p.isEmpty()) return "Choose a password";
        if (p.length() < MIN_PASSWORD_LENGTH) return "Use at least " + MIN_PASSWORD_LENGTH + " characters";
        if (!p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) return "Use both letters and numbers";
        return null;
    }

    public static String confirmPasswordError(String password, String confirm) {
        if (confirm == null || confirm.isEmpty()) return "Type your password again";
        if (!confirm.equals(password)) return "Passwords do not match";
        return null;
    }

    /** On the sign-in screen we only check that something was typed; Firebase checks the rest. */
    public static String signInPasswordError(String password) {
        return password == null || password.isEmpty() ? "Enter your password" : null;
    }

    // --------------------------------------------------------- reservations

    /**
     * The pickup date must be a real date from today up to {@link #MAX_PICKUP_DAYS_AHEAD} days ahead,
     * and not a Sunday (the library is closed).
     *
     * @param pickup the chosen date, yyyy-MM-dd
     * @param today  today's date, yyyy-MM-dd
     */
    public static String pickupDateError(String pickup, String today) {
        if (pickup == null || pickup.trim().isEmpty()) return "Choose a pickup date";
        Calendar p = parse(pickup);
        Calendar t = parse(today);
        if (p == null) return "That is not a valid date";
        if (t == null) throw new IllegalArgumentException("today must be yyyy-MM-dd: " + today);
        long days = daysBetween(t, p);
        if (days < 0) return "The pickup date cannot be in the past";
        if (days > MAX_PICKUP_DAYS_AHEAD) return "Choose a date within " + MAX_PICKUP_DAYS_AHEAD + " days";
        if (p.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) return "The library is closed on Sundays";
        return null;
    }

    // ------------------------------------------------------------- date helpers

    /** Today's date on this device as yyyy-MM-dd. */
    public static String today() {
        return toIso(Calendar.getInstance());
    }

    public static String toIso(Calendar c) {
        return toIso(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    /** @param month0 month counted from 0 (January = 0), as Calendar and DatePicker use. */
    public static String toIso(int year, int month0, int day) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month0 + 1, day);
    }

    /** Parses yyyy-MM-dd strictly (2026-02-30 is rejected); null if invalid. */
    public static Calendar parse(String iso) {
        if (iso == null || !ISO_DATE.matcher(iso.trim()).matches()) return null;
        String[] parts = iso.trim().split("-");
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.setLenient(false);
        c.clear();
        c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        try {
            c.getTimeInMillis();   // non-lenient calendars throw here for impossible dates
        } catch (IllegalArgumentException e) {
            return null;
        }
        return c;
    }

    private static long daysBetween(Calendar from, Calendar to) {
        return Math.round((to.getTimeInMillis() - from.getTimeInMillis()) / 86_400_000.0);
    }
}
