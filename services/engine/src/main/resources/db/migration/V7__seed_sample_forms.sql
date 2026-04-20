-- ================================================================================================
-- Sample Form Schemas Seed Data
-- ================================================================================================
-- Description: Seeds sample form-js schemas for common Werkflow workflows
-- Author: Werkflow Team
-- Date: 2025-12-30
-- Version: 7
-- ================================================================================================
-- Purpose: Provides ready-to-use form schemas for testing and demonstration
-- Forms included:
--   1. CapEx Request Form (capex-request)
--   2. Employee Onboarding Form (employee-onboarding)
--   3. Asset Transfer Form (asset-transfer)
--   4. Contact Request Form (contact-request)
--   5. Purchase Request Form (purchase-request)
-- ================================================================================================

-- ================================================================================================
-- 1. CapEx Request Form
-- ================================================================================================
-- Used in: capex-approval-process.bpmn20.xml
-- FormKey: capex-request
-- Purpose: Capital expenditure request submission form
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'capex-request',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "requesterName",
                "key": "requesterName",
                "label": "Requester Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "requesterEmail",
                "key": "requesterEmail",
                "label": "Email",
                "validate": {
                    "required": true,
                    "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
                }
            },
            {
                "type": "textfield",
                "id": "departmentId",
                "key": "departmentId",
                "label": "Department ID",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "projectName",
                "key": "projectName",
                "label": "Project Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textarea",
                "id": "description",
                "key": "description",
                "label": "Description",
                "validate": {
                    "required": true,
                    "minLength": 50
                }
            },
            {
                "type": "number",
                "id": "requestAmount",
                "key": "requestAmount",
                "label": "Requested Amount ($)",
                "validate": {
                    "required": true,
                    "min": 1
                }
            },
            {
                "type": "select",
                "id": "category",
                "key": "category",
                "label": "Category",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Equipment", "value": "EQUIPMENT"},
                    {"label": "Software", "value": "SOFTWARE"},
                    {"label": "Infrastructure", "value": "INFRASTRUCTURE"},
                    {"label": "Facility", "value": "FACILITY"},
                    {"label": "Other", "value": "OTHER"}
                ]
            },
            {
                "type": "textfield",
                "id": "justification",
                "key": "justification",
                "label": "Business Justification",
                "validate": {
                    "required": true,
                    "minLength": 100
                }
            },
            {
                "type": "textfield",
                "id": "expectedROI",
                "key": "expectedROI",
                "label": "Expected ROI (%)",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'Capital Expenditure Request Form - Initial submission for CapEx workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ================================================================================================
-- 2. Employee Onboarding Form
-- ================================================================================================
-- FormKey: employee-onboarding
-- Purpose: New employee onboarding information collection
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'employee-onboarding',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "firstName",
                "key": "firstName",
                "label": "First Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "lastName",
                "key": "lastName",
                "label": "Last Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "email",
                "key": "email",
                "label": "Company Email",
                "validate": {
                    "required": true,
                    "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
                }
            },
            {
                "type": "textfield",
                "id": "phone",
                "key": "phone",
                "label": "Phone Number",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "select",
                "id": "department",
                "key": "department",
                "label": "Department",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Engineering", "value": "ENGINEERING"},
                    {"label": "Finance", "value": "FINANCE"},
                    {"label": "Human Resources", "value": "HR"},
                    {"label": "Sales", "value": "SALES"},
                    {"label": "Marketing", "value": "MARKETING"},
                    {"label": "Operations", "value": "OPERATIONS"}
                ]
            },
            {
                "type": "textfield",
                "id": "jobTitle",
                "key": "jobTitle",
                "label": "Job Title",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "startDate",
                "key": "startDate",
                "label": "Start Date",
                "validate": {
                    "required": true
                },
                "description": "Format: YYYY-MM-DD"
            },
            {
                "type": "textfield",
                "id": "managerName",
                "key": "managerName",
                "label": "Reporting Manager",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "select",
                "id": "employmentType",
                "key": "employmentType",
                "label": "Employment Type",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Full-time", "value": "FULL_TIME"},
                    {"label": "Part-time", "value": "PART_TIME"},
                    {"label": "Contract", "value": "CONTRACT"},
                    {"label": "Intern", "value": "INTERN"}
                ]
            },
            {
                "type": "textarea",
                "id": "specialRequirements",
                "key": "specialRequirements",
                "label": "Special Requirements/Notes",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'Employee Onboarding Form - Collects new hire information',
    'TASK_FORM',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ================================================================================================
-- 3. Asset Transfer Form
-- ================================================================================================
-- FormKey: asset-transfer
-- Purpose: Transfer assets between departments or locations
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'asset-transfer',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "assetId",
                "key": "assetId",
                "label": "Asset ID",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "assetName",
                "key": "assetName",
                "label": "Asset Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "select",
                "id": "assetType",
                "key": "assetType",
                "label": "Asset Type",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Computer Equipment", "value": "COMPUTER"},
                    {"label": "Office Furniture", "value": "FURNITURE"},
                    {"label": "Vehicle", "value": "VEHICLE"},
                    {"label": "Machinery", "value": "MACHINERY"},
                    {"label": "Other", "value": "OTHER"}
                ]
            },
            {
                "type": "textfield",
                "id": "fromDepartment",
                "key": "fromDepartment",
                "label": "From Department",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "toDepartment",
                "key": "toDepartment",
                "label": "To Department",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "fromLocation",
                "key": "fromLocation",
                "label": "From Location",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "toLocation",
                "key": "toLocation",
                "label": "To Location",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "transferDate",
                "key": "transferDate",
                "label": "Transfer Date",
                "validate": {
                    "required": true
                },
                "description": "Format: YYYY-MM-DD"
            },
            {
                "type": "textarea",
                "id": "reason",
                "key": "reason",
                "label": "Reason for Transfer",
                "validate": {
                    "required": true,
                    "minLength": 20
                }
            },
            {
                "type": "textfield",
                "id": "requestedBy",
                "key": "requestedBy",
                "label": "Requested By",
                "validate": {
                    "required": true
                }
            }
        ]
    }'::jsonb,
    'Asset Transfer Form - Request asset transfer between departments',
    'TASK_FORM',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ================================================================================================
-- 4. Contact Request Form
-- ================================================================================================
-- FormKey: contact-request
-- Purpose: Simple contact form for general inquiries
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'contact-request',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "name",
                "key": "name",
                "label": "Full Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "email",
                "key": "email",
                "label": "Email Address",
                "validate": {
                    "required": true,
                    "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
                }
            },
            {
                "type": "textfield",
                "id": "phone",
                "key": "phone",
                "label": "Phone Number",
                "validate": {
                    "required": false
                }
            },
            {
                "type": "select",
                "id": "category",
                "key": "category",
                "label": "Inquiry Category",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "General Question", "value": "GENERAL"},
                    {"label": "Technical Support", "value": "TECHNICAL"},
                    {"label": "Billing", "value": "BILLING"},
                    {"label": "Feature Request", "value": "FEATURE"},
                    {"label": "Other", "value": "OTHER"}
                ]
            },
            {
                "type": "textfield",
                "id": "subject",
                "key": "subject",
                "label": "Subject",
                "validate": {
                    "required": true,
                    "minLength": 5
                }
            },
            {
                "type": "textarea",
                "id": "message",
                "key": "message",
                "label": "Message",
                "validate": {
                    "required": true,
                    "minLength": 20
                }
            },
            {
                "type": "select",
                "id": "priority",
                "key": "priority",
                "label": "Priority",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Low", "value": "LOW"},
                    {"label": "Medium", "value": "MEDIUM"},
                    {"label": "High", "value": "HIGH"},
                    {"label": "Urgent", "value": "URGENT"}
                ]
            }
        ]
    }'::jsonb,
    'Contact Request Form - General inquiry submission form',
    'CUSTOM',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ================================================================================================
-- 5. Purchase Request Form
-- ================================================================================================
-- FormKey: purchase-request
-- Purpose: Request purchase of goods or services
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'purchase-request',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "requesterId",
                "key": "requesterId",
                "label": "Requester ID",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "requesterName",
                "key": "requesterName",
                "label": "Requester Name",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "departmentId",
                "key": "departmentId",
                "label": "Department",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "textfield",
                "id": "itemDescription",
                "key": "itemDescription",
                "label": "Item Description",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "select",
                "id": "itemCategory",
                "key": "itemCategory",
                "label": "Item Category",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Office Supplies", "value": "OFFICE_SUPPLIES"},
                    {"label": "IT Equipment", "value": "IT_EQUIPMENT"},
                    {"label": "Software License", "value": "SOFTWARE"},
                    {"label": "Furniture", "value": "FURNITURE"},
                    {"label": "Services", "value": "SERVICES"},
                    {"label": "Other", "value": "OTHER"}
                ]
            },
            {
                "type": "number",
                "id": "quantity",
                "key": "quantity",
                "label": "Quantity",
                "validate": {
                    "required": true,
                    "min": 1
                }
            },
            {
                "type": "number",
                "id": "unitPrice",
                "key": "unitPrice",
                "label": "Unit Price ($)",
                "validate": {
                    "required": true,
                    "min": 0.01
                }
            },
            {
                "type": "number",
                "id": "totalAmount",
                "key": "totalAmount",
                "label": "Total Amount ($)",
                "validate": {
                    "required": true,
                    "min": 0.01
                },
                "description": "Calculated: Quantity × Unit Price"
            },
            {
                "type": "textfield",
                "id": "vendorName",
                "key": "vendorName",
                "label": "Preferred Vendor",
                "validate": {
                    "required": false
                }
            },
            {
                "type": "textarea",
                "id": "justification",
                "key": "justification",
                "label": "Business Justification",
                "validate": {
                    "required": true,
                    "minLength": 30
                }
            },
            {
                "type": "textfield",
                "id": "budgetCode",
                "key": "budgetCode",
                "label": "Budget Code",
                "validate": {
                    "required": true
                }
            },
            {
                "type": "select",
                "id": "urgency",
                "key": "urgency",
                "label": "Urgency",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Normal", "value": "NORMAL"},
                    {"label": "Urgent", "value": "URGENT"},
                    {"label": "Emergency", "value": "EMERGENCY"}
                ]
            }
        ]
    }'::jsonb,
    'Purchase Request Form - Request purchase of goods or services',
    'TASK_FORM',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ================================================================================================
-- Additional CapEx Approval Form for User Tasks
-- ================================================================================================
-- FormKey: capex-approval
-- Purpose: Used by managers/VPs/CFO to approve or reject CapEx requests
-- ================================================================================================

INSERT INTO form_schemas (
    form_key,
    version,
    schema_json,
    description,
    form_type,
    is_active,
    created_by,
    updated_by
) VALUES (
    'capex-approval',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "text",
                "id": "requestSummary",
                "text": "### Review Capital Expenditure Request"
            },
            {
                "type": "textfield",
                "id": "projectName",
                "key": "projectName",
                "label": "Project Name",
                "disabled": true
            },
            {
                "type": "number",
                "id": "requestAmount",
                "key": "requestAmount",
                "label": "Requested Amount ($)",
                "disabled": true
            },
            {
                "type": "textarea",
                "id": "description",
                "key": "description",
                "label": "Description",
                "disabled": true
            },
            {
                "type": "select",
                "id": "decision",
                "key": "approved",
                "label": "Decision",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Approve", "value": "true"},
                    {"label": "Reject", "value": "false"}
                ]
            },
            {
                "type": "textarea",
                "id": "comments",
                "key": "approvalComments",
                "label": "Comments",
                "validate": {
                    "required": true,
                    "minLength": 10
                }
            }
        ]
    }'::jsonb,
    'CapEx Approval Form - Used by approvers to make approval decisions',
    'APPROVAL',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- ================================================================================================
-- Verification Query
-- ================================================================================================
-- Query to verify all forms were inserted successfully
-- Run this after migration to confirm:
-- SELECT form_key, version, form_type, is_active, description
-- FROM form_schemas
-- ORDER BY form_key;
-- ================================================================================================

-- Expected results: 6 rows (capex-request, capex-approval, employee-onboarding,
--                           asset-transfer, contact-request, purchase-request)
