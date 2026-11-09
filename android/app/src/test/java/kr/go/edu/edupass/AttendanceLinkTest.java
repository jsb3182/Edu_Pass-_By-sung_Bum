package kr.go.edu.edupass;

import java.net.URI;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttendanceLinkTest {
    private static final String PATH = "/attendance/index.html#token=" + "A".repeat(43);

    @Test
    public void localAllowsPrivateHttpWhileRemoteRequiresHttps() {
        assertNotNull(AttendanceLink.parse("http://192.168.1.8:28080" + PATH, true));
        assertThrows(IllegalArgumentException.class, () -> AttendanceLink.parse("http://192.168.1.8" + PATH, false));
        assertThrows(IllegalArgumentException.class, () -> AttendanceLink.parse("http://example.org" + PATH, true));
        assertNotNull(AttendanceLink.parse("https://example.org" + PATH, false));
    }

    @Test
    public void webViewAllowsOnlyVisitorResourcesOnSelectedOrigin() {
        var link = AttendanceLink.parse("https://example.org" + PATH, false);
        assertTrue(link.permitsResource(URI.create("https://example.org:443/api/public/attendance/submit")));
        assertFalse(link.permitsResource(URI.create("https://example.org/api/admin/attendance/events")));
        assertFalse(link.permitsResource(URI.create("https://different.org/attendance/index.html")));
        assertFalse(link.permitsNavigation(URI.create("https://example.org/master/index.html")));
    }
}
