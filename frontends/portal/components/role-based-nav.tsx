'use client'

import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'
import Link from 'next/link'

export interface NavigationItem {
  label: string
  href: string
  requiredRoles?: string[]
  icon?: React.ReactNode
}

export interface RoleBasedNavProps {
  items: NavigationItem[]
  className?: string
  activePathname?: string
}

/**
 * Navigation component that shows/hides menu items based on user roles.
 */
export function RoleBasedNav({ items, className = '', activePathname }: RoleBasedNavProps) {
  const { isAuthenticated } = useAuth()
  const { hasAnyRole } = useAuthorization()

  if (!isAuthenticated) {
    return null
  }

  const visibleItems = items.filter(item => {
    // Show item if no roles required, or user has required role
    if (!item.requiredRoles || item.requiredRoles.length === 0) {
      return true
    }
    return hasAnyRole(item.requiredRoles)
  })

  return (
    <nav className={`space-y-1 ${className}`}>
      {visibleItems.map(item => (
        <Link
          key={item.href}
          href={item.href}
          className={`flex items-center px-4 py-2 rounded-md transition-colors ${
            activePathname === item.href
              ? 'bg-blue-100 text-blue-900 font-medium'
              : 'text-gray-700 hover:bg-gray-100'
          }`}
        >
          {item.icon && <span className="mr-3">{item.icon}</span>}
          {item.label}
        </Link>
      ))}
    </nav>
  )
}

/**
 * Admin navigation items - visible only to ADMIN and SUPER_ADMIN roles
 */
export const adminNavItems: NavigationItem[] = [
  {
    label: 'Users',
    href: '/admin/users',
    requiredRoles: ['ADMIN', 'SUPER_ADMIN'],
  },
  {
    label: 'Roles',
    href: '/admin/roles',
    requiredRoles: ['SUPER_ADMIN'],
  },
  {
    label: 'Departments',
    href: '/admin/departments',
    requiredRoles: ['ADMIN', 'SUPER_ADMIN'],
  },
  {
    label: 'Services',
    href: '/services',
    requiredRoles: ['SERVICE_ADMIN', 'ADMIN', 'SUPER_ADMIN'],
  },
]

