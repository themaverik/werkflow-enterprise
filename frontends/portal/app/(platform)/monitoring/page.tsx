'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { RefreshCw, CheckCircle, XCircle, AlertCircle, Clock } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/ui/status-badge'

interface ServiceDetail {
  status: string
  components?: Record<string, { status: string; details?: Record<string, unknown> }>
}

interface ServiceHealth {
  name: string
  status: string
  url: string
  details?: ServiceDetail
}

interface HealthResponse {
  status: string
  timestamp: string
  services: ServiceHealth[]
}

const STATUS_CONFIG: Record<string, { label: string; icon: typeof CheckCircle; bg: string; text: string; border: string }> = {
  UP:      { label: 'Healthy',  icon: CheckCircle,  bg: 'var(--badge-success-bg)', text: 'var(--badge-success)', border: 'var(--badge-success-border)' },
  DOWN:    { label: 'Down',     icon: XCircle,      bg: 'var(--badge-danger-bg)',  text: 'var(--badge-danger)',  border: 'var(--badge-danger-border)'  },
  DEGRADED:{ label: 'Degraded', icon: AlertCircle,  bg: 'var(--badge-warning-bg)', text: 'var(--badge-warning)', border: 'var(--badge-warning-border)' },
}

function SubComponent({ name, status }: { name: string; status: string }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.DEGRADED
  const Icon = cfg.icon
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-border last:border-0">
      <span className="text-xs text-muted-foreground capitalize">{name.replace(/([A-Z])/g, ' $1').trim()}</span>
      <div className="flex items-center gap-1.5">
        <Icon size={12} strokeWidth={2} style={{ color: cfg.text }} />
        <span className="text-xs font-medium" style={{ color: cfg.text }}>{cfg.label}</span>
      </div>
    </div>
  )
}

export default function ProcessHealthPage() {
  const { status, data: session } = useSession()
  const token = (session?.accessToken as string) ?? ''

  const { data: health, isLoading, dataUpdatedAt, refetch, isFetching } = useQuery<HealthResponse>({
    queryKey: ['portalHealth'],
    queryFn: async () => {
      const res = await fetch('/api/health', { headers: { Authorization: `Bearer ${token}` } })
      if (!res.ok) throw new Error('Health check failed')
      return res.json()
    },
    enabled: status === 'authenticated',
    refetchInterval: 30_000,
    staleTime: 25_000,
  })

  const overallCfg = STATUS_CONFIG[health?.status ?? ''] ?? STATUS_CONFIG.DEGRADED
  const OverallIcon = overallCfg.icon
  const upCount = health?.services.filter(s => s.status === 'UP').length ?? 0
  const totalCount = health?.services.length ?? 0

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Process Health</h1>
          <p className="text-sm text-muted-foreground mt-0.5">Real-time service health across the Werkflow platform</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching} aria-label="Refresh health">
          <RefreshCw size={14} strokeWidth={1.8} className={`mr-1.5 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Overall status banner */}
      {isLoading ? (
        <Skeleton className="h-20 rounded-xl" />
      ) : health && (
        <div className="bg-card border border-border rounded-xl p-5 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div style={{ width: 48, height: 48, borderRadius: 12, background: overallCfg.bg, border: `1.5px solid ${overallCfg.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <OverallIcon size={22} style={{ color: overallCfg.text }} strokeWidth={2} />
            </div>
            <div>
              <p className="text-sm font-semibold text-muted-foreground uppercase tracking-wider text-xs mb-0.5">Overall Status</p>
              <StatusBadge status={health.status} />
            </div>
          </div>
          <div className="text-right">
            <p className="text-2xl font-bold text-foreground">{upCount} / {totalCount}</p>
            <p className="text-xs text-muted-foreground">services healthy</p>
          </div>
        </div>
      )}

      {/* Service cards */}
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-40 rounded-xl" />)}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {(health?.services ?? []).map((svc) => {
            const cfg = STATUS_CONFIG[svc.status] ?? STATUS_CONFIG.DEGRADED
            const Icon = cfg.icon
            const components = svc.details?.components ?? {}
            return (
              <div key={svc.name} className="bg-card border border-border rounded-xl p-5 flex flex-col gap-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-foreground capitalize">{svc.name}</p>
                    <p className="text-xs text-muted-foreground mt-0.5 truncate">{svc.url === 'self' ? 'Next.js Portal' : svc.url}</p>
                  </div>
                  <Icon size={18} strokeWidth={2} style={{ color: cfg.text, flexShrink: 0, marginTop: 2 }} />
                </div>
                <StatusBadge status={svc.status} />
                {Object.keys(components).length > 0 && (
                  <div className="mt-1">
                    {Object.entries(components).map(([name, comp]) => (
                      <SubComponent key={name} name={name} status={(comp as { status: string }).status} />
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Last updated */}
      {dataUpdatedAt > 0 && (
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Clock size={11} strokeWidth={1.8} />
          Last checked {new Date(dataUpdatedAt).toLocaleTimeString()} · auto-refreshes every 30s
        </div>
      )}
    </div>
  )
}
