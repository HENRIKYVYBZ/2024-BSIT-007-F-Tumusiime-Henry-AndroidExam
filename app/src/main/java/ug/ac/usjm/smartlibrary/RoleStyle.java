package ug.ac.usjm.smartlibrary;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import java.util.Locale;

import ug.ac.usjm.smartlibrary.util.Roles;

/**
 * One colour per role, used for role badges and avatars so people recognise a role at a glance:
 * administrator gold, librarian teal, teaching staff purple, non-teaching staff blue, student light blue.
 * Accounts waiting for approval get a gold outline instead of a filled badge.
 */
public final class RoleStyle {

    private RoleStyle() {
    }

    /** Fill colour for the role. */
    public static int color(String role) {
        if (Roles.ADMIN.equals(role)) return Color.parseColor("#E0A526");
        if (Roles.LIBRARIAN.equals(role)) return Color.parseColor("#0E9F8E");
        if (Roles.TEACHING_STAFF.equals(role)) return Color.parseColor("#7C5CFC");
        if (Roles.NON_TEACHING_STAFF.equals(role)) return Color.parseColor("#3B82F6");
        return Color.parseColor("#DCE6F5");   // student
    }

    /** Text colour that reads well on {@link #color}. */
    public static int textColor(String role) {
        if (Roles.ADMIN.equals(role)) return Color.parseColor("#0B2545");
        if (Roles.STUDENT.equals(role) || !Roles.isKnown(role)) return Color.parseColor("#0B2545");
        return Color.WHITE;
    }

    /** Short symbol shown before the role name. */
    public static String symbol(String role) {
        if (Roles.ADMIN.equals(role)) return "★";
        if (Roles.LIBRARIAN.equals(role)) return "◆";
        if (Roles.TEACHING_STAFF.equals(role)) return "✎";
        if (Roles.NON_TEACHING_STAFF.equals(role)) return "●";
        return "●";
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /**
     * Turns a TextView into a role badge, e.g. "★ Administrator".
     *
     * @param waiting true for a staff account not yet approved: gold outline and "pending"
     */
    public static void applyBadge(TextView badge, String role, boolean waiting) {
        Context c = badge.getContext();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 12));
        if (waiting) {
            int gold = Color.parseColor("#E0A526");
            bg.setColor(Color.parseColor("#33E0A526"));
            bg.setStroke((int) dp(c, 1), gold);
            badge.setTextColor(gold);
            badge.setText(c.getString(R.string.role_waiting_badge, Roles.label(role)));
        } else {
            bg.setColor(color(role));
            badge.setTextColor(textColor(role));
            badge.setText(symbol(role) + "  " + Roles.label(role));
        }
        badge.setBackground(bg);
        int h = (int) dp(c, 9), v = (int) dp(c, 3);
        badge.setPadding(h, v, h, v);
    }

    /** Round avatar with the person's initials in their role colour. */
    public static void applyAvatar(TextView avatar, String fullName, String role) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color(role));
        avatar.setBackground(bg);
        avatar.setTextColor(textColor(role));
        avatar.setText(initials(fullName));
    }

    /** "Henry Tumusiime" -> "HT"; "Henry" -> "H"; empty -> "?". */
    static String initials(String fullName) {
        String n = fullName == null ? "" : fullName.trim();
        if (n.isEmpty()) return "?";
        String[] parts = n.split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
