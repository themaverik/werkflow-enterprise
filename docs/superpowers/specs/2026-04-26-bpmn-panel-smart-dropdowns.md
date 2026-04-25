# BPMN Properties Panel — Smart Dropdowns

**Date:** 2026-04-26
**Status:** Planned
**Scope:** `werkflow-enterprise` portal + engine

---

## Overview

Two UX improvements to the BPMN process designer properties panel:

1. **Delegate Expression Dropdown** — replace the free-text `delegateExpression` field with a dropdown populated from live Spring beans registered in the engine
2. **Candidate Groups Tag-select** — replace the free-text candidate groups field with a tag-select dropdown populated from the tenant's Keycloak groups, filtered by the logged-in user's role

---

## Feature 1 — Delegate Expression Dropdown

### Problem

The Delegate Expression field is a plain text input. Designers must know the exact Spring bean name (e.g. `${emailActionDelegate}`) and type it manually. Typos cause silent deploy failures or runtime errors.

### Solution

Convert to a `SelectEntry` dropdown. Options are fetched at runtime from the engine, which introspects the Spring `ApplicationContext` for all registered `JavaDelegate` beans.

### Implementation

#### Backend — `DelegateController.java` (new file)

```
services/engine/src/main/java/com/werkflow/engine/controller/DelegateController.java
```

- Endpoint: `GET /api/delegates`
- Auth: `@PreAuthorize("hasPermission(null, 'WORKFLOW:DESIGN')")`
- Implementation:
  ```java
  @Autowired ApplicationContext ctx;

  @GetMapping("/api/delegates")
  public List<String> listDelegates() {
      return new ArrayList<>(ctx.getBeansOfType(JavaDelegate.class).keySet());
  }
  ```
- Returns: `["dmnRouteDelegate", "emailActionDelegate", "externalApiCallDelegate"]`
- No service layer needed — pure Spring introspection, read-only

#### Frontend

**`lib/api/flowable.ts`** — add:
```ts
export async function getDelegates(): Promise<string[]> {
  const response = await apiClient.get('/api/delegates')
  return response.data
}
```

**`lib/bpmn/flowable-properties-provider.ts`** — add module-level state:
```ts
let delegateOptions: string[] = []
export function setDelegateOptions(options: string[]) {
  delegateOptions = options
}
```

Replace the `TextFieldEntry` for `delegateExpression` with `SelectEntry`:
```ts
{
  id: 'delegateExpression',
  component: SelectEntry,
  label: translate('Delegate Expression'),
  getValue: () => element.businessObject.get('flowable:delegateExpression') || '',
  setValue: (value: string) => modeling.updateProperties(element, {
    'flowable:delegateExpression': value || undefined,
    delegateExpression: value || undefined,
  }),
  getOptions: () => [
    { value: '', label: translate('(none)') },
    ...delegateOptions.map(name => ({
      value: `\${${name}}`,
      label: name,
    }))
  ],
}
```

**`components/bpmn/BpmnDesigner.tsx`** — fetch on mount:
```ts
import { getDelegates } from '@/lib/api/flowable'
import { setDelegateOptions } from '@/lib/bpmn/flowable-properties-provider'

// inside useEffect:
getDelegates()
  .then(names => { setDelegateOptions(names); refreshPropertiesPanel() })
  .catch(err => console.error('Failed to load delegates:', err))
```

### Behaviour
- Default selection: `(none)` — empty string, no `delegateExpression` attribute written
- When action type is set (e.g. NOTIFICATION), the delegate expression field is auto-populated by `setActionType` — the dropdown will reflect the value correctly via `getValue`
- Java Class field remains a free-text input (uncommon advanced usage)

---

## Feature 2 — Candidate Groups Tag-select

### Problem

Candidate groups is a free-text comma-separated field. Designers must know valid group IDs and type them exactly. The hint text shows random UUIDs from the API which are unreadable. There is no visual feedback for what groups are available.

### Solution

Replace with a tag-select component: selected groups shown as removable chips, remaining available groups shown in a dropdown. Groups are loaded from `GET /api/groups` (already implemented). The visible list is filtered by the logged-in user's role.

### Role-based Filtering

| User Role | Groups shown |
|---|---|
| `ADMIN`, `SUPER_ADMIN`, `WORKFLOW_ADMIN` | All tenant groups |
| All other roles (EMPLOYEE, dept roles) | DOA_L* groups only |

Rationale: non-admin designers should only assign to generic approval chains, not to specific department groups they may not have visibility over.

### Implementation

#### `components/bpmn/CandidateGroupsInput.tsx` (new file)

Custom React component following the bpmn-js entry interface (`id`, `element`, `getValue`, `setValue` props):

```tsx
interface Props {
  id: string
  getValue: () => string
  setValue: (value: string) => void
  availableGroups: Array<{ id: string; name: string }>
}

export function CandidateGroupsInput({ getValue, setValue, availableGroups }: Props) {
  const current = getValue().split(',').map(s => s.trim()).filter(Boolean)

  const toggle = (groupId: string) => {
    const next = current.includes(groupId)
      ? current.filter(g => g !== groupId)
      : [...current, groupId]
    setValue(next.join(','))
  }

  return (
    <div>
      {/* Selected tags */}
      <div className="flex flex-wrap gap-1 mb-2">
        {current.map(id => (
          <span key={id} className="...chip styles...">
            {id} <button onClick={() => toggle(id)}>×</button>
          </span>
        ))}
      </div>
      {/* Available groups dropdown */}
      <select onChange={e => toggle(e.target.value)} value="">
        <option value="">{availableGroups.length ? 'Add group...' : 'No groups available'}</option>
        {availableGroups
          .filter(g => !current.includes(g.id))
          .map(g => <option key={g.id} value={g.id}>{g.name} ({g.id})</option>)}
      </select>
    </div>
  )
}
```

#### `lib/bpmn/flowable-properties-provider.ts`

Add user role to module-level state (set from `BpmnDesigner.tsx` after session resolves):
```ts
let currentUserRoles: string[] = []
export function setCurrentUserRoles(roles: string[]) {
  currentUserRoles = roles
}
```

Update `candidateGroupsEntry` to use `CandidateGroupsInput`:
```ts
function candidateGroupsEntry(element, modeling, translate, debounce) {
  const ADMIN_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN']
  const isAdmin = currentUserRoles.some(r => ADMIN_ROLES.includes(r))
  const filtered = isAdmin
    ? groupOptions
    : groupOptions.filter(g => /^DOA_L\d+$/i.test(g.id))

  return {
    id: 'ab-candidateGroups',
    element,
    component: CandidateGroupsInput,
    availableGroups: filtered,
    getValue: () => element.businessObject.get('flowable:candidateGroups') || '',
    setValue: (value: string) =>
      modeling.updateProperties(element, { 'flowable:candidateGroups': value || undefined }),
  }
}
```

#### `components/bpmn/BpmnDesigner.tsx`

Set user roles after session resolves:
```ts
import { setCurrentUserRoles } from '@/lib/bpmn/flowable-properties-provider'

// in useEffect (after session):
const roles = getRolesFromSession(session)
setCurrentUserRoles(roles)
```

---

## Out of Scope

- Multi-level group hierarchy UI
- Search/filter within the dropdown (group list is small)
- Creating new groups from the designer
- Assigning groups dynamically based on process variables

---

## Files Changed

| File | Change |
|---|---|
| `services/engine/.../controller/DelegateController.java` | NEW — `GET /api/delegates` endpoint |
| `frontends/portal/lib/api/flowable.ts` | Add `getDelegates()` |
| `frontends/portal/lib/bpmn/flowable-properties-provider.ts` | Delegate dropdown + candidate groups tag-select + `setDelegateOptions` + `setCurrentUserRoles` |
| `frontends/portal/components/bpmn/CandidateGroupsInput.tsx` | NEW — tag-select component |
| `frontends/portal/components/bpmn/BpmnDesigner.tsx` | Fetch delegates + set user roles on mount |
