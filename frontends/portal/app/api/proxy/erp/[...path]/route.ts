import { auth } from '@/auth'
import { NextRequest, NextResponse } from 'next/server'

const ERP_BASE = process.env.ERP_BASE_URL ?? 'http://localhost:8084'

async function proxy(req: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const session = await auth()
  if (!session?.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { path } = await params
  const url = new URL(req.url)
  const target = `${ERP_BASE}/api/v1/${path.join('/')}${url.search}`

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${session.accessToken}`,
  }

  const body = req.method !== 'GET' && req.method !== 'HEAD'
    ? await req.text()
    : undefined

  const upstream = await fetch(target, {
    method: req.method,
    headers,
    body,
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
