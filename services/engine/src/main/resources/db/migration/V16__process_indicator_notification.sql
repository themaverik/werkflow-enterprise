-- Extend process_indicators with a notification indicator.
-- DEFAULT FALSE means existing rows are treated as hasNotification=false (no rescan needed).
-- Next reseed via WERKFLOW_RESET_EXAMPLES=true will repopulate all rows with the correct value.

ALTER TABLE process_indicators
    ADD COLUMN IF NOT EXISTS has_notification BOOLEAN NOT NULL DEFAULT FALSE;
