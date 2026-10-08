package ug.ac.usjm.smartlibrary;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ug.ac.usjm.smartlibrary.data.Reservation;
import ug.ac.usjm.smartlibrary.util.DateText;
import ug.ac.usjm.smartlibrary.util.Loans;
import ug.ac.usjm.smartlibrary.util.Validator;

/**
 * Shows each of the person's reservations as a card with its status (layout: item_reservation.xml):
 * waiting for pickup (with Cancel), on loan with its due date (red when overdue), or finished.
 */
public class ReservationAdapter extends BaseAdapter {

    /** Lets the screen decide what happens when Cancel is tapped. */
    public interface OnCancelClickListener {
        void onCancelClick(Reservation reservation);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final OnCancelClickListener listener;
    private final List<Reservation> items = new ArrayList<>();

    public ReservationAdapter(Context context, OnCancelClickListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setReservations(List<Reservation> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Reservation getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_reservation, parent, false);
        }
        final Reservation r = getItem(position);
        String today = Validator.today();

        ((TextView) convertView.findViewById(R.id.res_title)).setText(r.bookTitle);
        TextView when = (TextView) convertView.findViewById(R.id.res_pickup);
        when.setTextColor(ContextCompat.getColor(context, R.color.lib_text));
        if (r.isOnLoan() && r.dueDate != null) {
            int late = Loans.daysOverdue(r.dueDate, today);
            if (late > 0) {
                when.setText(context.getResources().getQuantityString(R.plurals.overdue_by, late, late,
                        DateText.pretty(r.dueDate)));
                when.setTextColor(ContextCompat.getColor(context, R.color.lib_red));
            } else {
                when.setText(context.getString(R.string.due_back_on, DateText.pretty(r.dueDate)));
            }
        } else {
            when.setText(context.getString(R.string.pickup_on, DateText.pretty(r.pickupDate), r.shelf));
        }
        ((TextView) convertView.findViewById(R.id.res_student)).setText(
                context.getString(R.string.reserved_by, r.userName, r.idNumber, r.code()));

        TextView status = (TextView) convertView.findViewById(R.id.res_status);
        int label, bg, color;
        if (r.isActive()) {
            label = R.string.status_active;
            bg = R.drawable.bg_badge_green;
            color = R.color.lib_green;
        } else if (r.isOnLoan()) {
            boolean late = r.dueDate != null && Loans.daysOverdue(r.dueDate, today) > 0;
            label = late ? R.string.status_overdue : R.string.status_on_loan;
            bg = late ? R.drawable.bg_badge_red : R.drawable.bg_badge_gold;
            color = late ? R.color.lib_red : R.color.lib_notice;
        } else if (Reservation.RETURNED.equals(r.status)) {
            label = R.string.status_returned;
            bg = R.drawable.bg_badge_grey;
            color = R.color.lib_muted;
        } else if (Reservation.EXPIRED.equals(r.status) || Reservation.NO_SHOW.equals(r.status)) {
            label = Reservation.NO_SHOW.equals(r.status) ? R.string.status_no_show : R.string.status_expired;
            bg = R.drawable.bg_badge_grey;
            color = R.color.lib_muted;
        } else {
            label = R.string.status_cancelled;
            bg = R.drawable.bg_badge_red;
            color = R.color.lib_red;
        }
        status.setText(label);
        status.setBackgroundResource(bg);
        status.setTextColor(ContextCompat.getColor(context, color));

        Button cancel = (Button) convertView.findViewById(R.id.res_cancel);
        cancel.setVisibility(r.isActive() ? View.VISIBLE : View.GONE);
        cancel.setOnClickListener(v -> listener.onCancelClick(r));
        return convertView;
    }
}
