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

  const engineUrl = process.env.ENGINE_BASE_URL ?? 'http://localhost:8081'
  const adminUrl  = process.env.ADMIN_BASE_URL  ?? 'http://localhost:8083'

  const [engine, admin] = await Promise.all([
    checkService('engine', engineUrl),
    checkService('admin', adminUrl),
  ])

  const portal: ServiceHealth = { name: 'portal', status: 'UP', url: 'self' }
  const services = [portal, engine, admin]
  const overallUp = services.every((s) => s.status === 'UP')

  return NextResponse.json(
    {
      status: overallUp ? 'UP' : 'DEGRADED',
      timestamp: new Date().toISOString(),
      services,
    },
    { status: 200 }
  )
}
