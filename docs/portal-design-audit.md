# Werkflow Portal — Design Audit Report

**Date**: 2026-06-10 (session 30)
**Scope**: Full portal — all pages, shared components, library customisations
**Status**: Read-only audit. No code changes. All items queued for implementation.

---

## Executive Summary

The portal demonstrates a mature foundation: a well-structured CSS token system, consistent use of shadcn/ui primitives across most admin pages, and a correctly implemented proxy architecture for server-side auth forwarding. The weakest areas are a dual-system design inconsistency on the Processes page (100 inline `style={{}}` calls versus Tailwind everywhere else), a pervasive `(session as any)` pattern that bypasses the entire next-auth type system across 13+ files, and a mix of two toast libraries (`sonner` and `@/hooks/use-toast`) used interchangeably within the same feature area. Three specific visual defects called out in the brief are confirmed and precisely located.

---

## Findings

### DS-1 — Design System: Parallel Token Systems (--wf-* vs shadcn CSS variables)

**Severity**: MEDIUM
**Files**: `app/globals.css:8-147`, `tailwind.config.ts:24-53`
**Pattern**: The design system maintains two independent token families: 24 `--wf-*` hex-value tokens (e.g. `--wf-accent: #149ba5`) and the shadcn HSL variable set (`--primary: 184 78% 36%`). These represent the same colours expressed in two different formats with no cross-reference. Components consuming Tailwind classes (`text-primary`, `bg-accent`) use the HSL path; components consuming `globals.css` BEM-style classes use the `--wf-*` path; and some components (notably `monitoring/page.tsx` and the combobox styles in `globals.css:601–655`) hardcode raw hex values directly.
**Impact**: A brand colour update must be applied in three places to stay consistent. The monitoring page's `StatusPill` component uses raw hex `#f0fdf4`, `#16a34a` etc. directly in a `STATUS_CONFIG` object, bypassing both token systems entirely.
**Recommendation**: Declare the `--wf-*` tokens as computed aliases over the shadcn HSL variables (`--wf-accent: hsl(var(--primary))`), or the reverse. Eliminate the third class of raw hex literals in component-level `style` objects by routing them through either token family.

---

### DS-2 — Design System: Processes Page Uses Inline Styles Exclusively

**Severity**: HIGH
**Files**: `app/(platform)/processes/page.tsx:23–113`, `app/(platform)/processes/page.tsx:272–1247`
**Pattern**: The Processes page contains 100 `style={{}}` JSX attribute calls and only 10 Tailwind `className` calls. It defines local constants `ACCENT = '#149ba5'`, a `T` object of hardcoded hex/hsl strings, `TAG_PALETTE` with 10 hardcoded colour hex values, and `iconBtn` / `tagPill` CSSProperties helpers. Buttons are rendered as raw `<button>` elements and `<Link>` components with manually written hover state via `useState` pairs (`hoverEdit`, `hoverDel`, `hoverConn`, `hoverDmn`, `hoverNotif`, `hoverForm`) — 6 separate booleans in `DeployedCard` alone.
**Impact**: This page is visually inconsistent with every other page in the application. Six hover-state booleans per card prevent keyboard focus styles. Raw `<button>` and `<a>` elements miss the accessibility baseline that the shared `Button` and shadcn components provide. The page is 1,247 lines, well above the 800-line budget.
**Recommendation**: Replace the inline style/constant approach with Tailwind classes and `shadcn/ui Button` components. Replace the 6 hover boolean pairs with CSS `group` + `group-hover:` utilities. Extract `DeployedCard` and `DraftCard` into separate files.

---

### DS-3 — Design System: Delete Button Contrast (Tenant Table)

**Severity**: MEDIUM
**Files**: `app/(platform)/admin/platform/tenants/page.tsx:322–330`
**Pattern**: The Delete button uses `variant="ghost"` with `className="... text-destructive hover:text-destructive"`. The ghost variant applies no background by default, so the button sits on the white table row. The `--destructive` token resolves to `hsl(0 84% 56%)` (red `#dc2626`) against white — this achieves approximately 4.6:1 contrast, borderline AA. The reported "teal background with red text" is visible when the row renders inside the rounded table container (`bg-muted/40` thead background bleeds into the card's rounded border, creating a perceived teal context on certain viewport sizes).
**Impact**: Low contrast in action button labels degrades readability for users with moderate visual impairment. The ghost variant without a visible boundary also makes the button hard to identify as interactive.
**Recommendation**: Apply `hover:bg-destructive/10` consistently (already used correctly in `admin/connectors/page.tsx:209`). For explicit destructive row actions, use `variant="ghost"` with both `text-destructive` and `hover:bg-destructive/10`.

---

### DS-4 — Design System: Monitoring Page Uses Hardcoded Hex in STATUS_CONFIG

**Severity**: LOW
**Files**: `app/(platform)/monitoring/page.tsx:28–31`
**Pattern**: `STATUS_CONFIG` uses raw hex strings `#f0fdf4`, `#16a34a`, `#bbf7d0`, `#dc2626`, `#fef2f2`, `#fecaca`, `#fffbeb`, `#c27b00`, `#fde68a` directly inside a JS object, and the `StatusPill` uses these via inline `style`. These are identical to the `--badge-*` tokens already defined in `globals.css:132–146`.
**Impact**: If the badge colour scale changes, this component will not update.
**Recommendation**: Replace JS string constants with CSS custom property references: `background: 'var(--badge-success-bg)'` etc., consistent with `components/ui/status-badge.tsx:4–8`.

---

### DS-5 — Design System: ConfirmDialog Overrides shadcn Button with Inline Style

**Severity**: LOW
**Files**: `components/ui/confirm-dialog.tsx:57`
**Pattern**: `style={variant === 'destructive' ? { backgroundColor: '#BA3920', color: 'white' } : undefined}` overrides `variant="destructive"` with a non-token hex colour not present anywhere else in the design system.
**Impact**: The confirm dialog destructive button diverges from all other destructive actions. Will not respond to theme changes.
**Recommendation**: Remove the inline `style` override. `variant="destructive"` already renders the correct colour from `--destructive`.

---

### AP-1 — Anti-Pattern: `(session as any)` Pervasive Across Auth Layer

**Severity**: HIGH
**Files**: `lib/auth/auth-context.tsx:38,46,91,98,99`, `auth.config.ts:73,86–101,147,168,184`, `app/providers.tsx:45`, `app/api/auth/logout/route.ts:6`, `components/auth/token-expired-dialog.tsx:25,28,32`, `app/(platform)/layout-client.tsx:12`
**Pattern**: Custom Keycloak claims (`accessToken`, `idToken`, `roles`, `tenantId`, `doa_level`, `department`, `error`) added in `auth.config.ts` callbacks are never declared in a next-auth module augmentation. Every consumer casts `session as any` or `token as any` to access them. This pattern appears 17 times across the codebase.
**Impact**: Zero TypeScript safety for the auth layer. A rename of `accessToken` in the JWT callback would produce no compile error but break every consumer silently.
**Recommendation**: Expand `types/next-auth.d.ts` to declare all custom fields on `Session`, `JWT`, and `User`. The `KeycloakJWTPayload` interface already in `auth.config.ts:6` should extend `JWT` rather than `JWTPayload`.

---

### AP-2 — Anti-Pattern: `useEffect` for Derived State

**Severity**: MEDIUM
**Files**: `app/(platform)/processes/start/[id]/page.tsx:58–60`, `app/(platform)/processes/page.tsx:196–207`
**Pattern**: `useEffect(() => { if (initialFormData) setFormData(initialFormData) }, [initialFormData])` copies React Query result into a separate `useState`. Error toast `useEffect` blocks in `processes/page.tsx` fire on every render where `error` is truthy — React Query v5 documents this as an anti-pattern.
**Impact**: Unnecessary re-renders. Error toasts may fire multiple times.
**Recommendation**: Initialise `formData` directly with `useState(initialFormData ?? {})`. Handle error toasts via React Query `meta.onError` or `throwOnError`.

---

### AP-3 — Anti-Pattern: Auth Redirect Fires Before Session Loads

**Severity**: MEDIUM
**Files**: `app/(platform)/admin/platform/tenants/page.tsx:165–169`, `app/(platform)/admin/platform/tenants/new/page.tsx:39–45`
**Pattern**: `useEffect(() => { if (!isSuperAdmin) { router.replace('/dashboard') } }, [isSuperAdmin, router])` fires when `user` is `null` (session loading), because `hasRole` returns `false` before the session resolves — causing a redirect for all users on initial render.
**Impact**: SUPER_ADMIN users see a momentary redirect before the session resolves and the component re-renders correctly.
**Recommendation**: Gate the redirect: `if (status !== 'loading' && !isSuperAdmin) router.replace('/dashboard')`.

---

### AP-4 — Anti-Pattern: Direct `apiClient` Bypasses Proxy in `start/[id]/page.tsx`

**Severity**: HIGH
**Files**: `app/(platform)/processes/start/[id]/page.tsx:15,47–54`, `lib/api/client.ts:11–13`
**Pattern**: `start/[id]/page.tsx` uses the Axios `apiClient` (targets `NEXT_PUBLIC_ENGINE_API_URL` directly) inside a React Query `queryFn`. The default is `http://localhost:8081`, which is unreachable from the browser in Docker. All other pages call `/api/proxy/engine/...` through the Next.js route handler which correctly attaches the bearer token server-side.
**Impact**: In Docker or any production deployment, these calls fail silently with a network error.
**Recommendation**: Replace `apiClient.get(url, ...)` with `fetch('/api/proxy/engine/...')`, consistent with all other pages.

---

### AP-5 — Anti-Pattern: `window.confirm` for Destructive Actions

**Severity**: MEDIUM
**Files**: `app/(platform)/admin/tenant/custody-mappings/page.tsx:407`, `app/(platform)/admin/tenant/approval-authority/page.tsx:235,348`, `app/(platform)/admin/tenant/role-mappings/page.tsx:223`
**Pattern**: Three admin pages call `window.confirm(...)` inside `onClick` handlers for delete/remove actions. The rest of the admin suite uses `ConfirmDialog`.
**Impact**: Cannot be styled, fails in SSR/test environments, blocks the main thread. Visually inconsistent.
**Recommendation**: Replace with `<ConfirmDialog>` from `components/ui/confirm-dialog.tsx`.

---

### AP-6 — Anti-Pattern: Dual Toast Libraries Used Interchangeably

**Severity**: MEDIUM
**Files**: `app/(platform)/processes/page.tsx:19–20`, `app/(platform)/admin/email-templates/[key]/page.tsx:11`, `app/(platform)/tasks/page.tsx:14`, and ~10 additional files
**Pattern**: `toast` from `'sonner'` and `useToast` from `'@/hooks/use-toast'` (shadcn Radix-based) used in parallel. `processes/page.tsx` imports both in the same file (lines 19–20).
**Impact**: Notifications appear from different positions and with different visual treatment depending on which code path triggered them.
**Recommendation**: Consolidate on `sonner` (already installed). Remove `@/hooks/use-toast` and the Radix `<Toaster>` provider. Migrate ~12 `useToast` call sites.

---

### AP-7 — Anti-Pattern: Token Extraction Duplicated Across 13+ Pages

**Severity**: MEDIUM
**Files**: `admin/tenant/role-mappings/page.tsx:75`, `admin/tenant/departments/page.tsx:41`, `admin/tenant/custody-mappings/page.tsx:203`, `monitoring/page.tsx:59`, and 10 additional files
**Pattern**: Every page independently calls `const token = (session?.accessToken as string) ?? ''`. The `useAuth()` hook already exposes `token` directly.
**Impact**: Token extraction logic duplicated across 13+ files. Root cause is AP-1 (missing type augmentation).
**Recommendation**: Resolve AP-1 first, then pages can use `useAuth().token` or switch to proxy calls that handle auth server-side.

---

### AP-8 — Anti-Pattern: `layout-client.tsx` Contains Orphaned Feature Code

**Severity**: LOW
**Files**: `app/(platform)/layout-client.tsx:1–110`
**Pattern**: `StudioLayoutClient` is not mounted anywhere in the current route tree (`(platform)/layout.tsx` uses only `AppShell`). Contains a dead `useEffect` with a potential infinite re-render loop (`canAccessRoute` in its own dependency array).
**Recommendation**: Delete the file. If needed for a future Studio layout, re-introduce with correct types and fixed dependency array.

---

### SEC-1 — Security: Internal URLs Leaked via `NEXT_PUBLIC_` Server-Side Route Handlers

**Severity**: HIGH
**Files**: `app/api/health/route.ts:25–26`, `app/api/proxy/engine/[...path]/route.ts:4`, `app/api/proxy/admin/[...path]/route.ts:4`
**Pattern**: Route handlers (server-side) read `NEXT_PUBLIC_ENGINE_API_URL` and `NEXT_PUBLIC_ADMIN_SERVICE_URL`. The `NEXT_PUBLIC_` prefix bundles these values into the client-side JS bundle, exposing internal Docker hostnames (e.g. `engine:8081`) to any authenticated user who inspects `__NEXT_DATA__`.
**Recommendation**: Use `ENGINE_API_URL` / `ADMIN_SERVICE_URL` (no `NEXT_PUBLIC_` prefix) in route handlers. Retain `NEXT_PUBLIC_` variants only if client components genuinely need them.

---

### SEC-2 — Security: `dangerouslySetInnerHTML` in ProcessFlowBackdrop

**Severity**: LOW
**Files**: `components/layout/process-flow-backdrop.tsx:261`
**Pattern**: `dangerouslySetInnerHTML={{ __html: svg }}` where `svg` is a hardcoded string literal in the same file — no current XSS risk.
**Impact**: None at present. Future maintainers may add an `svg` prop without realising the injection surface.
**Recommendation**: Convert the static SVG to JSX `<svg>` element.

---

### SEC-3 — Security: `/api/health` Route Is Unauthenticated

**Severity**: HIGH
**Files**: `app/api/health/route.ts`
**Pattern**: The route handler calls the engine and admin health endpoints and returns their status without any `auth()` check. An unauthenticated browser request returns service status and component details, leaking infrastructure topology.
**Recommendation**: Add `const session = await auth()` and return `401` if no session.

---

### LIB-1 — Library Customisation: dmn-js Overrides Target Internal Class Names

**Severity**: MEDIUM
**Files**: `components/dmn/dmn-overrides.css:1–37`, `app/globals.css:494–596`
**Pattern**: Overrides target `.decision-table-name`, `.dmn-definitions`, `.tjs-table`, `.dms-select-options`, `.add-rule`, `.add-input`, `.add-output` — all internal implementation class names not part of the documented dmn-js public CSS variable API. The variable API is already used correctly in `globals.css:449–483`.
**Impact**: A dmn-js minor/patch update renaming internal classes will silently break the visual theme.
**Recommendation**: Remove overrides where a `--decision-table-*` variable already exists. Mark remaining internal overrides with `/* dmn-js INTERNAL — verify on upgrade */`.

---

### LIB-2 — Library Customisation: BPMN Panel Header Uses `!important`

**Severity**: LOW
**Files**: `app/globals.css:316–330`
**Pattern**: `.bio-properties-panel-header { background-color: var(--panel-hdr-bg) !important; ... }` — required because library CSS loads after `globals.css`. Sets a precedent for `!important` overrides.
**Recommendation**: Load library CSS before `globals.css` by importing at the component level, or raise selector specificity (`.werkflow-props-panel .bio-properties-panel-header`) to avoid `!important`.

---

### COMP-1 — Reusability: Inline Delete Confirmation Dialog Duplicated

**Severity**: MEDIUM
**Files**: `app/(platform)/admin/tenant/credentials/page.tsx:233–257`, `app/(platform)/admin/connectors/page.tsx:264–283`
**Pattern**: Both pages re-implement `<Dialog>` delete confirmation manually. `components/ui/confirm-dialog.tsx` already exists and is used on the tenants and processes pages.
**Recommendation**: Replace inline `<Dialog>` blocks with `<ConfirmDialog>`.

---

### COMP-2 — Reusability: Raw `<table>` Instead of shadcn Table Primitives

**Severity**: LOW
**Files**: `admin/platform/tenants/page.tsx:273–280`, `admin/tenant/users/page.tsx:206–213`, `admin/tenant/departments/page.tsx:103–111`, `admin/tenant/credentials/page.tsx:155–165`
**Pattern**: All four pages implement the same raw `<thead><tr className="border-b border-border bg-muted/40"><th className="text-left px-4 py-3 font-semibold text-muted-foreground">` structure. `components/ui/table.tsx` (shadcn) is installed but unused on all four pages.
**Recommendation**: Adopt `Table`, `TableHeader`, `TableRow`, `TableHead`, `TableBody`, `TableCell` from the existing shadcn primitive.

---

### COMP-3 — Reusability: Empty-State Pattern Copy-Pasted

**Severity**: LOW
**Files**: `admin/platform/tenants/page.tsx:251–267`, `admin/tenant/users/page.tsx:191–201`, `admin/connectors/page.tsx:174–186`
**Pattern**: Identical `<Card><CardContent className="flex flex-col items-center justify-center py-16 text-center">` structure with icon, title, description, and optional CTA button.
**Recommendation**: Extract a shared `EmptyState` component accepting `icon`, `title`, `description`, and optional `action` slot.

---

### COMP-4 — Component Design: Orphaned `layout-client.tsx`

**Severity**: LOW
**Files**: `app/(platform)/layout-client.tsx:1–110`
**Pattern**: `StudioLayoutClient` is not mounted anywhere. 110 lines of dead code including a loading spinner, access-denied card, and route-access check with a potential infinite re-render loop.
**Recommendation**: Delete. See AP-8.

---

## Component Inventory

| Component | Location | Approx. Reuse | Verdict |
|---|---|---|---|
| `ConfirmDialog` | `components/ui/confirm-dialog.tsx` | 4 uses + 2 inline duplicates | OK — needs wider adoption |
| `PageSurface` | `components/layout/page-surface.tsx` | ~20 pages | OK |
| `StatusBadge` | `components/ui/status-badge.tsx` | ~5 uses | OK — uses CSS tokens correctly |
| `Button` | `components/ui/button.tsx` | ~80+ uses | OK |
| `StatCard` | `components/ui/stat-card.tsx` | tasks, dashboard | OK |
| `FilterPills` | `components/ui/filter-pills.tsx` | tasks page | OK — could be reused on processes page |
| `ErrorDisplay` | `components/ui/error-display.tsx` | ~3 uses | OK |
| `AvatarCell` | `components/ui/avatar-cell.tsx` | limited | OK |
| `PriorityBadge` | `components/ui/priority-badge.tsx` | tasks | OK |
| `ComingSoonPage` | `components/ui/coming-soon-page.tsx` | 2 pages | OK |
| `AppShell` | `components/layout/app-shell.tsx` | 1 (layout) | OK |
| `Sidebar` | `components/layout/sidebar.tsx` | via AppShell | OK |
| `TopBar` | `components/layout/topbar.tsx` | via AppShell | OK |
| `StudioLayoutClient` | `app/(platform)/layout-client.tsx` | **0** | **NEEDS REMOVAL — orphaned, dead** |
| `DeployedCard` (inline) | `app/(platform)/processes/page.tsx:629` | 1 | **NEEDS EXTRACTION** |
| `DraftCard` (inline) | `app/(platform)/processes/page.tsx:933` | 1 | **NEEDS EXTRACTION** |
| `GuideSection` (inline) | `app/(platform)/processes/page.tsx:1113` | 1 | **NEEDS EXTRACTION** |
| Delete Dialog (inline) | `admin/tenant/credentials/page.tsx:233` | 1 | **DUPLICATE of ConfirmDialog** |
| Delete Dialog (inline) | `admin/connectors/page.tsx:264` | 1 | **DUPLICATE of ConfirmDialog** |
| `StatusPill` (inline) | `monitoring/page.tsx:33` | 1 | **NEEDS REFACTOR — raw hex, conflicts with StatusBadge** |

---

## Library Customisation Boundary Map

| Library | Customisation Location | Method | Verdict |
|---|---|---|---|
| bpmn-js canvas/palette | `app/globals.css:172–233` | CSS vars on `.djs-*` classes | OK — stable documented classes |
| @bpmn-io/properties-panel | `app/globals.css:244–309` | Official CSS variable API | OK — correct extension point |
| @bpmn-io/properties-panel header | `app/globals.css:316–330` | Direct class override + `!important` | WARN — CSS load order issue |
| bpmn-js Flowable extension | `lib/bpmn/flowable-properties-module.ts` | `additionalModules` + `moddleExtensions` | OK — official extension points |
| dmn-js decision table | `app/globals.css:449–483` | Official `--decision-table-*` variables | OK |
| dmn-js decision table | `app/globals.css:494–596` | Internal class names (`.tjs-table`, `.dms-select-options`) | WARN — not public API |
| dmn-js overrides | `components/dmn/dmn-overrides.css` | Internal class names (`.decision-table-name`) | WARN — not public API |
| form-js palette | `lib/forms/createPaletteFilterModule.ts` | `additionalModules` | OK — official extension point |
| DmnEditor init | `components/dmn/DmnEditor.tsx:64` | UMD dist import + `modelerRef: any` | WARN — `modelerRef` untyped |

---

## Priority Fix Order

Ranked by impact × effort (highest ROI first):

| # | Finding | Effort | Impact |
|---|---|---|---|
| 1 | **AP-1** — Add `next-auth.d.ts` augmentation | XS | HIGH — eliminates 17 `as any` casts, unblocks AP-7 |
| 2 | **SEC-3** — Add `auth()` guard to `/api/health` route | XS | HIGH — unauthenticated info disclosure |
| 3 | **AP-4** — Replace `apiClient` in `start/[id]` with proxy fetch | S | HIGH — Docker-unreachable direct call |
| 4 | **SEC-1** — Remove `NEXT_PUBLIC_` from server-only URL vars | S | HIGH — internal hostname leak to browser bundle |
| 5 | **DS-2** — Refactor Processes page (100 inline styles → Tailwind + shadcn) | L | HIGH — largest visual consistency debt |
| 6 | **AP-6** — Consolidate toast to `sonner`, remove `useToast` | M | MEDIUM — notification UX consistency |
| 7 | **AP-5** — Replace `window.confirm` in 3 admin pages with `ConfirmDialog` | S | MEDIUM |
| 8 | **COMP-1** — Replace inline delete dialogs with `ConfirmDialog` | S | MEDIUM |
| 9 | **DS-1** — Alias `--wf-*` tokens over shadcn HSL vars | M | MEDIUM — token unification |
| 10 | **LIB-1** — Audit dmn-js internal class overrides | S | MEDIUM — upgrade fragility |
| 11 | **DS-3 + DS-5** — Delete button contrast + ConfirmDialog inline style | XS | LOW–MEDIUM — one-liners each |
| 12 | **AP-2 + AP-3** — Fix `useEffect` derived state + redirect guard | S | MEDIUM |
| 13 | **COMP-2 + COMP-3** — shadcn Table primitives + EmptyState extraction | M | LOW |
| 14 | **COMP-4** — Delete orphaned `layout-client.tsx` | XS | LOW |
