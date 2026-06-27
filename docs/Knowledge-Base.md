# Knowledge Base

> Auto-generated. Do not edit directly.
> Last synced: 2026-06-27 | ADRs: see docs/adr/index.md in platform repo (34) | Sources: CLAUDE.md, docs/Roadmap.md, docs/Architecture/, docs/adr/ (platform), superpowers/specs, superpowers/plans

---

## 1. Project Identity

**Werkflow Enterprise** — BPMN/DMN/CMMN process automation platform with a visual designer and form builder. Pre-MVP, targeting an internal demo / MVP release cut (Droplet-blocked).

**Stack**
- Engine: Spring Boot + Flowable 7.2 (multi-tenant, JWT-secured); Flyway engine migrations at V30
- Admin service: Spring Boot (connector catalog, PSS/DTDS design-time, DMN, tenant config, org directory)
- Portal: Next.js 14 (App Router, next-auth v5, Keycloak OIDC, React Query)
- Auth: Keycloak 26 (realm `werkflow`; clients `werkflow-portal`/`werkflow-engine`/`werkflow-admin`); JWT carries `realm_access.roles`
- Credentials: OpenBao per-tenant credential store (AES paths removed; secretRef model)
- DB: PostgreSQL 15 (schema-per-service: `engine`, `admin_service`)

**Repos**
- `werkflow-enterprise` — engine + admin + portal (this repo)
- `werkflow-erp` — ERP domain services (procurement, inventory, finance, etc.)
- `werkflow-public` — OSS release track

**Key constraints**
- Multi-tenant: all queries tenant-scoped; tenant from JWT via `JwtClaimsExtractor.getTenantCode` (null/blank normalised to `default`); task ops keyed on `jwt.getSubject()` (sub UUID), not `preferred_username`
- Form schemas strictly tenant-scoped, no fallback-to-default (ADR-032)
- No external message broker for internal process events (ADR-008); signals modelled in BPMN under tenant-scoped deployments, never bare `runtimeService.signalEventReceived()`
- ERP services must have zero build-time Werkflow dependency (ADR-001)
- Schema-per-service: admin never queries engine tables directly

---

## 2. Architecture Decisions

ADRs are tracked in `docs/adr/index.md` (auto-maintained by adr-manager) in the **platform repo** (`~/Projects/werkflow-platform/docs/adr/`); records in `docs/adr/ADR-NNN-*.md`. This Knowledge Base may cite ADR numbers in implementation notes but holds no decision records. As of this sync the registry holds 34 ADRs (ADR-001 through ADR-034). This repo has no local `docs/adr/`.

---

## 3. Domain Knowledge

### Form Schemas + Tenant Scoping (D1/D2)
*Source: ADR-032, ADR-033, sessions 42–43 (2026-06-24/25)*

- `form_schemas` strictly tenant-scoped: V29 composite `UNIQUE(form_key,version,tenant_id)`; every read/write/list/version query filters `tenant_id` with no fallback-to-default (ADR-032)
- Tenant source: JWT (`getTenantCode`) for HTTP; `ProcessDefinition.getTenantId()` for start-form; `Task.getTenantId()` for task-form; deploying tenant for deploy-time pin/validate — all normalised null/blank to `default`
- `@Cacheable` form key must normalise tenant; per-tenant seed path writes copies under each tenant
- Deploy fail-loud (ADR-033, amends ADR-026): deploy throws aggregate `422 {missingForms, missingDecisions}` on dangling form/DMN refs via `DeployReferenceValidator` on both deploy paths
- form-js (`@bpmn-io/form-js` 1.19): date fields are `type:"datetime"+subtype:"date"` (bare `type:"date"` silently fails); headless `expression` field computes a FEEL value into a key (enable in `FormSchemaValidator.VARIABLE_TYPES` + `FormJsEditor` allowedTypes); FEEL date diff idiom `(date(end)-date(start))/duration("P1D")+1`

### Example Seeding (logical unit)
*Source: ADR-026/031, example-seeding sessions 40–42*

- `ProcessExampleDeployer` seeds BPMN + form + DMN + display-name atomically per tenant; deploy-time `validateIdMatchesKey`
- Shared `BpmnFormRefExtractor.extractFormRefs()` used by both deploy and seed paths (DRY)
- Seed library (session 42): dropped finance-approval, added it-helpdesk-ticket (first real XML `bpmn:sendTask`, exposing the `WerkflowSendTaskXMLConverter` parse fix; ADR-015 amended); V30 `ON CONFLICT` must be composite `(template_key,tenant_id)`
- Seed forms de-bloated (session, 2026-06): leave 27→5 fields (derive `leaveDays` via form expression), capex 22→8, procurement wired to `procurement_matrix` DMN-gated single approval; cross-check form keys against DMN inputs AND verify the DMN is actually wired in the BPMN

### Connector Catalog + DTDS (M4.5–M4.7)
*Source: docs/superpowers/specs/, M4.5–M4.7 implementation*

- `ConnectorDefinitionV2` — JSONB entity in admin DB; envelope has `transport.type` (`rest`, `webhook`, `database`), `operations[]`, `tags[]`
- DTDS endpoints (`/api/v1/design/connectors/**`) — read-only design-time data consumed by portal BPMN/Form/DMN designers
- Webhook connector definition includes `transport.webhook.events[]`: each event has `messageName`, `correlationVariable` (Flowable Message correlation)
- Org/department directory read via connector key `org-directory` (renamed from `hr-portal`; `ErpMetadataReader`)

### Credentials (OpenBao, M4.12 Phase B)
*Source: ADR-024, M4.12 B.1a–B.6*

- Per-tenant secrets in OpenBao via `credentialRef` (slug); engine resolves the connector's own credential server-side (ADR-024 Model A), closing connector-mode no-auth gap
- HTTP / DB / connector credential impls behind `CredentialType` + registry; `EncryptionService` (last AES path) deleted
- Admin `/credential-binding` endpoint exposes slug + label only, never secrets; edit = rotate-all (industry-standard for pure secret stores)

### BPMN Designer
*Source: docs/Architecture/, superpowers/specs/2026-04-26-bpmn-panel-smart-dropdowns.md*

- Properties panel extension `flowable-properties-provider.ts` — action blocks, assignment, form key, signal/message events, DMN decision ref, script, service delegate
- DMN bound as `serviceTask flowable:type="dmn"` + `decisionTableReferenceKey` (NOT businessRuleTask — Flowable routes that to Drools; ADR-026)
- `moddleExtensions` required for `flowable:*` attributes; `WerkflowDeadExtensionAttrValidator` hard-rejects 4 dead `flowable:*` attrs at deploy
- Action block auto-morphs generic `Task` → `ServiceTask`; in-scope variable picker surfaces form-field variables (`ProcessVariableScopeService`)
- RestrictedExpressionManager (ADR-013) wired at FlowableConfig — EL expression security

### Platform Semantics Service (M4.4)
*Source: PSS implementation*

- 9 endpoints under `/api/v1/pss/`: capabilities, locale, candidate-groups, process-metadata, tags, config-vars, custody-vars, feel-catalog, visibility-policy
- All endpoints use `@AuthenticationPrincipal Jwt jwt` only — no `?tenantCode=` param
- `CandidateGroupsAggregator` merges Tier 1 (engine YAML) + Tier 2 (DB); excludes `_APPROVER` suffix groups

### DOA / Approval Authority
*Source: ADR-002, ADR-003, ADR-025, ADR-027/029*

- Thresholds stored as `ConfigurationVariable` (type `DOA_THRESHOLD`); `DmnConfigVariableInjector` injects `configVars` + `custodyVars` as FEEL variables
- Canonical approval contract is the `decision` variable (`${decision == 'approve'}`, ADR-025); approval escalation cluster shipped (ADR-027) with config-sourced DOA + jam-proofed escalate UI
- Custody groups from ERP resolve to Flowable `candidateGroups` at task-creation time

---

## 4. Implementation History

| Milestone | Description | Status | Key Commits |
|-----------|-------------|--------|-------------|
| M1 — ERP APIs | custody, dept, user-profile APIs (281 tests) | Complete | c6f9abf–d1bedb7 |
| M2 — ADR Foundation | form types, signal scoping, async history, indexes | Complete | a2b53ce, f048a98 |
| M3 — ADR Core | FlowableGroupResolver, configVars, BPMN action blocks, custody→ERP | Complete | de0f6c9 |
| M4 — UI Overhaul | dark sidebar, screen rewrites, editors, analytics dashboard | Complete | 7aac042 |
| M5 — Signal Events | BPMN signal throw/catch, timer + error boundaries | Complete | 00e04aa |
| M6 — Analytics | process-stats, task-metrics, health endpoints | Complete | b1c9f15, 7aac042 |
| M4.4 — PSS + Categorization | 9 PSS endpoints; ADR-010 categorization model | Complete | 2315179 |
| M4.5 — DTDS Core | ConnectorDefinition envelope, DTDS endpoints, portal hooks | Complete | (pushed) |
| M4.6 — Webhook + Facades | engine webhook receiver, HMAC, replay, correlator; Form/DMN facades | Complete | dee91d3, d5dd96a |
| M4.7 — DB Connector | database adapter + connector generators | Complete | (pushed) |
| M4.11 P3 — BPMN-native coverage | 11/11 P3 elements audited; ADR-012/013/019–022 | Complete | 972c887 |
| M4.12 — Credentials → OpenBao | Phase A + B.1a–B.6; secretRef model; AES removed; ADR-023/024 | Complete | (B.6 7f4f9e9) |
| M4.13 — Event-type audit | Link blocked at deploy; signal/message panels using stock bpmn-js | Complete | cfd01bb |
| M4.14 — Per-tenant seeding | classpath example seeding; ExampleSeedClient S2S; ADR-031 | Complete | (pushed) |
| Item 7 — Approval escalation | BPMN + engine + 7c-UI; config-sourced DOA | Complete (ADR-027/029) | ce45d402 |
| Item 11/13/14 — Test harness + validators | process test harness; dead-attr validator; capex DMN groups | Complete | 5e33822, b5c7092, 84c40a2 |
| Sessions 42–43 — D1/D2 + seed reshuffle | form tenant scoping (V29); deploy fail-loud (422); seed library (V30); CI greening | Complete | 4d7949fe, dd335a63 |
| Session (cont. 2026-06-27) — pre-MVP hardening | blast-radius Phase 1/2 (formKey@N, S2S/portal 401 resilience, ADR-034); seed-form de-bloat; departments→`org-directory`; env_file hardening; realm-file reconciliation | Complete | acf37212, 863dea62, 8682a3de |

---

## 5. Current State

**Phase**: Pre-MVP — Blast-Radius Phase Plan + Manual E2E, then MVP release cut (Droplet-blocked).
**Branch**: `main`, HEAD `8682a3de`.
**Operational**: dev DB at engine V30; docker stack boots from `.env.shared` (compose `environment:` overrides removed so `*_CLIENT_SECRET` are owned by env_file); single canonical realm file `infrastructure/keycloak/realms/werkflow-realm.json`.

**Done this session (cont. 2026-06-27)**
- Blast-radius Phase 1: formKey@version resolution verify, assignee vs `flowable:assignee` verify, DRY `BpmnFormRefExtractor.extractFormRefs()` (39af596f)
- Blast-radius Phase 2: portal silent-401 refresh + Engine→Admin S2S retry/evict resilience (ADR-034; a0f69e03, e87cc3eb)
- Manual-E2E fixes: V24–V26 act_* cleanup guards (64be6232); dashboard task-count vs list + `process` default-key fixes (5419b075, ed05836c)
- Seed forms de-bloated (leave/capex/procurement) with DMN alignment
- Departments connector key `hr-portal` → `org-directory` (863dea62)
- Env/realm hardening: env_file secret ownership (acf37212); realm-file reconciliation + setup docs (8682a3de)

**Next**
- Blast-radius **Phase 3**: remove `flowable-assignment` group from UserTask panel (M4.9 Task 1/5) + decide Custody Groups panel visibility (Task 4)
- Then Manual E2E, then MVP release cut (Droplet-blocked)

---

## 6. Open Decisions

Pointer only. Proposed / not-yet-active ADRs are tracked in the platform `docs/adr/index.md` ("Open / not-yet-active"); pending implementation work behind accepted ADRs and the engineering backlog live in `docs/Roadmap.md`. Notable open items: blast-radius Phase 3 designer-panel decisions (M4.9 Tasks 1/4/5); direct-assignee→sub field gap (parked, Option A recommended).

---

## 7. Tech Debt & Future Implications

| Item | Source | Priority |
|------|--------|----------|
| Webhook replay cache is single-node Caffeine — replace with Redis for multi-instance | ADR-011 | Pre-prod |
| HMAC secrets in env vars — rotation requires service restart | ADR-011 | Pre-prod |
| Kafka/RabbitMQ deferred — `transport: messaging` slot reserved in envelope | ADR-008 | Customer-driven |
| CMMN removed from demo scope — re-evaluate post-demo | ADR-001 | Post-demo |
| M7 CI/CD — release.yml, production compose, runbooks still pending | Roadmap M7 | Pre-release |

---

## 8. Docs Index

| Document | Purpose | Last Updated |
|----------|---------|--------------|
| `CLAUDE.md` | Dev guidelines, toolchain, milestone verification gates | 2026-06 |
| `docs/Roadmap.md` (enterprise) | Repo-level sprint tracking | 2026-06-25 |
| `~/Projects/werkflow-platform/docs/Roadmap.md` | Master milestone map | 2026-06 |
| `~/Projects/werkflow-platform/docs/adr/index.md` | 34 architecture decision records (canonical) | 2026-06 |
| `docs/Architecture/` | Design specs, delegate analysis, workflow guides | Various |
| `docs/superpowers/specs/` | Feature specs consumed by implementation | 2026-04/05 |
| `docs/Knowledge-Base.md` | This file | 2026-06-27 |
