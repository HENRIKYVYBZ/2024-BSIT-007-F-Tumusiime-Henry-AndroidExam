package ug.ac.usjm.smartlibrary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Unit tests for the library server address typed in the Sync dialog. */
public class ServerAddressTest {

    @Test
    public void ipAndPortGetHttpAdded() {
        assertEquals("http://192.168.1.10:8000", ServerAddress.normalise(" 192.168.1.10:8000/ "));
    }

    @Test
    public void fullUrlsAreKept() {
        assertEquals("https://library.usjm.ac.ug", ServerAddress.normalise("https://library.usjm.ac.ug"));
    }

    @Test
    public void badAddressesAreRejected() {
        assertNull(ServerAddress.normalise(""));
        assertNull(ServerAddress.normalise("192.168.1.10:8000/catalogue"));
        assertNull(ServerAddress.normalise("not an address"));
    }

    @Test
    public void booksUrlPointsAtTheApi() {
        assertEquals("http://10.0.0.5:8000/api/books/", ServerAddress.booksUrl("http://10.0.0.5:8000"));
    }
}
