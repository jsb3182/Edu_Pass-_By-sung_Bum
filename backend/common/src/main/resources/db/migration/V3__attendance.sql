CREATE TABLE attendance_events (
    id UUID PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    event_date DATE NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ
);

CREATE TABLE attendance_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES attendance_events(id),
    submission_id UUID NOT NULL,
    education_office VARCHAR(150) NOT NULL,
    job_title VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    image_png BYTEA NOT NULL CHECK (octet_length(image_png) <= 256000),
    image_sha256 CHAR(64) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, submission_id),
    UNIQUE (event_id, phone, full_name)
);

CREATE INDEX attendance_entries_order ON attendance_entries(event_id, signed_at, id);
