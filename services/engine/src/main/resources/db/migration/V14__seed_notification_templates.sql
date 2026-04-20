INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('capex-approved', 'email', 'Your CapEx Request Has Been Approved',
 'Dear $${requesterName},

Your Capital Expenditure request "$${title}" for $${amount} has been approved.

The budget has been reserved and you may proceed.

Best regards,
Finance Team'),

('capex-rejected', 'email', 'Your CapEx Request Has Been Declined',
 'Dear $${requesterName},

Your Capital Expenditure request "$${title}" has been declined.

Reason: $${reason}

Please reach out to your line manager if you have questions.

Best regards,
Finance Team'),

('task-assigned', 'email', 'A New Task Requires Your Attention',
 'Dear $${assigneeName},

A new task has been assigned to you.

Task: $${taskName}
Process: $${processName}

Please log in to the system to review and act on this task.

Best regards,
Workflow System'),

('leave-approved', 'email', 'Your Leave Request Has Been Approved',
 'Dear $${requesterName},

Your leave request from $${startDate} to $${endDate} has been approved.

Enjoy your time off.

Best regards,
HR Team'),

('leave-rejected', 'email', 'Your Leave Request Has Been Declined',
 'Dear $${requesterName},

Your leave request from $${startDate} to $${endDate} has been declined.

Reason: $${reason}

Please contact HR if you need further clarification.

Best regards,
HR Team'),

('asset-request-update', 'email', 'Asset Request Update: $${status}',
 'Dear $${requesterName},

Your request for $${assetType} has been updated.

Status: $${status}

Log in to the system to view full details.

Best regards,
IT / Procurement Team');

-- 6 template rows seeded
