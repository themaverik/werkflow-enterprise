'use client'

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Clock, User, Calendar, AlertCircle, CheckCircle2 } from "lucide-react"
import Link from "next/link"
import type { Task } from "@/lib/types/task"

interface TaskCardProps {
  task: Task
  onClaim?: (taskId: string) => void
  onView?: (taskId: string) => void
  showClaimButton?: boolean
  isLoading?: boolean
}

export function TaskCard({ task, onClaim, onView, showClaimButton = true, isLoading = false }: TaskCardProps) {
  const isAssigned = !!task.assignee
  const isOverdue = task.dueDate && new Date(task.dueDate) < new Date()

  const priorityColor = task.priority
    ? task.priority >= 100
      ? 'destructive'
      : task.priority >= 75
      ? 'default'
      : task.priority >= 50
      ? 'secondary'
      : 'outline'
    : 'outline'

  const priorityLabel = task.priority
    ? task.priority >= 100
      ? 'Urgent'
      : task.priority >= 75
      ? 'High'
      : task.priority >= 50
      ? 'Medium'
      : 'Low'
    : 'Normal'

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    const now = new Date()
    const diffMs = date.getTime() - now.getTime()
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24))

    if (diffDays === 0) {
      return 'Today'
    } else if (diffDays === 1) {
      return 'Tomorrow'
    } else if (diffDays === -1) {
      return 'Yesterday'
    } else if (diffDays < -1) {
      return `${Math.abs(diffDays)} days ago`
    } else if (diffDays > 1) {
      return `in ${diffDays} days`
    }

    return date.toLocaleDateString()
  }

  return (
    <Card className={`hover:shadow-md transition-shadow ${isOverdue ? 'border-destructive' : ''}`}>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <CardTitle
              className="text-base truncate"
              title={task.name}
            >
              {task.name}
            </CardTitle>
            <CardDescription className="text-xs mt-1 truncate" title={task.processDefinitionName || task.processDefinitionKey || 'Unknown Process'}>
              {task.processDefinitionName || task.processDefinitionKey || 'Unknown Process'}
            </CardDescription>
          </div>
          <Badge variant={priorityColor} className="shrink-0">
            {priorityLabel}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="space-y-2 text-sm">
          {task.assignee && (
            <div className="flex items-center gap-2 text-muted-foreground">
              <User className="h-4 w-4" />
              <span className="truncate">{task.assignee}</span>
            </div>
          )}

          {!task.assignee && (
            <div className="flex items-center gap-2 text-muted-foreground">
              <User className="h-4 w-4" />
              <span className="italic">Unassigned</span>
            </div>
          )}

          <div className="flex items-center gap-2 text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span>Created {formatDate(task.createTime)}</span>
          </div>

          {task.dueDate && (
            <div className={`flex items-center gap-2 ${isOverdue ? 'text-destructive' : 'text-muted-foreground'}`}>
              {isOverdue ? (
                <AlertCircle className="h-4 w-4" />
              ) : (
                <Calendar className="h-4 w-4" />
              )}
              <span>Due {formatDate(task.dueDate)}</span>
            </div>
          )}
        </div>

        {task.description && (
          <p className="text-sm text-muted-foreground line-clamp-2">
            {task.description}
          </p>
        )}

        <div className="flex gap-2 pt-2">
          <Button
            asChild
            variant="outline"
            size="sm"
            className="flex-1"
            onClick={() => onView?.(task.id)}
          >
            <Link href={`/tasks/${task.id}`}>
              View Details
            </Link>
          </Button>

          {showClaimButton && !isAssigned && (
            <Button
              variant="default"
              size="sm"
              className="flex-1"
              onClick={() => onClaim?.(task.id)}
              disabled={isLoading}
            >
              <CheckCircle2 className="h-4 w-4 mr-1" />
              {isLoading ? 'Claiming...' : 'Claim'}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
