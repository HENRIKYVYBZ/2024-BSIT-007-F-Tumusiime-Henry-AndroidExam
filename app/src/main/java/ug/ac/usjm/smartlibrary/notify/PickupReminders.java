package ug.ac.usjm.smartlibrary.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;
import java.util.Map;

import ug.ac.usjm.smartlibrary.R;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.util.ReminderTime;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Schedules a "collect your book today" notification for 08:00 on each reservation's pickup date.
 * Uses AlarmManager (built into Android, works offline). When the alarm goes off,
 * {@link PickupReminderReceiver} shows the notification. Scheduled reminders are also remembered in
 * SharedPreferences so they can be put back after the phone restarts, even without internet.
 */
public final class PickupReminders {

    public static final String CHANNEL_ID = "pickup_reminders";
    static final String EXTRA_ALARM_ID = "alarm_id";
    private static final String PREFS = "pickup_reminders";
    private static final char SEP = '\u001F';   // separates the saved fields
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_SHELF = "shelf";

    /** Reminders go off at this hour (24-hour clock) on the pickup day. */
    public static final int REMINDER_HOUR = 8;

    private PickupReminders() {
    }

    /** Creates the notification channel users see in Settings (needed on Android 8+; safe to call often). */
    public static void createChannel(Context context) {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_pickup_name))
                .setDescription(context.getString(R.string.channel_pickup_description))
                .build();
        NotificationManagerCompat.from(context).createNotificationChannel(channel);
    }

    /**
     * Schedules the reminder for one reservation (calling it again just updates it). If 08:00 on the
     * pickup day has already passed (e.g. the pickup is today), the reminder comes after 10 seconds.
     */
    public static void schedule(Context context, Reservation r) {
        prefs(context).edit().putString(r.id, r.bookTitle + SEP + r.shelf + SEP + r.pickupDate).apply();
        schedule(context, r.alarmId(), r.bookTitle, r.shelf, r.pickupDate);
    }

    private static void schedule(Context context, int alarmId, String title, String shelf, String pickupDate) {
        long when = ReminderTime.at(pickupDate, REMINDER_HOUR, System.currentTimeMillis());
        if (when < 0) return;   // invalid date
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        // Inexact alarm: needs no special permission and saves battery; it may arrive a few minutes late.
        alarms.set(AlarmManager.RTC_WAKEUP, when, pendingIntent(context, alarmId, title, shelf));
    }

    /** Removes a reminder, e.g. when the reservation is cancelled or the book was collected. */
    public static void cancel(Context context, Reservation r) {
        if (!prefs(context).contains(r.id)) return;
        prefs(context).edit().remove(r.id).apply();
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pendingIntent(context, r.alarmId(), null, null));
        NotificationManagerCompat.from(context).cancel(r.alarmId());
    }

    /** Keeps reminders in step with the reservation: on while it is waiting for pickup, off otherwise. */
    public static void sync(Context context, Reservation r) {
        if (r.isActive() && r.pickupDate != null && r.pickupDate.compareTo(Validator.today()) >= 0) {
            if (!prefs(context).contains(r.id)) schedule(context, r);
        } else {
            cancel(context, r);
        }
    }

    /** Alarms are wiped when the phone restarts, so this puts back every saved reminder that is still due. */
    public static void rescheduleAll(Context context) {
        String today = Validator.today();
        SharedPreferences.Editor cleanup = prefs(context).edit();
        for (Map.Entry<String, ?> e : prefs(context).getAll().entrySet()) {
            String[] f = String.valueOf(e.getValue()).split(String.valueOf(SEP), -1);
            if (f.length != 3 || f[2].compareTo(today) < 0) {
                cleanup.remove(e.getKey());   // finished or unreadable
                continue;
            }
            // Pickup is today and 08:00 has passed: the reminder was already shown, so don't repeat it.
            if (f[2].equals(today) && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= REMINDER_HOUR) continue;
            schedule(context, e.getKey().hashCode(), f[0], f[1], f[2]);
        }
        cleanup.apply();
    }

    /** On sign-out: the next person using this phone must not get these reminders. */
    public static void clearAll(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String id : prefs(context).getAll().keySet()) {
            if (alarms != null) alarms.cancel(pendingIntent(context, id.hashCode(), null, null));
            NotificationManagerCompat.from(context).cancel(id.hashCode());
        }
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent pendingIntent(Context context, int alarmId, String title, String shelf) {
        Intent i = new Intent(context, PickupReminderReceiver.class);
        i.setAction(PickupReminderReceiver.ACTION_REMIND);
        i.putExtra(EXTRA_ALARM_ID, alarmId);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_SHELF, shelf);
        // One PendingIntent per reservation (request code = its alarm id), so each can be cancelled.
        return PendingIntent.getBroadcast(context, alarmId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
