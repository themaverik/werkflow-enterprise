-- DOA threshold table: maps DOA level to maximum approvable amount per tenant.
-- max_amount = NULL means unlimited authority (DOA_L4).
-- Currency is stored per row to support multi-currency configurations.

CREATE TABLE IF NOT EXISTS doa_threshold (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  VARCHAR(100) NOT NULL,
    doa_level  VARCHAR(20)  NOT NULL,
    max_amount NUMERIC(15, 2),        -- NULL = unlimited
    currency   VARCHAR(3)   NOT NULL  DEFAULT 'USD',
    CONSTRAINT uq_doa_threshold_tenant_level UNIQUE (tenant_id, doa_level)
);

CREATE INDEX idx_doa_threshold_tenant ON doa_threshold (tenant_id);

-- Seed default thresholds for the default tenant.
-- Adjust amounts and currency for production deployment.
INSERT INTO doa_threshold (tenant_id, doa_level, max_amount, currency) VALUES
    ('default', 'DOA_L0', 0,         'USD'),  -- no approval authority
    ('default', 'DOA_L1', 1000.00,   'USD'),  -- entry-level approver
    ('default', 'DOA_L2', 10000.00,  'USD'),  -- mid-level approver
    ('default', 'DOA_L3', 100000.00, 'USD'),  -- senior approver
    ('default', 'DOA_L4', NULL,      'USD')   -- executive, unlimited
ON CONFLICT (tenant_id, doa_level) DO NOTHING;
