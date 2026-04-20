# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest  | Yes       |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues by emailing **security@werkflow.io** with:

- A description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Any suggested mitigations (optional)

You will receive an acknowledgement within **48 hours** and a status update within **7 days**.

## Disclosure Policy

- We follow coordinated disclosure. Please give us reasonable time to investigate and patch before public disclosure.
- Once a fix is released, we will publish a security advisory on GitHub.
- We credit reporters in the advisory unless you prefer to remain anonymous.

## Scope

In scope:
- Authentication and authorisation bypasses
- Remote code execution
- SQL injection or data exposure
- SSRF vulnerabilities
- Secrets or credentials exposed in logs or API responses

Out of scope:
- Denial of service attacks
- Issues in third-party dependencies (please report those upstream)
- Social engineering

## Security Best Practices for Deployers

- Rotate all secrets in `.env.*.example` files before production use
- Never expose the Flowable engine REST API (`8081`) or admin service (`8083`) publicly
- Run Keycloak behind a reverse proxy with TLS
- Enable `WERKFLOW_BUSINESS_ENABLED=false` unless you are deploying the optional business module
