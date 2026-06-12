import { auth } from '@/auth'
import { NextRequest, NextResponse } from 'next/server'

const ADMIN_BASE = process.env.ADMIN_SERVICE_URL ?? 'http://localhost:8083'

type RouteContext = { params: Promise<{ connectorKey: string; path: string[] }> }

async function proxy(req: NextRequest, ctx: RouteContext) {
  const session = await auth()
  if (!session?.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { connectorKey, path } = await ctx.params
  const connectorPath = '/' + path.join('/')

  // Pass the original HTTP method as a query param so admin knows the intended method
  const body =
    req.method !== 'GET' && req.method !== 'HEAD' ? await req.text() : undefined

  // tenantCode is intentionally omitted — admin service resolves tenant from the forwarded JWT
  const url = new URL(`${ADMIN_BASE}/api/connectors/${connectorKey}/call`)
  url.searchParams.set('path', connectorPath)
  url.searchParams.set('method', req.method)

  // Forward extra query params from the browser (e.g. filter, page).
  // Skip reserved keys that were already set server-side to prevent override.
  const RESERVED = new Set(['path', 'method'])
  req.nextUrl.searchParams.forEach((v, k) => {
    if (!RESERVED.has(k)) url.searchParams.set(k, v)
  })

  const upstream = await fetch(url.toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.accessToken}`,
    },
    body: body ?? undefined,
  })

  const text = await upstream.text()
  return new NextResponse(text, {
    status: upstream.status,
    headers: { 'Content-Type': upstream.headers.get('Content-Type') ?? 'application/json' },
  })
}

export const GET    = proxy
export const POST   = proxy
export const PUT    = proxy
export const PATCH  = proxy
export const DELETE = proxy
