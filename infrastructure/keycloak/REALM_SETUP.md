# Keycloak Realm Setup — Deprecated

This manual realm-creation guide is **deprecated**. The `werkflow` realm is now defined by
the canonical `realms/werkflow-realm.json` and imported automatically via `--import-realm`
on a fresh Keycloak volume.

See **[README.md](README.md)** for the current setup, client/role reference, and the
`import-realm.sh` flow. Do not create the realm or clients by hand — the import is the
single source of truth, and hand-created config will drift from it.
