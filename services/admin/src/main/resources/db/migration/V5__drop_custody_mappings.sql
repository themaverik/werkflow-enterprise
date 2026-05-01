-- ADR-004: custody mappings moved to werkflow-erp (P1.6.2).
-- Admin-service is no longer the source of truth for this data.
DROP TABLE IF EXISTS tenant_custody_mappings;
