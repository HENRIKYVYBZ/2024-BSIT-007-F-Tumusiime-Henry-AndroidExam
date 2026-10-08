package ug.ac.usjm.smartlibrary.util;

import java.util.Calendar;

/** Works out when a pickup reminder should go off. Plain Java, so it is unit tested. */
public final class ReminderTime {

    /** If the reminder time has already passed (e.g. pickup is today), remind after this delay. */
    public static final long LATE_DELAY_MS = 10_000;

    private ReminderTime() {
    }

    /**
     * @param pickupDate yyyy-MM-dd
     * @param hour       hour of the day (0-23, phone's local time) for the reminder
     * @param now        current time in milliseconds
     * @return the reminder time in milliseconds, {@code now + LATE_DELAY_MS} if that time has
     *         passed, or -1 if the date is invalid
     */
    public static long at(String pickupDate, int hour, long now) {
        Calendar day = Validator.parse(pickupDate);
        if (day == null) return -1;
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hour, 0, 0);
        long at = local.getTimeInMillis();
        return at > now ? at : now + LATE_DELAY_MS;
    }
}
