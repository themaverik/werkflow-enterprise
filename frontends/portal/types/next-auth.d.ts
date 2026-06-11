import 'next-auth'
import { DefaultSession } from 'next-auth'

declare module 'next-auth' {
  interface Session extends DefaultSession {
    accessToken?: string
    idToken?: string
    tenantId?: string
    doa_level?: string | number
    department?: string
    groups?: string[]
    error?: string
    user: DefaultSession['user'] & {
      roles?: string[]
      given_name?: string
      family_name?: string
    }
  }

  interface User {
    roles?: string[]
    tenantId?: string
    doa_level?: string | number
    department?: string
    username?: string
    firstName?: string
    lastName?: string
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    accessToken?: string
    refreshToken?: string
    idToken?: string
    expiresAt?: number
    roles?: string[]
    groups?: string[]
    doa_level?: string | number
    department?: string
    tenantId?: string
    preferred_username?: string
    given_name?: string
    family_name?: string
    error?: string
  }
}
