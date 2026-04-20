-- Seeds the five standard departments for the default tenant.
-- Requires the default organisation (seeded in V2) to exist.

INSERT INTO departments (organization_id, name, code, description, tenant_code, active, created_at, updated_at)
SELECT
    o.id,
    d.dept_name,
    d.dept_code,
    d.dept_desc,
    'default',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM organizations o
CROSS JOIN (VALUES
    ('Engineering', 'ENG', 'Engineering and Product'),
    ('Finance',     'FIN', 'Finance and Accounting'),
    ('HR',          'HR',  'Human Resources'),
    ('Operations',  'OPS', 'Operations'),
    ('IT',          'IT',  'Information Technology')
) AS d(dept_name, dept_code, dept_desc)
WHERE o.name = 'Werkflow Corporation'
ON CONFLICT (organization_id, code) DO NOTHING;
