import type { NextAuthConfig } from "next-auth"
import Keycloak from "next-auth/providers/keycloak"
import { decodeJwt, JWTPayload } from 'jose'; // Import the decode function from jose

// 1. Define the specific shape of your Keycloak payload
interface KeycloakJWTPayload extends JWTPayload {
  realm_access?: {
    roles: string[];
  };
  resource_access?: {
    [clientId: string]: {
      roles: string[];
    };
  };
  // Add other custom claims here if needed
}

/**
 * NextAuth Configuration for Keycloak OAuth2
 *
 * This configuration handles the dual-URL challenge in Docker environments:
 * - Browser needs to reach Keycloak via localhost:8090 (host-mapped port)
 * - Server-side code needs to reach Keycloak via keycloak:8080 (internal Docker network)
 * - Tokens contain issuer=localhost:8090 (what Keycloak advertises via KC_HOSTNAME)
 *
 * Three-URL Strategy:
 * - KEYCLOAK_ISSUER_INTERNAL: Server-side OIDC discovery (keycloak:8080 in Docker)
 * - KEYCLOAK_ISSUER_PUBLIC: Token validation issuer (localhost:8090 - matches token claims)
 * - KEYCLOAK_ISSUER_BROWSER: Browser redirects (localhost:8090 - user-accessible)
 */
export const authConfig = {
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,

      // CRITICAL: Use KEYCLOAK_ISSUER_PUBLIC for issuer validation
      // This must match what Keycloak puts in the "iss" claim of tokens
      // Keycloak returns "http://localhost:8090/realms/werkflow" via KC_HOSTNAME config
      issuer: process.env.KEYCLOAK_ISSUER_PUBLIC || process.env.KEYCLOAK_ISSUER,

      // Disable PKCE: confidential client (client_secret) provides equivalent security.
      // PKCE code_verifier is bound to the authorization URL cookie; the dual-URL setup
      // (browser→localhost:8090, server→keycloak:8080) causes the verifier lookup to fail
      // during token exchange → pkce_verification_failed.
      checks: ["state"],

      // Override authorization URL for browser redirects
      // Users click login -> browser redirects to this URL (must be accessible from browser)
      authorization: {
        params: { scope: "openid email profile" },
        url: `${process.env.KEYCLOAK_ISSUER_BROWSER || process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/auth`,
      },

      // Override token URL to use internal network for server-side token exchange
      // After browser redirect with code, server exchanges code for token here
      token: `${process.env.KEYCLOAK_ISSUER_INTERNAL || process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/token`,

      // Override userinfo URL to use internal network for server-side user data fetch
      // Server fetches user profile data after getting token
      userinfo: `${process.env.KEYCLOAK_ISSUER_INTERNAL || process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/userinfo`,
    })
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      // Initial sign in
      if (account) {
        token.accessToken = account.access_token
        token.refreshToken = account.refresh_token
        token.idToken = account.id_token
        token.expiresAt = account.expires_at

        // Extract roles from Keycloak token
        const realmAccess = (profile as any)?.realm_access
        token.roles = realmAccess?.roles || []

        // HIGH-08: JWT claims must not be logged in production
        if (process.env.NODE_ENV === 'development') {
          console.log('Realm access: ', realmAccess)
          console.log('Realm Roles: ', realmAccess?.roles)
        }

        try {
          const decodedToken = decodeJwt(account.access_token || '') as KeycloakJWTPayload;

          token.roles = decodedToken.realm_access?.roles || [];
          token.groups = (decodedToken as any).groups || [];
          // Derive DOA level from doa_approver_levelN roles if not explicitly set as a claim
          const doaLevelFromClaim = (decodedToken as any).doa_level;
          if (doaLevelFromClaim !== undefined) {
            token.doa_level = doaLevelFromClaim;
          } else {
            const roles: string[] = decodedToken.realm_access?.roles || [];
            const doaRole = roles.find(r => /^doa_approver_level(\d+)$/.test(r));
            const match = doaRole?.match(/^doa_approver_level(\d+)$/);
            token.doa_level = match ? parseInt(match[1], 10) : undefined;
          }
          token.department = (decodedToken as any).department;
          token.preferred_username = (decodedToken as any).preferred_username;
          token.given_name = (decodedToken as any).given_name;
          token.family_name = (decodedToken as any).family_name;

        } catch (error) {
          console.error("Error decoding access token with jose:", error);
        }
      } else if (token.refreshToken && token.expiresAt) {
        // Token refresh: check if expired and refresh if needed
        const now = Math.floor(Date.now() / 1000)
        const refreshThreshold = 60 // Refresh if within 60 seconds of expiry

        if (now > (token.expiresAt as number) - refreshThreshold) {
          try {
            const response = await fetch(
              `${process.env.KEYCLOAK_ISSUER_INTERNAL || process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/token`,
              {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({
                  grant_type: 'refresh_token',
                  client_id: process.env.KEYCLOAK_CLIENT_ID!,
                  client_secret: process.env.KEYCLOAK_CLIENT_SECRET!,
                  refresh_token: token.refreshToken as string,
                }).toString(),
              }
            )

            if (response.ok) {
              const refreshedTokens = await response.json()
              token.accessToken = refreshedTokens.access_token
              token.refreshToken = refreshedTokens.refresh_token
              token.idToken = refreshedTokens.id_token
              token.expiresAt = Math.floor(Date.now() / 1000) + (refreshedTokens.expires_in || 300)
            } else {
              console.error('Token refresh failed:', response.status)
              return { ...token, error: 'RefreshAccessTokenError' as const }
            }
          } catch (error) {
            console.error('Error refreshing token:', error)
            return { ...token, error: 'RefreshAccessTokenError' as const }
          }
        }
      }

      return token
    },
    async session({ session, token }) {
      const s = session as any
      s.accessToken = token.accessToken as string
      s.idToken = token.idToken as string
      if (s.user) {
        s.user.roles = token.roles as string[]
        s.user.name = (token.preferred_username as string) || s.user.name
        s.user.given_name = token.given_name
        s.user.family_name = token.family_name
      }
      s.groups = token.groups as string[]
      s.doa_level = token.doa_level
      s.department = token.department
      if (token.error) {
        s.error = token.error
      }

      return session
    },
    authorized({ auth, request: { nextUrl } }) {
      // MED-11: force re-auth when refresh token has expired
      if ((auth as any)?.error === 'RefreshAccessTokenError') {
        return false
      }

      const isLoggedIn = !!auth?.user
      const pathname = nextUrl.pathname

      // Public routes
      const publicRoutes = ['/', '/login', '/error']
      if (publicRoutes.includes(pathname)) {
        return true
      }

      // All app routes require authentication
      const isProtectedRoute =
        pathname.startsWith('/dashboard') ||
        pathname.startsWith('/processes') ||
        pathname.startsWith('/forms') ||
        pathname.startsWith('/workflows') ||
        pathname.startsWith('/services') ||
        pathname.startsWith('/tasks') ||
        pathname.startsWith('/requests') ||
        pathname.startsWith('/monitoring') ||
        pathname.startsWith('/analytics') ||
        pathname.startsWith('/hr') ||
        pathname.startsWith('/finance') ||
        pathname.startsWith('/procurement') ||
        pathname.startsWith('/inventory')

      if (isProtectedRoute) {
        return isLoggedIn
      }

      return true
    },
  },
  pages: {
    signIn: '/login',
  },
  session: {
    strategy: 'jwt',
  },
  trustHost: true,
} satisfies NextAuthConfig
