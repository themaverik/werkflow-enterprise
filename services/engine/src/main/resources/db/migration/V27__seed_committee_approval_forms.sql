-- Seed notification template and form schemas for the Parallel Committee Approval sample workflow.

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

-- Seed form schemas for the Parallel Committee Approval sample workflow.
-- Includes: committee-review-start (process start) and committee-review-decision (member vote task).

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'committee-review-start',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "proposalTitle",
                "key": "proposalTitle",
                "label": "Proposal Title",
                "validate": {
                    "required": true,
                    "maxLength": 150
                }
            },
            {
                "type": "textarea",
                "id": "proposalDescription",
                "key": "proposalDescription",
                "label": "Proposal Description",
                "validate": {
                    "required": true,
                    "minLength": 20
                }
            },
            {
                "type": "select",
                "id": "proposalCategory",
                "key": "proposalCategory",
                "label": "Category",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Policy", "value": "POLICY"},
                    {"label": "Budget", "value": "BUDGET"},
                    {"label": "Strategic Initiative", "value": "STRATEGIC"},
                    {"label": "Operational Change", "value": "OPERATIONAL"},
                    {"label": "Other", "value": "OTHER"}
                ]
            },
            {
                "type": "textfield",
                "id": "attachments",
                "key": "attachments",
                "label": "Supporting Documents (optional)",
                "description": "Comma-separated document references or URLs",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'Committee Review Start Form — process start form for the parallel committee approval workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'committee-review-decision',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "text",
                "id": "reviewHeader",
                "text": "### Committee Member Vote"
            },
            {
                "type": "textfield",
                "id": "proposalTitle",
                "key": "proposalTitle",
                "label": "Proposal Title",
                "disabled": true
            },
            {
                "type": "textarea",
                "id": "proposalDescription",
                "key": "proposalDescription",
                "label": "Proposal Description",
                "disabled": true
            },
            {
                "type": "textfield",
                "id": "proposalCategory",
                "key": "proposalCategory",
                "label": "Category",
                "disabled": true
            },
            {
                "type": "select",
                "id": "voteField",
                "key": "vote",
                "label": "Vote",
                "validate": {
                    "required": true
                },
                "values": [
                    {"label": "Approve", "value": "approve"},
                    {"label": "Reject", "value": "reject"},
                    {"label": "Abstain", "value": "abstain"}
                ]
            },
            {
                "type": "textarea",
                "id": "voteComments",
                "key": "voteComments",
                "label": "Comments",
                "description": "Rationale or conditions attached to your vote",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'Committee Review Decision Form — used by all three committee member review tasks',
    'APPROVAL',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
