'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'
import {
  LayoutDashboard, CheckSquare, FileText, BookOpen,
  Workflow, FormInput, GitBranch, Mail,
  Link2, ShieldCheck, Building2, Users, TrendingUp, Activity,
  BriefcaseBusiness, AlertCircle, Tag, Eye, Globe, Database,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface NavItem {
  labelKey: string
  href: string
  icon: LucideIcon
  requiredRoles?: string[]
}

interface SidebarSection {
  titleKey: string
  items: NavItem[]
}

const sidebarSections: SidebarSection[] = [
  {
    titleKey: 'general',
    items: [
      { labelKey: 'dashboard',      href: '/dashboard',  icon: LayoutDashboard },
      { labelKey: 'myTasks',        href: '/tasks',      icon: CheckSquare },
      { labelKey: 'myRequests',     href: '/requests',   icon: FileText },
      { labelKey: 'serviceCatalog', href: '/services',   icon: BookOpen },
    ],
  },
  {
    titleKey: 'designStudio',
    items: [
      { labelKey: 'processes',      href: '/processes',             icon: Workflow,          requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'forms',          href: '/forms',                 icon: FormInput,         requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'decisions',      href: '/decisions',             icon: GitBranch,         requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'emailTemplates', href: '/admin/email-templates', icon: Mail,              requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'connectorsData',
    items: [
      { labelKey: 'connectors',     href: '/admin/connectors',         icon: Link2,       requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'datasources',    href: '/admin/tenant/datasources', icon: Database,    requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'deadLetterJobs', href: '/admin/jobs/dead-letter',   icon: AlertCircle, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'tenantSetup',
    items: [
      { labelKey: 'roleMappings',      href: '/admin/tenant/role-mappings',      icon: Users,             requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'approvalAuthority', href: '/admin/tenant/approval-authority', icon: ShieldCheck,       requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'departments',       href: '/admin/tenant/departments',        icon: Building2,         requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'custodyMappings',   href: '/admin/tenant/custody-mappings',   icon: BriefcaseBusiness, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'categories',        href: '/admin/tenant/categories',         icon: Tag,               requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'visibilityPolicy',  href: '/admin/tenant/visibility-policy',  icon: Eye,               requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'locale',            href: '/admin/tenant/locale',             icon: Globe,             requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'monitoring',
    items: [
      { labelKey: 'analytics',     href: '/analytics',  icon: TrendingUp, requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'processHealth', href: '/monitoring', icon: Activity,   requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
]

export function Sidebar() {
  const pathname = usePathname()
  const { isAuthenticated, user } = useAuth()
  const { hasAnyRole } = useAuthorization()
  const t = useTranslations('nav')

  if (!isAuthenticated) return null

  const firstName = user?.firstName ?? ''
  const lastName = user?.lastName ?? ''
  const fullName = [firstName, lastName].filter(Boolean).join(' ')
  const displayName: string = fullName || user?.username || 'User'

  const displayRole: string = (user?.roles?.[0] ?? 'Employee')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c: string) => c.toUpperCase())

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
      <div
        className="px-3 py-3 flex items-center justify-center"
        style={{ borderBottom: '1px solid var(--sidebar-border)' }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/werkflow-logo.png" alt="Werkflow" style={{ width: '100%', height: 'auto', maxHeight: 56, objectFit: 'contain' }} />
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-6 overflow-y-auto">
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
                const isActive =
                  pathname === item.href || pathname?.startsWith(item.href + '/')
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      className="flex items-center gap-2.5 px-2 py-2 rounded-lg text-sm font-medium transition-all"
                      style={{
                        background: isActive ? 'var(--sidebar-active-bg)' : 'transparent',
                        color: isActive ? 'var(--sidebar-active-text)' : 'var(--sidebar-nav-text)',
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
      <div
        className="mx-3 mb-4 px-3 py-3 rounded-xl"
        style={{ background: 'rgba(255,255,255,0.05)' }}
      >
        <div className="flex items-center gap-2.5">
          <div
            className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold shrink-0"
            style={{ background: 'hsl(var(--primary))' }}
          >
            {displayName
              .split(' ')
              .map((n: string) => n[0] ?? '')
              .join('')
              .slice(0, 2)
              .toUpperCase()}
          </div>
          <div className="min-w-0">
            <p
              className="text-sm font-semibold leading-none truncate"
              style={{ color: 'var(--sidebar-active-text)' }}
            >
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
