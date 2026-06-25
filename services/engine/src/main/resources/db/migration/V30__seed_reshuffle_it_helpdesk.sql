-- V30: Seed library reshuffle — remove finance-approval orphan, add IT helpdesk templates.
--
-- Context:
--   - finance-approval-process.bpmn20.xml and budget-request-form.json are deleted from
--     the classpath; ProcessExampleDeployer will no longer seed them on startup.
--   - Remove any residual budget-request-form row that the deployer may have (re-)seeded
--     after V20 on an upgraded instance (idempotent: deletes 0 rows on clean deploys).
--   - Seed the two notification_templates consumed by the new it-helpdesk-ticket sendTasks.
--     NotificationDelegate calls templateService.render() synchronously; missing rows throw.

-- Remove orphaned finance form if still present (deployer re-seeds on each restart prior to V30)
DELETE FROM form_schemas WHERE form_key = 'budget-request-form' AND tenant_id = 'default';

-- Seed notification templates for IT helpdesk sendTasks.
-- notification_templates has a tenant_id (V18, NOT NULL DEFAULT 'default') and the unique
-- key is composite (template_key, tenant_id) — so the conflict target must be composite.
INSERT INTO notification_templates (template_key, channel, subject, body, name, tenant_id) VALUES
('ticket-acknowledged', 'email', 'Your IT ticket has been received',
 'Hi,

We have received your IT support ticket and it is now in our queue.

Ticket subject: $${subject}

Our team will review and respond as soon as possible.

Best regards,
IT Support Team', 'Ticket Acknowledged', 'default')
ON CONFLICT (template_key, tenant_id) DO NOTHING;

INSERT INTO notification_templates (template_key, channel, subject, body, name, tenant_id) VALUES
('ticket-resolved', 'email', 'Your IT ticket has been resolved',
 'Hi,

Your IT support ticket has been resolved.

Ticket subject: $${subject}

If you have any further questions, please log a new ticket.

Best regards,
IT Support Team', 'Ticket Resolved', 'default')
ON CONFLICT (template_key, tenant_id) DO NOTHING;
