'use client'

import { useState, useEffect } from 'react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { CheckCircle2, XCircle, Loader2, FileText, Building, User as UserIcon, Calendar, DollarSign, ArrowUp, AlertTriangle } from "lucide-react"
import { DOAIndicator } from './DOAIndicator'
import { useDoaLevels, parseDoaLevelNumber } from '@/lib/hooks/useDoaLevels'
import { toast } from 'sonner'
import type { Task } from '@/lib/types/task'
import type { User } from '@/lib/auth/auth-context'

export interface ApprovalPanelProps {
  task: Task
  user: User
  onApprove: (comment: string) => Promise<void>
  onReject: (comment: string) => Promise<void>
  onEscalate: (comment: string) => Promise<void>
  isSubmitting?: boolean
}

type ApprovalAction = 'approve' | 'reject' | 'escalate'

const DEAD_END_MESSAGE =
  "You've reached the maximum approval level for your authority. Ask an administrator to grant the required DOA level, then sign in again to continue."

export function ApprovalPanel({
  task,
  user,
  onApprove,
  onReject,
  onEscalate,
  isSubmitting = false,
}: ApprovalPanelProps) {
  const [selectedAction, setSelectedAction] = useState<ApprovalAction>('approve')
  const [comment, setComment] = useState('')
  const [validationError, setValidationError] = useState('')

  const tenantId = task.tenantId ?? undefined
  const { data: doaLevels, isLoading: isLoadingDoa, isError: isDoaError } = useDoaLevels(tenantId)

  const rawRequestAmount = task.processVariables?.requestAmount ?? task.processVariables?.amount
  const requestAmount = typeof rawRequestAmount === 'number' ? rawRequestAmount : Number(rawRequestAmount) || 0
  const rawRequiredDoaLevel = task.processVariables?.approvalLevel
  const requiredDoaLevel = typeof rawRequiredDoaLevel === 'number' ? rawRequiredDoaLevel : Number(rawRequiredDoaLevel) || 1
  const userDoaLevel = user.doaLevel || 0

  // Derive configured limits from the fetched DoA levels.
  // doaLevel field is a string; parseDoaLevelNumber normalises "1"/"L1" etc. to a number.
  const userApprovalLimit = (() => {
    if (!doaLevels) return 0
    const myLevel = doaLevels.find(
      (l) => parseDoaLevelNumber(l.doaLevel) === userDoaLevel
    )
    return myLevel?.maxAmount ?? 0
  })()

  const maxConfiguredLevel = (() => {
    if (!doaLevels || doaLevels.length === 0) return 0
    return Math.max(...doaLevels.map((l) => parseDoaLevelNumber(l.doaLevel)))
  })()

  const nextApproverRole = (() => {
    if (!doaLevels) return undefined
    // Find the level immediately above the user's level.
    const sorted = [...doaLevels].sort(
      (a, b) => parseDoaLevelNumber(a.doaLevel) - parseDoaLevelNumber(b.doaLevel)
    )
    const nextLevel = sorted.find(
      (l) => parseDoaLevelNumber(l.doaLevel) > userDoaLevel
    )
    return nextLevel?.label
  })()

  // While DOA config is loading, treat canApprove as false to avoid acting on stale hardcoded data.
  // Fail closed: require userApprovalLimit > 0 so a missing/unconfigured level does not grant approval.
  const canApprove = !isLoadingDoa && userApprovalLimit > 0 && userDoaLevel >= requiredDoaLevel && requestAmount <= userApprovalLimit
  const isAssignedToUser = task.assignee === user.username

  // Escalate is shown only when: cannot approve AND BPMN gateway routes escalate AND a higher authority exists.
  // Also blocked when DOA config failed to load — we cannot know if a higher authority exists.
  const higherAuthorityExists = userDoaLevel < maxConfiguredLevel
  const canEscalate = !canApprove && task.canEscalate === true && higherAuthorityExists && !isLoadingDoa && !isDoaError

  // Dead-end (authority insufficient): user cannot approve AND either the BPMN has no escalate route OR no
  // higher authority exists. Only shown when config loaded successfully — config errors get their own message.
  // Also suppressed when tenantId is absent (DOA lookup was skipped entirely).
  const isDeadEnd = !!tenantId && !isDoaError && !canApprove && !canEscalate && !isLoadingDoa

  // Show dead-end toast once when the user lands on a dead-end approval task.
  // !isLoadingDoa guard prevents a spurious re-fire during the loading bounce.
  useEffect(() => {
    if (!isLoadingDoa && isDeadEnd && isAssignedToUser) {
      toast.error('Approval Authority Insufficient', { description: DEAD_END_MESSAGE })
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isDeadEnd, isAssignedToUser])

  // When escalate becomes unavailable (e.g. user switches to a task where it is not offered),
  // reset the selected action away from escalate to avoid submitting an invalid choice.
  useEffect(() => {
    if (selectedAction === 'escalate' && !canEscalate) {
      setSelectedAction('reject')
    }
  }, [canEscalate, selectedAction])

  const handleSubmit = async () => {
    // Hard stop: close the render-gap window between escalate radio disappearing and the reset effect firing.
    if (selectedAction === 'escalate' && !canEscalate) { setSelectedAction('reject'); return }

    if ((selectedAction === 'reject' || selectedAction === 'escalate') && !comment.trim()) {
      setValidationError(
        selectedAction === 'reject'
          ? 'Comment is required when rejecting a request'
          : 'Comment is required when escalating a request'
      )
      return
    }

    setValidationError('')

    try {
      switch (selectedAction) {
        case 'approve':
          await onApprove(comment)
          break
        case 'reject':
          await onReject(comment)
          break
        case 'escalate':
          await onEscalate(comment)
          break
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'An unexpected error occurred'
      toast.error('Action Failed', { description: message })
    }
  }

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount)

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return 'N/A'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
  }

  const commentPlaceholder = () => {
    if (selectedAction === 'approve') return 'Add any additional comments...'
    if (selectedAction === 'escalate') return 'Explain why you are escalating this request...'
    return 'Explain why you are rejecting this request...'
  }

  const commentLabel = () => {
    if (selectedAction === 'approve') return 'Comment (Optional)'
    if (selectedAction === 'escalate') return 'Escalation Reason (Required)'
    return 'Rejection Reason (Required)'
  }

  return (
    <div className="space-y-6">
      {/* DOA Indicator — presentational, receives config-sourced data */}
      <DOAIndicator
        userDoaLevel={userDoaLevel}
        requestAmount={requestAmount}
        requiredDoaLevel={requiredDoaLevel}
        userApprovalLimit={userApprovalLimit}
        canApprove={canApprove}
        nextApproverRole={nextApproverRole}
        configuredLevels={
          doaLevels
            ? [...doaLevels].sort(
                (a, b) => parseDoaLevelNumber(a.doaLevel) - parseDoaLevelNumber(b.doaLevel)
              )
            : undefined
        }
      />

      {/* DOA config error — shown when the authority configuration could not be loaded */}
      {isDoaError && (
        <Card className="border-destructive">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-start gap-3 text-destructive">
              <AlertTriangle className="h-5 w-5 mt-0.5 shrink-0" />
              <p className="text-sm">
                Could not load approval authority configuration. Try again or contact an administrator.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Dead-end inline alert — shown when config loaded but user has insufficient authority */}
      {isDeadEnd && (
        <Card className="border-destructive">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-start gap-3 text-destructive">
              <AlertTriangle className="h-5 w-5 mt-0.5 shrink-0" />
              <p className="text-sm">{DEAD_END_MESSAGE}</p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Request Summary */}
      <Card>
        <CardHeader>
          <CardTitle>Request Summary</CardTitle>
          <CardDescription>Review the details before making a decision</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <FileText className="h-4 w-4" />
                <span>Title</span>
              </div>
              <p className="font-medium">{String(task.processVariables?.title ?? task.name)}</p>
            </div>

            <div className="space-y-1">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <DollarSign className="h-4 w-4" />
                <span>Amount</span>
              </div>
              <p className="font-semibold text-lg">{formatCurrency(requestAmount)}</p>
            </div>

            <div className="space-y-1">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <UserIcon className="h-4 w-4" />
                <span>Requested By</span>
              </div>
              <p className="font-medium">{String(task.processVariables?.requestedBy ?? 'Unknown')}</p>
            </div>

            <div className="space-y-1">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Building className="h-4 w-4" />
                <span>Department</span>
              </div>
              <p className="font-medium">{String(task.processVariables?.departmentName ?? user.department ?? '')}</p>
            </div>

            <div className="space-y-1">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Calendar className="h-4 w-4" />
                <span>Created</span>
              </div>
              <p className="font-medium">{formatDate(task.createTime)}</p>
            </div>

            <div className="space-y-1">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Calendar className="h-4 w-4" />
                <span>Due Date</span>
              </div>
              <p className="font-medium">{formatDate(task.dueDate)}</p>
            </div>
          </div>

          {task.processVariables?.description && (
            <>
              <Separator />
              <div className="space-y-2">
                <Label className="text-sm text-muted-foreground">Description</Label>
                <p className="text-sm">{String(task.processVariables.description)}</p>
              </div>
            </>
          )}

          {task.processVariables?.businessJustification && (
            <div className="space-y-2">
              <Label className="text-sm text-muted-foreground">Business Justification</Label>
              <p className="text-sm">{String(task.processVariables.businessJustification)}</p>
            </div>
          )}

          {task.processVariables?.expectedBenefits && (
            <div className="space-y-2">
              <Label className="text-sm text-muted-foreground">Expected Benefits</Label>
              <p className="text-sm">{String(task.processVariables.expectedBenefits)}</p>
            </div>
          )}

          {task.processVariables?.category && (
            <div className="space-y-2">
              <Label className="text-sm text-muted-foreground">Category</Label>
              <Badge variant="outline">{String(task.processVariables.category)}</Badge>
            </div>
          )}

          {task.processVariables?.priority && (
            <div className="space-y-2">
              <Label className="text-sm text-muted-foreground">Priority</Label>
              <Badge variant={
                task.processVariables.priority === 'HIGH' ? 'destructive' :
                task.processVariables.priority === 'MEDIUM' ? 'default' : 'secondary'
              }>
                {String(task.processVariables.priority)}
              </Badge>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Approval Decision Form */}
      <Card>
        <CardHeader>
          <CardTitle>Approval Decision</CardTitle>
          <CardDescription>
            {isAssignedToUser
              ? 'Make your decision on this request'
              : 'You must claim this task before making a decision'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <RadioGroup
            value={selectedAction}
            onValueChange={(value) => setSelectedAction(value as ApprovalAction)}
            disabled={!isAssignedToUser || isSubmitting || isLoadingDoa}
          >
            {/* Approve — always shown; disabled when user cannot approve */}
            <div className="flex items-center space-x-2">
              <RadioGroupItem value="approve" id="approve" disabled={!canApprove} />
              <Label
                htmlFor="approve"
                className={`flex items-center gap-2 cursor-pointer ${!canApprove ? 'opacity-50' : ''}`}
              >
                <CheckCircle2 className="h-4 w-4 text-green-600" />
                <span>Approve</span>
                {!canApprove && !isLoadingDoa && (
                  <Badge variant="destructive" className="ml-2">Exceeds Authority</Badge>
                )}
              </Label>
            </div>

            {/* Escalate — shown only when canEscalate is true */}
            {canEscalate && (
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="escalate" id="escalate" disabled={!canEscalate} />
                <Label
                  htmlFor="escalate"
                  className={`flex items-center gap-2 cursor-pointer ${!canEscalate ? 'opacity-50' : ''}`}
                >
                  <ArrowUp className="h-4 w-4 text-amber-600" />
                  <span>Escalate to higher authority</span>
                </Label>
              </div>
            )}

            {/* Reject — always shown */}
            <div className="flex items-center space-x-2">
              <RadioGroupItem value="reject" id="reject" />
              <Label htmlFor="reject" className="flex items-center gap-2 cursor-pointer">
                <XCircle className="h-4 w-4 text-red-600" />
                <span>Reject</span>
              </Label>
            </div>
          </RadioGroup>

          <div className="space-y-2">
            <Label htmlFor="comment">{commentLabel()}</Label>
            <Textarea
              id="comment"
              placeholder={commentPlaceholder()}
              value={comment}
              onChange={(e) => {
                setComment(e.target.value)
                setValidationError('')
              }}
              disabled={!isAssignedToUser || isSubmitting}
              rows={4}
              className={validationError ? 'border-red-500' : ''}
            />
            {validationError && (
              <p className="text-sm text-red-500">{validationError}</p>
            )}
          </div>
        </CardContent>
        <CardFooter className="flex justify-between">
          <div className="text-sm text-muted-foreground">
            {!isAssignedToUser && 'Claim this task to make a decision'}
          </div>
          <Button
            onClick={handleSubmit}
            disabled={
              !isAssignedToUser ||
              isSubmitting ||
              isLoadingDoa ||
              (selectedAction === 'approve' && !canApprove) ||
              (selectedAction === 'escalate' && !canEscalate)
            }
            className="min-w-[120px]"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Submitting...
              </>
            ) : (
              <>
                {selectedAction === 'approve' && <CheckCircle2 className="h-4 w-4 mr-2" />}
                {selectedAction === 'reject' && <XCircle className="h-4 w-4 mr-2" />}
                {selectedAction === 'escalate' && <ArrowUp className="h-4 w-4 mr-2" />}
                Submit Decision
              </>
            )}
          </Button>
        </CardFooter>
      </Card>
    </div>
  )
}
