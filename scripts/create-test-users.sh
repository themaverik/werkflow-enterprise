#!/bin/bash

# Keycloak Test Users Creation Script
# Creates test users with different roles for Werkflow platform

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
REALM="${REALM:-werkflow}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

echo "=========================================="
echo "Keycloak Test Users Creation"
echo "=========================================="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo ""

# Get admin token
echo "Getting admin token..."
TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r .access_token)

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get admin token. Check Keycloak credentials."
  exit 1
fi

echo "Admin token obtained successfully."
echo ""

# Helper function to create user
create_user() {
  local username=$1
  local email=$2
  local password=$3
  local roles=$4
  local doa_level=$5

  echo "Creating user: $username ($email)..."

  # Build roles array
  local roles_json=""
  IFS=',' read -ra ROLE_ARRAY <<< "$roles"
  for role in "${ROLE_ARRAY[@]}"; do
    if [ -z "$roles_json" ]; then
      roles_json="\"$role\""
    else
      roles_json="$roles_json, \"$role\""
    fi
  done

  # Build user JSON
  local user_json="{
    \"username\": \"$username\",
    \"email\": \"$email\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"credentials\": [{
      \"type\": \"password\",
      \"value\": \"$password\",
      \"temporary\": false
    }]"

  # Add attributes if DOA level is set
  if [ ! -z "$doa_level" ]; then
    user_json="$user_json,
    \"attributes\": {
      \"doa_level\": [\"$doa_level\"]
    }"
  fi

  user_json="$user_json
  }"

  # Create user
  response=$(curl -s -w "\n%{http_code}" -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$user_json")

  http_code=$(echo "$response" | tail -n1)

  if [ "$http_code" -eq 201 ] || [ "$http_code" -eq 409 ]; then
    if [ "$http_code" -eq 409 ]; then
      echo "  User already exists: $username"
    else
      echo "  User created successfully: $username"
    fi

    # Get user ID
    user_id=$(curl -s -H "Authorization: Bearer $TOKEN" \
      "$KEYCLOAK_URL/admin/realms/$REALM/users?username=$username" | jq -r '.[0].id')

    # Assign roles
    echo "  Assigning roles to $username..."
    for role in "${ROLE_ARRAY[@]}"; do
      # Get role representation
      role_repr=$(curl -s -H "Authorization: Bearer $TOKEN" \
        "$KEYCLOAK_URL/admin/realms/$REALM/roles/$role")

      if [ "$role_repr" != "null" ] && [ ! -z "$role_repr" ]; then
        curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users/$user_id/role-mappings/realm" \
          -H "Authorization: Bearer $TOKEN" \
          -H "Content-Type: application/json" \
          -d "[$role_repr]" > /dev/null
        echo "    - Assigned role: $role"
      fi
    done

    echo "  User $username setup complete!"
  else
    echo "  ERROR: Failed to create user $username (HTTP $http_code)"
  fi

  echo ""
}

# Create test users
echo "Creating test users..."
echo ""

# 1. Alice - Regular Employee (Requester)
create_user "alice" "alice@company.com" "Test1234!" "employee,asset_request_requester" ""

# 2. Bob - HR Manager (Level 1 Approver)
create_user "bob.hr" "bob@company.com" "Test1234!" "employee,hr_manager,asset_request_approver,doa_approver_level1" "1"

# 3. Charlie - IT Head (Level 2 Approver)
create_user "charlie.it" "charlie@company.com" "Test1234!" "employee,it_head,asset_request_approver,inventory_manager,doa_approver_level2,department_head" "2"

# 4. David - Finance Head / CFO (Level 4 Approver)
create_user "david.finance" "david@company.com" "Test1234!" "employee,finance_head,doa_approver_level3,doa_approver_level4,department_head" "4"

# 5. Emma - Super Admin
create_user "emma.admin" "emma@company.com" "Test1234!" "super_admin,admin" "4"

# 6. Frank - Procurement Manager
create_user "frank.procurement" "frank@company.com" "Test1234!" "employee,procurement_manager,procurement_approver,doa_approver_level1" "1"

# 7. Grace - Inventory Manager
create_user "grace.inventory" "grace@company.com" "Test1234!" "employee,inventory_manager,hub_manager" ""

# 8. Henry - Transport Manager
create_user "henry.transport" "henry@company.com" "Test1234!" "employee,logistics_manager,transport_approver,doa_approver_level1" "1"

# 9. Iris - Warehouse Staff
create_user "iris.warehouse" "iris@company.com" "Test1234!" "employee,warehouse_staff" ""

# 10. Jack - Driver
create_user "jack.driver" "jack@company.com" "Test1234!" "employee,driver" ""

echo "=========================================="
echo "Test Users Creation Complete!"
echo "=========================================="
echo ""
echo "Created Users:"
echo "----------------------------------------"
echo "1. alice           - Employee (Requester)"
echo "2. bob.hr          - HR Manager (DOA Level 1)"
echo "3. charlie.it      - IT Head (DOA Level 2)"
echo "4. david.finance   - Finance Head (DOA Level 4)"
echo "5. emma.admin      - Super Admin"
echo "6. frank.procurement - Procurement Manager"
echo "7. grace.inventory - Inventory Manager"
echo "8. henry.transport - Transport Manager"
echo "9. iris.warehouse  - Warehouse Staff"
echo "10. jack.driver    - Driver"
echo ""
echo "All users have password: Test1234!"
echo ""
echo "Test login at: $KEYCLOAK_URL/realms/$REALM/account"
echo "Admin console: $KEYCLOAK_URL/admin"
echo ""
