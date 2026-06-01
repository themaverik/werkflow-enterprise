-- Deploy-time persisted indicators so the /processes list query doesn't re-scan
-- BPMN XML per row. A missing row means both flags are false (LEFT JOIN semantic).
-- Populated and upserted by ProcessDefinitionService on every successful deployment.

CREATE TABLE IF NOT EXISTS process_indicators (
    process_definition_id VARCHAR(255) PRIMARY KEY,
    has_dmn               BOOLEAN     NOT NULL DEFAULT FALSE,
    has_connector         BOOLEAN     NOT NULL DEFAULT FALSE,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
