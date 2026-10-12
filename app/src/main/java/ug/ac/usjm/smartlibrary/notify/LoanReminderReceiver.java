package ug.ac.usjm.smartlibrary.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ug.ac.usjm.smartlibrary.R;

/** Shows "your book is due back today" when the due-date alarm goes off. Not exported. */
public class LoanReminderReceiver extends BroadcastReceiver {

    static final String ACTION_DUE = "ug.ac.usjm.smartlibrary.action.LOAN_DUE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_DUE.equals(intent.getAction())) return;
        String title = intent.getStringExtra(LoanReminders.EXTRA_TITLE);
        if (title == null) return;
        int id = intent.getIntExtra(LoanReminders.EXTRA_ALARM_ID, 0);
        LoanReminders.show(context, id, context.getString(R.string.return_today_text, title));
    }
}
