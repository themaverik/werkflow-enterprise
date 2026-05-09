'use client'

import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { DtdsFieldTree } from '@/components/bpmn/DtdsFieldTree'
import { useDtdsFields } from '@/hooks/useDtdsFields'
import type { OperationSummary } from '@/lib/api/dtds'

const OPERATION_CATEGORY_COLORS: Record<OperationSummary['category'], string> = {
  read: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
  write: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  list: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
  delete: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
  stream: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
}

interface DtdsOperationDetailProps {
  connectorKey: string
  operation: OperationSummary
}

export function DtdsOperationDetail({ connectorKey, operation }: DtdsOperationDetailProps) {
  const [expanded, setExpanded] = useState(false)

  const inputFields = useDtdsFields(
    expanded ? connectorKey : null,
    expanded ? operation.id : null,
    'input'
  )
  const outputFields = useDtdsFields(
    expanded ? connectorKey : null,
    expanded ? operation.id : null,
    'output'
  )

  return (
    <div className="rounded-md border overflow-hidden">
      <button
        type="button"
        className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-accent transition-colors focus-visible:outline-none focus-visible:bg-accent"
        onClick={() => setExpanded(prev => !prev)}
        aria-expanded={expanded}
      >
        <span
          className={`shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded ${OPERATION_CATEGORY_COLORS[operation.category]}`}
        >
          {operation.category}
        </span>
        <span className="flex-1 text-sm font-medium truncate">{operation.displayName}</span>
        <code className="text-xs text-muted-foreground shrink-0">{operation.id}</code>
        {expanded ? (
          <ChevronUp className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
        ) : (
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
        )}
      </button>

      {expanded && (
        <div className="border-t bg-muted/10 p-3">
          {operation.description && (
            <p className="text-xs text-muted-foreground mb-3">{operation.description}</p>
          )}

          <Tabs defaultValue="input">
            <TabsList className="w-full grid grid-cols-2 h-7">
              <TabsTrigger value="input" className="text-xs">Input fields</TabsTrigger>
              <TabsTrigger value="output" className="text-xs">Output fields</TabsTrigger>
            </TabsList>
            <TabsContent value="input" className="mt-2">
              <DtdsFieldTree
                fields={inputFields.fields}
                isLoading={inputFields.isLoading}
                error={inputFields.error}
              />
            </TabsContent>
            <TabsContent value="output" className="mt-2">
              <DtdsFieldTree
                fields={outputFields.fields}
                isLoading={outputFields.isLoading}
                error={outputFields.error}
              />
            </TabsContent>
          </Tabs>
        </div>
      )}
    </div>
  )
}
