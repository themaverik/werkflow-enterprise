# Keycloak Authentication Flow Architecture

**Document:** Technical architecture for OAuth2 authentication flow
**Last Updated:** 2025-11-25

---

## Overview

Werkflow uses **Keycloak** as the Identity and Access Management (IAM) provider with **NextAuth.js v5** handling OAuth2 flows on the frontend.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Browser                             │
└────────┬──────────────────────────────────────────────┬─────────┘
         │                                               │
         │ 1. GET /login                                 │ 5. Redirect with
         │                                               │    auth code
         ▼                                               │
┌─────────────────────────┐                             │
│   Admin Portal          │                             │
│   (Next.js 14)          │                             │
│   Port: 4000            │                             │
│                         │                             │
│   /app/login/page.tsx   │◄────────────────────────────┘
│   /app/api/auth/        │
│     [...nextauth]/      │
│       route.ts          │
└────────┬────────────────┘
         │
         │ 2. signIn("keycloak")
         │    redirect to Keycloak
         │
         ▼
┌─────────────────────────┐
│   Keycloak              │
│   Port: 8090 (host)     │
│   Port: 8080 (internal) │
│                         │
│   Realm: werkflow       │
│   Client: werkflow-     │
│           admin-portal  │
└────────┬────────────────┘
         │
         │ 3. User enters
         │    credentials
         │
         │ 4. Validate and
         │    generate code
         │
         └─────────────────┐
                           │
                           ▼
         ┌─────────────────────────────┐
         │ NextAuth Callback Handler   │
         │ /api/auth/callback/keycloak │
         └─────────┬───────────────────┘
                   │
                   │ 6. Exchange code
                   │    for access token
                   │
                   ▼
         ┌─────────────────────────┐
         │ Keycloak Token Endpoint │
         │ (Internal: keycloak:8080)│
         └─────────┬───────────────┘
                   │
                   │ 7. Return JWT tokens:
                   │    - access_token
                   │    - refresh_token
                   │    - id_token
                   │
                   ▼
         ┌─────────────────────────┐
         │ NextAuth Session        │
         │ Create JWT session      │
         │ Set HTTP-only cookie    │
         └─────────┬───────────────┘
                   │
                   │ 8. Redirect to
                   │    /portal/tasks
                   │
                   ▼
         ┌─────────────────────────┐
         │ Protected Route         │
         │ Middleware checks auth  │
         └─────────────────────────┘
```

---

## Detailed Flow

### Phase 1: Initiate Login

**User Action:** Click "Sign in with Keycloak" button

**Frontend Code:** `/frontends/admin-portal/app/login/page.tsx`
```tsx
<form action={async () => {
  "use server"
  await signIn("keycloak", { redirectTo: "/portal/tasks" })
}}>
```

**Request:**
```http
POST /api/auth/signin/keycloak
Host: localhost:4000
```

**NextAuth Response:**
```http
HTTP/1.1 302 Found
Location: http://localhost:8090/realms/werkflow/protocol/openid-connect/auth?
  client_id=werkflow-admin-portal&
  redirect_uri=http://localhost:4000/api/auth/callback/keycloak&
  response_type=code&
  scope=openid+email+profile&
  state=abc123&
  code_challenge=xyz789&
  code_challenge_method=S256
```

### Phase 2: Keycloak Authentication

**User sees:** Keycloak login page at `http://localhost:8090/realms/werkflow/protocol/openid-connect/auth`

**User Action:** Enter username and password

**Keycloak:** Validates credentials against realm users

**Keycloak Response:**
```http
HTTP/1.1 302 Found
Location: http://localhost:4000/api/auth/callback/keycloak?
  code=eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0...&
  session_state=12345678-1234-1234-1234-123456789012
```

### Phase 3: Token Exchange

**NextAuth Handler:** `/app/api/auth/[...nextauth]/route.ts`

**Request to Keycloak (Server-Side):**
```http
POST http://keycloak:8080/realms/werkflow/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(werkflow-admin-portal:4uohM7y1sGkOcR2gTR1APo4JDmkwRxSv)

grant_type=authorization_code&
code=eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0...&
redirect_uri=http://localhost:4000/api/auth/callback/keycloak&
code_verifier=abc123...
```

**Keycloak Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjEyMyJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_expires_in": 15552000,
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid email profile"
}
```

### Phase 4: Session Creation

**NextAuth Callback:** `auth.config.ts` → `callbacks.jwt()`

**JWT Token Processing:**
```typescript
async jwt({ token, account, profile }) {
  if (account) {
    token.accessToken = account.access_token
    token.refreshToken = account.refresh_token
    token.idToken = account.id_token
    token.expiresAt = account.expires_at

    // Extract roles from Keycloak token
    const decodedToken = decodeJwt(account.access_token)
    token.roles = decodedToken.realm_access?.roles || []
  }
  return token
}
```

**Session Callback:**
```typescript
async session({ session, token }) {
  session.accessToken = token.accessToken
  session.user.roles = token.roles
  return session
}
```

**Cookie Set:**
```http
Set-Cookie: next-auth.session-token=eyJhbGciOiJIUzI1NiJ9...;
  Path=/;
  HttpOnly;
  Secure;
  SameSite=Lax
```

### Phase 5: Redirect to Protected Route

**NextAuth Response:**
```http
HTTP/1.1 302 Found
Location: /portal/tasks
```

**Middleware Check:** `middleware.ts`
```typescript
export default auth((req) => {
  if (!req.auth) {
    return NextResponse.redirect(new URL('/login', req.url))
  }
})
```

---

## JWT Token Structure

### Access Token Claims

```json
{
  "exp": 1732544400,
  "iat": 1732540800,
  "jti": "12345678-1234-1234-1234-123456789012",
  "iss": "http://localhost:8090/realms/werkflow",
  "aud": "werkflow-admin-portal",
  "sub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "typ": "Bearer",
  "azp": "werkflow-admin-portal",
  "session_state": "98765432-4321-4321-4321-210987654321",
  "realm_access": {
    "roles": [
      "super_admin",
      "admin",
      "employee"
    ]
  },
  "resource_access": {
    "werkflow-admin-portal": {
      "roles": [
        "workflow_designer"
      ]
    }
  },
  "scope": "openid email profile",
  "email_verified": true,
  "name": "Emma Admin",
  "preferred_username": "emma.admin",
  "given_name": "Emma",
  "family_name": "Admin",
  "email": "emma@company.com"
}
```

### Custom Claims for Werkflow

Additional claims can be added via Keycloak User Attributes:

```json
{
  "department": "IT",
  "manager_id": "b2c3d4e5-f6g7-8901-bcde-f12345678901",
  "doa_level": 4,
  "is_poc": true,
  "employee_id": "EMP-001"
}
```

---

## Network Configuration

### Three-URL Strategy

Werkflow uses a three-URL strategy to handle Docker networking:

| URL Type | Value | Used For |
|----------|-------|----------|
| **KEYCLOAK_ISSUER_BROWSER** | http://localhost:8090/realms/werkflow | Browser redirects (user-facing) |
| **KEYCLOAK_ISSUER_PUBLIC** | http://localhost:8090/realms/werkflow | Token validation (issuer claim) |
| **KEYCLOAK_ISSUER_INTERNAL** | http://keycloak:8080/realms/werkflow | Server-side API calls (token exchange) |

### Why Three URLs?

1. **Browser URL (localhost:8090):**
   - User's browser can't reach Docker internal network
   - Must use host-mapped port (8090 → 8080)

2. **Public Issuer (localhost:8090):**
   - Keycloak advertises this in JWT token's `iss` claim
   - NextAuth validates token against this issuer
   - Must match what Keycloak returns

3. **Internal URL (keycloak:8080):**
   - Admin portal container reaches Keycloak via Docker network
   - Faster (no port mapping overhead)
   - More secure (internal network only)

### Docker Compose Configuration

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    container_name: werkflow-keycloak
    environment:
      KC_HOSTNAME: localhost
      KC_HOSTNAME_PORT: 8090
      KC_PROXY: edge
    ports:
      - "8090:8080"
    networks:
      - werkflow-network

  admin-portal:
    container_name: werkflow-admin-portal
    environment:
      KEYCLOAK_ISSUER_BROWSER: http://localhost:8090/realms/werkflow
      KEYCLOAK_ISSUER_PUBLIC: http://localhost:8090/realms/werkflow
      KEYCLOAK_ISSUER_INTERNAL: http://keycloak:8080/realms/werkflow
    networks:
      - werkflow-network
```

---

## Security Considerations

### 1. PKCE (Proof Key for Code Exchange)

NextAuth.js automatically uses PKCE for security:

**Authorization Request:**
```
code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
code_challenge_method=S256
```

**Token Exchange:**
```
code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

Prevents authorization code interception attacks.

### 2. HTTP-Only Cookies

Session cookie is HTTP-only:
```
Set-Cookie: next-auth.session-token=...; HttpOnly; Secure; SameSite=Lax
```

JavaScript cannot access the cookie (XSS protection).

### 3. CORS Configuration

Backend services must allow admin portal origin:

```yaml
spring:
  web:
    cors:
      allowed-origins:
        - http://localhost:4000
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
      allow-credentials: true
```

### 4. Token Validation

Backend services validate JWT tokens:

1. **Verify signature** using Keycloak's public key (JWK)
2. **Check issuer** matches expected value
3. **Check audience** matches service ID
4. **Check expiration** (exp claim)
5. **Extract roles** from realm_access claim

---

## Error Scenarios

### Scenario 1: Realm Doesn't Exist

**Symptom:**
```
HTTP/1.1 302 Found
Location: /api/auth/error?error=Configuration
```

**Cause:** Keycloak realm "werkflow" doesn't exist.

**Solution:** Import realm from backup file.

### Scenario 2: Invalid Client Secret

**Symptom:**
```json
{
  "error": "unauthorized_client",
  "error_description": "Invalid client credentials"
}
```

**Cause:** Client secret in docker-compose.yml doesn't match Keycloak.

**Solution:** Update client secret to match.

### Scenario 3: Invalid Redirect URI

**Symptom:**
```
error=invalid_redirect_uri
```

**Cause:** Callback URL not registered in Keycloak client.

**Solution:** Add `http://localhost:4000/*` to Valid Redirect URIs.

### Scenario 4: CORS Error

**Symptom:**
```
Access to fetch at 'http://localhost:8081/api/...' from origin 'http://localhost:4000'
has been blocked by CORS policy
```

**Cause:** Backend doesn't allow admin portal origin.

**Solution:** Configure CORS in Spring Boot application.

---

## Role-Based Authorization

### Frontend Authorization

**Middleware Check:**
```typescript
// middleware.ts
export default auth((req) => {
  const isProtectedRoute = req.nextUrl.pathname.startsWith('/portal')

  if (isProtectedRoute && !req.auth) {
    return NextResponse.redirect(new URL('/login', req.url))
  }
})
```

**Component-Level Check:**
```tsx
// Check user role
const session = await auth()
const isAdmin = session?.user?.roles?.includes('super_admin')

{isAdmin && (
  <Button>Admin Only Action</Button>
)}
```

### Backend Authorization

**Spring Security Configuration:**
```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminEndpoint() {
  // ...
}

@PreAuthorize("hasAnyRole('ASSET_REQUEST_APPROVER', 'HR_MANAGER')")
public ResponseEntity<?> approveRequest() {
  // ...
}
```

**Custom DOA Authorization:**
```java
public ResponseEntity<?> approveFinance(String workflowId) {
  Jwt jwt = (Jwt) SecurityContextHolder.getContext()
    .getAuthentication().getPrincipal();

  Integer userDoaLevel = jwt.getClaim("doa_level");
  Workflow workflow = getWorkflow(workflowId);
  int requiredLevel = calculateRequiredDoaLevel(workflow.getAmount());

  if (userDoaLevel < requiredLevel) {
    throw new AccessDeniedException("Insufficient DOA level");
  }

  // Approve workflow
}
```

---

## Monitoring and Debugging

### Enable Debug Logging

**Admin Portal (Next.js):**
```bash
DEBUG=*auth* npm run dev
```

**Backend Services (Spring Boot):**
```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.security.oauth2: TRACE
```

**Keycloak:**
```bash
docker compose logs -f keycloak | grep -E "ERROR|WARN|token"
```

### Useful Debug Endpoints

**Keycloak OIDC Configuration:**
```bash
curl http://localhost:8090/realms/werkflow/.well-known/openid-configuration | jq
```

**NextAuth Session:**
```bash
curl -b cookies.txt http://localhost:4000/api/auth/session | jq
```

**Decode JWT Token:**
```bash
echo "eyJhbGciOiJSUzI1NiJ9..." | cut -d. -f2 | base64 -d | jq
```

---

## Performance Considerations

### Token Caching

NextAuth caches tokens in JWT session:
- Reduces Keycloak API calls
- Faster page loads
- Session expires when access token expires

### Token Refresh

NextAuth automatically refreshes tokens:
```typescript
async jwt({ token, account }) {
  // Check if token expired
  if (Date.now() < token.expiresAt) {
    return token
  }

  // Refresh token
  const response = await fetch(tokenUrl, {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: token.refreshToken
    })
  })

  const newTokens = await response.json()
  return {
    ...token,
    accessToken: newTokens.access_token,
    expiresAt: Date.now() + newTokens.expires_in * 1000
  }
}
```

### Connection Pooling

Backend services use connection pooling for Keycloak Admin API:
```java
@Bean
public Keycloak keycloak() {
  return KeycloakBuilder.builder()
    .serverUrl("http://keycloak:8080")
    .realm("werkflow")
    .clientId("workflow-engine")
    .clientSecret("...")
    .connectionPoolSize(10)
    .build();
}
```

---

## Related Documentation

- **Authentication 404 Fix:** `/docs/Troubleshooting/Authentication-404-Callback-Fix.md`
- **RBAC Implementation:** `/docs/Keycloak-Implementation-Guide.md`
- **Docker Configuration:** `/docs/OAuth2-Docker-Configuration.md`

---

## Summary

Werkflow implements **enterprise-grade authentication** using:
- **Keycloak** for identity management
- **OAuth2 + OIDC** for secure authentication
- **JWT tokens** for stateless authorization
- **PKCE** for security
- **Role-based access control** (RBAC)
- **Delegation of Authority** (DOA) for workflow approvals

**Key Files:**
- Frontend: `/frontends/admin-portal/auth.config.ts`
- Realm Export: `/infrastructure/keycloak/keycloak-realm-export.json`
- Docker Config: `/infrastructure/docker/docker-compose.yml`

**Quick Links:**
- Keycloak Admin: http://localhost:8090/admin
- Admin Portal: http://localhost:4000
- OIDC Config: http://localhost:8090/realms/werkflow/.well-known/openid-configuration
