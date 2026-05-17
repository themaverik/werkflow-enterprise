'use client'

import { useCallback } from 'react'
import { RefreshCw } from 'lucide-react'
import { Label } from '@/components/ui/label'
import { VariableComboBoxBpmnAdapter } from '@/components/bpmn/VariableComboBoxBpmnAdapter'
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
  processId: string
  activityId: string
  onMappingChange: (path: string, processVariable: string) => void
}

export function DtdsInputFieldForm({
  fields,
  isLoading,
  error,
  mappings,
  processId,
  activityId,
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
          <InputMappingRow
            key={field.path}
            field={field}
            processVariable={mapping?.processVariable ?? ''}
            processId={processId}
            activityId={activityId}
            onMappingChange={onMappingChange}
          />
        )
      })}
    </div>
  )
}

// HIGH #2 + MEDIUM #6: isolated row component so useCallback is stable per-row
interface InputMappingRowProps {
  field: FieldEntry
  processVariable: string
  processId: string
  activityId: string
  onMappingChange: (path: string, processVariable: string) => void
}

function InputMappingRow({
  field,
  processVariable,
  processId,
  activityId,
  onMappingChange,
}: InputMappingRowProps) {
  const getValue = useCallback(() => processVariable, [processVariable])
  const setValue = useCallback(
    (v: string) => onMappingChange(field.path, v),
    [field.path, onMappingChange]
  )
  const ariaLabel = `Map process variable to ${field.path}`

  return (
    <div className="grid grid-cols-2 gap-1 items-center">
      <Label
        className="text-xs font-mono truncate"
        title={field.path}
      >
        {field.path}
        {field.required && (
          <span className="ml-1 text-destructive text-[10px]">*</span>
        )}
      </Label>
      {/* F6c: variable picker for the process variable mapping side */}
      <VariableComboBoxBpmnAdapter
        key={`input-${activityId}-${field.path}`}
        mode="single"
        sourceKeys={['dtds-variables-string', 'dtds-variables-number', 'dtds-variables-date']}
        processId={processId}
        activityId={activityId}
        placeholder={`\${${toCamelCase(field.path)}}`}
        label={ariaLabel}
        getValue={getValue}
        setValue={setValue}
      />
    </div>
  )
}

function toCamelCase(path: string): string {
  const last = path.split('.').pop() ?? path
  return last.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase())
}
