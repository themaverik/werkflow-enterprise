-- ============================================================
-- WERKFLOW ADMIN — ROLE GROUP MAPPINGS
-- ============================================================
-- Adds the role_group_mappings table used by FlowableGroupResolver
-- (ADR-003) to resolve Keycloak roles to Flowable candidateGroup
-- identifiers per tenant, replacing YAML-only static config.
-- ============================================================

CREATE TABLE IF NOT EXISTS role_group_mappings (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_code VARCHAR(50)  NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    group_name  VARCHAR(200) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rgm_tenant_role_group UNIQUE (tenant_code, role_name, group_name)
);

CREATE INDEX IF NOT EXISTS idx_rgm_tenant ON role_group_mappings (tenant_code);
