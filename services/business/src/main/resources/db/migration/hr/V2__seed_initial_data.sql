-- Flyway Migration V2: Seed Initial Data
-- This script inserts sample data for testing and development

-- Insert sample departments
INSERT INTO departments (id, name, description, code, is_active, parent_department_id, created_at, created_by, version)
VALUES
    (1, 'Engineering', 'Software development and engineering team', 'ENG', TRUE, NULL, CURRENT_TIMESTAMP, 'system', 0),
    (2, 'Human Resources', 'HR and people operations', 'HR', TRUE, NULL, CURRENT_TIMESTAMP, 'system', 0),
    (3, 'Finance', 'Finance and accounting department', 'FIN', TRUE, NULL, CURRENT_TIMESTAMP, 'system', 0),
    (4, 'Sales', 'Sales and business development', 'SALES', TRUE, NULL, CURRENT_TIMESTAMP, 'system', 0),
    (5, 'Marketing', 'Marketing and communications', 'MKTG', TRUE, NULL, CURRENT_TIMESTAMP, 'system', 0),
    (6, 'Backend Team', 'Backend development team', 'ENG-BE', TRUE, 1, CURRENT_TIMESTAMP, 'system', 0),
    (7, 'Frontend Team', 'Frontend development team', 'ENG-FE', TRUE, 1, CURRENT_TIMESTAMP, 'system', 0);

-- Reset sequence for departments
SELECT setval('departments_id_seq', (SELECT MAX(id) FROM departments));

-- Insert sample employees
INSERT INTO employees (id, employee_code, first_name, last_name, email, phone_number, date_of_birth, join_date,
                      job_title, employment_status, salary, address, department_id, manager_id, created_at, created_by, version)
VALUES
    (1, 'EMP001', 'John', 'Doe', 'john.doe@werkflow.com', '+1234567890', '1985-05-15', '2020-01-15',
     'Engineering Manager', 'ACTIVE', 120000.00, '123 Main St, City, State', 1, NULL, CURRENT_TIMESTAMP, 'system', 0),

    (2, 'EMP002', 'Jane', 'Smith', 'jane.smith@werkflow.com', '+1234567891', '1990-08-22', '2020-03-01',
     'Senior Backend Developer', 'ACTIVE', 95000.00, '456 Oak Ave, City, State', 6, 1, CURRENT_TIMESTAMP, 'system', 0),

    (3, 'EMP003', 'Mike', 'Johnson', 'mike.johnson@werkflow.com', '+1234567892', '1988-11-10', '2020-06-15',
     'Senior Frontend Developer', 'ACTIVE', 90000.00, '789 Pine Rd, City, State', 7, 1, CURRENT_TIMESTAMP, 'system', 0),

    (4, 'EMP004', 'Sarah', 'Williams', 'sarah.williams@werkflow.com', '+1234567893', '1992-03-25', '2021-01-20',
     'HR Manager', 'ACTIVE', 85000.00, '321 Elm St, City, State', 2, NULL, CURRENT_TIMESTAMP, 'system', 0),

    (5, 'EMP005', 'Robert', 'Brown', 'robert.brown@werkflow.com', '+1234567894', '1987-07-18', '2021-04-10',
     'Finance Manager', 'ACTIVE', 95000.00, '654 Maple Dr, City, State', 3, NULL, CURRENT_TIMESTAMP, 'system', 0),

    (6, 'EMP006', 'Emily', 'Davis', 'emily.davis@werkflow.com', '+1234567895', '1993-12-05', '2021-08-01',
     'Backend Developer', 'ACTIVE', 75000.00, '987 Birch Ln, City, State', 6, 2, CURRENT_TIMESTAMP, 'system', 0),

    (7, 'EMP007', 'David', 'Wilson', 'david.wilson@werkflow.com', '+1234567896', '1991-09-14', '2022-02-15',
     'Frontend Developer', 'ACTIVE', 72000.00, '147 Cedar Way, City, State', 7, 3, CURRENT_TIMESTAMP, 'system', 0),

    (8, 'EMP008', 'Lisa', 'Martinez', 'lisa.martinez@werkflow.com', '+1234567897', '1994-04-30', '2022-05-20',
     'HR Specialist', 'ACTIVE', 60000.00, '258 Spruce Ct, City, State', 2, 4, CURRENT_TIMESTAMP, 'system', 0);

-- Reset sequence for employees
SELECT setval('employees_id_seq', (SELECT MAX(id) FROM employees));

-- Insert sample leave requests
INSERT INTO leaves (employee_id, leave_type, start_date, end_date, number_of_days, reason, status,
                   approved_by, approved_at, created_at, created_by, version)
VALUES
    (2, 'ANNUAL', '2024-12-20', '2024-12-27', 5, 'Christmas vacation', 'APPROVED', 1, '2024-12-01', CURRENT_TIMESTAMP, 'system', 0),
    (3, 'SICK', '2024-11-15', '2024-11-16', 2, 'Medical appointment', 'APPROVED', 1, '2024-11-14', CURRENT_TIMESTAMP, 'system', 0),
    (6, 'ANNUAL', '2025-01-10', '2025-01-12', 3, 'Personal travel', 'PENDING', NULL, NULL, CURRENT_TIMESTAMP, 'system', 0),
    (8, 'COMPENSATORY', '2024-12-10', '2024-12-10', 1, 'Worked on weekend', 'APPROVED', 4, '2024-12-08', CURRENT_TIMESTAMP, 'system', 0);

-- Insert sample attendance records
INSERT INTO attendances (employee_id, attendance_date, check_in_time, check_out_time, status, worked_hours, created_at, created_by, version)
VALUES
    (2, '2024-11-01', '09:00:00', '18:00:00', 'PRESENT', 9.0, CURRENT_TIMESTAMP, 'system', 0),
    (2, '2024-11-04', '09:15:00', '17:45:00', 'PRESENT', 8.5, CURRENT_TIMESTAMP, 'system', 0),
    (3, '2024-11-01', '08:45:00', '17:30:00', 'PRESENT', 8.75, CURRENT_TIMESTAMP, 'system', 0),
    (3, '2024-11-04', '09:00:00', '18:30:00', 'PRESENT', 9.5, CURRENT_TIMESTAMP, 'system', 0),
    (6, '2024-11-01', '09:30:00', '18:00:00', 'PRESENT', 8.5, CURRENT_TIMESTAMP, 'system', 0),
    (7, '2024-11-01', '09:00:00', '17:00:00', 'PRESENT', 8.0, CURRENT_TIMESTAMP, 'system', 0),
    (2, '2024-11-02', NULL, NULL, 'WEEKEND', 0.0, CURRENT_TIMESTAMP, 'system', 0),
    (3, '2024-11-02', NULL, NULL, 'WEEKEND', 0.0, CURRENT_TIMESTAMP, 'system', 0);

-- Insert sample performance reviews
INSERT INTO performance_reviews (employee_id, review_date, review_period_start, review_period_end, rating, score,
                                strengths, areas_for_improvement, goals, reviewer_id, employee_acknowledged, created_at, created_by, version)
VALUES
    (2, '2024-06-30', '2024-01-01', '2024-06-30', 'EXCEEDS_EXPECTATIONS', 88.5,
     'Excellent technical skills, great team collaboration, delivers on time',
     'Could improve documentation practices',
     'Lead a major project in H2 2024, mentor junior developers',
     1, TRUE, CURRENT_TIMESTAMP, 'system', 0),

    (3, '2024-06-30', '2024-01-01', '2024-06-30', 'MEETS_EXPECTATIONS', 82.0,
     'Strong frontend skills, good code quality',
     'Improve communication with backend team',
     'Enhance UI/UX skills, take ownership of frontend architecture',
     1, TRUE, CURRENT_TIMESTAMP, 'system', 0),

    (6, '2024-06-30', '2024-01-01', '2024-06-30', 'MEETS_EXPECTATIONS', 78.0,
     'Quick learner, eager to take on new challenges',
     'Need more experience with system design',
     'Complete advanced backend development course, work on optimization',
     2, FALSE, CURRENT_TIMESTAMP, 'system', 0);

-- Insert sample payroll records
INSERT INTO payrolls (employee_id, payment_month, payment_year, payment_date, basic_salary, allowances, bonuses,
                     overtime_pay, tax_deduction, insurance_deduction, other_deductions, gross_salary, net_salary,
                     is_paid, created_at, created_by, version)
VALUES
    (2, 11, 2024, '2024-11-30', 95000.00, 5000.00, 0.00, 1000.00, 20200.00, 1500.00, 0.00, 101000.00, 79300.00,
     TRUE, CURRENT_TIMESTAMP, 'system', 0),

    (3, 11, 2024, '2024-11-30', 90000.00, 4500.00, 0.00, 500.00, 19000.00, 1500.00, 0.00, 95000.00, 74500.00,
     TRUE, CURRENT_TIMESTAMP, 'system', 0),

    (6, 11, 2024, '2024-11-30', 75000.00, 3500.00, 2000.00, 0.00, 16100.00, 1200.00, 0.00, 80500.00, 63200.00,
     TRUE, CURRENT_TIMESTAMP, 'system', 0),

    (2, 12, 2024, '2024-12-31', 95000.00, 5000.00, 5000.00, 0.00, 21000.00, 1500.00, 0.00, 105000.00, 82500.00,
     FALSE, CURRENT_TIMESTAMP, 'system', 0);
