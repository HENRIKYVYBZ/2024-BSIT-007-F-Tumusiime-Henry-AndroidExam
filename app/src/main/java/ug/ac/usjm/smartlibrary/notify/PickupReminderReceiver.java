package ug.ac.usjm.smartlibrary.notify;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import ug.ac.usjm.smartlibrary.MyReservationsActivity;
import ug.ac.usjm.smartlibrary.R;

/**
 * Receives the alarm set by {@link PickupReminders} and shows the "collect your book" notification.
 * Not exported: only this app's own alarms can trigger it.
 */
public class PickupReminderReceiver extends BroadcastReceiver {

    static final String ACTION_REMIND = "ug.ac.usjm.smartlibrary.action.PICKUP_REMINDER";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REMIND.equals(intent.getAction())) return;

        long id = intent.getLongExtra(PickupReminders.EXTRA_RESERVATION_ID, -1);
        String title = intent.getStringExtra(PickupReminders.EXTRA_TITLE);
        String shelf = intent.getStringExtra(PickupReminders.EXTRA_SHELF);
        if (id < 0 || title == null) return;

        // Android 13+ needs the user's permission to show notifications.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        PickupReminders.createChannel(context);

        // Tapping the notification opens My reservations (with the catalogue behind it for Back).
        Intent open = new Intent(context, MyReservationsActivity.class);
        PendingIntent tap = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(open)
                .getPendingIntent((int) id, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder n = new NotificationCompat.Builder(context, PickupReminders.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(R.string.reminder_text, title))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.reminder_big_text, title, shelf)))
                .setColor(ContextCompat.getColor(context, R.color.lib_navy))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tap)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify((int) id, n.build());
    }
}
