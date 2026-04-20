'use client'

import { useState } from 'react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { CheckCircle2, XCircle, ArrowUpCircle, Loader2, FileText, Building, User as UserIcon, Calendar, DollarSign } from "lucide-react"
import { DOAIndicator } from './DOAIndicator'
import type { Task } from '@/lib/types/task'
import type { User } from '@/lib/auth/auth-context'

export interface ApprovalPanelProps {
  task: Task
  user: User
  onApprove: (comment: string) => Promise<void>
  onReject: (comment: string) => Promise<void>
  onEscalate: (reason: string) => Promise<void>
  isSubmitting?: boolean
}

type ApprovalAction = 'approve' | 'reject' | 'escalate'

const DOA_LIMITS: Record<number, number> = {
  1: 1000,
  2: 10000,
  3: 50000,
  4: 250000,
}

const DOA_ROLES: Record<number, string> = {
  1: 'Department Manager',
  2: 'Department Head',
  3: 'Finance Manager',
  4: 'CFO',
}

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

  const rawRequestAmount = task.processVariables?.requestAmount ?? task.processVariables?.amount
  const requestAmount = typeof rawRequestAmount === 'number' ? rawRequestAmount : Number(rawRequestAmount) || 0
  const rawRequiredDoaLevel = task.processVariables?.approvalLevel
  const requiredDoaLevel = typeof rawRequiredDoaLevel === 'number' ? rawRequiredDoaLevel : Number(rawRequiredDoaLevel) || 1
  const userDoaLevel = user.doaLevel || 0
  const userApprovalLimit = DOA_LIMITS[userDoaLevel] || 0

  const canApprove = userDoaLevel >= requiredDoaLevel && requestAmount <= userApprovalLimit
  const isAssignedToUser = task.assignee === user.username

  const handleSubmit = async () => {
    if (selectedAction === 'reject' && !comment.trim()) {
      setValidationError('Comment is required when rejecting a request')
      return
    }

    if (selectedAction === 'escalate' && !comment.trim()) {
      setValidationError('Reason is required when escalating a request')
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
    } catch (error) {
      console.error('Approval action failed:', error)
    }
  }

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount)
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return 'N/A'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
  }

  return (
    <div className="space-y-6">
      {/* DOA Indicator */}
      <DOAIndicator
        userDoaLevel={userDoaLevel}
        requestAmount={requestAmount}
        requiredDoaLevel={requiredDoaLevel}
        userApprovalLimit={userApprovalLimit}
        nextApproverRole={DOA_ROLES[requiredDoaLevel]}
      />

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
            disabled={!isAssignedToUser || isSubmitting}
          >
            <div className="flex items-center space-x-2">
              <RadioGroupItem value="approve" id="approve" disabled={!canApprove} />
              <Label
                htmlFor="approve"
                className={`flex items-center gap-2 cursor-pointer ${!canApprove ? 'opacity-50' : ''}`}
              >
                <CheckCircle2 className="h-4 w-4 text-green-600" />
                <span>Approve</span>
                {!canApprove && (
                  <Badge variant="destructive" className="ml-2">Exceeds Authority</Badge>
                )}
              </Label>
            </div>

            <div className="flex items-center space-x-2">
              <RadioGroupItem value="reject" id="reject" />
              <Label htmlFor="reject" className="flex items-center gap-2 cursor-pointer">
                <XCircle className="h-4 w-4 text-red-600" />
                <span>Reject</span>
              </Label>
            </div>

            <div className="flex items-center space-x-2">
              <RadioGroupItem value="escalate" id="escalate" />
              <Label htmlFor="escalate" className="flex items-center gap-2 cursor-pointer">
                <ArrowUpCircle className="h-4 w-4 text-blue-600" />
                <span>Escalate to Higher Authority</span>
              </Label>
            </div>
          </RadioGroup>

          <div className="space-y-2">
            <Label htmlFor="comment">
              {selectedAction === 'approve' ? 'Comment (Optional)' :
               selectedAction === 'reject' ? 'Rejection Reason (Required)' :
               'Escalation Reason (Required)'}
            </Label>
            <Textarea
              id="comment"
              placeholder={
                selectedAction === 'approve' ? 'Add any additional comments...' :
                selectedAction === 'reject' ? 'Explain why you are rejecting this request...' :
                'Explain why escalation is needed...'
              }
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
            disabled={!isAssignedToUser || isSubmitting || (!canApprove && selectedAction === 'approve')}
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
                {selectedAction === 'escalate' && <ArrowUpCircle className="h-4 w-4 mr-2" />}
                Submit Decision
              </>
            )}
          </Button>
        </CardFooter>
      </Card>
    </div>
  )
}
