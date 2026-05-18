# Claude Code — werkflow-enterprise

**Master Roadmap**: `~/Projects/werkflow-platform/docs/Roadmap.md`
**Repo Roadmap**: `docs/Roadmap.md`
**Rules**: `.claude/rules/` (auto-enforced)
**Stack**: Spring Boot (engine + admin-service) + Next.js portal

---

## Execution Workflow

**Primary stack:** ECC + Caveman + Graphify + Claude-mem

Strict lifecycle per task:

1. **Discover** — `graphify` to map affected files before touching anything
2. **Recall** — query `claude-mem` for past decisions on this domain
3. **Plan** — one paragraph max; no long multi-phase blocks
4. **Execute** — `caveman` for boilerplate; `ecc` skills for complex logic
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
- No direct calls to `runtimeService.signalEventReceived()` — use `TenantAwareSignalService` only

---

## Coding Behavior (Karpathy Principles)

> Full guidelines in `karpathy-guidelines` skill. These bias toward caution over speed.

* **Think before coding:** State assumptions explicitly. Surface ambiguity. Ask before guessing.
* **Simplicity first:** Minimum code that solves the problem. No speculative features or abstractions.
* **Surgical changes:** Touch only what the task requires. Don't improve adjacent code. Match existing style.
* **Goal-driven execution:** Define verifiable success criteria before starting. Loop until verified.

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
