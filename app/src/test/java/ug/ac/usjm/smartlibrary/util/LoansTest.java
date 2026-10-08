package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the circulation desk's date rules. */
public class LoansTest {

    @Test
    public void dueDateCountsLoanDaysFromCollection() {
        assertEquals("2026-10-22", Loans.dueDate("2026-10-08", 14));
        assertEquals("2026-11-07", Loans.dueDate("2026-10-08", 30));   // crosses into November
        assertEquals("2027-01-04", Loans.dueDate("2026-12-21", 14));   // crosses into the new year
    }

    @Test
    public void notOverdueUntilTheDayAfterTheDueDate() {
        assertEquals(0, Loans.daysOverdue("2026-10-22", "2026-10-20"));
        assertEquals(0, Loans.daysOverdue("2026-10-22", "2026-10-22"));
        assertEquals(1, Loans.daysOverdue("2026-10-22", "2026-10-23"));
        assertEquals(10, Loans.daysOverdue("2026-10-22", "2026-11-01"));
    }

    @Test
    public void missedPickupIsOnlyBeforeToday() {
        assertTrue(Loans.pickupMissed("2026-10-07", "2026-10-08"));
        assertFalse(Loans.pickupMissed("2026-10-08", "2026-10-08"));
        assertFalse(Loans.pickupMissed("2026-10-09", "2026-10-08"));
    }

    @Test
    public void shortCodeIsSixCapitals() {
        assertEquals("K3F9QZ", Loans.shortCode("k3f9qzAbCdEf123"));
        assertEquals("AB", Loans.shortCode("ab"));
        assertEquals("", Loans.shortCode(null));
    }

    @Test
    public void loanPeriodDependsOnRole() {
        assertEquals(14, Roles.loanDays(Roles.STUDENT, true));
        assertEquals(30, Roles.loanDays(Roles.TEACHING_STAFF, true));
        assertEquals(14, Roles.loanDays(Roles.TEACHING_STAFF, false));   // not approved yet
    }
}
