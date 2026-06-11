# Werkflow Portal — Claude Code Implementation Handover

**Date**: 2026-06-11  
**Based on**: portal-design-audit.md (session 30) + DESIGN.md v1.0  
**Approach**: Minimum-change corrections only. No redesign, no new features.  
**Stack**: Next.js 14, shadcn/ui, Tailwind CSS, TypeScript, React Query v5, next-auth v5

---

## Reading Order

1. Read `DESIGN.md` first — it is the visual contract this handover implements.
2. Work tracks in priority order: **Track A** (security/correctness blockers) → **Track B** (design consistency) → **Track C** (library hardening).
3. Each task lists: severity, files, exact change, and acceptance criteria.
4. Do not change anything not listed. Surface any ambiguity as a comment rather than guessing.

---

## Track A — Security & Correctness Blockers

These must be resolved before design work. They are small, high-impact changes.

---

### A-1 · Add next-auth type augmentation
**Audit ref**: AP-1  
**Severity**: HIGH — blocks A-7 and removes 17 `as any` casts  
**File**: `types/next-auth.d.ts`

**Current state**: File exists but does not declare custom Keycloak claims on `Session`, `JWT`, or `User`.

**Change**: Extend the three next-auth interfaces to declare all custom fields added in `auth.config.ts` callbacks.

```typescript
import 'next-auth'
import { DefaultSession, DefaultUser } from 'next-auth'
import { JWT as DefaultJWT } from 'next-auth/jwt'

declare module 'next-auth' {
  interface Session extends DefaultSession {
    accessToken: string
    idToken: string
    roles: string[]
    tenantId: string
    doa_level?: string
    department?: string
    error?: string
    user: DefaultSession['user'] & {
      firstName?: string
      lastName?: string
      username?: string
    }
  }

  interface User extends DefaultUser {
    accessToken?: string
    idToken?: string
    roles?: string[]
    tenantId?: string
    doa_level?: string
    department?: string
    username?: string
    firstName?: string
    lastName?: string
  }
}

declare module 'next-auth/jwt' {
  interface JWT extends DefaultJWT {
    accessToken?: string
    idToken?: string
    roles?: string[]
    tenantId?: string
    doa_level?: string
    department?: string
    error?: string
  }
}
```

**After**: Remove every `(session as any)`, `(token as any)`, `(session?.accessToken as string)` cast across:
- `lib/auth/auth-context.tsx`
- `auth.config.ts`
- `app/providers.tsx`
- `app/api/auth/logout/route.ts`
- `components/auth/token-expired-dialog.tsx`
- `app/(platform)/layout-client.tsx`

**Acceptance**: `tsc --noEmit` passes with zero type errors on the auth layer.

---

### A-2 · Guard `/api/health` route
**Audit ref**: SEC-3  
**Severity**: HIGH — unauthenticated info disclosure  
**File**: `app/api/health/route.ts`

**Change**: Add session check at the top of the `GET` handler.

```typescript
import { auth } from '@/auth'

export async function GET() {
  const session = await auth()
  if (!session) {
    return new Response('Unauthorized', { status: 401 })
  }
  // … existing logic
}
```

**Acceptance**: `GET /api/health` without a session cookie returns 401.

---

### A-3 · Remove `NEXT_PUBLIC_` prefix from server-only URL env vars
**Audit ref**: SEC-1  
**Severity**: HIGH — internal hostnames leak into browser bundle  
**Files**: `app/api/health/route.ts`, `app/api/proxy/engine/[...path]/route.ts`, `app/api/proxy/admin/[...path]/route.ts`

**⚠ Scope decision required before starting.** `lib/api/client.ts` uses both `NEXT_PUBLIC_ENGINE_API_URL` and `NEXT_PUBLIC_ADMIN_SERVICE_URL` as Axios base URLs that run in the browser (direct client-side API calls, bypassing the proxy). Removing the `NEXT_PUBLIC_` prefix without migrating those calls will break the app.

**Step 1 — Investigate first**:
```bash
grep -r "apiClient" --include="*.tsx" --include="*.ts" app/
```
Count the number of client components that call `apiClient` directly.

**Step 2a — If ≤ 5 files** (full fix, recommended):
1. Migrate those client components to use `fetch('/api/proxy/...')` instead of `apiClient`.
2. Remove `NEXT_PUBLIC_` prefix from both env vars in all route handlers and `.env.example` / `.env.local`.
3. Add a comment at the top of `lib/api/client.ts` explaining it is now server-only.

**Step 2b — If > 5 files** (partial fix for this sprint):
1. Remove `NEXT_PUBLIC_` **only from the three route handler files** listed above — this is the immediate security fix.
2. Leave `lib/api/client.ts` unchanged.
3. Open a follow-on ticket: "Migrate remaining `apiClient` calls to `/api/proxy/` and remove `NEXT_PUBLIC_` from env files."

**Acceptance**: Zero `NEXT_PUBLIC_ENGINE_API_URL` / `NEXT_PUBLIC_ADMIN_SERVICE_URL` references in files under `app/api/` regardless of chosen scope. Chosen scope documented in a comment in `lib/api/client.ts`.

---

### A-4 · Fix `start/[id]/page.tsx` to use proxy
**Audit ref**: AP-4  
**Severity**: HIGH — fails in Docker / production  
**File**: `app/(platform)/processes/start/[id]/page.tsx`

**Change**: Replace `apiClient.get(...)` with `fetch('/api/proxy/engine/...')` using the same URL path pattern used by all other pages.

```typescript
// Before
const { data } = useQuery({
  queryKey: ['process-form', id],
  queryFn: () => apiClient.get(`/repository/process-definitions/${id}/startFormData`).then(r => r.data)
})

// After
const { data } = useQuery({
  queryKey: ['process-form', id],
  queryFn: async () => {
    const res = await fetch(`/api/proxy/engine/repository/process-definitions/${id}/startFormData`)
    if (!res.ok) throw new Error('Failed to fetch form data')
    return res.json()
  }
})
```

Remove the `apiClient` import from this file if it is no longer used after the change.

**Acceptance**: Process start page loads in Docker without network errors.

---

### A-5 · Fix auth redirect guard
**Audit ref**: AP-3  
**Severity**: MEDIUM — momentary redirect flicker for SUPER_ADMIN users  
**Files**: `app/(platform)/admin/platform/tenants/page.tsx:165–169`, `app/(platform)/admin/platform/tenants/new/page.tsx:39–45`

**Change**: Gate redirect on session status, not just role value.

```typescript
// Before
useEffect(() => {
  if (!isSuperAdmin) router.replace('/dashboard')
}, [isSuperAdmin, router])

// After
const { status } = useSession()
useEffect(() => {
  if (status !== 'loading' && !isSuperAdmin) router.replace('/dashboard')
}, [status, isSuperAdmin, router])
```

**Acceptance**: SUPER_ADMIN user navigating directly to `/admin/platform/tenants` does not see a redirect flash.

---

## Track B — Design Consistency

These are the visual and component consistency fixes. Work top-to-bottom within this track.

---

### B-1 · Unify CSS token families (DS-1)
**Audit ref**: DS-1  
**Severity**: MEDIUM  
**File**: `app/globals.css` (`:root` block, lines 8–30 approximately)

**Change**: Replace the `--wf-*` hex literal block with computed aliases over the shadcn HSL variables, exactly as specified in `DESIGN.md §2.2`. Only tokens that have a direct shadcn equivalent become aliases; unique tokens (`--wf-brand`, `--wf-canvas`, sidebar tokens, etc.) keep their hex values.

The `--panel-*` block (lines ~40–90) and `--badge-*` block (lines ~132–146) remain unchanged — they are already correct.

**Do not change** the shadcn HSL variable block (`--background`, `--primary`, etc.) — that is the source of truth.

**Decision**: Derive `--wf-accent-dk` / `--wf-accent-lt` / `--wf-accent-bg` from `--primary` using `color-mix()` so they auto-follow any primary hue change. No manual updates needed if the brand colour changes.

```css
/* Tints derived from --primary — auto-follow hue changes.
   color-mix(in oklch) supported: Chrome 111+, Safari 16.2+, Firefox 113+ */
--wf-accent-dk: color-mix(in oklch, hsl(var(--primary)) 85%, black);
--wf-accent-lt: color-mix(in oklch, hsl(var(--primary)) 25%, white);
--wf-accent-bg: color-mix(in oklch, hsl(var(--primary)) 8%,  white);
```

**Acceptance**: Visually identical render. `grep -r '#149ba5\|#0f1e2a\|#6b7e8c\|#e2eaee\|#f0f4f6' --include='*.css' app/globals.css` returns zero matches in the `--wf-*` alias block (only in the panel/badge blocks where they are still valid hex seeds, or in comments).

---

### B-2 · Refactor Processes page — inline styles → Tailwind + shadcn (DS-2)
**Audit ref**: DS-2  
**Severity**: HIGH — largest visual consistency debt  
**File**: `app/(platform)/processes/page.tsx` (1,247 lines)

This is the largest task. Break it into sub-steps and commit each separately.

#### B-2a · Extract sub-components into separate files

Create these files (move existing inline component definitions):

| New file | Extracted from |
|---|---|
| `app/(platform)/processes/_components/DeployedCard.tsx` | `DeployedCard` function (~line 629) |
| `app/(platform)/processes/_components/DraftCard.tsx` | `DraftCard` function (~line 933) |
| `app/(platform)/processes/_components/GuideSection.tsx` | `GuideSection` function (~line 1113) |

Keep imports consistent. No logic changes — extraction only.

#### B-2b · Remove local design token constants

Delete the `ACCENT`, `T`, `TAG_PALETTE`, `FALLBACK_COLORS`, `iconBtn`, `tagPill` constants at the top of the file (~lines 23–113). These will be replaced by Tailwind classes and CSS variable references.

**`TAG_PALETTE` exception**: The per-tag colour mapping is data-driven (Approval=blue, Legal=purple, etc.) and cannot be expressed as static Tailwind classes. Keep `TAG_PALETTE` and `tagColor()` but move them to `app/(platform)/processes/_utils/tagColors.ts`. The resulting colour value is used only as a runtime `style={{ color }}` on the tag pill — this is an approved use of inline style for a dynamic runtime value.

#### B-2c · Replace raw `<button>` + hover state booleans with shadcn `Button`

In `DeployedCard`, replace the 6 hover boolean `useState` pairs (`hoverEdit`, `hoverDel`, `hoverConn`, `hoverDmn`, `hoverNotif`, `hoverForm`) with CSS group utilities:

```tsx
// Before — in DeployedCard
const [hoverEdit, setHoverEdit] = useState(false)
// …
<button
  style={{ ...iconBtn, background: hoverEdit ? T.bg : '#fff' }}
  onMouseEnter={() => setHoverEdit(true)}
  onMouseLeave={() => setHoverEdit(false)}
>
  <Pencil size={14} color={ACCENT} />
</button>

// After
<Button
  variant="ghost"
  size="icon"
  className="h-[30px] w-[30px] rounded-md border border-border hover:bg-muted"
  asChild={false}
>
  <Pencil size={14} className="text-primary" />
</Button>
```

The delete action button specifically uses the destructive ghost pattern from `DESIGN.md §7.1`:
```tsx
<Button variant="ghost" size="sm" className="h-[30px] w-[30px] text-destructive hover:bg-destructive/10 hover:text-destructive rounded-md border border-border">
  <Trash2 size={14} />
</Button>
```

#### B-2d · Replace inline-styled filter pills with `FilterPills`

The tag/category filter at the top of the process list currently uses the bespoke `tagPill()` helper and `activeTag: string | null` state — **single-select only** (confirmed by code review). Replace with the shared `<FilterPills>` component from `components/ui/filter-pills.tsx`, which is also single-select. Map the existing `activeTag` / `setActiveTag` state directly to `FilterPills`' `active` / `onChange` props. No behaviour change needed.

**Note**: `forms/page.tsx` and `decisions/page.tsx` use the identical `activeTag` pattern with the same inline-styled pills. Apply the same `FilterPills` replacement to both those pages in this step.

#### B-2e · Consolidate toast to `sonner`

Line 19–20 imports both `useToast` and `toast`. Remove `useToast` import and the `const { toast: uiToast } = useToast()` call. Replace all `uiToast(…)` calls with `toast.success(…)` / `toast.error(…)` from sonner.

**Acceptance**: `processes/page.tsx` and its sub-components contain zero `style={{}}` calls for static colour or layout values. The page is visually identical to its current state. No console errors. All interactive states (hover, active, focus) work correctly.

---

### B-3 · Fix ConfirmDialog destructive button override (DS-5)
**Audit ref**: DS-5  
**Severity**: LOW — one line  
**File**: `components/ui/confirm-dialog.tsx:57`

**Change**: Remove the inline style override.

```tsx
// Before
<Button
  variant={variant}
  style={variant === 'destructive' ? { backgroundColor: '#BA3920', color: 'white' } : undefined}
  onClick={handleConfirm}
>

// After
<Button
  variant={variant}
  onClick={handleConfirm}
>
```

`variant="destructive"` already renders `bg-destructive text-destructive-foreground` from the shadcn token. `--destructive` is `hsl(0 84% 56%)` which is `#dc2626`. The hex `#BA3920` used in the override is a non-system colour — its removal is intentional.

**Acceptance**: Destructive confirm button renders teal-system red (`--destructive`), not the bespoke `#BA3920`.

---

### B-4 · Fix delete button contrast in tenant table (DS-3)
**Audit ref**: DS-3  
**Severity**: MEDIUM  
**File**: `app/(platform)/admin/platform/tenants/page.tsx:322–330`

**Change**: Add `hover:bg-destructive/10` to the delete button's className.

```tsx
// Before
<Button variant="ghost" className="... text-destructive hover:text-destructive">

// After
<Button variant="ghost" className="... text-destructive hover:bg-destructive/10 hover:text-destructive">
```

**Acceptance**: Delete button in tenant table shows a light red background on hover, matching the pattern already used in `admin/connectors/page.tsx:209`.

---

### B-5 · Replace monitoring `StatusPill` with `StatusBadge` (DS-4)
**Audit ref**: DS-4  
**Severity**: LOW  
**File**: `app/(platform)/monitoring/page.tsx:28–55`

**Change**:
1. Delete the local `STATUS_CONFIG` object and `StatusPill` component.
2. Add `--badge-neutral-*` tokens to `app/globals.css` alongside the other `--badge-*` entries:

```css
/* In app/globals.css :root — add alongside other --badge-* tokens */
--badge-neutral:        hsl(220 13% 40%);
--badge-neutral-bg:     hsl(220 13% 91%);
--badge-neutral-border: hsl(220 13% 80%);
```

3. Add `UP` / `DOWN` / `DEGRADED` / `draft` mappings to `STATUS_STYLES` in `components/ui/status-badge.tsx`:

```typescript
// In status-badge.tsx STATUS_STYLES
UP:       { bg: 'var(--badge-success-bg)', color: 'var(--badge-success)', border: 'var(--badge-success-border)', label: 'Healthy' },
DOWN:     { bg: 'var(--badge-danger-bg)',  color: 'var(--badge-danger)',  border: 'var(--badge-danger-border)',  label: 'Down' },
DEGRADED: { bg: 'var(--badge-warning-bg)', color: 'var(--badge-warning)', border: 'var(--badge-warning-border)', label: 'Degraded' },
draft:    { bg: 'var(--badge-neutral-bg)', color: 'var(--badge-neutral)', border: 'var(--badge-neutral-border)', label: 'Draft' },
```

4. Replace `<StatusPill status={…} />` with `<StatusBadge status={…} />` in the monitoring page.

**Acceptance**: Monitoring page status pills use `--badge-*` tokens. `draft` status uses `--badge-neutral-*`. No raw hex in `monitoring/page.tsx` or `status-badge.tsx`.

---

### B-6 · Replace `window.confirm` with `ConfirmDialog` (AP-5)
**Audit ref**: AP-5  
**Severity**: MEDIUM  
**Files**:
- `app/(platform)/admin/tenant/custody-mappings/page.tsx:407`
- `app/(platform)/admin/tenant/approval-authority/page.tsx:235, 348`
- `app/(platform)/admin/tenant/role-mappings/page.tsx:223`

**Change**: For each `window.confirm(…)` call:
1. Add a state pair: `const [confirmOpen, setConfirmOpen] = useState(false)` and `const [pendingId, setPendingId] = useState<string | null>(null)`.
2. Replace the inline `window.confirm` + action with: set `pendingId` + `setConfirmOpen(true)`.
3. Render `<ConfirmDialog>` at the bottom of the JSX, wired to the state.

**Acceptance**: Zero `window.confirm` calls in the codebase. Delete actions in the three pages use `ConfirmDialog`.

---

### B-7 · Replace inline delete Dialogs with `ConfirmDialog` (COMP-1)
**Audit ref**: COMP-1  
**Severity**: MEDIUM  
**Files**:
- `app/(platform)/admin/tenant/credentials/page.tsx:233–257`
- `app/(platform)/admin/connectors/page.tsx:264–283`

**Change**: Delete the inline `<Dialog>` blocks and replace with `<ConfirmDialog>` using the same state wiring pattern as B-6.

**Acceptance**: Both pages use `<ConfirmDialog>` for delete confirmation. No inline `<Dialog>` delete reimplementations remain.

---

### B-8 · Migrate raw `<table>` to shadcn `Table` primitives (COMP-2)
**Audit ref**: COMP-2  
**Severity**: LOW  
**Files**:
- `app/(platform)/admin/platform/tenants/page.tsx:273–280`
- `app/(platform)/admin/tenant/users/page.tsx:206–213`
- `app/(platform)/admin/tenant/departments/page.tsx:103–111`
- `app/(platform)/admin/tenant/credentials/page.tsx:155–165`

**Change**: In each file, replace the raw `<table><thead><tr className="border-b border-border bg-muted/40"><th className="text-left px-4 py-3 font-semibold text-muted-foreground">` pattern with shadcn Table primitives:

```tsx
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

<Table>
  <TableHeader>
    <TableRow>
      <TableHead>Column Name</TableHead>
    </TableRow>
  </TableHeader>
  <TableBody>
    {items.map(item => (
      <TableRow key={item.id}>
        <TableCell>{item.value}</TableCell>
      </TableRow>
    ))}
  </TableBody>
</Table>
```

**Note**: Do not change cell content or data logic — only the wrapping markup changes.

**Acceptance**: All four pages use shadcn Table primitives. Visual output is identical to current.

---

### B-9 · Extract shared `EmptyState` component (COMP-3)
**Audit ref**: COMP-3  
**Severity**: LOW  
**Files to create**: `components/ui/empty-state.tsx`  
**Files to update**: `admin/platform/tenants/page.tsx:251–267`, `admin/tenant/users/page.tsx:191–201`, `admin/connectors/page.tsx:174–186`

**New component**:

```tsx
// components/ui/empty-state.tsx
import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description?: string
  action?: ReactNode
}

export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center justify-center py-16 text-center gap-3">
        <Icon className="text-muted-foreground" size={32} strokeWidth={1.5} />
        <div>
          <p className="font-semibold text-foreground">{title}</p>
          {description && (
            <p className="text-sm text-muted-foreground mt-1">{description}</p>
          )}
        </div>
        {action}
      </CardContent>
    </Card>
  )
}
```

Replace the three copy-pasted empty state blocks with `<EmptyState>`.

---

### B-10 · Consolidate to `sonner` toast (AP-6)
**Audit ref**: AP-6  
**Severity**: MEDIUM  
**Scope**: ~12 files

**Steps**:
1. In `app/providers.tsx`: ensure only the `sonner` `<Toaster>` is present. Remove `@/components/ui/toaster` (Radix) import and its `<Toaster>` render.
2. Search for all `useToast` imports (`grep -r "useToast\|use-toast" --include="*.tsx" --include="*.ts"`).
3. In each file: remove `useToast` import, remove `const { toast } = useToast()`, replace `toast({ title, description, variant })` calls with `toast.success(…)` / `toast.error(…)` from sonner.
4. Do not delete `hooks/use-toast.ts` until all call sites are migrated (delete it in the final step to confirm no remaining references).

**Known call sites** (from audit): `processes/page.tsx`, `admin/email-templates/[key]/page.tsx`, `tasks/page.tsx`, and ~9 additional files.

**Acceptance**: `grep -r "use-toast\|useToast" --include="*.tsx" --include="*.ts"` returns zero results. All toast notifications appear from the same position with consistent styling.

---

### B-11 · Fix `useEffect` derived state anti-patterns (AP-2)
**Audit ref**: AP-2  
**Severity**: MEDIUM  
**Files**:
- `app/(platform)/processes/start/[id]/page.tsx:58–60`
- `app/(platform)/processes/page.tsx:196–207`

**Change 1** (`start/[id]`): Replace `useEffect` that copies React Query result into state.

```typescript
// Before
const [formData, setFormData] = useState({})
useEffect(() => {
  if (initialFormData) setFormData(initialFormData)
}, [initialFormData])

// After
const [formData, setFormData] = useState(initialFormData ?? {})
```

If `initialFormData` is async (from React Query), use `useState(() => initialFormData ?? {})` and handle updates via the query directly.

**Change 2** (`processes/page.tsx`): Error toast `useEffect` blocks that fire on every render where `error` is truthy. **Confirmed: project uses React Query v5.59.0** — `onError` on query options is removed in v5. Use render-time error handling:

```typescript
// Option A — render-time error display (preferred for page-level data)
const { data, error, isLoading } = useQuery({ queryKey: ['processes'], queryFn: fetchProcesses })
if (error) return <ErrorDisplay error={error} />

// Option B — non-blocking toast where partial data must still show
const errorShown = useRef(false)
useEffect(() => {
  if (error && !errorShown.current) {
    errorShown.current = true
    toast.error('Failed to load processes')
  }
}, [error])
```

Use **Option A** for page-level data fetching. Use **Option B** only where the error must be a non-blocking toast alongside partial data.

---

### B-12 · Delete orphaned `layout-client.tsx` (COMP-4 / AP-8)
**Audit ref**: AP-8 + COMP-4  
**Severity**: LOW  
**File**: `app/(platform)/layout-client.tsx`

**Verify first**: Confirm `StudioLayoutClient` is not imported anywhere (`grep -r "StudioLayoutClient\|layout-client" --include="*.tsx" --include="*.ts"`). If zero results, delete the file.

**Acceptance**: File does not exist. No import errors.

---

### B-13 · Consolidate token extraction to `useAuth()` (AP-7)
**Audit ref**: AP-7  
**Severity**: MEDIUM — depends on A-1 being complete first  
**Scope**: 13+ files

**Prerequisite**: A-1 (type augmentation) must be complete.

**After A-1**, `useAuth()` from `lib/auth/auth-context.tsx` exposes a typed `token` field. Replace all instances of:
```typescript
const token = (session?.accessToken as string) ?? ''
```
with:
```typescript
const { token } = useAuth()
```

Or, for pages that can switch to proxy-based fetching (which handles auth server-side), remove the token extraction entirely and replace `apiClient` / manual `Authorization` header calls with `fetch('/api/proxy/...')`.

**Files** (non-exhaustive — search with `grep -r "accessToken as string" --include="*.tsx"`):
- `admin/tenant/role-mappings/page.tsx`
- `admin/tenant/departments/page.tsx`
- `admin/tenant/custody-mappings/page.tsx`
- `monitoring/page.tsx`
- And ~9 more

---

## Track C — Library Customisation Hardening

These protect the library theme from breaking on upgrades.

---

### C-1 · Annotate dmn-js internal class overrides (LIB-1)
**Audit ref**: LIB-1  
**Severity**: MEDIUM  
**Files**: `app/globals.css:494–596`, `components/dmn/dmn-overrides.css:1–37`

**Change**: For each CSS rule targeting an undocumented internal class name (`.tjs-table`, `.dms-select-options`, `.add-rule`, `.add-input`, `.add-output`, `.decision-table-name`, `.dmn-definitions`):

1. Check whether a `--decision-table-*` official variable already covers the same property. If yes, remove the internal class override and use the variable instead.
2. If no official variable exists, keep the override but add a comment:

```css
/* dmn-js INTERNAL — not in public CSS variable API.
   Class: .tjs-table  Library: dmn-js  Verified: v14.x
   If this breaks after upgrade, check: https://github.com/bpmn-io/dmn-js/blob/develop/CHANGELOG.md */
```

**Acceptance**: Every internal class override in both files has either been replaced with an official variable or has the annotation comment. No visual change.

---

### C-2 · Resolve `bio-properties-panel-header` `!important` (LIB-2)
**Audit ref**: LIB-2  
**Severity**: LOW  
**File**: `app/globals.css:316–330`

**Root cause**: `globals.css` loads before the library CSS, so the library's own variable declaration on `.bio-properties-panel` wins source order for the header background.

**Preferred fix**: In the BPMN editor component (`components/bpmn/BpmnEditor.tsx` or equivalent), import the library CSS explicitly:

```typescript
// At the top of BpmnEditor.tsx — before any local imports
import 'bpmn-js/dist/assets/bpmn-js.css'
import '@bpmn-io/properties-panel/assets/properties-panel.css'
```

This makes library CSS load first in the component bundle, so `globals.css` wins source order naturally. After confirming the header background is still dark (`var(--panel-hdr-bg)`), remove the `!important` from the `.bio-properties-panel-header` rules in `globals.css`.

**Fallback if import reordering is not possible**: Raise specificity instead of using `!important`:
```css
/* Before */
.bio-properties-panel-header { background-color: var(--panel-hdr-bg) !important; }

/* After */
.werkflow-props-panel .bio-properties-panel-header,
.bio-properties-panel .bio-properties-panel-header {
  background-color: var(--panel-hdr-bg);
}
```

**Acceptance**: Header renders with dark background. Zero `!important` declarations in the `globals.css` panel section.

---

### C-3 · Type `DmnEditor` modeler ref (LIB-1 adjacent)
**Audit ref**: LIB-1 boundary map  
**Severity**: LOW  
**File**: `components/dmn/DmnEditor.tsx:64`

**Change**: Replace `modelerRef: any` with the dmn-js type if available, or a narrow interface:

```typescript
interface DmnModelerInstance {
  importXML: (xml: string) => Promise<{ warnings: unknown[] }>
  saveXML: (options?: { format?: boolean }) => Promise<{ xml: string }>
  destroy: () => void
  getActiveViewer: () => { get: (name: string) => unknown } | null
}

const modelerRef = useRef<DmnModelerInstance | null>(null)
```

Check `types/dmn-js.d.ts` first — if a type is already declared there, use it directly.

---

## Summary Table

| ID | Track | Severity | Effort | Depends on |
|---|---|---|---|---|
| A-1 | A — Correctness | HIGH | XS | — |
| A-2 | A — Security | HIGH | XS | — |
| A-3 | A — Security | HIGH | S | — |
| A-4 | A — Correctness | HIGH | S | — |
| A-5 | A — Correctness | MEDIUM | XS | — |
| B-1 | B — Design | MEDIUM | S | — |
| B-2 | B — Design | HIGH | L | — |
| B-3 | B — Design | LOW | XS | — |
| B-4 | B — Design | MEDIUM | XS | — |
| B-5 | B — Design | LOW | S | — |
| B-6 | B — Design | MEDIUM | S | — |
| B-7 | B — Design | MEDIUM | S | — |
| B-8 | B — Design | LOW | M | — |
| B-9 | B — Design | LOW | S | — |
| B-10 | B — Design | MEDIUM | M | — |
| B-11 | B — Design | MEDIUM | S | — |
| B-12 | B — Design | LOW | XS | — |
| B-13 | B — Design | MEDIUM | M | A-1 |
| C-1 | C — Libraries | MEDIUM | S | — |
| C-2 | C — Libraries | LOW | S | — |
| C-3 | C — Libraries | LOW | XS | — |

**Effort key**: XS < 30 min · S = 30–90 min · M = 90 min–half day · L = half day+

---

## Out of Scope

The following are explicitly not in this handover:

- Any new features or pages
- Redesign of existing visual patterns (the current aesthetic is approved)
- Dark mode support
- i18n string changes
- Test coverage (existing Playwright/Vitest suite should not be broken, but adding new tests is not required)
- Unlayer email editor theming (currently functional; listed in DESIGN.md §10 for future reference only)
- Performance optimisation

## Pre-Start Resolution Notes

All ambiguities resolved. No open questions before starting.

| # | Topic | Resolution |
|---|---|---|
| 1 | `--wf-accent-dk` / `--wf-accent-lt` | Derive via `color-mix(in oklch, …)` from `--primary` — see B-1 |
| 2 | `draft` status colour | Add `--badge-neutral-*` tokens to `globals.css` — see B-5 |
| 3 | FilterPills multi-select | Confirmed single-select (`string \| null`). `FilterPills` is a direct drop-in. Also apply to `forms/page.tsx` and `decisions/page.tsx` — see B-2d |
| 4 | React Query version | Confirmed v5.59.0. Use render-time error handling (Option A) — see B-11 |
| 5 | `NEXT_PUBLIC_` scope | Claude Code to investigate `apiClient` call count and choose scope — see A-3 |
