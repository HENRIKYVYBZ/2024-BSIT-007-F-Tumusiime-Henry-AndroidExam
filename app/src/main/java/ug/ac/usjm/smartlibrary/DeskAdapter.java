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
 * Cards at the circulation desk (layout: item_desk.xml). Holds show "No-show" and "Collected";
 * loans show "Returned". Today's pickups are flagged TODAY, late loans show how many days overdue.
 */
public class DeskAdapter extends BaseAdapter {

    /** What the librarian tapped on a card. */
    public interface OnDeskAction {
        void onCollected(Reservation r);

        void onNoShow(Reservation r);

        void onReturned(Reservation r);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final OnDeskAction listener;
    private final List<Reservation> items = new ArrayList<>();

    public DeskAdapter(Context context, OnDeskAction listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setItems(List<Reservation> list) {
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

    private static class ViewHolder {
        TextView avatar, title, flag, person, role, when;
        Button secondary, primary;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_desk, parent, false);
            h = new ViewHolder();
            h.avatar = (TextView) convertView.findViewById(R.id.desk_avatar);
            h.title = (TextView) convertView.findViewById(R.id.desk_title);
            h.flag = (TextView) convertView.findViewById(R.id.desk_flag);
            h.person = (TextView) convertView.findViewById(R.id.desk_person);
            h.role = (TextView) convertView.findViewById(R.id.desk_role);
            h.when = (TextView) convertView.findViewById(R.id.desk_when);
            h.secondary = (Button) convertView.findViewById(R.id.btn_secondary);
            h.primary = (Button) convertView.findViewById(R.id.btn_primary);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }
        final Reservation r = getItem(position);
        String today = Validator.today();

        RoleStyle.applyAvatar(h.avatar, r.userName, r.role);
        RoleStyle.applyBadge(h.role, r.role, false);
        h.title.setText(r.bookTitle);
        h.person.setText(context.getString(R.string.desk_person, r.userName, r.idNumber, r.code()));
        h.when.setTextColor(ContextCompat.getColor(context, R.color.lib_muted));

        if (r.isActive()) {
            boolean isToday = today.equals(r.pickupDate);
            h.when.setText(context.getString(R.string.pickup_on, DateText.pretty(r.pickupDate), r.shelf));
            showFlag(h.flag, isToday ? R.string.flag_today : 0, R.drawable.bg_badge_gold, R.color.lib_notice);
            h.secondary.setVisibility(View.VISIBLE);
            h.secondary.setText(R.string.no_show);
            h.secondary.setOnClickListener(v -> listener.onNoShow(r));
            h.primary.setText(R.string.mark_collected);
            h.primary.setOnClickListener(v -> listener.onCollected(r));
        } else {
            int late = r.dueDate == null ? 0 : Loans.daysOverdue(r.dueDate, today);
            if (late > 0) {
                h.when.setText(context.getResources().getQuantityString(R.plurals.overdue_by, late, late,
                        DateText.pretty(r.dueDate)));
                h.when.setTextColor(ContextCompat.getColor(context, R.color.lib_red));
                showFlag(h.flag, R.string.status_overdue, R.drawable.bg_badge_red, R.color.lib_red);
            } else {
                h.when.setText(context.getString(R.string.due_back_on,
                        r.dueDate == null ? "" : DateText.pretty(r.dueDate)));
                showFlag(h.flag, 0, 0, 0);
            }
            h.secondary.setVisibility(View.GONE);
            h.primary.setText(R.string.mark_returned);
            h.primary.setOnClickListener(v -> listener.onReturned(r));
        }
        return convertView;
    }

    /** @param label 0 hides the flag */
    private void showFlag(TextView flag, int label, int bg, int color) {
        if (label == 0) {
            flag.setVisibility(View.GONE);
            return;
        }
        flag.setVisibility(View.VISIBLE);
        flag.setText(label);
        flag.setBackgroundResource(bg);
        flag.setTextColor(ContextCompat.getColor(context, color));
    }
}
