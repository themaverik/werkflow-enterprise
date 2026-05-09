-- ADR-010: Tenant-registered category controlled vocabulary for artifact catalog grouping.
CREATE TABLE category (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(100) NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    icon         VARCHAR(50),
    color        VARCHAR(20),
    display_order INTEGER     NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_category_tenant ON category(tenant_id);

-- Seed default categories for the default tenant.
-- Tenants may rename, add, or remove via Tenant Setup.
INSERT INTO category (tenant_id, code, display_name, icon, color, display_order)
VALUES
    ('default', 'hr',          'Human Resources', 'users',           'purple', 1),
    ('default', 'finance',     'Finance',          'currency-rupee',  'green',  2),
    ('default', 'procurement', 'Procurement',      'shopping-cart',   'blue',   3),
    ('default', 'it',          'IT',               'device-laptop',   'cyan',   4),
    ('default', 'legal',       'Legal',            'scale',           'orange', 5),
    ('default', 'operations',  'Operations',       'settings',        'amber',  6),
    ('default', 'other',       'Other',            'folder',          'gray',   99)
ON CONFLICT (tenant_id, code) DO NOTHING;
