'use client'

import { RefreshCw } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { FieldEntry } from '@/lib/api/dtds'

interface InputMapping {
  path: string
  processVariable: string
}

interface DtdsInputFieldFormProps {
  fields: FieldEntry[]
  isLoading: boolean
  error: Error | null
  mappings: InputMapping[]
  onMappingChange: (path: string, processVariable: string) => void
}

export function DtdsInputFieldForm({
  fields,
  isLoading,
  error,
  mappings,
  onMappingChange,
}: DtdsInputFieldFormProps) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground py-1">
        <RefreshCw className="h-3 w-3 animate-spin" />
        Loading input fields…
      </div>
    )
  }

  if (error) {
    return (
      <p className="text-xs text-destructive">
        Failed to load input fields: {error.message}
      </p>
    )
  }

  const leafFields = fields.filter(f => f.depth === 0 || f.isArrayItem === false)

  if (leafFields.length === 0) {
    return (
      <p className="text-xs text-muted-foreground italic">No input fields for this operation.</p>
    )
  }

  return (
    <div className="space-y-2">
      {leafFields.map((field) => {
        const mapping = mappings.find(m => m.path === field.path)
        return (
          <div key={field.path} className="grid grid-cols-2 gap-1 items-center">
            <Label
              className="text-xs font-mono truncate"
              title={field.path}
            >
              {field.path}
              {field.required && (
                <span className="ml-1 text-destructive text-[10px]">*</span>
              )}
            </Label>
            <Input
              className="h-7 text-xs"
              placeholder={`\${${toCamelCase(field.path)}}`}
              value={mapping?.processVariable ?? ''}
              onChange={(e) => onMappingChange(field.path, e.target.value)}
              aria-label={`Map process variable to ${field.path}`}
            />
          </div>
        )
      })}
    </div>
  )
}

function toCamelCase(path: string): string {
  const last = path.split('.').pop() ?? path
  return last.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase())
}
