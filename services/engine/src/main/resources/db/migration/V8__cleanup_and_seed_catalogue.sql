-- ============================================================
-- WERKFLOW ENGINE — CATALOGUE CLEANUP AND CANONICAL SEED
-- ============================================================
-- Purpose: Reconcile process_draft and form_schemas with the
--   8 canonical enterprise BPMN files that exist on classpath.
--   Removes stale processes/forms from deleted workflows and
--   upserts all 8 canonical processes + 12 canonical forms
--   with full schema content.
--
-- Application: Apply MANUALLY via psql to the running DB.
--   psql -U werkflow_admin -d werkflow -f V8__cleanup_and_seed_catalogue.sql
--
-- Does NOT touch: Flyway schema_version checksum of V1 or V2.
-- Safe to re-run: All writes use ON CONFLICT DO UPDATE/NOTHING.
-- ============================================================

BEGIN;

-- ============================================================
-- 3a. Remove stale Flowable process deployments
-- Flowable cascades: delete act_re_procdef first (FK to deployment),
-- then act_re_deployment. act_ru_* and act_hi_* tables also have
-- FKs — this targets only definition-level cleanup (no running instances).
-- ============================================================

DELETE FROM act_re_procdef
WHERE key_ IN (
    'asset-transfer-approval-process',
    'document-review',
    'sla-escalation',           -- actual deployed key (not sla-escalation-process)
    'sla-escalation-process',   -- guard: in case older deploy used this key
    'doa-routing-process',
    'parallel-committee-approval',
    'capex-approval-process-v2' -- old v2 suffix deploy; replaced by capex-approval-process
);

DELETE FROM act_re_deployment
WHERE id_ NOT IN (
    SELECT DISTINCT deployment_id_ FROM act_re_procdef
)
AND name_ IN (
    'asset-transfer-approval-process',
    'document-review',
    'sla-escalation',
    'sla-escalation-process',
    'doa-routing-process',
    'parallel-committee-approval',
    'capex-approval-process-v2'
);

-- ============================================================
-- 3b. Remove stale process_draft entries
-- ============================================================

DELETE FROM process_draft
WHERE process_key IN (
    'asset-transfer-approval-process',
    'document-review',
    'sla-escalation',
    'sla-escalation-process',
    'doa-routing-process',
    'parallel-committee-approval',
    'capex-approval-process-v2'
);

-- ============================================================
-- 3c. Clean up orphaned form_schemas
-- budget-request-form deleted here so it can be re-inserted
-- with the correct schema sourced from finance-approval-form.json
-- ============================================================

DELETE FROM form_schemas
WHERE form_key IN (
    'asset-transfer',
    'capex-approval',
    'document-review-form',
    'sla-escalation-form',
    'sla-escalation-decision',
    'committee-review-start',
    'committee-review-decision',
    'doa-request-form',
    'doa-approval-form',
    'equipment-request-form',
    'budget-request-form'
);

-- ============================================================
-- 3d. Rename capex-request key to capex-request-form
-- Aligns with the formKey used in capex-approval-process.bpmn20.xml
-- ============================================================

UPDATE form_schemas
SET form_key = 'capex-request-form'
WHERE form_key = 'capex-request';

-- ============================================================
-- 3e. Upsert all 12 canonical forms with full schemas
-- ON CONFLICT upgrades any minimal/stale records that exist
-- (e.g. event-ticket-form created via UI with empty schema).
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
    'Asset Request Form — category cascade + office locations',
    'PROCESS_START', true, 'system', 'system'
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

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
          "text": "<h3>Event Ticket Request</h3><p>Submit a request to attend or participate in an event. Provide complete details to allow the coordinator to evaluate the request.</p>"
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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 4. budget-request-form (schema sourced from finance-approval-form.json)
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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 5. general-approval-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'general-approval-form', 1, 'General Approval Form',
    '{
      "id": "general-approval-form",
      "name": "General Approval Form",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>General Approval Request</h3><p>Submit a request for approval. Provide a clear title, description, and justification to help approvers make an informed decision.</p>"
        },
        {
          "type": "textfield",
          "key": "title",
          "label": "Request Title",
          "placeholder": "Enter a concise title for your request",
          "validate": { "required": true, "minLength": 5, "maxLength": 100 }
        },
        {
          "type": "textarea",
          "key": "description",
          "label": "Description",
          "placeholder": "Describe what you are requesting and the context",
          "validate": { "required": true, "minLength": 10, "maxLength": 2000 }
        },
        {
          "type": "select",
          "key": "requestType",
          "label": "Request Type",
          "values": [
            { "label": "Leave", "value": "leave" },
            { "label": "Expense", "value": "expense" },
            { "label": "Access", "value": "access" },
            { "label": "Other", "value": "other" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "justification",
          "label": "Justification",
          "placeholder": "Explain why this request should be approved",
          "validate": { "required": true, "minLength": 10, "maxLength": 1000 }
        },
        {
          "type": "number",
          "key": "amount",
          "label": "Amount (USD)",
          "placeholder": "Enter estimated cost (0 if not applicable)",
          "validate": { "required": true, "min": 0 }
        },
        {
          "type": "select",
          "key": "priority",
          "label": "Priority",
          "values": [
            { "label": "Low", "value": "low" },
            { "label": "Medium", "value": "medium" },
            { "label": "High", "value": "high" }
          ],
          "validate": { "required": true }
        }
      ]
    }'::jsonb,
    'General Approval Form — process start form for the general approval workflow',
    'PROCESS_START', true, 'system', 'system'
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 6. general-approval-decision
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'general-approval-decision', 1, 'General Approval Decision Form',
    '{
      "id": "general-approval-decision",
      "name": "Approval Decision",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Approval Decision</h3><p>Review the request details and enter your decision below.</p>"
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
          "key": "comments",
          "label": "Comments",
          "placeholder": "Provide justification for your decision",
          "validate": { "required": true, "minLength": 5, "maxLength": 2000 }
        }
      ]
    }'::jsonb,
    'General Approval Decision Form — used by Manager and Director approval tasks',
    'APPROVAL', true, 'system', 'system'
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 7. leave-request-form
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
          "type": "select",
          "key": "startDatePeriod",
          "label": "Start Date Period",
          "values": [
            { "label": "Full Day", "value": "full_day" },
            { "label": "Morning", "value": "morning" },
            { "label": "Afternoon", "value": "afternoon" }
          ],
          "validate": { "required": true }
        },
        {
          "type": "select",
          "key": "endDatePeriod",
          "label": "End Date Period",
          "values": [
            { "label": "Full Day", "value": "full_day" },
            { "label": "Morning", "value": "morning" },
            { "label": "Afternoon", "value": "afternoon" }
          ],
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
          "key": "coveringPerson",
          "label": "Covering Person",
          "placeholder": "Name of person covering your responsibilities"
        },
        {
          "type": "email",
          "key": "coveringPersonEmail",
          "label": "Covering Person Email",
          "placeholder": "Email of covering person"
        },
        {
          "type": "checkbox",
          "key": "hasMedicalCertificate",
          "label": "Medical certificate attached (for sick leave)",
          "conditional": { "when": "leaveType", "eq": "sick" }
        },
        {
          "type": "textfield",
          "key": "medicalPractitioner",
          "label": "Medical Practitioner Name",
          "placeholder": "Name of doctor/clinic",
          "conditional": { "when": "leaveType", "eq": "sick" }
        },
        {
          "type": "date",
          "key": "medicalCertificateDate",
          "label": "Medical Certificate Date",
          "conditional": { "when": "leaveType", "eq": "sick" }
        },
        {
          "type": "textfield",
          "key": "relationship",
          "label": "Relationship to Deceased",
          "placeholder": "Enter relationship",
          "conditional": { "when": "leaveType", "eq": "bereavement" },
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "institutionName",
          "label": "Educational Institution",
          "placeholder": "Enter institution name",
          "conditional": { "when": "leaveType", "eq": "study" }
        },
        {
          "type": "textfield",
          "key": "courseTitle",
          "label": "Course/Program Title",
          "placeholder": "Enter course title",
          "conditional": { "when": "leaveType", "eq": "study" }
        },
        {
          "type": "number",
          "key": "currentBalance",
          "label": "Current Leave Balance",
          "placeholder": "Your current leave balance",
          "disabled": true
        },
        {
          "type": "number",
          "key": "balanceAfterLeave",
          "label": "Balance After Leave",
          "placeholder": "Calculated balance after this leave",
          "disabled": true
        },
        {
          "type": "checkbox",
          "key": "handoverCompleted",
          "label": "Handover documentation completed",
          "validate": { "required": true }
        },
        {
          "type": "checkbox",
          "key": "projectTasksAssigned",
          "label": "All project tasks have been assigned/delegated",
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "handoverNotes",
          "label": "Handover Notes",
          "placeholder": "Provide details of handover arrangements",
          "validate": { "minLength": 20 }
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
        },
        {
          "type": "textfield",
          "key": "position",
          "label": "Position/Title",
          "placeholder": "Enter your position",
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
    'Leave Request Form — full leave submission form with type-conditional fields',
    'PROCESS_START', true, 'system', 'system'
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 8. onboarding-checklist-form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'onboarding-checklist-form', 1, 'Onboarding Checklist Form',
    '{
      "id": "onboarding-checklist-form",
      "name": "Onboarding Checklist Form",
      "versionTag": "1",
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Employee Onboarding Checklist</h3><p>Complete the details below to initiate the onboarding workflow for the new hire.</p>"
        },
        {
          "type": "textfield",
          "key": "employeeName",
          "label": "Employee Name",
          "placeholder": "Enter the new hire''s full name",
          "validate": { "required": true }
        },
        {
          "type": "date",
          "key": "startDate",
          "label": "Start Date",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "department",
          "label": "Department",
          "placeholder": "Enter the department the new hire is joining",
          "validate": { "required": true }
        },
        {
          "type": "textfield",
          "key": "role",
          "label": "Job Role / Title",
          "placeholder": "Enter the new hire''s job title",
          "validate": { "required": true }
        },
        {
          "type": "checklist",
          "key": "equipmentRequired",
          "label": "Equipment Required",
          "values": [
            { "label": "Laptop", "value": "laptop" },
            { "label": "Phone", "value": "phone" },
            { "label": "Access Card", "value": "access_card" },
            { "label": "Email Setup", "value": "email_setup" },
            { "label": "Slack", "value": "slack" }
          ],
          "validate": { "required": false }
        },
        {
          "type": "textfield",
          "key": "buddyAssigned",
          "label": "Buddy Assigned",
          "placeholder": "Enter the name of the assigned buddy",
          "validate": { "required": false }
        },
        {
          "type": "textarea",
          "key": "notes",
          "label": "Additional Notes",
          "placeholder": "Any special requirements or notes for the onboarding team",
          "validate": { "required": false }
        }
      ]
    }'::jsonb,
    'Onboarding Checklist Form — new hire onboarding workflow start form',
    'PROCESS_START', true, 'system', 'system'
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 9. procurement-request-form
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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 10. vendor-selection
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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 11. quotation-review
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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- 12. procurement-approval
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
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';

-- ============================================================
-- 3f. Upsert all 8 canonical processes in process_draft
-- process_draft schema: id, process_key, name, bpmn_xml,
--   created_by, updated_by, created_at, updated_at
-- ON CONFLICT on process_key (UNIQUE constraint in V1).
-- bpmn_xml holds the full XML content for portal display and
-- re-deployment via the designer.
-- ============================================================

-- 1. asset-request-process
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'asset-request-process',
    'Asset Request',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/asset-request">

  <signal id="procurementApprovedSignal" name="procurementApproved" flowable:scope="global"/>

  <process id="asset-request-process" name="Asset Request" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Asset request workflow with custodian review, stock check, and procurement escalation</documentation>
    <startEvent id="start" name="Asset Request Submitted"
              flowable:formKey="asset-request-form"
              flowable:initiator="initiatorId"/>
    <userTask id="custodianReview" name="Custodian Review"
              flowable:candidateGroups="${custodianGroupName},SUPER_ADMIN,ADMIN"/>
    <exclusiveGateway id="decisionGateway" name="Decision?"/>
    <endEvent id="endFulfilled" name="Request Fulfilled"/>
    <endEvent id="endRejected" name="Request Rejected"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="custodianReview"/>
    <sequenceFlow id="flow3" sourceRef="custodianReview" targetRef="decisionGateway"/>
    <sequenceFlow id="approve" sourceRef="decisionGateway" targetRef="endFulfilled">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''approve''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="reject" sourceRef="decisionGateway" targetRef="endRejected">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''reject''}</conditionExpression>
    </sequenceFlow>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 2. capex-approval-process
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'capex-approval-process',
    'CapEx Approval Process',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/capex">
  <process id="capex-approval-process" name="Capital Expenditure Approval" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Capital Expenditure approval workflow with multi-level DOA routing.</documentation>
    <startEvent id="startEvent" name="CapEx Request Submitted" flowable:formKey="capex-request-form"/>
    <userTask id="managerApproval" name="Manager Review"
              flowable:candidateGroups="DEPT:FIN::DOA:L2,SUPER_ADMIN"
              flowable:formKey="capex-request-form"/>
    <endEvent id="endEventApproved" name="Request Approved"/>
    <endEvent id="endEventRejected" name="Request Rejected"/>
    <sequenceFlow id="flow0" sourceRef="startEvent" targetRef="managerApproval"/>
    <sequenceFlow id="flow16" sourceRef="managerApproval" targetRef="endEventApproved"/>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 3. event-ticket-request
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'event-ticket-request',
    'Event Ticket Request',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/examples">
  <process id="event-ticket-request" name="Event Ticket Request" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Event ticket request workflow. Employee submits; event coordinator reviews and approves or rejects.</documentation>
    <startEvent id="startEvent" name="Ticket Request Submitted"
                flowable:formKey="event-ticket-form"
                flowable:initiator="initiator"/>
    <userTask id="reviewRequest" name="Review Request"
              flowable:candidateGroups="EVENT_COORDINATOR,SUPER_ADMIN"
              flowable:formKey="event-ticket-form"/>
    <exclusiveGateway id="approvalGateway" name="Approved?"/>
    <endEvent id="endApproved" name="Ticket Approved"/>
    <endEvent id="endRejected" name="Ticket Rejected"/>
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="reviewRequest"/>
    <sequenceFlow id="flow2" sourceRef="reviewRequest" targetRef="approvalGateway"/>
    <sequenceFlow id="flow3" sourceRef="approvalGateway" targetRef="endApproved">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''approve''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow4" sourceRef="approvalGateway" targetRef="endRejected">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''reject''}</conditionExpression>
    </sequenceFlow>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 4. finance-approval-process (renamed to Budget Request Process)
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'finance-approval-process',
    'Budget Request Process',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/finance">
  <process id="finance-approval-process" name="Budget Request Process" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Final finance sign-off after procurement creates a purchase order. Finance team reviews and approves or rejects expenditure.</documentation>
    <startEvent id="startEvent" name="Finance Review Requested"
                flowable:formKey="budget-request-form"
                flowable:initiator="initiator"/>
    <userTask id="financeReview" name="Finance Review"
              flowable:candidateGroups="FINANCE_APPROVER,SUPER_ADMIN"
              flowable:formKey="budget-request-form"/>
    <exclusiveGateway id="approvalGateway" name="Approved?"/>
    <endEvent id="endApproved" name="Finance Approved"/>
    <endEvent id="endRejected" name="Finance Rejected"/>
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="financeReview"/>
    <sequenceFlow id="flow2" sourceRef="financeReview" targetRef="approvalGateway"/>
    <sequenceFlow id="flow3" sourceRef="approvalGateway" targetRef="endApproved">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''approve''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow4" sourceRef="approvalGateway" targetRef="endRejected">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''reject''}</conditionExpression>
    </sequenceFlow>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 5. general-approval
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'general-approval',
    'General Approval',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/examples">
  <process id="general-approval" name="General Approval" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>General-purpose approval workflow with DOA-based routing. Manager always reviews; Director also reviews when amount exceeds $50K.</documentation>
    <startEvent id="startEvent" name="Request Submitted" flowable:initiator="initiator"/>
    <userTask id="submitRequest" name="Submit Request"
              flowable:assignee="${initiator}"
              flowable:formKey="general-approval-form"/>
    <userTask id="managerApproval" name="Manager Approval"
              flowable:candidateGroups="DOA_L2,SUPER_ADMIN"
              flowable:formKey="general-approval-decision"/>
    <exclusiveGateway id="managerDecisionGateway" name="Manager Decision?"/>
    <exclusiveGateway id="amountGateway" name="Requires Director Approval?"/>
    <userTask id="directorApproval" name="Director Approval"
              flowable:candidateGroups="DOA_L3,DOA_L4,SUPER_ADMIN"
              flowable:formKey="general-approval-decision"/>
    <exclusiveGateway id="mergeGateway" name="Merge"/>
    <endEvent id="endApproved" name="Request Approved"/>
    <endEvent id="endRejected" name="Request Rejected"/>
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="submitRequest"/>
    <sequenceFlow id="flow2" sourceRef="submitRequest" targetRef="managerApproval"/>
    <sequenceFlow id="flow3" sourceRef="managerApproval" targetRef="managerDecisionGateway"/>
    <sequenceFlow id="flow4" sourceRef="managerDecisionGateway" targetRef="amountGateway">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''approve''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow5" sourceRef="managerDecisionGateway" targetRef="endRejected">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''reject''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow6" sourceRef="amountGateway" targetRef="directorApproval">
      <conditionExpression xsi:type="tFormalExpression">${amount > 50000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow7" sourceRef="amountGateway" targetRef="mergeGateway">
      <conditionExpression xsi:type="tFormalExpression">${amount &lt;= 50000}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow8" sourceRef="directorApproval" targetRef="mergeGateway"/>
    <sequenceFlow id="flow9" sourceRef="mergeGateway" targetRef="endApproved">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''approve''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow10" sourceRef="mergeGateway" targetRef="endRejected">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''reject''}</conditionExpression>
    </sequenceFlow>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 6. leave-request
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'leave-request',
    'Leave Request',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/examples">
  <process id="leave-request" name="Leave Request" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Employee leave request workflow. Employee submits; manager reviews and approves or rejects.</documentation>
    <startEvent id="startEvent" name="Leave Request Submitted"
                flowable:formKey="leave-request-form"
                flowable:initiator="initiator"/>
    <userTask id="managerReview" name="Manager Review"
              flowable:candidateGroups="MANAGER,SUPER_ADMIN"
              flowable:formKey="leave-request-form"/>
    <exclusiveGateway id="approvalGateway" name="Approved?"/>
    <endEvent id="endApproved" name="Leave Approved"/>
    <endEvent id="endRejected" name="Leave Rejected"/>
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="managerReview"/>
    <sequenceFlow id="flow2" sourceRef="managerReview" targetRef="approvalGateway"/>
    <sequenceFlow id="flow3" sourceRef="approvalGateway" targetRef="endApproved">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''approve''}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow4" sourceRef="approvalGateway" targetRef="endRejected">
      <conditionExpression xsi:type="tFormalExpression">${decision == ''reject''}</conditionExpression>
    </sequenceFlow>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 7. onboarding-checklist
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'onboarding-checklist',
    'Onboarding Checklist',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/examples">
  <process id="onboarding-checklist" name="Onboarding Checklist" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Employee onboarding workflow. HR submits new hire details; IT and Facilities tasks run in parallel; manager assigns buddy on completion.</documentation>
    <startEvent id="startEvent" name="Onboarding Started" flowable:initiator="initiator"/>
    <userTask id="hrSubmit" name="HR: Submit Onboarding"
              flowable:candidateGroups="SUPER_ADMIN"
              flowable:formKey="onboarding-checklist-form"/>
    <parallelGateway id="forkGateway" name="Start Parallel Setup"/>
    <userTask id="itSetup" name="IT: System Setup" flowable:candidateGroups="SUPER_ADMIN"/>
    <userTask id="facilitiesSetup" name="Facilities: Workspace Setup" flowable:candidateGroups="SUPER_ADMIN"/>
    <parallelGateway id="joinGateway" name="Setup Complete"/>
    <userTask id="assignBuddy" name="Assign Manager Buddy" flowable:candidateGroups="SUPER_ADMIN"/>
    <endEvent id="endEvent" name="Onboarding Complete"/>
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="hrSubmit"/>
    <sequenceFlow id="flow2" sourceRef="hrSubmit" targetRef="forkGateway"/>
    <sequenceFlow id="flow3" sourceRef="forkGateway" targetRef="itSetup"/>
    <sequenceFlow id="flow4" sourceRef="forkGateway" targetRef="facilitiesSetup"/>
    <sequenceFlow id="flow5" sourceRef="itSetup" targetRef="joinGateway"/>
    <sequenceFlow id="flow6" sourceRef="facilitiesSetup" targetRef="joinGateway"/>
    <sequenceFlow id="flow7" sourceRef="joinGateway" targetRef="assignBuddy"/>
    <sequenceFlow id="flow9" sourceRef="assignBuddy" targetRef="endEvent"/>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

-- 8. procurement-approval-process
INSERT INTO process_draft (process_key, name, bpmn_xml, created_by, updated_by)
VALUES (
    'procurement-approval-process',
    'Procurement Approval Process',
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://werkflow.com/bpmn/procurement">
  <signal id="procurementApprovedSignal" name="procurementApproved" flowable:scope="global"/>
  <process id="procurement-approval-process" name="Procurement Approval Process" isExecutable="true"
           flowable:versionTag="1">
    <extensionElements>
      <flowable:properties>
        <flowable:property name="category" value="examples"/>
      </flowable:properties>
    </extensionElements>
    <documentation>Purchase request approval with vendor selection, quotation review, and DMN-backed approval routing via procurement-matrix.dmn.</documentation>
    <startEvent id="startEvent" name="Purchase Request Submitted" flowable:formKey="procurement-request-form"/>
    <userTask id="selectVendor" name="Select Vendor"
              flowable:candidateGroups="DOA_L1,SUPER_ADMIN"
              flowable:formKey="vendor-selection"/>
    <userTask id="reviewQuotations" name="Review Quotations"
              flowable:candidateGroups="DOA_L1,SUPER_ADMIN"
              flowable:formKey="quotation-review"/>
    <userTask id="managerApproval" name="Manager Approval"
              flowable:candidateGroups="DOA_L2,SUPER_ADMIN"
              flowable:formKey="procurement-approval"/>
    <endEvent id="endEventApproved" name="PO Created"/>
    <endEvent id="endEventRejected" name="Request Rejected"/>
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="selectVendor"/>
    <sequenceFlow id="flow3" sourceRef="selectVendor" targetRef="reviewQuotations"/>
    <sequenceFlow id="flow5" sourceRef="reviewQuotations" targetRef="managerApproval"/>
    <sequenceFlow id="flow12" sourceRef="managerApproval" targetRef="endEventApproved"/>
  </process>
</definitions>',
    'system',
    'system'
)
ON CONFLICT (process_key) DO UPDATE
    SET name       = EXCLUDED.name,
        bpmn_xml   = EXCLUDED.bpmn_xml,
        updated_by = 'system',
        updated_at = now();

COMMIT;

-- ============================================================
-- POST-APPLY VERIFICATION QUERIES
-- Run these after applying the migration to confirm consistency.
-- ============================================================
--
-- Check all 8 canonical processes in process_draft:
--   SELECT process_key, name FROM process_draft
--   WHERE process_key IN (
--     'asset-request-process','capex-approval-process','event-ticket-request',
--     'finance-approval-process','general-approval','leave-request',
--     'onboarding-checklist','procurement-approval-process'
--   ) ORDER BY process_key;
--
-- Check all 12 canonical forms:
--   SELECT form_key, name, form_type, is_active FROM form_schemas
--   WHERE form_key IN (
--     'asset-request-form','capex-request-form','event-ticket-form',
--     'budget-request-form','general-approval-form','general-approval-decision',
--     'leave-request-form','onboarding-checklist-form','procurement-request-form',
--     'vendor-selection','quotation-review','procurement-approval'
--   ) ORDER BY form_key;
--
-- Check stale forms are gone:
--   SELECT form_key FROM form_schemas
--   WHERE form_key IN (
--     'asset-transfer','capex-approval','sla-escalation-form',
--     'sla-escalation-decision','committee-review-start','committee-review-decision',
--     'doa-request-form','doa-approval-form'
--   );  -- Should return 0 rows.
