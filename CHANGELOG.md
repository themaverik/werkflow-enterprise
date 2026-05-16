# Changelog

All notable changes to werkflow are documented here.

Format: `[Unreleased]` for in-progress work. Releases follow [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### M4.11 — BPMN/DMN Native Coverage + Panel Decomposition (2026-05-16)

#### Added
- `SET_VARIABLES` action block + `SetVariablesDelegate` (constructor-injected `ExpressionManager`; satisfies `Delegate-Checklist.md`)
- Context-aware action-type dropdown filter per BPMN element type (`ACTION_TYPES_BY_ELEMENT`)
- `DeprecationNoticeEntry` Preact component for `bpmn:receiveTask` (rendered with `role="alert"`)
- Auto-default `flowable:actionType="SEND_NOTIFICATION"` on `bpmn:sendTask` placement
- Shared modules: `lib/bpmn/extension-elements.ts` (low-level read/write helpers), `lib/bpmn/action-block-logic.ts` (`readVarFields`, `setManualStepConfirmation`)
- `flowable:In` / `flowable:Out` moddle types for `bpmn:callActivity` `<flowable:in>` / `<flowable:out>` serialization
- Co-located panel sections under `components/bpmn/sections/`: `HumanApprovalSection`, `SendNotificationSection`, `ExternalApiCallSection` (+ tab content), `CallSubprocessSection`, `SetVariablesSection`, `ManualStepSection`
- `e2e/tests/27-bpmn-designer-m411-smoke.spec.ts` smoke test

#### Changed
- `CALL_SUBPROCESS` morphs to `bpmn:callActivity` (was ServiceTask); native `calledElement` + `<flowable:in>` / `<flowable:out>` (no delegate)
- `SEND_NOTIFICATION` morphs to `bpmn:sendTask` (was ServiceTask); delegate bean renamed `emailActionDelegate` → `notificationDelegate`; class renamed `EmailActionDelegate` → `NotificationDelegate`
- `MANUAL_STEP` without confirmation morphs to `bpmn:manualTask`; with confirmation morphs to `bpmn:userTask` with synthesized `formKey="__werkflow_confirm_step__"`
- `ServiceTaskPropertiesPanel.tsx` reduced from 1322 LOC to 109 LOC (thin dispatcher)
- `ServiceTaskLike` moddle type extended to cover `bpmn:CallActivity`, `bpmn:ManualTask`, `bpmn:ScriptTask`, `bpmn:UserTask` so `flowable:actionType` survives XML roundtrip
- All `<flowable:field>` / `<flowable:in>` / `<flowable:out>` writes now route through `modeling.updateProperties` for commandStack integrity (undo/redo + `hasChanges` flag)
- Element morphs deferred via `queueMicrotask` so React state updates settle before bpmn-js mutates the canvas; post-morph writes operate on the `bpmnReplace` return value
- React sidebar imports `setActionType` + `getApplicableActionTypes` from the Preact provider and passes `modeler` as injector (CRITICAL fix — previously the morph branch never fired from the sidebar)
- Example BPMNs migrated: `emailActionDelegate` → `notificationDelegate` in `onboarding-checklist.bpmn20.xml`, `procurement-approval-process.bpmn20.xml`, `capex-approval-process.bpmn20.xml`
- E2E specs `25-workflow-event-ticket.spec.ts` and `26-workflow-leave-request.spec.ts`: replaced `serviceTask + ${dmnRouteDelegate}` with native `bpmn:businessRuleTask + flowable:decisionRef`

#### Removed
- `DMN_ROUTE` action type (zero in-flight usage); `DmnRouteDelegate.java` deleted. BusinessRuleTask + native `decisionRef` is the canonical DMN-evaluation surface
- `CallSubprocessDelegate.java` (replaced by native CallActivity)

#### Fixed
- `NotificationDelegateTest` class body rename (file rename in commit `e472d2a` did not include the in-file class rename)

#### Refs
ADR-009, ADR-011 (werkflow-platform/docs/adr/). See M4.11 entry in master Roadmap.

---

### M4.6 Post-Merge Bugfixes (2026-05-10)

#### Fixed
- Engine service compile errors: `WebhookCorrelator` rewritten for Flowable 7.2.0 API (removed non-existent `MessageCorrelationResult`; uses `EventSubscriptionQuery` + `messageEventReceived`)
- Admin service compile errors: `getVariablesAt()` renamed to `variablesAt()` in `FormFacadeController` and `DmnFacadeController`
- Docker build failure: bumped `groovy-all` from `4.0.21` to `4.0.22`; added BuildKit cache mounts to Dockerfile Maven steps
- `ENGINE_SERVICE_URL` default corrected from `localhost:8081` to `werkflow-engine:8081` in `application.yml` (overrode `@Value` defaults in `EngineClient` and `ProcessVariableScopeService`)
- Engine `SecurityConfig`: `/api/v1/config/flowable-role-mappings` now `permitAll` for internal service-to-service calls
- `CandidateGroupsAggregator`: added `unless = "#result.isEmpty()"` to prevent startup race condition from caching empty candidate groups
- `LocaleProjector` and `VisibilityPolicyProjector`: added `evict()` methods; `ConfigurationVariableController` now calls them after LOCALE and POLICY config var mutations
- `RoleGroupMappingController`: evicts `pss.candidateGroups` cache after Tier 2 mapping create/delete
- `RoleGroupMappingController.create()`: resolves `tenantCode` from JWT when not provided in request body (fixes 400 on POST)
- `RoleGroupMappingService.delete()`: returns `tenantCode` of deleted entity for cache eviction

#### Changed
- Custody Mappings page: candidate group input is now a grouped `<select>` dropdown (Tier 1 + Tier 2) with free-text fallback only when no groups are configured
- Role Mappings page: Tier 2 candidate group field is always free text (groups are being defined here, not selected)
- `useCandidateGroups` hook: added `refetchOnMount: 'always'` to pick up backend changes after service restart
- `ChipRow` component: shows disabled "Loading groups…" select during fetch instead of text fallback
- Role Mappings and Custody Mappings mutations now invalidate `['pss', 'candidateGroups']` React Query cache on success
- Custody Mappings terminology section updated: "Process Custody" renamed to "Approval Group"; "Custody Owner" term added
- Admin service and engine service READMEs rewritten to reflect current implementation

---

### S21 — Code Cleanup & IP Preparation
- Added Apache 2.0 LICENSE, NOTICE, and LICENSES/ directory
- Added SECURITY.md, .github/ issue templates, and PR template
- Replaced proprietary license header in README with Apache 2.0 reference

---

## Pre-Release History (S1–S20)

> These sprints established the core platform before the initial public release.

### S20 — Business Service Decoupling
- Removed business-service from platform Dockerfile and docker-compose.yml
- Created `docker-compose.business.yml` overlay for optional business module deployment

### S19 — Business-Agnostic Delegate Layer
- Replaced all domain-specific Java delegates with a single `ExternalApiCallDelegate`
- Added POST body support and tenant-aware URL resolution via `TenantEndpointResolver`
- Migrated all BPMN processes to connector mode; deleted 21 domain delegate classes

### S18 — API Integration Layer
- Extracted `SsrfGuard` and `SecretsResolver` to shared `werkflow-common` module
- Introduced Connector Registry: `connector_key`, `display_name`, `sample_schema` per tenant
- Added `ServiceTaskPropertiesPanel` contract editor with schema-driven body builder
- Added Gateway Condition Builder with DOA level and custody group catalog support
- Added Form DataSource reference panel in the BPMN designer

### S17 — Multi-Tenant Identity, Group Routing & Integration Architecture
- Introduced `Tenant`, `TenantServiceEndpoint`, `TenantApiCredential`, `TenantRolePermission` data model
- Migrated Department from business-service to admin-service
- Replaced hardcoded Flowable group constants with 5-step `FlowableGroupResolver` pipeline
- Migrated all BPMN candidateGroups to `DEPT:*` / `DOA:*` prefix format
- Added admin UI screens for tenant configuration (users, departments, DoA, services, credentials)
- Security: fixed TaskGuard, added `@PreAuthorize` across task, process, and user controllers
- Purged hardcoded engine client secret from git history via `git filter-repo`

### S16 — Engine Service Fixes
- Fixed wrong property key for inventory service URL in `CreateAssetRequestDelegate`
- Added OAuth2 client credentials to engine for authenticated service-to-service calls
- Fixed NPE in `ProcessMonitoringUtil` when process variables contain null values
- Added Drafts section to the Processes page

### S15 — Permissions Model
- Introduced YAML-driven RBAC with `PermissionConfig` and `WerkflowPermissionEvaluator`
- Added domain guards: `AssetRequestGuard`, `TaskGuard`, `HubManagerGuard`
- Migrated all controller endpoints to `@PreAuthorize` annotations

### S14 — Generic Form Data Sources
- Introduced schema-declared `properties.dataSource` pattern for form-js select components
- Added `resolveFormData` / `resolveDependentData` utilities with cascade support
- Migrated asset request to generic start form; deleted custom `/requests/asset/new` page
- Added Flyway V16 seed migration for asset request form schema

### S13 — Form Key Enhancements
- Fixed Form Key preload bug in the BPMN designer
- Added Form Key support on all event types
- Added Trigger Process property (frontend + backend)

### S12 — Mailpit SMTP & Env Reorganisation
- Added Mailpit service for local email testing (port 8025)
- Reorganised environment files into `config/env/` folder
- Made SMTP configuration fully environment-variable-driven

### S11 — Action Blocks
- Added three BPMN action block types: Human Approval, Notification (email), External API Call
- Added visual distinction (colour + badge overlay) in the BPMN designer
- Added `EmailActionDelegate`, `ExternalApiCallDelegate`, `NotificationTemplateService`
- Added SSRF protection, secrets masking, and process audit log

### S10 — Design Studio Fixes
- Fixed Keycloak SSO logout (end_session with id_token_hint)
- Fixed form save, process deploy endpoint, and ProcessDraft backend
- Replaced form.io preview with form-js; deleted legacy form.io components
- Aligned roles (WORKFLOW_ADMIN, WORKFLOW_DESIGNER) across sidebar, pages, and SecurityConfig

### S9 — Fixes & Improvements
- Refreshed README to reflect current architecture
- Fixed Keycloak SSO logout silent re-authentication bug
- Cleaned sidebar: removed non-functional business-module sections

### S8 — Workflow Group Resolution Redesign
- Replaced Keycloak-group-based Flowable routing with DOA-level authority model
- Introduced `FlowableGroups` constants, `FlowableGroupResolver`, and startup BPMN validator
- Added `doa_threshold` DB table with tenant-configurable thresholds and admin UI
- Migrated all BPMN candidateGroups from domain roles to DOA levels

### S7 — Core Domain Redesign
- Redesigned domain model: multi-department org structure, asset request workflows
- Introduced `AssetRequest` entity, BPMN with custodian-dept routing, portal start form
- Standardised roles with `RoleLevel` enum and `doaLevel` in JWT context

### S6 — Playwright E2E Suite
- Full portal E2E coverage: auth, RBAC, process/workflow flows, DOA approval, form management
- GitHub Actions CI integration

### S5 — Integration Testing
- CapEx workflow verified end-to-end inside Docker (8 containers)
- Portal runtime issues resolved

### S4 — Frontend Completion
- Task detail page, request tracking page, and dashboard wired to backend APIs

### S3 — Service Consolidation
- Merged HR, Finance, Procurement, Inventory into single `business-service` (port 8084)
- Consolidated two portals into one Next.js app with route-group modules

### S2 — BPMN Wiring
- Migrated CapEx approval process to `RestServiceDelegate` with field injection
- Added JWT propagation and finance workflow endpoints

### S1 — Critical Fixes
- Replaced `WebClient.block()` with `RestClient` in `RestServiceDelegate` (thread starvation)
- Upgraded Flowable 7.0.1 → 7.1.0 for Spring Boot 3.3.4 alignment
- Deleted dual legacy package trees (Finance, Procurement)
