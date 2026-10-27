CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE detail_tasks (
    id UUID PRIMARY KEY,
    application_no_raw VARCHAR(120) NOT NULL,
    application_no_clean VARCHAR(120) NOT NULL UNIQUE,
    applicant_name VARCHAR(200) NOT NULL,
    school_name VARCHAR(200) NOT NULL,
    status VARCHAR(100) NOT NULL,
    source VARCHAR(30) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1
);
CREATE INDEX idx_detail_tasks_updated ON detail_tasks (updated_at DESC);

CREATE TABLE import_batches (
    id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    inserted_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    unchanged_count INTEGER NOT NULL,
    imported_by UUID NOT NULL REFERENCES users(id),
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE signing_sessions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES detail_tasks(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    signer_name VARCHAR(200) NOT NULL,
    signer_affiliation VARCHAR(200) NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('EDUCATION', 'REVIEW', 'OTHER')),
    task_version BIGINT NOT NULL,
    issued_by UUID NOT NULL REFERENCES users(id),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX idx_signing_sessions_task ON signing_sessions(task_id);

CREATE TABLE signatures (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES signing_sessions(id),
    task_id UUID NOT NULL REFERENCES detail_tasks(id),
    signer_name VARCHAR(200) NOT NULL,
    signer_affiliation VARCHAR(200) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    image_png BYTEA NOT NULL,
    image_sha256 CHAR(64) NOT NULL,
    task_snapshot_sha256 CHAR(64) NOT NULL,
    task_snapshot JSONB NOT NULL,
    consent_text TEXT NOT NULL,
    location_consent_text TEXT NOT NULL,
    device_label VARCHAR(120),
    client_signed_at TIMESTAMPTZ,
    location_captured_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_m DOUBLE PRECISION,
    location_consent BOOLEAN NOT NULL,
    location_status VARCHAR(30) NOT NULL CHECK (location_status IN ('AVAILABLE', 'DENIED', 'UNAVAILABLE', 'TIMEOUT', 'NOT_REQUESTED')),
    CHECK (location_consent OR location_status = 'NOT_REQUESTED'),
    CHECK ((location_status = 'AVAILABLE' AND latitude IS NOT NULL AND longitude IS NOT NULL
            AND accuracy_m IS NOT NULL AND latitude BETWEEN -90 AND 90
            AND longitude BETWEEN -180 AND 180 AND accuracy_m > 0)
        OR (location_status <> 'AVAILABLE' AND latitude IS NULL AND longitude IS NULL AND accuracy_m IS NULL))
);
CREATE INDEX idx_signatures_task ON signatures(task_id, received_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_time ON audit_events(occurred_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    node_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    exported_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE exported_at IS NULL;

CREATE TABLE inbox_events (
    id UUID PRIMARY KEY,
    source_node_id VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION reject_evidence_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Evidence records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER signatures_append_only BEFORE UPDATE OR DELETE ON signatures
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();
CREATE TRIGGER audit_events_append_only BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();
CREATE TRIGGER import_batches_append_only BEFORE UPDATE OR DELETE ON import_batches
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();
