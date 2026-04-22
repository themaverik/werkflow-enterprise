-- ============================================================
-- WERKFLOW ENGINE — ENTERPRISE BASELINE
-- ============================================================
-- Adds enterprise-only tables and seed data on top of the OSS
-- V1__baseline.sql. Covers: DOA thresholds, custody tracking,
-- enterprise workflow role mappings, enterprise notification
-- templates, enterprise form schemas, and ERP service registry.
--
-- Applies after: V1__baseline.sql (OSS)
-- Enterprise features: asset-request, CapEx, DOA routing,
--   SLA escalation, parallel committee approval.
-- ============================================================

-- ============================================================
-- CUSTODY COLUMNS (form_schemas + process_custody table)
-- ============================================================

ALTER TABLE form_schemas
    ADD COLUMN IF NOT EXISTS owning_department     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS created_by_department VARCHAR(100);

CREATE TABLE IF NOT EXISTS process_custody (
    process_definition_key VARCHAR(255) PRIMARY KEY,
    owning_department      VARCHAR(100) NOT NULL,
    created_by             VARCHAR(255),
    created_by_department  VARCHAR(100),
    created_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_process_custody_dept ON process_custody(owning_department);

COMMENT ON TABLE process_custody IS 'Tracks department ownership of Flowable process definitions for edit/delete rights';
COMMENT ON COLUMN process_custody.owning_department IS 'Department that has custody (edit/delete rights) over this process';
COMMENT ON COLUMN form_schemas.owning_department IS 'Department that has custody (edit/delete rights) over this form';

-- ============================================================
-- DOA THRESHOLD TABLE
-- (Consolidated from V11 + V21 labels + V22 amount updates)
-- ============================================================

CREATE TABLE IF NOT EXISTS doa_threshold (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   VARCHAR(100) NOT NULL,
    doa_level   VARCHAR(20)  NOT NULL,
    max_amount  NUMERIC(15, 2),              -- NULL = unlimited
    currency    VARCHAR(3)   NOT NULL DEFAULT 'USD',
    label       VARCHAR(100),
    description VARCHAR(500),
    CONSTRAINT uq_doa_threshold_tenant_level UNIQUE (tenant_id, doa_level)
);

CREATE INDEX IF NOT EXISTS idx_doa_threshold_tenant ON doa_threshold (tenant_id);

-- Seed default thresholds for the default tenant.
-- Amounts reflect S25 sample workflow ranges (L1: ≤$10K, L2: ≤$50K, L3: ≤$200K, L4: unlimited).
INSERT INTO doa_threshold (tenant_id, doa_level, max_amount, currency, label) VALUES
    ('default', 'DOA_L0', 0,          'USD', 'No Approval Authority'),
    ('default', 'DOA_L1', 10000.00,   'USD', 'Junior Approver'),
    ('default', 'DOA_L2', 50000.00,   'USD', 'Manager'),
    ('default', 'DOA_L3', 200000.00,  'USD', 'Senior Manager'),
    ('default', 'DOA_L4', NULL,       'USD', 'Executive')
ON CONFLICT (tenant_id, doa_level) DO NOTHING;

-- ============================================================
-- GET_EFFECTIVE_DOA_LEVEL FUNCTION (enterprise-only)
-- ============================================================

CREATE OR REPLACE FUNCTION get_effective_doa_level(p_user_id VARCHAR(255), p_check_time TIMESTAMP DEFAULT NOW())
RETURNS INT AS $$
DECLARE
    v_override_level INT;
BEGIN
    SELECT override_doa_level INTO v_override_level
    FROM doa_overrides
    WHERE user_id = p_user_id
      AND NOT revoked
      AND p_check_time BETWEEN valid_from AND valid_until
    ORDER BY override_doa_level DESC
    LIMIT 1;
    RETURN v_override_level;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_effective_doa_level IS 'Get effective DOA level for user, considering active overrides';

-- ============================================================
-- ENTERPRISE WORKFLOW ROLE MAPPINGS (asset_request workflow)
-- ============================================================

INSERT INTO workflow_role_mappings (workflow_key, task_key, task_name, required_roles, required_groups, custom_logic, description) VALUES
('asset_request', 'submit_request', 'Submit Asset Request',
    ARRAY['employee'], NULL, NULL,
    'Any employee can submit asset requests'),

('asset_request', 'line_manager_approval', 'Line Manager Approval',
    ARRAY['asset_request_approver'], NULL, 'manager_id_match',
    'Requires approval from submitter''s direct manager'),

('asset_request', 'it_approval', 'IT Department Approval',
    ARRAY['asset_request_approver'], ARRAY['/IT Department/Managers', '/IT Department/POC'], NULL,
    'Requires approval from IT department manager or POC'),

('asset_request', 'procurement_approval', 'Procurement Approval',
    ARRAY['procurement_approver'], ARRAY['/Procurement Department/Managers', '/Procurement Department/POC'], NULL,
    'Requires approval from Procurement department'),

('asset_request', 'finance_doa_approval', 'Finance DOA Approval',
    ARRAY['doa_approver_level1', 'doa_approver_level2', 'doa_approver_level3', 'doa_approver_level4'],
    ARRAY['/Finance Department/Approvers'], 'doa_level_check',
    'Requires approval from Finance with sufficient DOA level based on amount'),

('asset_request', 'hub_assignment', 'Warehouse Hub Assignment',
    ARRAY['hub_manager', 'central_hub_manager'], NULL, 'hub_match',
    'Assign to appropriate warehouse hub manager'),

('asset_request', 'asset_delivery', 'Asset Delivery',
    ARRAY['hub_manager', 'central_hub_manager'], NULL, NULL,
    'Mark asset as delivered to employee')
ON CONFLICT (workflow_key, task_key) DO NOTHING;

-- ============================================================
-- ENTERPRISE ROLE HIERARCHY
-- ============================================================

INSERT INTO role_hierarchy (parent_role, child_role, description) VALUES
('super_admin',      'admin',                  'Super admin inherits all admin permissions'),
('admin',            'workflow_designer',      'Admin can design workflows'),
('admin',            'workflow_admin',         'Admin can administer workflows'),
('hr_head',          'asset_request_approver', 'HR head can approve asset requests'),
('hr_head',          'doa_approver_level1',    'HR head has level 1 DOA'),
('it_head',          'asset_request_approver', 'IT head can approve asset requests'),
('finance_head',     'doa_approver_level4',    'Finance head has level 4 DOA'),
('finance_head',     'doa_approver_level3',    'Finance head has level 3 DOA'),
('central_hub_manager', 'hub_manager',         'Central hub manager has hub manager permissions')
ON CONFLICT (parent_role, child_role) DO NOTHING;

-- ============================================================
-- ENTERPRISE NOTIFICATION TEMPLATES
-- (task-assigned / leave-approved / leave-rejected already in OSS V1)
-- ============================================================

INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('capex-approved', 'email', 'Your CapEx Request Has Been Approved',
 'Dear $${requesterName},

Your Capital Expenditure request "$${title}" for $${amount} has been approved.

The budget has been reserved and you may proceed.

Best regards,
Finance Team')
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('capex-rejected', 'email', 'Your CapEx Request Has Been Declined',
 'Dear $${requesterName},

Your Capital Expenditure request "$${title}" has been declined.

Reason: $${reason}

Please reach out to your line manager if you have questions.

Best regards,
Finance Team')
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('asset-request-update', 'email', 'Asset Request Update: $${status}',
 'Dear $${requesterName},

Your request for $${assetType} has been updated.

Status: $${status}

Log in to the system to view full details.

Best regards,
IT / Procurement Team')
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('task-reminder', 'email', 'Reminder: Action Required on Your Task',
 'Hi,

This is a reminder that a task assigned to you is still awaiting action.

Task: $${taskName}
Process: $${processName}

Please log in and complete the task before the deadline.

Best regards,
Workflow System')
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('task-escalation', 'email', 'Task Escalated Due to SLA Breach',
 'Hi,

A task was not completed within the required SLA window and has been escalated.

Task: $${taskName}
Process: $${processName}

The task has been reassigned to a senior approver. No further action is required from you unless contacted.

Best regards,
Workflow System')
ON CONFLICT (template_key) DO NOTHING;

INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('committee-outcome', 'email', 'Committee Review Complete: $${proposalTitle}',
 'Hi,

All committee members have submitted their votes for your proposal.

Proposal: $${proposalTitle}
Category: $${proposalCategory}

Please log in to view the full outcome and next steps.

Best regards,
Workflow System')
ON CONFLICT (template_key) DO NOTHING;

-- ============================================================
-- ENTERPRISE FORM SCHEMAS
-- ============================================================

-- CapEx Request Form (final state: V9 schema — textarea justification, minLength 10)
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'capex-request', 1, 'CapEx Request Form',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "requesterName", "key": "requesterName", "label": "Requester Name", "validate": {"required": true}},
            {"type": "textfield", "id": "requesterEmail", "key": "requesterEmail", "label": "Email",
             "validate": {"required": true, "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"}},
            {"type": "textfield", "id": "departmentId", "key": "departmentId", "label": "Department ID", "validate": {"required": true}},
            {"type": "textfield", "id": "projectName", "key": "projectName", "label": "Project Name", "validate": {"required": true}},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "validate": {"required": true, "minLength": 10}},
            {"type": "number", "id": "requestAmount", "key": "requestAmount", "label": "Requested Amount ($)", "validate": {"required": true, "min": 1}},
            {"type": "select", "id": "category", "key": "category", "label": "Category", "validate": {"required": true},
             "values": [
                 {"label": "Equipment", "value": "EQUIPMENT"},
                 {"label": "Software", "value": "SOFTWARE"},
                 {"label": "Infrastructure", "value": "INFRASTRUCTURE"},
                 {"label": "Facility", "value": "FACILITY"},
                 {"label": "Other", "value": "OTHER"}
             ]},
            {"type": "textarea", "id": "justification", "key": "justification", "label": "Business Justification", "validate": {"required": true, "minLength": 10}},
            {"type": "textfield", "id": "expectedROI", "key": "expectedROI", "label": "Expected ROI (%)", "validate": {"required": false}}
        ]
    }'::jsonb,
    'Capital Expenditure Request Form',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- CapEx Approval Form (decision form used by approvers)
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'capex-approval', 1, 'CapEx Approval Form',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "text", "id": "requestSummary", "text": "### Review Capital Expenditure Request"},
            {"type": "textfield", "id": "projectName", "key": "projectName", "label": "Project Name", "disabled": true},
            {"type": "number", "id": "requestAmount", "key": "requestAmount", "label": "Requested Amount ($)", "disabled": true},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "disabled": true},
            {"type": "select", "id": "decision", "key": "approved", "label": "Decision", "validate": {"required": true},
             "values": [{"label": "Approve", "value": "true"}, {"label": "Reject", "value": "false"}]},
            {"type": "textarea", "id": "comments", "key": "approvalComments", "label": "Comments", "validate": {"required": true, "minLength": 10}}
        ]
    }'::jsonb,
    'CapEx Approval Form — used by approvers to make approval decisions',
    'APPROVAL', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- Asset Transfer Form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'asset-transfer', 1, 'Asset Transfer Form',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "assetId", "key": "assetId", "label": "Asset ID", "validate": {"required": true}},
            {"type": "textfield", "id": "assetName", "key": "assetName", "label": "Asset Name", "validate": {"required": true}},
            {"type": "select", "id": "assetType", "key": "assetType", "label": "Asset Type", "validate": {"required": true},
             "values": [
                 {"label": "Computer Equipment", "value": "COMPUTER"},
                 {"label": "Office Furniture", "value": "FURNITURE"},
                 {"label": "Vehicle", "value": "VEHICLE"},
                 {"label": "Machinery", "value": "MACHINERY"},
                 {"label": "Other", "value": "OTHER"}
             ]},
            {"type": "textfield", "id": "fromDepartment", "key": "fromDepartment", "label": "From Department", "validate": {"required": true}},
            {"type": "textfield", "id": "toDepartment", "key": "toDepartment", "label": "To Department", "validate": {"required": true}},
            {"type": "textfield", "id": "fromLocation", "key": "fromLocation", "label": "From Location", "validate": {"required": true}},
            {"type": "textfield", "id": "toLocation", "key": "toLocation", "label": "To Location", "validate": {"required": true}},
            {"type": "textfield", "id": "transferDate", "key": "transferDate", "label": "Transfer Date", "description": "Format: YYYY-MM-DD", "validate": {"required": true}},
            {"type": "textarea", "id": "reason", "key": "reason", "label": "Reason for Transfer", "validate": {"required": true, "minLength": 20}},
            {"type": "textfield", "id": "requestedBy", "key": "requestedBy", "label": "Requested By", "validate": {"required": true}}
        ]
    }'::jsonb,
    'Asset Transfer Form — request asset transfer between departments',
    'TASK_FORM', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- Asset Request Form (final state: V18 three-level cascade + V19 office locations + V20 deliveryDate subtype:date)
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'asset-request-form', 1, 'Asset Request Form',
    '{
        "type": "default",
        "components": [
            {
                "type": "text",
                "text": "<h3>Asset Request</h3><p>Select an asset type, category, and item, then complete the request details below.</p>"
            },
            {
                "type": "select",
                "key": "assetTypeId",
                "label": "Asset Type",
                "valuesKey": "assetTypeOptions",
                "validate": {"required": true},
                "properties": {
                    "dataSource": {
                        "url": "/api/business/asset-categories/root",
                        "labelField": "name",
                        "valueField": "id",
                        "valuesKey": "assetTypeOptions"
                    }
                }
            },
            {
                "type": "select",
                "key": "categoryId",
                "label": "Asset Category",
                "valuesKey": "categoryOptions",
                "validate": {"required": true},
                "properties": {
                    "dataSource": {
                        "url": "/api/business/asset-categories",
                        "labelField": "name",
                        "valueField": "id",
                        "valuesKey": "categoryOptions",
                        "dependsOn": "assetTypeId",
                        "dependsOnParam": "parentCategoryId"
                    }
                }
            },
            {
                "type": "select",
                "key": "assetDefinitionId",
                "label": "Asset",
                "valuesKey": "assetDefinitions",
                "validate": {"required": true},
                "properties": {
                    "dataSource": {
                        "url": "/api/business/asset-definitions",
                        "labelField": "name",
                        "valueField": "id",
                        "filter": {"active": true},
                        "valuesKey": "assetDefinitions",
                        "dependsOn": "categoryId",
                        "dependsOnParam": "categoryId"
                    }
                }
            },
            {
                "type": "number",
                "key": "quantity",
                "label": "Quantity",
                "defaultValue": 1,
                "validate": {"required": true, "min": 1}
            },
            {
                "type": "select",
                "key": "officeLocation",
                "label": "Office Location",
                "validate": {"required": true},
                "values": [
                    {"label": "Seattle, USA",         "value": "SEATTLE_US"},
                    {"label": "Bangalore, India",     "value": "BANGALORE_IN"},
                    {"label": "Shillong, India",      "value": "SHILLONG_IN"},
                    {"label": "Stockholm, Sweden",    "value": "STOCKHOLM_SE"},
                    {"label": "Melbourne, Australia", "value": "MELBOURNE_AU"}
                ]
            },
            {
                "type": "datetime",
                "key": "deliveryDate",
                "label": "Required By Date (Optional)",
                "subtype": "date"
            },
            {
                "type": "textarea",
                "key": "justification",
                "label": "Justification",
                "validate": {"required": true, "minLength": 10}
            },
            {
                "type": "textfield",
                "key": "requesterName",
                "label": "Your Name",
                "readonly": true,
                "validate": {"required": false}
            },
            {
                "type": "textfield",
                "key": "requesterEmail",
                "label": "Your Email",
                "readonly": true,
                "validate": {"required": false}
            }
        ]
    }'::jsonb,
    'Asset Request Form — three-level cascade (type→category→asset), enterprise office locations',
    'TASK_FORM', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- SLA Escalation Forms (V26)
INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'sla-escalation-form', 1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "title", "key": "title", "label": "Item Title", "validate": {"required": true, "maxLength": 100}},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "validate": {"required": true, "minLength": 10}},
            {"type": "select", "id": "priority", "key": "priority", "label": "Priority", "validate": {"required": true},
             "values": [
                 {"label": "Low", "value": "LOW"},
                 {"label": "Medium", "value": "MEDIUM"},
                 {"label": "High", "value": "HIGH"},
                 {"label": "Critical", "value": "CRITICAL"}
             ]},
            {"type": "textfield", "id": "reviewerGroup", "key": "reviewerGroup", "label": "Reviewer Group (optional)",
             "description": "Candidate group ID to assign the review task to", "validate": {"required": false}}
        ]
    }'::jsonb,
    'SLA Escalation Form — process start form for the SLA escalation sample workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'sla-escalation-decision', 1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "text", "id": "reviewHeader", "text": "### Review Item"},
            {"type": "textfield", "id": "title", "key": "title", "label": "Item Title", "disabled": true},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "disabled": true},
            {"type": "textfield", "id": "priority", "key": "priority", "label": "Priority", "disabled": true},
            {"type": "select", "id": "decisionField", "key": "decision", "label": "Decision", "validate": {"required": true},
             "values": [
                 {"label": "Approve", "value": "approve"},
                 {"label": "Reject", "value": "reject"},
                 {"label": "Request More Info", "value": "more_info"}
             ]},
            {"type": "textarea", "id": "reviewComments", "key": "reviewComments", "label": "Comments", "validate": {"required": false}}
        ]
    }'::jsonb,
    'SLA Escalation Decision Form — used by both Review Item and Escalated Review tasks',
    'APPROVAL', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- Parallel Committee Approval Forms (V27)
INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'committee-review-start', 1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "proposalTitle", "key": "proposalTitle", "label": "Proposal Title", "validate": {"required": true, "maxLength": 150}},
            {"type": "textarea", "id": "proposalDescription", "key": "proposalDescription", "label": "Proposal Description", "validate": {"required": true, "minLength": 20}},
            {"type": "select", "id": "proposalCategory", "key": "proposalCategory", "label": "Category", "validate": {"required": true},
             "values": [
                 {"label": "Policy", "value": "POLICY"},
                 {"label": "Budget", "value": "BUDGET"},
                 {"label": "Strategic Initiative", "value": "STRATEGIC"},
                 {"label": "Operational Change", "value": "OPERATIONAL"},
                 {"label": "Other", "value": "OTHER"}
             ]},
            {"type": "textfield", "id": "attachments", "key": "attachments", "label": "Supporting Documents (optional)",
             "description": "Comma-separated document references or URLs", "validate": {"required": false}}
        ]
    }'::jsonb,
    'Committee Review Start Form — process start form for the parallel committee approval workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'committee-review-decision', 1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "text", "id": "reviewHeader", "text": "### Committee Member Vote"},
            {"type": "textfield", "id": "proposalTitle", "key": "proposalTitle", "label": "Proposal Title", "disabled": true},
            {"type": "textarea", "id": "proposalDescription", "key": "proposalDescription", "label": "Proposal Description", "disabled": true},
            {"type": "textfield", "id": "proposalCategory", "key": "proposalCategory", "label": "Category", "disabled": true},
            {"type": "select", "id": "voteField", "key": "vote", "label": "Vote", "validate": {"required": true},
             "values": [
                 {"label": "Approve", "value": "approve"},
                 {"label": "Reject", "value": "reject"},
                 {"label": "Abstain", "value": "abstain"}
             ]},
            {"type": "textarea", "id": "voteComments", "key": "voteComments", "label": "Comments",
             "description": "Rationale or conditions attached to your vote", "validate": {"required": false}}
        ]
    }'::jsonb,
    'Committee Review Decision Form — used by all three committee member review tasks',
    'APPROVAL', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- DOA Routing Forms (V28)
INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'doa-request-form', 1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "requestTitle", "key": "requestTitle", "label": "Request Title", "validate": {"required": true, "maxLength": 150}},
            {"type": "number", "id": "requestAmount", "key": "requestAmount", "label": "Request Amount", "validate": {"required": true, "min": 0}},
            {"type": "select", "id": "department", "key": "department", "label": "Department", "validate": {"required": true},
             "values": [
                 {"label": "Finance", "value": "FINANCE"},
                 {"label": "IT", "value": "IT"},
                 {"label": "Operations", "value": "OPERATIONS"},
                 {"label": "HR", "value": "HR"},
                 {"label": "Legal", "value": "LEGAL"},
                 {"label": "Other", "value": "OTHER"}
             ]},
            {"type": "textarea", "id": "justification", "key": "justification", "label": "Business Justification", "validate": {"required": true, "minLength": 20}}
        ]
    }'::jsonb,
    'DoA Request Form — process start form for the DoA routing sample workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'doa-approval-form', 1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "text", "id": "approvalHeader", "text": "### Spend Request Review"},
            {"type": "textfield", "id": "requestTitle", "key": "requestTitle", "label": "Request Title", "disabled": true},
            {"type": "number", "id": "requestAmount", "key": "requestAmount", "label": "Request Amount", "disabled": true},
            {"type": "textfield", "id": "department", "key": "department", "label": "Department", "disabled": true},
            {"type": "textarea", "id": "justification", "key": "justification", "label": "Business Justification", "disabled": true},
            {"type": "select", "id": "approvalDecision", "key": "approvalDecision", "label": "Decision", "validate": {"required": true},
             "values": [
                 {"label": "Approve", "value": "APPROVED"},
                 {"label": "Reject", "value": "REJECTED"},
                 {"label": "Request More Information", "value": "MORE_INFO"}
             ]},
            {"type": "textarea", "id": "approvalComments", "key": "approvalComments", "label": "Comments", "validate": {"required": false}}
        ]
    }'::jsonb,
    'DoA Approval Form — used by all four approval tasks in the DoA routing workflow',
    'APPROVAL', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ============================================================
-- ERP SERVICE REGISTRY SEED (HR, Finance, Procurement, Inventory)
-- ============================================================

INSERT INTO service_registry (service_name, display_name, description, service_type, version, is_active, health_status, metadata, created_by) VALUES
('hr-service',          'Human Resources Service',        'Manages employee data, leave requests, payroll, and HR workflows',             'BUSINESS', '1.0.0', true, 'UNKNOWN', '{"department": "HR", "owner": "hr-team@werkflow.com", "cost_center": "CC-1001"}'::jsonb,          'system'),
('finance-service',     'Finance & Accounting Service',   'Handles invoicing, payments, expense tracking, and financial reporting',        'BUSINESS', '1.0.0', true, 'UNKNOWN', '{"department": "Finance", "owner": "finance-team@werkflow.com", "cost_center": "CC-2001"}'::jsonb,  'system'),
('procurement-service', 'Procurement Service',            'Manages purchase orders, vendor management, and procurement workflows',         'BUSINESS', '1.0.0', true, 'UNKNOWN', '{"department": "Operations", "owner": "procurement-team@werkflow.com", "cost_center": "CC-3001"}'::jsonb, 'system'),
('inventory-service',   'Inventory Management Service',   'Tracks inventory levels, stock movements, and warehouse operations',            'BUSINESS', '1.0.0', true, 'UNKNOWN', '{"department": "Operations", "owner": "inventory-team@werkflow.com", "cost_center": "CC-3002"}'::jsonb, 'system')
ON CONFLICT (service_name) DO NOTHING;

-- Environment URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8082', true, 100, true, 'http://localhost:8082/actuator/health', 60
FROM service_registry WHERE service_name = 'hr-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://hr-service:8082', true, 100, true, 'http://hr-service:8082/actuator/health', 60
FROM service_registry WHERE service_name = 'hr-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://hr-api.werkflow.com', true, 100, true, 'https://hr-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'hr-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8084', true, 100, true, 'http://localhost:8084/actuator/health', 60
FROM service_registry WHERE service_name = 'finance-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://finance-service:8084', true, 100, true, 'http://finance-service:8084/actuator/health', 60
FROM service_registry WHERE service_name = 'finance-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://finance-api.werkflow.com', true, 100, true, 'https://finance-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'finance-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8085', true, 100, true, 'http://localhost:8085/actuator/health', 60
FROM service_registry WHERE service_name = 'procurement-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://procurement-service:8085', true, 100, true, 'http://procurement-service:8085/actuator/health', 60
FROM service_registry WHERE service_name = 'procurement-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://procurement-api.werkflow.com', true, 100, true, 'https://procurement-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'procurement-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8086', true, 100, true, 'http://localhost:8086/actuator/health', 60
FROM service_registry WHERE service_name = 'inventory-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://inventory-service:8086', true, 100, true, 'http://inventory-service:8086/actuator/health', 60
FROM service_registry WHERE service_name = 'inventory-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://inventory-api.werkflow.com', true, 100, true, 'https://inventory-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'inventory-service' ON CONFLICT (service_id, environment) DO NOTHING;

-- Finance service example endpoints
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT id, '/api/invoices',         'GET',  'List all invoices',    true, false, 30000, 3, true FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT id, '/api/invoices',         'POST', 'Create new invoice',   true, false, 30000, 3, true FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT id, '/api/invoices/{id}',    'GET',  'Get invoice by ID',    true, false, 30000, 3, true FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT id, '/api/invoices/{id}',    'PUT',  'Update invoice',       true, false, 30000, 3, true FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT id, '/api/invoices/{id}/approve', 'POST', 'Approve invoice', true, false, 30000, 3, true FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT id, '/api/payments',         'POST', 'Process payment',      true, false, 60000, 2, true FROM service_registry WHERE service_name = 'finance-service';

-- Service tags
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'department', 'HR'          FROM service_registry WHERE service_name = 'hr-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'team', 'hr-platform'       FROM service_registry WHERE service_name = 'hr-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'priority', 'high'          FROM service_registry WHERE service_name = 'hr-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'department', 'Finance'      FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'team', 'finance-platform'  FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'priority', 'critical'      FROM service_registry WHERE service_name = 'finance-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'department', 'Operations'  FROM service_registry WHERE service_name = 'procurement-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'team', 'procurement-platform' FROM service_registry WHERE service_name = 'procurement-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'priority', 'medium'        FROM service_registry WHERE service_name = 'procurement-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'department', 'Operations'  FROM service_registry WHERE service_name = 'inventory-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'team', 'inventory-platform' FROM service_registry WHERE service_name = 'inventory-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'priority', 'medium'        FROM service_registry WHERE service_name = 'inventory-service';
