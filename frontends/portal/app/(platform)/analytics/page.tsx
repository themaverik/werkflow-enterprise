'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { StatCard } from '@/components/ui/stat-card'
import { Activity, CheckCircle, AlertTriangle, TrendingUp, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  LineChart, Line, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from 'recharts'

interface ProcessStats {
  totalCount: number
  successCount: number
  failureCount: number
  avgDurationMs: number
  successRate: number
  executionsOverTime: Array<{ date: string; count: number }>
}

interface TaskMetrics {
  avgCycleTimeMs: number
  bottleneckStepId: string
  slaCompliancePct: number
  overdueCount: number
  escalationCount: number
  bottlenecks: Array<{ stepName: string; avgMs: number }>
}

async function fetchProcessStats(token: string): Promise<ProcessStats> {
  const res = await fetch('/api/proxy/engine/analytics/process-stats', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch process stats')
  return res.json() as Promise<ProcessStats>
}

async function fetchTaskMetrics(token: string): Promise<TaskMetrics> {
  const res = await fetch('/api/proxy/engine/analytics/task-metrics', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch task metrics')
  return res.json() as Promise<TaskMetrics>
}

export default function AnalyticsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''

  const { data: procStats, isLoading: loadingProc } = useQuery({
    queryKey: ['analyticsProcessStats'],
    queryFn: () => fetchProcessStats(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: taskMetrics, isLoading: loadingTask } = useQuery({
    queryKey: ['analyticsTaskMetrics'],
    queryFn: () => fetchTaskMetrics(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive p-6">Access denied.</p>
  }

  const isLoading = loadingProc || loadingTask
  const executions = procStats?.executionsOverTime ?? []
  const bottlenecks = taskMetrics?.bottlenecks ?? []

  const handleExportCsv = () => {
    if (!executions.length) return
    const rows = [['Date', 'Count'], ...executions.map((e) => [e.date, String(e.count)])]
    const csv = rows.map((r) => r.join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'analytics.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-8 max-w-6xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Analytics Dashboard</h1>
          <p className="text-sm text-muted-foreground mt-0.5">Process execution metrics and SLA compliance</p>
        </div>
        <Button variant="outline" size="sm" onClick={handleExportCsv} aria-label="Export CSV">
          <Download size={14} strokeWidth={1.8} className="mr-1.5" /> Export CSV
        </Button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard icon={Activity}      label="Total Executions" value={procStats?.totalCount ?? 0}                            iconColor="#7c3aed" />
          <StatCard icon={CheckCircle}   label="Success Rate"     value={`${(procStats?.successRate ?? 0).toFixed(1)}%`}        iconColor="#16a34a" />
          <StatCard icon={AlertTriangle} label="Overdue Tasks"    value={taskMetrics?.overdueCount ?? 0}                        iconColor="#dc2626" />
          <StatCard icon={TrendingUp}    label="SLA Compliance"   value={`${(taskMetrics?.slaCompliancePct ?? 0).toFixed(1)}%`} iconColor="#1d4ed8" />
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="bg-card border border-border rounded-xl p-5">
          <h2 className="text-base font-semibold mb-4">Process Executions Over Time</h2>
          {isLoading ? <Skeleton className="h-48" /> : (
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={executions}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(207 26% 88%)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line type="monotone" dataKey="count" stroke="#7c3aed" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="bg-card border border-border rounded-xl p-5">
          <h2 className="text-base font-semibold mb-4">Task Bottlenecks (Avg Cycle Time)</h2>
          {isLoading ? <Skeleton className="h-48" /> : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={bottlenecks}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(207 26% 88%)" />
                <XAxis dataKey="stepName" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 11 }} unit="ms" />
                <Tooltip formatter={(v) => [`${((Number(v)) / 1000 / 60).toFixed(1)} min`, 'Avg Time']} />
                <Bar dataKey="avgMs" fill="#1d4ed8" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <div className="bg-card border border-border rounded-xl p-5">
        <h2 className="text-base font-semibold mb-4">SLA Summary</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            { label: 'Overdue Tasks',    value: taskMetrics?.overdueCount ?? 0,                        color: '#dc2626' },
            { label: 'Escalations',      value: taskMetrics?.escalationCount ?? 0,                     color: '#c27b00' },
            { label: 'SLA Compliance %', value: `${(taskMetrics?.slaCompliancePct ?? 0).toFixed(1)}%`, color: '#16a34a' },
          ].map(({ label, value, color }) => (
            <div key={label} className="p-4 rounded-xl border border-border">
              <p className="text-xs text-muted-foreground mb-1">{label}</p>
              <p className="text-2xl font-bold" style={{ color }}>{value}</p>
            </div>
          ))}
        </div>
      </div>

      {bottlenecks.length > 0 && (
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="text-base font-semibold">Bottleneck Detail</h2>
          </div>
          <table className="w-full text-sm" aria-label="Bottleneck detail">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Step</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Avg Duration</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {bottlenecks.map((b) => (
                <tr key={b.stepName} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs">{b.stepName}</td>
                  <td className="px-4 py-3 font-semibold">{(b.avgMs / 1000 / 60).toFixed(1)} min</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
