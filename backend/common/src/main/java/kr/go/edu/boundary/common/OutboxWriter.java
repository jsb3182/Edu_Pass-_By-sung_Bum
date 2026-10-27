package kr.go.edu.boundary.common;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String nodeId;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, @Value("${app.node-id}") String nodeId) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.nodeId = nodeId;
    }

    public void record(String eventType, UUID entityId, Object payload) {
        // 여기서는 전송하지 않습니다. 원본 데이터와 같은 트랜잭션에 대기 기록만 저장합니다.
        try {
            jdbc.update("INSERT INTO outbox_events(id, node_id, event_type, entity_id, payload) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb)",
                    UUID.randomUUID(), nodeId, eventType, entityId, mapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("동기화 기록을 생성할 수 없습니다.", exception);
        }
    }
}

