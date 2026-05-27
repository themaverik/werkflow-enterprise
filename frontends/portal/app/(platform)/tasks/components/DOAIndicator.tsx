'use client'

import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { AlertCircle, CheckCircle2, TrendingUp } from "lucide-react"
import type { DoaLevel } from '@/lib/api/doa'

export interface DOAIndicatorProps {
  userDoaLevel: number
  requestAmount: number
  requiredDoaLevel: number
  userApprovalLimit: number
  /**
   * Whether the current user can approve this request.
   * Must be the parent's resolved value — passed in so the indicator stays in sync
   * with the full canApprove formula (including requestAmount, userApprovalLimit > 0, etc.)
   * rather than recomputing a simplified version locally.
   */
  canApprove: boolean
  /** Role label for the next approver in the configured DOA hierarchy. */
  nextApproverRole?: string
  /** Full list of configured DOA levels (used to render the reference table), pre-sorted ascending by level. */
  configuredLevels?: DoaLevel[]
}

export function DOAIndicator({
  userDoaLevel,
  requestAmount,
  requiredDoaLevel,
  userApprovalLimit,
  canApprove,
  nextApproverRole,
  configuredLevels,
}: DOAIndicatorProps) {
  const utilizationPercent = userApprovalLimit > 0
    ? Math.min((requestAmount / userApprovalLimit) * 100, 100)
    : 100

  const getStatusColor = (): 'destructive' | 'default' | 'secondary' | 'outline' => {
    if (!canApprove) return 'destructive'
    if (utilizationPercent >= 90) return 'default'
    if (utilizationPercent >= 70) return 'secondary'
    return 'outline'
  }

  const getStatusIcon = () => {
    if (!canApprove) return <AlertCircle className="h-4 w-4" />
    if (utilizationPercent >= 90) return <TrendingUp className="h-4 w-4" />
    return <CheckCircle2 className="h-4 w-4" />
  }

  const getStatusMessage = () => {
    if (!canApprove) {
      const nextRole = nextApproverRole ?? `Level ${requiredDoaLevel}`
      return `This request exceeds your approval authority. Required level: ${requiredDoaLevel} (${nextRole})`
    }
    if (utilizationPercent >= 90) {
      return 'Request amount is near your approval limit'
    }
    return 'Within your approval authority'
  }

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount)

  return (
    <Card>
      <CardContent className="pt-6">
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {getStatusIcon()}
              <span className="font-semibold">Delegation of Authority</span>
            </div>
            <Badge variant={getStatusColor()}>
              Level {userDoaLevel}
            </Badge>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Request Amount</span>
              <span className="font-semibold">{formatCurrency(requestAmount)}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Your Approval Limit</span>
              <span className="font-semibold">{formatCurrency(userApprovalLimit)}</span>
            </div>
          </div>

          {canApprove && (
            <div className="space-y-2">
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>Authority Utilization</span>
                <span>{utilizationPercent.toFixed(0)}%</span>
              </div>
              <Progress value={utilizationPercent} className="h-2" />
            </div>
          )}

          <div className={`text-sm p-3 rounded-lg ${
            canApprove
              ? 'bg-green-50 dark:bg-green-950 text-green-700 dark:text-green-300'
              : 'bg-red-50 dark:bg-red-950 text-red-700 dark:text-red-300'
          }`}>
            {getStatusMessage()}
          </div>

          {!canApprove && nextApproverRole && (
            <div className="text-sm text-muted-foreground">
              Next Approver: <span className="font-medium">{nextApproverRole}</span>
            </div>
          )}

          {configuredLevels && configuredLevels.length > 0 && (
            <div className="pt-2 border-t">
              <div className="text-xs text-muted-foreground">
                <div className="font-semibold mb-1">DOA Levels:</div>
                <div className="space-y-1">
                  {configuredLevels.map((level) => (
                    <div key={level.id} className="flex justify-between">
                      <span>{level.label}</span>
                      <span>{formatCurrency(level.maxAmount)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
