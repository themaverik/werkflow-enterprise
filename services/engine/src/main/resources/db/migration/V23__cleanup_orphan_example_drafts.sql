-- ============================================================
-- WERKFLOW ENGINE — CLEANUP ORPHAN EXAMPLE PROCESS DRAFTS
-- ============================================================
-- Purpose: Remove process_draft rows for the 4 example processes
--   deleted from classpath in de7588e (ADR-031 Phase B curated set).
--   These rows were system-seeded by V8 and ProcessExampleDeployer
--   but their BPMNs no longer exist on classpath.
--
-- NOT included: procurement-approval-process, finance-approval-process
--   — their BPMNs are being restored to classpath in this same change,
--     so their process_draft rows become valid again on next boot.
--
-- ProcessExampleDeployer.pruneOrphanSystemDrafts() handles ongoing
-- cleanup on future BPMN removals; this migration only cleans up the
-- currently stale rows accumulated before that logic was added.
--
-- Safe to re-run: DELETE WHERE … is idempotent.
-- ============================================================

DELETE FROM process_draft
WHERE created_by = 'system'
  AND process_key IN (
    'asset-request-process',
    'general-approval',
    'event-ticket-request',
    'onboarding-checklist'
  );
