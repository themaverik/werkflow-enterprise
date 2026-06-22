'use client'

import Link from 'next/link'
import { Play, Pencil, Trash2, Link2, Workflow, Bell, FileText, GitBranch, History } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { type ProcessDef, getProcessTags, tagColor, primaryColorForProcess } from '../_utils/tagColors'

// Shared icon-button class (replaces the old `iconBtn` CSSProperties object)
const ICON_BTN = 'h-[30px] w-[30px] rounded-md border border-border'

interface DeployedCardProps {
  processKey: string
  latest: ProcessDef
  sortedVersions: ProcessDef[]
  canEdit: boolean
  isDeleting: boolean
  onDelete: () => void
}

export function DeployedCard({
  processKey,
  latest,
  sortedVersions,
  canEdit,
  isDeleting,
  onDelete,
}: DeployedCardProps) {
  const displayName = latest.name || processKey
  const tags = getProcessTags(latest)
  const primaryColor = primaryColorForProcess(tags)
  const hasForm = Boolean(latest.hasStartFormKey || latest.startFormKey)
  const hasDmn = Boolean(latest.hasDmn)
  const hasConnector = Boolean(latest.hasConnector)
  const hasNotification = Boolean(latest.hasNotification)

  return (
    <div className="bg-card border border-border rounded-xl p-5 flex flex-col gap-3 wf-card-interactive">
      {/* Top row: icon + name + version badge + tags */}
      <div className="flex gap-3 items-start">
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 11,
            background: primaryColor + '20',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <GitBranch size={20} color={primaryColor} strokeWidth={1.8} />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-sm font-semibold text-foreground overflow-hidden text-ellipsis whitespace-nowrap">
              {displayName}
            </span>
            <span
              style={{
                fontSize: 10,
                padding: '1px 6px',
                borderRadius: 99,
                background: primaryColor + '18',
                color: primaryColor,
                border: '1px solid ' + primaryColor + '40',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              v{latest.version}
            </span>
          </div>

          {tags.length > 0 && (
            <div className="flex gap-1 mt-1.5 flex-wrap">
              {tags.map((tag) => (
                <span
                  key={tag}
                  style={{
                    fontSize: 10,
                    padding: '2px 7px',
                    borderRadius: 99,
                    background: tagColor(tag) + '18',
                    color: tagColor(tag),
                    border: '1px solid ' + tagColor(tag) + '40',
                    fontWeight: 500,
                  }}
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Stats row */}
      <div className="flex gap-5">
        <div>
          <div className="text-xl font-bold text-foreground leading-none">0</div>
          <div className="text-[10px] text-muted-foreground mt-0.5">Active instances</div>
        </div>
        <div className="flex items-start gap-1">
          <div>
            <div className="text-xl font-bold text-foreground leading-none">{sortedVersions.length}</div>
            <div className="text-[10px] text-muted-foreground mt-0.5">Versions deployed</div>
          </div>
          {sortedVersions.length > 1 && (
            <span
              title={`Deployed versions: v${sortedVersions.map((v) => v.version).join(', v')}`}
              className="flex mt-[3px]"
            >
              <History size={11} className="text-muted-foreground" strokeWidth={2} />
            </span>
          )}
        </div>
      </div>

      {/* Process Custody */}
      {(latest.owningDepartment || latest.category) && (
        <div className="border-t border-border pt-2.5">
          <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-[0.06em] mb-1.5">
            Process Custody
          </div>
          <dl className="grid gap-y-[3px]" style={{ gridTemplateColumns: 'auto 1fr', columnGap: 8 }}>
            <dt className="text-[10px] text-muted-foreground">Department</dt>
            <dd className="text-[10px] text-muted-foreground font-mono">{latest.owningDepartment ?? '—'}</dd>
            <dt className="text-[10px] text-muted-foreground">Category</dt>
            <dd className="text-[10px] text-muted-foreground font-mono">{latest.category ?? '—'}</dd>
          </dl>
        </div>
      )}

      {/* Divider */}
      <div className="h-px bg-border" />

      {/* Action row: Start Process + icon buttons */}
      <div className="flex items-center gap-1.5">
        {/* Start Process */}
        <Button
          asChild
          size="sm"
          className="text-xs font-semibold"
          style={{ backgroundColor: primaryColor, borderColor: primaryColor }}
        >
          <Link href={`/processes/start/${processKey}`}>
            <Play size={11} strokeWidth={2.5} className="mr-1" />
            Start Process
          </Link>
        </Button>

        <div className="flex-1" />

        {/* Edit */}
        {canEdit ? (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} hover:bg-muted`}
            asChild
            title="Edit process"
          >
            <Link href={`/processes/edit/${latest.id}`}>
              <Pencil size={13} className="text-muted-foreground" strokeWidth={2} />
            </Link>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} opacity-30 cursor-not-allowed`}
            disabled
            title="No edit permission"
          >
            <Pencil size={13} className="text-muted-foreground" strokeWidth={2} />
          </Button>
        )}

        {/* Connector */}
        {hasConnector ? (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} hover:bg-muted`}
            asChild
            title="View connectors"
          >
            <Link href="/admin/connectors">
              <Link2 size={13} className="text-muted-foreground" strokeWidth={2} />
            </Link>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} opacity-30 cursor-not-allowed`}
            disabled
            title="No connectors configured"
          >
            <Link2 size={13} className="text-muted-foreground" strokeWidth={2} />
          </Button>
        )}

        {/* DMN */}
        {hasDmn ? (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} hover:bg-muted`}
            asChild
            title="View DMN decisions"
          >
            <Link href="/decisions">
              <Workflow size={13} className="text-muted-foreground" strokeWidth={2} />
            </Link>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} opacity-30 cursor-not-allowed`}
            disabled
            title="No DMN configured"
          >
            <Workflow size={13} className="text-muted-foreground" strokeWidth={2} />
          </Button>
        )}

        {/* Notification */}
        {hasNotification ? (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} hover:bg-muted`}
            asChild
            title="View notification templates"
          >
            <Link href="/admin/email-templates">
              <Bell size={13} className="text-muted-foreground" strokeWidth={2} />
            </Link>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} opacity-30 cursor-not-allowed`}
            disabled
            title="No notifications configured"
          >
            <Bell size={13} className="text-muted-foreground" strokeWidth={2} />
          </Button>
        )}

        {/* Form */}
        {hasForm ? (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} hover:bg-muted`}
            asChild
            title="Open start form"
          >
            <Link href={`/processes/start/${processKey}`}>
              <FileText size={13} className="text-muted-foreground" strokeWidth={2} />
            </Link>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} opacity-30 cursor-not-allowed`}
            disabled
            title="No form configured"
          >
            <FileText size={13} className="text-muted-foreground" strokeWidth={2} />
          </Button>
        )}

        {/* Delete */}
        {canEdit && (
          <Button
            variant="ghost"
            size="icon"
            className={`${ICON_BTN} text-destructive hover:bg-destructive/10 hover:text-destructive`}
            onClick={onDelete}
            disabled={isDeleting}
            title="Delete process"
          >
            <Trash2 size={13} strokeWidth={2} />
          </Button>
        )}
      </div>
    </div>
  )
}
