package kr.go.edu.boundary.tasks;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tasks")
public class TaskController {
    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public TaskService.TaskPage list(@RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page) {
        return tasks.list(query, page);
    }

    @GetMapping("/{id}")
    public TaskRecord get(@PathVariable UUID id) {
        return tasks.get(id);
    }

    @PostMapping
    public TaskRecord create(@Valid @RequestBody CreateTask request, Authentication authentication) {
        return tasks.create(request.applicationNo(), request.applicantName(), request.schoolName(),
                request.status(), authentication.getName());
    }

    @PutMapping("/{id}/status")
    public TaskRecord status(@PathVariable UUID id, @Valid @RequestBody ChangeStatus request,
            Authentication authentication) {
        return tasks.changeStatus(id, request.status(), request.version(), authentication.getName());
    }

    public record CreateTask(@NotBlank @Size(max = 120) String applicationNo, @NotBlank @Size(max = 200) String applicantName,
            @NotBlank @Size(max = 200) String schoolName, @NotBlank @Size(max = 100) String status) { }

    public record ChangeStatus(@NotBlank @Size(max = 100) String status, long version) { }
}

