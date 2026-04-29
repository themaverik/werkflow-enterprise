# Werkflow Enterprise — Roadmap

**Repo scope**: Enterprise-only engine, admin-service, and portal features
**Master Roadmap**: `~/Projects/werkflow-platform/docs/Roadmap.md` (authoritative for all future tasks)
**Last Updated**: 2026-04-29
**Target**: Internal Enterprise Demo — June 2026

> Future tasks in this file are synced from the master Roadmap. Do not add tasks here without adding them to master first.

---

## Current State

| Item | Status |
|------|--------|
| E2E quality gate | 7/7 specs passing |
| ADRs | ADR-001 through ADR-009 written |
| Active milestone | M2 — ADR Foundation + Performance (no deps, ready to start) |
| Blocked on | M3 waits for ERP M1 (P1.6.1–P1.6.3) |

---

## Active Milestones

### M2 — ADR Foundation + Performance

**Deps**: none — start now
**Estimate**: 12–14 hours

#### Engine Quick Wins (ADR-009)

- [ ] Add `parentDeploymentId` to `ProcessDeploymentService` for bundle deployments
- [ ] Add transient variables to `ExternalApiCallDelegate` — raw API response transient, masked response persists to DB
- [ ] Add task-local variables pattern to `ExternalApiCallDelegate` for parallel branch outputs

#### Form Field Types (ADR-007)

- [ ] Refactor `FormSchemaValidator.java` — four sets: `VARIABLE_TYPES (A)`, `DISPLAY_TYPES (B)`, `SERVICE_TYPES (C)`, `INVALID_TYPES (D)`
- [ ] `validateFormSchema()` — accepts A+B+C; rejects D with `400 Invalid component type`
- [ ] `validateFormData()` — skips B+C; returns `501` for C types on submit
- [ ] `TaskFormService.java` — filter Category B keys before `variablesToSave`; `dynamiclist` → Flowable `json` type

#### Signal Tenant Scoping (ADR-008)

- [ ] Create `TenantAwareSignalService` — wraps `signalEventReceivedWithTenantId` and async variant; injects `tenantId` from security context
- [ ] Enforce no direct calls to non-tenant `runtimeService.signalEventReceived()`

#### Performance

- [ ] Async history: `async-history-enabled: true`, batch size 100
- [ ] Async email: move sending to background `EmailJobHandler`
- [ ] Flowable query predicates + DB indexes on common task query columns
- [ ] Dead-letter job monitoring UI (view, retry, update before retry)

---

### M3 — ADR Core Implementation

**Deps**: M1 (ERP APIs) + M2 complete
**Estimate**: 24–30 hours

#### Group 2a — FlowableGroupResolver Simplification (ADR-003)

- [ ] Remove `doa_approver_level1/2/3/4` from `FlowableGroupProperties` YAML
- [ ] Remove `doaLevel` cumulative loop and compound group emission from `FlowableGroupResolver`
- [ ] Remove `adminServiceClient.getTenantDepartmentCodes()` and `getTenantCrossDeptThreshold()` calls
- [ ] Add `RoleGroupMapping` table to admin-service + Flyway migration
- [ ] Add `RoleGroupMappingService` with 5-min cache per tenant
- [ ] Add `GET/POST/DELETE /api/v1/config/role-mappings` endpoints
- [ ] Add `UserGroupLookupProxy` SPI wrapper

#### Group 2b — configVars Admin API (ADR-002)

- [ ] `GET/POST/PUT/DELETE /api/v1/config/vars` for tenant `ConfigurationVariable` entries
- [ ] Two-layer data: Level definitions (L1–L4 → amounts) + Role-to-level mapping (role → level)
- [ ] Remove `DoaThresholdService` and associated admin-service DOA threshold methods

#### Group 2c — BPMN Action Blocks (ADR-009)

- [ ] `SEND_NOTIFICATION` block — channel multi-select, template picker, recipient expression, condition expression
- [ ] `CALL_SUBPROCESS` block — process key picker, in/out variable mapping table
- [ ] `GROOVY_SCRIPT` block — inline script editor, admin-restricted
- [ ] `MANUAL_STEP` block — description field, confirmation required flag
- [ ] Remove `DmnEvaluationDelegate` from action block options
- [ ] Signal throw event: signal name enum picker (not free-text)
- [ ] `NotificationDelegate` — Slack/WhatsApp adapters or explicit `UnsupportedOperationException`

#### Group 3a — Tenant Setup UI (ADR-006)

- [ ] `Tenant Setup` sidebar section (ADMIN/SUPER_ADMIN guard)
- [ ] `/admin/tenant/approval-authority` — two-layer configVars UI: L1–L4 threshold amounts + role→level mapping (ADR-002)
- [ ] `/admin/tenant/role-mappings` — Tier 1 read-only + Tier 2 editable rows (ADR-003)
- [ ] `/admin/tenant/departments` — reads from ERP; redirect from `/admin/departments` (ADR-005)
- [ ] `/admin/tenant/custody-groups` — reads from ERP; redirect from `/admin/custody` (ADR-004)
- [ ] Tenant Setup checklist widget on `/admin/dashboard`

#### Group 3b — Custody Move to ERP (ADR-004)

- [ ] Remove custody DB table from admin-service
- [ ] Update portal `/admin/tenant/custody-groups` to call ERP `GET/POST/PUT/DELETE /api/v1/custody-mappings`
- [ ] Add `custodyVars` context builder in `DmnConfigVariableInjector` (5-min cache per tenant)

#### Group 3c — Department Simplification (ADR-005)

- [ ] `SetOwningDepartmentDelegate` — resolves submitter ERP dept → `owningDepartment` variable; fallback to form value
- [ ] `FlowableGroupResolver` Step 4: fetch user ERP dept → emit `${deptCode}_APPROVER`
- [ ] Remove `departments` table from admin-service
- [ ] Department-scoped query filter in `TaskService` and `ProcessMonitoringService` (ERP-enabled guard)

#### Group 3d — Form Editor Improvements (ADR-007)

- [ ] `FormJsEditor.tsx` — fetch tenant component allowlist; pass `createPaletteFilterModule(allowedTypes)` to `FormEditor`
- [ ] `FormJsEditor.tsx` — fetch `CSS_THEME` config vars; apply as inline style on `.fjs-container`
- [ ] `lib/forms/createPaletteFilterModule.ts` — deregisters non-allowed types on `form.init`
- [ ] `GET /api/v1/config/form-components` — hardcoded default allowlist; no admin UI (deferred post-demo)

---

### M4 — UI Full Visual Overhaul

**Deps**: M3 complete (Tenant Setup + config screens must exist)
**Estimate**: 20–24 hours
**Design handoff**: 2026-04-30

#### Design System Foundation

- [ ] Tailwind config: primary purple palette, dark sidebar tokens, badge colour map
- [ ] CSS custom properties: `--sidebar-bg`, `--primary`, `--primary-foreground`, `--badge-*`
- [ ] Shared components: `StatCard`, `FilterPills`, `StatusBadge`, `PriorityBadge`, `AvatarCell`

#### Navigation Overhaul

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

#### Screen Overhaul

- [ ] **Service Catalog** (new) — card grid of available processes; category filter pills; step tags; working days estimate; Submit Request CTA
- [ ] **My Tasks** — stat cards row; All/Mine/Overdue/Unassigned tabs; task table with assignee avatar, priority badge, status badge, due date, View/Claim actions
- [ ] **My Requests** — request list with status tracking
- [ ] **Forms** — stat cards; tabs; search; category pills; table with Form/Process/Fields/Submissions/Status/Updated; inline actions
- [ ] **Processes** — stat cards; Deployed/Drafts tabs; card grid; active instances + versions; Start Workflow + action icons
- [ ] **Decisions** — aligned to Forms list pattern
- [ ] **Email Templates** — move to Design Studio section; existing list + editor UI unchanged
- [ ] **Dashboard** — overview cards, recent activity, quick actions, Tenant Setup checklist widget
- [ ] **Connectors** — aligned to new table pattern
- [ ] **Tenant Setup sub-pages** — Approval Authority, Role Mappings, Departments, Custody Groups, Form Components (all new — M3 Group 3a)

#### Editor CSS Theming

- [ ] **bpmn-js** — canvas bg, toolbar buttons, properties panel bg/text (~3h)
- [ ] **form-js** — container bg, field labels/inputs, buttons, palette panel (~2h)
- [ ] **dmn-js** — table header bg, cell borders, toolbar, hit policy badges (~1h)
- [ ] All three: inject primary color + font via CSS custom properties; no JS internals

---

### M5 — ADR Signal Events

**Deps**: M3 complete
**Estimate**: 6–8 hours

- [ ] Procurement process: `IntermediateThrowEvent(Signal)` after final approval — uses `TenantAwareSignalService`
- [ ] Asset request process: `IntermediateCatchEvent(Signal)` for `procurementApproved`
- [ ] All approval UserTasks: non-interrupting Timer boundary (PT48H → reminder)
- [ ] All approval UserTasks: interrupting Timer boundary (PT72H → escalate)
- [ ] All external-call service tasks: Error boundary event with fallback flow

---

### M6 — Analytics + Basic Monitoring

**Deps**: M3 (Flowable history + config tables)
**Parallel-safe**: alongside M4/M5
**Estimate**: 12–14 hours

- [ ] Backend: process execution stats, task metrics, user/group workload (all < 1s for 100k+ instances)
- [ ] Frontend Analytics Dashboard (`/admin/analytics`): overview stat cards, line chart (executions over time), bar chart (by status), task bottleneck table, SLA dashboard, CSV/PDF export
- [ ] Frontend Process Health (`/admin/monitoring`): dead-letter job UI (view, retry), active instance count, SLA at-risk list
- [ ] Add Monitoring sidebar section (ADMIN/SUPER_ADMIN): Analytics Dashboard + Process Health links
- [ ] Activate existing analytics sidebar link (commented since S23)
- [ ] Health check endpoints on all services (`/actuator/health`)
- [ ] Troubleshooting runbook

---

### M7 — CI/CD + Production Readiness

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

## Historical Summary — Completed (S21–S28.10)

| Sprint | Highlights |
|--------|-----------|
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
