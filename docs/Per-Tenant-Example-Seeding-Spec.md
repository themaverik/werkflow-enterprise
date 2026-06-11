# Per-Tenant Example Seeding — Implementation Spec

**ADR**: ADR-031  
**Priority**: Highest — blocks MVP demo readiness  
**Date**: 2026-06-11  
**Status**: Ready for implementation

---

## Problem Statement

The current example seeding has three critical defects:

1. **Isolation violation**: All examples land under `tenant_id="default"`. New tenants see
   nothing. Sharing default tenant data cross-tenant is an anti-pattern.
2. **Broken examples**: The seed audit (2026-06-11) found 3 orphan forms, 1 orphan DMN,
   and 5 BPMNs with unresolvable form keys. Broken examples undermine the demo.
3. **No atomic control**: BPMN, DMN, and Forms are controlled by three independent mechanisms
   (`ProcessExampleDeployer`, `DmnExampleDeployer`, `FormSchemaDataLoader`). They cannot be
   treated as one unit.

---

## Seed Example Audit Findings (2026-06-11)

### Current inventory: 8 BPMNs · 3 DMNs · 6 Forms

#### Coherent (keep + fix):

| Workflow | BPMN | DMN | Forms needed | Status |
|----------|------|-----|--------------|--------|
| Capex Approval | `capex-approval-process` | `capex-approver-resolution.dmn` (3 decisions) | `capex-request-form`, `capex-approval-form` | Fix: approval userTasks use wrong form key (use capex-request-form instead of capex-approval-form) |
| Leave Request | `leave-request` | `leave-approval.dmn` | `leave-request-form`, `leave-approval-form` | Fix: DMN not wired to BPMN; approval form not referenced |

#### Remove (broken — missing forms or inline formProperty):

| BPMN | Why |
|------|-----|
| `procurement-approval-process` | 4 form keys, none seeded |
| `finance-approval-process` | `budget-request-form` not seeded |
| `general-approval` | `general-approval-form`, `general-approval-decision` not seeded |
| `event-ticket-request` | `event-ticket-form` not seeded |
| `onboarding-checklist` | `onboarding-checklist-form` not seeded; 2 tasks use inline formProperty |
| `asset-request-process` | Mid-process tasks use legacy inline `flowable:formProperty` (no form key) |

#### Remove (orphan artifacts):

| Artifact | Why |
|----------|-----|
| `capex-approval-form` (form) | Seeded but no BPMN used it — needs to be wired to capex approval userTasks first |
| `leave-approval-form` (form) | Seeded but no BPMN used it — needs to be wired to leave approval userTask first |
| `purchase-requisition-form` (form) | No BPMN references it |
| `leave-approval.dmn` | In classpath but leave-request BPMN has no DMN service task |

**Curated set for MVP: 2 coherent workflows** (after fixing the wiring gaps above).

---

## Classpath Restructure

### Before (remove):
```
resources/
  processes/examples/               ← 8 BPMNs (mixed coherent + broken)
  dmn-examples/                     ← 3 DMNs (1 orphan)
  forms/                            ← 6 forms (3 orphan)
```

### After:
```
resources/
  examples/
    tenants/
      default/
        bpmn/
          capex-approval-process.bpmn20.xml
          leave-request.bpmn20.xml
        dmn/
          capex-approver-resolution.dmn
          leave-approval.dmn
        forms/
          capex-request-form.json
          capex-approval-form.json
          leave-request-form.json
          leave-approval-form.json
```

- `examples/tenants/{tenantId}/` folders can be added per tenant for vertical-specific overrides.
- If no tenant-specific folder exists, `default/` is used.
- Each folder contains exactly the artifacts needed for that folder's workflows — no orphans.

---

## API Design

### Engine — Internal Seed Endpoint

```
POST /api/internal/examples/seed/{tenantId}
Authorization: ENGINE_SERVICE role (service-to-service JWT)
```

Request body (optional filter):
```json
{
  "workflows": ["capex-approval-process", "leave-request"]  // omit = all
}
```

Response:
```json
{
  "tenantId": "acme-corp",
  "deployed": [
    { "workflow": "capex-approval-process", "forms": 2, "dmn": 1, "bpmn": 1 }
  ],
  "skipped": [
    { "workflow": "leave-request", "reason": "already_deployed" }
  ],
  "failed": []
}
```

### Admin Service — Platform Seed Endpoint

```
POST /api/v1/platform/tenants/{id}/seed-examples
Authorization: SUPER_ADMIN
```

Delegates to the engine's internal endpoint using the service-to-service JWT.
Returns the same response shape.

---

## Transaction Design

For each workflow example (e.g. "Capex Approval"):

```
Step 1: Check idempotency
  → If BPMN already deployed for tenant (Flowable query by key + tenantId): skip entirely

Step 2: Deploy forms (DB transaction)
  → INSERT INTO form_schema (...) ON CONFLICT (form_key, tenant_id) DO NOTHING
  → Rollback: DELETE FROM form_schema WHERE form_key IN (...) AND tenant_id = ?

Step 3: Deploy DMN (Flowable DMN engine)
  → dmnRepositoryService.createDeployment().tenantId(tenantId).enableDuplicateFiltering().deploy()
  → Rollback on BPMN failure: dmnRepositoryService.deleteDeployment(id)

Step 4: Deploy BPMN (Flowable BPMN engine)
  → repositoryService.createDeployment().tenantId(tenantId).enableDuplicateFiltering().deploy()
  → On failure: compensate steps 3 + 2 (in reverse)
```

A failure in one workflow does not block others. The response records per-workflow outcomes.

---

## BPMN Fixes Required (before implementing seeding)

These must ship before the seeding service is built, or the curated set cannot be reduced to 2:

### Capex Approval Process

- `managerApprovalTask`, `vpApprovalTask`, `cfoApprovalTask`: change `flowable:formKey` from
  `capex-request-form` → `capex-approval-form`
- The `capex-approval-form` schema JSON must include the decision fields (approve/reject +
  comments) in addition to the read-only summary of the request

### Leave Request Process

- Add a DMN service task before the human approval gate:
  `flowable:type="dmn"`, `decisionTableReferenceKey="leave_approval"`
- The DMN output variable (e.g. `autoApproval`) routes the gateway — if `true`, skip human
  approval; if `false`, proceed to `leaveApprovalTask`
- Wire `leaveApprovalTask` `flowable:formKey` to `leave-approval-form`
- The `leave-approval-form` schema JSON must include approve/reject + comments fields

---

## Portal UI Changes

### Tenant Creation Form

Add a checkbox below the provisioning fields:

```
☑ Seed starter content  (BPMN, DMN and Form examples for this tenant)
```

Checked by default. When ticked, the admin service calls `POST .../seed-examples` immediately
after `POST .../tenants` completes. If seeding fails, provisioning still succeeds — the
operator can retry seeding from the tenant detail page.

### Tenant Detail Page

Add a "Seed starter content" button (only visible if no examples are deployed yet for the
tenant). Shows a result dialog listing what was deployed vs skipped.

---

## Implementation Tasks (in dependency order)

### Phase A — BPMN/Form fixes (prerequisite)
1. Fix `capex-approval-process.bpmn20.xml`: wire 3 approval tasks to `capex-approval-form`
2. Add approval decision fields to `capex-approval-form.json`
3. Fix `leave-request.bpmn20.xml`: add DMN routing + wire approval task to `leave-approval-form`
4. Add approve/reject fields to `leave-approval-form.json`

### Phase B — Classpath restructure
5. Move surviving files to `examples/tenants/default/{bpmn,dmn,forms}/`
6. Delete the 6 removed BPMNs, 1 orphan DMN, 3 orphan forms from classpath
7. Retire `ProcessExampleDeployer`, `DmnExampleDeployer`, `FormSchemaDataLoader` (or scope
   them to dev profile only reading from the new `default/` folder)

### Phase C — Seeding service (engine)
8. `ExampleSeedService` — classpath resolver with tenant fallback to `default/`
9. `ExampleSeedService.seedTenant(String tenantId, List<String> workflows)` — atomic per-workflow
10. Internal endpoint `POST /api/internal/examples/seed/{tenantId}`
11. Unit tests: idempotency, partial failure compensation, tenant folder resolution

### Phase D — Admin service wiring
12. `POST /api/v1/platform/tenants/{id}/seed-examples` — delegates to engine via service JWT
13. `TenantProvisioningService.provision()` — call seed endpoint when `seedExamples=true`

### Phase E — Portal UI
14. Checkbox on tenant creation form
15. "Seed starter content" button on tenant detail page (idempotent, shows result dialog)

---

## Acceptance Criteria

- [ ] Creating a new tenant with "Seed starter content" checked deploys exactly 2 workflows
      under the new tenant's `tenant_id`
- [ ] Newly provisioned tenant sees capex and leave examples in the portal process list
- [ ] Repeating the seed call returns `skipped` for already-deployed workflows
- [ ] A failed DMN deployment leaves no orphan form rows (compensation works)
- [ ] `WERKFLOW_DEPLOY_EXAMPLES=false` in the enterprise docker-compose (production default)
- [ ] `WERKFLOW_DEPLOY_EXAMPLES=true` still works for the dev compose (default tenant only)
- [ ] All example BPMNs deploy cleanly: `AllProcessesDeployAndStartTest` passes
