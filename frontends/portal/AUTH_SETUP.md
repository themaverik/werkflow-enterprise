# Authentication Setup Guide

This guide explains how to set up Keycloak authentication for the Werkflow frontend.

## Overview

The frontend uses **NextAuth v5 (Auth.js)** with **Keycloak** as the identity provider. Authentication is required for:
- **Studio routes** (`/studio/*`) - Requires `HR_ADMIN` role
- **Portal routes** (`/portal/*`) - Requires any authenticated user

## Prerequisites

1. **Keycloak server** running on `http://localhost:8090`
2. **Werkflow realm** created in Keycloak
3. **Frontend client** configured in Keycloak

## Keycloak Client Setup

### 1. Create Client in Keycloak Admin Console

1. Navigate to http://localhost:8090/admin
2. Login with `admin` / `admin123`
3. Select the `werkflow` realm
4. Go to **Clients** → **Create client**

### 2. Client Configuration

**General Settings:**
- **Client ID**: `werkflow-frontend`
- **Name**: `Werkflow Frontend Application`
- **Description**: `Next.js frontend for Werkflow`
- **Client authentication**: ON (Confidential client)
- **Authorization**: OFF (not needed)
- **Standard flow enabled**: ON
- **Direct access grants**: OFF (not needed for web app)

**Access Settings:**
- **Root URL**: `http://localhost:3000`
- **Home URL**: `http://localhost:3000`
- **Valid redirect URIs**:
  - `http://localhost:3000/*`
  - `http://localhost:3000/api/auth/callback/keycloak`
- **Valid post logout redirect URIs**: `http://localhost:3000/*`
- **Web origins**: `http://localhost:3000`

**Capability Config:**
- **Client authentication**: ON
- **Authorization**: OFF
- **Authentication flow**:
  - ✅ Standard flow
  - ❌ Direct access grants
  - ❌ Implicit flow
  - ❌ Service accounts roles

### 3. Get Client Secret

1. Go to **Clients** → `werkflow-frontend` → **Credentials** tab
2. Copy the **Client secret** value
3. Save it for the `.env.local` file

### 4. Configure Client Scopes

1. Go to **Clients** → `werkflow-frontend` → **Client scopes** tab
2. Ensure these are assigned:
   - `email` (default)
   - `profile` (default)
   - `roles` (default)

### 5. Verify Realm Roles

Ensure these roles exist in the realm:
- `HR_ADMIN` - Full access to Studio and Portal
- `HR_MANAGER` - Access to Portal + some admin features
- `MANAGER` - Access to Portal, approve tasks
- `EMPLOYEE` - Basic access to Portal

## Frontend Environment Configuration

### 1. Create `.env.local` File

```bash
cd frontend
cp .env.local.example .env.local
```

### 2. Configure Environment Variables

Edit `.env.local`:

```env
# Backend API
NEXT_PUBLIC_API_URL=http://localhost:8080/api

# NextAuth Configuration
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-secret-key-here

# Keycloak Configuration
KEYCLOAK_CLIENT_ID=werkflow-frontend
KEYCLOAK_CLIENT_SECRET=your-client-secret-from-keycloak
KEYCLOAK_ISSUER=http://localhost:8090/realms/werkflow
```

### 3. Generate NextAuth Secret

```bash
openssl rand -base64 32
```

Copy the output and paste it as `NEXTAUTH_SECRET` value.

## Testing Authentication

### 1. Start the Application

```bash
cd frontend
npm install
npm run dev
```

### 2. Test Login Flow

1. Navigate to http://localhost:3000
2. Click on any protected route (e.g., "My Tasks")
3. You should be redirected to `/login`
4. Click "Sign in with Keycloak"
5. You'll be redirected to Keycloak login page
6. Enter your Keycloak credentials
7. After successful authentication, you'll be redirected back to the app

### 3. Verify Session

- Check the user menu in the top-right corner
- Your name, email, and roles should be displayed
- Click on your profile to see all assigned roles

### 4. Test Role-Based Access

**HR_ADMIN Access:**
1. Login with a user that has `HR_ADMIN` role
2. Navigate to `/studio/processes`
3. You should see the Process Designer page

**Non-Admin Access:**
1. Login with a user without `HR_ADMIN` role
2. Try to access `/studio/processes`
3. You should see "Access Denied" message

### 5. Test Logout

1. Click on user menu
2. Click "Sign out"
3. You should be redirected to the home page
4. Session should be cleared

## Architecture Overview

### Authentication Flow

```
1. User clicks login
   ↓
2. NextAuth redirects to Keycloak
   ↓
3. User authenticates with Keycloak
   ↓
4. Keycloak redirects back with authorization code
   ↓
5. NextAuth exchanges code for tokens
   ↓
6. Session created with user info and roles
   ↓
7. User redirected to original destination
```

### File Structure

```
frontend/
├── auth.config.ts              # NextAuth configuration
├── auth.ts                     # NextAuth instance
├── middleware.ts               # Route protection middleware
├── types/next-auth.d.ts        # TypeScript session types
├── app/
│   ├── api/auth/[...nextauth]/ # NextAuth API routes
│   ├── login/                  # Login page
│   ├── (portal)/               # Protected portal routes
│   └── (studio)/               # Protected studio routes (HR_ADMIN)
└── components/
    ├── auth/
    │   └── user-menu.tsx       # User profile dropdown
    └── layout/
        └── header.tsx          # Navigation header
```

### Session Structure

```typescript
{
  user: {
    name: "John Doe",
    email: "john@werkflow.com",
    roles: ["HR_ADMIN", "MANAGER"]
  },
  accessToken: "eyJhbGciOiJSUzI1NiIs..."
}
```

### Role-Based Access Control

| Role | Access |
|------|--------|
| **HR_ADMIN** | Full access to Studio + Portal |
| **HR_MANAGER** | Portal access + some admin features |
| **MANAGER** | Portal access + task approval |
| **EMPLOYEE** | Basic Portal access |

**Protected in Code:**

```typescript
// In layout.tsx (Studio)
const hasAdminRole = session?.user?.roles?.includes("HR_ADMIN")

// In middleware.ts
const isProtectedRoute = pathname.startsWith('/studio') ||
                         pathname.startsWith('/portal')
```

## API Integration with Authentication

### Server Components

```typescript
import { auth } from "@/auth"
import { createAuthenticatedClient } from "@/lib/api/auth-client"

export default async function Page() {
  const session = await auth()

  if (!session?.accessToken) {
    return <div>Not authenticated</div>
  }

  const apiClient = createAuthenticatedClient(session.accessToken)
  const data = await apiClient.get('/workflows/processes')

  return <div>{/* Use data */}</div>
}
```

### Client Components

```typescript
'use client'

import { useSession } from "next-auth/react"

export function MyComponent() {
  const { data: session } = useSession()

  // Use session.accessToken for API calls
  return <div>{session?.user?.name}</div>
}
```

## Troubleshooting

### Issue: Redirect Loop

**Cause**: NEXTAUTH_URL doesn't match the actual URL
**Solution**: Ensure `NEXTAUTH_URL=http://localhost:3000` (no trailing slash)

### Issue: Invalid Redirect URI

**Cause**: Keycloak client redirect URIs don't match
**Solution**: Add `http://localhost:3000/api/auth/callback/keycloak` to Valid redirect URIs

### Issue: Roles Not Showing

**Cause**: Keycloak token doesn't include realm roles
**Solution**:
1. Go to Client Scopes → `roles` → Mappers
2. Verify `realm roles` mapper exists and is enabled

### Issue: 401 Unauthorized from Backend

**Cause**: Access token not being sent to backend
**Solution**: Use `createAuthenticatedClient(session.accessToken)` in server components

### Issue: Session Not Persisting

**Cause**: NEXTAUTH_SECRET is missing or invalid
**Solution**: Generate new secret with `openssl rand -base64 32`

## Production Deployment

### Environment Variables

```env
# Production Backend API
NEXT_PUBLIC_API_URL=https://api.werkflow.com

# NextAuth (Production)
NEXTAUTH_URL=https://werkflow.com
NEXTAUTH_SECRET=<strong-random-secret>

# Keycloak (Production)
KEYCLOAK_CLIENT_ID=werkflow-frontend-prod
KEYCLOAK_CLIENT_SECRET=<production-secret>
KEYCLOAK_ISSUER=https://auth.werkflow.com/realms/werkflow
```

### Keycloak Production Setup

1. Use HTTPS for all URLs
2. Use production client ID and secret
3. Update redirect URIs to production domain
4. Enable SSL required mode
5. Configure proper CORS settings

## Security Best Practices

✅ **Always use HTTPS in production**
✅ **Keep client secrets secure** (never commit to git)
✅ **Rotate secrets regularly**
✅ **Use strong NEXTAUTH_SECRET** (minimum 32 characters)
✅ **Validate roles on both frontend and backend**
✅ **Set secure cookie settings in production**
✅ **Enable CSRF protection** (enabled by default in NextAuth)
✅ **Implement rate limiting** on auth endpoints

## Next Steps

After authentication is working:
- **Phase 2**: Implement BPMN Designer (requires HR_ADMIN)
- **Phase 3**: Implement Form Builder (requires HR_ADMIN)
- **Phase 4**: Implement Task Portal (all authenticated users)

## References

- [NextAuth v5 Documentation](https://authjs.dev/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [OAuth 2.0 Flow](https://oauth.net/2/)
- [JWT Tokens](https://jwt.io/)

---

**Last Updated**: 2024-11-15
**NextAuth Version**: 5.0.0-beta.22
**Keycloak Version**: 23.0
