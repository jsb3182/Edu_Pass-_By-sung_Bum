package kr.go.edu.boundary.common;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(String actor, String action, String type, UUID id, Map<String, ?> details) {
        // 호출한 서비스의 트랜잭션에 참여하므로 업무 저장이 실패하면 감사 기록도 롤백됩니다.
        try {
            jdbc.update("INSERT INTO audit_events(id, actor, action, entity_type, entity_id, details) "
                            + "VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                    UUID.randomUUID(), actor, action, type, id, mapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("감사 기록을 생성할 수 없습니다.", exception);
        }
    }
}

