'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'
import type { NavigationItem } from '@/components/role-based-nav'

interface SidebarSection {
  titleKey: string
  items: (NavigationItem & { labelKey: string })[]
}

const sidebarSections: SidebarSection[] = [
  {
    titleKey: 'general',
    items: [
      { labelKey: 'dashboard', label: 'Dashboard', href: '/dashboard' },
      { labelKey: 'myTasks', label: 'My Tasks', href: '/tasks' },
      { labelKey: 'myRequests', label: 'My Requests', href: '/requests' },
      { labelKey: 'createRequest', label: 'Create a Request', href: '/processes' },
    ],
  },
  {
    titleKey: 'designStudio',
    items: [
      { labelKey: 'processes', label: 'Processes', href: '/processes', requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'forms', label: 'Forms', href: '/forms', requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
      { labelKey: 'decisions', label: 'Decisions', href: '/decisions', requiredRoles: ['ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'SUPER_ADMIN'] },
    ],
  },
  {
    titleKey: 'admin',
    items: [
      { labelKey: 'authorityLevels', label: 'Authority Levels', href: '/admin/doa', requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'departments', label: 'Departments', href: '/admin/departments', requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'connectors', label: 'Connectors', href: '/admin/connectors', requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'custodyMappings', label: 'Custody Mappings', href: '/admin/custody', requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
      { labelKey: 'emailTemplates', label: 'Email Templates', href: '/admin/email-templates', requiredRoles: ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN'] },
    ],
  },
  // System section hidden until Monitoring and Analytics are implemented
  // {
  //   titleKey: 'system',
  //   items: [
  //     { labelKey: 'monitoring', label: 'Monitoring', href: '/monitoring', requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
  //     { labelKey: 'analytics', label: 'Analytics', href: '/analytics', requiredRoles: ['ADMIN', 'SUPER_ADMIN'] },
  //   ],
  // },
]

export function Sidebar() {
  const pathname = usePathname()
  const { isAuthenticated } = useAuth()
  const { hasAnyRole } = useAuthorization()
  const t = useTranslations('nav')

  if (!isAuthenticated) {
    return null
  }

  const visibleSections = sidebarSections
    .map(section => ({
      ...section,
      items: section.items.filter(item => {
        if (!item.requiredRoles || item.requiredRoles.length === 0) return true
        return hasAnyRole(item.requiredRoles)
      }),
    }))
    .filter(section => section.items.length > 0)

  return (
    <aside
      className="w-64 border-r min-h-[calc(100vh-3.5rem)] p-4 space-y-6 shrink-0"
      style={{ background: '#f0f4f5', color: '#149ba5', fontWeight: 600, lineHeight: 1 }}
    >
      {visibleSections.map(section => (
        <div key={section.titleKey}>
          <h3 className="px-3 mb-2 text-xs uppercase tracking-wider" style={{ color: '#149ba5', fontWeight: 600 }}>
            {t(section.titleKey as Parameters<typeof t>[0])}
          </h3>
          <nav className="space-y-1">
            {section.items.map(item => {
              const isActive = pathname === item.href || pathname?.startsWith(item.href + '/')
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center px-3 py-2 text-sm rounded-md transition-colors ${
                    isActive
                      ? 'bg-white/50'
                      : 'hover:bg-white/30'
                  }`}
                  style={{ color: '#149ba5', fontWeight: 600, lineHeight: 1 }}
                >
                  {t(item.labelKey as Parameters<typeof t>[0])}
                </Link>
              )
            })}
          </nav>
        </div>
      ))}
    </aside>
  )
}
