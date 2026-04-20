'use client'

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { Clock, CheckCircle2, Circle, User, Loader2 } from 'lucide-react'
import { useProcessHistory } from '@/lib/hooks/useTasks'
import type { TaskHistory } from '@/lib/types/task'

interface ProcessTimelineProps {
  processInstanceId: string | undefined
  taskHistory: TaskHistory[] | undefined
  isLoading: boolean
}

type ActivityStatus = 'completed' | 'active' | 'pending'

interface TimelineItem {
  id: string
  name: string
  status: ActivityStatus
  timestamp?: string
  endTime?: string
  startTime?: string
  assignee?: string
  comment?: string
}

function formatDuration(startTime?: string, endTime?: string): string | null {
  if (!startTime || !endTime) return null

  const start = new Date(startTime).getTime()
  const end = new Date(endTime).getTime()
  const diffMs = end - start

  if (diffMs < 0) return null

  const minutes = Math.floor(diffMs / 60000)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}d ${hours % 24}h`
  if (hours > 0) return `${hours}h ${minutes % 60}m`
  return `${minutes}m`
}

function formatTimestamp(ts?: string): string {
  if (!ts) return ''
  return new Date(ts).toLocaleString()
}

function StatusIcon({ status }: { status: ActivityStatus }) {
  if (status === 'completed') {
    return <CheckCircle2 className="h-5 w-5 text-green-600 flex-shrink-0" />
  }
  if (status === 'active') {
    return <Circle className="h-5 w-5 text-blue-600 flex-shrink-0" />
  }
  return <Circle className="h-5 w-5 text-muted-foreground flex-shrink-0" />
}

function StatusBadge({ status }: { status: ActivityStatus }) {
  const label =
    status === 'completed' ? 'Completed' : status === 'active' ? 'Active' : 'Pending'

  const className =
    status === 'completed'
      ? 'bg-green-100 text-green-800 border-green-200'
      : status === 'active'
      ? 'bg-blue-100 text-blue-800 border-blue-200'
      : 'bg-gray-100 text-gray-600 border-gray-200'

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${className}`}
    >
      {label}
    </span>
  )
}

function mapProcessHistoryToTimeline(rawHistory: any[]): TimelineItem[] {
  if (!Array.isArray(rawHistory)) return []

  return rawHistory.map((item: any, index: number) => {
    const status: ActivityStatus = item.endTime
      ? 'completed'
      : item.startTime
      ? 'active'
      : 'pending'

    return {
      id: item.id || item.activityId || `activity-${index}`,
      name: item.activityName || item.name || item.activityId || 'Unknown Activity',
      status,
      timestamp: item.endTime || item.startTime || item.timestamp,
      startTime: item.startTime,
      endTime: item.endTime,
      assignee: item.assignee || item.userId,
      comment: item.comment,
    }
  })
}

function mapTaskHistoryToTimeline(history: TaskHistory[]): TimelineItem[] {
  return history.map((item) => ({
    id: item.id,
    name: item.action.charAt(0).toUpperCase() + item.action.slice(1),
    status: 'completed' as ActivityStatus,
    timestamp: item.timestamp,
    assignee: item.userName || item.userId,
    comment: item.comment,
  }))
}

function TimelineList({ items }: { items: TimelineItem[] }) {
  if (items.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        No history available for this task.
      </div>
    )
  }

  return (
    <div className="relative">
      {items.map((item, index) => {
        const isLast = index === items.length - 1
        const duration = formatDuration(item.startTime, item.endTime)

        return (
          <div key={item.id} className="flex gap-4">
            {/* Timeline rail */}
            <div className="flex flex-col items-center">
              <StatusIcon status={item.status} />
              {!isLast && <div className="w-px flex-1 bg-border mt-1 mb-1" />}
            </div>

            {/* Content */}
            <div className={`pb-6 flex-1 min-w-0 ${isLast ? '' : ''}`}>
              <div className="flex flex-wrap items-center gap-2 mb-1">
                <span className="font-semibold text-sm">{item.name}</span>
                <StatusBadge status={item.status} />
              </div>

              <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                {item.timestamp && (
                  <span className="flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {formatTimestamp(item.timestamp)}
                  </span>
                )}
                {item.assignee && (
                  <span className="flex items-center gap-1">
                    <User className="h-3 w-3" />
                    {item.assignee}
                  </span>
                )}
                {duration && (
                  <span className="text-muted-foreground">Duration: {duration}</span>
                )}
              </div>

              {item.comment && (
                <p className="mt-1 text-sm text-muted-foreground">{item.comment}</p>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}

export function ProcessTimeline({
  processInstanceId,
  taskHistory,
  isLoading,
}: ProcessTimelineProps) {
  const {
    data: processHistory,
    isLoading: isLoadingProcessHistory,
    isError: isProcessHistoryError,
  } = useProcessHistory(processInstanceId)

  const items: TimelineItem[] = (() => {
    if (!isProcessHistoryError && processHistory) {
      const mapped = mapProcessHistoryToTimeline(
        Array.isArray(processHistory) ? processHistory : processHistory.data || []
      )
      if (mapped.length > 0) return mapped
    }

    if (taskHistory && taskHistory.length > 0) {
      return mapTaskHistoryToTimeline(taskHistory)
    }

    return []
  })()

  const showLoading = isLoading || isLoadingProcessHistory

  return (
    <Card>
      <CardHeader>
        <CardTitle>Process Timeline</CardTitle>
        <CardDescription>Timeline of actions performed on this task</CardDescription>
      </CardHeader>
      <CardContent>
        {showLoading && (
          <div className="flex items-center justify-center py-8 text-muted-foreground gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>Loading history...</span>
          </div>
        )}

        {!showLoading && <TimelineList items={items} />}
      </CardContent>
    </Card>
  )
}
