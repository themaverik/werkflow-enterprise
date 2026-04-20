-- Seed form schemas for the DoA Routing via DMN sample workflow.
-- Includes: doa-request-form (process start) and doa-approval-form (approval task).
-- DMN files (doa_routing, leave_approval, procurement_matrix) deploy via classpath
-- auto-deployment configured in flowable.dmn.deployment-resources.

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'doa-request-form',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "requestTitle",
                "key": "requestTitle",
                "label": "Request Title",
                "validate": {
                    "required": true,
                    "maxLength": 150
                }
            },
            {
                "type": "number",
                "id": "requestAmount",
                "key": "requestAmount",
                "label": "Request Amount",
                "validate": {
                    "required": true,
                    "min": 0
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
                    {"label": "Finance", "value": "FINANCE"},
                    {"label": "IT", "value": "IT"},
                    {"label": "Operations", "value": "OPERATIONS"},
                    {"label": "HR", "value": "HR"},
                    {"label": "Legal", "value": "LEGAL"},
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
                    "minLength": 20
                }
            }
        ]
    }'::jsonb,
    'DoA Request Form — process start form for the DoA routing sample workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'doa-approval-form',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "text",
                "id": "approvalHeader",
                "text": "### Spend Request Review"
            },
            {
                "type": "textfield",
                "id": "requestTitle",
                "key": "requestTitle",
                "label": "Request Title",
                "disabled": true
            },
            {
                "type": "number",
                "id": "requestAmount",
                "key": "requestAmount",
                "label": "Request Amount",
                "disabled": true
            },
            {
                "type": "textfield",
                "id": "department",
                "key": "department",
                "label": "Department",
                "disabled": true
            },
            {
                "type": "textarea",
                "id": "justification",
                "key": "justification",
                "label": "Business Justification",
                "disabled": true
            },
            {
                "type": "select",
                "id": "approvalDecision",
                "key": "approvalDecision",
                "label": "Decision",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Approve", "value": "APPROVED"},
                    {"label": "Reject", "value": "REJECTED"},
                    {"label": "Request More Information", "value": "MORE_INFO"}
                ]
            },
            {
                "type": "textarea",
                "id": "approvalComments",
                "key": "approvalComments",
                "label": "Comments",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'DoA Approval Form — used by all four approval tasks in the DoA routing workflow',
    'APPROVAL',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
