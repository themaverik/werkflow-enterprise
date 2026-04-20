import type { UserClaims } from '@/lib/types/task'

export class JwtError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'JwtError'
  }
}

export async function getTokenFromStorage(): Promise<string | null> {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const token = localStorage.getItem('auth_token')
    return token
  } catch (error) {
    console.error('Error retrieving token from storage:', error)
    return null
  }
}

export async function setTokenInStorage(token: string): Promise<void> {
  if (typeof window === 'undefined') {
    return
  }

  try {
    localStorage.setItem('auth_token', token)
  } catch (error) {
    console.error('Error storing token:', error)
  }
}

export async function removeTokenFromStorage(): Promise<void> {
  if (typeof window === 'undefined') {
    return
  }

  try {
    localStorage.removeItem('auth_token')
  } catch (error) {
    console.error('Error removing token:', error)
  }
}

export function parseJwtToken(token: string): any {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      throw new JwtError('Invalid JWT token format')
    }

    const payload = parts[1]
    const decoded = Buffer.from(payload, 'base64').toString('utf-8')
    return JSON.parse(decoded)
  } catch (error) {
    if (error instanceof JwtError) {
      throw error
    }
    throw new JwtError('Failed to parse JWT token')
  }
}

export function extractUserClaims(token: string): UserClaims {
  try {
    const payload = parseJwtToken(token)

    return {
      sub: payload.sub || '',
      preferred_username: payload.preferred_username || payload.username || '',
      email: payload.email || '',
      name: payload.name || payload.given_name || payload.preferred_username || '',
      department: payload.department || '',
      roles: payload.realm_access?.roles || payload.roles || [],
      groups: payload.groups || [],
      doaLevel: payload.doaLevel || payload.doa_level || 0,
      managerId: payload.managerId || payload.manager_id || '',
    }
  } catch (error) {
    throw new JwtError('Failed to extract user claims from token')
  }
}

export function isTokenExpired(token: string): boolean {
  try {
    const payload = parseJwtToken(token)
    const exp = payload.exp

    if (!exp) {
      return true
    }

    const currentTime = Math.floor(Date.now() / 1000)
    return currentTime >= exp
  } catch (error) {
    return true
  }
}

export function getTokenExpirationTime(token: string): Date | null {
  try {
    const payload = parseJwtToken(token)
    const exp = payload.exp

    if (!exp) {
      return null
    }

    return new Date(exp * 1000)
  } catch (error) {
    return null
  }
}

export function getUserGroups(userClaims: UserClaims): string[] {
  return userClaims.groups || []
}

export function mapGroupsToCandidateGroups(keycloakGroups: string[]): string[] {
  // Pass through the raw Keycloak group names as-is.
  // These must match the candidateGroups defined in BPMN process definitions
  // (e.g. FINANCE_MANAGER, FINANCE_VP, FINANCE_CFO).
  return keycloakGroups.filter(g => g && g.trim().length > 0)
}

export function hasRole(userClaims: UserClaims, role: string): boolean {
  return (userClaims.roles || []).some(r => r.toLowerCase() === role.toLowerCase())
}

export function hasAnyRole(userClaims: UserClaims, roles: string[]): boolean {
  return roles.some(role => hasRole(userClaims, role))
}

export function isManager(userClaims: UserClaims): boolean {
  return hasRole(userClaims, 'manager') || hasRole(userClaims, 'department_manager')
}

export function isApprover(userClaims: UserClaims, requiredDoaLevel?: number): boolean {
  if (requiredDoaLevel !== undefined) {
    return (userClaims.doaLevel || 0) >= requiredDoaLevel
  }
  return (userClaims.doaLevel || 0) > 0
}
