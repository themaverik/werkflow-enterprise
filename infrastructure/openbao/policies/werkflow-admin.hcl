# werkflow-admin policy — read + write + delete on tenant credential secrets.
# Used by services/admin to provision and rotate credentials via the portal CRUD API.

path "secret/data/tenants/+/+/+" {
  capabilities = ["create", "read", "update", "delete"]
}

path "secret/metadata/tenants/+/+/+" {
  capabilities = ["read", "delete", "list"]
}

# Allow listing one level of metadata to support discovery / debug tooling.
path "secret/metadata/tenants/+/+" {
  capabilities = ["list"]
}

path "secret/metadata/tenants/+" {
  capabilities = ["list"]
}
