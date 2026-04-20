CREATE TABLE tenant_custody_mappings (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_code     VARCHAR(50)  NOT NULL,
    category_key    VARCHAR(100) NOT NULL,
    custody_group   VARCHAR(200) NOT NULL,
    display_name    VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tcm_tenant_category UNIQUE (tenant_code, category_key)
);

CREATE INDEX idx_tcm_tenant ON tenant_custody_mappings (tenant_code);
