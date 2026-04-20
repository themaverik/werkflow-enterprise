'use client'

import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { ArrowLeft } from 'lucide-react'
import { getProcessInstance, getTasksByProcessInstance } from '@/lib/api/workflows'
import { ProcessTimeline } from '../../tasks/components/ProcessTimeline'
import { formatDate } from '@/lib/utils/format'
import type { TaskHistory } from '@/lib/types/task'

function StatusBadge({ ended, suspended }: { ended: boolean; suspended: boolean }) {
  if (ended) {
    return <Badge className="bg-green-100 text-green-800 border-green-200 hover:bg-green-100">Completed</Badge>
  }
  if (suspended) {
    return <Badge className="bg-yellow-100 text-yellow-800 border-yellow-200 hover:bg-yellow-100">Suspended</Badge>
  }
  return <Badge className="bg-blue-100 text-blue-800 border-blue-200 hover:bg-blue-100">Active</Badge>
}

const HIDDEN_VARIABLE_KEYS = new Set([
  'categoryId', 'assetDefinitionId', 'processDefinitionId',
])

function formatVariableKey(key: string): string {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (str) => str.toUpperCase())
    .trim()
}

function VariablesTable({ variables }: { variables: Record<string, unknown> }) {
  const entries = Object.entries(variables).filter(([key]) => !HIDDEN_VARIABLE_KEYS.has(key))

  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">No variables.</p>
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Variable</TableHead>
            <TableHead>Value</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map(([key, value]) => (
            <TableRow key={key}>
              <TableCell className="text-sm font-medium">{formatVariableKey(key)}</TableCell>
              <TableCell className="text-sm text-muted-foreground">
                {typeof value === 'object' ? JSON.stringify(value) : String(value ?? '-')}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

export default function RequestDetailPage() {
  const { status } = useSession()
  const params = useParams()
  const processInstanceId = params.id as string

  const { data: instance, isLoading: isLoadingInstance } = useQuery({
    queryKey: ['process-instance', processInstanceId],
    queryFn: () => getProcessInstance(processInstanceId),
    enabled: status === 'authenticated' && !!processInstanceId,
  })

  const { data: tasks, isLoading: isLoadingTasks } = useQuery({
    queryKey: ['process-tasks', processInstanceId],
    queryFn: () => getTasksByProcessInstance(processInstanceId),
    enabled: status === 'authenticated' && !!processInstanceId,
  })

  if (isLoadingInstance) {
    return (
      <div className="container py-6 space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (!instance) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <h3 className="text-lg font-semibold mb-2">Request Not Found</h3>
            <p className="text-muted-foreground mb-4">
              The request you are looking for does not exist or has been deleted.
            </p>
            <Button asChild>
              <Link href="/requests">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Requests
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="container py-6">
      <div className="mb-6">
        <Button variant="ghost" asChild className="mb-4">
          <Link href="/requests">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Requests
          </Link>
        </Button>

        <div className="flex items-center gap-3">
          <h1 className="text-3xl font-bold">
            {instance.businessKey ?? instance.processInstanceId}
          </h1>
          <StatusBadge ended={instance.ended} suspended={instance.suspended} />
        </div>
        <p className="text-muted-foreground mt-1 font-mono text-sm">
          Instance ID: {instance.processInstanceId}
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          {/* Details */}
          <Card>
            <CardHeader>
              <CardTitle>Request Details</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-muted-foreground mb-0.5">Process Definition</dt>
                  <dd className="font-medium font-mono">{instance.processDefinitionKey}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground mb-0.5">Business Key</dt>
                  <dd className="font-medium">{instance.businessKey ?? '-'}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground mb-0.5">Started</dt>
                  <dd className="font-medium">{formatDate(instance.startTime)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground mb-0.5">Ended</dt>
                  <dd className="font-medium">{formatDate(instance.endTime)}</dd>
                </div>
              </dl>
            </CardContent>
          </Card>

          {/* Variables */}
          <Card>
            <CardHeader>
              <CardTitle>Process Variables</CardTitle>
            </CardHeader>
            <CardContent>
              {instance.variables && Object.keys(instance.variables).length > 0 ? (
                <VariablesTable variables={instance.variables} />
              ) : (
                <p className="text-sm text-muted-foreground">No variables available.</p>
              )}
            </CardContent>
          </Card>

          {/* Timeline */}
          <ProcessTimeline
            processInstanceId={processInstanceId}
            taskHistory={tasks?.map((t): TaskHistory => ({
              id: t.taskId,
              taskId: t.taskId,
              action: t.assignee ? 'claim' : 'create',
              userId: t.assignee ?? '',
              userName: t.assignee,
              timestamp: t.createTime,
            }))}
            isLoading={isLoadingTasks}
          />
        </div>

        {/* Tasks sidebar */}
        <div className="lg:col-span-1">
          <Card>
            <CardHeader>
              <CardTitle>Current Tasks</CardTitle>
            </CardHeader>
            <CardContent>
              {isLoadingTasks ? (
                <div className="space-y-2">
                  <Skeleton className="h-10 w-full" />
                  <Skeleton className="h-10 w-full" />
                </div>
              ) : !tasks || tasks.length === 0 ? (
                <p className="text-sm text-muted-foreground">No active tasks.</p>
              ) : (
                <ul className="space-y-2">
                  {tasks.map((task) => (
                    <li key={task.taskId}>
                      <Link
                        href={`/tasks/${task.taskId}`}
                        className="block rounded-md border p-3 text-sm hover:bg-muted transition-colors"
                      >
                        <p className="font-medium">{task.taskName}</p>
                        {task.assignee && (
                          <p className="text-xs text-muted-foreground mt-0.5">
                            Assigned to: {task.assignee}
                          </p>
                        )}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
