package kr.go.edu.boundary.admin;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/summary")
    public AdminService.Summary summary() {
        return admin.summary();
    }

    @GetMapping("/audit")
    public List<AdminService.AuditEntry> audit() {
        return admin.audit();
    }

    @GetMapping("/users")
    public List<AdminService.UserSummary> users() {
        return admin.users();
    }

    @PostMapping("/users")
    public AdminService.UserSummary create(@RequestBody CreateUser request, Authentication authentication) {
        return admin.createUser(request.username(), request.password(), request.role(), authentication.getName());
    }

    @PostMapping("/users/{id}/deactivate")
    public void deactivate(@PathVariable UUID id, Authentication authentication) {
        admin.deactivate(id, authentication.getName());
    }

    @PostMapping("/password")
    public void password(@RequestBody ChangePassword request, Authentication authentication,
            HttpServletRequest servletRequest) {
        admin.changePassword(authentication.getName(), request.currentPassword(), request.nextPassword());
        var session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public record CreateUser(String username, String password, String role) { }
    public record ChangePassword(String currentPassword, String nextPassword) { }
}
