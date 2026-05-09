-- ADR-003 extension (ADR-010): flag Tier 2 groups that trigger cross-department manager visibility.
ALTER TABLE role_group_mappings
    ADD COLUMN IF NOT EXISTS is_manager_tier BOOLEAN NOT NULL DEFAULT FALSE;
