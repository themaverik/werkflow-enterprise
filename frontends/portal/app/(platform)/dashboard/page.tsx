'use client'

import Link from 'next/link'
import { useSession } from 'next-auth/react'
import { useQuery } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { ClipboardList, Users, AlertTriangle, TrendingUp, CheckCircle, Play, XCircle, Rocket } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useTaskSummary } from '@/lib/hooks/useTasks'
import { getActivityLogs, type ActivityLogEntry } from '@/lib/api/workflows'

// ----------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)

  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins}m ago`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}d ago`
}

function ActivityIcon({ type }: { type: ActivityLogEntry['type'] }) {
  switch (type) {
    case 'completed':
      return <CheckCircle className="h-4 w-4 text-green-500 shrink-0" />
    case 'started':
      return <Play className="h-4 w-4 text-blue-500 shrink-0" />
    case 'failed':
      return <XCircle className="h-4 w-4 text-red-500 shrink-0" />
    case 'deployed':
      return <Rocket className="h-4 w-4 text-purple-500 shrink-0" />
    default:
      return <CheckCircle className="h-4 w-4 text-muted-foreground shrink-0" />
  }
}

// ----------------------------------------------------------------
// Sub-components
// ----------------------------------------------------------------

function StatCardSkeleton() {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-10 w-10 rounded-md" />
          <div className="space-y-2">
            <Skeleton className="h-7 w-12" />
            <Skeleton className="h-3 w-20" />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

interface StatCardProps {
  icon: React.ReactNode
  label: string
  value: number
  valueClassName?: string
}

function StatCard({ icon, label, value, valueClassName }: StatCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center gap-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-md bg-muted">
            {icon}
          </div>
          <div>
            <div className={`text-2xl font-bold ${valueClassName ?? ''}`}>{value}</div>
            <p className="text-xs text-muted-foreground">{label}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function ActivitySkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-start gap-3">
          <Skeleton className="h-4 w-4 rounded-full mt-0.5 shrink-0" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-24" />
          </div>
        </div>
      ))}
    </div>
  )
}

// ----------------------------------------------------------------
// Page
// ----------------------------------------------------------------

export default function DashboardPage() {
  const t = useTranslations('dashboard')
  const { status } = useSession()
  const { data: summary, isLoading: isLoadingSummary, isError: isSummaryError } = useTaskSummary()

  const { data: activityLogs, isLoading: isLoadingActivity, isError: isActivityError } = useQuery({
    queryKey: ['dashboard-activity'],
    queryFn: () => getActivityLogs(5),
    enabled: status === 'authenticated',
    staleTime: 30000,
  })

  return (
    <div className="container py-6 space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold">{t('title')}</h1>
        <p className="text-muted-foreground">{t('subtitle')}</p>
      </div>

      {/* Stat Cards */}
      <section aria-label="Task summary">
        {isSummaryError ? (
          <Card>
            <CardContent className="py-8 text-center text-sm text-muted-foreground">
              {t('errorLoadingTasks')}
            </CardContent>
          </Card>
        ) : isLoadingSummary ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              icon={<ClipboardList className="h-5 w-5 text-muted-foreground" />}
              label={t('myTasks')}
              value={summary?.myTasks ?? 0}
            />
            <StatCard
              icon={<Users className="h-5 w-5 text-muted-foreground" />}
              label={t('teamTasks')}
              value={summary?.teamTasks ?? 0}
            />
            <StatCard
              icon={<AlertTriangle className="h-5 w-5 text-muted-foreground" />}
              label={t('overdue')}
              value={summary?.overdue ?? 0}
              valueClassName={(summary?.overdue ?? 0) > 0 ? 'text-red-500' : undefined}
            />
            <StatCard
              icon={<TrendingUp className="h-5 w-5 text-muted-foreground" />}
              label={t('highPriority')}
              value={summary?.highPriority ?? 0}
            />
          </div>
        )}
      </section>

      {/* Quick Actions */}
      <section aria-label="Quick actions">
        <h2 className="text-lg font-semibold mb-3">{t('quickActions')}</h2>
        <div className="flex flex-wrap gap-3">
          <Button variant="outline" asChild>
            <Link href="/tasks">{t('viewMyTasks')}</Link>
          </Button>
          <Button variant="outline" asChild>
            <Link href="/requests">{t('myRequests')}</Link>
          </Button>
          <Button variant="outline" asChild>
            <Link href="/processes">{t('startNewProcess')}</Link>
          </Button>
        </div>
      </section>

      {/* Recent Activity */}
      <section aria-label="Recent activity">
        <h2 className="text-lg font-semibold mb-3">{t('recentActivity')}</h2>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {t('last5Events')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {isActivityError ? (
              <p className="text-sm text-muted-foreground">{t('errorLoadingActivity')}</p>
            ) : isLoadingActivity ? (
              <ActivitySkeleton />
            ) : !activityLogs || activityLogs.length === 0 ? (
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
          </CardContent>
        </Card>
      </section>
    </div>
  )
}
