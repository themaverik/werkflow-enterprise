'use client'

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { useSession, signIn, signOut } from 'next-auth/react'

export interface User {
  username: string
  email: string
  firstName?: string
  lastName?: string
  roles: string[]
  groups: string[]
  doaLevel?: number
  department?: string
  tenantId: string
}

export interface AuthContextType {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  token: string | null
  idToken: string | null
  login: () => Promise<void>
  logout: () => Promise<void>
  refreshToken: () => Promise<string | null>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { data: session, status } = useSession()
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(status === 'loading')

  // MED-11: force sign-out when the refresh token has expired
  useEffect(() => {
    if (status === 'authenticated' && (session as any)?.error === 'RefreshAccessTokenError') {
      signOut({ redirect: true, callbackUrl: '/login' })
    }
  }, [session, status])

  // Update user when session changes
  useEffect(() => {
    if (status === 'authenticated' && session) {
      const s = session as any
      const sessionUser: User = {
        username: s.user?.name || '',
        email: s.user?.email || '',
        firstName: s.user?.given_name,
        lastName: s.user?.family_name,
        roles: s.user?.roles || [],
        groups: s.groups || [],
        doaLevel: typeof s.doa_level === 'number' ? s.doa_level : Number(s.doa_level) || undefined,
        department: s.department,
        tenantId: s.tenantId ?? 'default',
      }
      setUser(sessionUser)
    } else if (status === 'unauthenticated') {
      setUser(null)
    }
    setIsLoading(status === 'loading')
  }, [session, status])

  const login = useCallback(async () => {
    await signIn('keycloak', { redirect: true, callbackUrl: '/dashboard' })
  }, [])

  const logout = useCallback(async () => {
    // Fetch KC logout URL server-side before clearing session.
    // KEYCLOAK_ISSUER_BROWSER / KEYCLOAK_CLIENT_ID are server-only env vars
    // (no NEXT_PUBLIC_ prefix) so they cannot be read client-side directly.
    let kcLogoutUrl = '/login'
    try {
      const res = await fetch('/api/auth/logout')
      if (res.ok) {
        const data = await res.json()
        kcLogoutUrl = data.url ?? '/login'
      }
    } catch {
      // fall through to /login
    }

    await signOut({ redirect: false })
    window.location.href = kcLogoutUrl
  }, [])

  const refreshToken = useCallback(async (): Promise<string | null> => {
    // Token refresh is handled by next-auth automatically
    // This is a placeholder for explicit refresh if needed
    return (session as any)?.accessToken || null
  }, [session])

  const value: AuthContextType = {
    user,
    isAuthenticated: status === 'authenticated',
    isLoading,
    token: (session as any)?.accessToken || null,
    idToken: (session as any)?.idToken || null,
    login,
    logout,
    refreshToken,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
