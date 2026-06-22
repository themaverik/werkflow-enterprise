-- Remove orphan committee-outcome notification template.
-- Seeded by V2__enterprise_baseline.sql for a committee/proposal flow that was
-- never implemented (zero references across Java/TS/BPMN code).
-- linked_form_key is empty, no BPMN sendTask points at it.

DELETE FROM notification_templates
WHERE template_key = 'committee-outcome';
