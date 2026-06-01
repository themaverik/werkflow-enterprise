# Claude Code — werkflow-enterprise

**Master Roadmap**: `~/Projects/werkflow-platform/docs/Roadmap.md`
**Repo Roadmap**: `docs/Roadmap.md`
**Rules**: `.claude/rules/` (auto-enforced)
**Stack**: Spring Boot (engine + admin-service) + Next.js portal

---

## Core Principles (read first)

> These govern every change and override speed. Full guidelines in `karpathy-guidelines` skill.

* **Surgical changes:** Touch only what the task requires. Do NOT improve, refactor, or restyle adjacent code. Match existing patterns and style.
* **Simplicity first:** Minimum code that solves the problem. No speculative features, abstractions, state, or dependencies that weren't asked for.
* **Think before coding:** State assumptions explicitly. Surface ambiguity. Ask before guessing.
* **Goal-driven execution:** Define verifiable success criteria before starting. Loop until verified — no scope creep.

---

## Scope Tiers

Pick the tier first; it decides how much process applies.

* **Trivial** — single component, no new deps/state/abstractions, no API or schema change. Implement directly, run the relevant build/test gate, done. **Skip** graphify, claude-mem, and the agent pipeline.
* **Standard / Milestone** — everything else. Follow the full Execution Workflow below.

If unsure which tier, default to Trivial and ask before escalating.

---

## Execution Workflow (Standard / Milestone)

**Primary stack:** ECC + Graphify + Claude-mem

Strict lifecycle per task:

1. **Discover** — `graphify` to map affected files before touching anything
2. **Recall** — query `claude-mem` for past decisions on this domain
3. **Plan** — one paragraph max; no long multi-phase blocks
4. **Implement** — surgical changes only; touch only what the task requires; match existing style (see Core Principles)
5. **Verify** — see Milestone Verification section below
6. **Record** — save decisions to `claude-mem` immediately after task
7. **Purge** — `/compact` after each milestone phase

---

## Milestone Verification Protocol

Run verification **once at milestone end** (not per commit). During dev, run only affected tests.

### Per-Milestone Gates

| Milestone | Primary Gate | Review | E2E | Superpowers |
|---|---|---|---|---|
| M2 — ADR Foundation | `mvn clean verify` (engine + admin) | `ecc:java-review` on changed files only | No | No |
| M3 — ADR Core | `mvn clean verify` + `npm run build` | `ecc:java-review` + `ecc:typescript-review` | 2–3 targeted specs | Auth/role endpoints only |
| M4 — UI Overhaul | `npm run build` + browser smoke | `ecc:typescript-review` | Playwright visual smoke | No |
| M5 — Signal Events | `mvn clean verify` | `ecc:java-review` | Full 7-spec E2E suite | No |
| M6 — Analytics | `mvn clean verify` + query perf check | `ecc:java-review` | No | No |
| M7 — CI/CD | Pipeline runs itself | Config review | No | No |

### Key Efficiency Rules

1. Run `graphify` first — scope exactly which files changed; never scan the whole module
2. Pass **only changed files** to `ecc` reviewer — not the whole module
3. Full E2E suite runs once per milestone end, not per commit
4. During dev: `mvn test -pl services/engine -Dtest=<TestClass>` — targeted, not full suite
5. Superpowers reviewer reserved for auth/custody/role-mapping endpoints only
6. `npm run build` is the TypeScript gate — it catches type errors without a running server

### Dev Loop (Java)

```
Write code → mvn test -Dtest=<NewTest> → ecc:java-review (changed files) → fix → full suite at PR
```

### Dev Loop (TypeScript/Next.js)

```
Write code → npm run build → ecc:typescript-review (changed files) → browser check → E2E at milestone end
```

---

## Code Quality Standards

- Strict typing everywhere (no `any` in TypeScript, no raw types in Java)
- Functions small, pure where possible, highly cohesive
- DRY + SOLID — no duplicate business logic across services
- Docstrings on all public methods; no over-commenting obvious code
- All new logic has corresponding unit tests (success + edge-case failure)
- Signals: model them in BPMN (`intermediateThrowEvent`) under tenant-scoped deployments — the engine isolates delivery to the throwing tenant (proven by `SignalTenantIsolationTest`). Do **not** dispatch signals from Java with bare `runtimeService.signalEventReceived()` (a no-op against tenant-scoped subscriptions); if Java dispatch is ever needed, use the `...WithTenantId` variant

---

## Code Changes and Review

Applies to **Standard / Milestone** tier. Trivial-tier changes skip the agent pipeline.

- **staff-engineer** agent: all code implementations.
- **frontend-developer** agent: reviews all front-end changes made by staff-engineer.
- **system-architect** agent: architecture review, only on architecture changes / major refactors.
- For any other change, pick the most appropriate agent for the task.

**MUST NOT** start coding while ambiguity persists. Ask and surface any ambiguity about who does the change and what the change is.

---

## Graphify

Knowledge graph at `graphify-out/`.

- Before architecture or codebase questions: read `graphify-out/GRAPH_REPORT.md`
- If `graphify-out/wiki/index.md` exists: navigate it instead of reading raw files
- After modifying code files: rebuild graph via graphify watch (auto-triggered by hook)

---

## Code Review Strategy

| Scenario | Tool |
|----------|------|
| Java service / delegate / controller | `ecc:java-review` |
| TypeScript / Next.js component | `ecc:typescript-review` |
| Auth, custody, role-mapping endpoints | `/superpowers:code-reviewer` |
| Pre-production validation | `/superpowers:security-auditor` |
| Architecture change (major refactor) | `/superpowers:architect-review` |