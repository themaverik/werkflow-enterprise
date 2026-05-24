-- ADR-026 Phase 1: deployment bundle registry.
-- Records each deployed process bundle (a BPMN deployed together with its
-- referenced DMNs under one shared parent_deployment_id) so that:
--   * bundle_version is monotonic per (tenant_id, process_key), and
--   * rollback (ADR-026 Phase 3) can target a prior bundle's parent_deployment_id.
-- Flowable's ACT_* tables remain the source of truth for the deployed artifacts;
-- this table is the Werkflow-side bundle index.

CREATE TABLE IF NOT EXISTS process_bundle (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id            VARCHAR(100) NOT NULL,
    process_key          VARCHAR(255) NOT NULL,
    bundle_version       INT          NOT NULL,
    parent_deployment_id VARCHAR(255) NOT NULL,
    created_by           VARCHAR(255),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_process_bundle_tenant_key_version UNIQUE (tenant_id, process_key, bundle_version),
    -- parent_deployment_id is the key Phase 2 binding / Phase 3 rollback resolve on; keep it unambiguous.
    CONSTRAINT uq_process_bundle_parent_deployment_id UNIQUE (parent_deployment_id)
);

CREATE INDEX IF NOT EXISTS idx_process_bundle_tenant_key ON process_bundle(tenant_id, process_key);
