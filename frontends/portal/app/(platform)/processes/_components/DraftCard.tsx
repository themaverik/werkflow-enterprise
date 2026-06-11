'use client'

import Link from 'next/link'
import { Play, Pencil, Trash2, GitBranch } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface DraftSummary {
  processKey: string
  name: string
  updatedAt?: string
  departmentCode?: string
  categoryCode?: string
  tags?: string[]
}

interface DraftCardProps {
  draft: DraftSummary
  linkedDeployedProcessName?: string
  isDeleting: boolean
  onDelete: () => void
}

export function DraftCard({
  draft,
  linkedDeployedProcessName,
  isDeleting,
  onDelete,
}: DraftCardProps) {
  const displayName = draft.name || draft.processKey

  return (
    <div className="bg-card border border-dashed border-border rounded-xl p-5 flex flex-col gap-3.5 relative">
      {/* Draft badge top-right */}
      <span
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          fontSize: 10,
          fontWeight: 600,
          padding: '2px 7px',
          borderRadius: 99,
          background: 'var(--badge-warning-bg)',
          color: 'var(--badge-warning)',
          border: '1px solid var(--badge-warning-border)',
        }}
      >
        Draft
      </span>

      {/* Top row: icon + name */}
      <div className="flex gap-3 items-start">
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 11,
            background: 'var(--badge-warning-bg)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <GitBranch size={20} style={{ color: 'var(--badge-warning)' }} strokeWidth={1.8} />
        </div>

        <div className="flex-1 min-w-0 pr-12">
          <div className="text-sm font-semibold text-foreground overflow-hidden text-ellipsis whitespace-nowrap">
            {displayName}
          </div>
          <div className="text-[11px] text-muted-foreground mt-0.5">
            {draft.processKey}
          </div>
          {draft.updatedAt && (
            <div className="text-[10px] text-muted-foreground mt-0.5">
              Last edited {new Date(draft.updatedAt).toLocaleDateString()}
            </div>
          )}
          {linkedDeployedProcessName && (
            <span
              style={{
                display: 'inline-block',
                marginTop: 4,
                fontSize: 10,
                fontWeight: 600,
                padding: '2px 7px',
                borderRadius: 99,
                background: 'var(--badge-warning-bg)',
                color: 'var(--badge-warning)',
                border: '1px solid var(--badge-warning-border)',
              }}
            >
              Draft for deployed process
            </span>
          )}
        </div>
      </div>

      {/* Test Workflow button */}
      <Button
        asChild
        className="w-full justify-center text-white bg-amber-600 hover:bg-amber-700 border-0"
      >
        <Link href={`/processes/new?draft=${draft.processKey}`}>
          <Play size={12} strokeWidth={2.2} className="mr-1.5" />
          Test Workflow
        </Link>
      </Button>

      {/* Process Custody */}
      {(draft.departmentCode || draft.categoryCode || (draft.tags && draft.tags.length > 0)) && (
        <div className="border-t border-border pt-2.5">
          <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-[0.06em] mb-1.5">
            Process Custody
          </div>
          <dl className="grid gap-y-[3px]" style={{ gridTemplateColumns: 'auto 1fr', columnGap: 8 }}>
            <dt className="text-[10px] text-muted-foreground">Department</dt>
            <dd className="text-[10px] text-muted-foreground font-mono">{draft.departmentCode ?? '—'}</dd>
            <dt className="text-[10px] text-muted-foreground">Category</dt>
            <dd className="text-[10px] text-muted-foreground font-mono">{draft.categoryCode ?? '—'}</dd>
            <dt className="text-[10px] text-muted-foreground">Tags</dt>
            <dd className="text-[10px] text-muted-foreground">
              {draft.tags && draft.tags.length > 0 ? draft.tags.join(', ') : '—'}
            </dd>
          </dl>
        </div>
      )}

      {/* Icon action buttons */}
      <div className="flex gap-1.5">
        <Button
          variant="ghost"
          size="icon"
          className="h-[30px] w-[30px] rounded-md border border-border hover:bg-muted"
          asChild
          title="Edit draft"
        >
          <Link href={`/processes/new?draft=${draft.processKey}`}>
            <Pencil size={13} className="text-muted-foreground" strokeWidth={2} />
          </Link>
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="h-[30px] w-[30px] rounded-md border border-border text-destructive hover:bg-destructive/10 hover:text-destructive"
          onClick={onDelete}
          disabled={isDeleting}
          title="Delete draft"
        >
          <Trash2 size={13} strokeWidth={2} />
        </Button>
      </div>
    </div>
  )
}
