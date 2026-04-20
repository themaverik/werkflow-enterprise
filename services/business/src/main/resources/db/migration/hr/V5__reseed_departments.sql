-- V5: Reseed departments with org_id, type, locations
-- Clear existing data (employees first due to FK)
DELETE FROM hr_service.employees WHERE department_id IS NOT NULL;
DELETE FROM hr_service.employees;
DELETE FROM hr_service.departments;
ALTER SEQUENCE hr_service.departments_id_seq RESTART WITH 1;
ALTER SEQUENCE hr_service.employees_id_seq RESTART WITH 1;

INSERT INTO hr_service.departments (name, code, organization_id, department_type, office_location, department_email, is_active)
VALUES
    ('Information Technology', 'IT', 1, 'OPS', 'SEATTLE_US', 'it@werkflow.com', true),
    ('Human Resources', 'HR', 1, 'OPS', 'BANGALORE_IN', 'hr@werkflow.com', true),
    ('Finance', 'FINANCE', 1, 'OPS', 'SEATTLE_US', 'finance@werkflow.com', true),
    ('Procurement', 'PROCUREMENT', 1, 'OPS', 'BANGALORE_IN', 'procurement@werkflow.com', true),
    ('Operations', 'OPS', 1, 'OPS', 'SHILLONG_IN', 'ops@werkflow.com', true),
    ('Data & Analytics', 'DATA', 1, 'DATA', 'STOCKHOLM_SE', 'data@werkflow.com', true);
