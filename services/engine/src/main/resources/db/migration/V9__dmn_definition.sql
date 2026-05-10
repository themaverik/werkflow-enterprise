-- ADR-010 § DMN: Design-time DMN definition store.
-- Provides a draft/publish lifecycle for decision tables parallel to
-- process_draft and form_schemas. Includes the three-axis categorization
-- columns (department_code, category_code, tags) introduced by V5 for
-- process_draft and form_schemas so that TagProjector can include DMN
-- artifacts in its unified tag vocabulary.
--
-- NOTE: there is no native Flowable "DMN draft" table in the OSS baseline;
-- this is a Werkflow-managed draft store. Deployed DMN XML is still pushed
-- to Flowable's ACT_DMN_* tables via the engine's DmnRepositoryService.

CREATE TABLE IF NOT EXISTS dmn_definition (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    decision_key    VARCHAR(255) NOT NULL,
    name            VARCHAR(255),
    version         INT          NOT NULL DEFAULT 1,
    dmn_xml         TEXT         NOT NULL,
    tenant_id       VARCHAR(100),
    department_code VARCHAR(64),
    category_code   VARCHAR(64),
    tags            TEXT[]       NOT NULL DEFAULT '{}',
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_dmn_definition_key_version_tenant UNIQUE (decision_key, version, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_dmn_definition_tenant    ON dmn_definition(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dmn_definition_dept      ON dmn_definition(department_code);
CREATE INDEX IF NOT EXISTS idx_dmn_definition_cat       ON dmn_definition(category_code);
CREATE INDEX IF NOT EXISTS idx_dmn_definition_tags      ON dmn_definition USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_dmn_definition_key       ON dmn_definition(decision_key);
CREATE INDEX IF NOT EXISTS idx_dmn_definition_active    ON dmn_definition(tenant_id, is_active);
