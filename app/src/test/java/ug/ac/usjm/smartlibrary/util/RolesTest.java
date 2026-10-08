package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the role rules and the staff ID / role-specific date checks. */
public class RolesTest {

    @Test
    public void onlyStudentAndStaffRolesCanBeChosenAtSignUp() {
        assertTrue(Roles.canSelfSignUp(Roles.STUDENT));
        assertTrue(Roles.canSelfSignUp(Roles.TEACHING_STAFF));
        assertTrue(Roles.canSelfSignUp(Roles.NON_TEACHING_STAFF));
        assertFalse(Roles.canSelfSignUp(Roles.LIBRARIAN));
        assertFalse(Roles.canSelfSignUp(Roles.ADMIN));
        assertFalse(Roles.canSelfSignUp("superuser"));
    }

    @Test
    public void staffWaitingForApprovalGetStudentLimits() {
        assertEquals(Roles.STUDENT, Roles.effective(Roles.TEACHING_STAFF, false));
        assertEquals(3, Roles.maxActiveReservations(Roles.TEACHING_STAFF, false));
        assertEquals(7, Roles.maxPickupDaysAhead(Roles.TEACHING_STAFF, false));
        assertFalse(Roles.canRecommend(Roles.TEACHING_STAFF, false));
    }

    @Test
    public void limitsDependOnRole() {
        assertEquals(3, Roles.maxActiveReservations(Roles.STUDENT, true));
        assertEquals(5, Roles.maxActiveReservations(Roles.NON_TEACHING_STAFF, true));
        assertEquals(10, Roles.maxActiveReservations(Roles.TEACHING_STAFF, true));
        assertEquals(14, Roles.maxPickupDaysAhead(Roles.TEACHING_STAFF, true));
        assertEquals(7, Roles.maxPickupDaysAhead(Roles.NON_TEACHING_STAFF, true));
    }

    @Test
    public void onlyLibrariansAndAdminsRunTheDeskAndOnlyAdminsManageUsers() {
        assertTrue(Roles.canRunDesk(Roles.LIBRARIAN, true));
        assertTrue(Roles.canRunDesk(Roles.ADMIN, true));
        assertFalse(Roles.canRunDesk(Roles.TEACHING_STAFF, true));
        assertFalse(Roles.canRunDesk(Roles.LIBRARIAN, false));
        assertTrue(Roles.canManageUsers(Roles.ADMIN, true));
        assertFalse(Roles.canManageUsers(Roles.LIBRARIAN, true));
    }

    @Test
    public void unknownRoleIsTreatedAsStudent() {
        assertEquals(Roles.STUDENT, Roles.effective("hacker", true));
        assertEquals("Student", Roles.label(null));
    }

    @Test
    public void staffIdFormat() {
        assertNull(Validator.staffIdError("ST-0123"));
        assertNull(Validator.staffIdError("usjm/lib/007"));
        assertNull(Validator.staffIdError("10457"));
        assertNotNull(Validator.staffIdError(""));
        assertNotNull(Validator.staffIdError("ST 0123"));
        assertNotNull(Validator.staffIdError("ST--0123"));
    }

    @Test
    public void idNumberCheckFollowsRole() {
        assertNull(Validator.idNumberError(Roles.STUDENT, "2023/BIT/0457"));
        assertNotNull(Validator.idNumberError(Roles.STUDENT, "ST-0123"));
        assertNull(Validator.idNumberError(Roles.TEACHING_STAFF, "ST-0123"));
    }

    @Test
    public void lecturersMayBookFurtherAhead() {
        // 2026-10-05 is a Monday; 2026-10-15 is 10 days later (a Thursday).
        assertNotNull(Validator.pickupDateError("2026-10-15", "2026-10-05", 7));
        assertNull(Validator.pickupDateError("2026-10-15", "2026-10-05", 14));
    }
}
