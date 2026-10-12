package ug.ac.usjm.smartlibrary.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Two-step account recovery for people who forget which email they signed up with.
 *
 * Step 1 is the registration number or staff ID, step 2 a 6-digit recovery PIN chosen at sign-up.
 * The account's email is stored in Firestore under recovery/{key}, where key = SHA-256 of both.
 * Neither the ID nor the PIN is stored: the record can only be found by someone who knows both,
 * so a classmate who knows your registration number can't look up your email.
 * Plain Java so it can be unit tested.
 */
public final class RecoveryKey {

    public static final int PIN_LENGTH = 6;

    private RecoveryKey() {
    }

    /** The Firestore document id for this ID number and PIN (64 hex characters). */
    public static String of(String idNumber, String pin) {
        String id = Validator.normaliseRegNumber(idNumber);
        String input = "usjm-smart-library|recovery|" + id + "|" + (pin == null ? "" : pin.trim());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on Android", e);
        }
    }

    /** Rules for a new PIN: exactly 6 digits, not all the same, not a simple run like 123456. */
    public static String pinError(String pin) {
        String p = pin == null ? "" : pin.trim();
        if (p.isEmpty()) return "Choose a 6-digit recovery PIN";
        if (!p.matches("\\d{" + PIN_LENGTH + "}")) return "The PIN must be exactly " + PIN_LENGTH + " digits";
        if (p.chars().distinct().count() == 1) return "Don't use the same digit six times";
        if ("0123456789".contains(p) || "9876543210".contains(p)) return "Too easy to guess. Avoid runs like 123456";
        return null;
    }

    public static String confirmPinError(String pin, String confirm) {
        if (confirm == null || confirm.trim().isEmpty()) return "Type the PIN again";
        if (!confirm.trim().equals(pin == null ? "" : pin.trim())) return "The PINs do not match";
        return null;
    }

    /** "henry.t@gmail.com" -> "he•••••@gmail.com": enough to recognise, not enough to copy. */
    public static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String name = email.substring(0, at);
        String keep = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return keep + "•••••" + email.substring(at);
    }
}
