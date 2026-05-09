'use client'

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { RefreshCw } from 'lucide-react'
import type { OperationSummary } from '@/lib/api/dtds'

const CATEGORY_COLORS: Record<OperationSummary['category'], string> = {
  read: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
  write: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  list: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
  delete: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
  stream: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
}

interface DtdsOperationPickerProps {
  operations: OperationSummary[]
  value: string
  isLoading: boolean
  error: Error | null
  onChange: (operationId: string) => void
}

export function DtdsOperationPicker({
  operations,
  value,
  isLoading,
  error,
  onChange,
}: DtdsOperationPickerProps) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-1.5 h-8 text-xs text-muted-foreground">
        <RefreshCw className="h-3 w-3 animate-spin" />
        Loading operations…
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

  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="h-8 text-xs">
        <SelectValue placeholder="Select operation" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="">
          (none)
        </SelectItem>
        {operations.map(op => (
          <SelectItem key={op.id} value={op.id}>
            <span className="flex items-center gap-1.5">
              <span
                className={`inline-block text-[10px] font-medium px-1 rounded ${CATEGORY_COLORS[op.category]}`}
              >
                {op.category}
              </span>
              {op.displayName}
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

interface OperationCategoryBadgeProps {
  category: OperationSummary['category']
}

export function OperationCategoryBadge({ category }: OperationCategoryBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={`text-[10px] font-medium ${CATEGORY_COLORS[category]}`}
    >
      {category}
    </Badge>
  )
}
