-- V7: Datasource circuit breaker event log
-- Records when circuit breakers open/close for tenant datasource connections.
-- Used for operational visibility and alerting — not for business logic.

CREATE TABLE IF NOT EXISTS datasource_circuit_breaker_event (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_code  TEXT        NOT NULL,
    connector_key TEXT       NOT NULL,
    event_type   TEXT        NOT NULL,   -- OPEN | HALF_OPEN | CLOSED | CALL_NOT_PERMITTED
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    detail       TEXT
);

CREATE INDEX IF NOT EXISTS idx_ds_cb_event_tenant_connector
    ON datasource_circuit_breaker_event (tenant_code, connector_key, occurred_at DESC);
