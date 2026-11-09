-- 서명자 이름은 최대 200자이며 같은 이름을 감사 기록에도 보존합니다.
ALTER TABLE audit_events ALTER COLUMN actor TYPE VARCHAR(200);
