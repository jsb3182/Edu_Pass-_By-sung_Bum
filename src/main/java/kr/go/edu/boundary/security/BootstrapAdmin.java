package kr.go.edu.boundary.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final String username;
    private final String password;

    public BootstrapAdmin(JdbcTemplate jdbc, PasswordEncoder encoder, TransactionTemplate transaction,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.transaction = transaction;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(907253)", (result, row) -> result.getObject(1));
            Integer count = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
            if (count != null && count > 0) {
                return;
            }
            if (username.isBlank() || password.length() < 16) {
                throw new IllegalStateException("최초 실행에는 BOOTSTRAP_ADMIN_USER와 16자 이상의 BOOTSTRAP_ADMIN_PASSWORD가 필요합니다.");
            }
            jdbc.update("INSERT INTO users(id, username, password_hash, role) VALUES (?, ?, ?, 'ADMIN')",
                    UUID.randomUUID(), username, encoder.encode(password));
        });
    }
}

