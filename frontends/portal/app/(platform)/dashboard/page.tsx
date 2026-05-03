'use client'

import Link from 'next/link'
import { useSession } from 'next-auth/react'
import { useQuery } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import {
  ClipboardList, Users, AlertTriangle, TrendingUp,
  CheckCircle, Play, XCircle, Rocket, ArrowRight,
} from 'lucide-react'
import { StatCard } from '@/components/ui/stat-card'
import { Skeleton } from '@/components/ui/skeleton'
import { useTaskSummary } from '@/lib/hooks/useTasks'
import { getActivityLogs, type ActivityLogEntry } from '@/lib/api/workflows'

function formatTimestamp(timestamp: string): string {
  const diffMs = Date.now() - new Date(timestamp).getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

function ActivityIcon({ type }: { type: ActivityLogEntry['type'] }) {
  const props = { size: 16, strokeWidth: 1.8 }
  switch (type) {
    case 'completed': return <CheckCircle {...props} className="text-green-500 shrink-0" />
    case 'started':   return <Play {...props} className="text-blue-500 shrink-0" />
    case 'failed':    return <XCircle {...props} className="text-red-500 shrink-0" />
    case 'deployed':  return <Rocket {...props} className="text-purple-500 shrink-0" />
    default:          return <CheckCircle {...props} className="text-muted-foreground shrink-0" />
  }
}

export default function DashboardPage() {
  const t = useTranslations('dashboard')
  const { status } = useSession()
  const { data: summary, isLoading: loadingSummary } = useTaskSummary()
  const { data: activityLogs, isLoading: loadingActivity } = useQuery({
    queryKey: ['dashboard-activity'],
    queryFn: () => getActivityLogs(5),
    enabled: status === 'authenticated',
    staleTime: 30000,
  })

  return (
    <div className="space-y-8 w-full">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>
        <p className="text-sm text-muted-foreground mt-0.5">{t('subtitle')}</p>
      </div>

      {/* Stat cards */}
      <section aria-label="Task summary">
        {loadingSummary ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-24 rounded-xl" />
            ))}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard icon={ClipboardList} label={t('myTasks')}      value={summary?.myTasks ?? 0}      iconColor="#149ba5" />
            <StatCard icon={Users}         label={t('teamTasks')}    value={summary?.teamTasks ?? 0}    iconColor="#1d4ed8" />
            <StatCard icon={AlertTriangle} label={t('overdue')}      value={summary?.overdue ?? 0}      iconColor="#dc2626" />
            <StatCard icon={TrendingUp}    label={t('highPriority')} value={summary?.highPriority ?? 0} iconColor="#c27b00" />
          </div>
        )}
      </section>

      {/* Quick actions */}
      <section aria-label="Quick actions">
        <h2 className="text-base font-semibold mb-3">{t('quickActions')}</h2>
        <div className="flex flex-wrap gap-2">
          {[
            { label: t('viewMyTasks'),     href: '/tasks' },
            { label: t('myRequests'),      href: '/requests' },
            { label: t('startNewProcess'), href: '/services' },
          ].map(({ label, href }) => (
            <Link
              key={href}
              href={href}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg border border-border bg-card text-sm font-medium hover:bg-muted transition-colors"
            >
              {label}
              <ArrowRight size={14} className="text-muted-foreground" />
            </Link>
          ))}
        </div>
      </section>

      {/* Recent activity */}
      <section aria-label="Recent activity">
        <h2 className="text-base font-semibold mb-3">{t('recentActivity')}</h2>
        <div className="bg-card border border-border rounded-xl p-5">
          {loadingActivity ? (
            <div className="space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-10 rounded" />
              ))}
            </div>
          ) : !activityLogs?.length ? (
            <p className="text-sm text-muted-foreground">{t('noRecentActivity')}</p>
          ) : (
            <ul className="space-y-4" role="list">
              {activityLogs.map((entry) => (
                <li key={entry.id} className="flex items-start gap-3">
                  <ActivityIcon type={entry.type} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm leading-snug">{entry.message}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {entry.user} &middot; {formatTimestamp(entry.timestamp)}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  )
}
