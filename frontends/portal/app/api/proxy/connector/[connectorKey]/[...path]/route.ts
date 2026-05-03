import { auth } from '@/auth'
import { NextRequest, NextResponse } from 'next/server'

const ADMIN_BASE = process.env.NEXT_PUBLIC_ADMIN_SERVICE_URL ?? 'http://localhost:8083'
const TENANT_CODE = process.env.NEXT_PUBLIC_TENANT_CODE ?? 'default'

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

  const url = new URL(`${ADMIN_BASE}/api/connectors/${connectorKey}/call`)
  url.searchParams.set('tenantCode', TENANT_CODE)
  url.searchParams.set('path', connectorPath)
  url.searchParams.set('method', req.method)

  // Forward any extra query params from the browser (e.g. filter, page)
  req.nextUrl.searchParams.forEach((v, k) => url.searchParams.set(k, v))

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
