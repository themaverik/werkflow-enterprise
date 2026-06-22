# Werkflow Portal — Design System Reference

**Version**: 1.0 · **Status**: Canonical  
**Scope**: Werkflow-native pages + controlled extension of bpmn-js, dmn-js, form-js, and Unlayer  
**Stack**: Next.js 14 · shadcn/ui · Tailwind CSS · DM Sans · JetBrains Mono

---

## 1. Principles

1. **One change, everywhere.** All colour, spacing, and type decisions live in CSS custom properties in `app/globals.css`. No raw hex/hsl literals in component files — route them through a token.
2. **Tailwind first, inline styles never (for static values).** Tailwind classes are the implementation language for layout, spacing, colour, and type on all Werkflow-native pages. `style={{}}` is permitted only for genuinely runtime-dynamic values (e.g. a per-item accent colour computed from data).
3. **shadcn/ui primitives everywhere.** Raw `<button>`, `<table>`, `<input>` elements are replaced by their shadcn equivalents. Custom behaviour is composed on top, never re-implemented from scratch.
4. **Library customisation at the documented boundary.** bpmn-js, dmn-js, form-js, and Unlayer are themed via their published CSS variable APIs and official extension points. Internal class names are a last resort and must be annotated.
5. **Minimal delta.** This document describes what the system *is*, not a redesign. Changes are corrections to bring the implementation in line with the already-established visual direction.

---

## 2. Token System

### 2.1 Architecture: two families, one source of truth

The codebase currently maintains two parallel colour families:

| Family | Format | Consumed by |
|---|---|---|
| `--wf-*` | Hex literals | Panel CSS, BEM-style classes, inline `style={}` |
| shadcn HSL vars (`--primary`, `--border`, …) | `H S% L%` (no `hsl()` wrapper) | Tailwind classes (`text-primary`, `bg-border`, …) |

**Target state:** `--wf-*` tokens become *computed aliases* over the shadcn HSL variables. The shadcn HSL vars remain the single source of truth. Changing `--primary` propagates to both Tailwind classes and all `--wf-*` references automatically.

### 2.2 Canonical token mapping (`app/globals.css` `:root`)

Replace the hex literals in the `--wf-*` block with these aliases. Tokens that have no shadcn equivalent keep their hex value but are documented as primary.

```css
/* ── Werkflow tokens (aliases over shadcn vars) ── */
--wf-accent:    hsl(var(--primary));
--wf-accent-dk: color-mix(in oklch, hsl(var(--primary)) 85%, black);  /* derived — auto-follows --primary */
--wf-accent-lt: color-mix(in oklch, hsl(var(--primary)) 25%, white);  /* derived — auto-follows --primary */
--wf-accent-bg: color-mix(in oklch, hsl(var(--primary)) 8%,  white);  /* derived — auto-follows --primary */

--wf-brand:   #111c27;                     /* sidebar/header bg — primary brand dark */
--wf-brand-2: #1a2d3d;                     /* secondary brand dark */

--wf-text:    hsl(var(--foreground));
--wf-text-2:  hsl(211 36% 28%);            /* no shadcn equiv */
--wf-muted:   hsl(var(--muted-foreground));
--wf-light:   hsl(207 18% 71%);            /* no shadcn equiv */
--wf-border:  hsl(var(--border));
--wf-border-2: hsl(210 26% 93%);           /* subtler — no shadcn equiv */
--wf-bg:      hsl(var(--background));
--wf-card:    hsl(var(--card));
--wf-canvas:  hsl(210 20% 93%);            /* editor canvas — no shadcn equiv */

--wf-success:    hsl(142 72% 37%);
--wf-success-bg: hsl(138 76% 97%);
--wf-warning:    hsl(38 100% 38%);
--wf-warning-bg: hsl(48 100% 97%);
--wf-danger:     hsl(var(--destructive));
--wf-danger-bg:  hsl(0 84% 97%);
--wf-info:       hsl(221 83% 53%);
--wf-info-bg:    hsl(214 100% 97%);
--wf-purple:     hsl(263 70% 50%);
```

**Rule:** The `--panel-*` tokens that feed library theming remain derived from `--wf-*` tokens (already correct in the codebase). Do not add a third layer.

### 2.3 Badge tokens

The `--badge-*` set is the canonical source for all semantic status colours (used by `StatusBadge`, `StatusPill`, `PriorityBadge`). No component may hardcode these colours as hex literals.

```
--badge-success / --badge-success-bg / --badge-success-border
--badge-warning / --badge-warning-bg / --badge-warning-border
--badge-danger  / --badge-danger-bg  / --badge-danger-border
--badge-blue    / --badge-blue-bg    / --badge-blue-border
--badge-teal    / --badge-teal-bg    / --badge-teal-border
--badge-neutral / --badge-neutral-bg / --badge-neutral-border  /* grey — for draft / inactive states */
```

### 2.4 Panel tokens (shared editor theming)

`--panel-*` tokens are the single source of truth for bpmn-js, dmn-js, form-js, and Unlayer theming. They are already defined correctly in `globals.css`. **Do not override panel colours directly in component files** — modify the `--panel-*` token instead.

---

## 3. Typography

| Role | Family | Size | Weight | Tailwind |
|---|---|---|---|---|
| Body default | DM Sans | 14px (0.875rem) | 400 | `text-sm` |
| Body medium | DM Sans | 14px | 500 | `text-sm font-medium` |
| Label / caption | DM Sans | 12px | 500 | `text-xs font-medium` |
| Page title (H1) | DM Sans | 20px | 700 | `text-xl font-bold` |
| Section heading (H2) | DM Sans | 16px | 600 | `text-base font-semibold` |
| Card heading | DM Sans | 14px | 600 | `text-sm font-semibold` |
| Stat value | DM Sans | 24px | 700 | `text-2xl font-bold` |
| Panel label | DM Sans | 12px | 500 | via `--panel-fs-label` |
| Panel value / input | DM Sans | 12px | 400 | via `--panel-fs` |
| Code / expression | JetBrains Mono | 12px | 400–600 | via `--panel-font` override |
| Nav section label | DM Sans | 11px | 600, uppercase, 0.08em tracking | sidebar only |

**Rules:**
- Never use font sizes below 11px.
- `font-semibold` (600) is the maximum weight for UI labels. `font-bold` (700) is reserved for numeric stat values and page titles only.
- Line heights: `leading-none` for stats, `leading-snug` (1.375) for titles, `leading-normal` (1.5) for body.

---

## 4. Colour Palette

### Brand

| Token | Value | Usage |
|---|---|---|
| `--wf-accent` / `hsl(var(--primary))` | `#149ba5` | Primary interactive colour — buttons, links, focus rings, active states |
| `--wf-brand` | `#111c27` | Sidebar, editor panel headers |
| `--wf-brand-2` | `#1a2d3d` | Secondary brand dark surface |

### Surfaces

| Token | Tailwind | Usage |
|---|---|---|
| `hsl(var(--background))` | `bg-background` | App canvas (`#f0f4f6` equivalent) |
| `hsl(var(--card))` | `bg-card` | White page surface, table rows |
| `hsl(var(--muted))` | `bg-muted` | Table header rows, disabled fields, subtle backgrounds |

### Text

| Token | Tailwind | Usage |
|---|---|---|
| `hsl(var(--foreground))` | `text-foreground` | Primary body text |
| `hsl(var(--muted-foreground))` | `text-muted-foreground` | Secondary / helper text, table column headers |

### Semantic

| Intent | Text token | Background token | Tailwind equivalent |
|---|---|---|---|
| Success | `--badge-success` | `--badge-success-bg` | — use CSS vars |
| Warning | `--badge-warning` | `--badge-warning-bg` | — use CSS vars |
| Danger / Destructive | `hsl(var(--destructive))` | `--badge-danger-bg` | `text-destructive` |
| Info | `--badge-blue` | `--badge-blue-bg` | — use CSS vars |
| Brand teal | `--badge-teal` | `--badge-teal-bg` | — use CSS vars |

### Sidebar

Sidebar uses its own scoped token set (`--sidebar-*`). Do not use generic foreground/background tokens inside the sidebar; use the sidebar tokens so the dark surface can be independently themed.

---

## 5. Spacing

Spacing follows Tailwind's default 4px grid. These are the standard usages:

| Context | Value | Tailwind |
|---|---|---|
| Page padding (PageSurface interior) | 24px | `p-6` |
| Card interior padding | 20px | `p-5` |
| Section gap within a page | 24px | `gap-6` or `space-y-6` |
| Between cards in a grid | 16px | `gap-4` |
| Form field gap | 16px | `gap-4` |
| Inline element gap (icon + label) | 8px | `gap-2` |
| Table cell padding | 12px 16px | `py-3 px-4` |
| Button icon-only | 10px all | `p-2.5` |

---

## 6. Radius & Shadow

| Token | Value | Tailwind | Usage |
|---|---|---|---|
| `--wf-radius` / `--radius` | 8px | `rounded-lg` | Cards, modals, inputs |
| `--wf-radius-sm` | 6px | `rounded-md` | Buttons, badges, chips |
| `--wf-radius-lg` | 12px | `rounded-xl` | Stat cards, large panels |
| `--wf-shadow-sm` | `0 1px 2px rgba(15,30,42,0.04)` | `shadow-sm` | Table rows, inputs |
| `--wf-shadow` | `0 2px 8px rgba(15,30,42,0.06)` | `shadow` | Cards, dropdowns |
| `--wf-shadow-lg` | `0 12px 40px rgba(15,30,42,0.12)` | `shadow-lg` | Modals, popovers |

### Interactive Cards

Cards that respond to user attention (clickable rows, grid tiles, marketplace items) use the canonical hover treatment via the `.wf-card-interactive` utility class.

**Tokens** (declared in `globals.css`):
- `--wf-card-hover-shadow` — drop shadow on hover
- `--wf-card-hover-border` — accent-tinted border on hover
- `--wf-card-hover-lift` — vertical translate on hover (negative px value)
- `--wf-card-transition-duration` — 150ms

**Usage:**

```tsx
<Card className="wf-card-interactive">...</Card>
// or for non-shadcn cards:
<article className="rounded-lg border bg-card wf-card-interactive">...</article>
```

**When to apply:** cards that represent an actionable entity — i.e. cards containing one or more action buttons (Install, Edit, Test, View, Toggle) that operate on the entity the card represents. The hover lift signals "this row has actions you can take." Pure-display cards with no actions inside them (info banners, status-only panels, loading skeletons) MUST NOT use this — hover would be confusing.

**Canonical reference:** Marketplace connector cards (`app/(platform)/admin/marketplace/page.tsx`).

---

## 7. Components

All components in `components/ui/` are shadcn-based primitives. Extend them; do not re-implement.

### 7.1 Button

**Source:** `components/ui/button.tsx`

| Variant | Usage |
|---|---|
| `default` | Primary action (teal fill) |
| `destructive` | Permanent destructive action. **Never** override with inline `style={{ backgroundColor }}` — the `--destructive` token handles it. |
| `outline` | Secondary action, cancel |
| `ghost` | Tertiary / icon actions in tables and toolbars |
| `link` | In-text navigation |

| Size | Height | Usage |
|---|---|---|
| `default` | 40px (`h-10`) | Standard page actions |
| `sm` | 36px (`h-9`) | Table row actions, compact toolbars |
| `icon` | 40×40px | Toolbar icon-only buttons |

**Destructive ghost pattern** (table row delete):
```tsx
<Button variant="ghost" size="sm" className="text-destructive hover:bg-destructive/10 hover:text-destructive">
  Delete
</Button>
```
This is the only approved pattern for row-level delete actions. Do not use `variant="ghost"` with `text-destructive` without `hover:bg-destructive/10`.

### 7.2 Badge

**Source:** `components/ui/badge.tsx`  
Use for category labels, version tags, role labels. Not for status — use `StatusBadge` for that.

`success` and `warning` variants in badge.tsx currently use raw Tailwind green/yellow. **These should be updated** to use `--badge-success` / `--badge-warning` tokens via inline style or a CSS variable reference for consistency (see CLAUDE_HANDOVER §Track A, item 3).

### 7.3 StatusBadge

**Source:** `components/ui/status-badge.tsx`  
Already correctly uses `--badge-*` CSS custom property tokens. This is the canonical pattern for all status indicators across the portal.

**Do not create a second status component.** The inline `StatusPill` in `monitoring/page.tsx` duplicates this. It must be replaced with `StatusBadge` (see CLAUDE_HANDOVER §DS-4).

Status → token mapping:
```
active    → --badge-blue-*
completed → --badge-success-*
suspended → --badge-warning-*
failed    → --badge-danger-*
draft     → --badge-neutral-*
UP        → --badge-success-*
DOWN      → --badge-danger-*
DEGRADED  → --badge-warning-*
```

### 7.4 Table

**Source:** `components/ui/table.tsx` (shadcn — installed but underused)

Standard table anatomy:
```tsx
<Table>
  <TableHeader>
    <TableRow>
      <TableHead>Column</TableHead>
    </TableRow>
  </TableHeader>
  <TableBody>
    <TableRow>
      <TableCell>Value</TableCell>
    </TableRow>
  </TableBody>
</Table>
```

Do not use raw `<thead>/<tr>/<th>` with manually written Tailwind classes. The four pages using the raw pattern (tenants, users, departments, credentials) must be migrated.

### 7.5 ConfirmDialog

**Source:** `components/ui/confirm-dialog.tsx`

The only approved confirm/delete dialog. Current bug: the destructive variant overrides `variant="destructive"` with `style={{ backgroundColor: '#BA3920' }}`. This inline style must be removed — `variant="destructive"` already renders the correct colour from `--destructive`.

Three admin pages (`custody-mappings`, `approval-authority`, `role-mappings`) still use `window.confirm()`. Replace with `<ConfirmDialog>`. Two pages (`credentials`, `connectors`) re-implement inline `<Dialog>` delete flows. Replace with `<ConfirmDialog>`.

### 7.6 EmptyState

**Status:** Does not exist as a shared component. Must be extracted.

Standard empty-state structure used across at least 3 pages:
```tsx
<Card>
  <CardContent className="flex flex-col items-center justify-center py-16 text-center gap-3">
    <Icon className="text-muted-foreground" size={32} strokeWidth={1.5} />
    <div>
      <p className="font-semibold text-foreground">{title}</p>
      <p className="text-sm text-muted-foreground mt-1">{description}</p>
    </div>
    {action}
  </CardContent>
</Card>
```

Extract to `components/ui/empty-state.tsx` with props: `icon`, `title`, `description`, `action?: ReactNode`.

### 7.7 FilterPills

**Source:** `components/ui/filter-pills.tsx`  
Already correctly uses Tailwind + shadcn colour tokens. Currently used only on the tasks page. Can be reused on the processes page to replace the bespoke inline-styled tag filter.

### 7.8 StatCard

**Source:** `components/ui/stat-card.tsx`  
Correctly uses Tailwind. The `iconColor` prop accepts a raw hex/hsl string for the per-stat accent — this is one of the approved uses of a runtime colour value, since it varies per card instance.

### 7.9 PageSurface

**Source:** `components/layout/page-surface.tsx`  
Standard white content canvas. All page-level content that is not an editor canvas should be wrapped in `<PageSurface>`. Current markup: `bg-card border border-border rounded-lg p-6`. Do not replicate this pattern manually.

---

## 8. Layout

### 8.1 AppShell

`components/layout/app-shell.tsx` composes `Sidebar` + `TopBar` + content area. All `(platform)` routes render through this shell.

### 8.2 Sidebar

- Width: `w-56` (224px), fixed.
- Background: `var(--sidebar-bg)` (`#111c27`).
- Uses `--sidebar-*` token family exclusively.
- Active nav item: `var(--sidebar-active-bg)` (teal at 22% opacity) + white text.
- Hover: `var(--sidebar-hover-bg)` (white at 5% opacity).
- Section labels: 11px, uppercase, 0.08em tracking, `var(--sidebar-dim-text)` (white 35%).

### 8.3 TopBar

- Height: `h-14` (56px).
- Background: `bg-card` (white).
- Right slot: notifications bell + `UserMenu`.
- No per-page content in the topbar — page titles live in the page body.

---

## 9. Page Anatomy Pattern

Every standard page follows this structure:

```tsx
// app/(platform)/[section]/page.tsx
export default function SectionPage() {
  return (
    <div className="flex flex-col gap-6 p-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">Page Title</h1>
          <p className="text-sm text-muted-foreground mt-1">Optional subtitle</p>
        </div>
        <Button>Primary Action</Button>
      </div>

      {/* Optional stat row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard … />
      </div>

      {/* Main content */}
      <PageSurface>
        {/* Filter bar, table, list, etc. */}
      </PageSurface>
    </div>
  )
}
```

---

## 10. Library Customisation Boundaries

### Approved extension points (use freely)

| Library | Mechanism |
|---|---|
| `@bpmn-io/properties-panel` | `--*` CSS variable API on `.bio-properties-panel` |
| `bpmn-js` canvas | CSS on stable `.djs-*` class names |
| `bpmn-js` behaviour | `additionalModules` + `moddleExtensions` in modeler config |
| `dmn-js` | `--decision-table-*` CSS variable API on `.dmn-decision-table-container` |
| `form-js` | `additionalModules` (palette filter, custom renderers) |
| `form-js` theme | `--form-js-*` CSS variables (if exposed) or `.fjs-*` stable class names |
| Unlayer | Theme object passed to `EmailEditor` config (`appearance`, `colors`) |

### Internal class overrides (use only as last resort)

These target undocumented internals. Each override must be annotated with a comment:

```css
/* dmn-js INTERNAL — verify on upgrade: <class> not in public API as of vX.Y */
```

Current internal overrides that need annotation:
- `globals.css:494–596` — `.tjs-table`, `.dms-select-options`, `.add-rule`, `.add-input`, `.add-output`
- `components/dmn/dmn-overrides.css` — `.decision-table-name`, `.dmn-definitions`

**LIB-2 resolution:** The `.bio-properties-panel-header` `!important` override exists because library CSS loads after `globals.css`. The correct fix is to import library CSS at the component level (inside the BPMN/DMN editor components) so `globals.css` wins source order, eliminating the need for `!important`.

### Unlayer (email templates)

Unlayer's theming surface is the config object passed to `EmailEditor`. Map `--wf-*` tokens to Unlayer's theme options:
- `primaryColor` → `--wf-accent` value
- `backgroundColor` → `--wf-bg` value  
- Font → `DM Sans` via Unlayer's custom fonts config

Do not override Unlayer's internal CSS class names — the library's DOM structure is considered unstable.

---

## 11. Toast Notifications

**Canonical library: `sonner`**

Use `import { toast } from 'sonner'` everywhere. `@/hooks/use-toast` (shadcn Radix toaster) is deprecated and must be removed.

| Pattern | Code |
|---|---|
| Success | `toast.success('Message')` |
| Error | `toast.error('Message')` |
| Info | `toast.info('Message')` |
| Loading + result | `toast.promise(fn, { loading, success, error })` |

The `<Toaster>` in `app/providers.tsx` should be the sonner `Toaster` only (from `'sonner'`). Remove the Radix `<Toaster>` from `@/components/ui/toaster`.

---

## 12. Forbidden Patterns

| Pattern | Replace with |
|---|---|
| `style={{ color: '#149ba5' }}` (static colour) | `className="text-primary"` or `style={{ color: 'var(--wf-accent)' }}` |
| `style={{ backgroundColor: '#BA3920' }}` on `variant="destructive"` | Remove — variant handles it |
| Raw `<button>` with hover via `useState` | `<Button>` from shadcn |
| Raw `<table><thead><tr>…` with manual Tailwind | `<Table><TableHeader>…` from shadcn |
| `window.confirm(…)` | `<ConfirmDialog>` |
| Inline `<Dialog>` delete re-implementation | `<ConfirmDialog>` |
| `useToast` from `@/hooks/use-toast` | `toast` from `sonner` |
| Duplicate inline `StatusPill` / status colour objects | `<StatusBadge>` |
| `const token = (session?.accessToken as string) ?? ''` | `useAuth().token` (after AP-1 type fix) |
| 6× `useState` hover booleans per card | CSS `group` + `group-hover:` utilities |
| Empty-state copy-paste | `<EmptyState>` shared component |
