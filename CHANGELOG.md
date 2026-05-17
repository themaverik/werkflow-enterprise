# Changelog

All notable changes to werkflow are documented here.

Format: `[Unreleased]` for in-progress work. Releases follow [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Form Designer Polish — Full Form-js Theming, Light Palette, Backend Validator Fixes (2026-05-17)

Branch `feature/form-designer-polish` (19 commits). Addresses DISCOVERY.md HIGH polish targets, viewer-vs-designer canvas rendering drift, the full `bpmn-io/form-js` CSS variable audit, multiple form-js library overrides surfaced during iterative smoke testing, and three backend validator gaps blocking standard form-js components. Reviewers: `staff-engineer` + `frontend-developer` (parallel); all flagged issues addressed before merge.

#### Added
- `frontends/portal/components/forms/formjs-theme.css` — new file (~600 lines). Full form-js variable wiring against `--panel-*` design tokens (typography, accent, surface, borders, text, inputs, FEEL editor, list/add/remove, toggle, palette, dragula, header, group, description). Higher-specificity selectors (`.fjs-editor-container .fjs-palette-container`, `.fjs-editor-container .fjs-properties-container`) to win over the library's local variable declarations. Imported AFTER the library CSS in `FormJsEditor.tsx` and `FormJsViewer.tsx` so source order keeps our overrides winning.
- Editor canvas parity rules — mirror `.fjs-container .fjs-input` library rules onto `.fjs-editor-container` since the editor wraps the canvas in `.fjs-form-container .fjs-form`, not `.fjs-container`.
- Prose typography inside `.fjs-form-field-text` — restores Tailwind-reset h1-h6, p, ul/ol, strong/em/a/code so stored `<h2>` etc. renders as actual headings.
- `onError?: (err: unknown) => void` prop on `FormJsEditor` and `FormJsViewer` — surfaces import/schema failures via toast; ref-backed (`onErrorRef`) to avoid stale closures.
- `schemaRef` + `lastImportedSchemaJsonRef` + `isReady` dep on schema-change useEffect — fixes a race where API-resolved schema arriving mid-mount was never imported.
- `isValidFormJsSchema()` structural guard for upload validation.
- `Loader2` spinner overlay (replaces text-only loading state).
- bpmn.io attribution (LGPL §4 compliance) — clickable `<a>` injected into `.fjs-palette-footer` (which is now pinned at the visible bottom via `display: flex; flex-direction: column` on `.fjs-palette` + `flex: 1; overflow-y: auto` on `.fjs-palette-entries`). Uses library convention class `fjs-powered-by fjs-form-field`.
- Unified `.form-designer-aside` shell replacing the inconsistent shadcn-Card-vs-bg-muted-div mix between Version History and Data Sources sidebars.
- Five `formBuilder.*` i18n keys (uploadInvalidSchema, uploadInvalidJson, uploadSuccess, invalidInitialForm, editorError).
- Backend: `PATHED_TYPES = {"group", "columns", "dynamiclist"}` in `FormSchemaValidator.java` so container/pathed types are exempted from `requiresKey()` — mirrors form-js's own `config.keyed` vs `config.pathed` distinction. Plus 4 new tests (`pathedTypes_containsContainerAndDynamiclist`, `validateFormSchema_acceptsGroupWithoutKey`, `validateFormSchema_acceptsDynamiclistWithPathNotKey`, `validateFormSchema_acceptsSeparatorWithoutKey`).
- Backend: `"separator"` added to `DISPLAY_TYPES` — form-js renders `<hr>` via the Separator component, registered alongside Spacer.

#### Changed
- Palette switched from dark to light theme — light-grey container, white tiles with `--panel-card-border` + soft shadow, dark icons/text, teal hover with subtle glow. Aligns with the rest of the portal's light-card aesthetic.
- Palette width 224 → 268px so two tiles fit the longest stock label ("Radio group", "Date time") on a single line; tile height 68px; grid gap 10px; library's hardcoded `width: 72px; margin: auto` on `.fjs-palette-container .fjs-palette-group` overridden with `width: auto; margin: 0` so the grid track governs width.
- `DEPARTMENTS` hardcoded array removed from FormJsBuilder — now fetched via `useQuery(getDepartments)` from the existing `/api/v1/departments` (ERP service) with loading + empty states. Was a real data bug, not cosmetic — `owningDepartment` is persisted with the form.
- Palette allowlist synced with `FormSchemaValidator.VALID_FIELD_TYPES` (was using invalid `'date'` and rejected types `filepicker/iframe/documentPreview/separator`); now: textfield, textarea, number, checkbox, radio, select, checklist, taglist, date, time, datetime, email, group, columns, html, text, button, image, spacer, separator, dynamiclist.
- `--color-background` mapping (was `--panel-input-disabled-bg`, now `--panel-input-bg`) so canvas form inputs render white not grey-tinted.
- FormJsBuilder dark toolbar uses `--panel-*` tokens instead of hardcoded colours.
- Unsaved-changes badge uses `text-amber-300` (8.3:1 WCAG AA on dark bg) instead of `rgba(255,200,80,0.85)`.
- `alert()` → sonner toasts in `FormJsBuilder.tsx` upload handlers and `preview/[key]/page.tsx` submit handler; `console.*` paths now propagate via `onErrorRef`.
- FormJsEditor wrapper `minHeight: 600px` removed — was causing the inner container to overflow the parent's `flex-1 overflow-hidden` when the viewport was shorter than 600px, clipping the bpmn.io footer below the visible region.
- All four form-js import sites converted to `next/dynamic({ ssr: false })` (FormJsBuilder, preview page, tasks FormSection, formjs-demo page) — fixes SSR `ReferenceError: KeyboardEvent is not defined` because `@bpmn-io/form-js[-editor]` references browser-only globals at module load.
- FormJsEditor loading overlay clears on import failure (`setIsReady(true)`) so the spinner does not hang indefinitely.
- `FormJsViewer.tsx` no longer imports `form-js-editor.css` (was wrong — viewer doesn't need editor styles).
- Duplicate `form-js-editor.css` import removed from `FormJsEditor.tsx`.

#### Fixed
- CRITICAL (engine, requires service restart to take effect): `requiresKey()` was rejecting standard form-js schemas for `group`, `columns`, `dynamiclist` (which use either inline child grouping or `path` instead of `key`) and for `separator` (which was not in `VALID_FIELD_TYPES` at all). All four now save cleanly.
- CRITICAL (frontend): canvas-empty bug on `/forms/edit/<key>` — race condition where API-resolved schema arrived after `editorRef.current` was null at second useEffect's first run, never re-imported.
- HIGH: radio/checkbox `<input>` width:100% from the canvas-input rule pushed sibling labels to the far right of the flex row. Scoped via `:not([type="radio"]):not([type="checkbox"])` and explicit `width: auto; flex: 0 0 auto` for radio/checkbox.
- HIGH: library's hardcoded `width: 72px` on `.fjs-palette-container .fjs-palette-field` defeated the grid layout. Overridden with `width: auto`.
- HIGH: bpmn.io attribution DOM leak on final unmount — `containerRef.current.innerHTML = ''` added to mount-effect cleanup.
- HIGH: `editor.importSchema(serializeSchemaProperties(schema))` was being called twice on cold mount (mount effect + `isReady` useEffect re-fire). Guarded with `lastImportedSchemaJsonRef`.
- HIGH: PRESENTATION/CONTAINERS palette groups stayed visible as empty cards because the previous nested `:has(.fjs-palette-fields:has(...))` selector was unreliable. Collapsed to single-level `:has(...)`.
- HIGH: text-view component rendered HTML as plain inline text because Tailwind preflight stripped h1-h6 defaults. Restored prose typography scoped to `.fjs-form-field-text`.
- MEDIUM: `t('key', { default: '...' })` is not valid next-intl v3 API — replaced with proper en.json keys.
- MEDIUM: schema-load logic in `FormJsBuilder` had a destructive `if (components && !type) reset to empty default` branch that discarded valid schemas missing only the top-level `type` field. Now spreads `{ type: 'default', schemaVersion: 9, ...parsed }`.
- MEDIUM: `/api/proxy/admin/config/form-components` returned 500 on every mount (endpoint doesn't exist on the backend). Removed the dead fetch; defaults inline.
- MEDIUM: schema upload accepted malformed inputs — replaced loose `!json.type || !json.components` with `isValidFormJsSchema()` structural guard.
- MEDIUM: LGPL "prominent notice" — bpmn.io attribution bumped from 10px muted to 11px / weight 600 / `--panel-text`.
- MEDIUM: data sources and version history sidebars looked completely different (one shadcn Card, one bg-muted div). Unified.
- LOW: search input was visually detached from its magnifying-glass icon because we were styling the inner `<input>` instead of the library's intended `.fjs-palette-search-container` shell. Fixed to match library convention.

#### Companion Reviews
- `staff-engineer` review iteration 1 — flagged 1 HIGH (stale closure), 3 MEDIUM (next-intl `default`, BpmnDesigner token gap, duplicate CSS import), 2 LOW. All addressed before commit.
- `staff-engineer` review iteration 2 — flagged 1 CRITICAL (radio width:100% pushing labels right), 1 HIGH (DOM cleanup leak), 2 MEDIUM (cold-mount double-import, LGPL prominence), 3 LOW. All addressed.
- `frontend-developer` review — flagged 1 MEDIUM (placeholder visibility), 3 LOW. All addressed.

#### Follow-ups Tracked in ROADMAP
- Cross-editor toolbar uniformity (BPMN designer header still uses shadcn Card, not `--panel-hdr-bg` — keeps the three editors visually inconsistent).
- Tenant-specific component allowlist via `/api/v1/config/vars?type=FORM_COMPONENT_ALLOWLIST` (deferred — backend endpoint doesn't exist yet).
- Backend extensions for `filepicker`, `iframe`, `documentPreview` if these become required (form-js ships them but engine `VALID_FIELD_TYPES` doesn't accept them).
- "Image upload with preview" combined component (form-js has `filepicker` for upload and `image` for URL display but no combined widget).

---

### M4.11 P3 — External-Api-Call Audit + CRITICAL/HIGH Fixes (2026-05-17)

First per-element P3 audit run under the expanded 5-point scope (Flowable native capability, Action Block mapping, Assignment mapping with context-aware variable combobox, Delegate compliance, Panel-section visibility). Audit doc committed in werkflow-platform `feature/m4.11-p3-external-api-call-audit`; 7 CRITICAL/HIGH findings fixed here.

#### Added
- `ab:timeoutSeconds` extension element on EXTERNAL_API_CALL action block — per-request HTTP timeout, default 30s, overridable per task
- `werkflow.connector.audit_failure` Micrometer counter (tagged by `actionType`) for alerting on audit-log write failures
- `tryExtractFieldsFromJson()` helper in `externalApiHelpers.ts` returning discriminated union `{ ok: true; rows } | { ok: false; error }` — surfaces JSON parse failures instead of swallowing
- Guided variable insertion affordance on the Request Body textarea in `ExternalApiCallSection` — toggle button reveals `VariableComboBoxBpmnAdapter`; selection inserts `${varName}` at cursor (sources: `dtds-variables-string|number|date`)
- `InputMappingRow` sub-component extracted from `DtdsInputFieldForm` so `useCallback` memoization works per-row

#### Changed
- `RestConnectorDelegate` now uses JDK `HttpClient.send()` directly (5s connect timeout + per-request timeout) — Spring `RestClient` wrapper removed because per-request timeout could not be exposed through it
- `ConnectorDelegateBase` constructor now takes `MeterRegistry`; cascade applied to `DatabaseConnectorDelegate` constructor
- Audit-log save failure no longer swallowed — logs at error level with full context (`actionType`, `executionId`, `processInstanceId`, `activityInstanceId`); failure still does not abort workflow execution
- SSRF guard moved to run AFTER expression substitution on the final URL string, just before HTTP dispatch — closes the bypass where `${expr}` in path was not validated
- Connector health-check fetch in `useExternalApiState` wrapped in `AbortController` with 5s timeout via `AbortSignal.any` (runtime-guarded); aborted on connector change, on unmount (dedicated cleanup effect), and on timeout
- Stale `contractJson` cleared synchronously on connector change; sample schema is set only in the success path
- Path field in `SetupTabContent` replaced plain `Input` with `VariableComboBoxBpmnAdapter` (`mode="single"`, source `dtds-variables-string`)
- Input mapping rows in `DtdsInputFieldForm` right-hand side replaced plain `Input` with `VariableComboBoxBpmnAdapter` (sources: `dtds-variables-string|number|date`)
- All `VariableComboBoxBpmnAdapter` `getValue` / `setValue` closures wrapped in `useCallback` at call sites — prevents the adapter's `[getValue]` effect from looping under parent re-renders
- Contract-import error display has `role="alert"` + `aria-live="polite"`; raw V8 `SyntaxError.message` replaced with a user-friendly normalized message
- Body picker toggle button has `aria-expanded` + `aria-controls`; focus moves into the picker on open

#### Fixed
- CRITICAL: `RestConnectorDelegate` had no HTTP timeout — engine threads could hang indefinitely on slow/dead endpoints, risking thread-pool starvation and stuck workflow instances
- HIGH: `ConnectorDelegateBase` swallowed audit-log save failures silently — compliance gap; operators had no signal when audit writes were failing
- HIGH: connector health-check fetch fired without `AbortController` — stale fetches resolved on unmounted components, causing setState warnings and stale state corruption
- HIGH: `extractFieldsFromJson` returned an empty array on JSON parse failure with no user-visible error — silent contract-import failure
- HIGH: connector change did not clear `contractJson` — switching connectors could leave the previous connector's schema in the textarea
- HIGH: `ExternalApiCallSection` had zero `VariableComboBoxBpmnAdapter` coverage — Path / Body / input mapping fields all relied on manual `${varName}` typing with no completion, no validation, no error surfacing
- HIGH: SSRF guard ran on the pre-substitution URL template — bypass risk where `${expr}` in path could resolve to internal-network or metadata endpoints at runtime

#### Deferred to Polish Pass / Subsequent P3 Audits
- 12 MEDIUM/LOW findings rolled into the Service-Task.md P3 audit (status code patterns, JSON response parsing, retry policy, async non-blocking via `FutureJavaDelegate`, multi-header support, body-template JSON validation, JSONPath silent skips, audit-log idempotency under retry)
- D1 implementation (admin tenant flag to gate raw URL mode) — decision locked, implementation deferred to admin-settings work
- Cosmetic duplicate visible labels on `VariableComboBox` call sites (outer `<Label>` plus adapter's internal label) — needs `ariaLabel` prop added to the adapter in a polish session

#### Companion Audit Doc
- `werkflow-platform` commit `992b536` — `docs/flowable-7.2/External-Api-Call.md` (354 lines, full 5-point audit + punch list + decisions D1–D4)

---

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
