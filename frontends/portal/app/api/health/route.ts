import { NextResponse } from 'next/server'
import { auth } from '@/auth'

interface ServiceHealth {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  url: string
  details?: unknown
}

async function checkService(name: string, url: string): Promise<ServiceHealth> {
  try {
    const res = await fetch(`${url}/actuator/health`, {
      signal: AbortSignal.timeout(3000),
      next: { revalidate: 0 },
    })
    if (!res.ok) return { name, status: 'DOWN', url }
    const data = await res.json()
    return { name, status: data.status === 'UP' ? 'UP' : 'DOWN', url, details: data }
  } catch {
    return { name, status: 'DOWN', url }
  }
}

export async function GET() {
  const session = await auth()
  if (!session) {
    return new Response('Unauthorized', { status: 401 })
  }

  const isAdmin = (session.user?.roles ?? []).some(
    (r) => r.toUpperCase() === 'ADMIN' || r.toUpperCase() === 'SUPER_ADMIN',
  )

  const engineUrl = process.env.ENGINE_BASE_URL  ?? 'http://localhost:8081'
  // ADMIN_BASE_URL is the canonical portal env var (used by the proxy route).
  // ADMIN_SERVICE_URL is kept as a fallback for deployments that set only one of the two.
  const adminUrl  = process.env.ADMIN_BASE_URL ?? process.env.ADMIN_SERVICE_URL ?? 'http://localhost:8083'

  const [engine, admin] = await Promise.all([
    checkService('engine', engineUrl),
    checkService('admin', adminUrl),
  ])

  const portal: ServiceHealth = { name: 'portal', status: 'UP', url: 'self' }
  const allServices = [portal, engine, admin]
  const overallUp = allServices.every((s) => s.status === 'UP')

  return NextResponse.json(
    {
      status: overallUp ? 'UP' : 'DEGRADED',
      timestamp: new Date().toISOString(),
      services: allServices.map(({ name, status, url, details }) => ({
        name,
        status,
        ...(isAdmin ? { url, details } : {}),
      })),
    },
    { status: 200 }
  )
}
