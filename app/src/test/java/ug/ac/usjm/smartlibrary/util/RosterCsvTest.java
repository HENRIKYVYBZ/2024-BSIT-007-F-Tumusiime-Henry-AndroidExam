package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for reading the official ID list (CSV) used to approve accounts automatically. */
public class RosterCsvTest {

    @Test
    public void readsAListWithAHeaderRow() {
        RosterCsv.Result r = RosterCsv.parse(
                "id,role,name\n"
                        + "2024/BSIT/007/F, student, Tumusiime Henry\n"
                        + "ST-0123, Teaching staff, Dr. Jane Akello\n"
                        + "NT-0042, non-teaching, Peter Okello\n");
        assertEquals(3, r.entries.size());
        assertEquals(0, r.problems.size());
        assertEquals(1, r.students);
        assertEquals(1, r.teachingStaff);
        assertEquals(1, r.nonTeachingStaff);
        assertEquals("2024-BSIT-007-F", r.entries.get(0).key);
        assertEquals("Dr. Jane Akello", r.entries.get(1).name);
    }

    @Test
    public void excelStyleFilesWork() {
        // Byte-order mark, semicolons, Windows line endings, quoted name with a comma.
        RosterCsv.Result r = RosterCsv.parse("﻿Registration number;Role;Name\r\n"
                + "2023/bit/0457;Student;\"Nakato, Sarah\"\r\n");
        assertEquals(1, r.entries.size());
        assertEquals("2023/BIT/0457", r.entries.get(0).id);
        assertEquals("Nakato, Sarah", r.entries.get(0).name);
    }

    @Test
    public void everydayRoleWordsAreUnderstood() {
        assertEquals(Roles.TEACHING_STAFF, RosterCsv.roleFrom("Lecturer"));
        assertEquals(Roles.TEACHING_STAFF, RosterCsv.roleFrom("teaching_staff"));
        assertEquals(Roles.NON_TEACHING_STAFF, RosterCsv.roleFrom("Non-teaching staff"));
        assertEquals(Roles.NON_TEACHING_STAFF, RosterCsv.roleFrom("support staff"));
        assertEquals(Roles.STUDENT, RosterCsv.roleFrom("STUDENTS"));
        assertNull(RosterCsv.roleFrom("librarian"));   // librarians and admins are never granted from a file
        assertNull(RosterCsv.roleFrom(""));
    }

    @Test
    public void aRegistrationNumberWithoutARoleIsAStudent() {
        RosterCsv.Result r = RosterCsv.parse("2022/BBA/0110\n");
        assertEquals(1, r.entries.size());
        assertEquals(Roles.STUDENT, r.entries.get(0).role);
    }

    @Test
    public void badLinesAreReportedAndSkipped() {
        RosterCsv.Result r = RosterCsv.parse(
                "2023/BIT/0457,student\n"
                        + "2023/BIT/0457,student\n"   // duplicate
                        + "hello,student\n"           // not a registration number
                        + "ST-9,manager\n");          // unknown role
        assertEquals(1, r.entries.size());
        assertEquals(3, r.problems.size());
        assertTrue(r.problems.get(0).startsWith("Line 2"));
        assertTrue(r.problems.get(2).contains("unknown role"));
    }

    @Test
    public void slashAndDashGiveTheSameKey() {
        assertEquals(RosterKey.of("2023/bit/0457 "), RosterKey.of("2023-BIT-0457"));
        assertEquals("ST-0123", RosterKey.of("st-0123"));
    }
}
