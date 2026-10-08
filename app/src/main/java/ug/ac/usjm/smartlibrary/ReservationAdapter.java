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

/** Shows each reservation as a card with its status and a Cancel button (layout: item_reservation.xml). */
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
        return items.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_reservation, parent, false);
        }
        final Reservation r = getItem(position);

        ((TextView) convertView.findViewById(R.id.res_title)).setText(r.bookTitle);
        ((TextView) convertView.findViewById(R.id.res_pickup)).setText(
                context.getString(R.string.pickup_on, DateText.pretty(r.pickupDate), r.shelf));
        ((TextView) convertView.findViewById(R.id.res_student)).setText(
                context.getString(R.string.reserved_by, r.studentName, r.regNumber, r.id));

        TextView status = (TextView) convertView.findViewById(R.id.res_status);
        int label, bg, color;
        if (Reservation.ACTIVE.equals(r.status)) {
            label = R.string.status_active;
            bg = R.drawable.bg_badge_green;
            color = R.color.lib_green;
        } else if (Reservation.EXPIRED.equals(r.status)) {
            label = R.string.status_expired;
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
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onCancelClick(r);
            }
        });
        return convertView;
    }
}
