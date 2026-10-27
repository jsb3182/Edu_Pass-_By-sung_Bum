package kr.go.edu.boundary.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    public ActiveAccountFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if ((path.equals("/admin.html") || path.startsWith("/api/admin/"))
                && authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Long active = jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ? AND active = true",
                    Long.class, authentication.getName());
            if (active == null || active == 0) {
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
