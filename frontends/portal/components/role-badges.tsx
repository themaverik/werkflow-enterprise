'use client'

import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'

interface RoleBadgesProps {
  maxBadges?: number
  className?: string
}

const roleColors: Record<string, { bg: string; text: string }> = {
  SUPER_ADMIN: { bg: 'bg-red-100', text: 'text-red-800' },
  ADMIN: { bg: 'bg-orange-100', text: 'text-orange-800' },
  HR_ADMIN: { bg: 'bg-blue-100', text: 'text-blue-800' },
  FINANCE_ADMIN: { bg: 'bg-green-100', text: 'text-green-800' },
  SERVICE_ADMIN: { bg: 'bg-purple-100', text: 'text-purple-800' },
  PROCUREMENT_ADMIN: { bg: 'bg-indigo-100', text: 'text-indigo-800' },
  HR_STAFF: { bg: 'bg-blue-50', text: 'text-blue-700' },
  FINANCE_STAFF: { bg: 'bg-green-50', text: 'text-green-700' },
  PROCUREMENT_STAFF: { bg: 'bg-indigo-50', text: 'text-indigo-700' },
  INVENTORY_STAFF: { bg: 'bg-cyan-50', text: 'text-cyan-700' },
  EMPLOYEE: { bg: 'bg-gray-100', text: 'text-gray-800' },
}

export function RoleBadges({ maxBadges = 3, className = '' }: RoleBadgesProps) {
  const { user, isAuthenticated } = useAuth()
  const { getDOALevel, getDepartment } = useAuthorization()

  if (!isAuthenticated || !user || !user.roles.length) {
    return null
  }

  const displayedRoles = user.roles.slice(0, maxBadges)
  const hiddenRoleCount = Math.max(0, user.roles.length - maxBadges)
  const doaLevel = getDOALevel()
  const department = getDepartment()

  const getRoleColor = (role: string) => {
    return roleColors[role] || { bg: 'bg-gray-100', text: 'text-gray-800' }
  }

  const getDoALevelLabel = (level?: number): string => {
    switch (level) {
      case 1:
        return 'Level 1 (<$1K)'
      case 2:
        return 'Level 2 (<$10K)'
      case 3:
        return 'Level 3 (<$100K)'
      case 4:
        return 'Level 4 (Unlimited)'
      default:
        return 'No DOA'
    }
  }

  return (
    <div className={`flex flex-wrap items-center gap-2 ${className}`}>
      {displayedRoles.map(role => {
        const colors = getRoleColor(role)
        return (
          <span
            key={role}
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colors.bg} ${colors.text}`}
            title={`Role: ${role.replace(/_/g, ' ')}`}
          >
            {role.replace(/_/g, ' ')}
          </span>
        )
      })}

      {hiddenRoleCount > 0 && (
        <span
          className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800"
          title={`${hiddenRoleCount} more role(s)`}
        >
          +{hiddenRoleCount} more
        </span>
      )}

      {doaLevel !== undefined && doaLevel > 0 && (
        <span
          className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800"
          title="Delegation of Authority Level"
        >
          DOA: {getDoALevelLabel(doaLevel)}
        </span>
      )}

      {department && (
        <span
          className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-800"
          title="Department"
        >
          {department}
        </span>
      )}
    </div>
  )
}

export function CompactRoleBadges({ className = '' }: { className?: string }) {
  const { user, isAuthenticated } = useAuth()

  if (!isAuthenticated || !user || !user.roles.length) {
    return null
  }

  // Show first role only in compact view
  const primaryRole = user.roles[0]
  const colors = roleColors[primaryRole] || { bg: 'bg-gray-100', text: 'text-gray-800' }

  return (
    <span
      className={`inline-block px-3 py-1 rounded-full text-xs font-semibold ${colors.bg} ${colors.text} ${className}`}
      title={`Roles: ${user.roles.join(', ')}`}
    >
      {primaryRole.replace(/_/g, ' ')}
      {user.roles.length > 1 && ` +${user.roles.length - 1}`}
    </span>
  )
}
