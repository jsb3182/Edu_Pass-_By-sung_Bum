package kr.go.edu.boundary.signing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerAddressTest {
    private SigningService server(String url, String mode, boolean allowHttp) {
        return new SigningService(null, null, null, null, null, null, url, mode, allowHttp);
    }

    @Test
    void localLanHttpRequiresExplicitDevelopmentOption() {
        assertThrows(IllegalArgumentException.class,
                () -> server("http://192.168.0.10:8080", "local", false));
        assertDoesNotThrow(() -> server("http://192.168.0.10:8080", "local", true));
        assertDoesNotThrow(() -> server("http://127.0.0.1:8080", "local", false));
    }

    @Test
    void awsAlwaysRequiresHttps() {
        assertThrows(IllegalArgumentException.class,
                () -> server("http://192.168.0.10:8080", "aws", true));
        assertDoesNotThrow(() -> server("https://visits.example.com", "aws", false));
    }

    @Test
    void baseAddressCannotContainCredentialsQueryOrPagePath() {
        for (String address : new String[]{"ftp://localhost", "https://user:pass@example.com",
                "https://example.com/user/index.html", "https://example.com?x=1", "https://example.com#token"}) {
            assertThrows(IllegalArgumentException.class, () -> server(address, "local", true));
        }
    }
}
