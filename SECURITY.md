# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest (main) | Yes |

## Reporting a Vulnerability

If you discover a security vulnerability in Werkflow, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

### How to Report

Email: **security@werkflow.io** (or open a [GitHub Security Advisory](https://github.com/themaverik/werkflow/security/advisories/new) if email is unavailable)

Include in your report:
- Description of the vulnerability and its potential impact
- Steps to reproduce or proof-of-concept
- Affected component (engine, admin, portal, infrastructure)
- Any suggested fix, if known

### What to Expect

- Acknowledgement within **48 hours**
- Assessment and severity classification within **7 days**
- A fix or mitigation plan communicated within **30 days** for critical/high severity issues
- Credit in the release notes if you wish to be acknowledged

### Scope

In scope:
- Authentication and authorisation bypass
- Multi-tenant data isolation failures
- SQL injection, XSS, SSRF, XXE
- Secrets or credentials exposed in logs or responses
- Remote code execution via BPMN/CMMN definitions

Out of scope:
- Vulnerabilities in third-party dependencies (report upstream)
- Issues requiring physical access to the host
- Social engineering attacks

## Security Design Notes

- JWT tokens are validated against Keycloak's JWKS endpoint on every request
- Tenant isolation is enforced at the Flowable engine query layer (`tenantId` scoping)
- SSRF is mitigated via `SsrfGuard` on all outbound connector calls
- Secrets in BPMN expressions are resolved via `SecretsResolver` and never logged
- Business-specific guards (`AssetRequestGuard`, `HubManagerGuard`) are disabled by default and require explicit opt-in via `werkflow.business.enabled=true`
