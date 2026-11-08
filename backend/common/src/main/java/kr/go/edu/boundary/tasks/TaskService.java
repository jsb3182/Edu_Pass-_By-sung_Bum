package kr.go.edu.boundary.tasks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import kr.go.edu.boundary.tasks.dto.TaskPage;
import kr.go.edu.boundary.common.ApplicationNumbers;
import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private static final int PAGE_SIZE = 50;
    private final TaskRepository repository;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public TaskService(TaskRepository repository, AuditWriter audit, OutboxWriter outbox) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    public TaskPage list(String query, int page) {
        if (page < 0 || page > 10_000) {
            throw new IllegalArgumentException("페이지 번호가 올바르지 않습니다.");
        }
        String pattern = "%" + (query == null ? "" : query.strip()) + "%";
        long total = repository.countMatching(pattern);
        List<TaskRecord> items = repository.findPage(pattern, PAGE_SIZE, page * PAGE_SIZE);
        return new TaskPage(items, total, page, PAGE_SIZE);
    }

    public TaskRecord get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("내역을 찾을 수 없습니다."));
    }

    // 엑셀과 직접 등록은 같은 정제 신청번호를 기준으로 동일한 내역을 찾습니다.
    public Optional<TaskRecord> findByCleanApplicationNo(String cleanNo) {
        return repository.findByCleanApplicationNo(cleanNo);
    }

    @Transactional
    public TaskRecord create(String applicationNo, String applicantName, String schoolName,
            String status, String actor) {
        String clean = ApplicationNumbers.clean(applicationNo);
        requireText(applicationNo, "신청번호", 120);
        requireText(applicantName, "신청자", 200);
        requireText(schoolName, "학교", 200);
        requireText(status, "상태", 100);
        if (findByCleanApplicationNo(clean).isPresent()) {
            throw new IllegalStateException("동일한 신청번호가 이미 있습니다.");
        }
        UUID id = UUID.randomUUID();
        repository.insertManual(id, applicationNo.strip(), clean,
                applicantName.strip(), schoolName.strip(), status.strip());
        TaskRecord task = get(id);
        audit.record(actor, "TASK_CREATED", "TASK", id, Map.of("applicationNo", clean));
        outbox.record("TASK_UPSERTED", id, task);
        return task;
    }

    @Transactional
    public TaskRecord changeStatus(UUID id, String status, long expectedVersion, String actor) {
        requireText(status, "상태", 100);
        // version 조건으로 다른 관리자의 변경을 조용히 덮어쓰지 않게 합니다.
        int updated = repository.updateStatus(id, status.strip(), expectedVersion);
        if (updated != 1) {
            throw new IllegalStateException("다른 사용자가 내역을 변경했습니다. 새로고침 후 다시 시도하세요.");
        }
        TaskRecord task = get(id);
        audit.record(actor, "TASK_STATUS_CHANGED", "TASK", id, Map.of("status", task.status()));
        outbox.record("TASK_UPSERTED", id, task);
        return task;
    }

    public long count() {
        return repository.count();
    }

    private static void requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " 입력값이 올바르지 않습니다.");
        }
    }

}
