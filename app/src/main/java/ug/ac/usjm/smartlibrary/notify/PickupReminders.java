package ug.ac.usjm.smartlibrary.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import ug.ac.usjm.smartlibrary.R;
import ug.ac.usjm.smartlibrary.data.LibraryRepository;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.util.ReminderTime;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Schedules a "collect your book today" notification for 08:00 on each reservation's pickup date.
 * Uses AlarmManager (built into Android, works offline). When the alarm goes off,
 * {@link PickupReminderReceiver} shows the notification.
 */
public final class PickupReminders {

    public static final String CHANNEL_ID = "pickup_reminders";
    static final String EXTRA_RESERVATION_ID = "reservation_id";
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
     * Schedules the reminder for one reservation. If 08:00 on the pickup day has already passed
     * (e.g. the pickup is today), the reminder comes after 10 seconds instead.
     */
    public static void schedule(Context context, long reservationId, String title, String shelf, String pickupDate) {
        long when = ReminderTime.at(pickupDate, REMINDER_HOUR, System.currentTimeMillis());
        if (when < 0) return;   // invalid date
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        // Inexact alarm: needs no special permission and saves battery; it may arrive a few minutes late.
        alarms.set(AlarmManager.RTC_WAKEUP, when, pendingIntent(context, reservationId, title, shelf));
    }

    /** Removes a scheduled reminder, e.g. when the reservation is cancelled. */
    public static void cancel(Context context, long reservationId) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pendingIntent(context, reservationId, null, null));
        NotificationManagerCompat.from(context).cancel((int) reservationId);
    }

    /** Alarms are wiped when the phone restarts, so this puts back one for every active reservation. */
    public static void rescheduleAll(Context context) {
        String today = Validator.today();
        List<Reservation> active = LibraryRepository.get(context).getActiveReservations(today);
        for (Reservation r : active) {
            schedule(context, r.id, r.bookTitle, r.shelf, r.pickupDate);
        }
    }

    private static PendingIntent pendingIntent(Context context, long reservationId, String title, String shelf) {
        Intent i = new Intent(context, PickupReminderReceiver.class);
        i.setAction(PickupReminderReceiver.ACTION_REMIND);
        i.putExtra(EXTRA_RESERVATION_ID, reservationId);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_SHELF, shelf);
        // One PendingIntent per reservation (request code = reservation id), so each can be cancelled.
        return PendingIntent.getBroadcast(context, (int) reservationId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
