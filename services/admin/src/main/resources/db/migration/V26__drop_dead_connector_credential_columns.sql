-- V26: Drop dead columns on tenant_api_credentials (M4.12 B.6 punch list, Roadmap item 11).
-- header_name: superseded by the http-header-auth credential's own headerName field in OpenBao.
-- secret_ref:  legacy pointer, fully superseded by credential_ref in V25.
-- Both are no longer read by any code path after the B.6 migration.

BEGIN;

ALTER TABLE admin_service.tenant_api_credentials DROP COLUMN IF EXISTS header_name;
ALTER TABLE admin_service.tenant_api_credentials DROP COLUMN IF EXISTS secret_ref;

COMMIT;
