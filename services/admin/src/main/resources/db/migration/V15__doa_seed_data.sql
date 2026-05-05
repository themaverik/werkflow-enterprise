-- ============================================================
-- WERKFLOW ADMIN — DOA SEED DATA
-- ============================================================
-- Populates default DOA threshold amounts and role-to-DOA-level
-- mappings per ADR-002 (FEEL context injection) and ADR-003
-- (Keycloak roles mapped to DOA authority levels).
--
-- All rows use ON CONFLICT DO NOTHING so re-running migrations
-- on an already-seeded DB is idempotent. Tenant admins can
-- update these values via the Approval Authority UI.
-- ============================================================

-- DOA Threshold Amounts (ADR-002: per-tenant DOA config via FEEL context)
INSERT INTO configuration_variables (tenant_code, var_key, var_value, var_type, description)
VALUES
  ('default', 'L1', '10000',      'DOA_THRESHOLD', 'Level 1 max approval amount (USD)'),
  ('default', 'L2', '50000',      'DOA_THRESHOLD', 'Level 2 max approval amount (USD)'),
  ('default', 'L3', '200000',     'DOA_THRESHOLD', 'Level 3 max approval amount (USD)'),
  ('default', 'L4', 'unlimited',  'DOA_THRESHOLD', 'Level 4 — no upper limit')
ON CONFLICT (tenant_code, var_key) DO NOTHING;

-- Role to DOA Level Mappings (ADR-003: Keycloak roles mapped to DOA levels)
INSERT INTO configuration_variables (tenant_code, var_key, var_value, var_type, description)
VALUES
  ('default', 'doa_approver_level1', 'L1', 'ROLE_DOA_LEVEL', 'L1 approver role'),
  ('default', 'doa_approver_level2', 'L2', 'ROLE_DOA_LEVEL', 'L2 approver role'),
  ('default', 'doa_approver_level3', 'L3', 'ROLE_DOA_LEVEL', 'L3 approver role'),
  ('default', 'doa_approver_level4', 'L4', 'ROLE_DOA_LEVEL', 'L4 approver role'),
  ('default', 'super_admin',         'L4', 'ROLE_DOA_LEVEL', 'Super admin has L4 authority'),
  ('default', 'admin',               'L3', 'ROLE_DOA_LEVEL', 'Admin has L3 authority')
ON CONFLICT (tenant_code, var_key) DO NOTHING;
