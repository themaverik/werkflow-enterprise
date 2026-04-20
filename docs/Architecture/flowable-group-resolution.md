# ADR: Flowable Candidate Group Resolution via DOA Levels and Department Passthrough

**Date:** 2026-03-13
**Status:** Accepted
**Deciders:** Platform Architecture Team

---

## Table of Contents

1. [Context](#context)
2. [Problem](#problem)
3. [Decision](#decision)
4. [Architecture: The Seven Layers](#architecture-the-seven-layers)
   - [Layer 1 — FlowableGroups Constants](#layer-1--flowablegroups-constants)
   - [Layer 2 — Role-to-Group Mapping](#layer-2--role-to-group-mapping)
   - [Layer 3 — Department Passthrough](#layer-3--department-passthrough)
   - [Layer 4 — DOA Threshold Table](#layer-4--doa-threshold-table)
   - [Layer 5 — FlowableGroupResolver](#layer-5--flowablegroupresolver)
   - [Layer 6 — BPMN candidateGroups](#layer-6--bpmn-candidategroups)
   - [Layer 7 — Startup BPMN Validator](#layer-7--startup-bpmn-validator)
5. [Resolution Examples](#resolution-examples)
6. [BPMN Migration Mapping](#bpmn-migration-mapping)
7. [Developer Guide: Adding a New Role with Approval Authority](#developer-guide-adding-a-new-role-with-approval-authority)
8. [Developer Guide: Adding a New Department](#developer-guide-adding-a-new-department)
9. [What is Removed](#what-is-removed)
10. [Coupling Analysis](#coupling-analysis)
11. [Consequences](#consequences)
12. [Implementation Checklist](#implementation-checklist)

---

## Context

Werkflow is an enterprise workflow automation platform built on Spring Boot microservices. The process orchestration layer uses Flowable BPMN. Identity and access are managed by Keycloak with OIDC. User tasks in BPMN processes are routed to eligible users via `candidateGroups`.

This document records the architectural decision to replace ad-hoc, department-specific group identifiers with a structured, tenant-agnostic authority model backed by a configurable role mapping and a per-tenant threshold table.

---

## Problem

The previous implementation used Keycloak group paths and hardcoded department-specific strings as Flowable routing identifiers. This approach had several structural problems:

**Broken resolution bridge.** The `taskCandidateGroupIn(userContext.getGroups())` call passed raw Keycloak group hierarchy paths (e.g. `/IT Department/Managers`) as the set of groups to match against. BPMN files used flat string identifiers (`FINANCE_MANAGER`, `PROCUREMENT_TEAM`). These two sets never intersected, meaning tasks routed this way produced empty candidate lists silently.

**Org-specific group names in shared BPMN.** Identifiers like `FINANCE_MANAGER`, `PROCUREMENT_TEAM`, and `INVENTORY_MANAGER` encoded department knowledge into process definitions. Adding a new department required editing BPMN files, which are shared infrastructure.

**No central definition.** There was no authoritative registry of valid group identifiers. Developers added strings to BPMN and code independently, with no validation that they corresponded to anything real.

**Three conflicting routing systems.** `WorkflowTaskRouter`, `WorkflowAuthorizationService`, and `TaskAssignmentDelegate` each implemented partial, overlapping routing logic with hardcoded amounts and hardcoded group strings. No single system was authoritative.

**No tenant isolation for thresholds.** Approval amount thresholds were hardcoded in Java (`calculateRequiredDoaLevel()`), making per-tenant configuration impossible without code changes.

---

## Decision

Separate the two orthogonal concerns that were conflated in the old design:

1. **Authority level** — structural, finite, and organisation-agnostic. Modelled as Delegation of Authority (DOA) levels. The set of valid levels is defined once in Java constants and referenced everywhere else by those constants.

2. **Department** — organisational data, varies per tenant. Passed through directly from the JWT `department` claim with no mapping layer required.

Role-to-authority-level mapping is externalised to `application.yml` and overrideable via environment variables or Kubernetes ConfigMaps, making it deployable per environment without code changes.

Amount thresholds are stored per tenant in a database table, making them configurable by administrators at runtime.

---

## Architecture: The Seven Layers

### Layer 1 — FlowableGroups Constants

**File:** `src/main/java/com/werkflow/engine/workflow/FlowableGroups.java`

This class is the single source of truth for all valid Flowable group identifiers that are structural (i.e. not derived from org data). Every string that appears in a BPMN `candidateGroups` attribute as a static value must exist here. This file also serves as the developer onboarding reference for what groups exist in the system.

```java
public final class FlowableGroups {

    // Administrative roles
    public static final String ADMIN             = "ADMIN";
    public static final String SUPER_ADMIN       = "SUPER_ADMIN";
    public static final String WORKFLOW_DESIGNER = "WORKFLOW_DESIGNER";

    // Delegation of Authority levels
    // DOA_L0: employee — submit only, no approval authority
    public static final String DOA_L0 = "DOA_L0";
    // DOA_L1: approval tier 1 — lowest threshold
    public static final String DOA_L1 = "DOA_L1";
    public static final String DOA_L2 = "DOA_L2";
    public static final String DOA_L3 = "DOA_L3";
    // DOA_L4: approval tier 4 — highest / unlimited
    public static final String DOA_L4 = "DOA_L4";

    private FlowableGroups() {}
}
```

To add support for a new authority level (e.g. for a new tenant that requires a finer-grained tier), add a new constant here. Existing constants and all code referencing them are unaffected.

---

### Layer 2 — Role-to-Group Mapping

**File:** `src/main/resources/application.yml`

This block bridges Keycloak realm roles to sets of Flowable group identifiers. It is read at startup by `FlowableGroupProperties` via `@ConfigurationProperties`.

```yaml
app:
  flowable:
    role-mappings:
      admin:                [ADMIN, SUPER_ADMIN]
      super_admin:          [SUPER_ADMIN]
      doa_approver_level1:  [DOA_L1]
      doa_approver_level2:  [DOA_L1, DOA_L2]
      doa_approver_level3:  [DOA_L1, DOA_L2, DOA_L3]
      doa_approver_level4:  [DOA_L1, DOA_L2, DOA_L3, DOA_L4]
      workflow_designer:    [WORKFLOW_DESIGNER]
    include-department-as-group: true
```

**Inheritance semantics.** Each higher authority level includes all lower levels in its mapping. This means a task that specifies `candidateGroups="DOA_L2"` is visible to any user whose resolved groups contain `DOA_L2` — whether they were mapped there directly (level 2) or inherited it (level 3, level 4). The BPMN task only needs to state the minimum required level. There is no need to enumerate every acceptable level in the BPMN.

This configuration can be overridden per environment via environment variables or a Kubernetes ConfigMap without modifying the application binary.

---

### Layer 3 — Department Passthrough

The JWT issued by Keycloak includes a `department` claim containing the user's department code (e.g. `Finance`, `IT`, `Legal`). When `include-department-as-group: true`, this value is added directly to the user's resolved Flowable groups by `FlowableGroupResolver`.

No mapping table is required because the department code on the user and the `custodianDeptCode` field on domain records (e.g. `InventoryCategory`) originate from the same source of truth. They are the same string.

**Security constraint.** Before adding the department value to the resolved groups, `FlowableGroupResolver` must verify that the value does not collide with any constant defined in `FlowableGroups`. A `department` claim value of `SUPER_ADMIN`, `DOA_L4`, or any other structural identifier must be rejected to prevent privilege escalation via identity provider attribute manipulation.

---

### Layer 4 — DOA Threshold Table

**Location:** Database, per-tenant

Amount thresholds are stored in the database and managed via the admin UI. No amounts are hardcoded anywhere in application code or configuration.

```sql
CREATE TABLE doa_threshold (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  VARCHAR(100) NOT NULL,
    doa_level  VARCHAR(20)  NOT NULL,   -- references a FlowableGroups DOA constant
    max_amount NUMERIC(15,2),           -- NULL means unlimited
    currency   VARCHAR(3)   NOT NULL,
    UNIQUE (tenant_id, doa_level)
);
```

At the start of a process that involves a monetary approval, a service task queries this table for the tenant's thresholds, determines the minimum DOA level required for the request amount, and injects the result as a process variable (e.g. `requiredDoaLevel`). The BPMN user task then uses that variable or a static `DOA_LN` value in its `candidateGroups`.

`max_amount = NULL` represents unlimited authority and is the expected value for `DOA_L4` rows.

---

### Layer 5 — FlowableGroupResolver

**File:** `src/main/java/com/werkflow/engine/workflow/FlowableGroupResolver.java`

This is the single authoritative bean for converting a user's JWT claims into a list of Flowable group identifiers. It is the only place where role-to-group mapping logic executes.

**Inputs:**
- `List<String> roles` — the user's Keycloak realm roles, from `JwtUserContext`
- `String department` — the user's `department` JWT claim, may be null

**Output:**
- `List<String>` — deduplicated list of resolved Flowable group identifiers

**Resolution algorithm:**

1. For each role in the user's role list, look up the configured mapping in `FlowableGroupProperties`. Add all mapped groups to the result set.
2. If `include-department-as-group` is true and department is non-null and non-empty, verify the department value does not match any constant in `FlowableGroups`. If the check passes, add the department value to the result set.
3. Return the deduplicated list.

This bean is injected wherever Flowable identity integration requires the current user's group membership — primarily in `WorkflowTaskService` and in any custom identity provider bridge.

---

### Layer 6 — BPMN candidateGroups

BPMN process definitions use `candidateGroups` in two forms:

**Static authority-based tasks.** Use DOA level constants directly. Specify only the minimum required level. Higher-level users inherit membership and will see the task automatically.

```xml
<!-- Any user with DOA_L2 or above, or a SUPER_ADMIN, can claim this task -->
<userTask id="approveRequest" name="Approve Request"
          flowable:candidateGroups="DOA_L2,SUPER_ADMIN" />
```

**Department custodian tasks.** Use a process variable set at process start from the relevant domain record's `custodianDeptCode`. The variable value resolves to the department string (e.g. `Finance`), which matches the department in the user's resolved groups.

```xml
<!-- custodianGroupName is set at process start from InventoryCategory.custodianDeptCode -->
<userTask id="custodianReview" name="Custodian Review"
          flowable:candidateGroups="${custodianGroupName},SUPER_ADMIN,ADMIN" />
```

No BPMN file should contain department names as static string literals. Department routing is always dynamic via process variables.

---

### Layer 7 — Startup BPMN Validator

**File:** `src/main/java/com/werkflow/engine/workflow/BpmnGroupValidator.java`

An `ApplicationListener<ApplicationReadyEvent>` that runs after the application context is fully loaded. It iterates all deployed BPMN process definitions, extracts every static `candidateGroups` value, and validates each token against the set of constants declared in `FlowableGroups`.

If any BPMN references a static group identifier that does not exist in `FlowableGroups`, the validator throws an exception and prevents the application from starting. Dynamic expressions (values containing `${`) are skipped.

This prevents a class of silent failures where a typo such as `DOA_l2` (lowercase L) or `DOA_L 2` (space) would result in tasks that are permanently unclaimed with no error logged.

---

## Resolution Examples

### Example 1: Finance approver, DOA L2, Finance department

```
JWT roles:       [doa_approver_level2, employee]
JWT department:  Finance

Resolved groups: [DOA_L1, DOA_L2, Finance]

BPMN candidateGroups="DOA_L2"               matches DOA_L2        -> eligible
BPMN candidateGroups="${custodianGroupName}" (resolved: Finance)   -> eligible
BPMN candidateGroups="DOA_L3"                                      -> not eligible (correct)
```

### Example 2: Cross-functional approver, DOA L3, no department

```
JWT roles:       [doa_approver_level3, employee]
JWT department:  (not set)

Resolved groups: [DOA_L1, DOA_L2, DOA_L3]

BPMN candidateGroups="DOA_L2"               inherits membership   -> eligible
BPMN candidateGroups="DOA_L3"                                      -> eligible
BPMN candidateGroups="${custodianGroupName}" (resolved: IT)        -> not eligible (correct)
BPMN candidateGroups="DOA_L4"                                      -> not eligible (correct)
```

### Example 3: Adding DOA L5 for a new tenant

This scenario demonstrates the extension path when a new tenant requires a finer-grained authority tier.

1. Add `public static final String DOA_L5 = "DOA_L5";` to `FlowableGroups.java`
2. Add `doa_approver_level5: [DOA_L1, DOA_L2, DOA_L3, DOA_L4, DOA_L5]` to `application.yml`
3. Insert a threshold row for the tenant in `doa_threshold`
4. Reference `DOA_L5` in any new BPMN tasks designed for that tenant

Zero changes are required to existing tenant configurations, existing BPMN files, or the `FlowableGroupResolver` implementation.

---

## BPMN Migration Mapping

The following table records the mapping from legacy hardcoded group identifiers to their replacements under the new design. This mapping was used to update existing BPMN process definitions.

| Old candidateGroup | New candidateGroup | Rationale |
|---|---|---|
| `FINANCE_MANAGER` | `DOA_L2` | Mid-tier approval authority |
| `FINANCE_VP` | `DOA_L3` | Senior approval authority |
| `FINANCE_CFO` | `DOA_L4` | Maximum approval authority |
| `PROCUREMENT_TEAM` | `DOA_L1` | Entry-level approval |
| `PROCUREMENT_SUPERVISOR` | `DOA_L2` | Mid-tier approval |
| `PROCUREMENT_MANAGER` | `DOA_L3` | Senior approval |
| `PROCUREMENT_DIRECTOR` | `DOA_L4` | Maximum approval |
| `INVENTORY_MANAGER` | `DOA_L2` | Mid-tier approval |
| `procurement` (bare, asset-request-process) | `${procurementGroupName}` | Department variable, set at process start |

---

## Developer Guide: Adding a New Role with Approval Authority

Follow these steps when a new Keycloak role needs to carry workflow approval authority.

1. **Define the role in Keycloak.** Add the role to `werkflow-realm.json` with a description that records its intended authority level and approval scope.

2. **Determine the DOA level.** Based on the approval limits the role should carry, identify which `DOA_LN` level it corresponds to. Consult the `doa_threshold` table for the relevant tenant to understand what amounts each level covers.

3. **Add the mapping to application.yml.** Under `app.flowable.role-mappings`, add an entry for the new role name. Include all DOA levels up to and including the level the role carries, preserving the inheritance pattern.

   Example for a new `doa_approver_level2_alt` role that maps to L2 authority:
   ```yaml
   doa_approver_level2_alt: [DOA_L1, DOA_L2]
   ```

4. **Assign the role and `doa_level` attribute to users in Keycloak.** The `doa_level` attribute is used for display and reporting; the role is what drives group resolution.

5. **Configure amount thresholds for the tenant.** If the new role introduces a new DOA level, insert the corresponding threshold row(s) via the admin UI. If it maps to an existing level, no threshold change is needed.

6. **No BPMN changes required.** Any task that specifies `candidateGroups="DOA_L2"` will automatically become visible to users carrying this new role. Routing is driven by group membership, not by role name.

---

## Developer Guide: Adding a New Department

Follow these steps when a new organisational department needs to participate in workflow routing.

1. **Provision users in Keycloak.** Set the `department` attribute on user accounts (or their Keycloak group) to the department code string (e.g. `Legal`).

2. **Set `custodianDeptCode` on domain records.** When creating `InventoryCategory` or other records that drive department-scoped task routing, set `custodianDeptCode` to the same department code string.

3. **Done.** No code changes, no configuration changes, no BPMN changes are required. The department code flows through `FlowableGroupResolver` automatically and matches the process variable set at process start.

The only action required is ensuring the department code string is consistent between Keycloak user attributes and domain record fields. These values should originate from the same source (e.g. an HR system or a department reference table) to prevent drift.

---

## What is Removed

The following components are deleted as part of this refactor. They must not be reintroduced.

**`WorkflowTaskRouter`**
Used the Keycloak Admin API at runtime to fetch group membership lists and perform routing decisions. This was replaced by Flowable's native `candidateGroups` resolution, which requires no runtime Keycloak API calls.

**`WorkflowAuthorizationService.canApproveItRequest()`, `canApproveProcurement()`, `canApproveByDoaLevel()`**
Dead code with no callers at the time of removal. Contained hardcoded group paths and hardcoded amount thresholds. Superseded by DOA level candidateGroups in BPMN and the `doa_threshold` table respectively.

**`TaskAssignmentDelegate`**
A third routing system that added its own group strings to tasks at creation time. Its behaviour conflicted directly with DOA-level `candidateGroups` expressions in BPMN. Removed without replacement.

**`calculateRequiredDoaLevel()` in `WorkflowAuthorizationService` and `WorkflowTaskRouter`**
Hardcoded amount-to-level mapping. Replaced by the `doa_threshold` DB table, which is configurable per tenant via the admin UI.

---

## Coupling Analysis

| Layer | Coupled to | Via |
|---|---|---|
| `FlowableGroups` constants | Nothing | Standalone constants class |
| `application.yml` role-mappings | Keycloak role names | Configuration string keys |
| `FlowableGroupResolver` | `FlowableGroupProperties` | `@Autowired` / `@ConfigurationProperties` |
| `WorkflowTaskService` | `FlowableGroupResolver` | `@Autowired` |
| BPMN process definitions | `FlowableGroups` string values | XML `candidateGroups` attributes |
| `doa_threshold` table | `tenant_id`, DOA level strings | Database rows |

No layer is coupled to Keycloak's internal group hierarchy structure. Keycloak groups remain exclusively for organisational hierarchy management, attribute propagation, and SSO. They play no role in workflow task routing.

---

## Consequences

### What improves

**Multi-tenancy without code changes.** New tenants with different department structures or additional DOA levels require only configuration and database changes. No code, no BPMN edits for existing process definitions.

**BPMN portability.** Process definitions no longer encode department-specific knowledge. A process built for Finance can be deployed to a Procurement context by changing process variables at instantiation time, not by editing the BPMN.

**Typo prevention at startup.** The BPMN validator eliminates the silent failure mode where a misspelled group identifier causes tasks to be permanently unclaimed.

**Single routing authority.** `FlowableGroupResolver` is the only component that converts identity claims to Flowable groups. There is one place to read, one place to test, and one place to modify.

**No runtime Keycloak API dependency.** Group resolution happens locally from JWT claims and configuration. There is no dependency on Keycloak Admin API availability during task routing.

**Configurable thresholds.** Approval amount limits are managed by administrators in the database, not by developers in code.

### Constraints introduced

**`FlowableGroups` is a public API.** Constants must not be renamed or removed without a corresponding migration of all BPMN files that reference them and all `application.yml` mapping entries that use them. Treat this class with the same discipline as a public API surface.

**Role name strings in `application.yml` must match Keycloak exactly.** If a Keycloak role is renamed, the corresponding key in `app.flowable.role-mappings` must be updated in the same change. There is no compile-time enforcement of this contract.

**Department values must be sanitised.** Any system that writes to the `department` JWT claim (Keycloak mappers, HR integrations) must be reviewed to ensure it cannot write a value that collides with a `FlowableGroups` constant. The `FlowableGroupResolver` provides a runtime guard, but the issue should be prevented at source.

**Inheritance is explicit, not computed.** The cumulative group lists in `application.yml` (e.g. `doa_approver_level3: [DOA_L1, DOA_L2, DOA_L3]`) are written out manually. If a new intermediate level is introduced, all higher-level mappings must be updated to include it. This is intentional for clarity but requires discipline.

---

## Implementation Checklist

These steps are ordered to minimise risk. Each step can be committed and deployed independently.

1. **Create `FlowableGroups.java`**
   Define all constants. Annotate the class as the single source of truth. No logic.
   `src/main/java/com/werkflow/engine/workflow/FlowableGroups.java`

2. **Create `FlowableGroupProperties.java`**
   `@ConfigurationProperties(prefix = "app.flowable")` binding for the `role-mappings` map and `include-department-as-group` flag.
   `src/main/java/com/werkflow/engine/workflow/FlowableGroupProperties.java`

3. **Add role-mappings block to `application.yml`**
   Add the `app.flowable.role-mappings` and `include-department-as-group` entries.
   `src/main/resources/application.yml`

4. **Create `FlowableGroupResolver.java`**
   Implement the three-step resolution algorithm. Inject `FlowableGroupProperties`. Include the department collision guard against `FlowableGroups` constants.
   `src/main/java/com/werkflow/engine/workflow/FlowableGroupResolver.java`

5. **Wire `FlowableGroupResolver` into `WorkflowTaskService`**
   Replace any existing group derivation logic with a call to `FlowableGroupResolver.resolve(roles, department)`.
   `src/main/java/com/werkflow/workflow/service/WorkflowTaskService.java`

6. **Create `doa_threshold` table**
   Write the migration script. Insert initial rows for all existing tenants based on the values previously hardcoded in `calculateRequiredDoaLevel()`.
   `src/main/resources/db/migration/VX__create_doa_threshold.sql`

7. **Update `JwtUserContext`**
   Ensure `getDepartment()` reads the `department` claim. Ensure `getRoles()` returns realm roles (not resource roles unless both are needed).
   `src/main/java/com/werkflow/security/JwtUserContext.java`

8. **Migrate BPMN files**
   Apply the mapping table in [BPMN Migration Mapping](#bpmn-migration-mapping) to all process definition XML files. Replace static department group strings with `${variableName}` expressions. Replace legacy named groups with `DOA_LN` constants.
   `src/main/resources/processes/`

9. **Update process instantiation services**
   For each process that uses department-scoped tasks, ensure the service that starts the process reads the relevant `custodianDeptCode` from the domain record and sets it as a process variable before the first user task is reached.

10. **Create `BpmnGroupValidator.java`**
    Implement the `ApplicationListener<ApplicationReadyEvent>`. Iterate deployed process definitions, extract static `candidateGroups` tokens, validate against `FlowableGroups` constants, throw on first unknown value.
    `src/main/java/com/werkflow/engine/workflow/BpmnGroupValidator.java`

11. **Delete dead code**
    Remove `WorkflowTaskRouter`, the three dead methods from `WorkflowAuthorizationService`, `TaskAssignmentDelegate`, and the hardcoded `calculateRequiredDoaLevel()` implementations. Confirm no callers remain before deletion.

12. **Write unit tests for `FlowableGroupResolver`**
    Cover: single role mapping, multi-role deduplication, department passthrough enabled/disabled, department collision guard (department value matches a `FlowableGroups` constant), null department, empty role list.
    `src/test/java/com/werkflow/engine/workflow/FlowableGroupResolverTest.java`

13. **Write unit tests for `BpmnGroupValidator`**
    Cover: valid BPMN passes, BPMN with unknown static group fails startup, dynamic expressions are skipped.
    `src/test/java/com/werkflow/engine/workflow/BpmnGroupValidatorTest.java`

14. **Integration test: end-to-end task routing**
    Start a process with a known request amount. Assert the correct `requiredDoaLevel` variable is set. Assert the user task candidate groups resolve correctly for test users with known roles and departments.
