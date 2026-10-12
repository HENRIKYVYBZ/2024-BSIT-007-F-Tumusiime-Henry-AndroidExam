package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the librarian's book form and the walk-in reader lookup. */
public class BookValidatorTest {

    @Test
    public void titleAndAuthorAreRequired() {
        assertNull(BookValidator.titleError("Clean Code"));
        assertNotNull(BookValidator.titleError("   "));
        assertNotNull(BookValidator.titleError("A"));
        assertNull(BookValidator.authorError("Robert C. Martin"));
        assertNotNull(BookValidator.authorError(""));
    }

    @Test
    public void yearMustBeRealistic() {
        assertNull(BookValidator.yearError("2008", 2026));
        assertNull(BookValidator.yearError("2027", 2026));   // forthcoming edition
        assertNotNull(BookValidator.yearError("2028", 2026));
        assertNotNull(BookValidator.yearError("1200", 2026));
        assertNotNull(BookValidator.yearError("20x8", 2026));
    }

    @Test
    public void copiesCannotGoBelowCopiesOut() {
        assertNull(BookValidator.copiesError("3", 0));
        assertNull(BookValidator.copiesError("2", 2));
        assertTrue(BookValidator.copiesError("1", 2).contains("can't be less than 2"));
        assertNotNull(BookValidator.copiesError("0", 0));
        assertNotNull(BookValidator.copiesError("1000", 0));
        assertNotNull(BookValidator.copiesError("two", 0));
    }

    @Test
    public void readerIdAcceptsRegNumbersAndStaffIds() {
        assertNull(BookValidator.readerIdError("2023/bit/0457"));
        assertNull(BookValidator.readerIdError("ST-0123"));
        assertNotNull(BookValidator.readerIdError(""));
        assertNotNull(BookValidator.readerIdError("hello world"));
    }
}
