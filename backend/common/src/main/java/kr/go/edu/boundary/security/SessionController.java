package kr.go.edu.boundary.security;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    private final String mode;
    private final String nodeId;

    public SessionController(@Value("${app.mode}") String mode, @Value("${app.node-id}") String nodeId) {
        if ((!"local".equals(mode) && !"aws".equals(mode)) || nodeId.isBlank()) {
            throw new IllegalArgumentException("APP_MODE(local/aws)와 APP_NODE_ID를 지정해야 합니다.");
        }
        this.mode = mode;
        this.nodeId = nodeId;
    }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/api/admin/session")
    public Map<String, String> session(Authentication authentication) {
        return Map.of("username", authentication.getName(), "mode", mode, "nodeId", nodeId,
                "role", authentication.getAuthorities().iterator().next().getAuthority());
    }
}
