# Knowledge Base

> Auto-generated. Do not edit directly.
> Last synced: 2026-05-10 | ADRs: 11 | Sources: CLAUDE.md, CHANGELOG.md, docs/Architecture/, docs/adr/ (platform), superpowers/specs, superpowers/plans

---

## 1. Project Identity

**Werkflow Enterprise** — BPMN/DMN process automation platform for internal enterprise use. Targets an internal demo by June 2026.

**Stack**
- Engine: Spring Boot + Flowable 7 (multi-tenant, JWT-secured)
- Admin service: Spring Boot (connector catalog, PSS, DMN, tenant config, Flyway V1–V19)
- Portal: Next.js 14 (App Router, next-auth v5, Keycloak OIDC, React Query)
- Auth: Keycloak (realm: `werkflow`); JWT claims carry `realm_access.roles`
- DB: PostgreSQL 15 (schema-per-service: `engine`, `admin_service`)
- Migrations: Flyway (engine V1–V6, admin V1–V19)

**Repos**
- `werkflow-enterprise` — engine + admin + portal (this repo)
- `werkflow-erp` — ERP domain services (procurement, inventory, HR, capex)
- `werkflow-public` — OSS release track (parked until after demo)

**Key constraints**
- Multi-tenant: all queries tenant-scoped; `TenantContext` from JWT sub-claim
- No external message broker for internal process events (ADR-008)
- ERP services must have zero build-time Werkflow dependency (ADR-001)
- Schema-per-service: admin never queries engine tables directly

---

## 2. Architecture Decisions

| ID | Title | Status | Date |
|----|-------|--------|------|
| ADR-001 | S27 CMMN Scope Reduction | Accepted | 2026-04-20 |
| ADR-002 | DOA Threshold Configuration and DMN Variable Resolution | Accepted | 2026-04-27 |
| ADR-003 | Keycloak Role Simplification and Role-to-Group Mapping Strategy | Accepted | 2026-04-27 |
| ADR-004 | Custody Mapping Resolution | Accepted | 2026-04-27 |
| ADR-005 | Department-Scoped Routing and Visibility | Accepted | 2026-04-27 |
| ADR-006 | Tenant Configuration Screen Structure | Accepted | 2026-04-27 |
| ADR-007 | Form Field Types, Backend Handling, and Editor Customisation | Accepted | 2026-04-28 |
| ADR-008 | Event-Driven Architecture — Flowable Native Events | Accepted | 2026-04-28 |
| ADR-009 | BPMN Task Types, Action Blocks, and Process Design Patterns | Accepted | 2026-04-28 |
| ADR-010 | Department Simplification and Categorization Model | Proposed | 2026-05-06 |
| ADR-011 | Webhook Inbound + BPMN Message Event Correlation | Accepted | 2026-05-10 |

### By Domain

#### Auth & Security
- **ADR-003** — Keycloak roles map to Flowable candidate groups via two tiers: Tier 1 (YAML-configured in engine, read-only) and Tier 2 (DB-managed via admin Role Mappings UI). Groups ending `_APPROVER` are excluded from candidate group lists.
- **ADR-004** — Custody group resolution: ERP owns custody data; portal reads via `CandidateGroupsAggregator` which merges Tier 1 + Tier 2. `CustodyMappingService` in ERP bridges custody owners to Flowable group names.

#### Services & Integration
- **ADR-008** — Flowable native BPMN 2.0 events only; no external broker for internal choreography. All signal dispatch via `TenantAwareSignalService` (never call `signalEventReceived()` directly). Kafka/RabbitMQ deferred to P3.
- **ADR-011** — Inbound webhooks via `POST /api/v1/webhooks/{tenantCode}/{connectorKey}` (unauthenticated, HMAC-verified). Pluggable HMAC strategies: `none`, `generic`, `github`, `stripe`. Replay protection via Caffeine cache (2h TTL, single-node). Dead-letter table `webhook_undelivered` for uncorrelated events. ERP publisher `@ConditionalOnProperty` — zero build-time coupling.
- **ADR-001** (ERP) — ERP services never call Werkflow at build time. All Werkflow dependencies via `@ConditionalOnProperty` guards.

#### Data & Storage
- **ADR-002** — DOA thresholds stored as `ConfigurationVariable` (type `DOA_THRESHOLD`); injected into DMN FEEL context by `DmnConfigVariableInjector`. Custody variables (`custodyVars`) also injected. PUT to update, never POST a duplicate key (upsert semantics as of M4.6).
- **ADR-010** (Proposed) — Department simplified to a categorization label on process definitions; not a routing or visibility mechanism. ERP owns the org chart; admin owns process categorization.

#### Frontend
- **ADR-006** — Tenant configuration screen split into sub-routes under `/admin/tenant/`: Locale, Approval Authority, Role Mappings, Custody Mappings, Visibility Policy.
- **ADR-007** — Form field types backed by JSON Schema; Form.js editor (no additionalModules); `cancelled` guard for StrictMode double-invocation; CSS palette filter only.

#### Infrastructure & DevOps
- **ADR-001** (platform) — S27 CMMN scope reduction: CMMN removed from demo scope; Flowable engine configured `cmmn.enabled: false`.

#### Cross-cutting
- **ADR-005** — Department visibility scoping: process instances tagged with `departmentCode`; task queries filter by `candidateGroup` membership derived from Keycloak roles.
- **ADR-009** — Full BPMN task-type → action-block mapping: 9 types (UserTask, ServiceTask, SendTask, BusinessRuleTask, ScriptTask, CallActivity, ManualTask, ReceiveTask, SubProcess). All service-task variants use `ExternalApiCallDelegate` or named delegates via `flowable:delegateExpression`.

---

## 3. Domain Knowledge

### Connector Catalog (M4.5 / M4.6 / M4.7)
*Source: `docs/superpowers/specs/` + M4.5–M4.6 implementation*

- `ConnectorDefinitionV2` — JSONB entity in admin DB; envelope has `transport.type` (`rest`, `webhook`, `database`), `operations[]`, `tags[]`
- DTDS endpoints (`/api/v1/design/connectors/**`) — read-only design-time data; consumed by portal BPMN/Form/DMN designers
- Webhook connector definition includes `transport.webhook.events[]` array: each event has `messageName`, `correlationVariable` (maps to Flowable Message correlation)
- `werkflow-erp-events` connector seeded via V19 migration for `default` tenant; publishes `VendorStatusChanged` + `PurchaseOrderStatusChanged`
- Portal proxy: `/api/proxy/admin/[...path]` covers all admin endpoints including `/api/v1/design/*`

### BPMN Designer
*Source: `docs/Architecture/`, `docs/superpowers/specs/2026-04-26-bpmn-panel-smart-dropdowns.md`*

- Properties panel extension: `flowable-properties-provider.ts` — action blocks, assignment, form key, signal events, message events, DMN decision ref, script, service delegate
- Message (Webhook) group: connector picker (`flowable:webhookConnector`) + correlation expression (`flowable:correlationExpression`) on any element with `MessageEventDefinition`
- Signal dispatch: always via `TenantAwareSignalService`; never direct `runtimeService.signalEventReceived()`
- `moddleExtensions` required for `flowable:*` attributes; `flowable-moddle.json` in `lib/bpmn/`
- Action block auto-morphs generic `Task` → `ServiceTask` on type selection

### Platform Semantics Service (M4.4)
*Source: PSS implementation*

- 9 endpoints under `/api/v1/pss/`: capabilities, locale, candidate-groups, process-metadata, tags, config-vars, custody-vars, feel-catalog, visibility-policy
- All endpoints use `@AuthenticationPrincipal Jwt jwt` only — no `?tenantCode=` param
- `LocaleProjector`: reads `tenantLocale` ConfigVar (type `LOCALE`); falls back to USD defaults
- `CandidateGroupsAggregator`: merges Tier 1 (engine) + Tier 2 (DB); excludes `_APPROVER` suffix groups
- `TagProjector`: parameterized LIKE queries — never string concatenation

### DOA / Approval Authority
*Source: ADR-002, ADR-003, ADR-005*

- Thresholds stored as `ConfigurationVariable` with type `DOA_THRESHOLD`; key format `DOA_L{n}`
- `DmnConfigVariableInjector` injects `configVars` + `custodyVars` as FEEL variables into DMN execution context
- `ROLE_DOA_LEVEL` configvar maps Keycloak roles to DOA tier numbers
- Custody groups from ERP resolve to Flowable `candidateGroups` at task creation time

### Tenant Configuration UI
*Source: ADR-006, M4 implementation*

- `/admin/tenant/locale` — currency, date format, timezone (LOCALE configVar, upsert-safe POST)
- `/admin/tenant/approval-authority` — DOA threshold levels, role-level mapping
- `/admin/tenant/role-mappings` — Tier 2 role→group mapping (free-text group name, KC role dropdown)
- `/admin/tenant/custody-mappings` — custody owner → candidateGroups chip list (groups from PSS)
- `/admin/tenant/layout.tsx` — server-side `auth()` role gate; no per-page check needed

---

## 4. Implementation History

| Milestone | Description | Status | Key Commits |
|-----------|-------------|--------|-------------|
| M1 — ERP APIs | 281 tests; custody, dept, user profile APIs | ✅ Complete | c6f9abf–d1bedb7 |
| M2 — ADR Foundation | Form types, signal scoping, async history, indexes | ✅ Complete | a2b53ce, f048a98 |
| M3 — ADR Core | FlowableGroupResolver, configVars, BPMN action blocks, custody→ERP | ✅ Complete | de0f6c9 |
| M5 — Signal Events | BPMN signal throw/catch, timer + error boundaries | ✅ Complete | 00e04aa |
| M6A — Analytics | Process-stats, task-metrics, health endpoints | ✅ Complete | b1c9f15 |
| Security Fix Pass | 30 issues — JWT, tenant isolation, CORS, pagination | ✅ Complete | 6a09650 |
| M4 — UI Overhaul | Dark sidebar, 10 screen rewrites, editors, analytics dashboard | ✅ Complete | 7aac042 |
| M9 — Connector Runtime | V14 migration, EncryptionService, ConnectorCallDelegate, proxy | ✅ Complete | 9a1922a |
| M4.4 — PSS | 9 PSS endpoints, V16+V17 migrations, FlowableGroupResolver Step 4 removed | ✅ Complete | 2315179 |
| M4.4a — Custody UI | custody-groups→custody-mappings redirect, Terminology glossary | ✅ Complete | 7accb93 |
| M4.4b — Currency | LOCALE configVar, LocaleProjector, formatCurrency, Locale admin page | ✅ Complete | 7accb93 |
| M4.5 — DTDS Core | ConnectorDefinition envelope, DTDS endpoints, portal hooks | ✅ Complete | (pushed) |
| M4.6 — Webhook + Facades | Engine webhook receiver, HMAC, replay, correlator; Form/DMN facades; ERP publisher; portal message panel | ✅ Complete | dee91d3, d5dd96a |

---

## 5. Current State

**Active milestone**: M4.7 — Database Connector + Connector Generators
**Next**: `DatabaseConnectorDelegate`, named queries, OpenAPI ingestion as connector generator
**Branch to cut**: `feature/m4-7-db-connector` (from `feature/m4-6-webhook` merged to main)
**Flyway versions**: engine V7+ next, admin V20+ next, ERP procurement V29+ next

---

## 6. Open Decisions

| ID / Question | Source | Blocking? |
|---------------|--------|-----------|
| ADR-010 — Department simplification (Proposed) | `ADR-010-department-simplification.md` | No — categorization-only model already in use |
| M4.7 — DatabaseConnectorDelegate design: named queries vs raw SQL exposure | Roadmap M4.7 | Yes — blocks M4.7 start |
| M4.8 — Marketplace connector contribution model | Roadmap M4.8 | No — community-driven, deferred |
| M9 E2E close — smoke test after M4.8 | Roadmap | No — deferred to after M4.8 |

---

## 7. Tech Debt & Future Implications

| Item | Source | Priority |
|------|--------|----------|
| Webhook replay cache is single-node Caffeine — replace with Redis for multi-instance | ADR-011 | Pre-prod |
| HMAC secrets in env vars — key rotation requires service restart | ADR-011 | Pre-prod |
| `correlateAllWithResult()` fan-out unbounded — use unique correlation values per instance in BPMN design | ADR-011 | Design guidance |
| Kafka/RabbitMQ deferred — migration path to Zeebe available when needed | ADR-008 | P3 |
| CMMN removed from scope — re-evaluate post-demo if case management needed | ADR-001 | Post-demo |
| ADR-010 Proposed — department as categorization only; org chart fully in ERP | ADR-010 | Confirm before M4.7 |
| M9 E2E close deferred — connector runtime smoke test still pending | Roadmap | After M4.8 |

---

## 8. Docs Index

| Document | Purpose | Last Updated |
|----------|---------|--------------|
| `CLAUDE.md` | Dev guidelines, toolchain, milestone verification gates | 2026-05-09 |
| `docs/Roadmap.md` (enterprise) | Repo-level sprint tracking | 2026-05-09 |
| `~/Projects/werkflow-platform/docs/Roadmap.md` | Master milestone map (not git-tracked) | 2026-05-10 |
| `docs/adr/` (platform) | 11 architecture decision records | 2026-05-10 |
| `docs/Architecture/` | Design specs, delegate analysis, workflow guides | Various |
| `docs/superpowers/specs/` | Feature specs consumed by implementation | 2026-04-26 |
| `docs/Knowledge_Base.md` | This file | 2026-05-10 |
| `werkflow-erp/docs/adr/` | ERP-specific ADRs (service boundary, JWT, API contract) | 2026-04-xx |
