# Keycloak Realm Setup Guide

## Quick Start

Keycloak is running at: **http://localhost:8090**

Admin credentials:
- **Username**: `admin`
- **Password**: `REDACTED_PASSWORD`

## Create the Werkflow Realm

### Step 1: Access Admin Console

1. Open http://localhost:8090/admin
2. Log in with admin/REDACTED_PASSWORD

### Step 2: Create New Realm

1. Hover over the realm selector (top-left corner)
2. Click **Create Realm**
3. Enter realm name: `werkflow`
4. Click **Create**

### Step 3: Create OAuth Client

1. Go to **Clients** → **Create Client**
2. Client type: **OpenID Connect**
3. Client ID: `werkflow-portal`
4. Click **Next**

**Capability config:**
- Standard flow enabled: **On**
- Direct access grants enabled: **On**
- Client authentication: **On**

Click **Next** → **Save**

### Step 4: Configure Client

1. Go to the `werkflow-portal` client
2. **Access** tab:
   - Valid redirect URIs: `http://localhost:4000/api/auth/callback/keycloak`
   - Valid post logout redirect URIs: `http://localhost:4000/api/auth/logout`
   - Web origins: `http://localhost:4000`
   - Click **Save**

3. **Credentials** tab:
   - Copy the **Client secret**: `REDACTED_KC_PORTAL_SECRET`
   - Click **Regenerate** if you need a new secret (update docker-compose env vars if changed)

### Step 5: Create Test Users

1. Go to **Users** → **Create User**
2. Create user **admin**:
   - Email: `admin@werkflow.local`
   - Email verified: **On**
   - Set password: `REDACTED_PASSWORD` (not temporary)

3. Create user **testuser**:
   - Email: `testuser@werkflow.local`
   - Email verified: **On**
   - Set password: `password123` (not temporary)

### Step 6: Assign Roles

1. Go to Users → **admin** → **Role mapping**
2. Assign realm roles: **admin**

3. Go to Users → **testuser** → **Role mapping**
4. Assign realm roles: **user**, **workflow-designer**

### Step 7: Create Realm Roles (Optional)

If roles don't exist, create them:

1. Go to **Realm roles** → **Create role**
2. Create these roles:
   - `user` — User role
   - `admin` — Administrator
   - `workflow-designer` — Workflow Designer
   - `doa_approver_level1` — DOA Approver L1
   - `doa_approver_level2` — DOA Approver L2
   - `doa_approver_level3` — DOA Approver L3
   - `doa_approver_level4` — DOA Approver L4

## Verify Setup

1. Navigate to http://localhost:4000
2. You should be redirected to Keycloak login
3. Login with **testuser** / **password123**
4. You should be redirected back to the portal

## Automated Import (JSON)

A Keycloak realm JSON export is available at:
```
infrastructure/keycloak/realms/werkflow-realm.json
```

To use automatic import:
1. Uncomment `--import-realm` in docker-compose.yml KC command
2. Add volume mount: `../keycloak/realms:/opt/keycloak/data/import`
3. Keycloak will auto-import on startup

Note: Manual realm creation is more reliable for development.
