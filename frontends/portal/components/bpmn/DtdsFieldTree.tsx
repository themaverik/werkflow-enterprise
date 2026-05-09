'use client'

import { RefreshCw, ChevronRight } from 'lucide-react'
import type { FieldEntry } from '@/lib/api/dtds'

interface DtdsFieldTreeProps {
  fields: FieldEntry[]
  isLoading: boolean
  error: Error | null
  /** When provided, each field row becomes clickable and calls this handler */
  onFieldClick?: (field: FieldEntry) => void
}

export function DtdsFieldTree({
  fields,
  isLoading,
  error,
  onFieldClick,
}: DtdsFieldTreeProps) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground py-1">
        <RefreshCw className="h-3 w-3 animate-spin" />
        Loading fields…
      </div>
    )
  }

  if (error) {
    return (
      <p className="text-xs text-destructive">
        Failed to load fields: {error.message}
      </p>
    )
  }

  if (fields.length === 0) {
    return (
      <p className="text-xs text-muted-foreground italic">No fields defined for this operation.</p>
    )
  }

  return (
    <div className="rounded-md border overflow-hidden text-xs">
      {fields.map((field, idx) => (
        <FieldRow
          key={`${field.path}-${idx}`}
          field={field}
          onClick={onFieldClick ? () => onFieldClick(field) : undefined}
          isLast={idx === fields.length - 1}
        />
      ))}
    </div>
  )
}

interface FieldRowProps {
  field: FieldEntry
  onClick?: () => void
  isLast: boolean
}

function FieldRow({ field, onClick, isLast }: FieldRowProps) {
  const indent = field.depth * 12
  const isClickable = !!onClick

  return (
    <div
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable ? 0 : undefined}
      onClick={onClick}
      onKeyDown={isClickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') onClick?.() } : undefined}
      className={[
        'flex items-center gap-1.5 px-2 py-1.5',
        !isLast && 'border-b',
        isClickable
          ? 'cursor-pointer hover:bg-accent focus-visible:bg-accent focus-visible:outline-none'
          : '',
      ].filter(Boolean).join(' ')}
    >
      {/* depth indent */}
      <span style={{ width: indent, flexShrink: 0 }} />

      {field.depth > 0 && (
        <ChevronRight className="h-3 w-3 text-muted-foreground shrink-0" />
      )}

      <span className="flex-1 font-mono truncate" title={field.path}>
        {field.path}
      </span>

      {field.required && (
        <span className="shrink-0 text-[10px] font-semibold text-destructive">req</span>
      )}
      {field.isArrayItem && (
        <span className="shrink-0 text-[10px] text-muted-foreground">[]</span>
      )}
      <span className="shrink-0 text-[10px] text-muted-foreground bg-muted px-1 rounded">
        {field.type}
      </span>
    </div>
  )
}
