-- Seed notification templates and form schemas for the SLA Escalation sample workflow.

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

-- Seed form schemas for the SLA Escalation sample workflow.
-- Includes: sla-escalation-form (process start) and sla-escalation-decision (review/escalation task).

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'sla-escalation-form',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "textfield",
                "id": "title",
                "key": "title",
                "label": "Item Title",
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
                    {"label": "Critical", "value": "CRITICAL"}
                ]
            },
            {
                "type": "textfield",
                "id": "reviewerGroup",
                "key": "reviewerGroup",
                "label": "Reviewer Group (optional)",
                "description": "Candidate group ID to assign the review task to",
                "validate": {
                    "required": false
                }
            }
        ]
    }'::jsonb,
    'SLA Escalation Form — process start form for the SLA escalation sample workflow',
    'PROCESS_START',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'sla-escalation-decision',
    1,
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {
                "type": "text",
                "id": "reviewHeader",
                "text": "### Review Item"
            },
            {
                "type": "textfield",
                "id": "title",
                "key": "title",
                "label": "Item Title",
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
                "type": "textfield",
                "id": "priority",
                "key": "priority",
                "label": "Priority",
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
                    {"label": "Reject", "value": "reject"},
                    {"label": "Request More Info", "value": "more_info"}
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
    'SLA Escalation Decision Form — used by both Review Item and Escalated Review tasks',
    'APPROVAL',
    true,
    'system',
    'system'
) ON CONFLICT (form_key, version) DO NOTHING;
