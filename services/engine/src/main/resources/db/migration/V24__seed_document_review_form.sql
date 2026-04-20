-- Seed form schemas for the Document Review sample workflow.
-- Includes: document-review-form (process start) and document-review-decision (review task).

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'document-review-form',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "title",
                "key": "title",
                "label": "Document Title",
                "validate": {
                    "required": true,
                    "maxLength": 200
                }
            },
            {
                "type": "select",
                "id": "documentType",
                "key": "documentType",
                "label": "Document Type",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Policy", "value": "POLICY"},
                    {"label": "Contract", "value": "CONTRACT"},
                    {"label": "Report", "value": "REPORT"},
                    {"label": "Specification", "value": "SPECIFICATION"},
                    {"label": "Other", "value": "OTHER"}
                ]
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
                "type": "textfield",
                "id": "attachment",
                "key": "attachment",
                "label": "Attachment",
                "validate": {
                    "required": false
                },
                "description": "File name or URL of the document"
            },
            {
                "type": "select",
                "id": "reviewerGroup",
                "key": "reviewerGroup",
                "label": "Reviewer Group",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Legal", "value": "LEGAL"},
                    {"label": "Finance", "value": "FINANCE"},
                    {"label": "Engineering", "value": "ENGINEERING"},
                    {"label": "HR", "value": "HR"},
                    {"label": "Operations", "value": "OPERATIONS"}
                ]
            }
        ]
    }'::jsonb,
    'Document Review Form — process start form for the document review sample workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'document-review-decision',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "text",
                "id": "reviewHeader",
                "text": "### Review Document"
            },
            {
                "type": "textfield",
                "id": "title",
                "key": "title",
                "label": "Document Title",
                "disabled": true
            },
            {
                "type": "textfield",
                "id": "documentType",
                "key": "documentType",
                "label": "Document Type",
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
                "id": "decisionField",
                "key": "decision",
                "label": "Review Decision",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Approve", "value": "approve"},
                    {"label": "Reject", "value": "reject"},
                    {"label": "Request Revision", "value": "revise"}
                ]
            },
            {
                "type": "textarea",
                "id": "reviewFeedback",
                "key": "reviewFeedback",
                "label": "Feedback",
                "validate": {
                    "required": false
                },
                "description": "Required if requesting revision"
            }
        ]
    }'::jsonb,
    'Document Review Decision Form — used by the reviewer to approve, reject, or request revision',
    'APPROVAL',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
