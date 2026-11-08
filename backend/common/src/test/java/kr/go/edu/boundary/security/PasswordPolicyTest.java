package kr.go.edu.boundary.security;

import kr.go.edu.boundary.admin.AdminService;
import kr.go.edu.boundary.common.AuditWriter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordPolicyTest {
    @Test
    void rejectsMissingAndShortPasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("a".repeat(15)));
    }

    @Test
    void acceptsAsciiAndKoreanAtBcryptByteBoundary() {
        var encoder = new BCryptPasswordEncoder(4);
        for (String value : new String[]{"a".repeat(16), "a".repeat(72), "한".repeat(24)}) {
            assertDoesNotThrow(() -> PasswordPolicy.validate(value));
            assertTrue(encoder.matches(value, encoder.encode(value)));
        }
    }

    @Test
    void rejectsPasswordsBeyond72Utf8Bytes() {
        for (String value : new String[]{"a".repeat(73), "한".repeat(25)}) {
            assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(value));
        }
    }

    @Test
    void invalidPasswordCannotReachDatabaseOrEncoder() {
        var jdbc = mock(JdbcTemplate.class);
        var encoder = mock(PasswordEncoder.class);
        var audit = mock(AuditWriter.class);
        var service = new AdminService(jdbc, encoder, audit);
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("testuser", "한".repeat(25), "OPERATOR", "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "old-password", "a".repeat(73)));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", null, "a".repeat(16)));
        verifyNoInteractions(jdbc, encoder, audit);
    }
}
