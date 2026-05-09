'use client'

import { useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ChevronDown, ChevronUp } from 'lucide-react'
import type { ConnectorSummary, OperationSummary } from '@/lib/api/dtds'
import { useDtdsOperations } from '@/hooks/useDtdsOperations'
import { DtdsOperationDetail } from './DtdsOperationDetail'

const CATEGORY_COLORS: Record<string, string> = {
  erp: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
  crm: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  finance: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
  hr: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
  notification: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
  storage: 'bg-slate-100 text-slate-800 dark:bg-slate-900/30 dark:text-slate-300',
}

function categoryStyle(category: string): string {
  return CATEGORY_COLORS[category.toLowerCase()] ?? 'bg-muted text-muted-foreground'
}

interface DtdsConnectorCardProps {
  connector: ConnectorSummary
}

export function DtdsConnectorCard({ connector }: DtdsConnectorCardProps) {
  const [expanded, setExpanded] = useState(false)
  const { operations, isLoading, error } = useDtdsOperations(expanded ? connector.key : null)

  return (
    <Card className="flex flex-col">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <CardTitle className="text-base leading-tight truncate">
              {connector.displayName}
            </CardTitle>
            <div className="flex items-center gap-1.5 mt-1">
              <code className="text-xs bg-muted px-1.5 py-0.5 rounded">{connector.key}</code>
              <span className="text-xs text-muted-foreground">v{connector.version}</span>
            </div>
          </div>
          <span
            className={`shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${categoryStyle(connector.category)}`}
          >
            {connector.category}
          </span>
        </div>
        {connector.description && (
          <CardDescription className="mt-1.5 text-sm line-clamp-2">
            {connector.description}
          </CardDescription>
        )}
      </CardHeader>

      <CardContent className="flex-1 space-y-3">
        {connector.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {connector.tags.map(tag => (
              <Badge key={tag} variant="secondary" className="text-xs">
                {tag}
              </Badge>
            ))}
          </div>
        )}

        <Button
          variant="outline"
          size="sm"
          className="w-full h-7 text-xs gap-1.5"
          onClick={() => setExpanded(prev => !prev)}
          aria-expanded={expanded}
        >
          {expanded ? (
            <>
              <ChevronUp className="h-3 w-3" />
              Hide operations
            </>
          ) : (
            <>
              <ChevronDown className="h-3 w-3" />
              View operations
            </>
          )}
        </Button>

        {expanded && (
          <OperationList
            connectorKey={connector.key}
            operations={operations}
            isLoading={isLoading}
            error={error}
          />
        )}
      </CardContent>
    </Card>
  )
}

interface OperationListProps {
  connectorKey: string
  operations: OperationSummary[]
  isLoading: boolean
  error: Error | null
}

function OperationList({ connectorKey, operations, isLoading, error }: OperationListProps) {
  if (isLoading) {
    return (
      <div className="space-y-1.5">
        {[1, 2, 3].map(i => (
          <div key={i} className="h-8 bg-muted animate-pulse rounded" />
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <p className="text-xs text-destructive">
        Failed to load operations: {error.message}
      </p>
    )
  }

  if (operations.length === 0) {
    return (
      <p className="text-xs text-muted-foreground italic">No operations defined.</p>
    )
  }

  return (
    <div className="space-y-1.5">
      {operations.map(op => (
        <DtdsOperationDetail
          key={op.id}
          connectorKey={connectorKey}
          operation={op}
        />
      ))}
    </div>
  )
}
