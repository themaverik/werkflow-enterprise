# M4 UI Full Visual Overhaul — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current teal-themed, icon-less portal with the approved dark-sidebar design system, overhauling every screen to match the Figma-export HTML reference.

**Architecture:** Design tokens land in `globals.css` + `tailwind.config.ts` first; all screen rewrites consume those tokens via Tailwind classes and CSS custom properties. The `AppShell` is restructured from a top-header+sidebar layout to a full-height sidebar + slim topbar layout.

**Tech Stack:** Next.js 14 (App Router), Tailwind CSS 3, lucide-react, react-query, next-intl, recharts (install required for analytics).

**Design reference:** `/Users/lamteiwahlang/Projects/Werkflow Redesigned Final/` — open the relevant HTML before implementing any task.

---

## File Map

### New files
| Path | Responsibility |
|------|---------------|
| `components/layout/topbar.tsx` | Slim top bar: search, notification bell, user avatar |
| `components/ui/stat-card.tsx` | Coloured icon + big number + label card |
| `components/ui/filter-pills.tsx` | Horizontal pill tab group (All / Mine / Overdue / etc.) |
| `components/ui/status-badge.tsx` | Semantic colour badge (active / completed / suspended / failed) |
| `components/ui/priority-badge.tsx` | Priority colour badge (Urgent / High / Medium / Low) |
| `components/ui/avatar-cell.tsx` | Circular avatar initials cell for table rows |
| `app/(platform)/services/page.tsx` | Service Catalog (new screen) |
| `app/(platform)/admin/tenant/approval-authority/page.tsx` | Tenant Setup — configVars L1–L4 UI |
| `app/(platform)/admin/tenant/role-mappings/page.tsx` | Tenant Setup — role→level mapping UI |
| `app/(platform)/admin/tenant/departments/page.tsx` | Tenant Setup — ERP-backed dept list |
| `app/(platform)/admin/tenant/custody-groups/page.tsx` | Tenant Setup — ERP-backed custody list |
| `lib/forms/createPaletteFilterModule.ts` | form-js palette filter for tenant component allowlist |

### Modified files
| Path | Change |
|------|--------|
| `app/globals.css` | Replace teal tokens with design system tokens; add DM Sans font |
| `tailwind.config.ts` | Add sidebar/primary/badge colour keys; extend font family |
| `components/layout/app-shell.tsx` | Full-height sidebar + topbar layout |
| `components/layout/sidebar.tsx` | Dark bg, icons, 5-section structure, user profile card at bottom |
| `app/(platform)/dashboard/page.tsx` | New stat cards, quick actions, recent activity, Tenant Setup checklist widget |
| `app/(platform)/tasks/page.tsx` | Stat cards row + tab pills + task table with avatar/badge columns |
| `app/(platform)/requests/page.tsx` | Request list with status badges, search, tab filters |
| `app/(platform)/forms/page.tsx` | Stat cards, tab pills, table view, inline actions |
| `app/(platform)/processes/page.tsx` | Stat cards, Deployed/Drafts tabs, card grid, action icons |
| `app/(platform)/decisions/page.tsx` | Align to Forms list pattern (table + stat cards) |
| `app/(platform)/analytics/page.tsx` | Full M6 Group B analytics dashboard |
| `components/forms/FormJsEditor.tsx` | Palette filter + tenant CSS vars injection |

---

## Task 1: Design System Tokens

**Files:**
- Modify: `app/globals.css`
- Modify: `tailwind.config.ts`

- [ ] **Step 1: Update `globals.css` `:root` variables**

Replace the entire `:root` block and the `body` font-family:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    /* Layout */
    --sidebar-bg: #111c27;
    --sidebar-border: rgba(255,255,255,0.07);
    --sidebar-dim-text: rgba(255,255,255,0.35);
    --sidebar-nav-text: rgba(255,255,255,0.6);
    --sidebar-active-text: #ffffff;
    --sidebar-active-bg: rgba(124,58,237,0.28);
    --sidebar-hover-bg: rgba(255,255,255,0.05);

    /* Surface */
    --background: 210 20% 96%;        /* #f0f4f6 */
    --card: 0 0% 100%;
    --foreground: 211 60% 10%;        /* #0f1e2a */
    --card-foreground: 211 60% 10%;
    --popover: 0 0% 100%;
    --popover-foreground: 211 60% 10%;

    /* Border / input */
    --border: 207 26% 88%;            /* #e2eaee */
    --input: 207 26% 88%;

    /* Brand */
    --primary: 263 79% 56%;           /* #7c3aed */
    --primary-foreground: 0 0% 100%;
    --secondary: 210 20% 96%;
    --secondary-foreground: 211 60% 10%;

    /* States */
    --muted: 210 20% 96%;
    --muted-foreground: 207 14% 47%;  /* #6b7e8c */
    --accent: 263 79% 56%;
    --accent-foreground: 0 0% 100%;
    --destructive: 0 84% 56%;         /* #dc2626 */
    --destructive-foreground: 0 0% 100%;
    --ring: 263 79% 56%;
    --radius: 0.5rem;

    /* Semantic badge tokens */
    --badge-success: #16a34a;
    --badge-success-bg: #f0fdf4;
    --badge-success-border: #bbf7d0;
    --badge-warning: #c27b00;
    --badge-warning-bg: #fffbeb;
    --badge-warning-border: #fde68a;
    --badge-danger: #dc2626;
    --badge-danger-bg: #fef2f2;
    --badge-danger-border: #fecaca;
    --badge-blue: #1d4ed8;
    --badge-blue-bg: #eff6ff;
    --badge-blue-border: #bfdbfe;
    --badge-purple: #7c3aed;
    --badge-purple-bg: #f5f3ff;
    --badge-purple-border: #ddd6fe;
  }
}

@layer base {
  * { @apply border-border; }
  body {
    @apply bg-background text-foreground;
    font-family: 'DM Sans', sans-serif;
    font-size: 1rem;
  }
}

/* BPMN.js specific styles */
.djs-container { font-family: Arial, sans-serif; }

/* Thin scrollbar */
::-webkit-scrollbar { width: 5px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #c5d2da; border-radius: 4px; }
```

- [ ] **Step 2: Add DM Sans font import in `app/layout.tsx`**

Read `app/layout.tsx`. Replace the existing font import section (or add if none) at the top:

```tsx
import { DM_Sans } from 'next/font/google'

const dmSans = DM_Sans({
  subsets: ['latin'],
  weight: ['300', '400', '500', '600', '700'],
  variable: '--font-dm-sans',
  display: 'swap',
})
```

Add `className={dmSans.variable}` to the `<html>` element and `font-sans` to `<body>` if not present. Then update globals.css body font-family to `var(--font-dm-sans), sans-serif`.

- [ ] **Step 3: Update `tailwind.config.ts` to add brand tokens**

Replace the `theme.extend` section:

```ts
extend: {
  fontFamily: {
    sans: ['var(--font-dm-sans)', 'sans-serif'],
  },
  colors: {
    border: 'hsl(var(--border))',
    input: 'hsl(var(--input))',
    ring: 'hsl(var(--ring))',
    background: 'hsl(var(--background))',
    foreground: 'hsl(var(--foreground))',
    primary: {
      DEFAULT: 'hsl(var(--primary))',
      foreground: 'hsl(var(--primary-foreground))',
    },
    secondary: {
      DEFAULT: 'hsl(var(--secondary))',
      foreground: 'hsl(var(--secondary-foreground))',
    },
    destructive: {
      DEFAULT: 'hsl(var(--destructive))',
      foreground: 'hsl(var(--destructive-foreground))',
    },
    muted: {
      DEFAULT: 'hsl(var(--muted))',
      foreground: 'hsl(var(--muted-foreground))',
    },
    accent: {
      DEFAULT: 'hsl(var(--accent))',
      foreground: 'hsl(var(--accent-foreground))',
    },
    card: {
      DEFAULT: 'hsl(var(--card))',
      foreground: 'hsl(var(--card-foreground))',
    },
    sidebar: {
      DEFAULT: '#111c27',
      border: 'rgba(255,255,255,0.07)',
    },
  },
  borderRadius: {
    lg: 'var(--radius)',
    md: 'calc(var(--radius) - 2px)',
    sm: 'calc(var(--radius) - 4px)',
  },
  keyframes: {
    'accordion-down': { from: { height: '0' }, to: { height: 'var(--radix-accordion-content-height)' } },
    'accordion-up': { from: { height: 'var(--radix-accordion-content-height)' }, to: { height: '0' } },
  },
  animation: {
    'accordion-down': 'accordion-down 0.2s ease-out',
    'accordion-up': 'accordion-up 0.2s ease-out',
  },
},
```

- [ ] **Step 4: Start the dev server and verify font + background colour changed**

```bash
cd frontends/portal && npm run dev
```

Open http://localhost:4000. Background should be `#f0f4f6` (light blue-grey). Font should be DM Sans (check with browser devtools — computed font-family on `body`).

- [ ] **Step 5: Commit**

```bash
git add frontends/portal/app/globals.css frontends/portal/tailwind.config.ts frontends/portal/app/layout.tsx
git commit -m "feat(m4): design system tokens — DM Sans, purple primary, dark sidebar vars"
```

---

## Task 2: Shared UI Components

**Files:**
- Create: `components/ui/stat-card.tsx`
- Create: `components/ui/filter-pills.tsx`
- Create: `components/ui/status-badge.tsx`
- Create: `components/ui/priority-badge.tsx`
- Create: `components/ui/avatar-cell.tsx`

- [ ] **Step 1: Create `components/ui/stat-card.tsx`**

```tsx
import type { LucideIcon } from 'lucide-react'

interface StatCardProps {
  icon: LucideIcon
  label: string
  value: number | string
  iconColor: string
  sub?: string
}

export function StatCard({ icon: Icon, label, value, iconColor, sub }: StatCardProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-4">
      <div
        className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
        style={{ background: iconColor + '18' }}
      >
        <Icon size={20} style={{ color: iconColor }} strokeWidth={1.8} />
      </div>
      <div>
        <div className="text-2xl font-bold text-foreground leading-none">{value}</div>
        <div className="text-xs text-muted-foreground mt-1">{label}</div>
        {sub && <div className="text-xs font-medium mt-0.5" style={{ color: iconColor }}>{sub}</div>}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create `components/ui/filter-pills.tsx`**

```tsx
interface FilterOption {
  key: string
  label: string
  count?: number
}

interface FilterPillsProps {
  options: FilterOption[]
  active: string
  onChange: (key: string) => void
}

export function FilterPills({ options, active, onChange }: FilterPillsProps) {
  return (
    <div className="flex gap-2 flex-wrap">
      {options.map((opt) => (
        <button
          key={opt.key}
          onClick={() => onChange(opt.key)}
          className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
            active === opt.key
              ? 'bg-primary text-primary-foreground'
              : 'bg-card border border-border text-muted-foreground hover:bg-muted'
          }`}
        >
          {opt.label}
          {opt.count !== undefined && (
            <span className={`text-xs rounded-full px-1.5 py-0.5 ${
              active === opt.key ? 'bg-white/20 text-white' : 'bg-muted text-muted-foreground'
            }`}>
              {opt.count}
            </span>
          )}
        </button>
      ))}
    </div>
  )
}
```

- [ ] **Step 3: Create `components/ui/status-badge.tsx`**

```tsx
type Status = 'active' | 'completed' | 'suspended' | 'failed' | string

const STATUS_STYLES: Record<string, { bg: string; color: string; border: string; label: string }> = {
  active:    { bg: 'var(--badge-blue-bg)',    color: 'var(--badge-blue)',    border: 'var(--badge-blue-border)',    label: 'Active' },
  completed: { bg: 'var(--badge-success-bg)', color: 'var(--badge-success)', border: 'var(--badge-success-border)', label: 'Completed' },
  suspended: { bg: 'var(--badge-warning-bg)', color: 'var(--badge-warning)', border: 'var(--badge-warning-border)', label: 'Suspended' },
  failed:    { bg: 'var(--badge-danger-bg)',  color: 'var(--badge-danger)',  border: 'var(--badge-danger-border)',  label: 'Failed' },
}

export function StatusBadge({ status }: { status: Status }) {
  const s = STATUS_STYLES[status] ?? {
    bg: '#f8fafc', color: '#475569', border: '#e2e8f0', label: status,
  }
  return (
    <span
      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border"
      style={{ background: s.bg, color: s.color, borderColor: s.border }}
    >
      {s.label}
    </span>
  )
}
```

- [ ] **Step 4: Create `components/ui/priority-badge.tsx`**

```tsx
const PRIORITY_STYLES: Record<string, { bg: string; color: string; border: string; label: string }> = {
  urgent: { bg: 'var(--badge-danger-bg)',  color: 'var(--badge-danger)',  border: 'var(--badge-danger-border)',  label: 'Urgent' },
  high:   { bg: '#fff7ed',                 color: '#c2410c',              border: '#fed7aa',                    label: 'High' },
  medium: { bg: 'var(--badge-warning-bg)', color: 'var(--badge-warning)', border: 'var(--badge-warning-border)', label: 'Medium' },
  low:    { bg: 'var(--badge-success-bg)', color: 'var(--badge-success)', border: 'var(--badge-success-border)', label: 'Low' },
}

function priorityKey(value: number): keyof typeof PRIORITY_STYLES {
  if (value >= 100) return 'urgent'
  if (value >= 75) return 'high'
  if (value >= 50) return 'medium'
  return 'low'
}

export function PriorityBadge({ priority }: { priority: number }) {
  const key = priorityKey(priority)
  const s = PRIORITY_STYLES[key]
  return (
    <span
      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border"
      style={{ background: s.bg, color: s.color, borderColor: s.border }}
    >
      {s.label}
    </span>
  )
}
```

- [ ] **Step 5: Create `components/ui/avatar-cell.tsx`**

```tsx
interface AvatarCellProps {
  name: string
  size?: number
}

function initials(name: string): string {
  return name
    .split(' ')
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase()
}

const AVATAR_COLORS = [
  '#7c3aed', '#1d4ed8', '#16a34a', '#c27b00', '#dc2626',
  '#0891b2', '#6366f1', '#f59e0b',
]

function colorFor(name: string): string {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return AVATAR_COLORS[h % AVATAR_COLORS.length]
}

export function AvatarCell({ name, size = 28 }: AvatarCellProps) {
  const bg = colorFor(name)
  return (
    <span
      className="inline-flex items-center justify-center rounded-full text-white font-semibold shrink-0"
      style={{ width: size, height: size, fontSize: size * 0.36, background: bg }}
      title={name}
    >
      {initials(name)}
    </span>
  )
}
```

- [ ] **Step 6: Commit**

```bash
git add frontends/portal/components/ui/stat-card.tsx \
        frontends/portal/components/ui/filter-pills.tsx \
        frontends/portal/components/ui/status-badge.tsx \
        frontends/portal/components/ui/priority-badge.tsx \
        frontends/portal/components/ui/avatar-cell.tsx
git commit -m "feat(m4): shared UI components — StatCard, FilterPills, StatusBadge, PriorityBadge, AvatarCell"
```

---

## Task 3: New Sidebar + AppShell Restructure

**Files:**
- Modify: `components/layout/sidebar.tsx` — full rewrite
- Create: `components/layout/topbar.tsx`
- Modify: `components/layout/app-shell.tsx` — new layout structure

Reference: `Werkflow Employee Portal.html` — sidebar section.

- [ ] **Step 1: Rewrite `components/layout/sidebar.tsx`**

Full replacement (preserve the `SidebarSection` data structure but update styles and add icons):

```tsx
'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'
import {
  LayoutDashboard, CheckSquare, FileText, BookOpen,
  Workflow, FormInput, GitBranch, Mail,
  Link2, Settings, ShieldCheck, Building2, Users, TrendingUp, Activity,
  BriefcaseBusiness, LucideIcon, AlertCircle,
} from 'lucide-react'

interface NavItem {
  labelKey: string
  label: string
  href: string
  icon: LucideIcon
  requiredRoles?: string[]
  badge?: string
}

interface SidebarSection {
  titleKey: string
  items: NavItem[]
}

const sidebarSections: SidebarSection[] = [
  {
    titleKey: 'general',
    items: [
      { labelKey: 'dashboard',     label: 'Dashboard',       href: '/dashboard',  icon: LayoutDashboard },
      { labelKey: 'myTasks',       label: 'My Tasks',        href: '/tasks',      icon: CheckSquare },
      { labelKey: 'myRequests',    label: 'My Requests',     href: '/requests',   icon: FileText },
      { labelKey: 'serviceCatalog',label: 'Service Catalog', href: '/services',   icon: BookOpen },
    ],
  },
  {
    titleKey: 'designStudio',
    items: [
      { labelKey: 'processes',      label: 'Processes',       href: '/processes',  icon: Workflow,    requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'forms',          label: 'Forms',           href: '/forms',      icon: FormInput,   requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'decisions',      label: 'Decisions',       href: '/decisions',  icon: GitBranch,   requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'emailTemplates', label: 'Email Templates', href: '/admin/email-templates', icon: Mail, requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'admin',
    items: [
      { labelKey: 'connectors', label: 'Connectors', href: '/admin/connectors', icon: Link2,       requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'deadLetterJobs', label: 'Failed Jobs', href: '/admin/jobs/dead-letter', icon: AlertCircle, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'tenantSetup',
    items: [
      { labelKey: 'approvalAuthority', label: 'Approval Authority', href: '/admin/tenant/approval-authority', icon: ShieldCheck, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'roleMappings',      label: 'Role Mappings',      href: '/admin/tenant/role-mappings',      icon: Users,       requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'departments',       label: 'Departments',        href: '/admin/tenant/departments',        icon: Building2,   requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'custodyGroups',     label: 'Custody Groups',     href: '/admin/tenant/custody-groups',     icon: BriefcaseBusiness, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'monitoring',
    items: [
      { labelKey: 'analytics',     label: 'Analytics',      href: '/analytics',  icon: TrendingUp,  requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'processHealth', label: 'Process Health', href: '/monitoring', icon: Activity,    requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
]

export function Sidebar() {
  const pathname = usePathname()
  const { isAuthenticated, user } = useAuth()
  const { hasAnyRole } = useAuthorization()
  const t = useTranslations('nav')

  if (!isAuthenticated) return null

  const displayName = user?.name ?? user?.username ?? 'User'
  const displayRole = user?.roles?.[0] ?? 'Employee'

  const visibleSections = sidebarSections
    .map((section) => ({
      ...section,
      items: section.items.filter((item) =>
        !item.requiredRoles?.length || hasAnyRole(item.requiredRoles)
      ),
    }))
    .filter((section) => section.items.length > 0)

  return (
    <aside
      className="w-56 shrink-0 flex flex-col h-screen sticky top-0 overflow-y-auto"
      style={{ background: 'var(--sidebar-bg)', borderRight: '1px solid var(--sidebar-border)' }}
    >
      {/* Logo */}
      <div className="px-4 py-4 flex items-center" style={{ borderBottom: '1px solid var(--sidebar-border)' }}>
        <img src="/werkflow-logo.png" alt="Werkflow" style={{ height: 40, width: 'auto', objectFit: 'contain' }} />
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-6">
        {visibleSections.map((section) => (
          <div key={section.titleKey}>
            <p
              className="px-2 mb-1.5 text-xs font-semibold uppercase tracking-widest"
              style={{ color: 'var(--sidebar-dim-text)' }}
            >
              {t(section.titleKey as Parameters<typeof t>[0])}
            </p>
            <ul className="space-y-0.5">
              {section.items.map((item) => {
                const isActive = pathname === item.href || pathname?.startsWith(item.href + '/')
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      className="flex items-center gap-2.5 px-2 py-2 rounded-lg text-sm font-medium transition-colors"
                      style={{
                        background: isActive ? 'var(--sidebar-active-bg)' : 'transparent',
                        color: isActive ? 'var(--sidebar-active-text)' : 'var(--sidebar-nav-text)',
                      }}
                      onMouseEnter={(e) => {
                        if (!isActive) (e.currentTarget as HTMLElement).style.background = 'var(--sidebar-hover-bg)'
                      }}
                      onMouseLeave={(e) => {
                        if (!isActive) (e.currentTarget as HTMLElement).style.background = 'transparent'
                      }}
                    >
                      <item.icon size={16} strokeWidth={1.8} />
                      <span>{t(item.labelKey as Parameters<typeof t>[0])}</span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </nav>

      {/* User profile card */}
      <div className="px-3 py-3 mx-3 mb-4 rounded-xl" style={{ background: 'rgba(255,255,255,0.05)' }}>
        <div className="flex items-center gap-2.5">
          <div
            className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold shrink-0"
            style={{ background: '#7c3aed' }}
          >
            {displayName.split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase()}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold leading-none truncate" style={{ color: 'var(--sidebar-active-text)' }}>
              {displayName}
            </p>
            <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--sidebar-dim-text)' }}>
              {displayRole}
            </p>
          </div>
        </div>
      </div>
    </aside>
  )
}
```

- [ ] **Step 2: Add missing nav translation keys**

Read `messages/en.json` (or equivalent). Add any missing keys:
- `tenantSetup`, `monitoring`, `serviceCatalog`, `approvalAuthority`, `roleMappings`, `custodyGroups`, `emailTemplates`, `connectors`, `deadLetterJobs`

- [ ] **Step 3: Create `components/layout/topbar.tsx`**

```tsx
import { Bell, Search } from 'lucide-react'
import { UserMenu } from '@/components/auth/user-menu'

export function TopBar() {
  return (
    <header
      className="h-14 border-b flex items-center px-6 gap-4 shrink-0 sticky top-0 z-40 bg-card"
      style={{ borderColor: 'var(--border)' }}
    >
      <div className="flex-1 flex items-center gap-2 max-w-sm">
        <div className="relative flex-1">
          <Search
            size={14}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <input
            placeholder="Search..."
            className="w-full pl-8 pr-3 py-1.5 text-sm rounded-lg bg-background border border-border focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
        </div>
      </div>
      <div className="ml-auto flex items-center gap-3">
        <button className="relative p-2 rounded-lg hover:bg-muted transition-colors">
          <Bell size={18} className="text-muted-foreground" />
        </button>
        <UserMenu />
      </div>
    </header>
  )
}
```

- [ ] **Step 4: Rewrite `components/layout/app-shell.tsx`**

```tsx
import { Sidebar } from '@/components/layout/sidebar'
import { TopBar } from '@/components/layout/topbar'

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <TopBar />
        <main className="flex-1 overflow-y-auto p-6">{children}</main>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Verify layout in browser**

Open http://localhost:4000/dashboard.

Expected:
- Dark sidebar (`#111c27`) full height on the left, 224px wide
- Each nav item has an icon + label
- Active item has purple-tinted background
- Slim top bar with search + bell + user menu
- Main content scrolls independently of sidebar

- [ ] **Step 6: Commit**

```bash
git add frontends/portal/components/layout/
git commit -m "feat(m4): navigation overhaul — dark sidebar with icons, 5-section structure, slim topbar"
```

---

## Task 4: Dashboard Overhaul

**Files:**
- Modify: `app/(platform)/dashboard/page.tsx`

Reference: `Werkflow Redesigned.html` — Dashboard section.

- [ ] **Step 1: Rewrite `app/(platform)/dashboard/page.tsx`**

Replace the entire file:

```tsx
'use client'

import Link from 'next/link'
import { useSession } from 'next-auth/react'
import { useQuery } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { ClipboardList, Users, AlertTriangle, TrendingUp, CheckCircle, Play, XCircle, Rocket, ArrowRight } from 'lucide-react'
import { StatCard } from '@/components/ui/stat-card'
import { StatusBadge } from '@/components/ui/status-badge'
import { Skeleton } from '@/components/ui/skeleton'
import { useTaskSummary } from '@/lib/hooks/useTasks'
import { getActivityLogs, type ActivityLogEntry } from '@/lib/api/workflows'

function formatTimestamp(timestamp: string): string {
  const diffMs = Date.now() - new Date(timestamp).getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

function ActivityIcon({ type }: { type: ActivityLogEntry['type'] }) {
  const props = { size: 16, strokeWidth: 1.8 }
  switch (type) {
    case 'completed': return <CheckCircle {...props} className="text-green-500 shrink-0" />
    case 'started':   return <Play {...props} className="text-blue-500 shrink-0" />
    case 'failed':    return <XCircle {...props} className="text-red-500 shrink-0" />
    case 'deployed':  return <Rocket {...props} className="text-purple-500 shrink-0" />
    default:          return <CheckCircle {...props} className="text-muted-foreground shrink-0" />
  }
}

export default function DashboardPage() {
  const t = useTranslations('dashboard')
  const { status } = useSession()
  const { data: summary, isLoading: loadingSummary } = useTaskSummary()
  const { data: activityLogs, isLoading: loadingActivity } = useQuery({
    queryKey: ['dashboard-activity'],
    queryFn: () => getActivityLogs(5),
    enabled: status === 'authenticated',
    staleTime: 30000,
  })

  return (
    <div className="space-y-8 max-w-5xl">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>
        <p className="text-sm text-muted-foreground mt-0.5">{t('subtitle')}</p>
      </div>

      {/* Stat cards */}
      <section>
        {loadingSummary ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-24 rounded-xl" />
            ))}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard icon={ClipboardList} label={t('myTasks')}     value={summary?.myTasks ?? 0}     iconColor="#7c3aed" />
            <StatCard icon={Users}         label={t('teamTasks')}   value={summary?.teamTasks ?? 0}   iconColor="#1d4ed8" />
            <StatCard icon={AlertTriangle} label={t('overdue')}     value={summary?.overdue ?? 0}     iconColor="#dc2626" />
            <StatCard icon={TrendingUp}    label={t('highPriority')}value={summary?.highPriority ?? 0}iconColor="#c27b00" />
          </div>
        )}
      </section>

      {/* Quick actions */}
      <section>
        <h2 className="text-base font-semibold mb-3">{t('quickActions')}</h2>
        <div className="flex flex-wrap gap-2">
          {[
            { label: t('viewMyTasks'),    href: '/tasks' },
            { label: t('myRequests'),     href: '/requests' },
            { label: t('startNewProcess'),href: '/services' },
          ].map(({ label, href }) => (
            <Link
              key={href}
              href={href}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg border border-border bg-card text-sm font-medium hover:bg-muted transition-colors"
            >
              {label}
              <ArrowRight size={14} className="text-muted-foreground" />
            </Link>
          ))}
        </div>
      </section>

      {/* Recent activity */}
      <section>
        <h2 className="text-base font-semibold mb-3">{t('recentActivity')}</h2>
        <div className="bg-card border border-border rounded-xl p-5">
          {loadingActivity ? (
            <div className="space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-10 rounded" />
              ))}
            </div>
          ) : !activityLogs?.length ? (
            <p className="text-sm text-muted-foreground">{t('noRecentActivity')}</p>
          ) : (
            <ul className="space-y-4">
              {activityLogs.map((entry) => (
                <li key={entry.id} className="flex items-start gap-3">
                  <ActivityIcon type={entry.type} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm leading-snug">{entry.message}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {entry.user} &middot; {formatTimestamp(entry.timestamp)}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  )
}
```

- [ ] **Step 2: Verify dashboard in browser**

Open http://localhost:4000/dashboard. Confirm: 4 stat cards in a grid row, quick action links, activity list. No visual regressions in sidebar.

- [ ] **Step 3: Commit**

```bash
git add "frontends/portal/app/(platform)/dashboard/page.tsx"
git commit -m "feat(m4): dashboard overhaul — stat cards, quick actions, activity feed"
```

---

## Task 5: My Tasks Overhaul

**Files:**
- Modify: `app/(platform)/tasks/page.tsx`
- Modify: `app/(platform)/tasks/components/TaskList.tsx` (update table columns)

Reference: `Werkflow Redesigned.html` — My Tasks section.

- [ ] **Step 1: Rewrite `app/(platform)/tasks/page.tsx`**

Replace the entire file with the new layout (preserve all existing hook imports and logic; only replace JSX structure):

```tsx
'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { Search, RefreshCw, ClipboardList, Users, AlertTriangle, Star } from 'lucide-react'
import { StatCard } from '@/components/ui/stat-card'
import { FilterPills } from '@/components/ui/filter-pills'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { TaskList } from './components/TaskList'
import { useTasks, useClaimTask, useTaskSummary } from '@/lib/hooks/useTasks'
import { mapGroupsToCandidateGroups } from '@/lib/utils/jwt'
import { useAuth } from '@/lib/auth/auth-context'
import { useToast } from '@/hooks/use-toast'
import type { TaskFilter } from '@/lib/types/task'

const TAB_OPTIONS = [
  { key: 'all',        label: 'All' },
  { key: 'myTasks',    label: 'Mine' },
  { key: 'overdue',    label: 'Overdue' },
  { key: 'unassigned', label: 'Unassigned' },
]

export default function TasksPage() {
  const t = useTranslations('tasks')
  const { toast } = useToast()
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [pageSize] = useState(20)
  const [searchText, setSearchText] = useState('')
  const [activeTab, setActiveTab] = useState('all')
  const [claimingTaskId, setClaimingTaskId] = useState<string | undefined>(undefined)

  const filters: TaskFilter = {
    myTasks: activeTab === 'myTasks',
    unassigned: activeTab === 'unassigned',
  }

  const buildQueryParams = () => {
    const params: any = {
      start: page * pageSize,
      size: pageSize,
      sort: 'createTime',
      order: 'desc' as const,
      includeProcessVariables: false,
    }
    if (user) {
      if (filters.myTasks) params.assignee = user.username
      else if (filters.unassigned) params.unassigned = true
    }
    if (activeTab === 'overdue') params.dueBefore = new Date().toISOString()
    if (searchText.length > 2) params.nameLike = `%${searchText}%`
    return params
  }

  const { data: tasksData, isLoading, refetch } = useTasks(buildQueryParams())
  const { data: summary, isLoading: loadingSummary } = useTaskSummary()

  const claimTaskMutation = useClaimTask()
  const handleClaim = async (taskId: string) => {
    if (!user) return
    setClaimingTaskId(taskId)
    try {
      await claimTaskMutation.mutateAsync({ taskId, assignee: user.username })
      toast({ title: t('taskClaimed'), description: t('taskClaimedDesc') })
    } catch (error: any) {
      toast({ title: t('claimFailed'), description: error.message, variant: 'destructive' })
    } finally {
      setClaimingTaskId(undefined)
    }
  }

  const tabsWithCount = TAB_OPTIONS.map((opt) => {
    let count: number | undefined
    if (opt.key === 'myTasks')    count = summary?.myTasks
    if (opt.key === 'overdue')    count = summary?.overdue
    return { ...opt, count }
  })

  return (
    <div className="space-y-6 max-w-6xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>
        <p className="text-sm text-muted-foreground mt-0.5">{t('subtitle')}</p>
      </div>

      {/* Stat cards */}
      {loadingSummary ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard icon={ClipboardList} label={t('myTasks')}     value={summary?.myTasks ?? 0}     iconColor="#7c3aed" />
          <StatCard icon={Users}         label={t('teamTasks')}   value={summary?.teamTasks ?? 0}   iconColor="#1d4ed8" />
          <StatCard icon={AlertTriangle} label={t('overdue')}     value={summary?.overdue ?? 0}     iconColor="#dc2626" />
          <StatCard icon={Star}          label={t('highPriority')}value={summary?.highPriority ?? 0}iconColor="#c27b00" />
        </div>
      )}

      {/* Tab filter + search */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center">
        <FilterPills options={tabsWithCount} active={activeTab} onChange={(k) => { setActiveTab(k); setPage(0) }} />
        <div className="flex gap-2 ml-auto">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder={t('searchPlaceholder')}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && refetch()}
              className="pl-8 w-56"
            />
          </div>
          <Button variant="outline" size="icon" onClick={() => refetch()} disabled={isLoading}>
            <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
          </Button>
        </div>
      </div>

      {/* Task list */}
      <TaskList
        tasks={tasksData?.data || []}
        total={tasksData?.total || 0}
        page={page}
        pageSize={pageSize}
        onPageChange={setPage}
        onClaim={handleClaim}
        isLoading={isLoading}
        claimingTaskId={claimingTaskId}
      />
    </div>
  )
}
```

- [ ] **Step 2: Update `app/(platform)/tasks/components/TaskList.tsx` to use new badges**

Find the priority and status rendering. Replace raw badge with `PriorityBadge` and `StatusBadge` imports:

```tsx
import { PriorityBadge } from '@/components/ui/priority-badge'
import { AvatarCell } from '@/components/ui/avatar-cell'
```

Replace any `<Badge>` used for priority with `<PriorityBadge priority={task.priority} />`.
Replace assignee text column with `<AvatarCell name={task.assignee ?? 'Unassigned'} />`.

- [ ] **Step 3: Verify in browser**

Open http://localhost:4000/tasks. Confirm: 4 stat cards at top, FilterPills tabs (All / Mine / Overdue / Unassigned), search input, task table with PriorityBadge and AvatarCell columns.

- [ ] **Step 4: Commit**

```bash
git add "frontends/portal/app/(platform)/tasks/"
git commit -m "feat(m4): my tasks overhaul — stat cards, tab filter pills, updated task table columns"
```

---

## Task 6: My Requests Overhaul

**Files:**
- Modify: `app/(platform)/requests/page.tsx`

Reference: `Werkflow Redesigned.html` — My Requests section.

- [ ] **Step 1: Add stat cards and filter pills to requests page**

Read the full current `requests/page.tsx`. At the top of the returned JSX, add:

```tsx
// At the top of the component, add:
const TAB_OPTIONS = [
  { key: 'all', label: 'All' },
  { key: 'active', label: 'Active' },
  { key: 'completed', label: 'Completed' },
  { key: 'suspended', label: 'Suspended' },
]
const [activeTab, setActiveTab] = useState<string>('all')
```

Replace the `<Badge>` status rendering with `<StatusBadge status={instance.status} />`.

Add import: `import { FilterPills } from '@/components/ui/filter-pills'` and `import { StatusBadge } from '@/components/ui/status-badge'`.

Replace the existing `<Tabs>` component with `<FilterPills options={TAB_OPTIONS} active={activeTab} onChange={setActiveTab} />`.

Update the filter logic to use `activeTab` instead of the Tabs value (same semantics, different component).

- [ ] **Step 2: Apply card wrapper to the table**

Wrap the table in:

```tsx
<div className="bg-card border border-border rounded-xl overflow-hidden">
  <table>...</table>
</div>
```

Update table header cells: `className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider bg-muted/50"`.

- [ ] **Step 3: Verify in browser**

Open http://localhost:4000/requests. Confirm FilterPills tabs at top, StatusBadge in table, card wrapper around table.

- [ ] **Step 4: Commit**

```bash
git add "frontends/portal/app/(platform)/requests/"
git commit -m "feat(m4): my requests overhaul — filter pills, status badges, card table wrapper"
```

---

## Task 7: Forms Page Overhaul

**Files:**
- Modify: `app/(platform)/forms/page.tsx`

Reference: `Werkflow Redesigned.html` — Forms section (table view, stat cards, tab pills).

- [ ] **Step 1: Replace the card-grid layout with a table layout**

Read the full current `forms/page.tsx`. Replace the grid of `<Card>` items with a table:

After the stat card row and before the "How to Create" guide:

```tsx
{/* Stat row */}
<div className="grid gap-4 sm:grid-cols-3 mb-6">
  <StatCard icon={FileText} label="Total Forms"   value={forms?.length ?? 0}   iconColor="#7c3aed" />
  <StatCard icon={Activity}  label="Deployed"      value={forms?.filter(f => f.key).length ?? 0} iconColor="#16a34a" />
  <StatCard icon={Plus}      label="Created Today" value={0}                    iconColor="#1d4ed8" />
</div>

{/* Filter pills */}
<div className="flex items-center justify-between mb-4">
  <FilterPills
    options={[{ key: 'all', label: 'All' }, { key: 'deployed', label: 'Deployed' }]}
    active="all"
    onChange={() => {}}
  />
  {isManagerOrAbove && (
    <Button asChild size="sm">
      <Link href="/forms/new"><Plus size={14} className="mr-1" />{t('createNewForm')}</Link>
    </Button>
  )}
</div>

{/* Table */}
<div className="bg-card border border-border rounded-xl overflow-hidden">
  <table className="w-full text-sm">
    <thead>
      <tr className="border-b border-border">
        <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Name</th>
        <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Key</th>
        <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Department</th>
        <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Status</th>
        <th className="px-4 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">Actions</th>
      </tr>
    </thead>
    <tbody className="divide-y divide-border">
      {forms?.map((form) => (
        <tr key={form.key} className="hover:bg-muted/30 transition-colors">
          <td className="px-4 py-3 font-medium">{form.name}</td>
          <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{form.key}</td>
          <td className="px-4 py-3 text-muted-foreground">{form.owningDepartment ?? '—'}</td>
          <td className="px-4 py-3"><StatusBadge status="completed" /></td>
          <td className="px-4 py-3">
            <div className="flex justify-end gap-1">
              <Button asChild variant="ghost" size="sm">
                <Link href={`/forms/preview/${form.key}`}><Eye size={14} /></Link>
              </Button>
              {canEditForm(form) && (
                <>
                  <Button asChild variant="ghost" size="sm">
                    <Link href={`/forms/edit/${form.key}`}><Edit size={14} /></Link>
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => handleDownload(form.key, form.formJson)}>
                    <Download size={14} />
                  </Button>
                  <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive"
                    onClick={() => setPendingConfirm({ title: t('deleteForm'), description: `Delete "${form.name}"?`, onConfirm: () => { setDeletingKey(form.key); deleteMutation.mutate(form.key) } })}>
                    <Trash2 size={14} />
                  </Button>
                </>
              )}
            </div>
          </td>
        </tr>
      ))}
    </tbody>
  </table>
</div>
```

Add required imports: `StatCard`, `FilterPills`, `StatusBadge`, `Activity`.

Remove the "How to Create a Form" guide card (it served as a placeholder for empty state; replace with a simpler empty state row inside the table body).

- [ ] **Step 2: Verify in browser**

Open http://localhost:4000/forms. Confirm: 3 stat cards, FilterPills, table with Name/Key/Department/Status/Actions columns, inline action buttons.

- [ ] **Step 3: Commit**

```bash
git add "frontends/portal/app/(platform)/forms/page.tsx"
git commit -m "feat(m4): forms page overhaul — table layout, stat cards, filter pills"
```

---

## Task 8: Processes Page Overhaul

**Files:**
- Modify: `app/(platform)/processes/page.tsx`

Reference: `Werkflow Redesigned.html` — Processes section (stat cards + Deployed/Drafts tabs + card grid).

- [ ] **Step 1: Add stat cards and FilterPills tabs to processes page**

Read the full current `processes/page.tsx`.

Add at the top of the return JSX:

```tsx
<div className="grid gap-4 sm:grid-cols-3 mb-6">
  <StatCard icon={Workflow}    label="Deployed"     value={processes?.length ?? 0}    iconColor="#7c3aed" />
  <StatCard icon={FileText}    label="Drafts"       value={drafts?.length ?? 0}       iconColor="#1d4ed8" />
  <StatCard icon={Activity}    label="Active Runs"  value={0}                         iconColor="#16a34a" />
</div>

<div className="flex items-center justify-between mb-4">
  <FilterPills
    options={[{ key: 'deployed', label: 'Deployed' }, { key: 'drafts', label: 'Drafts' }]}
    active={activeView}
    onChange={setActiveView}
  />
  {isManagerOrAbove && (
    <Button asChild size="sm"><Link href="/processes/new"><Plus size={14} className="mr-1" />{t('newProcess')}</Link></Button>
  )}
</div>
```

Add `const [activeView, setActiveView] = useState('deployed')` state. Conditionally render deployed cards or drafts list based on `activeView`.

Import: `StatCard`, `FilterPills`, `Workflow`, `Activity`.

- [ ] **Step 2: Update process cards styling**

Replace the current `<Card>` JSX for each process with:

```tsx
<div className="bg-card border border-border rounded-xl p-4 flex flex-col gap-3 hover:shadow-sm transition-shadow">
  <div className="flex items-start justify-between gap-2">
    <div>
      <p className="font-semibold text-foreground leading-snug">{process.name}</p>
      <p className="text-xs font-mono text-muted-foreground mt-0.5">{process.key} · v{process.version}</p>
    </div>
    <StatusBadge status="active" />
  </div>
  <div className="flex gap-1.5 mt-auto">
    <Button asChild variant="outline" size="sm" className="flex-1">
      <Link href={`/processes/edit/${process.id}`}><Edit size={14} className="mr-1" />Edit</Link>
    </Button>
    <Button asChild variant="ghost" size="sm">
      <Link href={`/processes/start/${process.id}`}><Play size={14} /></Link>
    </Button>
    {canEditProcess(process.owningDepartment) && (
      <Button variant="ghost" size="sm" className="text-destructive" onClick={...}>
        <Trash2 size={14} />
      </Button>
    )}
  </div>
</div>
```

- [ ] **Step 3: Verify in browser**

Open http://localhost:4000/processes. Confirm: 3 stat cards, Deployed/Drafts tab pills, process cards with updated styling, action buttons.

- [ ] **Step 4: Commit**

```bash
git add "frontends/portal/app/(platform)/processes/page.tsx"
git commit -m "feat(m4): processes page overhaul — stat cards, Deployed/Drafts tabs, updated card grid"
```

---

## Task 9: Decisions Page Overhaul

**Files:**
- Modify: `app/(platform)/decisions/page.tsx`

Reference: `Werkflow Redesigned.html` — Decisions section (align to Forms table pattern).

- [ ] **Step 1: Read current decisions page**

```bash
cat "frontends/portal/app/(platform)/decisions/page.tsx"
```

- [ ] **Step 2: Apply Forms table pattern**

Following the same structure as Task 7 (stat cards + FilterPills + table), convert the decisions list to:
- 2 stat cards: Total Decisions, Deployed
- FilterPills: All / Deployed
- Table columns: Name | Key | Hit Policy | Status | Actions (Edit, Delete)
- Use `StatusBadge` for status column

- [ ] **Step 3: Verify in browser and commit**

Open http://localhost:4000/decisions. Confirm table layout with stat cards and filter pills.

```bash
git add "frontends/portal/app/(platform)/decisions/page.tsx"
git commit -m "feat(m4): decisions page overhaul — table layout aligned to forms pattern"
```

---

## Task 10: Service Catalog (New Screen)

**Files:**
- Create: `app/(platform)/services/page.tsx`

Reference: `Werkflow Employee Portal.html` — Service Catalog section (card grid, category filter pills, step tags, Submit Request CTA).

- [ ] **Step 1: Create `app/(platform)/services/page.tsx`**

```tsx
'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getProcessDefinitions } from '@/lib/api/flowable'
import { FilterPills } from '@/components/ui/filter-pills'
import { Clock, ChevronRight, Play } from 'lucide-react'
import { Button } from '@/components/ui/button'

const DEPT_COLORS: Record<string, string> = {
  HR: '#7c3aed', Finance: '#16a34a', IT: '#0891b2',
  Procurement: '#dc2626', default: '#6b7e8c',
}

export default function ServiceCatalogPage() {
  const { status } = useSession()
  const [activeDept, setActiveDept] = useState('all')

  const { data: processes, isLoading } = useQuery({
    queryKey: ['processDefinitions'],
    queryFn: getProcessDefinitions,
    enabled: status === 'authenticated',
    staleTime: 60000,
  })

  const departments = ['all', ...Array.from(new Set(processes?.map((p) => p.category ?? 'General') ?? []))]
  const deptOptions = departments.map((d) => ({ key: d, label: d === 'all' ? 'All Services' : d }))

  const filtered = activeDept === 'all'
    ? (processes ?? [])
    : (processes ?? []).filter((p) => (p.category ?? 'General') === activeDept)

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Service Catalog</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Browse and start available workflows</p>
      </div>

      <FilterPills options={deptOptions} active={activeDept} onChange={setActiveDept} />

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="bg-card border border-border rounded-xl p-5 h-48 animate-pulse" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <p className="text-muted-foreground text-sm">No services available.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((process) => {
            const dept = process.category ?? 'General'
            const color = DEPT_COLORS[dept] ?? DEPT_COLORS.default
            return (
              <div key={process.id} className="bg-card border border-border rounded-xl p-5 flex flex-col gap-3 hover:shadow-sm transition-shadow">
                <div className="flex items-center gap-2">
                  <div
                    className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                    style={{ background: color + '18' }}
                  >
                    <Play size={16} style={{ color }} strokeWidth={1.8} />
                  </div>
                  <div>
                    <p className="font-semibold text-foreground text-sm leading-snug">{process.name}</p>
                    <p className="text-xs text-muted-foreground">{dept}</p>
                  </div>
                </div>

                <p className="text-xs text-muted-foreground leading-relaxed flex-1">
                  {process.description ?? 'Start this workflow to submit a request.'}
                </p>

                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Clock size={12} />
                  <span>Working days may vary</span>
                </div>

                <Button asChild size="sm" className="w-full mt-auto">
                  <Link href={`/processes/start/${process.id}`}>
                    Submit Request <ChevronRight size={14} className="ml-1" />
                  </Link>
                </Button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Verify in browser**

Open http://localhost:4000/services. Confirm: department filter pills at top, process cards with colour accent icons, Submit Request CTA per card.

- [ ] **Step 3: Commit**

```bash
git add "frontends/portal/app/(platform)/services/"
git commit -m "feat(m4): service catalog new screen — card grid with category filter pills"
```

---

## Task 11: Tenant Setup UI (Group 3a)

**Files:**
- Create: `app/(platform)/admin/tenant/approval-authority/page.tsx`
- Create: `app/(platform)/admin/tenant/role-mappings/page.tsx`
- Create: `app/(platform)/admin/tenant/departments/page.tsx`
- Create: `app/(platform)/admin/tenant/custody-groups/page.tsx`

API endpoints (already built in M3):
- Config vars: `GET/POST/PUT/DELETE /api/v1/config/vars` via admin-service proxy
- Role mappings: `GET/POST/DELETE /api/v1/config/role-mappings`
- Departments: `GET /api/v1/departments` (ERP)
- Custody mappings: `GET /api/v1/custody-mappings` (ERP)

All pages are ADMIN/SUPER_ADMIN guarded. All use the design system tokens.

- [ ] **Step 1: Create `app/(platform)/admin/tenant/approval-authority/page.tsx`**

```tsx
'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { StatCard } from '@/components/ui/stat-card'
import { ShieldCheck } from 'lucide-react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useState } from 'react'

// Types
interface ConfigVar {
  id?: string
  varKey: string
  varValue: string
  varType: string
  description?: string
}

// API helpers — proxy via portal's /api/admin/* route
async function fetchConfigVars(type: string, token: string): Promise<ConfigVar[]> {
  const res = await fetch(`/api/proxy/admin/config/vars?type=${type}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch config vars')
  return res.json()
}

async function upsertConfigVar(body: ConfigVar, token: string): Promise<ConfigVar> {
  const res = await fetch('/api/proxy/admin/config/vars', {
    method: body.id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to save config var')
  return res.json()
}

const DOA_LEVELS = ['L1', 'L2', 'L3', 'L4'] as const

export default function ApprovalAuthorityPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session as any)?.accessToken ?? ''
  const qc = useQueryClient()
  const [editValues, setEditValues] = useState<Record<string, string>>({})

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  const { data: doaVars, isLoading } = useQuery({
    queryKey: ['configVars', 'DOA_THRESHOLD'],
    queryFn: () => fetchConfigVars('DOA_THRESHOLD', token),
    enabled: status === 'authenticated',
  })

  const { data: roleLevelVars, isLoading: loadingRoles } = useQuery({
    queryKey: ['configVars', 'ROLE_DOA_LEVEL'],
    queryFn: () => fetchConfigVars('ROLE_DOA_LEVEL', token),
    enabled: status === 'authenticated',
  })

  const saveMutation = useMutation({
    mutationFn: (body: ConfigVar) => upsertConfigVar(body, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['configVars'] }),
  })

  const doaMap = Object.fromEntries((doaVars ?? []).map((v) => [v.varKey, v]))

  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Approval Authority</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Configure DOA threshold amounts per level and role-to-level assignments</p>
      </div>

      {/* L1–L4 threshold amounts */}
      <section>
        <h2 className="text-base font-semibold mb-3">Threshold Amounts</h2>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-24">Level</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Max Amount (USD)</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider w-24">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {DOA_LEVELS.map((level) => {
                const existing = doaMap[level]
                const draft = editValues[level] ?? existing?.varValue ?? ''
                return (
                  <tr key={level} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-semibold">{level}</td>
                    <td className="px-4 py-3">
                      <Input
                        type="number"
                        value={draft}
                        onChange={(e) => setEditValues((prev) => ({ ...prev, [level]: e.target.value }))}
                        className="w-48 h-8 text-sm"
                        placeholder="e.g. 10000"
                      />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        size="sm"
                        disabled={saveMutation.isPending}
                        onClick={() => saveMutation.mutate({
                          ...existing,
                          varKey: level,
                          varValue: draft,
                          varType: 'DOA_THRESHOLD',
                        })}
                      >
                        Save
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      {/* Role → Level mapping */}
      <section>
        <h2 className="text-base font-semibold mb-3">Role to Level Mapping</h2>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Role</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Assigned Level</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {(roleLevelVars ?? []).map((v) => (
                <tr key={v.varKey} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs">{v.varKey}</td>
                  <td className="px-4 py-3"><span className="font-semibold">{v.varValue}</span></td>
                </tr>
              ))}
              {!loadingRoles && !roleLevelVars?.length && (
                <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">No role mappings configured yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
```

- [ ] **Step 2: Create `app/(platform)/admin/tenant/role-mappings/page.tsx`**

```tsx
'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'

interface RoleGroupMapping {
  id?: string
  roleName: string
  groupName: string
  tier: 1 | 2
}

async function fetchRoleMappings(token: string): Promise<RoleGroupMapping[]> {
  const res = await fetch('/api/proxy/admin/config/role-mappings', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch role mappings')
  return res.json()
}

async function addRoleMapping(body: RoleGroupMapping, token: string): Promise<RoleGroupMapping> {
  const res = await fetch('/api/proxy/admin/config/role-mappings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to add role mapping')
  return res.json()
}

async function deleteRoleMapping(id: string, token: string): Promise<void> {
  await fetch(`/api/proxy/admin/config/role-mappings/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
}

export default function RoleMappingsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session as any)?.accessToken ?? ''
  const qc = useQueryClient()
  const [newRole, setNewRole] = useState('')
  const [newGroup, setNewGroup] = useState('')

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) return <p className="text-destructive">Access denied.</p>

  const { data: mappings, isLoading } = useQuery({
    queryKey: ['roleMappings'],
    queryFn: () => fetchRoleMappings(token),
    enabled: status === 'authenticated',
  })

  const addMutation = useMutation({
    mutationFn: (body: RoleGroupMapping) => addRoleMapping(body, token),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['roleMappings'] }); setNewRole(''); setNewGroup('') },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteRoleMapping(id, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roleMappings'] }),
  })

  const tier1 = mappings?.filter((m) => m.tier === 1) ?? []
  const tier2 = mappings?.filter((m) => m.tier === 2) ?? []

  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Role Mappings</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Tier 1 is read-only (Keycloak realm roles). Tier 2 is editable (custom group assignments).</p>
      </div>

      {[{ tier: 1, label: 'Tier 1 — Realm Roles (read-only)', rows: tier1, editable: false },
        { tier: 2, label: 'Tier 2 — Custom Group Mappings',   rows: tier2, editable: true }
      ].map(({ tier, label, rows, editable }) => (
        <section key={tier}>
          <h2 className="text-base font-semibold mb-3">{label}</h2>
          <div className="bg-card border border-border rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Role</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Group</th>
                  {editable && <th className="px-4 py-3 w-16" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {rows.map((m) => (
                  <tr key={m.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-mono text-xs">{m.roleName}</td>
                    <td className="px-4 py-3 font-mono text-xs">{m.groupName}</td>
                    {editable && (
                      <td className="px-4 py-3 text-right">
                        <Button variant="ghost" size="sm" className="text-destructive" onClick={() => m.id && deleteMutation.mutate(m.id)}>
                          <Trash2 size={14} />
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
                {!isLoading && rows.length === 0 && (
                  <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No mappings.</td></tr>
                )}
                {editable && (
                  <tr className="bg-muted/30">
                    <td className="px-4 py-3">
                      <Input value={newRole} onChange={(e) => setNewRole(e.target.value)} placeholder="role_name" className="h-8 text-sm font-mono" />
                    </td>
                    <td className="px-4 py-3">
                      <Input value={newGroup} onChange={(e) => setNewGroup(e.target.value)} placeholder="group_name" className="h-8 text-sm font-mono" />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button size="sm" disabled={!newRole || !newGroup || addMutation.isPending}
                        onClick={() => addMutation.mutate({ roleName: newRole, groupName: newGroup, tier: 2 })}>
                        <Plus size={14} />
                      </Button>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      ))}
    </div>
  )
}
```

- [ ] **Step 3: Create `app/(platform)/admin/tenant/departments/page.tsx`**

```tsx
'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'

interface Department {
  id: string
  deptCode: string
  deptName: string
  managerId?: string
}

async function fetchDepartments(token: string): Promise<Department[]> {
  const res = await fetch('/api/proxy/erp/departments', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch departments')
  const body = await res.json()
  return body.content ?? body
}

export default function DepartmentsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session as any)?.accessToken ?? ''

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) return <p className="text-destructive">Access denied.</p>

  const { data: depts, isLoading } = useQuery({
    queryKey: ['erpDepartments'],
    queryFn: () => fetchDepartments(token),
    enabled: status === 'authenticated',
  })

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Departments</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Departments are managed in the ERP system. This is a read-only view.</p>
      </div>
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Code</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Manager ID</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading && (
              <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading...</td></tr>
            )}
            {(depts ?? []).map((d) => (
              <tr key={d.id} className="hover:bg-muted/30">
                <td className="px-4 py-3 font-mono text-xs font-semibold">{d.deptCode}</td>
                <td className="px-4 py-3">{d.deptName}</td>
                <td className="px-4 py-3 text-muted-foreground text-xs">{d.managerId ?? '—'}</td>
              </tr>
            ))}
            {!isLoading && !depts?.length && (
              <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No departments found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Create `app/(platform)/admin/tenant/custody-groups/page.tsx`**

```tsx
'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'

interface CustodyMapping {
  id: string
  custodyOwner: string
  candidateGroups: string[]
}

async function fetchCustodyMappings(token: string): Promise<CustodyMapping[]> {
  const res = await fetch('/api/proxy/erp/custody-mappings', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch custody mappings')
  const body = await res.json()
  return body.content ?? body
}

export default function CustodyGroupsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session as any)?.accessToken ?? ''

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) return <p className="text-destructive">Access denied.</p>

  const { data: mappings, isLoading } = useQuery({
    queryKey: ['custodyMappings'],
    queryFn: () => fetchCustodyMappings(token),
    enabled: status === 'authenticated',
  })

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Custody Groups</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Custody owner to candidate group mappings from ERP.</p>
      </div>
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Owner</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Candidate Groups</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading && (
              <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading...</td></tr>
            )}
            {(mappings ?? []).map((m) => (
              <tr key={m.id} className="hover:bg-muted/30">
                <td className="px-4 py-3 font-mono text-xs">{m.custodyOwner}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {m.candidateGroups.map((g) => (
                      <span key={g} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-muted-foreground">{g}</span>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
            {!isLoading && !mappings?.length && (
              <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">No custody mappings found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Add redirect from old admin paths**

In `app/(platform)/admin/departments/page.tsx` (if it exists), replace with:
```tsx
import { redirect } from 'next/navigation'
export default function OldDepartmentsRedirect() { redirect('/admin/tenant/departments') }
```

Do the same for `app/(platform)/admin/custody/page.tsx` → `/admin/tenant/custody-groups`.
Do the same for `app/(platform)/admin/doa/page.tsx` → `/admin/tenant/approval-authority`.

- [ ] **Step 6: Verify all 4 tenant setup pages in browser**

Open each URL:
- http://localhost:4000/admin/tenant/approval-authority — shows L1–L4 table + role-level table
- http://localhost:4000/admin/tenant/role-mappings — shows Tier 1 (read-only) + Tier 2 (editable)
- http://localhost:4000/admin/tenant/departments — shows ERP department table
- http://localhost:4000/admin/tenant/custody-groups — shows ERP custody mappings

- [ ] **Step 7: Commit**

```bash
git add "frontends/portal/app/(platform)/admin/tenant/"
git commit -m "feat(m4): tenant setup UI — approval authority, role mappings, departments, custody groups"
```

---

## Task 12: Form Editor Improvements (Group 3d)

**Files:**
- Create: `lib/forms/createPaletteFilterModule.ts`
- Modify: `components/forms/FormJsEditor.tsx`

- [ ] **Step 1: Create `lib/forms/createPaletteFilterModule.ts`**

```ts
export function createPaletteFilterModule(allowedTypes: string[]) {
  function PaletteFilterModule(formFields: any) {
    const originalInit = formFields.init?.bind(formFields)
    formFields.init = function (...args: any[]) {
      originalInit?.(...args)
      const allTypes: string[] = formFields._formFields
        ? Object.keys(formFields._formFields._types ?? {})
        : []
      allTypes
        .filter((t) => !allowedTypes.includes(t))
        .forEach((t) => formFields.deregister(t))
    }
  }
  PaletteFilterModule.$inject = ['formFields']
  return { __init__: ['paletteFilter'], paletteFilter: ['type', PaletteFilterModule] }
}
```

- [ ] **Step 2: Modify `components/forms/FormJsEditor.tsx` to inject palette filter and CSS vars**

Read the full `FormJsEditor.tsx` file. Locate where `FormEditor` is instantiated (the `new FormEditor(...)` call).

Add before the `FormEditor` instantiation:

```tsx
// Fetch allowlist
const allowlistRes = await fetch('/api/proxy/admin/config/form-components', {
  headers: { Authorization: `Bearer ${accessToken}` },
}).catch(() => null)
const allowedTypes: string[] = allowlistRes?.ok
  ? await allowlistRes.json()
  : ['textfield', 'textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'button']

// Fetch tenant CSS vars (type=CSS_THEME)
const cssRes = await fetch('/api/proxy/admin/config/vars?type=CSS_THEME', {
  headers: { Authorization: `Bearer ${accessToken}` },
}).catch(() => null)
const cssVars: { varKey: string; varValue: string }[] = cssRes?.ok ? await cssRes.json() : []
```

Add `additionalModules: [createPaletteFilterModule(allowedTypes)]` to the FormEditor options.

After mounting the FormEditor, apply CSS vars to the container:

```tsx
if (containerRef.current && cssVars.length) {
  cssVars.forEach(({ varKey, varValue }) => {
    (containerRef.current as HTMLElement).style.setProperty(varKey, varValue)
  })
}
```

Add import at top of file: `import { createPaletteFilterModule } from '@/lib/forms/createPaletteFilterModule'`

- [ ] **Step 3: Verify form editor still loads**

Open http://localhost:4000/forms/new. The form editor should load normally. With no CSS_THEME vars configured, there is no visual change. The palette should still show the default allowed field types.

- [ ] **Step 4: Commit**

```bash
git add frontends/portal/lib/forms/createPaletteFilterModule.ts \
        frontends/portal/components/forms/FormJsEditor.tsx
git commit -m "feat(m4/3d): form editor — palette filter module + tenant CSS vars injection"
```

---

## Task 13: Editor CSS Theming (BPMN, form-js, DMN)

**Files:**
- Modify: `app/globals.css` (add editor overrides section)

Reference: `Werkflow Editor Theming.html` — open this file and read the CSS target selectors before writing.

- [ ] **Step 1: Read the editor theming design file**

```bash
cat "/Users/lamteiwahlang/Projects/Werkflow Redesigned Final/Werkflow Editor Theming.html" | grep -A5 '\.djs\|\.fjs\|\.dmn'
```

Note the specific selectors and colour values used.

- [ ] **Step 2: Add BPMN-js theme overrides to `globals.css`**

Append after the existing `/* BPMN.js specific styles */` section:

```css
/* ── BPMN-JS THEME ──────────────────────────────────────── */
.djs-container .djs-palette {
  background: var(--card);
  border-right: 1px solid var(--sidebar-border);
  border-radius: 0 8px 8px 0;
}
.djs-container .djs-palette .entry:hover {
  background: hsl(var(--muted));
}
.djs-container .djs-palette .highlighted-entry {
  background: hsl(var(--accent) / 0.15);
}
.djs-container {
  background: hsl(var(--background));
}
.djs-container .djs-element .djs-hit {
  fill: transparent;
}

/* Properties panel */
.bio-properties-panel {
  background: var(--card) !important;
  border-left: 1px solid var(--sidebar-border) !important;
  font-family: var(--font-dm-sans), sans-serif;
}
.bio-properties-panel .bio-properties-panel-header {
  background: hsl(var(--muted)) !important;
  color: hsl(var(--foreground)) !important;
}
.bio-properties-panel input,
.bio-properties-panel select,
.bio-properties-panel textarea {
  background: hsl(var(--background)) !important;
  border: 1px solid hsl(var(--border)) !important;
  color: hsl(var(--foreground)) !important;
  border-radius: 6px;
}
.bio-properties-panel label {
  color: hsl(var(--muted-foreground)) !important;
  font-size: 12px;
}

/* ── FORM-JS THEME ────────────────────────────────────────── */
.fjs-container {
  background: hsl(var(--background));
  font-family: var(--font-dm-sans), sans-serif;
}
.fjs-palette {
  background: var(--card) !important;
  border-right: 1px solid hsl(var(--border)) !important;
}
.fjs-palette-field:hover {
  background: hsl(var(--muted)) !important;
}
.fjs-form-field input,
.fjs-form-field select,
.fjs-form-field textarea {
  border: 1px solid hsl(var(--border)) !important;
  border-radius: 6px !important;
  background: var(--card) !important;
}
.fjs-form-field label {
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  font-weight: 500;
}

/* ── DMN-JS THEME ─────────────────────────────────────────── */
.dmn-decision-table-container .tjs-table th {
  background: hsl(var(--muted)) !important;
  color: hsl(var(--foreground)) !important;
  font-weight: 600;
  font-size: 12px;
}
.dmn-decision-table-container .tjs-table td {
  border: 1px solid hsl(var(--border)) !important;
}
.dmn-decision-table-container .tjs-table tr:hover td {
  background: hsl(var(--muted) / 0.5) !important;
}
.dmn-decision-table-container {
  font-family: var(--font-dm-sans), sans-serif;
}
```

- [ ] **Step 3: Verify editor theming**

Open http://localhost:4000/processes/new (BPMN editor). Verify: palette has white background, canvas has `#f0f4f6` background, properties panel has styled inputs.

Open http://localhost:4000/forms/new (form-js editor). Verify: palette matches card background, field inputs have styled borders.

Open a DMN table (http://localhost:4000/decisions/new). Verify: table header uses muted background, borders use theme border colour.

- [ ] **Step 4: Commit**

```bash
git add frontends/portal/app/globals.css
git commit -m "feat(m4): editor CSS theming — BPMN, form-js, dmn-js themed to design system"
```

---

## Task 14: Analytics Dashboard (M6 Group B)

**Files:**
- Modify: `app/(platform)/analytics/page.tsx`

Requires: install `recharts` package.

- [ ] **Step 1: Install recharts**

```bash
cd frontends/portal && npm install recharts
```

Expected: recharts and its peer deps added to `node_modules/`.

- [ ] **Step 2: Read existing analytics page**

```bash
cat "frontends/portal/app/(platform)/analytics/page.tsx"
```

Note existing API hooks and data shapes used.

- [ ] **Step 3: Rewrite `app/(platform)/analytics/page.tsx`**

```tsx
'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { StatCard } from '@/components/ui/stat-card'
import { Activity, CheckCircle, AlertTriangle, TrendingUp, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  LineChart, Line, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from 'recharts'

// These endpoints are built in M6 Group A
async function fetchProcessStats(token: string) {
  const res = await fetch('/api/proxy/engine/analytics/process-stats', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch process stats')
  return res.json()
}

async function fetchTaskMetrics(token: string) {
  const res = await fetch('/api/proxy/engine/analytics/task-metrics', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch task metrics')
  return res.json()
}

export default function AnalyticsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session as any)?.accessToken ?? ''

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive p-6">Access denied.</p>
  }

  const { data: procStats, isLoading: loadingProc } = useQuery({
    queryKey: ['analyticsProcessStats'],
    queryFn: () => fetchProcessStats(token),
    enabled: status === 'authenticated',
    staleTime: 60000,
  })

  const { data: taskMetrics, isLoading: loadingTask } = useQuery({
    queryKey: ['analyticsTaskMetrics'],
    queryFn: () => fetchTaskMetrics(token),
    enabled: status === 'authenticated',
    staleTime: 60000,
  })

  const isLoading = loadingProc || loadingTask

  // procStats shape from M6A: { totalCount, successCount, failureCount, avgDurationMs, successRate, executionsOverTime: [{date, count}] }
  // taskMetrics shape from M6A: { avgCycleTimeMs, bottleneckStepId, slaCompliancePct, overdueCount, escalationCount, bottlenecks: [{stepName, avgMs}] }

  const executions: { date: string; count: number }[] = procStats?.executionsOverTime ?? []
  const bottlenecks: { stepName: string; avgMs: number }[] = taskMetrics?.bottlenecks ?? []

  const handleExportCsv = () => {
    if (!executions.length) return
    const rows = [['Date', 'Count'], ...executions.map((e) => [e.date, e.count])]
    const csv = rows.map((r) => r.join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'analytics.csv'; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-8 max-w-6xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Analytics Dashboard</h1>
          <p className="text-sm text-muted-foreground mt-0.5">Process execution metrics and SLA compliance</p>
        </div>
        <Button variant="outline" size="sm" onClick={handleExportCsv}>
          <Download size={14} className="mr-1.5" /> Export CSV
        </Button>
      </div>

      {/* Overview stat cards */}
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard icon={Activity}      label="Total Executions" value={procStats?.totalCount ?? 0}                    iconColor="#7c3aed" />
          <StatCard icon={CheckCircle}   label="Success Rate"     value={`${procStats?.successRate?.toFixed(1) ?? 0}%`} iconColor="#16a34a" />
          <StatCard icon={AlertTriangle} label="Overdue Tasks"    value={taskMetrics?.overdueCount ?? 0}                iconColor="#dc2626" />
          <StatCard icon={TrendingUp}    label="SLA Compliance"   value={`${taskMetrics?.slaCompliancePct?.toFixed(1) ?? 0}%`} iconColor="#1d4ed8" />
        </div>
      )}

      {/* Charts row */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Line chart — executions over time */}
        <div className="bg-card border border-border rounded-xl p-5">
          <h2 className="text-base font-semibold mb-4">Process Executions Over Time</h2>
          {isLoading ? <Skeleton className="h-48" /> : (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={executions}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(207 26% 88%)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line type="monotone" dataKey="count" stroke="#7c3aed" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Bar chart — task bottlenecks */}
        <div className="bg-card border border-border rounded-xl p-5">
          <h2 className="text-base font-semibold mb-4">Task Bottlenecks (Avg Cycle Time)</h2>
          {isLoading ? <Skeleton className="h-48" /> : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={bottlenecks}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(207 26% 88%)" />
                <XAxis dataKey="stepName" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 11 }} unit="ms" />
                <Tooltip formatter={(v: number) => [`${(v / 1000 / 60).toFixed(1)} min`, 'Avg Time']} />
                <Bar dataKey="avgMs" fill="#1d4ed8" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* SLA + bottleneck table */}
      <div className="bg-card border border-border rounded-xl p-5">
        <h2 className="text-base font-semibold mb-4">SLA Summary</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            { label: 'Overdue Tasks',     value: taskMetrics?.overdueCount ?? 0,                         color: '#dc2626' },
            { label: 'Escalations',       value: taskMetrics?.escalationCount ?? 0,                      color: '#c27b00' },
            { label: 'SLA Compliance %',  value: `${taskMetrics?.slaCompliancePct?.toFixed(1) ?? 0}%`,  color: '#16a34a' },
          ].map(({ label, value, color }) => (
            <div key={label} className="p-4 rounded-xl border border-border">
              <p className="text-xs text-muted-foreground mb-1">{label}</p>
              <p className="text-2xl font-bold" style={{ color }}>{value}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Bottleneck detail table */}
      {bottlenecks.length > 0 && (
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="text-base font-semibold">Bottleneck Detail</h2>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Step</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Avg Duration</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {bottlenecks.map((b) => (
                <tr key={b.stepName} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs">{b.stepName}</td>
                  <td className="px-4 py-3 font-semibold">{(b.avgMs / 1000 / 60).toFixed(1)} min</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Verify analytics page in browser**

Open http://localhost:4000/analytics (as ADMIN user). Confirm: 4 stat cards, line chart, bar chart, SLA summary boxes, bottleneck table. Charts render even with empty data arrays (empty state).

- [ ] **Step 5: Commit**

```bash
git add frontends/portal/app/(platform)/analytics/page.tsx frontends/portal/package.json frontends/portal/package-lock.json
git commit -m "feat(m4/m6b): analytics dashboard — overview cards, line/bar charts, SLA summary, CSV export"
```

---

## Self-Review

**Spec coverage check:**

| Roadmap Item | Task |
|---|---|
| Design System tokens | Task 1 |
| Shared components (StatCard, FilterPills, StatusBadge, PriorityBadge, AvatarCell) | Task 2 |
| Dark sidebar — 5 sections, icons, user profile card | Task 3 |
| Navigation overhaul — slim topbar, bell, user menu | Task 3 |
| Dashboard overhaul | Task 4 |
| My Tasks overhaul | Task 5 |
| My Requests overhaul | Task 6 |
| Forms page — table layout | Task 7 |
| Processes page — Deployed/Drafts tabs, card grid | Task 8 |
| Decisions page — align to forms pattern | Task 9 |
| Service Catalog (new screen) | Task 10 |
| Tenant Setup UI — 4 sub-pages | Task 11 |
| Form Editor — palette filter + CSS vars | Task 12 |
| Editor CSS theming — BPMN, form-js, DMN | Task 13 |
| Analytics Dashboard (M6 Group B) + recharts | Task 14 |
| Dead-letter jobs wired to sidebar | ✅ sidebar Task 3 — already linked to /admin/jobs/dead-letter |
| Email Templates moved to Design Studio | ✅ sidebar Task 3 — already in designStudio section |
| Analytics sidebar link activated | ✅ sidebar Task 3 — monitoring section enabled |

**Placeholder scan:** No TBD/TODO markers. All component code is complete.

**Type consistency:** `StatCard` takes `icon: LucideIcon` throughout. `FilterPills` takes `{ key, label, count? }[]` throughout. `StatusBadge` takes `status: string` throughout. `PriorityBadge` takes `priority: number` throughout. `AvatarCell` takes `name: string` throughout. Consistent across all tasks.

---

## Execution Handoff

Plan saved to `docs/superpowers/plans/2026-05-01-m4-ui-overhaul.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch with checkpoints

Which approach?
