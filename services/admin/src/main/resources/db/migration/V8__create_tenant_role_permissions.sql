CREATE TABLE tenant_role_permissions (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_code VARCHAR(50)  NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    permission  VARCHAR(100) NOT NULL,
    UNIQUE (tenant_code, role_name, permission)
);

CREATE INDEX idx_trp_tenant_role ON tenant_role_permissions (tenant_code, role_name);
