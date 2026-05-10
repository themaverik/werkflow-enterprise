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
-- Canonical 12 forms matching classpath forms/ JSON files.
-- Stale forms (asset-transfer, capex-approval, sla-*, committee-*,
-- doa-*) are NOT inserted here — they were removed from the
-- canonical set. V8__cleanup_and_seed_catalogue.sql removes them
-- from running DBs and upserts all 12 canonical forms.
-- ============================================================

-- 1. asset-request-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'asset-request-form', 1, 'Asset Request Form',
    '{
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Asset Request</h3><p>Select an asset category and item, then complete the request details below.</p>"
        },
        {
          "type": "select",
          "key": "categoryId",
          "label": "Asset Category",
          "valuesKey": "categoryOptions",
          "validate": { "required": true },
          "properties": {
            "dataSource": {
              "url": "/api/business/asset-categories",
              "labelField": "name",
              "valueField": "id",
              "filter": { "active": true }
            }
          }
        },
        {
          "type": "select",
          "key": "assetDefinitionId",
          "label": "Asset",
          "valuesExpression": "= assetDefinitions[item.categoryId = categoryId]",
          "validate": { "required": true },
          "properties": {
            "dataSource": {
              "url": "/api/business/asset-definitions",
              "labelField": "name",
              "valueField": "id",
              "extraFields": ["categoryId"],
              "filter": { "active": true },
              "dependsOn": "categoryId",
              "dependsOnParam": "categoryId",
              "valuesKey": "assetDefinitions"
            }
          }
        },
        {
          "type": "number",
          "key": "quantity",
          "label": "Quantity",
          "defaultValue": 1,
          "validate": { "required": true, "min": 1 }
        },
        {
          "type": "select",
          "key": "officeLocation",
          "label": "Office Location",
          "validate": { "required": true },
          "values": [
            { "label": "Seattle, USA", "value": "SEATTLE_US" },
            { "label": "Bangalore, India", "value": "BANGALORE_IN" },
            { "label": "Shillong, India", "value": "SHILLONG_IN" },
            { "label": "Stockholm, Sweden", "value": "STOCKHOLM_SE" },
            { "label": "Melbourne, Australia", "value": "MELBOURNE_AU" }
          ]
        },
        {
          "type": "datetime",
          "subtype": "date",
          "key": "deliveryDate",
          "label": "Required By Date (Optional)"
        },
        {
          "type": "textarea",
          "key": "justification",
          "label": "Justification",
          "validate": { "required": true, "minLength": 10 }
        },
        {
          "type": "textfield",
          "key": "requesterName",
          "label": "Your Name",
          "readonly": true,
          "validate": { "required": false }
        },
        {
          "type": "textfield",
          "key": "requesterEmail",
          "label": "Your Email",
          "readonly": true,
          "validate": { "required": false }
        }
      ]
    }'::jsonb,
    'Asset Request Form — category cascade + enterprise office locations',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 2. capex-request-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'capex-request-form', 1, 'CapEx Request Form',
    '{
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Capital Expenditure Request Form</h3><p>Please provide details for your CapEx request.</p>"
        },
        {
          "type": "textfield",
          "key": "requestTitle",
          "label": "Request Title",
          "placeholder": "Enter request title",
          "validate": { "required": true, "minLength": 5, "maxLength": 100 }
        },
        {
          "type": "select",
          "key": "category",
          "label": "Category",
          "values": [
            { "label": "Equipment", "value": "equipment" },
            { "label": "Software", "value": "software" },
            { "label": "Infrastructure", "value": "infrastructure" },
            { "label": "Facility", "value": "facility" },
            { "label": "Other", "value": "other" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "number",
          "key": "requestAmount",
          "label": "Request Amount (USD)",
          "placeholder": "Enter amount",
          "validate": { "required": true, "min": 1, "max": 10000000 }
        },
        {
          "type": "textfield",
          "key": "department",
          "label": "Department",
          "placeholder": "Enter department",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "costCenter",
          "label": "Cost Center",
          "placeholder": "Enter cost center code",
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "description",
          "label": "Description",
          "placeholder": "Describe the expenditure in detail",
          "validate": { "required": true, "minLength": 20, "maxLength": 2000 }
        },
        {
          "type": "textarea",
          "key": "businessJustification",
          "label": "Business Justification",
          "placeholder": "Explain why this expenditure is necessary",
          "validate": { "required": true, "minLength": 50, "maxLength": 2000 }
        },
        {
          "type": "textarea",
          "key": "expectedBenefits",
          "label": "Expected Benefits",
          "placeholder": "List expected benefits and ROI",
          "validate": { "required": true, "minLength": 20 }
        },
        {
          "type": "select",
          "key": "priority",
          "label": "Priority",
          "values": [
            { "label": "Low", "value": "low" },
            { "label": "Medium", "value": "medium" },
            { "label": "High", "value": "high" },
            { "label": "Critical", "value": "critical" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "date",
          "key": "requiredByDate",
          "label": "Required By Date",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "vendor",
          "label": "Preferred Vendor (Optional)",
          "placeholder": "Enter vendor name if known"
        },
        {
          "type": "select",
          "key": "fundingSource",
          "label": "Funding Source",
          "values": [
            { "label": "Operating Budget", "value": "operating" },
            { "label": "Capital Budget", "value": "capital" },
            { "label": "Grant", "value": "grant" },
            { "label": "External Funding", "value": "external" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "checkbox",
          "key": "budgetApproved",
          "label": "Budget has been pre-approved",
          "validate": { "required": false }
        },
        {
          "type": "textfield",
          "key": "projectCode",
          "label": "Project Code (if applicable)",
          "placeholder": "Enter project code"
        },
        {
          "type": "number",
          "key": "estimatedMaintenanceCost",
          "label": "Annual Maintenance Cost (USD)",
          "placeholder": "Enter estimated annual maintenance cost",
          "validate": { "min": 0 }
        },
        {
          "type": "number",
          "key": "usefulLife",
          "label": "Expected Useful Life (Years)",
          "placeholder": "Enter expected useful life in years",
          "validate": { "required": true, "min": 1, "max": 50 }
        },
        {
          "type": "select",
          "key": "depreciationMethod",
          "label": "Depreciation Method",
          "values": [
            { "label": "Straight Line", "value": "straight_line" },
            { "label": "Declining Balance", "value": "declining_balance" },
            { "label": "Units of Production", "value": "units_production" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "alternativesConsidered",
          "label": "Alternatives Considered",
          "placeholder": "Describe alternative options considered",
          "validate": { "minLength": 20 }
        },
        {
          "type": "textarea",
          "key": "riskAssessment",
          "label": "Risk Assessment",
          "placeholder": "Identify potential risks and mitigation strategies",
          "validate": { "required": true, "minLength": 20 }
        },
        {
          "type": "textfield",
          "key": "requestorName",
          "label": "Requestor Name",
          "placeholder": "Enter your full name",
          "validate": { "required": true }
        },
        {
          "type": "email",
          "key": "requestorEmail",
          "label": "Requestor Email",
          "placeholder": "Enter your email",
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "additionalComments",
          "label": "Additional Comments",
          "placeholder": "Any additional information"
        }
      ]
    }'::jsonb,
    'Capital Expenditure Request Form — full CapEx submission form',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 3. event-ticket-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'event-ticket-form', 1, 'Event Ticket Request Form',
    '{
      "id": "event-ticket-form",
      "name": "Event Ticket Request Form",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Event Ticket Request</h3><p>Submit a request to attend or participate in an event.</p>"
        },
        {
          "type": "textfield",
          "key": "eventName",
          "label": "Event Name",
          "placeholder": "Enter the full name of the event",
          "validate": { "required": true, "minLength": 3, "maxLength": 150 }
        },
        {
          "type": "date",
          "key": "eventDate",
          "label": "Event Date",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "venue",
          "label": "Venue",
          "placeholder": "Enter the event venue or location",
          "validate": { "required": true }
        },
        {
          "type": "number",
          "key": "ticketCount",
          "label": "Number of Tickets",
          "placeholder": "Enter the number of tickets required",
          "validate": { "required": true, "min": 1, "max": 100 }
        },
        {
          "type": "select",
          "key": "purpose",
          "label": "Purpose",
          "values": [
            { "label": "Conference", "value": "conference" },
            { "label": "Workshop", "value": "workshop" },
            { "label": "Team Building", "value": "team_building" },
            { "label": "Client Entertainment", "value": "client_entertainment" },
            { "label": "Other", "value": "other" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "number",
          "key": "estimatedCost",
          "label": "Estimated Cost (USD)",
          "placeholder": "Enter total estimated cost for all tickets",
          "validate": { "required": true, "min": 0 }
        },
        {
          "type": "textarea",
          "key": "justification",
          "label": "Justification",
          "placeholder": "Explain the business value or personal development benefit of attending",
          "validate": { "required": true, "minLength": 10, "maxLength": 1000 }
        },
        {
          "type": "textfield",
          "key": "requestedBy",
          "label": "Requested By",
          "placeholder": "Enter your full name",
          "validate": { "required": true }
        }
      ]
    }'::jsonb,
    'Event Ticket Request Form — employee event attendance request',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 4. budget-request-form (schema from finance-approval-form.json)
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'budget-request-form', 1, 'Budget Request Form',
    '{
      "id": "finance-approval-form",
      "name": "Finance Approval Form",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Finance Approval Review</h3><p>Review the purchase details and provide your finance decision below.</p>"
        },
        {
          "type": "textarea",
          "key": "reviewNotes",
          "label": "Review Notes",
          "placeholder": "Enter your review observations and comments",
          "validate": { "required": true, "minLength": 10, "maxLength": 2000 }
        },
        {
          "type": "number",
          "key": "approvedAmount",
          "label": "Approved Amount (USD)",
          "placeholder": "Enter the amount approved (may differ from requested)",
          "validate": { "required": true, "min": 0 }
        },
        {
          "type": "textfield",
          "key": "budgetCode",
          "label": "Budget Code",
          "placeholder": "Enter the budget allocation code",
          "validate": { "required": true }
        },
        {
          "type": "select",
          "key": "paymentTerms",
          "label": "Payment Terms",
          "values": [
            { "label": "Net 30", "value": "net30" },
            { "label": "Net 60", "value": "net60" },
            { "label": "Immediate", "value": "immediate" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "select",
          "key": "decision",
          "label": "Decision",
          "values": [
            { "label": "Approved", "value": "approve" },
            { "label": "Rejected", "value": "reject" }
          ],
          "validate": { "required": true }
        }
      ]
    }'::jsonb,
    'Budget Request Form — finance approval decision form for budget requests',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 5. leave-request-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'leave-request-form', 1, 'Leave Request Form',
    '{
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Leave Request Form</h3><p>Submit your leave request for approval.</p>"
        },
        {
          "type": "select",
          "key": "leaveType",
          "label": "Leave Type",
          "values": [
            { "label": "Annual Leave", "value": "annual" },
            { "label": "Sick Leave", "value": "sick" },
            { "label": "Personal Leave", "value": "personal" },
            { "label": "Parental Leave", "value": "parental" },
            { "label": "Bereavement Leave", "value": "bereavement" },
            { "label": "Unpaid Leave", "value": "unpaid" },
            { "label": "Compensatory Leave", "value": "compensatory" },
            { "label": "Study Leave", "value": "study" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "date",
          "key": "startDate",
          "label": "Start Date",
          "validate": { "required": true }
        },
        {
          "type": "date",
          "key": "endDate",
          "label": "End Date",
          "validate": { "required": true }
        },
        {
          "type": "number",
          "key": "totalDays",
          "label": "Total Days",
          "placeholder": "Calculate total days",
          "validate": { "required": true, "min": 0.5, "max": 365 }
        },
        {
          "type": "textarea",
          "key": "reason",
          "label": "Reason for Leave",
          "placeholder": "Enter reason for leave request",
          "validate": { "required": true, "minLength": 10, "maxLength": 500 }
        },
        {
          "type": "textfield",
          "key": "contactNumber",
          "label": "Contact Number During Leave",
          "placeholder": "Enter contact number",
          "validate": { "required": true }
        },
        {
          "type": "email",
          "key": "emergencyEmail",
          "label": "Emergency Email",
          "placeholder": "Enter emergency contact email",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "employeeId",
          "label": "Employee ID",
          "placeholder": "Enter your employee ID",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "employeeName",
          "label": "Employee Name",
          "placeholder": "Enter your full name",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "department",
          "label": "Department",
          "placeholder": "Enter your department",
          "validate": { "required": true }
        }
      ]
    }'::jsonb,
    'Leave Request Form — employee leave submission form',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 6. procurement-request-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'procurement-request-form', 1, 'Procurement Request Form',
    '{
      "id": "procurement-request-form",
      "name": "Procurement Request Form",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Procurement Request</h3><p>Submit a procurement request for goods or services requiring vendor sourcing and approval.</p>"
        },
        {
          "type": "textfield",
          "key": "title",
          "label": "Request Title",
          "placeholder": "Enter a concise title for this procurement request",
          "validate": { "required": true, "minLength": 5, "maxLength": 100 }
        },
        {
          "type": "textarea",
          "key": "description",
          "label": "Description",
          "placeholder": "Describe the goods or services to be procured",
          "validate": { "required": true, "minLength": 10, "maxLength": 2000 }
        },
        {
          "type": "number",
          "key": "requestedAmount",
          "label": "Requested Amount (USD)",
          "placeholder": "Enter estimated total amount",
          "validate": { "required": true, "min": 1, "max": 100000000 }
        },
        {
          "type": "textfield",
          "key": "vendor",
          "label": "Preferred Vendor",
          "placeholder": "Enter preferred vendor name if known",
          "validate": { "required": false }
        },
        {
          "type": "textarea",
          "key": "justification",
          "label": "Business Justification",
          "placeholder": "Explain why this procurement is necessary and its business impact",
          "validate": { "required": true, "minLength": 20, "maxLength": 2000 }
        },
        {
          "type": "textfield",
          "key": "department",
          "label": "Requesting Department",
          "placeholder": "Enter your department name",
          "validate": { "required": true }
        },
        {
          "type": "date",
          "key": "requiredDate",
          "label": "Required By Date",
          "validate": { "required": true }
        },
        {
          "type": "select",
          "key": "priority",
          "label": "Priority",
          "values": [
            { "label": "Low", "value": "low" },
            { "label": "Medium", "value": "medium" },
            { "label": "High", "value": "high" },
            { "label": "Urgent", "value": "urgent" }
          ],
          "validate": { "required": true }
        }
      ]
    }'::jsonb,
    'Procurement Request Form — process start form for procurement approval workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 7. vendor-selection
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'vendor-selection', 1, 'Vendor Selection Form',
    '{
      "id": "vendor-selection",
      "name": "Vendor Selection",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Select Vendor</h3><p>Review the list of approved vendors and select the preferred supplier for this purchase request.</p>"
        },
        {
          "type": "textfield",
          "key": "selectedVendorId",
          "label": "Selected Vendor ID",
          "placeholder": "Enter the vendor ID from the approved vendor list",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "selectedVendorName",
          "label": "Vendor Name",
          "placeholder": "Enter the vendor name",
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "vendorSelectionNotes",
          "label": "Selection Notes",
          "placeholder": "Provide justification for vendor selection",
          "validate": { "maxLength": 1000 }
        }
      ]
    }'::jsonb,
    'Vendor Selection Form — procurement workflow task form for vendor selection',
    'TASK_FORM', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 8. quotation-review
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'quotation-review', 1, 'Quotation Review Form',
    '{
      "id": "quotation-review",
      "name": "Quotation Review",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Review Quotations</h3><p>Compare vendor quotations and select the best offer. Enter the winning quotation ID to proceed.</p>"
        },
        {
          "type": "textfield",
          "key": "selectedQuotationId",
          "label": "Selected Quotation ID",
          "placeholder": "Enter the ID of the accepted quotation",
          "validate": { "required": true }
        },
        {
          "type": "number",
          "key": "agreedUnitPrice",
          "label": "Agreed Unit Price",
          "validate": { "required": true, "min": 0 }
        },
        {
          "type": "textarea",
          "key": "quotationReviewNotes",
          "label": "Review Notes",
          "placeholder": "Summarise your quotation comparison and selection rationale",
          "validate": { "required": true, "minLength": 5, "maxLength": 2000 }
        }
      ]
    }'::jsonb,
    'Quotation Review Form — procurement workflow task form for quotation evaluation',
    'TASK_FORM', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- 9. procurement-approval
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'procurement-approval', 1, 'Procurement Approval Form',
    '{
      "id": "procurement-approval",
      "name": "Procurement Approval",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Procurement Approval</h3><p>Review the purchase request details and provide your approval decision.</p>"
        },
        {
          "type": "select",
          "key": "decision",
          "label": "Decision",
          "values": [
            { "label": "Approve", "value": "approve" },
            { "label": "Reject", "value": "reject" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "approvalComments",
          "label": "Comments",
          "placeholder": "Enter your review comments and justification",
          "validate": { "required": true, "minLength": 5, "maxLength": 2000 }
        },
        {
          "type": "textfield",
          "key": "purchaseOrderReference",
          "label": "Purchase Order Reference",
          "placeholder": "Enter PO number if approving (optional)",
          "validate": { "maxLength": 100 }
        }
      ]
    }'::jsonb,
    'Procurement Approval Form — approval decision form for procurement workflow',
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
