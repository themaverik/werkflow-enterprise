# Werkflow Enterprise — Roadmap

**Repo scope**: Enterprise-only engine, admin-service, and portal features
**Master Roadmap**: `~/Projects/werkflow-platform/docs/Roadmap.md` (authoritative for all future tasks)
**Last Updated**: 2026-04-30 (session end)
**Target**: Internal Enterprise Demo — June 2026

> Future tasks in this file are synced from the master Roadmap. Do not add tasks here without adding them to master first.

---

## Current State

| Item | Status |
|------|--------|
| E2E quality gate | 7/7 specs passing |
| ADRs | ADR-001 through ADR-009 written |
| Active milestone | M3 — Groups 2a/2b/2c complete; Groups 3b/3c next (3a/3d merged into M4) |
| Next session | Groups 3b + 3c + M5 together |
| Branch | feature/m3-adr-core (in progress) |

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

### Group 3b — Custody Move to ERP (ADR-004)

- [ ] Remove custody DB table from admin-service
- [ ] Update portal `/admin/tenant/custody-groups` to call ERP `GET/POST/PUT/DELETE /api/v1/custody-mappings`
- [ ] Add `custodyVars` context builder in `DmnConfigVariableInjector` (5-min cache per tenant)

### Group 3c — Department Simplification (ADR-005)

- [ ] `SetOwningDepartmentDelegate` — resolves submitter ERP dept → `owningDepartment` variable; fallback to form value
- [ ] `FlowableGroupResolver` Step 4: fetch user ERP dept → emit `${deptCode}_APPROVER`
- [ ] Remove `departments` table from admin-service
- [ ] Department-scoped query filter in `TaskService` and `ProcessMonitoringService` (ERP-enabled guard)

---

## M4 — UI Full Visual Overhaul + Tenant Setup + Form Editor

**Deps**: M3 Groups 3b/3c complete (ERP custody + department APIs wired)
**Estimate**: 28–32 hours
**Design handoff**: 2026-04-30

### Group 3a — Tenant Setup UI (ADR-006)

- [ ] `Tenant Setup` sidebar section (ADMIN/SUPER_ADMIN guard)
- [ ] `/admin/tenant/approval-authority` — two-layer configVars UI: L1–L4 threshold amounts + role→level mapping (ADR-002)
- [ ] `/admin/tenant/role-mappings` — Tier 1 read-only + Tier 2 editable rows (ADR-003)
- [ ] `/admin/tenant/departments` — reads from ERP; redirect from `/admin/departments` (ADR-005)
- [ ] `/admin/tenant/custody-groups` — reads from ERP; redirect from `/admin/custody` (ADR-004)
- [ ] Tenant Setup checklist widget on `/admin/dashboard`

### Group 3d — Form Editor Improvements (ADR-007)

- [ ] `FormJsEditor.tsx` — fetch tenant component allowlist; pass `createPaletteFilterModule(allowedTypes)` to `FormEditor`
- [ ] `FormJsEditor.tsx` — fetch `CSS_THEME` config vars; apply as inline style on `.fjs-container`
- [ ] `lib/forms/createPaletteFilterModule.ts` — deregisters non-allowed types on `form.init`
- [ ] `GET /api/v1/config/form-components` — hardcoded default allowlist; no admin UI (deferred post-demo)

### Design System Foundation

- [ ] Tailwind config: primary purple palette, dark sidebar tokens, badge colour map
- [ ] CSS custom properties: `--sidebar-bg`, `--primary`, `--primary-foreground`, `--badge-*`
- [ ] Shared components: `StatCard`, `FilterPills`, `StatusBadge`, `PriorityBadge`, `AvatarCell`

### Navigation Overhaul

Full sidebar structure (role-gated):

```
GENERAL          (all roles)
  Dashboard
  My Tasks
  My Requests
  Service Catalog          ← new

DESIGN STUDIO    (WORKFLOW_ADMIN, ADMIN, SUPER_ADMIN)
  Processes
  Forms
  Decisions
  Email Templates          ← move from Admin; already built (S28.9)

ADMIN            (ADMIN, SUPER_ADMIN)
  Connectors               ← existing

TENANT SETUP     (ADMIN, SUPER_ADMIN)
  Approval Authority       ← ADR-002
  Role Mappings            ← ADR-003
  Departments              ← ADR-005
  Custody Groups           ← ADR-004
MONITORING       (ADMIN, SUPER_ADMIN — M6)
  Analytics Dashboard
  Process Health
```

- [ ] Dark sidebar: five sections with icon + label nav items; role-gated visibility per section
- [ ] Move Email Templates nav item from Admin → Design Studio section
- [ ] User profile card at sidebar bottom (avatar, name, role)
- [ ] Notification bell + user avatar in top-right header

### Screen Overhaul

- [ ] **Service Catalog** (new) — card grid of available processes; category filter pills; step tags; working days estimate; Submit Request CTA
- [ ] **My Tasks** — stat cards row; All/Mine/Overdue/Unassigned tabs; task table with assignee avatar, priority badge, status badge, due date, View/Claim actions
- [ ] **My Requests** — request list with status tracking
- [ ] **Forms** — stat cards; tabs; search; category pills; table with Form/Process/Fields/Submissions/Status/Updated; inline actions
- [ ] **Processes** — stat cards; Deployed/Drafts tabs; card grid; active instances + versions; Start Workflow + action icons
- [ ] **Decisions** — aligned to Forms list pattern
- [ ] **Email Templates** — move to Design Studio section; existing list + editor UI unchanged
- [ ] **Dashboard** — overview cards, recent activity, quick actions, Tenant Setup checklist widget
- [ ] **Connectors** — aligned to new table pattern
- [ ] **Tenant Setup sub-pages** — Approval Authority, Role Mappings, Departments, Custody Groups (see Group 3a above)

### Editor CSS Theming

- [ ] **bpmn-js** — canvas bg, toolbar buttons, properties panel bg/text (~3h)
- [ ] **form-js** — container bg, field labels/inputs, buttons, palette panel (~2h)
- [ ] **dmn-js** — table header bg, cell borders, toolbar, hit policy badges (~1h)
- [ ] All three: inject primary color + font via CSS custom properties; no JS internals

---

## M5 — ADR Signal Events

**Deps**: M3 complete
**Estimate**: 6–8 hours

- [ ] Procurement process: `IntermediateThrowEvent(Signal)` after final approval — uses `TenantAwareSignalService`
- [ ] Asset request process: `IntermediateCatchEvent(Signal)` for `procurementApproved`
- [ ] All approval UserTasks: non-interrupting Timer boundary (PT48H → reminder)
- [ ] All approval UserTasks: interrupting Timer boundary (PT72H → escalate)
- [ ] All external-call service tasks: Error boundary event with fallback flow

---

## M6 — Analytics + Basic Monitoring

**Deps**: M3 (Flowable history + config tables)
**Parallel-safe**: alongside M4/M5
**Estimate**: 12–14 hours

- [ ] Backend: process execution stats, task metrics, user/group workload (all < 1s for 100k+ instances)
- [ ] Frontend Analytics Dashboard (`/admin/analytics`): overview stat cards, line chart (executions over time), bar chart (by status), task bottleneck table, SLA dashboard, CSV/PDF export
- [ ] Frontend Process Health (`/admin/monitoring`): active instance count, SLA at-risk list (dead-letter UI now in M2)
- [ ] Add Monitoring sidebar section (ADMIN/SUPER_ADMIN): Analytics Dashboard + Process Health links
- [ ] Health check endpoints on all services (`/actuator/health`)
- [ ] Troubleshooting runbook

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
| AI Gateway (S30) | Post-June |
| Vertical Workflow Templates (advanced) | Post-June |
| OSS release tasks | Parked — see werkflow-public/docs/Roadmap.md |

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
| ADR Session | ADR-002 through ADR-009 written (2026-04-28) |
| M2 | Engine QW (ADR-009) + Form types (ADR-007) + Signal scoping (ADR-008) + async history + DB indexes + dead-letter UI |
