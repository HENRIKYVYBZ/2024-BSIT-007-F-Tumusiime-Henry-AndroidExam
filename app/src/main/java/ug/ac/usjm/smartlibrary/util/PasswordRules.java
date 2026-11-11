package ug.ac.usjm.smartlibrary.util;

/**
 * Live checks for the "Choose a new password" screen: which rules the typed password meets
 * and how strong it is. The final yes/no still comes from {@link Validator#newPasswordError}
 * and {@link Validator#confirmPasswordError}, so sign-up and reset use the same rules.
 */
public final class PasswordRules {

    public static final int WEAK = 1, FAIR = 2, GOOD = 3, STRONG = 4;

    private PasswordRules() {
    }

    public static boolean longEnough(String p) {
        return p != null && p.length() >= Validator.MIN_PASSWORD_LENGTH;
    }

    public static boolean hasLetter(String p) {
        return p != null && p.matches(".*[A-Za-z].*");
    }

    public static boolean hasNumber(String p) {
        return p != null && p.matches(".*\\d.*");
    }

    /** True only when something was typed in both boxes and they are identical. */
    public static boolean matches(String password, String confirm) {
        return password != null && !password.isEmpty() && password.equals(confirm);
    }

    /**
     * 0 = nothing typed, otherwise {@link #WEAK} to {@link #STRONG}. A password that breaks a rule
     * is always WEAK; after that, length and a mix of upper case and symbols raise the score.
     */
    public static int strength(String p) {
        if (p == null || p.isEmpty()) return 0;
        if (!longEnough(p) || !hasLetter(p) || !hasNumber(p)) return WEAK;
        int score = FAIR;
        boolean mixedCase = p.matches(".*[a-z].*") && p.matches(".*[A-Z].*");
        boolean symbol = p.matches(".*[^A-Za-z0-9].*");
        if (p.length() >= 12 || mixedCase || symbol) score = GOOD;
        if ((p.length() >= 12 && (mixedCase || symbol)) || (mixedCase && symbol)) score = STRONG;
        return score;
    }

    public static String strengthLabel(int strength) {
        switch (strength) {
            case WEAK: return "Weak";
            case FAIR: return "Fair";
            case GOOD: return "Good";
            case STRONG: return "Strong";
            default: return "";
        }
    }
}
