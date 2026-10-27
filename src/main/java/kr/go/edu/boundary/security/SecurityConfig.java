package kr.go.edu.boundary.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return username -> jdbc.query(
                "SELECT username, password_hash, role, active FROM users WHERE username = ?",
                result -> {
                    if (!result.next()) {
                        throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username);
                    }
                    return User.withUsername(result.getString("username"))
                            .password(result.getString("password_hash"))
                            .roles(result.getString("role"))
                            .disabled(!result.getBoolean("active"))
                            .build();
                }, username);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JdbcTemplate jdbc) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login.html", "/sign.html", "/css/**", "/js/**", "/vendor/**",
                                "/favicon.ico",
                                "/api/csrf", "/actuator/health").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // 메뉴 숨김과 별개로 서버에서도 관리자 전용 API를 제한합니다.
                        .requestMatchers("/api/admin/users", "/api/admin/users/**",
                                "/api/admin/imports", "/api/admin/imports/**").hasRole("ADMIN")
                        .requestMatchers("/admin.html", "/api/admin/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .anyRequest().denyAll())
                // JavaScript와 로그인 양식이 /api/csrf에서 받은 토큰을 요청에 넣습니다.
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .formLogin(form -> form.loginPage("/login.html")
                        .defaultSuccessUrl("/admin.html", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login.html?logout"))
                .addFilterAfter(new ActiveAccountFilter(jdbc), UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(policy -> policy.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")))
                .build();
    }
}

