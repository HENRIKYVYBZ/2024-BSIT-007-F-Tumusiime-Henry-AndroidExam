package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Unit tests for two-step account recovery (ID number + recovery PIN). */
public class RecoveryKeyTest {

    @Test
    public void keyIsTheSameForTheSameIdAndPin() {
        // The ID is normalised, so "2023/bit/0457 " and "2023/BIT/0457" find the same account.
        assertEquals(RecoveryKey.of("2023/BIT/0457", "482913"), RecoveryKey.of(" 2023/bit/0457 ", "482913"));
        assertEquals(64, RecoveryKey.of("2023/BIT/0457", "482913").length());
    }

    @Test
    public void differentPinOrIdGivesADifferentKey() {
        String key = RecoveryKey.of("2023/BIT/0457", "482913");
        assertNotEquals(key, RecoveryKey.of("2023/BIT/0457", "482914"));
        assertNotEquals(key, RecoveryKey.of("2023/BIT/0458", "482913"));
    }

    @Test
    public void weakPinsAreRefused() {
        assertNull(RecoveryKey.pinError("482913"));
        assertNotNull(RecoveryKey.pinError(""));
        assertNotNull(RecoveryKey.pinError("12345"));
        assertNotNull(RecoveryKey.pinError("12a456"));
        assertNotNull(RecoveryKey.pinError("111111"));
        assertNotNull(RecoveryKey.pinError("123456"));
        assertNotNull(RecoveryKey.pinError("654321"));
        assertNotNull(RecoveryKey.confirmPinError("482913", "482931"));
        assertNull(RecoveryKey.confirmPinError("482913", "482913"));
    }

    @Test
    public void emailIsMasked() {
        assertEquals("he•••••@gmail.com", RecoveryKey.maskEmail("henry.t@gmail.com"));
        assertEquals("a•••••@usjm.ac.ug", RecoveryKey.maskEmail("ab@usjm.ac.ug"));
    }
}
