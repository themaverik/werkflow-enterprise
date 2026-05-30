# Compliance Checklist — Werkflow Enterprise Portal

**Completed:** 2026-05-30  
**Scope:** Portal (Next.js) + Engine (Spring Boot) + Admin Service (Spring Boot)  
**Jurisdiction:** GDPR (EU/EEA) + CCPA (California)

Legend: ✅ Pass · ⚠️ Known gap / deferred · ❌ Fail

---

## Legal Pages

| Item | Status | Notes |
|------|--------|-------|
| Privacy Policy published at `/legal/privacy` | ✅ | GDPR Art. 13/14 notice; CCPA §1798.100 rights; legal-basis table; sub-processor list |
| Terms of Use published at `/legal/terms` | ✅ | Acceptable-use, IP, limitation-of-liability, governing law |
| Cookie settings page at `/legal/cookies` | ✅ | Per-category toggles; bulk accept/reject; CCPA "do not sell" statement |
| Login page links updated to real legal URLs | ✅ | Previously pointed to `#` |

---

## Cookie Consent

| Item | Status | Notes |
|------|--------|-------|
| Consent banner shown on first visit | ✅ | `CookieConsentBanner` in root layout; disappears after choice |
| Consent persisted in localStorage | ✅ | Key `werkflow_consent`; schema versioned (`version: "1.0"`) |
| Essential cookies always accepted | ✅ | `essential: true` hardcoded; cannot be toggled off |
| Non-essential categories gated on consent | ✅ | Analytics, Preferences, Marketing — defaulting false until accepted |
| Analytics / preferences / marketing toggles | ✅ | Per-category toggle on Cookie Settings page |
| CCPA "Do not sell" statement included | ✅ | On Cookie Settings page + Privacy Policy §7 |
| No third-party tracking scripts | ✅ | Confirmed — portal uses only internal engine API calls; no GA/Mixpanel |

---

## GDPR Posture

| Item | Status | Notes |
|------|--------|-------|
| Data subject rights documented | ✅ | Access, rectification, erasure, portability, objection — Privacy Policy §6 |
| CCPA rights documented | ✅ | Privacy Policy §7 |
| Lawful basis documented per processing activity | ✅ | Privacy Policy §3 table |
| Retention periods documented | ✅ | Privacy Policy §4 |
| Sub-processor list published | ✅ | Privacy Policy §5 — Keycloak, OpenBao, cloud infra (TBC) |
| DPA / SCCs for non-EU transfers | ⚠️ | Cloud provider not yet selected; clause included by reference |
| Right-of-erasure endpoint (programmatic) | ⚠️ | Currently requires admin action via Keycloak + DB; automated erasure API deferred |
| Data portability endpoint | ⚠️ | Deferred — manual export available via admin queries |

---

## Security Headers

| Header | Status | Value |
|--------|--------|-------|
| `X-Frame-Options` | ✅ | `DENY` |
| `X-Content-Type-Options` | ✅ | `nosniff` |
| `Referrer-Policy` | ✅ | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | ✅ | camera, microphone, geolocation, payment all `()` |
| `Strict-Transport-Security` | ✅ | `max-age=63072000; includeSubDomains; preload` (apply after HTTPS confirmed) |
| `Content-Security-Policy` | ⚠️ | Not yet set — deferred; bpmn-js and form-js editors require `unsafe-eval` and careful img-src tuning; add in a dedicated CSP session |

---

## Authentication & Authorisation

| Item | Status | Notes |
|------|--------|-------|
| Authentication via Keycloak (OIDC) | ✅ | next-auth v5 beta; PKCE disabled (confidential client, dual-URL Docker setup) |
| JWT validated on every engine/admin request | ✅ | Spring Security validates against Keycloak JWKS on every request |
| Tenant isolation in engine queries | ✅ | All Flowable queries scoped by `tenantId` |
| Role-based access control | ✅ | `TENANT_ADMIN`, `PROCESS_USER`, `SUPER_ADMIN` roles enforced |
| Protected routes require valid session | ✅ | `ProtectedRoute` component; next-auth session checks |
| Script-task (Groovy) quarantined | ✅ | ADR-016 — script tasks reject at deploy time (Phase 1 quarantine) |

---

## Secret Management

| Item | Status | Notes |
|------|--------|-------|
| No hardcoded secrets in source code | ✅ | Verified — API keys/passwords use env vars or OpenBao |
| Secrets stored in OpenBao (Vault) | ✅ | All connector credentials and datasource passwords in OpenBao |
| `EncryptionService` (AES in-DB) removed | ✅ | Deleted in B.6; last AES path closed |
| `.env.example` documents required vars | ✅ | Checked — no real values |
| `AUTH_SECRET` set for next-auth | ✅ | Required for v5 session signing |

---

## Dependency CVEs (`npm audit`)

Assessed 2026-05-30. Run from `frontends/portal/`.

| Severity | Count (before fix) | Count (after `npm audit fix`) | Action |
|----------|--------------------|-------------------------------|--------|
| Critical | 1 (axios) | 0 (axios → 1.16.1) | ✅ Fixed |
| High | 7 (axios, flatted) | 0 | ✅ Fixed |
| Moderate | 9 | 6 | ⚠️ See below |

**Remaining after `npm audit fix` (require breaking upgrades):**

| Package | Severity | CVE summary | Fix | Blocker? |
|---------|----------|-------------|-----|----------|
| `next` 14.2.15 | Critical | Multiple DoS, cache poisoning, middleware bypass | Upgrade to next@16.x (breaking) | ⚠️ Deferred — major version upgrade; needs dedicated session |
| `next-intl` ≤4.9.1 | Moderate | Open redirect, prototype pollution | Upgrade to next-intl@4.x (breaking) | ⚠️ Deferred |
| `glob` 10.2.0–10.4.5 | High (dev) | CLI command injection | Upgrade via eslint-config-next@16 (breaking) | Dev-only; low risk in prod |
| `esbuild` ≤0.24.2 | Moderate (dev) | Dev server requests exposure | vitest@4 upgrade (breaking) | Dev-only; zero prod impact |

**Next steps:** Create a dedicated `chore(deps): next@16 + next-intl@4 upgrade` task. The Next.js CVEs (cache poisoning, middleware bypass) are the priority — most relevant for production deployment.

---

## Input Validation & Error Handling

| Item | Status | Notes |
|------|--------|-------|
| User input validated with Zod on forms | ✅ | Portal form submissions use Zod schemas |
| Backend validates at system boundaries | ✅ | Spring `@Valid` + custom validators on all API endpoints |
| Error responses don't leak stack traces | ✅ | Spring Boot `server.error.include-stacktrace=never` in prod profile |
| SQL injection prevention | ✅ | All queries via JPA/Flowable repositories (parameterised) |
| XSS prevention | ✅ | DOMPurify used in form-js renderer; React escapes output by default |
| SSRF mitigation | ✅ | `SsrfGuard` on all outbound connector calls |

---

## Rate Limiting

| Item | Status | Notes |
|------|--------|-------|
| Admin service API rate limiting | ⚠️ | Not implemented — relies on infrastructure-level rate limiting (nginx/ALB). Flag for prod deploy checklist (item 12). |
| Portal Next.js API routes | ⚠️ | No rate limiting on proxy routes — relies on upstream services. Acceptable for internal demo; add for public-facing prod. |

---

## Open Items (deferred, prioritised)

| Priority | Item |
|----------|------|
| HIGH | Next.js 14→16 upgrade (fixes critical CVEs: middleware bypass, cache poisoning) |
| HIGH | CSP header implementation (requires `unsafe-eval` budget analysis for bpmn-js/form-js) |
| MEDIUM | Automated data erasure API (GDPR Art. 17) |
| MEDIUM | Data portability export endpoint (GDPR Art. 20) |
| MEDIUM | Admin service + portal proxy rate limiting |
| LOW | Confirm cloud infrastructure provider for DPA/SCCs |
| LOW | vitest@4 + esbuild upgrade (dev-only) |
