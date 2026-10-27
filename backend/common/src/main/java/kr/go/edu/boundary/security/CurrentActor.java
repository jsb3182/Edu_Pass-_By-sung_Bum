package kr.go.edu.boundary.security;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    private final JdbcTemplate jdbc;

    public CurrentActor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID id(Authentication authentication) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ? AND active = true",
                UUID.class, authentication.getName());
    }
}

