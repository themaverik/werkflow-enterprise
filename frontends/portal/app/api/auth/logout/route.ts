import { NextRequest, NextResponse } from 'next/server'
import { auth } from '@/auth'

export async function GET(request: NextRequest) {
  const session = await auth()
  const idToken = session?.idToken

  const keycloakIssuer =
    process.env.KEYCLOAK_ISSUER_BROWSER || process.env.KEYCLOAK_ISSUER
  const clientId = process.env.KEYCLOAK_CLIENT_ID

  if (!keycloakIssuer || !clientId) {
    return NextResponse.json({ url: '/login' })
  }

  const postLogoutUri = `${request.nextUrl.origin}/login`
  const params = new URLSearchParams({
    client_id: clientId,
    post_logout_redirect_uri: postLogoutUri,
  })
  if (idToken) params.set('id_token_hint', idToken)

  return NextResponse.json({
    url: `${keycloakIssuer}/protocol/openid-connect/logout?${params.toString()}`,
  })
}
