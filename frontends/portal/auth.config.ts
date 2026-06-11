import type { NextAuthConfig, Session } from "next-auth"
import type { JWT } from "next-auth/jwt"
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
  groups?: string[];
  doa_level?: string | number;
  department?: string;
  preferred_username?: string;
  given_name?: string;
  family_name?: string;
  tenant_id?: string;
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

      // KC_HOSTNAME_STRICT_BACKCHANNEL=true (docker-compose) allows backchannel token exchange
      // to use the internal Docker URL (keycloak:8080) while the browser-facing URL remains
      // localhost:8090. PKCE is re-enabled now that the dual-URL split is handled at the KC layer.
      checks: ["pkce", "state"],

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
          token.groups = decodedToken.groups || [];
          // Derive DOA level from doa_approver_levelN roles if not explicitly set as a claim
          const doaLevelFromClaim = decodedToken.doa_level;
          if (doaLevelFromClaim !== undefined) {
            token.doa_level = doaLevelFromClaim;
          } else {
            const roles: string[] = decodedToken.realm_access?.roles || [];
            const doaRole = roles.find(r => /^doa_approver_level(\d+)$/.test(r));
            const match = doaRole?.match(/^doa_approver_level(\d+)$/);
            token.doa_level = match ? parseInt(match[1], 10) : undefined;
          }
          token.department = decodedToken.department;
          token.preferred_username = decodedToken.preferred_username;
          token.given_name = decodedToken.given_name;
          token.family_name = decodedToken.family_name;
          token.tenantId = decodedToken.tenant_id ?? 'default';

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
    async session({ session, token }: { session: Session; token: JWT }) {
      session.accessToken = token.accessToken ?? ''
      session.idToken = token.idToken ?? ''
      if (session.user) {
        session.user.roles = token.roles
        session.user.name = token.preferred_username || session.user.name
        session.user.given_name = token.given_name
        session.user.family_name = token.family_name
      }
      session.groups = token.groups
      session.doa_level = token.doa_level
      session.department = token.department
      session.tenantId = token.tenantId ?? ''
      if (token.error) {
        session.error = token.error
      }

      return session
    },
    authorized({ auth, request: { nextUrl } }) {
      // MED-11: force re-auth when refresh token has expired
      if (auth?.error === 'RefreshAccessTokenError') {
        return false
      }

      const isLoggedIn = !!auth?.user
      const pathname = nextUrl.pathname

      // Public routes
      const publicRoutes = ['/', '/login', '/error']
      if (publicRoutes.includes(pathname)) {
        return true
      }

      // Admin routes require authentication + ADMIN or SUPER_ADMIN role
      if (pathname.startsWith('/admin')) {
        if (!isLoggedIn) return false
        const roles: string[] = auth?.user?.roles ?? []
        const isAdmin = roles.some(r => r.toUpperCase() === 'ADMIN' || r.toUpperCase() === 'SUPER_ADMIN')
        if (!isAdmin) {
          return Response.redirect(new URL('/dashboard', nextUrl))
        }
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
