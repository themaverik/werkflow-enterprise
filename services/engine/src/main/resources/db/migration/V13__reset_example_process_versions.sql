-- V13: Cleanup orphan example drafts + bundle index.
--
-- V8 seeded a process_draft row for every shipped example. Independently,
-- ProcessExampleDeployer publishes the classpath BPMNs to Flowable on startup. Net result:
-- every example shows up in BOTH the Deployed and Drafts tabs of /processes even though
-- no user ever edited them — and the seeded drafts are stale V1 snapshots that would
-- silently downgrade the deployed V2 if a user clicked "Validate and push live".
--
-- This migration removes those orphan drafts and the bundle-versioning index for the
-- 8 examples. Flowable-side version reset (ACT_RE_DEPLOYMENT / ACT_RE_PROCDEF) is handled
-- separately by ProcessExampleDeployer's reset-on-startup mode (Java + RepositoryService
-- cascade), because Flowable's FK chain does not cascade on direct SQL DELETE.

DELETE FROM process_draft WHERE process_key IN (
    'asset-request-process',
    'capex-approval-process',
    'event-ticket-request',
    'finance-approval-process',
    'general-approval',
    'leave-request',
    'onboarding-checklist',
    'procurement-approval-process'
);

DELETE FROM process_bundle WHERE process_key IN (
    'asset-request-process',
    'capex-approval-process',
    'event-ticket-request',
    'finance-approval-process',
    'general-approval',
    'leave-request',
    'onboarding-checklist',
    'procurement-approval-process'
);
