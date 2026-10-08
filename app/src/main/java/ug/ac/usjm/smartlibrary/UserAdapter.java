package ug.ac.usjm.smartlibrary;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ug.ac.usjm.smartlibrary.data.UserProfile;

/**
 * Shows each account as a card (layout: item_user.xml): initials avatar and badge in the role's colour,
 * status, email and ID. Staff waiting for approval get a gold border and Approve / Reject buttons.
 */
public class UserAdapter extends BaseAdapter {

    /** Lets the screen decide what Approve and Reject do. */
    public interface OnDecisionListener {
        void onApprove(UserProfile user);

        void onReject(UserProfile user);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final OnDecisionListener listener;
    private final List<UserProfile> items = new ArrayList<>();
    private final SimpleDateFormat joinedFormat = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);

    public UserAdapter(Context context, OnDecisionListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setUsers(List<UserProfile> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public UserProfile getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /** Remembers the views of one card so they are not looked up again while scrolling. */
    private static class ViewHolder {
        View card, actions, approve, reject;
        TextView avatar, name, status, role, details, joined;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_user, parent, false);
            h = new ViewHolder();
            h.card = convertView.findViewById(R.id.user_card);
            h.avatar = (TextView) convertView.findViewById(R.id.user_avatar);
            h.name = (TextView) convertView.findViewById(R.id.user_name);
            h.status = (TextView) convertView.findViewById(R.id.user_status);
            h.role = (TextView) convertView.findViewById(R.id.user_role);
            h.details = (TextView) convertView.findViewById(R.id.user_details);
            h.joined = (TextView) convertView.findViewById(R.id.user_joined);
            h.actions = convertView.findViewById(R.id.user_actions);
            h.approve = convertView.findViewById(R.id.btn_approve);
            h.reject = convertView.findViewById(R.id.btn_reject);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }
        final UserProfile u = getItem(position);
        boolean waiting = u.isWaitingForApproval() && !u.suspended;

        h.name.setText(u.fullName.isEmpty() ? context.getString(R.string.no_name) : u.fullName);
        RoleStyle.applyAvatar(h.avatar, u.fullName, u.role);
        RoleStyle.applyBadge(h.role, u.role, waiting);
        String id = u.idNumber.isEmpty() ? context.getString(R.string.no_id) : u.idNumber;
        h.details.setText(context.getString(R.string.user_details, u.email, id));
        h.joined.setText(u.createdAt > 0
                ? context.getString(R.string.joined_on, joinedFormat.format(new Date(u.createdAt))) : "");

        int label, bg, color;
        if (u.suspended) {
            label = R.string.status_suspended;
            bg = R.drawable.bg_badge_red;
            color = R.color.lib_red;
        } else if (waiting) {
            label = R.string.status_waiting;
            bg = R.drawable.bg_badge_gold;
            color = R.color.lib_notice;
        } else {
            label = R.string.status_active;
            bg = R.drawable.bg_badge_green;
            color = R.color.lib_green;
        }
        h.status.setText(label);
        h.status.setBackgroundResource(bg);
        h.status.setTextColor(ContextCompat.getColor(context, color));

        // Waiting staff stand out and can be approved or rejected in one tap.
        h.card.setBackgroundResource(waiting ? R.drawable.bg_card_waiting : R.drawable.bg_card);
        h.actions.setVisibility(waiting ? View.VISIBLE : View.GONE);
        h.approve.setOnClickListener(v -> listener.onApprove(u));
        h.reject.setOnClickListener(v -> listener.onReject(u));
        return convertView;
    }
}
