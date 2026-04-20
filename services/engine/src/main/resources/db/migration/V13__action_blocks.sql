-- Action blocks: audit log and notification templates
-- Migration V13 — 2026-03-18

CREATE TABLE process_audit_log (
    id                     BIGSERIAL PRIMARY KEY,
    process_instance_id    VARCHAR(255) NOT NULL,
    execution_id           VARCHAR(255) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    action_type            VARCHAR(50)  NOT NULL,
    task_id                VARCHAR(255),
    task_name              VARCHAR(255),
    initiated_by           VARCHAR(255),
    timestamp              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    request_url            TEXT,
    request_method         VARCHAR(10),
    request_hash           VARCHAR(64),
    response_status        INTEGER,
    response_body          JSONB,
    response_truncated     BOOLEAN      NOT NULL DEFAULT FALSE,
    masked_fields          TEXT[],
    duration_ms            BIGINT,
    error_message          TEXT
);

CREATE INDEX idx_audit_process_instance ON process_audit_log(process_instance_id);
CREATE INDEX idx_audit_timestamp        ON process_audit_log(timestamp);

CREATE TABLE notification_templates (
    id           BIGSERIAL    PRIMARY KEY,
    template_key VARCHAR(100) NOT NULL UNIQUE,
    channel      VARCHAR(50)  NOT NULL,
    subject      VARCHAR(500),
    body         TEXT         NOT NULL,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
