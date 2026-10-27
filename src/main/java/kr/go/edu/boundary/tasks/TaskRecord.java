package kr.go.edu.boundary.tasks;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskRecord(UUID id, String applicationNo, String cleanApplicationNo,
        String applicantName, String schoolName, String status, String source,
        OffsetDateTime updatedAt, long version) { }

