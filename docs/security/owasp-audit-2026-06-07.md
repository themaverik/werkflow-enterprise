# OWASP Security Audit — Werkflow Enterprise
Date: 2026-06-07
Auditor: Security Audit (Claude Sonnet 4.6)
Scope: A02–A10 + supplementary checks (A01 cross-tenant isolation audited separately)

---

## Executive Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 5 |
| HIGH | 7 |
| MEDIUM | 6 |
| LOW | 4 |
| INFO | 3 |
| **Total** | **25** |

All CRITICAL findings require resolution before production release. HIGH findings should be resolved before v1.0.0. MEDIUM findings should be tracked as post-release hardening items.

---

## Findings

---

### CRITICAL — A02 — Google OAuth Client Secret Committed to Repository

**File:** `infrastructure/docker/docker-compose.yml:97`

**Description:**
A live Google OAuth2 client secret is hardcoded in a committed `docker-compose.yml` file. This credential provides access to a Google Cloud project under OAuth2 client ID `946266379427-...`. Any person with read access to this repository can extract and abuse this credential.

**Evidence:**
```yaml
GOOGLE_CLIENT_ID: "946266379427-quk1e84mtj72o8caeaidsp4msdfb00li.apps.googleusercontent.com"
GOOGLE_CLIENT_SECRET: "REDACTED_GOOGLE_SECRET"
```

**Fix:**
1. Rotate the Google OAuth2 client secret immediately in the Google Cloud Console.
2. Remove the hardcoded values from `docker-compose.yml` — replace with `${GOOGLE_CLIENT_ID}` and `${GOOGLE_CLIENT_SECRET}` environment variable references.
3. Add these variables to the `.env.shared.example` file with placeholder values.
4. Audit git history: if this file has ever been pushed to a remote repository, assume the secret is compromised regardless of the rotation.

---

### CRITICAL — A02 — NEXTAUTH_SECRET and Keycloak Client Secret Hardcoded in Docker Compose

**File:** `infrastructure/docker/docker-compose.yml:322,333`

**Description:**
Both the `NEXTAUTH_SECRET` (used to sign Next-Auth session JWTs) and the Keycloak portal client secret are hardcoded directly in the committed `docker-compose.yml`. If `docker-compose.yml` is used as-is in a staging or production environment, these fixed values allow session token forgery and OAuth2 client impersonation.

**Evidence:**
```yaml
NEXTAUTH_SECRET: xg7bVqYgFgTDwpVWe6gbyWwZVb8f0Yd1Wo+ZGuKdm/U=
...
KEYCLOAK_CLIENT_SECRET: REDACTED_KC_PORTAL_SECRET
```
The same secret `REDACTED_KC_PORTAL_SECRET` also appears in `application.yml:121` as the default fallback, and in `config/env/.env.local:38`.

**Fix:**
1. Replace all hardcoded values in `docker-compose.yml` with environment variable references (e.g., `${NEXTAUTH_SECRET}`, `${KEYCLOAK_CLIENT_SECRET}`).
2. Remove the default fallback in `application.yml:121` — set a required-at-startup guard instead.
3. Generate unique per-environment secrets. Do not reuse the same secret across dev/staging/prod.
4. Rotate the Keycloak client secret in the realm and update all consumers.

---

### CRITICAL — A05 — Management Endpoints (Actuator + Swagger) Fully Public in Engine Dev Config

**File:** `config/env/.env.engine:99`

**Description:**
The local engine environment file sets `EXPOSE_MANAGEMENT_ENDPOINTS=true`. The engine `SecurityConfig.java:63-68` responds to this flag by calling `permitAll()` on `/actuator/**`, `/v3/api-docs/**`, and `/swagger-ui/**`. This exposes Flowable engine metrics, JVM health details, thread dumps, and full OpenAPI documentation to unauthenticated callers. The engine `application.yml:163` also defaults `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` to `always`, meaning `/actuator/health` returns DB connection state, pool sizes, and mail configuration without authentication.

**Evidence:**
```
config/env/.env.engine:99 → EXPOSE_MANAGEMENT_ENDPOINTS=true
services/engine/src/main/java/com/werkflow/engine/config/SecurityConfig.java:63-68 → permitAll() on actuator/**
services/engine/src/main/resources/application.yml:163 → show-details: ${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:always}
```

**Fix:**
1. Ensure `EXPOSE_MANAGEMENT_ENDPOINTS=false` in all non-developer environments. The `.env.engine` file must not be committed with `true`, or at minimum document that this file is developer-local and is in `.gitignore`.
2. Change the `show-details` default from `always` to `when-authorized` in `application.yml`.
3. Restrict the metrics endpoint to only expose what Prometheus needs, under a separate Prometheus scrape port if possible.

---

### CRITICAL — A07 — Weak Keycloak Password Policy (Length Only, No Complexity)

**File:** `infrastructure/keycloak/realms/werkflow-realm.json:8`

**Description:**
The realm password policy is set to `length(8)` only — no uppercase, lowercase, digit, or special character requirements. Combined with the absence of `bruteForceProtected: true` in the realm JSON (not found in any grep), the platform has no credential stuffing protection at the authentication layer.

**Evidence:**
```json
"passwordPolicy": "length(8)"
```
No `bruteForceProtected` key was found anywhere in `werkflow-realm.json`.

**Fix:**
1. Strengthen password policy: `length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1)`.
2. Enable brute force protection by adding to the realm JSON:
   ```json
   "bruteForceProtected": true,
   "failureFactor": 5,
   "waitIncrementSeconds": 60,
   "maxFailureWaitSeconds": 900
   ```
3. Set `temporaryLockoutDuration` appropriate to the deployment context.

---

### CRITICAL — A07 — `directAccessGrantsEnabled: true` on Portal Client Bypasses OAuth2 Flow

**File:** `infrastructure/keycloak/realms/werkflow-realm.json:26`

**Description:**
The `werkflow-portal` Keycloak client has `directAccessGrantsEnabled: true`. This enables the Resource Owner Password Credentials (ROPC) grant, which allows any caller with the client secret and a user's password to obtain tokens without user interaction, bypassing MFA and SSO flows. This is explicitly deprecated in OAuth 2.1 and creates a path for credential stuffing directly against the token endpoint.

**Evidence:**
```json
{
  "clientId": "werkflow-portal",
  "directAccessGrantsEnabled": true,
  ...
}
```

**Fix:**
Set `directAccessGrantsEnabled: false` on the `werkflow-portal` client. The portal uses Authorization Code flow exclusively. If CI/CD needs direct authentication, create a dedicated service account client with ROPC enabled and scope it tightly.

---

### HIGH — A02 — Default Fallback Credentials in Application YAML

**File:** `services/admin/src/main/resources/application.yml:46`

**Description:**
`ADMIN_CLIENT_SECRET` has a hardcoded fallback default value `REDACTED_KC_SECRET` in `application.yml`. If the environment variable is not set at startup, Spring Boot silently uses this fallback. A misconfigured deployment would use the known-insecure credential without any error or warning.

**Evidence:**
```yaml
client-secret: ${ADMIN_CLIENT_SECRET:REDACTED_KC_SECRET}
```

**Fix:**
Remove the default fallback. Use `${ADMIN_CLIENT_SECRET}` without a default so that Spring Boot fails to start if the variable is missing. Add a startup validation bean that asserts critical secrets are non-empty:
```java
@Value("${spring.security.oauth2.client.registration.werkflow-admin.client-secret}")
private String adminClientSecret;

@PostConstruct
void validate() {
    if (adminClientSecret == null || adminClientSecret.isBlank()
            || adminClientSecret.contains("do-not-use")) {
        throw new IllegalStateException("ADMIN_CLIENT_SECRET is not configured for this environment");
    }
}
```

---

### HIGH — A05 — CSRF Protection Disabled on Both Services

**File:** `services/admin/src/main/java/com/werkflow/admin/config/SecurityConfig.java:53`
`services/engine/src/main/java/com/werkflow/engine/config/SecurityConfig.java:59`

**Description:**
Both Spring Boot services disable CSRF via `csrf.disable()`. For a pure stateless JWT/REST API this is acceptable in isolation, but the portal rewrites `/api/engine/:path*` and `/api/admin/:path*` through Next.js server-side routes (`next.config.mjs:42-50`). If a browser-accessible endpoint accepts state-changing requests and a cookie-based session ever exists in the call path, CSRF becomes exploitable. The current architecture is mostly safe, but this is a defence-in-depth gap.

**Evidence:**
```java
.csrf(csrf -> csrf.disable())
```
Both `SecurityConfig.java` files, lines 53 and 59 respectively.

**Fix:**
For pure JWT bearer token APIs with no cookie-based auth on the backend, `csrf.disable()` is acceptable. However, document this explicitly and add the Spring Security CSRF check back if the auth model ever changes. As a minimum: add a comment explaining why CSRF is disabled and what conditions would require re-enabling it.

---

### HIGH — A09 — Sensitive Exception Detail Leaked in Error Response Body

**File:** `services/engine/src/main/java/com/werkflow/engine/exception/GlobalExceptionHandler.java:278-290`

**Description:**
The `handleFlowableException` handler extracts the root cause message chain and returns the raw `message` to the API caller. Flowable exceptions can contain database table names, SQL fragments, BPMN delegate class names, and internal path information. The `handleIllegalArgumentException` handler similarly forwards `ex.getMessage()` directly to the 400 response body. An attacker who can trigger a Flowable exception (e.g., by submitting malformed BPMN or exploiting process variable rules) receives internal diagnostic information.

**Evidence:**
```java
// GlobalExceptionHandler.java:276-290
Throwable root = ex;
while (root.getCause() != null) root = root.getCause();
String message = root.getMessage() != null ? root.getMessage() : ex.getMessage();
// ...
.message(message)  // raw exception message returned to caller
```

**Fix:**
Map exception types to safe client-facing messages. Log the full detail server-side. Example:
```java
log.error("Flowable engine error [processKey={}]: {}", extractKey(ex), message, ex);
return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Process execution failed — see process audit log for details");
```
Apply the same pattern to `IllegalArgumentException` — return `"Invalid request parameters"` generically, and log the actual message.

---

### HIGH — A05 — Actuator Health Details Default to `always` in Engine

**File:** `services/engine/src/main/resources/application.yml:163`

**Description:**
The engine `application.yml` defaults `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` to `always`. This means the `/actuator/health` endpoint returns database connection strings, HikariCP pool state, mail server configuration, and Flowable engine status to any caller — including unauthenticated ones when `EXPOSE_MANAGEMENT_ENDPOINTS=true`. This is information useful for reconnaissance.

**Evidence:**
```yaml
show-details: ${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:always}
```

**Fix:**
Change the default to `when-authorized`. In production, restrict health detail access to monitoring service accounts:
```yaml
show-details: ${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:when-authorized}
```

---

### HIGH — A04 — No Rate Limiting on BPMN/DMN Deployment Endpoints

**File:** `services/engine/src/main/java/com/werkflow/engine/controller/ProcessDefinitionController.java:43-85`

**Description:**
The BPMN deploy endpoint (`POST /api/process-definitions/deploy`) and bundle deploy endpoint accept arbitrary BPMN XML strings without rate limiting. A legitimate user with `WORKFLOW:DEPLOY` permission can submit thousands of deployments in rapid succession, exhausting the Flowable engine's deployment table and connection pool. The datasource test endpoint correctly applies a rate limiter, but deployment does not.

**Evidence:**
No `@RateLimiter` annotation on `deployProcessDefinition` or `deployBundle`. The admin-service `application.yml:95-101` configures Resilience4j only for `datasource-test`.

**Fix:**
Add Resilience4j rate limiting to deploy endpoints. Example:
```java
@RateLimiter(name = "bpmn-deploy")
@PostMapping(value = "/deploy", ...)
public ResponseEntity<...> deployProcessDefinition(...)
```
Add a `bpmn-deploy` limiter configuration: `limitForPeriod: 10`, `limitRefreshPeriod: 60s`.

---

### HIGH — A07 — Next-Auth PKCE Disabled — Exposes Authorization Code Interception Risk

**File:** `frontends/portal/auth.config.ts:46`

**Description:**
PKCE is intentionally disabled with `checks: ["state"]` only. While the code comment explains this is due to a dual-URL Docker issue (browser→localhost:8090, server→keycloak:8080 causing verifier cookie loss), this disables a key defense against authorization code interception attacks. A network attacker who intercepts the authorization code redirect can exchange it for tokens without knowing the original verifier. This is particularly relevant if the callback URL is ever set to a broader wildcard.

**Evidence:**
```typescript
checks: ["state"],
// comment: "Disable PKCE: confidential client (client_secret) provides equivalent security."
```

**Fix:**
Resolve the underlying dual-URL problem by using a consistent issuer URL in all contexts. The Keycloak `KC_HOSTNAME` + `KC_HOSTNAME_STRICT_BACKCHANNEL=true` approach should allow the server to use the internal URL for token exchange while keeping the public URL for browser redirects. Once resolved, re-enable PKCE: `checks: ["pkce", "state"]`.

---

### HIGH — A06 — Spring Boot 3.3.2 and Keycloak 24.0.4 Outdated

**File:** `services/admin/pom.xml:23`, `services/engine/pom.xml:24`

**Description:**
Both services use Spring Boot 3.3.2 (released July 2024) and Keycloak 24.0.4 (released April 2024). Spring Boot 3.3.x has reached multiple patch versions since 3.3.2, and several CVEs affect Spring Framework and Tomcat in older 3.3.x versions. Keycloak 24.0.4 is significantly behind the current release line; multiple authentication and authorization CVEs have been published against Keycloak 24.x. The OWASP dependency check profile exists but uses `failBuildOnCVSS: 11`, which never fails (the maximum CVSS score is 10.0).

**Evidence:**
```xml
<spring-boot.version>3.3.2</spring-boot.version>
<keycloak.version>24.0.4</keycloak.version>
<failBuildOnCVSS>11</failBuildOnCVSS>  <!-- never triggers -->
```

**Fix:**
1. Upgrade Spring Boot to 3.3.x latest (or 3.4.x if tested).
2. Upgrade Keycloak client libraries to 25.x or later.
3. Lower `failBuildOnCVSS` to `7` so that high-severity CVEs block the CI build.
4. Run `mvn dependency-check:check -Psecurity-check` and triage all high/critical findings before release.

---

### MEDIUM — A02 — Stripe Webhook Timestamp Not Validated (Replay Attack)

**File:** `services/engine/src/main/java/com/werkflow/engine/webhook/HmacVerifier.java:70-91`

**Description:**
The Stripe HMAC verification extracts the `t=<timestamp>` value and uses it in the payload construction for HMAC-SHA256 verification, which is correct. However, the timestamp value itself is never checked against the current time. Stripe's recommended implementation rejects webhooks where `|now - t| > 300` seconds. Without this check, a valid Stripe signature can be replayed indefinitely as long as the idempotency key is not already in the `WebhookUndelivered` table or in the replay protection store.

**Evidence:**
```java
// HmacVerifier.java:77-91
String timestamp = null;
String v1 = null;
for (String part : sig.split(",")) {
    if (part.startsWith("t=")) timestamp = part.substring(2);
    if (part.startsWith("v1=")) v1 = part.substring(3);
}
// timestamp is used in payload construction but never checked against current time
byte[] payload = (timestamp + "." + new String(rawBody, ...)).getBytes(...);
```

**Fix:**
After extracting `timestamp`, validate it:
```java
long ts = Long.parseLong(timestamp);
long nowSeconds = Instant.now().getEpochSecond();
if (Math.abs(nowSeconds - ts) > 300) {
    log.warn("HmacVerifier[stripe]: timestamp too old or too new: {}", timestamp);
    return false;
}
```

---

### MEDIUM — A04 — No Rate Limiting on API Endpoints Beyond Datasource Test

**File:** `services/admin/src/main/java/com/werkflow/admin/config/SecurityConfig.java` (all controllers)

**Description:**
Only the datasource test endpoint applies a rate limiter. All other API endpoints — including Keycloak user creation (`POST /api/keycloak/users`), connector creation, and role management — have no rate limiting. An attacker with a valid admin JWT could enumerate or exhaust resources.

**Evidence:**
Only one `@RateLimiter` annotation found across the entire codebase:
```
services/admin/src/main/java/com/werkflow/admin/controller/DatasourceController.java:112
```

**Fix:**
Apply Resilience4j rate limiters to at minimum:
- `POST /api/keycloak/users` — user provisioning
- `POST /api/v1/credentials` — credential creation
- `POST /api/process-definitions/deploy` — BPMN deployment
Consider adding a global rate limiter at the Nginx/reverse proxy layer for production.

---

### MEDIUM — A09 — Authentication Failures Not Explicitly Logged at Service Layer

**File:** `services/admin/src/main/java/com/werkflow/admin/config/SecurityConfig.java`, `services/engine/src/main/java/com/werkflow/engine/config/SecurityConfig.java`

**Description:**
Neither service registers a custom `AuthenticationEventPublisher` or `AuthenticationFailureBadCredentialsEvent` listener. Failed JWT validation (expired tokens, wrong issuer, tampered signatures) is handled silently by Spring Security's default handling and logged at DEBUG level, not at WARN or ERROR. A SIEM would not receive structured security events for credential attacks.

**Evidence:**
No `AuthenticationEventPublisher` bean found in either `SecurityConfig.java`. No `@EventListener(AuthenticationFailureBadCredentialsEvent.class)` handler found in the codebase.

**Fix:**
Add a Spring Security authentication event listener:
```java
@Component
@Slf4j
public class SecurityAuditListener {
    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("SECURITY_AUTH_FAILURE type={} principal={}",
            event.getException().getClass().getSimpleName(),
            event.getAuthentication().getName());
    }
}
```

---

### MEDIUM — A05 — Content Security Policy Header Missing from Portal

**File:** `frontends/portal/next.config.mjs:21-40`

**Description:**
`next.config.mjs` sets `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, and `Strict-Transport-Security`, but does not set a `Content-Security-Policy` (CSP) header. The portal renders BPMN XML diagrams, DMN tables, and form-js content. Without CSP, a stored XSS vulnerability (e.g., in a process name or form label) would execute in the full origin context with no browser-level containment.

**Evidence:**
```javascript
// next.config.mjs — headers set:
'X-Frame-Options', 'X-Content-Type-Options', 'Referrer-Policy',
'Permissions-Policy', 'Strict-Transport-Security'
// Missing: Content-Security-Policy
```

**Fix:**
Add a CSP header. Start in report-only mode to identify violations, then enforce:
```javascript
{
  key: 'Content-Security-Policy',
  value: "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' http://localhost:8081 http://localhost:8083 http://localhost:8090; frame-ancestors 'none'"
}
```
Note: `bpmn-js` and `dmn-js` require `unsafe-eval` for their rendering pipeline. This is a known trade-off for these libraries.

---

### MEDIUM — A09 — Demo Seed Users Have Non-Temporary Passwords in Realm JSON

**File:** `infrastructure/keycloak/realms/werkflow-realm.json:187`

**Description:**
All seed users (`admin`, `demo.admin`, `demo.employee`, `demo.manager`, etc.) have `"temporary": false` on their credential entries. This means Keycloak will never prompt for a password change on first login. If demo data is ever imported into a production or staging realm, these known passwords (`REDACTED_PASSWORD`, `Demo1234!`) become permanent credentials.

**Evidence:**
```json
"credentials": [{ "type": "password", "value": "REDACTED_PASSWORD", "temporary": false }]
```
Found across all 11 seed users.

**Fix:**
Set `"temporary": true` on all seed user credentials. This forces a password change on first login and prevents known passwords from persisting. Alternatively, remove seed users entirely from the realm JSON and provision them through a post-deploy script that sets random passwords.

---

### MEDIUM — A05 — `FLOWABLE_REST_API_ENABLED=true` in Dev Env File

**File:** `config/env/.env.engine:39`

**Description:**
The dev environment file enables the Flowable native REST API (`FLOWABLE_REST_API_ENABLED=true`). The engine `application.yml:121` comments that this is disabled by default to prevent "parallel auth bypass." Flowable's built-in REST API uses Basic Authentication (not JWT), creating a parallel, potentially weaker auth path. The `.env.engine` file is excluded from git by `.gitignore`, but this represents a dangerous default for any developer who uses this file without reviewing it.

**Evidence:**
```
config/env/.env.engine:39 → FLOWABLE_REST_API_ENABLED=true
services/engine/src/main/resources/application.yml:121 → # REST API — LOW-06: disabled by default
```

**Fix:**
Remove `FLOWABLE_REST_API_ENABLED=true` from `.env.engine`. If developers need to inspect Flowable internals, they should enable it explicitly with awareness. Add a comment to the example file warning about the auth bypass risk.

---

### LOW — A07 — Access Token Lifespan of 5 Minutes with 10-Hour SSO Max

**File:** `infrastructure/keycloak/realms/werkflow-realm.json:5,7`

**Description:**
The access token lifespan is 300 seconds (5 minutes), which is reasonable. However, `ssoSessionMaxLifespan` is 36,000 seconds (10 hours). A stolen refresh token remains valid for up to 10 hours. There is no refresh token rotation or reuse detection configured in the realm JSON.

**Evidence:**
```json
"accessTokenLifespan": 300,
"ssoSessionMaxLifespan": 36000
```

**Fix:**
1. Set `ssoSessionIdleTimeout` to a lower value (e.g., 3,600 seconds/1 hour) so inactive sessions expire.
2. Enable refresh token rotation in the portal client:
   ```json
   "attributes": { "use.refresh.tokens": "true", "refresh.token.max.reuse": "0" }
   ```
   Setting `refresh.token.max.reuse: 0` enforces single-use refresh tokens with automatic rotation.

---

### LOW — A02 — OpenBao Running in Dev Mode (In-Memory, No Seal)

**File:** `infrastructure/docker/docker-compose.yml:157-176`

**Description:**
The OpenBao container is started with `server -dev`, which runs an unsealed, in-memory instance. All secrets are lost on container restart, and the root token is fixed (`werkflow-dev-root-token`). The comment in the compose file acknowledges this is insecure by design for development. The risk is that a developer who deploys this compose file to a shared environment (e.g., a staging server) exposes a fully accessible secrets backend with a known root token.

**Evidence:**
```yaml
command: server -dev
environment:
  BAO_DEV_ROOT_TOKEN_ID: werkflow-dev-root-token
```

**Fix:**
Add a clear runtime guard: if `BAO_DEV_ROOT_TOKEN_ID` is set, print a loud startup warning and refuse to start if `APP_ENVIRONMENT != development`. This can be implemented as a shell script wrapper or a Spring Boot `@PostConstruct` check that reads the environment.

---

### LOW — A06 — OWASP Dependency Check Never Fails (CVSS Threshold 11)

**File:** `services/admin/pom.xml:272`, `services/engine/pom.xml:378`

**Description:**
The `security-check` Maven profile runs `dependency-check-maven` but configures `failBuildOnCVSS: 11`. Since the maximum CVSS score is 10.0, this setting means the build never fails regardless of how many high or critical CVEs are found. The scan generates reports but provides no enforcement.

**Evidence:**
```xml
<failBuildOnCVSS>11</failBuildOnCVSS>
```

**Fix:**
Set `failBuildOnCVSS: 7` to fail on HIGH and CRITICAL findings. Suppress known false positives with entries in the `owasp-suppressions.xml` file. Add the `security-check` profile to the CI pipeline gate.

---

### LOW — A09 — Production Console.log Statements in Frontend

**File:** `frontends/portal/app/(platform)/forms/formjs-demo/page.tsx:77,91`

**Description:**
Two `console.log` statements exist in the form demo page that print form submission data and schema to the browser console without any `NODE_ENV` guard. Form submission data can contain sensitive process variables (amounts, employee data, approval decisions). These statements would be visible to any user who opens browser devtools.

**Evidence:**
```typescript
console.log('Form submitted:', data);   // line 77
console.log('Saving schema:', schema);  // line 91
```

**Fix:**
Remove both `console.log` statements. Use a structured logger utility that suppresses output in production (`if (process.env.NODE_ENV === 'development') { ... }`), or remove entirely if the demo page is not production-facing.

---

### INFO — A08 — BPMN/DMN XML XXE Hardening Applied Inconsistently

**File:** `services/engine/src/main/java/com/werkflow/engine/workflow/BpmnBundleRefExtractor.java:109-116`
`services/admin/src/main/java/com/werkflow/admin/designtime/bpmn/service/ProcessVariableScopeService.java:109-116`
`services/admin/src/main/java/com/werkflow/admin/designtime/dmn/controller/DmnFacadeController.java:141-147`

**Description:**
Three separate `DocumentBuilderFactory` instantiation sites all correctly apply XXE hardening (`disallow-doctype-decl`, `external-general-entities: false`, `external-parameter-entities: false`). This is a positive finding. However, the Flowable engine's own XML parsing (used internally during BPMN deploy) is not explicitly audited here — it is assumed to use Flowable's own secured parser. This should be confirmed against Flowable 7.2 release notes.

**Evidence:**
```java
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
```
All three custom XML parse sites are correctly hardened.

**Fix/Action:**
Verify that Flowable 7.2's `XmlUtil` or `BpmnXMLConverter` applies equivalent XXE hardening. If not confirmed in Flowable release notes, open a bug with the Flowable team or wrap the deploy endpoint in a pre-validation step using the already-hardened `BpmnBundleRefExtractor`.

---

### INFO — A10 — SSRF Guard Has Incomplete DNS Rebinding Protection

**File:** `shared/common/src/main/java/com/werkflow/common/security/SsrfGuard.java:28-33`

**Description:**
The `SsrfGuard.validate()` method resolves the hostname at validation time and checks the IP against denied ranges. However, DNS rebinding attacks can subvert this check: a domain that resolves to a public IP at validation time may later rebind to a private IP when the actual HTTP connection is made. The `RestConnectorDelegate` constructs the `HttpClient` at startup (not per-request), so there is no repeated DNS resolution gap. However, the guard comment on line 50 explicitly acknowledges this gap.

**Evidence:**
```java
// SsrfGuard.java:50 (javadoc comment)
// Note: DNS rebinding is not mitigated here
```

**Fix:**
This is a known limitation, documented in the code. The current mitigation (admin-controlled URLs, no redirects, `HttpClient.Redirect.NEVER`) reduces the practical risk. A full fix would require IP pinning at the connection layer (Java's `HttpClient` does not natively support this). Accept the current risk with documentation, or add Nginx-level egress filtering to block private IP ranges for outbound connector traffic.

---

### INFO — A07 — JWT `aud` Claim Not Validated

**File:** `services/engine/src/main/java/com/werkflow/engine/config/JwtDecoderConfig.java:60-90`
`services/admin/src/main/java/com/werkflow/admin/config/SecurityConfig.java:109-113`

**Description:**
Both services validate JWT `iss` (issuer) and `exp` (expiry) but do not validate the `aud` (audience) claim. The engine's `JwtDecoderConfig` creates a `DelegatingOAuth2TokenValidator` with only `JwtIssuerValidator` and `JwtTimestampValidator`. Spring's `NimbusJwtDecoder` does not enforce `aud` by default unless `JwtClaimValidator` is added. A token issued for a different Keycloak client (e.g., a third-party application in the same realm) could be presented to Werkflow's APIs if the issuer matches.

**Evidence:**
```java
OAuth2TokenValidator<Jwt> combinedValidator = new DelegatingOAuth2TokenValidator<>(
    issuerValidator,     // iss checked
    timestampValidator   // exp/nbf checked
    // NO audience validator
);
```

**Fix:**
Add audience validation. Keycloak includes the `aud` claim. Configure the expected audience:
```java
OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
    "aud",
    aud -> aud instanceof List && ((List<?>) aud).contains("werkflow-engine")
);
```
Apply equivalent validation in the admin service. This prevents token reuse across clients.

---

## Safe / Verified Items

The following checks were performed and passed, or controls were confirmed in place:

**A02 — Cryptographic Failures:**
- No MD5, SHA-1, or weak cipher algorithms found in production Java source (only SHA-256 for API key hashing).
- HMAC-SHA256 is used for webhook signature verification with constant-time comparison (`MessageDigest.isEqual`).
- OpenBao is used for all connector credential storage — no plaintext secrets stored in the database.
- Database passwords are injected via environment variables with no fallback in `engine/application.yml`.
- TLS is enforced by `SsrfGuard.validate()` for direct-URL connector calls (HTTPS only).

**A03 — Injection:**
- All `JdbcTemplate` and `NamedParameterJdbcTemplate` usages use parameterized queries. No string concatenation in SQL found.
- `ProcessVisibilityProjector` uses `?` placeholder correctly.
- Flowable EL injection is mitigated by `RestrictedExpressionManager` + `SecurityELResolver` which blocks method invocation and POJO reflection. Length/depth/function-call limits are enforced at parse time.
- BPMN/DMN XML upload uses `DocumentBuilderFactory` with full XXE hardening at all three custom parse sites.
- No `dangerouslySetInnerHTML` found in frontend components.

**A03 — Log Injection:**
- Log statements use SLF4J parameterized format (`{}`) throughout, preventing log injection via format string exploitation.
- JWT claim content (roles, realm access) is only logged at DEBUG or conditionally under `NODE_ENV === development`.

**A04 — Insecure Design:**
- Database connector queries enforce read-only mode, row caps (10,000 hard cap), and query timeouts at the JDBC layer.
- DML keyword scanner (`NamedQueryExecutor.rejectDml`) strips SQL comments before scanning to prevent comment-embedded bypass.

**A05 — Security Misconfiguration:**
- CORS is configured with an explicit allowlist (not wildcard `*`) in both services. `allowedOrigins` is driven by configuration.
- Error responses in both `GlobalExceptionHandler` implementations return generic messages for 500 errors — no stack traces exposed.
- Both `application.yml` files set `error.include-stacktrace: never` and `error.include-message: never`.
- Admin service `SecurityConfig` correctly adds `JwtValidators.createDefaultWithIssuer()` (line 112).
- Actuator health endpoint is always public, but `show-details` defaults to `when-authorized` in the admin service.

**A07 — Identification and Authentication Failures:**
- JWT signature validation uses JWK public keys fetched from Keycloak's JWKS endpoint — no symmetric key vulnerability.
- Sessions are stateless (`SessionCreationPolicy.STATELESS`) in both backend services.
- Next-Auth session strategy is `jwt` (not database), reducing session fixation surface.
- Refresh token flow in `auth.config.ts` handles `RefreshAccessTokenError` and forces re-authentication.

**A08 — Software and Data Integrity:**
- No Java `ObjectInputStream` deserialization of untrusted data found in production code.
- No Jackson `enableDefaultTyping()` or polymorphic deserialization found.
- OpenAPI/Swagger import uses `swagger-parser` library, not custom XML parsing.

**A09 — Security Logging:**
- VaultCredentialStore logs write operations at INFO but never logs secret values — only path and field names (keys, not values).
- Process audit log is implemented via `ProcessAuditLogRepository`.
- Access denied events are caught and logged in both `GlobalExceptionHandler` implementations.

**A10 — SSRF:**
- `RestConnectorDelegate` applies `SsrfGuard` after full URL resolution (post-EL evaluation), preventing expression-crafted bypass.
- `HttpClient.Redirect.NEVER` is set to prevent redirect-based SSRF.
- Connector-mode URLs go through `validateExternal()` (HTTP permitted, private IPs blocked except loopback/link-local). Direct-URL mode requires HTTPS and blocks all private ranges.

**Supplementary — Mass Assignment:**
- No evidence of `@RequestBody` mapping directly to JPA entities found. All controllers use DTO types (`OrganizationRequest`, `ConnectorRequest`, etc.).

**Supplementary — File Upload:**
- No multipart file upload endpoints found. BPMN/DMN XML is submitted as `application/json` body strings, size-bounded by the configured `BPMN_MAX_FILE_SIZE`.

**Supplementary — Input Validation:**
- `@Valid` annotation is used consistently on `@RequestBody` DTOs in admin controllers.
- Bean Validation (`spring-boot-starter-validation`) is present in both pom.xml files.
- `MethodArgumentNotValidException` is handled by both `GlobalExceptionHandler` implementations with field-level error detail.

---

## Finding Index by OWASP Category

| Category | Findings |
|----------|----------|
| A02 — Cryptographic Failures | CRITICAL (Google secret), CRITICAL (NEXTAUTH + KC secret), HIGH (default fallback creds), LOW (OpenBao dev mode), INFO (aud not validated) |
| A04 — Insecure Design | HIGH (no rate limit on deploy), MEDIUM (no rate limit on other endpoints) |
| A05 — Security Misconfiguration | CRITICAL (actuator public), HIGH (CSRF disabled), HIGH (health details always), MEDIUM (CSP missing), MEDIUM (Flowable REST enabled) |
| A06 — Vulnerable Components | HIGH (Spring Boot 3.3.2 + KC 24.0.4), LOW (CVSS threshold 11) |
| A07 — Authentication Failures | CRITICAL (weak password policy), CRITICAL (ROPC enabled), HIGH (PKCE disabled), LOW (SSO session max / no refresh rotation), INFO (aud not validated) |
| A08 — Data Integrity | INFO (Flowable XXE — confirm engine parser) |
| A09 — Security Logging | HIGH (exception detail in response), MEDIUM (auth failures not logged), LOW (console.log in frontend) |
| A10 — SSRF | INFO (DNS rebinding not mitigated, documented) |

---

*Audit conducted 2026-06-07 by reading source files directly. No dynamic testing was performed. All file references are absolute paths within `werkflow-enterprise/`. Severity ratings follow OWASP definitions: CRITICAL = exploitable in production with direct breach/RCE impact; HIGH = serious, exploitable under reasonable conditions; MEDIUM = needs fixing before public release; LOW = defence-in-depth; INFO = observation.*
