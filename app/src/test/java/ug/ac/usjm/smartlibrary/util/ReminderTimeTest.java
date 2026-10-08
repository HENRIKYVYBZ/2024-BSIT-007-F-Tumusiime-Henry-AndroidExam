package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

/** Unit tests for when pickup reminders go off. */
public class ReminderTimeTest {

    private static long localTime(int year, int month0, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month0, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void futurePickupIsRemindedAtEightOnThatDay() {
        long now = localTime(2026, Calendar.OCTOBER, 1, 15, 0);
        assertEquals(localTime(2026, Calendar.OCTOBER, 3, 8, 0), ReminderTime.at("2026-10-03", 8, now));
    }

    @Test
    public void pickupTodayAfterEightIsRemindedSoon() {
        long now = localTime(2026, Calendar.OCTOBER, 1, 15, 0);
        assertEquals(now + ReminderTime.LATE_DELAY_MS, ReminderTime.at("2026-10-01", 8, now));
    }

    @Test
    public void pickupTodayBeforeEightIsRemindedAtEight() {
        long now = localTime(2026, Calendar.OCTOBER, 1, 6, 30);
        assertEquals(localTime(2026, Calendar.OCTOBER, 1, 8, 0), ReminderTime.at("2026-10-01", 8, now));
    }

    @Test
    public void invalidDateGivesMinusOne() {
        assertEquals(-1, ReminderTime.at("2026-02-30", 8, 0));
    }
}
