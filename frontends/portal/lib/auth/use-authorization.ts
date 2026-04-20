'use client'

import { useAuth } from './auth-context'
import { useCallback, useState, useEffect } from 'react'
import { getDoaLevel, getDisplayRole } from '@/lib/utils/role-display'

export interface UseAuthorizationOptions {
  onDenied?: () => void
}

export interface UseAuthorizationReturn {
  hasRole: (role: string) => boolean
  hasAnyRole: (roles: string[]) => boolean
  hasAllRoles: (roles: string[]) => boolean
  canAccessRoute: (routePath: string) => Promise<boolean>
  getDOALevel: () => number | undefined
  getDepartment: () => string | undefined
  hasPermission: (permission: string) => boolean
  doaLevel: number
  displayRole: string
}

/**
 * Hook for checking user authorization and permissions.
 * Uses JWT claims from Keycloak token and backend role checks.
 */
export function useAuthorization(options?: UseAuthorizationOptions): UseAuthorizationReturn {
  const { user, token, isAuthenticated } = useAuth()
  const [routeAccessCache, setRouteAccessCache] = useState<Map<string, boolean>>(new Map())

  const hasRole = useCallback((role: string): boolean => {
    if (!isAuthenticated || !user) return false
    return user.roles.some(userRole => userRole.toUpperCase() === role.toUpperCase())
  }, [user, isAuthenticated])

  const hasAnyRole = useCallback((roles: string[]): boolean => {
    if (!isAuthenticated || !user || !roles.length) return false
    return roles.some(requiredRole =>
      user.roles.some(userRole => userRole.toUpperCase() === requiredRole.toUpperCase())
    )
  }, [user, isAuthenticated])

  const hasAllRoles = useCallback((roles: string[]): boolean => {
    if (!isAuthenticated || !user || !roles.length) return false
    return roles.every(requiredRole =>
      user.roles.some(userRole => userRole.toUpperCase() === requiredRole.toUpperCase())
    )
  }, [user, isAuthenticated])

  const canAccessRoute = useCallback(async (routePath: string): Promise<boolean> => {
    if (!isAuthenticated || !token) {
      options?.onDenied?.()
      return false
    }

    // Check cache first
    const cached = routeAccessCache.get(routePath)
    if (cached !== undefined) {
      return cached
    }

    try {
      // Call backend admin service to check access
      const adminApiUrl = process.env.NEXT_PUBLIC_ADMIN_API_URL || 'http://localhost:8083/api'
      const response = await fetch(
        `${adminApiUrl}/routes/has-access/${routePath.replace(/^\//, '')}`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      )

      const data = await response.json()
      const hasAccess = data.hasAccess === true

      // Cache the result
      setRouteAccessCache(prev => new Map(prev).set(routePath, hasAccess))

      if (!hasAccess) {
        options?.onDenied?.()
      }

      return hasAccess
    } catch (error) {
      console.error(`Error checking access to ${routePath}:`, error)
      return false
    }
  }, [isAuthenticated, token, routeAccessCache, options])

  const getDOALevel = useCallback((): number | undefined => {
    return user?.doaLevel
  }, [user])

  const getDepartment = useCallback((): string | undefined => {
    return user?.department
  }, [user])

  const hasPermission = useCallback((permission: string): boolean => {
    if (!isAuthenticated || !user) return false

    // Simple permission check based on roles
    // In a real system, this would be more sophisticated
    const permissionMap: Record<string, string[]> = {
      'approve_capex': ['finance_approver', 'admin', 'super_admin'],
      'approve_procurement': ['procurement_approver', 'admin', 'super_admin'],
      'manage_workflows': ['workflow_admin', 'super_admin'],
      'manage_users': ['admin', 'super_admin'],
      'view_analytics': ['analyst', 'admin', 'super_admin'],
    }

    const requiredRoles = permissionMap[permission] || []
    return hasAnyRole(requiredRoles)
  }, [user, isAuthenticated, hasAnyRole])

  const doaLevel = getDoaLevel(user?.roles ?? [])
  const displayRole = getDisplayRole(user?.roles ?? [], user?.department ?? undefined)

  return {
    hasRole,
    hasAnyRole,
    hasAllRoles,
    canAccessRoute,
    getDOALevel,
    getDepartment,
    hasPermission,
    doaLevel,
    displayRole,
  }
}
