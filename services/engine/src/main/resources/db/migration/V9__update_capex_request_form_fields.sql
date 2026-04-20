-- ================================================================================================
-- Update CapEx Request Form Fields
-- ================================================================================================
-- Description: Updates capex-request form schema:
--   1. Change 'justification' from textfield to textarea with minLength 10
--   2. Change 'description' minLength from 50 to 10
-- ================================================================================================

UPDATE form_schemas
SET schema_json = '{
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
                "minLength": 10
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
            "type": "textarea",
            "id": "justification",
            "key": "justification",
            "label": "Business Justification",
            "validate": {
                "required": true,
                "minLength": 10
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
    updated_by = 'system'
WHERE form_key = 'capex-request' AND version = 1;
