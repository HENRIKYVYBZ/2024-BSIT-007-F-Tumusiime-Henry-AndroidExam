package ug.ac.usjm.smartlibrary.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the university's official ID list from a CSV file (exported from the records office or
 * typed in Excel and saved as CSV). One person per line:
 *
 *   id, role, name
 *   2024/BSIT/007/F, student, Tumusiime Henry
 *   ST-0123, teaching staff, Dr. Jane Akello
 *
 * The role column accepts everyday words (student, lecturer, teaching, non-teaching, support...).
 * The name is optional. A first line that does not look like data (e.g. "id,role,name") is skipped
 * as a header. Commas or semicolons both work (Excel uses ";" in some regions).
 * Plain Java so it can be unit tested.
 */
public final class RosterCsv {

    /** One person on the list. */
    public static final class Entry {
        public final String id;      // normalised, e.g. 2024/BSIT/007/F
        public final String key;     // Firestore document ID, see RosterKey
        public final String role;    // Roles.STUDENT, TEACHING_STAFF or NON_TEACHING_STAFF
        public final String name;    // may be empty

        Entry(String id, String role, String name) {
            this.id = id;
            this.key = RosterKey.of(id);
            this.role = role;
            this.name = name;
        }
    }

    /** What was read: the good lines, and a short note for every line that was skipped. */
    public static final class Result {
        public final List<Entry> entries = new ArrayList<>();
        public final List<String> problems = new ArrayList<>();
        public int students, teachingStaff, nonTeachingStaff;
    }

    private RosterCsv() {
    }

    public static Result parse(String text) {
        Result result = new Result();
        if (text == null) return result;
        if (text.startsWith("﻿")) text = text.substring(1);   // byte-order mark from Excel

        String[] lines = text.split("\r\n|\r|\n");
        Set<String> seen = new HashSet<>();
        char sep = separatorOf(lines);
        boolean firstDataLine = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] cols = split(line, sep);
            String id = Validator.normaliseRegNumber(cols.length > 0 ? cols[0] : "");
            String roleText = cols.length > 1 ? cols[1] : "";
            String name = cols.length > 2 ? cols[2].trim() : "";

            String role = roleFrom(roleText);
            if (role == null && roleText.trim().isEmpty() && Validator.regNumberError(id) == null) {
                role = Roles.STUDENT;   // no role given, but it is clearly a registration number
            }
            if (firstDataLine && role == null && looksLikeHeader(cols)) {
                firstDataLine = false;
                continue;
            }
            firstDataLine = false;

            int lineNo = i + 1;
            if (role == null) {
                result.problems.add("Line " + lineNo + ": unknown role \"" + roleText.trim() + "\"");
                continue;
            }
            String idError = Validator.idNumberError(role, id);
            if (idError != null) {
                result.problems.add("Line " + lineNo + ": " + (id.isEmpty() ? "no ID" : id) + " - " + idError);
                continue;
            }
            Entry e = new Entry(id, role, name);
            if (!seen.add(e.key)) {
                result.problems.add("Line " + lineNo + ": " + id + " appears twice; the first one is kept");
                continue;
            }
            result.entries.add(e);
            if (Roles.STUDENT.equals(role)) result.students++;
            else if (Roles.TEACHING_STAFF.equals(role)) result.teachingStaff++;
            else result.nonTeachingStaff++;
        }
        return result;
    }

    /** Turns the words people actually write in the role column into one of the three self-sign-up roles. */
    public static String roleFrom(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        if (t.isEmpty()) return null;
        if (t.startsWith("non") || t.contains("support") || t.contains("admin staff")
                || t.equals("staff") || t.contains("administrative")) {
            return Roles.NON_TEACHING_STAFF;
        }
        if (t.contains("teach") || t.contains("lectur") || t.contains("academic") || t.contains("tutor")
                || t.contains("professor")) {
            return Roles.TEACHING_STAFF;
        }
        if (t.startsWith("student") || t.equals("s") || t.contains("undergrad") || t.contains("postgrad")) {
            return Roles.STUDENT;
        }
        return null;
    }

    private static boolean looksLikeHeader(String[] cols) {
        String first = cols.length > 0 ? cols[0].trim().toLowerCase(Locale.ROOT) : "";
        return first.contains("id") || first.contains("reg") || first.contains("number") || first.contains("no");
    }

    /** The whole file uses one separator: ";" if the first line has more semicolons than commas. */
    private static char separatorOf(String[] lines) {
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            int commas = 0, semis = 0;
            for (char c : l.toCharArray()) {
                if (c == ',') commas++;
                else if (c == ';') semis++;
            }
            return semis > commas ? ';' : ',';
        }
        return ',';
    }

    /** Splits one CSV line; "quoted, values" keep their separators. */
    static String[] split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');   // "" inside quotes is a literal quote
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == sep && !quoted) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }
}
