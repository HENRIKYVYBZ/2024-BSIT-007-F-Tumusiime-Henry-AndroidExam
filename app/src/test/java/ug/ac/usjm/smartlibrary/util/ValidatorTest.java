package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for the sign-up, sign-in and reservation form rules. Run in Android Studio: right-click this file -> Run.
 * "Today" is fixed at Thursday 1 October 2026 so the results never depend on the real date.
 */
public class ValidatorTest {

    private static final String TODAY = "2026-10-01";   // a Thursday

    // ---- name
    @Test
    public void validFullNameIsAccepted() {
        assertNull(Validator.nameError("Henry Tumusiime"));
        assertNull(Validator.nameError("  Okot p'Bitek  "));
    }

    @Test
    public void emptyNameIsRejected() {
        assertEquals("Enter your full name", Validator.nameError("   "));
        assertEquals("Enter your full name", Validator.nameError(null));
    }

    @Test
    public void nameWithDigitsIsRejected() {
        assertNotNull(Validator.nameError("Henry 2023"));
    }

    @Test
    public void singleNameIsRejected() {
        assertEquals("Enter both your first name and surname", Validator.nameError("Henry"));
    }

    // ---- registration number
    @Test
    public void validRegNumbersAreAccepted() {
        assertNull(Validator.regNumberError("2023/BIT/0457"));
        assertNull(Validator.regNumberError("USJM/23/BIT/045"));
        assertNull(Validator.regNumberError(" 2023/bit/0457 "));   // trimmed and upper-cased
    }

    @Test
    public void badRegNumbersAreRejected() {
        assertNotNull(Validator.regNumberError(""));
        assertNotNull(Validator.regNumberError("BIT-2023-0457"));
        assertNotNull(Validator.regNumberError("2023/BIT/"));
    }

    @Test
    public void regNumberIsNormalised() {
        assertEquals("2023/BIT/0457", Validator.normaliseRegNumber("  2023/bit/0457 "));
    }

    // ---- email and password (sign up / sign in)
    @Test
    public void validEmailIsAccepted() {
        assertNull(Validator.emailError("henry.tumusiime@gmail.com"));
        assertNull(Validator.emailError("  student@usjm.ac.ug "));
    }

    @Test
    public void badEmailsAreRejected() {
        assertEquals("Enter your email address", Validator.emailError(""));
        assertNotNull(Validator.emailError("henry@gmail"));
        assertNotNull(Validator.emailError("henry.gmail.com"));
    }

    @Test
    public void strongPasswordIsAccepted() {
        assertNull(Validator.newPasswordError("Library2026"));
    }

    @Test
    public void weakPasswordsAreRejected() {
        assertEquals("Use at least 8 characters", Validator.newPasswordError("abc12"));
        assertEquals("Use both letters and numbers", Validator.newPasswordError("onlyletters"));
        assertEquals("Use both letters and numbers", Validator.newPasswordError("12345678"));
    }

    @Test
    public void passwordsMustMatch() {
        assertNull(Validator.confirmPasswordError("Library2026", "Library2026"));
        assertEquals("Passwords do not match", Validator.confirmPasswordError("Library2026", "library2026"));
    }

    @Test
    public void signInNeedsAPassword() {
        assertEquals("Enter your password", Validator.signInPasswordError(""));
        assertNull(Validator.signInPasswordError("anything"));
    }

    // ---- pickup date
    @Test
    public void pickupTodayOrWithinAWeekIsAccepted() {
        assertNull(Validator.pickupDateError("2026-10-01", TODAY));   // today
        assertNull(Validator.pickupDateError("2026-10-08", TODAY));   // exactly 7 days ahead
    }

    @Test
    public void missingPickupDateIsRejected() {
        assertEquals("Choose a pickup date", Validator.pickupDateError(null, TODAY));
    }

    @Test
    public void pastPickupDateIsRejected() {
        assertEquals("The pickup date cannot be in the past", Validator.pickupDateError("2026-09-30", TODAY));
    }

    @Test
    public void pickupMoreThanAWeekAheadIsRejected() {
        assertEquals("Choose a date within 7 days", Validator.pickupDateError("2026-10-09", TODAY));
    }

    @Test
    public void sundayPickupIsRejected() {
        assertEquals("The library is closed on Sundays", Validator.pickupDateError("2026-10-04", TODAY));
    }

    @Test
    public void impossibleDateIsRejected() {
        assertEquals("That is not a valid date", Validator.pickupDateError("2026-02-30", "2026-02-25"));
    }

    @Test
    public void datesAreFormattedForStorage() {
        assertEquals("2026-01-05", Validator.toIso(2026, 0, 5));   // month 0 = January
    }
}
