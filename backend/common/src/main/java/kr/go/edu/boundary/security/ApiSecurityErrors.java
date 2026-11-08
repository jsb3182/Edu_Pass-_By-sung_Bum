package kr.go.edu.boundary.security;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/** API에는 HTML 이동 대신 상태 코드와 JSON 오류를 반환합니다. */
public class ApiSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;

    public ApiSecurityErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 만료되었거나 필요합니다. 다시 로그인해 주세요.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        if (!isApiRequest(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String message = exception instanceof CsrfException
                ? "요청 확인 정보가 만료되었거나 올바르지 않습니다. 화면을 새로고침해 주세요."
                : "이 작업을 수행할 권한이 없습니다.";
        write(response, HttpServletResponse.SC_FORBIDDEN, message);
    }

    public void inactiveAccount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isApiRequest(request)) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "비활성화된 계정입니다. 관리자에게 문의해 주세요.");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
