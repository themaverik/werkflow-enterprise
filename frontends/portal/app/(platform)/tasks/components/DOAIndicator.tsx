'use client'

import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { AlertCircle, CheckCircle2, TrendingUp } from "lucide-react"

export interface DOAIndicatorProps {
  userDoaLevel: number
  requestAmount: number
  requiredDoaLevel: number
  userApprovalLimit: number
  nextApproverRole?: string
}

const DOA_LIMITS: Record<number, { limit: number; role: string }> = {
  1: { limit: 1000, role: 'Department Manager' },
  2: { limit: 10000, role: 'Department Head' },
  3: { limit: 50000, role: 'Finance Manager' },
  4: { limit: 250000, role: 'CFO' },
}

export function DOAIndicator({
  userDoaLevel,
  requestAmount,
  requiredDoaLevel,
  userApprovalLimit,
  nextApproverRole,
}: DOAIndicatorProps) {
  const canApprove = userDoaLevel >= requiredDoaLevel
  const utilizationPercent = Math.min((requestAmount / userApprovalLimit) * 100, 100)

  const getStatusColor = () => {
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
      return `This request exceeds your approval authority. Required level: ${requiredDoaLevel} (${DOA_LIMITS[requiredDoaLevel]?.role || 'Unknown'})`
    }
    if (utilizationPercent >= 90) {
      return 'Request amount is near your approval limit'
    }
    return 'Within your approval authority'
  }

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount)
  }

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

          <div className="pt-2 border-t">
            <div className="text-xs text-muted-foreground">
              <div className="font-semibold mb-1">DOA Levels:</div>
              <div className="space-y-1">
                {Object.entries(DOA_LIMITS).map(([level, info]) => (
                  <div key={level} className="flex justify-between">
                    <span>Level {level}: {info.role}</span>
                    <span>{formatCurrency(info.limit)}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
