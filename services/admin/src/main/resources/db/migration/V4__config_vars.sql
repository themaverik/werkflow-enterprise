-- ============================================================
-- WERKFLOW ADMIN — CONFIGURATION VARIABLES
-- ============================================================
-- Adds the configuration_variables table (ADR-002) for per-tenant
-- key-value config: DOA threshold amounts, CSS theme values, and
-- any future tenant-scoped runtime config injected into DMN FEEL.
--
-- Also removes the cross_dept_doa_threshold column from tenants,
-- superseded by DOA config vars (ADR-002 + ADR-003).
-- ============================================================

CREATE TABLE IF NOT EXISTS configuration_variables (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_code VARCHAR(50)  NOT NULL,
    var_key     VARCHAR(100) NOT NULL,
    var_value   TEXT         NOT NULL,
    var_type    VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cv_tenant_key UNIQUE (tenant_code, var_key)
);

CREATE INDEX IF NOT EXISTS idx_cv_tenant ON configuration_variables (tenant_code);

ALTER TABLE tenants DROP COLUMN IF EXISTS cross_dept_doa_threshold;
