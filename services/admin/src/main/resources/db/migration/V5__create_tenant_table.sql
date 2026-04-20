CREATE TABLE tenants (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_code              VARCHAR(50)  NOT NULL UNIQUE,
    name                     VARCHAR(100) NOT NULL,
    keycloak_realm           VARCHAR(100),
    cross_dept_doa_threshold INT          NOT NULL DEFAULT 4,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_code ON tenants (tenant_code);
CREATE INDEX idx_tenants_active ON tenants (active) WHERE active = TRUE;
