-- Dead-letter store for webhook payloads that could not be correlated
-- to any running or startable process instance.
-- Ops can inspect and replay these from the Monitoring screen.

CREATE TABLE IF NOT EXISTS webhook_undelivered (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_code         VARCHAR(255) NOT NULL,
    connector_key       VARCHAR(255) NOT NULL,
    idempotency_key     VARCHAR(500),
    raw_body            TEXT NOT NULL,
    headers_json        TEXT,
    failure_reason      TEXT NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    replayed_at         TIMESTAMPTZ,
    replayed_by         VARCHAR(255)
);

CREATE INDEX idx_webhook_undelivered_tenant    ON webhook_undelivered (tenant_code);
CREATE INDEX idx_webhook_undelivered_connector ON webhook_undelivered (tenant_code, connector_key);
CREATE INDEX idx_webhook_undelivered_pending   ON webhook_undelivered (tenant_code) WHERE replayed_at IS NULL;
