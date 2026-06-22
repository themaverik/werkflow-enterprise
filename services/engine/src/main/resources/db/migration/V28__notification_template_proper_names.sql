-- V28__notification_template_proper_names.sql
--
-- Back-fills human-readable Title Case names for the 8 notification templates
-- whose name was set equal to template_key by V3__notification_template_designer.sql.
--
-- committee-outcome was removed by V27 and is not included here.
--
-- Idempotency: UPDATE on existing rows is safe to re-run (sets the same value).
-- Tenant scope: targets tenant_id = 'default' — the only seeded tenant.

UPDATE notification_templates SET name = 'Task Assigned'        WHERE template_key = 'task-assigned'        AND tenant_id = 'default';
UPDATE notification_templates SET name = 'Task Reminder'        WHERE template_key = 'task-reminder'        AND tenant_id = 'default';
UPDATE notification_templates SET name = 'Task Escalation'      WHERE template_key = 'task-escalation'      AND tenant_id = 'default';
UPDATE notification_templates SET name = 'Leave Approved'       WHERE template_key = 'leave-approved'       AND tenant_id = 'default';
UPDATE notification_templates SET name = 'Leave Rejected'       WHERE template_key = 'leave-rejected'       AND tenant_id = 'default';
UPDATE notification_templates SET name = 'CapEx Approved'       WHERE template_key = 'capex-approved'       AND tenant_id = 'default';
UPDATE notification_templates SET name = 'CapEx Rejected'       WHERE template_key = 'capex-rejected'       AND tenant_id = 'default';
UPDATE notification_templates SET name = 'Asset Request Update' WHERE template_key = 'asset-request-update' AND tenant_id = 'default';
