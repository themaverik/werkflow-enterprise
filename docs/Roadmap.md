# Werkflow Enterprise — Roadmap

**Repo scope**: Enterprise-only engine, admin-service, and portal features
**Master Roadmap**: `~/Projects/werkflow-platform/docs/Roadmap.md` (authoritative for all future tasks)
**Last Updated**: 2026-06-27 (session 44 — Blast-Radius Phase 3 closed; documentation debt)
**Target**: Internal Enterprise Demo — June 2026

> Future tasks in this file are synced from the master Roadmap. Do not add tasks here without adding them to master first.

---

## Current State

| Item | Status |
|------|--------|
| E2E quality gate | 7/7 specs passing (full milestone-end re-run = master item 9, last) |
| ADRs | ADR-001 through ADR-034 (in master `docs/adr/`) — latest: ADR-032 (form tenant scoping), ADR-033 (deploy fail-loud), ADR-034 (token-lifecycle resilience) |
| M4.11 / M4.12 | Complete (P3 11/11; Phase A + B.1a–B.6 + B.4/B.5-portal + item 8 sidebar gate) |
| Tier 1–3 done | All Tier-1 (mechanical cleanups, facade hardening, schema hygiene); item 7 approval-escalation cluster (BPMN + engine + 7c-UI, ADR-027) shipped |
| Active milestone | Pre-MVP — Blast-Radius Phase Plan + Manual E2E (per master Roadmap) |
| Done (sessions 42–43, 2026-06-25) | D2 form tenant scoping (ADR-032, V29); D1 deploy fail-loud (ADR-033, aggregate 422); seed library reshuffle (finance-approval→it-helpdesk, V30, ADR-015 amended for `WerkflowSendTaskXMLConverter`); CI greening + artifact-upload gating (`d7b4ec66`, `dd335a63`). All merged+pushed to enterprise main `dd335a63`; live-verified on dev stack. |
| Next | Blast-Radius **Phase 1 ✅**, **Phase 2 ✅** (ADR-034) DONE+PUSHED. **Phase 3 ✅ closed 2026-06-27 as verified no-op** — re-verify found M4.9 Tasks 1/4/5 already resolved by the M4.10 VariableComboBox refactor (`290ae067`); no code. Remaining: **Documentation Debt** (in progress) then Manual E2E, then MVP release cut (Droplet-blocked). |
| Branch | `main` — HEAD `ddb9c69b` |
| Operational | Dev DB at engine V30. Compose secrets now owned by `.env.shared` (env_file), not `environment:` overrides. **Before Manual E2E rebuild `engine-service` + `admin-service` + `portal`** — all three have source changes since `dd335a63`. Single canonical realm file `infrastructure/keycloak/realms/werkflow-realm.json`. |

### Session log (collapsed — detail in git history + docs/adr/ + Knowledge-Base)

- **Session 18** (2026-06-01, `e09e023`→`e694c1e` + platform `8b1674f`, pushed) — portal UX + engine semantics: ProcessExampleDeployer reset-on-startup, back-nav, "Start Process Anyway" fallback, V14 bare-date fix, no-form Submit hide, `FormNotFoundException`→404.
- **Session 19** (2026-06-01, `515e7d3`→`ecd5d47`) — DMN+connector+notification indicators (Option 2: V15 `process_indicators` + `BpmnIndicatorScanner`), marketplace UI, `DmnExampleDeployer` (V16), Flowable autoscan fix (renamed `dmn/`→`dmn-examples/`).
- **Session 33** (2026-06-12, main `d1f8a28`; branch `feature/portal-design-audit` `ad5bd1c`→`29c1b15`) — portal design audit sprint, all 20 `docs/portal-design-audit.md` items; 4 MEDIUM security fixes.
- **Session 41** (2026-06-22, `491b89f0`/`3214d5a8`/`ca17ba26`/`97da020b`/`4876f17c`/`7f23b1a1`) — manual E2E prep docs (`BPMN-Symbol-Reference.md`, `Manual-E2E-Test-Plan.md`), logical-unit enforcement, V24–V26 cleanup migrations, 4 latent bug fixes (BPMN XSD element-order, `BpmnFormKeyValidator` classpath, form display-name INSERT, idempotency).
- **Session 41 end-of-day** (2026-06-22, `a5b6195a`/`cec630d6`/`48d17356`/`7dcc561f`/`6426ec5d`/`04c21036`, pushed) — UI consistency hardening (`.wf-card-interactive`, datasource/services/connectors/tenants/email-templates) + form-rendering reliability stack (form-js `id` field, `validateIdMatchesKey`, `FormJsViewer` error visibility, email→textfield+validationType, V27/V28).
- **Session 42** (2026-06-24, `4d7949fe` + `04924b61`) — D2 form tenant scoping (ADR-032, V29) + D1 deploy fail-loud (ADR-033, aggregate 422, `DeployReferenceValidator`).
- **Session 43** (2026-06-25→27, pushed through `8682a3de`) — pre-MVP hardening: Blast-Radius Phase 1 (DRY `BpmnFormRefExtractor` `39af596f`; formKey@N + assignee verified-correct, no change) + Phase 2 (portal silent-401 `a0f69e03` + Engine→Admin S2S resilience `e87cc3eb`, ADR-034); V24–V26 act_* cleanup guards (`64be6232`); manual-E2E bug fixes (dashboard task-count vs list, `process` default-key); seed-form de-bloat (leave 27→5 / capex 22→8 / procurement DMN-gated `edca170f`/`e378fe69`/`9faa565b`); in-scope form-variable picker (`ProcessVariableScopeService`); departments connector `hr-portal`→`org-directory` (`863dea62`); env_file secret ownership (`acf37212`); Keycloak realm-file reconciliation + setup docs (`8682a3de`); Knowledge-Base sync (`91d1c8d9`).

---

## Documentation Debt (Pre-MVP)

Surfaced during the 2026-06-27 realm-file reconciliation (`8682a3de`) and Knowledge-Base sync (`91d1c8d9`).

- [x] **Rewrite `infrastructure/keycloak/README.md` against the realm JSON** *(2026-06-27)* — re-derived from canonical `realms/werkflow-realm.json`: correct clients (`werkflow-portal`/`werkflow-engine`/`werkflow-admin`), env-var client secrets (no hardcoded values), the actual 37 realm roles, removed the 6 phantom department groups (realm has none — role-based), added KC 26 version + Google IdP + `tenant_id` attribute. Also stubbed the superseded `REALM_SETUP.md` (removed stale manual flow + committed dev secrets) to point at the README.
- [x] **Normalise the Knowledge Base filename to the hyphen convention** *(2026-06-27)* — `git mv docs/Knowledge_Base.md → docs/Knowledge-Base.md`; updated all cross-refs (this Roadmap's session log + the KB's own docs-index/tech-debt rows). doc-sync already targets the hyphen name, so no dangling auto-generated target.

---

## M2 — ADR Foundation + Performance ✅ COMPLETE

Committed 2026-04-30 (`a2b53ce`/`f048a98`/`5ab62b4`) — engine quick wins (ADR-009), form field types (ADR-007), signal tenant scoping (ADR-008, `TenantAwareSignalService`), async history, `FlowableIndexCreator` (6 indexes), dead-letter job UI. Deferred: async email via Flowable `EmailJobHandler` (`@Async+@Retryable` already in place).

---

## M3 — ADR Core Implementation ✅ COMPLETE

Committed 2026-04-30 — Group 2a FlowableGroupResolver simplification + `RoleGroupMapping` (ADR-003, `9dae8d8`); Group 2b configVars admin API + level/role two-layer (ADR-002, `5d4e16c`); Group 2c BPMN action blocks (SEND_NOTIFICATION / CALL_SUBPROCESS / GROOVY_SCRIPT / MANUAL_STEP, ADR-009, `4ce7b89`); Group 3b custody → ERP (ADR-004, `3cb362a`); Group 3c department simplification + `SetOwningDepartmentDelegate` (ADR-005, `de0f6c9`). (Groups 3a/3d moved to M4.)

---

## M4 — UI Full Visual Overhaul + Tenant Setup + Form Editor + Analytics UI ✅ COMPLETE

Complete 2026-05-01, branch `feature/m4-ui-overhaul`, final commit `7aac042`. Covered: Group 3a Tenant Setup UI (ADR-006, `9d1d88a`/`54678f4`/`cbe28db`); Group 3d Form Editor (`FormJsEditor` palette filter + CSS theme, ADR-007, `c5fc003`); design-system foundation (Tailwind purple palette, shared `StatCard`/`StatusBadge` etc.); dark sidebar nav overhaul; full screen overhaul (Service Catalog/My Tasks/My Requests/Forms/Processes/Decisions/Dashboard/Connectors/Tenant Setup, `8f62118`/`06866d5`); editor CSS theming for bpmn-js/form-js/dmn-js (`05843e8`).

> **Design reference**: implement screens against approved Figma-export HTML at `/Users/lamteiwahlang/Projects/Werkflow Redesigned Final/`. Open the relevant HTML file before implementing any screen; derive colours/spacing/typography from it.

**Carry-forward `[~]`/`[ ]` items (not complete):**

- [~] `/admin/tenant/departments` — reads from ERP; redirect from `/admin/departments` (ADR-005) — page exists, redirect pending
- [ ] Tenant Setup checklist widget on `/admin/dashboard`

---

## M5 — ADR Signal Events ✅ COMPLETE

Committed 2026-04-30 (`00e04aa`) — procurement throw / asset-request catch signals via `TenantAwareSignalService`; non-interrupting (PT48H reminder) + interrupting (PT72H escalate) timer boundaries on approval UserTasks; error boundary + fallback on external-call service tasks.

---

## M6 — Analytics + Basic Monitoring ✅ COMPLETE (Group A + B)

Committed Group A `b1c9f15` · Group B `7aac042` — backend process/task metrics (<1s for 100k+ instances), Analytics Dashboard (charts + SLA + CSV export), Monitoring sidebar, `/actuator/health` via portal proxy.

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

> **Resolution 2026-06-27 (Blast-Radius Phase 3 re-verify):** Tasks 1, 4, 5 are RESOLVED — already overtaken by the M4.10 VariableComboBox refactor (`290ae067`), which post-dates this list. No code change; closed as verified no-op (no-side-effects rule). Tasks 2 and 3 were also superseded (FEEL assist moved to `ExpressionBuilder`; Artifact Metadata is process-level-scoped).

- [x] **Task 1** — RESOLVED (verified): the standard BPMN `flowable-assignment` group was removed from `flowable-properties-provider.ts` in M4.10 (`290ae067`). `HumanApprovalSection.tsx` (React sidebar) is the single authoritative assignment UI.
- [ ] **Task 2** — Move the 4-section suggestion panel (Process Variables, Custody Lookups, Business Tier 2, System Tier 1) to assist the Assignee Expression FEEL field in Action Block → HUMAN_APPROVAL. Currently wired to the wrong field (`candidateGroups`).
- [ ] **Task 3** — Scope Artifact Metadata panel to process level only (no element selected). Hide when any element is active on the canvas.
- [x] **Task 4** — RESOLVED (verified): the standalone always-on Custody Groups reference panel no longer exists. Custody groups are now *contextual* — a source inside the Candidate Groups combobox (UserTask/HUMAN_APPROVAL only) and a group inside `ExpressionBuilder` (sequence-flow conditions only).
- [x] **Task 5** — RESOLVED (verified): the `flowable-assignment` group (Assignee, Candidate Users, Candidate Groups text fields) is already absent from `flowable-properties-provider.ts` (removed in `290ae067`).
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
| Portal UI: `StatusPill` → `StatusBadge` (`forms`, `decisions` pages) | Post-demo polish — residual from M4 UI handover (B-5); monitoring already migrated |
| Portal UI: raw `<table>` → shadcn `Table` (~10 pages incl. FormSection, approval-authority, custody-mappings, role-mappings, categories, dead-letter, workflows, decisions, analytics) | Post-demo polish — residual from M4 UI handover (B-8); the 4 admin targets already migrated |

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

## M4.4 — Platform Semantics Service + Categorization (ADR-010) ✅ COMPLETE

**Reference**: Master Roadmap M4.4 / [`docs/M4.4-Platform-Semantics-Service-FINAL.md`](~/Projects/werkflow-platform/docs/M4.4-Platform-Semantics-Service-FINAL.md)

Engine refactor (removed `FlowableGroupResolver` Step 4 `${deptCode}_APPROVER`, ADR-010); PSS backend — `PlatformSemanticsController` (9 endpoints under `/api/v1/design/platform/`) + aggregators/projectors + Caffeine cache; schema migrations (`category` V16, `is_manager_tier` V17, engine V5 dept/category/tags); frontend designers + Categories/Visibility-Policy admin pages; security fixes C-1/C-2/H-1/H-2 (tenant from JWT only, parameterized LIKE, audit principal, server-side role gate).

---

## M4.4a — Process Custody UI Cleanup ✅ COMPLETE (7accb93)

Rename `/admin/tenant/custody-groups` → `/admin/tenant/custody-mappings` + redirect; process cards surface department/category/tags read-only; glossary disambiguating runtime routing vs definition governance.

---

## M4.4b — Currency Standardization ✅ COMPLETE (7accb93)

`ConfigurationVariable` type=LOCALE; PSS `GET /api/v1/design/platform/locale` + `LocaleProjector` (USD default); `formatCurrency`/`formatDmnThreshold` utilities; Tenant Setup → Locale admin page.

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
