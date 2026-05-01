import { NextResponse } from 'next/server'

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
  const engineUrl = process.env.NEXT_PUBLIC_ENGINE_API_URL ?? 'http://localhost:8081'
  const adminUrl  = process.env.NEXT_PUBLIC_ADMIN_SERVICE_URL ?? 'http://localhost:8083'
  const erpUrl    = process.env.NEXT_PUBLIC_ERP_API_URL ?? 'http://localhost:8085'

  const [engine, admin, erp] = await Promise.all([
    checkService('engine', engineUrl),
    checkService('admin', adminUrl),
    checkService('erp', erpUrl),
  ])

  const portal: ServiceHealth = { name: 'portal', status: 'UP', url: 'self' }
  const services = [portal, engine, admin, erp]
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
