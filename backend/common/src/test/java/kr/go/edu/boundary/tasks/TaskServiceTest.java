package kr.go.edu.boundary.tasks;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import kr.go.edu.boundary.common.AuditWriter;
import kr.go.edu.boundary.common.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository repository;
    @Mock AuditWriter audit;
    @Mock OutboxWriter outbox;
    @InjectMocks TaskService service;

    @Test
    void invalidPageNeverReachesDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.list("", -1));
        assertThrows(IllegalArgumentException.class, () -> service.list("", 10001));
        verifyNoInteractions(repository, audit, outbox);
    }

    @Test
    void invalidInputCannotCreateTaskOrEvents() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("A-001", " ", "학교", "접수", "admin"));
        verifyNoInteractions(repository, audit, outbox);
    }

    @Test
    void normalizedDuplicateCannotCreateTaskOrEvents() {
        when(repository.findByCleanApplicationNo("A001")).thenReturn(Optional.of(task(0)));
        assertThrows(IllegalStateException.class,
                () -> service.create("a / 001", "방문자", "학교", "접수", "admin"));
        verify(repository, never()).insertManual(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(audit, outbox);
    }

    @Test
    void versionConflictCannotEmitSuccessfulChangeEvents() {
        UUID id = UUID.randomUUID();
        when(repository.updateStatus(id, "심사", 0)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.changeStatus(id, "심사", 0, "admin"));
        verifyNoInteractions(audit, outbox);
    }

    @Test
    void successfulStatusChangeReturnsSavedVersionAndEmitsEvents() {
        TaskRecord saved = task(1);
        when(repository.updateStatus(saved.id(), "심사", 0)).thenReturn(1);
        when(repository.findById(saved.id())).thenReturn(Optional.of(saved));
        assertEquals(saved, service.changeStatus(saved.id(), " 심사 ", 0, "admin"));
        verify(audit).record(eq("admin"), eq("TASK_STATUS_CHANGED"), eq("TASK"), eq(saved.id()), anyMap());
        verify(outbox).record("TASK_UPSERTED", saved.id(), saved);
    }

    private TaskRecord task(long version) {
        return new TaskRecord(UUID.randomUUID(), "A-001", "A001", "방문자", "학교", "심사",
                "MANUAL", OffsetDateTime.now(), version);
    }
}
