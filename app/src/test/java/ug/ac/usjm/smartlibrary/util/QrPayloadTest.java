package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Unit tests for reading book ids from scanned QR labels. */
public class QrPayloadTest {

    @Test
    public void labelTextRoundTrips() {
        assertEquals("usjm-library://book/7", QrPayload.forBook(7));
        assertEquals(7, QrPayload.bookId(QrPayload.forBook(7)));
    }

    @Test
    public void spacesCaseAndTrailingSlashAreTolerated() {
        assertEquals(12, QrPayload.bookId("  USJM-LIBRARY://BOOK/12/ "));
    }

    @Test
    public void otherQrCodesAreRejected() {
        assertEquals(-1, QrPayload.bookId("https://www.google.com"));
        assertEquals(-1, QrPayload.bookId("usjm-library://book/"));
        assertEquals(-1, QrPayload.bookId("usjm-library://book/abc"));
        assertEquals(-1, QrPayload.bookId("usjm-library://book/0"));
        assertEquals(-1, QrPayload.bookId(null));
    }
}
