'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { Search, RefreshCw, ClipboardList, Users, AlertTriangle, Star } from 'lucide-react'
import { StatCard } from '@/components/ui/stat-card'
import { FilterPills } from '@/components/ui/filter-pills'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { TaskList } from './components/TaskList'
import { useTasks, useClaimTask, useTaskSummary } from '@/lib/hooks/useTasks'
import { useAuth } from '@/lib/auth/auth-context'
import { useToast } from '@/hooks/use-toast'

const TAB_OPTIONS = [
  { key: 'all',        label: 'All' },
  { key: 'myTasks',    label: 'My Tasks' },
  { key: 'overdue',    label: 'Overdue' },
  { key: 'unassigned', label: 'Team Tasks' },
]

export default function TasksPage() {
  const t = useTranslations('tasks')
  const { toast } = useToast()
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [pageSize] = useState(20)
  const [searchText, setSearchText] = useState('')
  const [activeTab, setActiveTab] = useState('all')
  const [claimingTaskId, setClaimingTaskId] = useState<string | undefined>(undefined)

  const buildQueryParams = () => {
    const params: Record<string, unknown> = {
      start: page * pageSize,
      size: pageSize,
      sort: 'createTime',
      order: 'desc' as const,
      includeProcessVariables: false,
    }
    if (user) {
      if (activeTab === 'myTasks') params.assignee = user.username
      else if (activeTab === 'unassigned') params.unassigned = true
    }
    if (activeTab === 'overdue') params.dueBefore = new Date().toISOString()
    if (searchText.length > 2) params.nameLike = `%${searchText}%`
    return params
  }

  const { data: tasksData, isLoading, refetch } = useTasks(buildQueryParams())
  const { data: summary, isLoading: loadingSummary } = useTaskSummary()
  const claimTaskMutation = useClaimTask()

  const handleClaim = async (taskId: string) => {
    if (!user) return
    setClaimingTaskId(taskId)
    try {
      await claimTaskMutation.mutateAsync({ taskId, assignee: user.username })
      toast({ title: t('taskClaimed'), description: t('taskClaimedDesc') })
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : t('claimFailedDesc')
      toast({ title: t('claimFailed'), description: msg, variant: 'destructive' })
    } finally {
      setClaimingTaskId(undefined)
    }
  }

  const tabsWithCount = TAB_OPTIONS.map((opt) => ({
    ...opt,
    count: opt.key === 'myTasks' ? summary?.myTasks : opt.key === 'overdue' ? summary?.overdue : undefined,
  }))

  return (
    <div className="space-y-6 w-full">
      <div>
        <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>
        <p className="text-sm text-muted-foreground mt-0.5">{t('subtitle')}</p>
      </div>

      {/* Stat cards */}
      {loadingSummary ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard icon={ClipboardList} label={t('myTasks')}      value={summary?.myTasks ?? 0}      iconColor="#149ba5" />
          <StatCard icon={Users}         label={t('teamTasks')}    value={summary?.teamTasks ?? 0}    iconColor="#1d4ed8" />
          <StatCard icon={AlertTriangle} label={t('overdue')}      value={summary?.overdue ?? 0}      iconColor="#dc2626" />
          <StatCard icon={Star}          label={t('highPriority')} value={summary?.highPriority ?? 0} iconColor="#c27b00" />
        </div>
      )}

      {/* Tab filter + search */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center">
        <FilterPills
          options={tabsWithCount}
          active={activeTab}
          onChange={(k) => { setActiveTab(k); setPage(0) }}
        />
        <div className="flex gap-2 ml-auto">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder={t('searchPlaceholder')}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && refetch()}
              className="pl-8 w-56"
              aria-label={t('searchPlaceholder')}
            />
          </div>
          <Button variant="outline" size="icon" onClick={() => refetch()} disabled={isLoading} aria-label="Refresh tasks">
            <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
          </Button>
        </div>
      </div>

      {/* Task list */}
      <TaskList
        tasks={tasksData?.data || []}
        total={tasksData?.total || 0}
        page={page}
        pageSize={pageSize}
        onPageChange={setPage}
        onClaim={handleClaim}
        isLoading={isLoading}
        claimingTaskId={claimingTaskId}
      />
    </div>
  )
}
