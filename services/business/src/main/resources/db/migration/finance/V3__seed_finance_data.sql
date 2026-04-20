-- Seed Budget Categories
INSERT INTO budget_categories (id, parent_category_id, name, code, description) VALUES
(1, NULL, 'Operations', 'OPS', 'Operational expenses'),
(2, NULL, 'Capital', 'CAP', 'Capital expenditures'),
(3, NULL, 'Human Resources', 'HR', 'Personnel costs'),
(4, NULL, 'Technology', 'TECH', 'Technology infrastructure'),
(5, 1, 'Office Supplies', 'OPS-SUP', 'Office supplies and materials'),
(6, 1, 'Travel', 'OPS-TRV', 'Business travel expenses'),
(7, 2, 'Equipment', 'CAP-EQP', 'Equipment purchases'),
(8, 3, 'Training', 'HR-TRN', 'Employee training and development'),
(9, 4, 'Software Licenses', 'TECH-LIC', 'Software licenses and subscriptions');

-- Seed Approval Thresholds
INSERT INTO approval_thresholds (min_amount, max_amount, approver_role, approval_order) VALUES
(0.01, 1000.00, 'DEPARTMENT_MANAGER', 1),
(1000.01, 5000.00, 'FINANCE_MANAGER', 1),
(5000.01, 25000.00, 'FINANCE_DIRECTOR', 1),
(25000.01, NULL, 'CFO', 1);

-- Reset sequence
SELECT setval('budget_categories_id_seq', 10, true);
