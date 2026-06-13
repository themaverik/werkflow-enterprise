'use client'

import { useParams, useRouter } from 'next/navigation'
import { useState, useMemo } from 'react'
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ArrowLeft, CheckCircle2, XCircle, UserPlus, Clock, User as UserIcon, Calendar } from "lucide-react"
import Link from "next/link"
import { Skeleton } from "@/components/ui/skeleton"
import { useTask, useTaskFormData, useTaskHistory, useCompleteTask, useClaimTask, useUnclaimTask, useDelegateTask, useSubmitTaskForm } from '@/lib/hooks/useTasks'
import { useAuth } from '@/lib/auth/auth-context'
import { toast } from 'sonner'
import { ApprovalPanel } from '../components/ApprovalPanel'
import { DelegationModal } from '../components/DelegationModal'
import { FormSection } from '../components/FormSection'
import { ProcessTimeline } from '../components/ProcessTimeline'
import { formatDateTime } from '@/lib/utils/format'

export default function TaskDetailPage() {
  const params = useParams()
  const router = useRouter()
  const { user } = useAuth()
  const taskId = params.id as string

  const [showDelegationModal, setShowDelegationModal] = useState(false)
  const [activeTab, setActiveTab] = useState('details')

  const { data: task, isLoading: isLoadingTask } = useTask(taskId)
  const { data: formData, isLoading: isLoadingForm } = useTaskFormData(taskId)
  const { data: history, isLoading: isLoadingHistory } = useTaskHistory(taskId)

  const completeTaskMutation = useCompleteTask()
  const claimTaskMutation = useClaimTask()
  const unclaimTaskMutation = useUnclaimTask()
  const delegateTaskMutation = useDelegateTask()
  const submitFormMutation = useSubmitTaskForm()

  // Must be called before any early returns to satisfy Rules of Hooks
  const isApproval = useMemo(() => {
    if (!task) return false
    const nameLower = (task.name || '').toLowerCase()
    const keyLower = (task.taskDefinitionKey || '').toLowerCase()
    return (
      nameLower.includes('approv') ||
      keyLower.includes('approv') ||
      task.processVariables?.approvalLevel !== undefined
    )
  }, [task])

  const handleClaim = async () => {
    if (!user) {
      toast.error('Authentication Required', { description: 'Please log in to claim tasks.' })
      return
    }

    try {
      await claimTaskMutation.mutateAsync({
        taskId,
        assignee: user.username,
      })

      toast.success('Task Claimed', { description: 'You have successfully claimed this task.' })
    } catch (error: any) {
      toast.error('Failed to Claim Task', { description: error.message || 'An error occurred while claiming the task.' })
    }
  }

  const handleComplete = async () => {
    try {
      await completeTaskMutation.mutateAsync({
        taskId,
        data: {},
      })

      toast.success('Task Completed', { description: 'The task has been completed successfully.' })

      router.push('/tasks')
    } catch (error: any) {
      toast.error('Failed to Complete Task', { description: error.message || 'An error occurred while completing the task.' })
    }
  }

  const handleApprove = async (comment: string) => {
    try {
      await completeTaskMutation.mutateAsync({
        taskId,
        data: {
          variables: {
            decision: 'approve',
            approved: true,
            approvalComment: comment,
            approvedBy: user?.username,
            approvalTimestamp: new Date().toISOString(),
          },
        },
      })

      toast.success('Request Approved', { description: 'The request has been approved successfully.' })

      router.push('/tasks')
    } catch (error: any) {
      toast.error('Approval Failed', { description: error.message || 'An error occurred while approving the request.' })
    }
  }

  const handleReject = async (comment: string) => {
    try {
      await completeTaskMutation.mutateAsync({
        taskId,
        data: {
          variables: {
            decision: 'reject',
            approved: false,
            rejectionReason: comment,
            rejectedBy: user?.username,
            rejectionTimestamp: new Date().toISOString(),
          },
        },
      })

      toast.success('Request Rejected', { description: 'The request has been rejected.' })

      router.push('/tasks')
    } catch (error: any) {
      toast.error('Rejection Failed', { description: error.message || 'An error occurred while rejecting the request.' })
    }
  }

  const handleEscalate = async (comment: string) => {
    try {
      await completeTaskMutation.mutateAsync({
        taskId,
        data: {
          variables: {
            decision: 'escalate',
            escalationComment: comment,
            escalatedBy: user?.username,
            escalationTimestamp: new Date().toISOString(),
          },
        },
      })

      toast.success('Escalated', { description: 'The request has been escalated to a higher authority.' })

      router.push('/tasks')
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'An error occurred while escalating the request.'
      toast.error('Escalation Failed', { description: message })
    }
  }

  const handleDelegateTask = async (assignee: string, reason: string) => {
    try {
      await delegateTaskMutation.mutateAsync({
        taskId,
        data: {
          assignee,
          reason,
        },
      })

      toast.success('Task Delegated', { description: 'The task has been delegated successfully.' })

      setShowDelegationModal(false)
      router.push('/tasks')
    } catch (error: any) {
      toast.error('Delegation Failed', { description: error.message || 'An error occurred while delegating the task.' })
    }
  }

  const handleUnclaim = async () => {
    try {
      await unclaimTaskMutation.mutateAsync(taskId)
      toast.success('Task Unclaimed', { description: 'Task has been returned to the queue.' })
    } catch (error: any) {
      toast.error('Failed to Unclaim', { description: error.message || 'An error occurred.' })
    }
  }

  const handleFormSubmit = async (data: Record<string, any>) => {
    try {
      await submitFormMutation.mutateAsync({
        taskId,
        formData: data,
      })
      toast.success('Task Completed', { description: 'Form submitted successfully.' })
      router.push('/tasks')
    } catch (error: any) {
      toast.error('Submission Failed', { description: error.message || 'Failed to submit form.' })
    }
  }

  if (isLoadingTask) {
    return (
      <div className="container py-6 space-y-4">
        <Skeleton className="h-8 w-1/3 rounded" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    )
  }

  if (!task) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <h3 className="text-lg font-semibold mb-2">Task Not Found</h3>
            <p className="text-muted-foreground mb-4">
              The task you are looking for does not exist or has been deleted.
            </p>
            <Button asChild>
              <Link href="/tasks">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Tasks
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  const isAssignedToUser = task.assignee === user?.username
  const canClaim = !task.assignee && user
  const canComplete = isAssignedToUser
  const hasForm = formData?.formData && typeof formData.formData === 'object' && Array.isArray(formData.formData.components)

  const assigneeDisplay = task.assignee
    ? isAssignedToUser
      ? [user?.firstName, user?.lastName].filter(Boolean).join(' ') || task.assignee
      : task.assignee
    : 'Unassigned'

  const FILTERED_VAR_PATTERN = /[Uu]rl$|_url$|[Tt]oken$|[Aa]uthorization/
  function formatVarLabel(key: string): string {
    return key
      .replace(/_/g, ' ')
      .replace(/([A-Z])/g, ' $1')
      .replace(/^\s+/, '')
      .replace(/\b\w/g, (c) => c.toUpperCase())
      .trim()
  }
  const displayVars = Object.entries(task.processVariables || {}).filter(
    ([key]) => !FILTERED_VAR_PATTERN.test(key)
  )

  const priorityColor = task.priority
    ? task.priority >= 75
      ? 'destructive'
      : task.priority >= 50
      ? 'default'
      : 'secondary'
    : 'secondary'

  const priorityLabel = task.priority
    ? task.priority >= 75
      ? 'Urgent'
      : task.priority >= 50
      ? 'High'
      : task.priority >= 25
      ? 'Medium'
      : 'Low'
    : 'Normal'

  return (
    <div className="container py-6">
      <div className="mb-6">
        <Button variant="ghost" asChild className="mb-4">
          <Link href="/tasks">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Tasks
          </Link>
        </Button>

        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold mb-2">{task.name}</h1>
            <p className="text-muted-foreground">
              {task.processDefinitionName || task.processDefinitionKey}
            </p>
          </div>
          <Badge variant={priorityColor}>{priorityLabel}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {isApproval && user ? (
            <ApprovalPanel
              task={task}
              user={user}
              onApprove={handleApprove}
              onReject={handleReject}
              onEscalate={handleEscalate}
              isSubmitting={completeTaskMutation.isPending}
            />
          ) : (
            <Tabs defaultValue="details" className="w-full" onValueChange={setActiveTab}>
            <TabsList>
              <TabsTrigger value="details">Details</TabsTrigger>
              <TabsTrigger value="form">Form</TabsTrigger>
              <TabsTrigger value="history">History</TabsTrigger>
            </TabsList>

            <TabsContent value="details" className="mt-4">
              <Card>
                <CardHeader>
                  <CardTitle>Task Information</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {task.description && (
                    <div>
                      <h4 className="font-semibold mb-2">Description</h4>
                      <p className="text-sm text-muted-foreground">{task.description}</p>
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <h4 className="font-semibold mb-2 flex items-center gap-2">
                        <UserIcon className="h-4 w-4" />
                        Assignee
                      </h4>
                      <p className="text-sm text-muted-foreground">
                        {assigneeDisplay}
                      </p>
                    </div>

                    <div>
                      <h4 className="font-semibold mb-2 flex items-center gap-2">
                        <Clock className="h-4 w-4" />
                        Created
                      </h4>
                      <p className="text-sm text-muted-foreground">
                        {formatDateTime(task.createTime)}
                      </p>
                    </div>

                    {task.dueDate && (
                      <div>
                        <h4 className="font-semibold mb-2 flex items-center gap-2">
                          <Calendar className="h-4 w-4" />
                          Due Date
                        </h4>
                        <p className="text-sm text-muted-foreground">
                          {formatDateTime(task.dueDate)}
                        </p>
                      </div>
                    )}
                  </div>

                  {displayVars.length > 0 && (
                    <div>
                      <h4 className="font-semibold mb-3">Process Variables</h4>
                      <div className="divide-y rounded-lg border">
                        {displayVars.map(([key, value]) => (
                          <div key={key} className="flex items-start px-4 py-2 gap-4">
                            <span className="text-sm font-medium text-muted-foreground w-40 shrink-0 pt-0.5">
                              {formatVarLabel(key)}
                            </span>
                            <span className="text-sm break-all">
                              {value === null || value === undefined ? (
                                <span className="text-muted-foreground italic">—</span>
                              ) : typeof value === 'boolean' ? (
                                <span className={value ? 'text-green-600' : 'text-red-600'}>
                                  {value ? 'Yes' : 'No'}
                                </span>
                              ) : typeof value === 'object' ? (
                                JSON.stringify(value)
                              ) : (
                                String(value)
                              )}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="form" className="mt-4">
              <FormSection
                formData={formData}
                isLoading={isLoadingForm}
                onSubmit={handleFormSubmit}
                isSubmitting={submitFormMutation.isPending}
                readonly={!canComplete}
              />
            </TabsContent>

            <TabsContent value="history" className="mt-4">
              {activeTab === 'history' && (
                <ProcessTimeline
                  processInstanceId={task.processInstanceId}
                  taskHistory={history}
                  isLoading={isLoadingHistory}
                />
              )}
            </TabsContent>
          </Tabs>
          )}
        </div>

        <div className="lg:col-span-1">
          {!isApproval && (
            <Card>
              <CardHeader>
                <CardTitle>Actions</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {canClaim && (
                  <Button
                    onClick={handleClaim}
                    className="w-full"
                    disabled={claimTaskMutation.isPending}
                  >
                    <CheckCircle2 className="h-4 w-4 mr-2" />
                    {claimTaskMutation.isPending ? 'Claiming...' : 'Claim Task'}
                  </Button>
                )}

                {canComplete && (
                  <>
                    {!hasForm && (
                      <Button
                        onClick={handleComplete}
                        className="w-full"
                        disabled={completeTaskMutation.isPending}
                      >
                        <CheckCircle2 className="h-4 w-4 mr-2" />
                        {completeTaskMutation.isPending ? 'Completing...' : 'Complete Task'}
                      </Button>
                    )}

                    <Button
                      variant="outline"
                      className="w-full"
                      onClick={handleUnclaim}
                      disabled={unclaimTaskMutation.isPending}
                    >
                      <XCircle className="h-4 w-4 mr-2" />
                      {unclaimTaskMutation.isPending ? 'Unclaiming...' : 'Unclaim Task'}
                    </Button>

                    <Button
                      variant="outline"
                      className="w-full"
                      onClick={() => setShowDelegationModal(true)}
                    >
                      <UserPlus className="h-4 w-4 mr-2" />
                      Delegate Task
                    </Button>
                  </>
                )}

                {!canClaim && !canComplete && (
                  <div className="text-center py-4 text-sm text-muted-foreground">
                    {task.assignee && task.assignee !== user?.username
                      ? 'This task is assigned to another user'
                      : 'You cannot perform actions on this task'}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {isApproval && canClaim && (
            <Card>
              <CardHeader>
                <CardTitle>Quick Actions</CardTitle>
              </CardHeader>
              <CardContent>
                <Button
                  onClick={handleClaim}
                  className="w-full"
                  disabled={claimTaskMutation.isPending}
                >
                  <CheckCircle2 className="h-4 w-4 mr-2" />
                  {claimTaskMutation.isPending ? 'Claiming...' : 'Claim Task'}
                </Button>
              </CardContent>
            </Card>
          )}

          <Card className="mt-4">
            <CardHeader>
              <CardTitle className="text-base">Task Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Task ID</span>
                <span className="font-mono text-xs">{task?.id?.substring(0, 8)}...</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Process Instance</span>
                <span className="font-mono text-xs">
                  {task.processInstanceId?.substring(0, 8)}...
                </span>
              </div>
              {task.taskDefinitionKey && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Definition Key</span>
                  <span className="font-mono text-xs">{task.taskDefinitionKey}</span>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <DelegationModal
        isOpen={showDelegationModal}
        onClose={() => setShowDelegationModal(false)}
        onDelegate={handleDelegateTask}
        isSubmitting={delegateTaskMutation.isPending}
      />
    </div>
  )
}
