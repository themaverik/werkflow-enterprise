# Werkflow Enterprise — Roadmap

**Repo scope**: Enterprise-only engine, admin-service, and portal features
**Master Roadmap**: `~/Projects/werkflow-platform/docs/Roadmap.md` (authoritative for all future tasks)
**Last Updated**: 2026-06-22 (session 41)
**Target**: Internal Enterprise Demo — June 2026

> Future tasks in this file are synced from the master Roadmap. Do not add tasks here without adding them to master first.

---

## Current State

| Item | Status |
|------|--------|
| E2E quality gate | 7/7 specs passing (full milestone-end re-run = master item 9, last) |
| ADRs | ADR-001 through ADR-029 (in master `docs/adr/`) — latest: ADR-027 (approval escalation), ADR-028 (process test harness), ADR-029 (DOA emission + routing patterns) |
| Active milestone | Pre-MVP Tier 5 (release hardening) per master Roadmap |
| M4.11 / M4.12 | Complete (P3 11/11; Phase A + B.1a–B.6 + B.4/B.5-portal + item 8 sidebar gate) |
| Tier 1–3 done | All Tier-1 (mechanical cleanups, facade hardening, schema hygiene); item 7 approval-escalation cluster (BPMN + engine + 7c-UI, ADR-027) shipped |
| Done this session | ✅ **Session 18 — portal UX + engine semantics (2026-06-01, 9 enterprise commits `e09e023`→`e694c1e` + platform `8b1674f`, all pushed)** — ProcessExampleDeployer reset-on-startup; asset-request BPMN exec-keyword fix; `/services` card styling parity with `/processes`; source-aware back nav; "Start Process Anyway" fallback; V14 bare-date fix; hide Submit Request when no form; React Query `retry:false`; `FormNotFoundException`→404; CLAUDE.md mandate staff-engineer + frontend-developer review |
| Done this session | ✅ **Session 19 — DMN+connector+notification indicators + marketplace + DMN seeding (2026-06-01, 7 enterprise commits `515e7d3`→`ecd5d47`, NOT pushed yet)** — Items 1+2+3 shipped from session 18 punch list + B1/B2 sub-tasks uncovered during Item 3 + critical Flowable autoscan fix. (1) `515e7d3` Item 1 — `/services` no-form description ternary. (2) `8a272f7` Item 3 — DMN + connector indicators on /processes cards (Option 2): V15 `process_indicators` + `BpmnIndicatorScanner` (XXE-hardened DOM) + DTO extension + batch lookup `findAllById` (no N+1) + portal `Workflow` icon. (3) `9332162` B1 — new `DmnExampleDeployer` seeds 3 example DMNs under `tenantId="default"` + reset mode wipes ALL tenantless DMN deployments + prior default-tenant deployments; `ProcessExampleDeployer @DependsOn`. (4) `03142a4` B2 — scanner Spring-derives connector bean names from `Map<String, ConnectorDelegateBase>` (auto-includes @Bean aliases like `restConnectorDelegate`); `hasNotification` field for separate notification indicator; V16 ALTER + Bell icon (channel-agnostic) + new `DelegateLabel` utility. (5) `a74f9f7` Item 2 — marketplace `useQuery(listConnectors)` + amber info banner + installed badge + disabled "Installed" button + removed contradicting Contribute CTA. (6) `afac983`+`ecd5d47` critical fix — `flowable.dmn.deployment-resources` does NOT control Flowable's spring-boot autoscan; renamed `src/main/resources/dmn/` → `dmn-examples/` so `DmnExampleDeployer` is sole owner. Tests: scanner 15/15 + DelegateLabel 7/7 green; portal builds green; engine compile clean. Verified DB state: 6 DMN decisions in `default` tenant + zero tenantless rows + indicator table correctly populated per BPMN content |
| Done session 33 | ✅ **Session 33 — Portal design audit sprint (2026-06-12)** — All 20 items from `docs/portal-design-audit.md` implemented. Track A (5 security/correctness fixes: next-auth types, health auth guard, NEXT_PUBLIC_ removal, proxy fetch, redirect guard) committed to main (`d1f8a28`). Tracks B+C (design consistency + library hardening) on feature branch `feature/portal-design-audit` (`ad5bd1c`→`29c1b15`). Security audit: 4 MEDIUMs fixed (env var mismatch, ERP NEXT_PUBLIC_, connector param injection, health topology disclosure). 4 deferred non-blocking items in master roadmap post-MVP backlog. Design references: `frontends/portal/DESIGN.md` + `frontends/portal/CLAUDE_HANDOVER.md`. |
| Done this session | ✅ **Session 41 — manual E2E prep + logical unit enforcement audit + orphan cleanup (2026-06-22, enterprise commits `491b89f0` + `3214d5a8` + `ca17ba26`; first 2 pushed)** — (1) `docs/BPMN-Symbol-Reference.md` (334 lines, 71 inline BPMN 2.0 SVG figures, support vocabulary + E2E testability matrix + start-event formKey terminology clarification); (2) `docs/Manual-E2E-Test-Plan.md` (545 lines, 10 manual tests with inline SVGs, Test Artifact Lifecycle section with `e2e-` prefix convention + per-test reuse table + cleanup checklist, seed library reshuffle plan); (3) V24 orphan cleanup migration (atomic; deletes 13 legacy form versions + 13 orphan DMN versions + 26 orphan BPMN procdef rows; verified zero `act_ru_*`/`act_hi_*` row touched); (4) `ExampleSeedService.formExists` idempotency fix (5 LoC + new `FormSchemaService.formExistsAnyVersion` — eliminates duplicate-version-on-re-seed bug for archived forms). Audit found stale engine image (2026-06-16) predating session 40 seeder fix `e226f93f` (2026-06-18) — explains 5 missing forms; user manually rebuilds before manual E2E. |
| Next | Pre-MVP hard gate (user-confirmed correctness over speed): (a) User rebuilds engine + restart → V24 auto-runs + missing forms seeded; (b) Manual E2E Option A (10 tests vs current code as baseline); (c) D2 form tenant scoping (Flyway add `tenant_id` to `form_schemas` + thread tenantId through `BpmnFormKeyPinner`/`FormSchemaService`/`FormSchemaController`, 3-5h); (d) D1 R1 hardening with Amendments 1+5 (engine `BpmnFormKeyPinner` + `BundleDeploymentService` + standalone `/deploy` fail-loud on missing form/DMN refs + portal toast pattern, 1-3h); (e) Seed library reshuffle (finance-approval removal + IT Helpdesk authoring with self-contained `NotificationDelegate` verification, 3-4h); (f) Re-run authoring tests (Tests 2, 3, 6) against final code. Option B fallback (single test pass after all code lands) recorded but not preferred. |
| Branch | `main` — HEAD `ca17ba26` (engine fix + V24 not yet pushed; docs already pushed) |
| Operational | Dev DB at V23; V24 will land on first engine restart after user rebuild. Engine image rebuild required: `mvn clean package -pl services/engine -am -DskipTests && docker compose build engine && docker compose up -d engine`. `ProcessExampleDeployer` self-heals on boot; V24 is one-time cleanup for legacy state (NOT a permanent recurring migration). `.env.shared` requirements unchanged from session 40. |

---

## M2 — ADR Foundation + Performance ✅ COMPLETE

**Committed**: 2026-04-30 — commits a2b53ce, f048a98, 5ab62b4

### Engine Quick Wins (ADR-009)

- [x] Add `parentDeploymentId` to `ProcessDefinitionService` for bundle deployments
- [x] Add transient variables to `ExternalApiCallDelegate` — raw API response transient (`storeRawResponse`), masked response persists
- [x] Add task-local variables pattern to `ExternalApiCallDelegate` for parallel branch isolation (`useLocalVariables`)

### Form Field Types (ADR-007)

- [x] Refactor `FormSchemaValidator.java` — four sets: `VARIABLE_TYPES (A)`, `DISPLAY_TYPES (B)`, `SERVICE_TYPES (C)`, `INVALID_TYPES (D)`
- [x] `validateFormSchema()` — accepts A+B+C; rejects D with `400 Invalid component type`
- [x] `validateFormData()` — skips B; throws `FormFieldTypeNotImplementedException` (501) for C on submit
- [x] `TaskFormService.java` — filter Category B keys before `variablesToSave`
- [x] `FormFieldTypeNotImplementedException` + `GlobalExceptionHandler` 501 handler

### Signal Tenant Scoping (ADR-008)

- [x] `TenantAwareSignalService` — wraps `signalEventReceivedWithTenantId` + async variant; validates non-blank tenantId
- [x] 8 tests; no direct calls to non-tenant `runtimeService.signalEventReceived()` allowed

### Performance

- [x] Async history: `async-history-enabled: true`, pool 2/4 — history writes off the process transaction
- [x] DB indexes: `FlowableIndexCreator` adds 6 indexes on `ACT_RU_TASK` + `ACT_RU_IDENTITYLINK`
- [x] Dead-letter job monitoring: `JobManagementController` + portal `/admin/jobs/dead-letter` page + 5 tests
- [⏸️] Async email via Flowable `EmailJobHandler` — deferred; `@Async+@Retryable` already in place

---

## M3 — ADR Core Implementation

**Deps**: M1 (ERP APIs) + M2 complete — both done
**Estimate**: 8–10 hours remaining (Groups 3a/3d moved to M4)
**Next session**: Groups 3b + 3c + M5 together

### Group 2a — FlowableGroupResolver Simplification (ADR-003) ✅ COMPLETE

**Committed**: 2026-04-30 — commit 9dae8d8

- [x] Remove `doa_approver_level1/2/3/4` from `FlowableGroupProperties` YAML
- [x] Remove `doaLevel` cumulative loop and compound group emission from `FlowableGroupResolver`
- [x] Remove `adminServiceClient.getTenantDepartmentCodes()` and `getTenantCrossDeptThreshold()` calls
- [x] Add `RoleGroupMapping` table to admin-service + Flyway migration (V3)
- [x] Add `RoleGroupMappingService` with 5-min cache per tenant (via AdminServiceClient Caffeine)
- [x] Add `GET/POST/DELETE /api/v1/config/role-mappings` endpoints
- [x] Add `UserGroupLookupProxy` SPI interface

### Group 2b — configVars Admin API (ADR-002) ✅ COMPLETE

**Committed**: 2026-04-30 — commit 5d4e16c

- [x] `GET/POST/PUT/DELETE /api/v1/config/vars` for tenant `ConfigurationVariable` entries
- [x] Two-layer data: Level definitions (L1–L4 → amounts) + Role-to-level mapping (role → level)
- [x] Remove `crossDeptDoaThreshold` from Tenant entity + V4 migration drops DB column
- [x] `AdminServiceClient.getConfigVars(tenantCode)` with 5-min cache (prepares DmnConfigVariableInjector)

### Group 2c — BPMN Action Blocks (ADR-009) ✅ COMPLETE

**Committed**: 2026-04-30 — commit 4ce7b89

- [x] `SEND_NOTIFICATION` block — channel multi-select (Email + Slack/WhatsApp stubs), template picker, recipient, condition
- [x] `CALL_SUBPROCESS` block — processKey, inVariables, outVariables fields + `CallSubprocessDelegate`
- [x] `GROOVY_SCRIPT` block — inline script editor, admin-restricted label
- [x] `MANUAL_STEP` block — stepDescription + confirmationRequired fields
- [x] Remove `DMN_ROUTE` from action block options (native BusinessRuleTask per ADR-009)
- [x] Signal throw event: signal name dropdown (reads `bpmn:Signal` elements from diagram)
- [x] `SlackNotificationChannel` + `WhatsAppNotificationChannel` stubs (`UnsupportedOperationException`)

### Group 3b — Custody Move to ERP (ADR-004) ✅ COMPLETE

**Committed**: 2026-04-30 — commit 3cb362a

- [x] Remove custody DB table from admin-service (V5 migration + delete entity/repo/service/controller)
- [x] Update portal `/admin/custody` to call ERP `GET/POST/PUT/DELETE /api/v1/custody-mappings`; add `erpApiClient`; update BpmnDesigner custody dropdown
- [x] Add `ErpServiceClient` + `DmnConfigVariableInjector` (configVars + custodyVars enrichment); update `DmnRouteDelegate`

### Group 3c — Department Simplification (ADR-005) ✅ COMPLETE

**Committed**: 2026-04-30 — commit de0f6c9

- [x] `SetOwningDepartmentDelegate` — resolves submitter ERP dept → `owningDepartment` variable; fallback to form value
- [x] `FlowableGroupResolver` Step 4: fetch user ERP dept → emit `${deptCode}_APPROVER` (NOTE: will be REMOVED in M4.4 per ADR-010)
- [x] Remove `departments` table from admin-service (V6 migration)
- [x] Department-scoped query filter in `WorkflowTaskService` and `ProcessMonitoringService` (ERP-enabled guard)

---

## M4 — UI Full Visual Overhaul + Tenant Setup + Form Editor + Analytics UI ✅ COMPLETE

**Deps**: M3 complete (Groups 3b/3c), M5 complete, M6 Group A complete
**Estimate**: 32–36 hours (includes M6 Group B)
**Status**: COMPLETE — 2026-05-01 — branch feature/m4-ui-overhaul — final commit 7aac042

### Design Reference

All screens must be implemented against the approved Figma-export HTML designs:

**Local path**: `/Users/lamteiwahlang/Projects/Werkflow Redesigned Final/`

| File | Covers |
|------|--------|
| `Werkflow Redesigned.html` | Employee Portal — Dashboard, My Tasks, My Requests, Service Catalog, Processes, Forms, Decisions |
| `Werkflow Employee Portal.html` | Full portal shell — sidebar, header, navigation structure |
| `Werkflow Editor Theming.html` | BPMN / Form / DMN editor CSS theming targets |
| `Werkflow Email Templates.html` | Email Templates screen (Design Studio section) |
| `Werkflow Login.html` | Login page |
| `Werkflow Landing Page.html` | Public landing page |
| `uploads/` | Component screenshots — DMN Table, Form Editor, Process, Email Template |

**Rule**: Open the relevant HTML file before implementing any screen. Derive all colours, spacing, typography, and component structure from the design files — do not guess or invent.

### Group 3a — Tenant Setup UI (ADR-006)

- [x] `Tenant Setup` sidebar section (ADMIN/SUPER_ADMIN guard) — reordered: Role Mappings → Approval Authority → Departments → Custody Groups *(commit: 9d1d88a)*
- [x] `/admin/tenant/role-mappings` — Tier 1 read-only from engine YAML endpoint; Tier 2 with Keycloak realm-roles dropdown (ADR-003) *(commit: 54678f4)*
- [x] `/admin/tenant/approval-authority` — dynamic L1–L10 levels; 1:1 role→level with KC + level dropdowns; delete per row (ADR-002) *(commit: 54678f4)*
- [x] `/admin/tenant/custody-groups` — reads from ERP; info tip (Candidate Groups vs Custody Groups) *(commit: 9d1d88a)*
- [~] `/admin/tenant/departments` — reads from ERP; redirect from `/admin/departments` (ADR-005) — page exists, redirect pending
- [ ] Tenant Setup checklist widget on `/admin/dashboard`
- [x] Engine: `GET /api/v1/config/flowable-role-mappings` — returns YAML Tier-1 mappings as JSON *(commit: cbe28db)*
- [x] Admin: `GET /api/v1/keycloak/realm-roles` — lists KC realm roles via client-credentials Admin API *(commit: cbe28db)*
- [x] Portal: engine proxy route `/api/proxy/engine/[...path]` *(commit: cbe28db)*

### Group 3d — Form Editor Improvements (ADR-007) ✅ COMPLETE

- [x] `FormJsEditor.tsx` — fetch tenant component allowlist; pass `createPaletteFilterModule(allowedTypes)` to `FormEditor` *(commit: c5fc003)*
- [x] `FormJsEditor.tsx` — fetch `CSS_THEME` config vars; apply as inline style on `.fjs-container`
- [x] `lib/forms/createPaletteFilterModule.ts` — deregisters non-allowed types on `form.init`
- [x] `GET /api/v1/config/form-components` — hardcoded default allowlist; no admin UI (deferred post-demo)

### Design System Foundation ✅ COMPLETE

- [x] Tailwind config: primary purple palette, dark sidebar tokens, badge colour map
- [x] CSS custom properties: `--sidebar-bg`, `--primary`, `--primary-foreground`, `--badge-*`
- [x] Shared components: `StatCard`, `FilterPills`, `StatusBadge`, `PriorityBadge`, `AvatarCell`

### Navigation Overhaul ✅ COMPLETE

- [x] Dark sidebar: five sections with icon + label nav items; role-gated visibility per section *(commit: sidebar rewrite)*
- [x] Move Email Templates nav item from Admin → Design Studio section
- [x] User profile card at sidebar bottom (avatar, name, role)
- [x] Notification bell + user avatar in top-right header

### Screen Overhaul ✅ COMPLETE

- [x] **Service Catalog** *(commit: 8f62118)* — card grid, category filter pills, step tags, Submit Request CTA
- [x] **My Tasks** — stat cards row, All/Mine/Overdue/Unassigned tabs, task table *(commit: task 5)*
- [x] **My Requests** — request list with status tracking *(commit: task 6)*
- [x] **Forms** — stat cards, tabs, search, category pills, table + inline actions *(commit: task 7)*
- [x] **Processes** — stat cards, Deployed/Drafts tabs, card grid *(commit: 8f62118)*
- [x] **Decisions** — aligned to Forms list pattern *(commit: 8f62118)*
- [x] **Email Templates** — moved to Design Studio section; existing UI unchanged
- [x] **Dashboard** — overview cards, recent activity, quick actions *(commit: task 4)*
- [x] **Connectors** — aligned to new table pattern
- [x] **Tenant Setup sub-pages** — Approval Authority, Role Mappings, Departments, Custody Groups *(commit: 06866d5)*

### Editor CSS Theming ✅ COMPLETE

- [x] **bpmn-js** — canvas bg, toolbar buttons, properties panel bg/text *(commit: 05843e8)*
- [x] **form-js** — container bg, field labels/inputs, buttons, palette panel *(commit: 05843e8)*
- [x] **dmn-js** — table header bg, cell borders, toolbar, hit policy badges *(commit: 05843e8)*
- [x] All three: inject primary color + font via CSS custom properties; no JS internals

---

## M5 — ADR Signal Events ✅ COMPLETE

**Committed**: 2026-04-30 — commit 00e04aa

- [x] Procurement process: `IntermediateThrowEvent(Signal)` after final approval — uses `TenantAwareSignalService`
- [x] Asset request process: `IntermediateCatchEvent(Signal)` for `procurementApproved`
- [x] All approval UserTasks: non-interrupting Timer boundary (PT48H → reminder)
- [x] All approval UserTasks: interrupting Timer boundary (PT72H → escalate)
- [x] All external-call service tasks: Error boundary event with fallback flow

---

## M6 — Analytics + Basic Monitoring ✅ COMPLETE (Group A + B)

**Committed**: Group A commit b1c9f15 · Group B commit 7aac042

- [x] Backend: process execution stats, task metrics (avg cycle time, bottleneck step, SLA %) — all < 1s for 100k+ instances
- [x] Frontend Analytics Dashboard: overview stat cards, line chart (executions over time), bar chart (task bottlenecks), SLA dashboard, CSV export
- [x] Monitoring sidebar section (ADMIN/SUPER_ADMIN): Analytics Dashboard + Process Health links
- [x] Health check endpoints on all services (`/actuator/health`) via portal proxy
- [ ] Basic alerting runbook doc — deferred post-demo

---

## M4.9 — UI Polish (BPMN Designer + Portal)

**Branch**: `feature/m4-9-ui-polish`
**Phase**: Post-M4 hardening before internal demo
**Last session**: 2026-05-12

### Completed this session

- [x] Datasource edit page crash — replaced `use(params)` with `useParams()` (Next.js 14 compat)
- [x] Service Catalog "General" label repeating on all groups — fixed category fallback logic
- [x] BPMN right panel: Custody Groups + Artifact Metadata headers now use bpmn-js chevron style (no +/- buttons, consistent font)
- [x] Artifact Metadata: removed dev-time PSS pill links
- [x] Role Mappings page crash — `g.groupName` → `g.key`/`g.label` (CandidateGroupEntry shape change)
- [x] CandidateGroupsInput: tier 1 filter no longer requires `readOnly: true`
- [x] Moved `candidateGroupsEntry` into the `flowable-assignment` group; removed duplicate from `HUMAN_APPROVAL` action block; fixed attribute access (`flowable:candidateGroups` → `candidateGroups`)

### Open Questions — pick up next session

> **Context**: The Action Block → HUMAN_APPROVAL → Assignee Expression is the authoritative assignment mechanism in Werkflow. The standard BPMN Assignment section (Assignee, Candidate Users, Candidate Groups) maps to different XML attributes that the engine may not read.

- [ ] **Task 1** — Decide: remove standard BPMN `flowable-assignment` group from UserTask panel entirely, or keep as low-level override? Consult ADR-009 (BPMN task type → action block mapping).
- [ ] **Task 2** — Move the 4-section suggestion panel (Process Variables, Custody Lookups, Business Tier 2, System Tier 1) to assist the Assignee Expression FEEL field in Action Block → HUMAN_APPROVAL. Currently wired to the wrong field (`candidateGroups`).
- [ ] **Task 3** — Scope Artifact Metadata panel to process level only (no element selected). Hide when any element is active on the canvas.
- [ ] **Task 4** — Decide Custody Groups reference panel visibility: always-on, process-level-only, or only when a UserTask with HUMAN_APPROVAL is selected.
- [ ] **Task 5** — Remove `flowable-assignment` group (Assignee, Candidate Users, Candidate Groups text fields) from `flowable-properties-provider.ts` once Task 1 decision is made.
- [ ] **Open question** — Attribute discrepancy: native `assignee` field reads `businessObject.assignee`; Action Block reads `businessObject.get('flowable:assignee')`. Are these the same XML attribute? Verify with moddle extension config and engine behaviour.

---

## M7 — CI/CD + Production Readiness

**Deps**: none hard; slot after M2 is stable
**Parallel-safe**: alongside M4–M6
**Estimate**: 6–8 hours (enterprise share)

- [ ] CI (`ci.yml`): trigger on PR + push to main; jobs: engine-build, admin-build, portal-build
- [ ] Release (`release.yml`): trigger on tag `v*`; build + push to `ghcr.io`; GitHub Release
- [ ] `docker-compose.production.yml` — resource limits, restart policies, health checks, log rotation
- [ ] `.env.production.example` — all vars with comments
- [ ] `scripts/validate-env.sh`, `backup.sh`, `restore.sh`
- [ ] Deployment + troubleshooting runbooks

---

## Deferred

| Feature | Status |
|---------|--------|
| Governed Case Management (S28.7) | Post-June |
| AI Gateway (S30) | Post-June — will implement `transport: mcp` adapter on M4.5 ConnectorDefinition envelope |
| Vertical Workflow Templates (advanced) | Post-June |
| Message Broker Connectors (Kafka/RabbitMQ/SQS) | Customer-driven — `transport: messaging` slot reserved in ConnectorDefinition envelope |
| gRPC Connectors | Post-launch — internal-microservice-only initially |
| M4.4c — Priority + SLA + Process Categories | Post-demo polish — see Other-Semantics-To-Standardize.md |
| M4.4d — Reason Codes + Business Calendar | Post-demo polish — see Other-Semantics-To-Standardize.md |
| Tenant Setup checklist widget | Post-demo on `/admin/dashboard` |
| Basic alerting runbook | Post-demo |

---

## Historical Summary — Completed (S21–M2)

| Sprint / Milestone | Highlights |
|--------------------|-----------|
| S21–S23 | OSS cleanup, IP prep, i18n (next-intl) |
| S24–S25 | Multi-tenancy security, history cleanup, 3 sample BPMNs + Flyway seeds |
| S26–S26.7 | CI/CD (v1.0.0 tagged), OSS/enterprise repo split, Flyway V1+V2 squash |
| S27 | BPMN timer/signal/multi-instance, SLA + parallel committee templates |
| S28.5 | GlobalTaskNotificationListener — engine-level email on assign/complete |
| S28.6 | DMN decision table support — dmn-js editor, full CRUD API, audit trail |
| S28.8 | BPMN designer hardening — flowable-moddle, deploy fixes, draft system (5 bugs) |
| S28.9 | Email template designer (Unlayer), CRUD API, BPMN template key picker |
| S28.9+ | Post-fixes: toast system, Script Task panel, Expression Builder security |
| S28.10 | BPMN smart dropdowns — delegate expression select, candidate groups tag-select |
| E2E Gate | 7/7 specs pass; mike.it 4th test user added |
| ADR Session | ADR-002 through ADR-009 written (2026-04-28); ADR-010 written (2026-05-09) |
| M2 | Engine QW (ADR-009) + Form types (ADR-007) + Signal scoping (ADR-008) + async history + DB indexes + dead-letter UI |

---

## M4.4 — Platform Semantics Service + Categorization (ADR-010)

**Phase**: Pre-Internal-Demo
**Estimate**: 16–18 hours
**Reference**: Master Roadmap M4.4 / [`docs/M4.4-Platform-Semantics-Service-FINAL.md`](~/Projects/werkflow-platform/docs/M4.4-Platform-Semantics-Service-FINAL.md)
**ADR**: ADR-010 (Department Simplification + Categorization)

### Engine Refactor (ADR-010)

- [x] Remove Step 4 from `FlowableGroupResolver` (lines 76–78: `${deptCode}_APPROVER` emission, ADR-005 remnant)
- [x] Update sample BPMN files: replace `${deptCode}_APPROVER` with role-mapped groups via DMN routing
- [x] Update `FlowableGroupResolver` javadoc (document three-step model)

### Backend PSS (services/admin)

- [x] `PlatformSemanticsController` — nine endpoints under `/api/v1/design/platform/` (JWT-only tenant; no user-supplied tenantCode)
  - `/capabilities`, `/candidate-groups`, `/feel-expressions`, `/process-variables`
  - `/categories`, `/tags`, `/departments`, `/visibility-policy`
- [x] `CapabilityAggregator`, `CandidateGroupsAggregator`, `FeelExpressionGenerator`
- [x] `CategoryProjector`, `TagProjector`, `VisibilityPolicyProjector`, `DepartmentProjector`
- [x] Caffeine cache (5-min TTL, invalidation on admin writes)

### Schema Migrations

- [x] `category` table (Flyway V16 — applied)
- [x] Add `department_code`, `category_id`, `tags[]` to `process_draft`, `form_schemas` (engine V5)
- [x] Add `is_manager_tier` boolean to `role_group_mapping` (Flyway V17 — applied)
- [x] Seed default categories on tenant creation

### Frontend (frontends/portal)

- [x] BPMN designer: candidate-groups picker from PSS (no department section)
- [x] DMN designer: type-aware autocomplete from PSS feel-expressions
- [x] Artifact metadata panel (shared): department + category + tags pickers
- [x] Tenant Setup → Categories admin page (CRUD + server-side role gate)
- [x] Tenant Setup → Visibility Policy admin page (server-side role gate)
- [x] Capability-aware degradation in all three designers

### Security fixes (post-review)

- [x] C-1: Removed `?tenantCode=` query param — tenant derived exclusively from JWT
- [x] C-2: Parameterized SQL LIKE clause in `TagProjector` (no string concatenation)
- [x] H-1: Added `@AuthenticationPrincipal Jwt jwt` to `/process-variables` for audit trail
- [x] H-2: `app/(platform)/admin/tenant/layout.tsx` — server-side `auth()` role gate

---

## M4.4a — Process Custody UI Cleanup ✅ COMPLETE (7accb93)

**Phase**: Pre-Internal-Demo (alongside M4.4)

- [x] Rename admin route `/admin/tenant/custody-groups` → `/admin/tenant/custody-mappings`; redirect from old URL *(commit: 7accb93)*
- [x] Process list cards surface department/category/tags as read-only custody metadata *(commit: 7accb93)*
- [x] Glossary / Terminology section on Custody Mappings page disambiguating runtime routing vs. definition governance *(commit: 7accb93)*

---

## M4.4b — Currency Standardization ✅ COMPLETE (7accb93)

**Phase**: Pre-Internal-Demo (alongside M4.4)

- [x] `ConfigurationVariable` type=LOCALE (currency, locale, timezone, numberFormat, dateFormat per tenant) *(commit: 7accb93)*
- [x] PSS endpoint: `GET /api/v1/design/platform/locale`; `LocaleProjector` with safe USD default *(commit: 7accb93)*
- [x] `formatCurrency` / `formatDmnThreshold` utilities; DMN FEEL panel shows locale-formatted thresholds *(commit: 7accb93)*
- [x] Tenant Setup → Locale admin page (currency + timezone + date format + live preview) *(commit: 7accb93)*

---

## M4.5 — Connector Spec Formalisation + DTDS Shared Core

**Phase**: Pre-Internal-Demo
**Estimate**: 18–22 hours
**Reference**: Master Roadmap M4.5

### Spec Adoption (services/admin)

- [ ] Copy connector schemas to `services/admin/src/main/resources/schemas/connector/v1/`
- [ ] `ConnectorDefinitionValidator` — JSON Schema validation at registration
- [ ] Flyway: `connector_definition_v2` table (key, version, tenant_id, definition_json)
- [ ] Migration: convert existing connector rows to ConnectorDefinition envelope format
- [ ] OpenAPI ingestion: `POST /api/v1/connectors/import-openapi`

### DTDS Shared Core (services/admin/designtime/)

- [ ] `DesignTimeDataController` — routes under `/api/v1/design/`
- [ ] `ConnectorCatalogService` — tenant-scoped connector list + definition retrieval
- [ ] `SchemaResolverService` + `SchemaFlattenerService`
- [ ] Caffeine cache keyed `{tenantCode}:{connectorKey}:{version}:{operationId}:{direction}` (30-min TTL)
- [ ] All DTDS endpoints (`GET /connectors`, `GET /connectors/{key}`, `GET /connectors/{key}/operations`, etc.)

### BPMN Facade (services/admin/designtime/bpmn/)

- [ ] `BpmnFacadeController` — `GET /api/v1/design/bpmn/processes/{processDefId}/variables-at/{activityId}`
- [ ] `ProcessVariableScopeService` — BPMN XML traversal, accumulated variables with provenance

### Portal Integration

- [ ] Replace BPMN connector dropdown with DTDS-driven version
- [ ] Add operation picker (by category icon) + output field tree + input field form
- [ ] Connector list reads from DTDS; OpenAPI import wizard

---

## M4.6 — Webhook Inbound + DTDS Form/DMN Facades

**Phase**: Pre-Internal-Demo (after M4.5)
**Estimate**: 16–20 hours
**Reference**: Master Roadmap M4.6

### Webhook Receiver (services/engine/webhook/)

- [ ] `WebhookController` — `POST /api/v1/webhooks/{tenantCode}/{connectorKey}`
- [ ] `HmacVerifier` (pluggable: Stripe-style, GitHub-style, raw SHA-256)
- [ ] `WebhookCorrelator` — Flowable message correlation (start or signal in-flight)
- [ ] `ReplayProtectionService` — idempotency key cache per connector
- [ ] Dead-letter queue: `webhook_undelivered` table + Monitoring screen integration

### DTDS Form Facade (services/admin/designtime/form/)

- [ ] `FormFacadeController` — `GET /api/v1/design/form/binding-targets`, `/connector-options/{key}/{opId}`

### DTDS DMN Facade (services/admin/designtime/dmn/)

- [ ] `DmnFacadeController` — `GET /api/v1/design/dmn/decisions/{dmnId}/inputs`, `/binding-candidates`
- [ ] FEEL type converter (JSON Schema → FEEL)

### Portal (Form + DMN designer integration)

- [ ] Form-js: select-field data source picker from DTDS Form facade
- [ ] DMN editor: ranked variable candidates from DTDS DMN facade

---

## M4.7 — Database Connector + Connector Generators

**Phase**: Pre-Internal-Demo (after M4.5)
**Estimate**: 14–18 hours
**Reference**: Master Roadmap M4.7

### Refactor ExternalApiCallDelegate

- [ ] Extract `ConnectorDelegateBase` — shared: audit, masking, error mode dispatch, transient/local variables
- [ ] Rename `ExternalApiCallDelegate` → `RestConnectorDelegate extends ConnectorDelegateBase`

### Database Adapter (services/engine/action/)

- [ ] `DatabaseConnectorDelegate extends ConnectorDelegateBase`
- [ ] `NamedQueryExecutor` (JdbcTemplate, setMaxRows, setQueryTimeout, setReadOnly)
- [ ] `KeysetPaginator` — pagination loop via cursorParameters
- [ ] `DatasourceRegistry` — per-tenant JDBC datasource (analogous to TenantEndpointResolver)
- [ ] Resilience4j circuit breaker keyed per `{tenantCode}:{connectorKey}`
- [ ] DML rejection at registration (readOnly flag enforcement)

### Admin Screen + Demo

- [ ] `/admin/tenant/datasources` — CRUD for tenant datasources; "Test connection" button
- [ ] Seed `legacy-hris-readonly` demo DB connector

---

## M4.8 — Marketplace Foundation

**Phase**: Demo onward (community-driven)
**Estimate**: 6–8 hours core team
**Reference**: Master Roadmap M4.8

- [ ] `marketplace/` directory structure + CI validation workflow
- [ ] `marketplace/CONTRIBUTING.md` — submission guidelines
- [ ] Seed: `werkflow/werkflow-erp`, `community/slack`, `community/github`, `community/postgres-readonly`, `community/openai-chat`
- [ ] Portal page `/admin/marketplace` — browseable catalog + Install action
