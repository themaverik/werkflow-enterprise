'use client'

import { ReactNode, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'

export interface ProtectedRouteProps {
  children: ReactNode
  requiredRoles?: string[]
  requiredAllRoles?: boolean
  fallback?: ReactNode
}

/**
 * Component that protects routes based on user roles.
 *
 * @param requiredRoles - Array of role names (OR logic by default)
 * @param requiredAllRoles - If true, user must have ALL roles (AND logic)
 * @param fallback - Component to show while checking access
 */
export function ProtectedRoute({
  children,
  requiredRoles = [],
  requiredAllRoles = false,
  fallback = <AccessDenied />,
}: ProtectedRouteProps) {
  const router = useRouter()
  const { isAuthenticated, isLoading } = useAuth()
  const { hasAnyRole, hasAllRoles } = useAuthorization()
  const [hasAccess, setHasAccess] = useState<boolean | null>(null)

  useEffect(() => {
    if (isLoading) {
      return
    }

    if (!isAuthenticated) {
      router.push('/login')
      return
    }

    if (!requiredRoles.length) {
      setHasAccess(true)
      return
    }

    const access = requiredAllRoles ? hasAllRoles(requiredRoles) : hasAnyRole(requiredRoles)
    setHasAccess(access)

    if (!access) {
      // Log access denial for audit
      console.warn(`Access denied. Required roles: ${requiredRoles.join(', ')}`)
    }
  }, [isAuthenticated, isLoading, requiredRoles, requiredAllRoles, hasAnyRole, hasAllRoles, router])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-lg text-gray-600">Loading...</div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return null // Router will redirect to login
  }

  if (hasAccess === null) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-lg text-gray-600">Checking permissions...</div>
      </div>
    )
  }

  if (hasAccess) {
    return <>{children}</>
  }

  return <>{fallback}</>
}

export function AccessDenied() {
  const { logout } = useAuth()

  return (
    <div className="flex items-center justify-center h-screen">
      <div className="text-center">
        <h1 className="text-4xl font-bold text-red-600 mb-4">403</h1>
        <h2 className="text-2xl font-semibold mb-2">Access Denied</h2>
        <p className="text-gray-600 mb-6">You do not have permission to access this resource.</p>
        <div className="space-x-4">
          <button
            onClick={() => window.history.back()}
            className="px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300"
          >
            Go Back
          </button>
          <button
            onClick={() => logout()}
            className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
          >
            Logout
          </button>
        </div>
      </div>
    </div>
  )
}
