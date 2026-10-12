package ug.ac.usjm.smartlibrary.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import ug.ac.usjm.smartlibrary.MyReservationsActivity;
import ug.ac.usjm.smartlibrary.R;
import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.util.DateText;
import ug.ac.usjm.smartlibrary.util.Loans;
import ug.ac.usjm.smartlibrary.util.ReminderTime;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Return reminders for books on loan:
 *  - an alarm at 08:00 on the due date ("due back today"), which works offline;
 *  - when the app opens: a notification for books due tomorrow, overdue books (once a day each),
 *    and books the librarian asked to have back.
 */
public final class LoanReminders {

    public static final String CHANNEL_ID = "return_reminders";
    static final String EXTRA_ALARM_ID = "alarm_id";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_DUE = "due";
    private static final String PREFS_ALARMS = "loan_alarms";
    private static final String PREFS_SHOWN = "loan_reminders_shown";
    private static final char SEP = '\u001F';

    private LoanReminders() {
    }

    public static void createChannel(Context context) {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_return_name))
                .setDescription(context.getString(R.string.channel_return_description))
                .build();
        NotificationManagerCompat.from(context).createNotificationChannel(channel);
    }

    private static int alarmId(String reservationId) {
        return ("loan:" + reservationId).hashCode();
    }

    /** Keeps the due-date alarm in step with the loan: on while it is out, off once returned. */
    public static void sync(Context context, Reservation r) {
        SharedPreferences prefs = prefs(context, PREFS_ALARMS);
        String today = Validator.today();
        if (r.isOnLoan() && r.dueDate != null && r.dueDate.compareTo(today) >= 0) {
            String value = r.bookTitle + SEP + r.dueDate;
            if (value.equals(prefs.getString(r.id, null))) return;   // already scheduled
            prefs.edit().putString(r.id, value).apply();
            schedule(context, alarmId(r.id), r.bookTitle, r.dueDate);
        } else if (prefs.contains(r.id)) {
            prefs.edit().remove(r.id).apply();
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.cancel(pendingIntent(context, alarmId(r.id), null, null));
        }
    }

    private static void schedule(Context context, int id, String title, String dueDate) {
        // Due today and 08:00 has passed: the "on open" check covers it instead.
        if (dueDate.equals(Validator.today())
                && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= PickupReminders.REMINDER_HOUR) return;
        long when = ReminderTime.at(dueDate, PickupReminders.REMINDER_HOUR, System.currentTimeMillis());
        if (when < 0) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.set(AlarmManager.RTC_WAKEUP, when, pendingIntent(context, id, title, dueDate));
    }

    /** After a restart: put back every due-date alarm that is still ahead. */
    public static void rescheduleAll(Context context) {
        String today = Validator.today();
        SharedPreferences prefs = prefs(context, PREFS_ALARMS);
        SharedPreferences.Editor cleanup = prefs.edit();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String[] f = String.valueOf(e.getValue()).split(String.valueOf(SEP), -1);
            if (f.length != 2 || f[1].compareTo(today) < 0) {
                cleanup.remove(e.getKey());
                continue;
            }
            schedule(context, alarmId(e.getKey()), f[0], f[1]);
        }
        cleanup.apply();
    }

    /** On sign-out: the next person on this phone must not get these reminders. */
    public static void clearAll(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String id : prefs(context, PREFS_ALARMS).getAll().keySet()) {
            if (alarms != null) alarms.cancel(pendingIntent(context, alarmId(id), null, null));
            NotificationManagerCompat.from(context).cancel(alarmId(id));
        }
        prefs(context, PREFS_ALARMS).edit().clear().apply();
        prefs(context, PREFS_SHOWN).edit().clear().apply();
    }

    /**
     * Called when the app opens with the person's reservations: shows at most one notification per
     * loan per day for books due tomorrow, overdue books and books the library asked for.
     */
    public static void check(Context context, List<Reservation> mine) {
        String today = Validator.today();
        String tomorrow = Loans.dueDate(today, 1);
        SharedPreferences shown = prefs(context, PREFS_SHOWN);
        for (Reservation r : mine) {
            if (!r.isOnLoan() || r.dueDate == null) continue;
            // Once a day per loan, plus once more if the librarian asks for it back later that day.
            String stamp = today + "|" + r.reminderSentOn;
            if (stamp.equals(shown.getString(r.id, null))) continue;
            int late = Loans.daysOverdue(r.dueDate, today);
            String text;
            if (r.reminderSentOn != null && late > 0) {
                text = context.getString(R.string.return_asked_text, r.bookTitle, DateText.pretty(r.dueDate));
            } else if (late > 0) {
                text = context.getResources().getQuantityString(R.plurals.return_overdue_text, late, r.bookTitle, late);
            } else if (r.dueDate.equals(tomorrow)) {
                text = context.getString(R.string.return_tomorrow_text, r.bookTitle);
            } else if (r.dueDate.equals(today)) {
                text = context.getString(R.string.return_today_text, r.bookTitle);
            } else {
                continue;
            }
            if (show(context, alarmId(r.id), text)) shown.edit().putString(r.id, stamp).apply();
        }
    }

    /** @return false when notifications are not allowed */
    static boolean show(Context context, int id, String text) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        createChannel(context);
        PendingIntent tap = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(new Intent(context, MyReservationsActivity.class))
                .getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.return_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setColor(ContextCompat.getColor(context, R.color.lib_navy))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tap)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(id, n.build());
        return true;
    }

    private static SharedPreferences prefs(Context c, String name) {
        return c.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static PendingIntent pendingIntent(Context context, int id, String title, String dueDate) {
        Intent i = new Intent(context, LoanReminderReceiver.class);
        i.setAction(LoanReminderReceiver.ACTION_DUE);
        i.putExtra(EXTRA_ALARM_ID, id);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_DUE, dueDate);
        return PendingIntent.getBroadcast(context, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
