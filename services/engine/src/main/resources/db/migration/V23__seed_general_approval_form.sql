-- Seed form schemas for the General Approval sample workflow.
-- Includes: general-approval-form (process start) and general-approval-decision (approval task).

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'general-approval-form',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "title",
                "key": "title",
                "label": "Request Title",
                "validate": {
                    "required": true,
                    "maxLength": 100
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
                "id": "amount",
                "key": "amount",
                "label": "Amount ($)",
                "validate": {
                    "required": true,
                    "min": 0
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
                    {"label": "Budget", "value": "BUDGET"},
                    {"label": "Operational", "value": "OPERATIONAL"},
                    {"label": "Capital", "value": "CAPITAL"},
                    {"label": "Other", "value": "OTHER"}
                ]
            },
            {
                "type": "textfield",
                "id": "attachments",
                "key": "attachments",
                "label": "Attachments (optional)",
                "validate": {
                    "required": false
                },
                "description": "Comma-separated file references or URLs"
            }
        ]
    }'::jsonb,
    'General Approval Form — process start form for the general approval sample workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'general-approval-decision',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "text",
                "id": "reviewHeader",
                "text": "### Review Request"
            },
            {
                "type": "textfield",
                "id": "title",
                "key": "title",
                "label": "Request Title",
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
                "type": "number",
                "id": "amount",
                "key": "amount",
                "label": "Amount ($)",
                "disabled": true
            },
            {
                "type": "textfield",
                "id": "category",
                "key": "category",
                "label": "Category",
                "disabled": true
            },
            {
                "type": "select",
                "id": "decisionField",
                "key": "decision",
                "label": "Decision",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Approve", "value": "approve"},
                    {"label": "Reject", "value": "reject"}
                ]
            },
            {
                "type": "textarea",
                "id": "reviewComments",
                "key": "reviewComments",
                "label": "Comments",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'General Approval Decision Form — used by Manager and Director approval tasks',
    'APPROVAL',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
