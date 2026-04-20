# Keycloak RBAC Configuration

## Overview

This directory contains Keycloak realm configuration and management scripts for the Werkflow platform RBAC system.

## Files

- `werkflow-realm.json` - Complete realm configuration with roles, groups, clients, and mappers
- `import-realm.sh` - Script to import realm into Keycloak
- `sample-users.json` - Sample users for testing
- `README.md` - This file

## Quick Start

### 1. Start Keycloak

Ensure Keycloak is running:

```bash
cd /Users/lamteiwahlang/Projects/werkflow/infrastructure/docker
docker-compose up -d keycloak
```

Wait for Keycloak to be ready (check http://localhost:8090/health/ready).

### 2. Import Realm

```bash
cd /Users/lamteiwahlang/Projects/werkflow/infrastructure/keycloak
./import-realm.sh
```

This will:
- Wait for Keycloak to be ready
- Import the werkflow-platform realm
- Create all roles, groups, clients, and mappers
- Verify the import

### 3. Verify Import

Login to Keycloak Admin Console:
- URL: http://localhost:8090/admin
- Username: `admin`
- Password: `REDACTED_PASSWORD`

Navigate to the `werkflow-platform` realm and verify:
- Roles: 25+ realm roles created
- Groups: 6 department groups with sub-groups
- Clients: 3 clients (werkflow-admin-portal, werkflow-engine, werkflow-hr-portal)
- Users: 1 admin user created

### 4. Create Test Users

#### Option A: Manually via Admin Console

Follow the steps in `/docs/Security/Keycloak-Operations-Guide.md` under "Adding a New Employee".

#### Option B: Using Sample Users File

Import sample users (requires manual POST to Keycloak API):

```bash
# Get admin token
TOKEN=$(curl -s -X POST "http://localhost:8090/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=REDACTED_PASSWORD" \
  -d "grant_type=password" | jq -r '.access_token')

# Import each user from sample-users.json
cat sample-users.json | jq -c '.[]' | while read user; do
  curl -X POST "http://localhost:8090/admin/realms/werkflow-platform/users" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$user"
done
```

Note: You'll need to update `manager_id` attributes after users are created.

## Realm Structure

### Roles

**Global Roles**:
- `admin` - System administrator
- `super_admin` - Super administrator (C-Suite)
- `employee` - Base employee role

**Functional Roles**:
- `asset_request_requester` - Can submit asset requests
- `asset_request_approver` - Can approve asset requests
- `doa_approver_level1` - Approve up to $1,000
- `doa_approver_level2` - Approve up to $10,000
- `doa_approver_level3` - Approve up to $100,000
- `doa_approver_level4` - Approve unlimited

**Department Head Roles** (Composite):
- `hr_head` - HR Department Head
- `it_head` - IT Department Head
- `finance_head` - Finance Department Head
- `procurement_head` - Procurement Department Head
- `transport_head` - Transport Department Head
- `inventory_head` - Inventory Department Head

**Specialized Roles**:
- `department_poc` - Department Point of Contact
- `inventory_manager` - Manage inventory
- `hub_manager` - Manage warehouse hub
- `central_hub_manager` - Manage central hub
- `procurement_approver` - Approve procurement
- `transport_manager` - Manage transport
- `workflow_designer` - Design workflows

### Groups

```
/HR Department
├── /HR Department/Managers
├── /HR Department/Specialists
└── /HR Department/POC

/IT Department
├── /IT Department/Managers
├── /IT Department/Inventory
└── /IT Department/POC

/Finance Department
├── /Finance Department/Managers
├── /Finance Department/Approvers
├── /Finance Department/Officers
└── /Finance Department/POC

/Procurement Department
├── /Procurement Department/Managers
├── /Procurement Department/Specialists
└── /Procurement Department/POC

/Transport Department
├── /Transport Department/Managers
├── /Transport Department/Coordinators
└── /Transport Department/Drivers

/Inventory Warehouse
├── /Inventory Warehouse/Central Hub
├── /Inventory Warehouse/Hub A
└── /Inventory Warehouse/Hub B
```

### Clients

1. **werkflow-admin-portal**
   - Type: Confidential
   - Protocol: OpenID Connect
   - Flows: Authorization Code + PKCE
   - Redirect URIs: http://localhost:4000/*, http://localhost:3000/*
   - Client Roles: admin, manager, poc, approver, requester, viewer

2. **werkflow-engine**
   - Type: Confidential
   - Protocol: OpenID Connect
   - Flows: Service Account (Client Credentials)
   - Purpose: Backend workflow engine API access
   - Client Roles: workflow_admin, task_processor, event_publisher

3. **werkflow-hr-portal**
   - Type: Confidential
   - Protocol: OpenID Connect
   - Flows: Authorization Code + PKCE
   - Redirect URIs: http://localhost:4001/*

### Custom Attributes

All clients include protocol mappers for these custom attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `department` | String | User's department |
| `employee_id` | String | Employee identifier |
| `manager_id` | String | Manager's Keycloak user ID |
| `cost_center` | String | Cost center code |
| `doa_level` | Integer | Delegation of Authority level (1-4) |
| `is_poc` | Boolean | Is department POC |
| `hub_id` | String | Warehouse hub identifier |

## Configuration

### Environment Variables

The import script uses these environment variables (with defaults):

```bash
KEYCLOAK_URL=http://localhost:8090
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=REDACTED_PASSWORD
```

Override if needed:

```bash
export KEYCLOAK_URL=https://keycloak.company.com
./import-realm.sh
```

### Client Secrets

**IMPORTANT**: The realm configuration contains default client secrets. These MUST be changed in production:

1. Login to Keycloak Admin Console
2. Navigate to: Clients → Select client → Credentials
3. Click "Regenerate Secret"
4. Update application environment variables with new secret

Default secrets (CHANGE THESE):
- `werkflow-admin-portal`: `REDACTED_KC_PORTAL_SECRET`
- `werkflow-engine`: set via `ENGINE_CLIENT_SECRET` env var in `config/env/.env.engine`
- `werkflow-hr-portal`: `REDACTED_KC_SECRET`

## Updating the Realm

### Export Current Realm

To export the current realm configuration:

```bash
# Via Docker
docker exec -it werkflow-keycloak /opt/keycloak/bin/kc.sh export \
  --realm werkflow-platform \
  --file /tmp/werkflow-realm-export.json

# Copy from container
docker cp werkflow-keycloak:/tmp/werkflow-realm-export.json ./werkflow-realm-backup.json
```

### Re-import Realm

To update the realm (WARNING: This deletes existing configuration):

```bash
./import-realm.sh
```

The script will ask for confirmation before deleting the existing realm.

## Troubleshooting

### Import Script Fails

**Error: "Keycloak did not become ready in time"**

Solution:
- Increase MAX_RETRIES in script
- Check Keycloak container logs: `docker logs werkflow-keycloak`
- Verify database connection

**Error: "Failed to get access token"**

Solution:
- Verify admin credentials
- Check KEYCLOAK_ADMIN and KEYCLOAK_ADMIN_PASSWORD environment variables
- Login manually to Admin Console to verify credentials

### Realm Already Exists

The script will prompt to delete and recreate. To force delete:

```bash
# Get token
TOKEN=$(curl -s -X POST "http://localhost:8090/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=REDACTED_PASSWORD" \
  -d "grant_type=password" | jq -r '.access_token')

# Delete realm
curl -X DELETE "http://localhost:8090/admin/realms/werkflow-platform" \
  -H "Authorization: Bearer $TOKEN"

# Re-run import
./import-realm.sh
```

### Users Not Imported

The `sample-users.json` file is for reference only. You must import users manually via:
1. Keycloak Admin Console (recommended for small number of users)
2. Keycloak Admin REST API (for bulk import)
3. LDAP/Active Directory integration (for enterprise)

## Production Deployment

### Pre-Production Checklist

- [ ] Change all client secrets
- [ ] Update redirect URIs to production URLs
- [ ] Enable HTTPS (sslRequired: EXTERNAL or ALL)
- [ ] Configure SMTP for email notifications
- [ ] Set up database backups
- [ ] Enable brute force protection
- [ ] Configure password policy
- [ ] Set up monitoring and alerts
- [ ] Test token expiration settings
- [ ] Verify MFA for admin accounts
- [ ] Review and test all roles and groups
- [ ] Document custom configuration

### Recommended Production Settings

Update in Keycloak Admin Console:

1. **Realm Settings → General**
   - SSL Required: EXTERNAL (or ALL for maximum security)
   - User Registration: Disabled
   - Verify Email: Enabled
   - Login with Email: Enabled

2. **Realm Settings → Tokens**
   - Access Token Lifespan: 5-15 minutes
   - Refresh Token Max Reuse: 0 (disabled)
   - SSO Session Idle: 30 minutes
   - SSO Session Max: 10 hours

3. **Realm Settings → Security Defenses**
   - Brute Force Detection: Enabled
   - Max Login Failures: 3-5
   - Wait Increment: 60 seconds
   - Max Wait: 900 seconds (15 minutes)

4. **Authentication → Required Actions**
   - Verify Email: Enabled
   - Update Password: Enabled (for temporary passwords)

5. **Authentication → Password Policy**
   - Minimum Length: 12
   - Not Username: Enabled
   - Uppercase Characters: 1
   - Lowercase Characters: 1
   - Digits: 1
   - Special Characters: 1
   - Not Recently Used: 3
   - Expire Password: 90 days

## Integration

### Backend (Spring Boot)

See `/docs/Security/Keycloak-Implementation-Guide.md` for:
- Spring Security configuration
- JWT token validation
- Role extraction and authorization
- Keycloak Admin Client usage

### Frontend (Next.js)

See `/docs/Security/Keycloak-Implementation-Guide.md` for:
- NextAuth.js configuration
- Client-side role checks
- Protected routes and components

## References

- Keycloak RBAC Design: `/docs/Security/Keycloak-RBAC-Design.md`
- Operations Guide: `/docs/Security/Keycloak-Operations-Guide.md`
- Implementation Guide: `/docs/Security/Keycloak-Implementation-Guide.md`
- Database Schema: `/services/engine/src/main/resources/db/migration/V3__create_rbac_tables.sql`

## Support

For issues or questions:
1. Check troubleshooting sections in documentation
2. Review Keycloak logs: `docker logs werkflow-keycloak`
3. Verify token claims at https://jwt.io
4. Consult Keycloak documentation: https://www.keycloak.org/documentation
