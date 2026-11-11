package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the live checklist and strength meter on "Choose a new password". */
public class PasswordRulesTest {

    @Test
    public void eachRuleIsCheckedOnItsOwn() {
        assertFalse(PasswordRules.longEnough("abc123"));
        assertTrue(PasswordRules.longEnough("abcd1234"));
        assertFalse(PasswordRules.hasLetter("12345678"));
        assertTrue(PasswordRules.hasLetter("1234567a"));
        assertFalse(PasswordRules.hasNumber("abcdefgh"));
        assertTrue(PasswordRules.hasNumber("abcdefg1"));
    }

    @Test
    public void confirmMustMatchExactly() {
        assertTrue(PasswordRules.matches("Library2026", "Library2026"));
        assertFalse(PasswordRules.matches("Library2026", "library2026"));   // case matters
        assertFalse(PasswordRules.matches("Library2026", "Library2026 "));  // a stray space is a mismatch
        assertFalse(PasswordRules.matches("", ""));                         // two empty boxes do not count
    }

    @Test
    public void strengthGrowsWithLengthAndVariety() {
        assertEquals(0, PasswordRules.strength(""));
        assertEquals(PasswordRules.WEAK, PasswordRules.strength("abc1"));          // too short
        assertEquals(PasswordRules.WEAK, PasswordRules.strength("abcdefghij"));    // no number
        assertEquals(PasswordRules.FAIR, PasswordRules.strength("library26"));
        assertEquals(PasswordRules.GOOD, PasswordRules.strength("Library26"));     // mixed case
        assertEquals(PasswordRules.STRONG, PasswordRules.strength("Library#2026"));
        assertEquals("Strong", PasswordRules.strengthLabel(PasswordRules.STRONG));
    }

    @Test
    public void meterAgreesWithTheValidator() {
        // Anything the meter rates above WEAK must also be accepted by the sign-up rules.
        for (String p : new String[]{"library26", "Library26", "Library#2026", "mbarara2026usjm"}) {
            assertTrue(p, PasswordRules.strength(p) > PasswordRules.WEAK);
            assertEquals(p, null, Validator.newPasswordError(p));
        }
    }
}
