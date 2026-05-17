# Werkflow Roadmap

**Project**: Enterprise Workflow Automation Platform

---

## Execution Order

```
S1  — Critical Fixes                         COMPLETED
S2  — BPMN Wiring                            COMPLETED
S3  — Service Consolidation                  COMPLETED
S4  — Frontend Completion                    COMPLETED
S5  — Integration Testing                    COMPLETED (S5.2 merged into S8.3)
S6  — Playwright E2E Suite                   COMPLETED
S7  — Core Domain Redesign                   COMPLETED
S8  — Group Resolution Redesign              COMPLETED
S9  — Fixes & Improvements                   COMPLETED
S10 — Design Studio Fixes                    COMPLETED
S11 — Action Blocks                          COMPLETED
S12 — Mailpit SMTP + Env Reorganisation      COMPLETED
S13 — Form Key Enhancements                  COMPLETED
S14 — Generic Form Data Sources              COMPLETED
S15 — Permissions Model                      COMPLETED
S16 — Engine Service Fixes                   COMPLETED
S17 — Multi-Tenant Identity & Group Routing  COMPLETED
S18 — API Integration Layer                  COMPLETED
S19 — Business-Agnostic Delegate Layer       COMPLETED
S20 — Business Service Decoupling            COMPLETED
S21 — Code Cleanup & IP Preparation          COMPLETED
S22 — Engine Isolation, Permissions & Portal Cleanup  COMPLETED
S23 — i18n (Internationalisation)            COMPLETED
S24 — Multi-Tenancy Security Hardening       COMPLETED
S25 — Example Workflows & Seed Data          COMPLETED
S26 — CI/CD Pipeline                        COMPLETED
S27 — Community Launch
```

---

## Completed Phases

### S1 — Critical Fixes
- Replaced `WebClient.block()` with `RestClient` in `RestServiceDelegate`
- Upgraded Flowable 7.0.1 → 7.1.0
- Deleted dual legacy package trees

### S2 — BPMN Wiring
- Migrated CapEx approval process to `RestServiceDelegate` with field injection
- Added JWT propagation and finance workflow endpoints

### S3 — Service Consolidation
- Merged HR, Finance, Procurement, Inventory into single `business-service` (port 8084)
- Consolidated two portals into one Next.js app

### S4 — Frontend Completion
- Task detail page, request tracking page, and dashboard wired to backend APIs

### S5 — Integration Testing
- CapEx workflow verified end-to-end inside Docker

### S6 — Playwright E2E Suite
- Full portal E2E coverage: auth, RBAC, process/workflow flows, DOA approval
- GitHub Actions CI integration

### S7 — Core Domain Redesign
- Multi-department org structure and asset request workflows
- `AssetRequest` entity, BPMN custodian-dept routing, portal start form

### S8 — Workflow Group Resolution Redesign
- Replaced Keycloak-group-based routing with DOA-level authority model
- `FlowableGroupResolver`, startup BPMN validator, `doa_threshold` DB table

### S9 — Fixes & Improvements
- README refresh, Keycloak SSO logout fix, sidebar cleanup

### S10 — Design Studio Fixes
- Fixed form save, process deploy, ProcessDraft backend
- Replaced form.io preview with form-js

### S11 — Action Blocks
- Three BPMN action block types: Human Approval, Notification, External API Call
- `EmailActionDelegate`, `ExternalApiCallDelegate`, SSRF protection, audit log

### S12 — Mailpit SMTP & Env Reorganisation
- Mailpit service for local email testing
- Environment files reorganised into `config/env/`

### S13 — Form Key Enhancements
- Form Key on all BPMN event types
- Trigger Process property (frontend + backend)

### S14 — Generic Form Data Sources
- `properties.dataSource` pattern for form-js select components
- Cascade support; asset request migrated to generic start form

### S15 — Permissions Model
- YAML-driven RBAC with `PermissionConfig` and `WerkflowPermissionEvaluator`
- Domain guards: `AssetRequestGuard`, `TaskGuard`, `HubManagerGuard`

### S16 — Engine Service Fixes
- Fixed delegate property key and OAuth2 client credentials for service-to-service calls
- Drafts section on processes page

### S17 — Multi-Tenant Identity, Group Routing & Integration Architecture
- `Tenant`, `TenantServiceEndpoint`, `TenantApiCredential`, `TenantRolePermission` data model
- `FlowableGroupResolver` 5-step pipeline; BPMN candidateGroups migrated to `DEPT:*`/`DOA:*` prefix format
- Admin UI for tenant configuration

### S18 — API Integration Layer
- Connector Registry with SSRF-protected test-call proxy
- Gateway Condition Builder with DOA and custody catalog
- Form DataSource reference panel in BPMN designer

### S19 — Business-Agnostic Delegate Layer
- Single `ExternalApiCallDelegate` replaces all domain delegates
- Tenant-aware URL resolution via `TenantEndpointResolver`
- All BPMN processes migrated to connector mode

### S20 — Business Service Decoupling
- Business-service removed from platform Dockerfile and `docker-compose.yml`
- `docker-compose.business.yml` overlay for optional deployment

---

## Upcoming

### UI Polish — Cross-Editor Toolbar Uniformity (In Progress)

- BPMN, Form, and DMN designer toolbars must share the same dark-header token contract (`--panel-hdr-bg`, `--panel-hdr-border`, `--panel-hdr-text`)
- Form designer toolbar already migrated (2026-05-17, branch `feature/form-designer-polish`)
- BPMN designer toolbar currently uses a shadcn `Card` (white) — pending migration
- Goal: changing a single `--panel-hdr-*` token propagates across all three editors with no per-editor overrides

### S26 — CI/CD Pipeline (Completed 2026-04-21)

- `ci.yml` — builds and tests engine, admin, and portal on every PR and push to main
- `release.yml` — builds and pushes Docker images to `ghcr.io` on `v*` tags; creates GitHub Release with image pull commands
- `e2e.yml` — existing E2E test workflow (Playwright, full portal integration)
- Initial git commit: f28d910 — 773 files, all sprints S1–S28.6
- Tagged v1.0.0

### S28.6 — DMN Decision Table Support (Completed 2026-04-20)

- `flowable.dmn` enabled in application.yml (`DmnDecisionService`, `DmnRepositoryService`, `DmnHistoryService`)
- Three sample DMN files auto-deployed from classpath: `doa_routing`, `leave_approval`, `procurement_matrix`
- Sample BPMN: `doa-routing-process` — Business Rule Task evaluates `doa_routing` DMN; ExclusiveGateway routes by `approverGroup` output
- Backend REST: `GET/POST /api/v1/dmn/decisions`, get XML, redeploy, delete deployment, test endpoint, execution history (paginated)
- Portal: Decisions list, new/edit pages with embedded `dmn-js` editor (dynamic import, SSR-safe), test panel
- BPMN properties panel: `flowable-dmn` group for Business Rule Task (`decisionRef`, `mapDecisionResult`, `resultVariable`)
- Flyway V28: seeds `doa-request-form` (PROCESS_START) and `doa-approval-form` (APPROVAL) schemas
- `dmn-js` ^16.7.1 added to Portal `package.json`

### S27 — Advanced BPMN Modeling (Completed)

Internal Demo Edition — first sprint of the enterprise capability phase.

#### BPMN Enhancements
- `flowable-spring-boot-starter-cmmn` enabled (dep + yaml config; required for S31 Full CMMN)
- Properties panel: signal events — added Flowable `correlationKey` entry in `FlowablePropertiesProvider`; timer configuration and multi-instance loop settings handled by native `BpmnPropertiesProviderModule`
- BPMN expression validation in `ProcessDefinitionService` correctly passes timer, signal, and gateway elements
- New example template: SLA Escalation Process — 48h non-interrupting reminder + 72h interrupting escalation (timer boundary events); Flyway V26
- New example template: Parallel Committee Approval — 3 concurrent reviewer tasks, parallel gateway sync, outcome notification; Flyway V27

> Case management deferred: S27 CMMN Base dropped. S28.7 builds a custom Governed Case Engine (not Flowable CMMN directly). Full Flowable CMMN is S31 Premium only.

---

## Recently Completed

### S25 — Example Workflows & Seed Data
- Engine V22: updated DOA thresholds to L1=$10K, L2=$50K, L3=$200K, L4=unlimited
- Admin V12: seeded 5 departments (Engineering, Finance, HR, Operations, IT) for the default tenant
- Admin V13: seeded `webhook-test` connector (`https://httpbin.org`) for the default tenant
- Engine V23–V25: seeded form schemas for all three sample workflows (start + decision forms)
- Added sample BPMN: `general-approval` — DOA-gated approval with Manager and optional Director step
- Added sample BPMN: `document-review` — reviewer group routing with approve/reject/revise loop
- Added sample BPMN: `onboarding-checklist` — parallel gateway for IT and Facilities tasks
- Added three demo Keycloak users (demo.admin L4, demo.manager L2, demo.employee L1) in Engineering dept
- `docs/First-Workflow-Tutorial.md` — 30-minute tutorial walking through the general-approval workflow
- `docs/Sample-Workflows.md` — purpose, actors, flow, and modeling lessons for all three samples
- README: added Swagger UI link (`http://localhost:8081/swagger-ui.html`)

### S24 — Multi-Tenancy Security Hardening
- `TenantContext` component and `TenantContextFilter` added to engine service; `tenant_code` JWT claim populates ThreadLocal per request
- All process instance, task, and history queries in `ProcessInstanceService`, `TaskService`, `TaskController`, `ProcessMonitoringService` scoped by `tenantId` — prevents cross-tenant data leakage
- Flowable history cleanup enabled: 365-day retention, nightly at 01:00, batch size 100
- Async executor tuned for production: core 4 / max 16 / queue 500 / idle-timeout 5 min
- `MultiTenantIsolationTest` — 6 integration tests verifying zero data leakage across tenant boundaries
- Security audit confirmed: all external URL access goes through `SsrfGuard`; all credential lookups use `SecretsResolver`

### S23 — i18n (Internationalisation)
- `next-intl` v3 integration with without-routing strategy (no URL prefix)
- `messages/en.json` — full English baseline across all namespaces: `nav`, `dashboard`, `tasks`, `requests`, `processes`, `forms`, `services`, `admin.*`, `auth`, `bpmn`, `formBuilder`, `common`
- Server components use `getTranslations`; client components use `useTranslations`
- `NextIntlClientProvider` in root layout; `createNextIntlPlugin` in `next.config.mjs`
- All portal pages and components instrumented: sidebar, user menu, dashboard, tasks, requests, processes, forms, services, all admin pages, BPMN designer, expression builder, service task panel, form builder/viewer, login page, confirm dialog, error display, coming-soon page
- Disabled language switcher stub added to header with `TODO(i18n)` comment
- `docs/TRANSLATION-GUIDE.md` — step-by-step guide for adding a new language
- `CONTRIBUTING.md` — created with Contributing Translations section

### S22 — Engine Isolation, Permissions & Portal Cleanup
- Guarded `AssetRequestGuard` behind `werkflow.business.enabled` flag (`@ConditionalOnProperty`)
- Migrated CapEx BPMN to connector pattern (`capex-service`)
- Moved 4 example BPMNs to `resources/processes/examples/`; created `ProcessExampleDeployer`
- `DomainGuard` interface with auto-discovery registry replacing hardcoded switch in `WerkflowPermissionEvaluator`
- Role hierarchy in YAML (`EMPLOYEE → WORKFLOW_DESIGNER → WORKFLOW_ADMIN → ADMIN → SUPER_ADMIN`); business roles removed from core YAML
- Flowable 7.1.0 → 7.2.0
- Deleted all business route groups from portal; cleaned `client.ts`, `next.config.mjs`, `role-based-nav.tsx`

### S21 — Code Cleanup & IP Preparation
- Added `.github/` scaffolding (issue templates, PR template)
- `CHANGELOG.md` created
- Environment file audit completed
- Flyway migration context comments added (V15–V20)
- Public `ROADMAP.md` cleaned
