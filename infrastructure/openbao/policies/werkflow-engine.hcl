# werkflow-engine policy — read-only on tenant credential secrets.
# Used by services/engine to resolve tenant credentials at delegate execution time.
# Engine never writes; rotation and provisioning are admin-service concerns.

path "secret/data/tenants/+/+/+" {
  capabilities = ["read"]
}
