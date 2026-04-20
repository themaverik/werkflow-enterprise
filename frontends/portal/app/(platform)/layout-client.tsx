'use client'

import { ReactNode, useEffect, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'

interface StudioLayoutClientProps {
  children: ReactNode
  initialRoles: string[]
  session: any
}

/**
 * Client-side layout wrapper for Studio that checks dynamic role configuration.
 * Uses backend /api/routes/has-access endpoint to determine if user can access /studio.
 */
export function StudioLayoutClient({
  children,
  initialRoles,
  session,
}: StudioLayoutClientProps) {
  const { token, isAuthenticated } = useAuth()
  const { canAccessRoute } = useAuthorization()
  const [hasAccess, setHasAccess] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!isAuthenticated) {
      setHasAccess(false)
      return
    }

    // Check access via backend
    const checkAccess = async () => {
      try {
        const access = await canAccessRoute('/studio')
        setHasAccess(access)
      } catch (err) {
        console.error('Error checking studio access:', err)
        setError('Failed to verify access permissions')
        setHasAccess(false)
      }
    }

    checkAccess()
  }, [isAuthenticated, token, canAccessRoute])

  if (hasAccess === null) {
    return (
      <div className="container py-12 flex items-center justify-center min-h-[500px]">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
          <p className="mt-4 text-sm text-muted-foreground">Checking permissions...</p>
        </div>
      </div>
    )
  }

  if (!hasAccess) {
    return (
      <div className="container py-12">
        <Card className="max-w-2xl mx-auto">
          <CardHeader>
            <CardTitle>Access Denied</CardTitle>
            <CardDescription>You don't have permission to access the Studio</CardDescription>
          </CardHeader>
          <CardContent>
            {error && (
              <p className="text-sm text-red-600 mb-4">
                <strong>Error:</strong> {error}
              </p>
            )}
            <p className="text-sm text-muted-foreground mb-6">
              The Process Studio and Form Builder require specific administrator roles. Please contact your
              administrator if you need access.
            </p>
            <div className="space-y-3">
              <p className="text-sm font-semibold text-foreground">Your current roles:</p>
              <div className="flex flex-wrap gap-2 max-h-48 overflow-y-auto p-2 border rounded-md bg-muted/30">
                {initialRoles && initialRoles.length > 0 ? (
                  initialRoles.map(role => (
                    <span
                      key={role}
                      className="inline-flex items-center px-3 py-1.5 bg-secondary text-secondary-foreground rounded-md text-xs font-medium whitespace-nowrap"
                    >
                      {role}
                    </span>
                  ))
                ) : (
                  <span className="text-sm text-muted-foreground">No roles assigned</span>
                )}
              </div>
            </div>
            <div className="mt-6 pt-4 border-t">
              <a
                href="/dashboard"
                className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
              >
                Go to My Tasks
              </a>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return <>{children}</>
}
