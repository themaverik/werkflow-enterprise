'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Search, Bell, RefreshCw } from "lucide-react"
import { TaskList } from './components/TaskList'
import { TaskFilters } from './components/TaskFilters'
import { useTasks, useClaimTask, useTaskSummary } from '@/lib/hooks/useTasks'
import { mapGroupsToCandidateGroups } from '@/lib/utils/jwt'
import { useAuth } from '@/lib/auth/auth-context'
import { useToast } from "@/hooks/use-toast"
import type { TaskFilter } from '@/lib/types/task'

export default function TasksPage() {
  const t = useTranslations('tasks')
  const { toast } = useToast()
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [pageSize] = useState(20)
  const [searchText, setSearchText] = useState('')
  const [filters, setFilters] = useState<TaskFilter>({})
  const [claimingTaskId, setClaimingTaskId] = useState<string | undefined>(undefined)

  const buildQueryParams = () => {
    const params: any = {
      start: page * pageSize,
      size: pageSize,
      sort: 'createTime',
      order: 'desc' as const,
      includeProcessVariables: false,
    }

    if (user) {
      if (filters.myTasks) {
        params.assignee = user.username
      } else if (filters.teamTasks) {
        const candidateGroups = mapGroupsToCandidateGroups(user.groups || [])
        if (candidateGroups.length > 0) {
          params.candidateGroups = candidateGroups.join(',')
        }
      } else if (filters.unassigned) {
        params.unassigned = true
      } else {
        // Default: don't send candidateGroups/candidateUser params
        // Let the backend use JWT claims to return assigned + candidate group tasks
      }
    }

    if (searchText && searchText.length > 2) {
      params.nameLike = `%${searchText}%`
    }

    if (filters.priority !== undefined) {
      params.priority = filters.priority
    }

    if (filters.processDefinitionKey) {
      params.processDefinitionKey = filters.processDefinitionKey
    }

    if (filters.dueBefore) {
      params.dueBefore = filters.dueBefore
    }

    if (filters.dueAfter) {
      params.dueAfter = filters.dueAfter
    }

    return params
  }

  const { data: tasksData, isLoading: isLoadingTasks, refetch } = useTasks(buildQueryParams())
  const { data: summary, isLoading: isLoadingSummary } = useTaskSummary()
  const claimTaskMutation = useClaimTask()

  const handleClaim = async (taskId: string) => {
    if (!user) {
      toast({
        title: t('authRequired'),
        description: t('loginToClaim'),
        variant: 'destructive',
      })
      return
    }

    setClaimingTaskId(taskId)

    try {
      await claimTaskMutation.mutateAsync({
        taskId,
        assignee: user.username,
      })

      toast({
        title: t('taskClaimed'),
        description: t('taskClaimedDesc'),
      })
    } catch (error: any) {
      toast({
        title: t('claimFailed'),
        description: error.message || t('claimFailedDesc'),
        variant: 'destructive',
      })
    } finally {
      setClaimingTaskId(undefined)
    }
  }

  const handleFilterChange = (newFilters: TaskFilter) => {
    setFilters(newFilters)
    setPage(0)
  }

  const handleSearch = () => {
    setPage(0)
    refetch()
  }

  const handleRefresh = () => {
    refetch()
  }

  return (
    <div className="container py-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={handleRefresh} disabled={isLoadingTasks}>
            <RefreshCw className={`h-4 w-4 ${isLoadingTasks ? 'animate-spin' : ''}`} />
          </Button>
          <Button variant="outline" size="icon">
            <Bell className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {summary && !isLoadingSummary && (
        <div className="grid gap-4 md:grid-cols-4 mb-6">
          <Card>
            <CardContent className="pt-6">
              <div className="text-2xl font-bold">{summary.myTasks}</div>
              <p className="text-xs text-muted-foreground">{t('myTasks')}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-2xl font-bold">{summary.teamTasks}</div>
              <p className="text-xs text-muted-foreground">{t('teamTasks')}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-2xl font-bold">{summary.overdue}</div>
              <p className="text-xs text-muted-foreground">{t('overdue')}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-2xl font-bold">{summary.highPriority}</div>
              <p className="text-xs text-muted-foreground">{t('highPriority')}</p>
            </CardContent>
          </Card>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <div className="lg:col-span-1">
          <TaskFilters
            filters={filters}
            onFilterChange={handleFilterChange}
            userDepartment={user?.department}
          />
        </div>

        <div className="lg:col-span-3 space-y-4">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t('searchPlaceholder')}
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    handleSearch()
                  }
                }}
                className="pl-9"
              />
            </div>
            <Button onClick={handleSearch} disabled={isLoadingTasks}>
              {t('search')}
            </Button>
          </div>

          <TaskList
            tasks={tasksData?.data || []}
            total={tasksData?.total || 0}
            page={page}
            pageSize={pageSize}
            onPageChange={setPage}
            onClaim={handleClaim}
            isLoading={isLoadingTasks}
            claimingTaskId={claimingTaskId}
          />
        </div>
      </div>
    </div>
  )
}
