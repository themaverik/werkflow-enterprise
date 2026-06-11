'use client'

import { useState, useMemo } from 'react'
import Link from 'next/link'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useTranslations } from 'next-intl'
import {
  Plus, Trash2, Edit2, Play, ChevronDown, ChevronUp,
  Search, Activity, CheckCircle, FileText, Grid3X3,
} from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { listDecisions, deleteDeployment, type DmnDecisionDto } from '@/lib/api/dmn'
import { FilterPills } from '@/components/ui/filter-pills'

// ---------------------------------------------------------------------------
// Design tokens
// ---------------------------------------------------------------------------
const ACCENT = '#149ba5'
const T = {
  text: '#0f1e2a',
  muted: '#6b7e8c',
  light: '#94a3b8',
  bg: '#f0f4f6',
  card: '#ffffff',
  border: '#e2eaee',
  blue: '#1d4ed8',
  blueBg: '#eff6ff',
  blueBorder: '#bfdbfe',
  success: '#16a34a',
  successBg: '#f0fdf4',
  successBorder: '#bbf7d0',
  warning: '#c27b00',
  warningBg: '#fffbeb',
  warningBorder: '#fde68a',
  purple: '#7c3aed',
  purpleBg: '#f5f3ff',
  purpleBorder: '#ddd6fe',
  danger: '#dc2626',
}

const HIT_POLICY_META: Record<string, { bg: string; color: string; border: string }> = {
  FIRST:        { bg: T.blueBg,    color: T.blue,    border: T.blueBorder },
  UNIQUE:       { bg: T.successBg, color: T.success, border: T.successBorder },
  COLLECT:      { bg: T.purpleBg,  color: T.purple,  border: T.purpleBorder },
  'RULE ORDER': { bg: '#fff7ed',   color: '#c2410c', border: '#fed7aa' },
}

const ADMIN_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER']

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function extractTag(category?: string): string {
  if (!category) return ''
  try {
    const url = new URL(category)
    const parts = url.pathname.split('/').filter(Boolean)
    const last = parts[parts.length - 1] ?? ''
    return last.charAt(0).toUpperCase() + last.slice(1)
  } catch {
    return category
  }
}

function deriveStatus(d: DmnDecisionDto): 'Active' | 'Draft' {
  return d.key ? 'Active' : 'Draft'
}

function mockRulesCount(d: DmnDecisionDto): number {
  // Deterministic mock based on id length until API provides rules count
  return (d.id?.length ?? 4) % 8 + 2
}

// ---------------------------------------------------------------------------
// Sub-components (inline, no external deps)
// ---------------------------------------------------------------------------
function StatCard({
  icon: Icon,
  label,
  value,
  color,
}: {
  icon: React.ElementType
  label: string
  value: number
  color: string
}) {
  return (
    <div
      style={{
        background: T.card,
        border: '1px solid ' + T.border,
        borderRadius: 12,
        padding: '16px 20px',
        display: 'flex',
        alignItems: 'center',
        gap: 14,
      }}
    >
      <div
        style={{
          width: 40,
          height: 40,
          borderRadius: 10,
          background: color + '18',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <Icon size={20} strokeWidth={1.8} style={{ color }} />
      </div>
      <div>
        <div style={{ fontSize: 22, fontWeight: 700, color: T.text, lineHeight: 1 }}>{value}</div>
        <div style={{ fontSize: 12, color: T.muted, marginTop: 3 }}>{label}</div>
      </div>
    </div>
  )
}

function TypeBadge({ type }: { type?: string }) {
  const isTable = type === 'Decision Table'
  return (
    <span
      style={{
        display: 'inline-block',
        fontSize: 11,
        fontWeight: 600,
        padding: '2px 8px',
        borderRadius: 6,
        background: isTable ? T.blueBg : T.purpleBg,
        color: isTable ? T.blue : T.purple,
        border: '1px solid ' + (isTable ? T.blueBorder : T.purpleBorder),
        whiteSpace: 'nowrap',
      }}
    >
      {type ?? 'Expression'}
    </span>
  )
}

function HitPolicyBadge({ policy }: { policy?: string }) {
  if (!policy) return <span style={{ color: T.light, fontSize: 12 }}>—</span>
  const meta = HIT_POLICY_META[policy.toUpperCase()] ?? {
    bg: T.bg, color: T.muted, border: T.border,
  }
  return (
    <span
      style={{
        display: 'inline-block',
        fontSize: 11,
        fontWeight: 600,
        padding: '2px 8px',
        borderRadius: 6,
        background: meta.bg,
        color: meta.color,
        border: '1px solid ' + meta.border,
        whiteSpace: 'nowrap',
      }}
    >
      {policy.toUpperCase()}
    </span>
  )
}

function StatusPill({ status }: { status: 'Active' | 'Draft' }) {
  const isActive = status === 'Active'
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        fontSize: 12,
        fontWeight: 600,
        padding: '3px 10px',
        borderRadius: 20,
        background: isActive ? T.successBg : T.warningBg,
        color: isActive ? T.success : T.warning,
        border: '1px solid ' + (isActive ? T.successBorder : T.warningBorder),
      }}
    >
      <span
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: isActive ? T.success : T.warning,
        }}
      />
      {status}
    </span>
  )
}

function IconBtn({
  onClick,
  label,
  color,
  children,
}: {
  onClick?: () => void
  label: string
  color?: string
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      style={{
        width: 30,
        height: 30,
        borderRadius: 8,
        border: '1px solid ' + T.border,
        background: T.card,
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: color ?? T.muted,
        transition: 'background 0.15s',
        flexShrink: 0,
      }}
      onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.background = T.bg }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.background = T.card }}
    >
      {children}
    </button>
  )
}

function ExpandedPreview({ decision }: { decision: DmnDecisionDto }) {
  const isTable = !decision.hitPolicy || decision.hitPolicy !== 'collect'
  return (
    <div
      style={{
        borderTop: '1px solid ' + T.border,
        padding: '20px 24px',
        background: T.bg,
      }}
    >
      {isTable ? (
        <div>
          <div style={{ fontSize: 12, fontWeight: 600, color: T.muted, marginBottom: 12, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Decision Table Preview
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead>
                <tr>
                  {['#', 'Input: Amount', 'Input: Category', 'Output: Approval', 'Output: Limit'].map((col, i) => (
                    <th
                      key={i}
                      style={{
                        padding: '8px 12px',
                        background: i < 3 ? T.blueBg : T.successBg,
                        color: i < 3 ? T.blue : T.success,
                        border: '1px solid ' + (i < 3 ? T.blueBorder : T.successBorder),
                        fontWeight: 600,
                        textAlign: 'left',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  ['1', '< 10000', '"standard"', '"AUTO_APPROVE"', '10000'],
                  ['2', '>= 10000', '"premium"', '"MANUAL_REVIEW"', '50000'],
                  ['3', '-', '-', '"REJECT"', '0'],
                ].map((row, ri) => (
                  <tr key={ri} style={{ background: ri % 2 === 0 ? T.card : T.bg }}>
                    {row.map((cell, ci) => (
                      <td
                        key={ci}
                        style={{
                          padding: '7px 12px',
                          border: '1px solid ' + T.border,
                          fontFamily: ci > 0 ? 'monospace' : undefined,
                          fontSize: ci > 0 ? 12 : 13,
                          color: T.text,
                        }}
                      >
                        {cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p style={{ fontSize: 12, color: T.muted, marginTop: 10 }}>
            This is a preview. Open in editor to view and edit the full decision table.
          </p>
        </div>
      ) : (
        <div>
          <div style={{ fontSize: 12, fontWeight: 600, color: T.muted, marginBottom: 12, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Expression Preview
          </div>
          <pre
            style={{
              background: '#1e293b',
              color: '#e2e8f0',
              padding: '14px 18px',
              borderRadius: 8,
              fontSize: 13,
              lineHeight: 1.6,
              overflow: 'auto',
              margin: 0,
            }}
          >
{`// FEEL Expression — ${decision.name}
if amount < 10000 then
  "AUTO_APPROVE"
else if category = "premium" then
  "MANUAL_REVIEW"
else
  "REJECT"`}
          </pre>
          <p style={{ fontSize: 12, color: T.muted, marginTop: 10 }}>
            Open in editor to view and edit the full FEEL expression.
          </p>
        </div>
      )}
    </div>
  )
}

function DecisionRow({
  decision,
  canManage,
  onDelete,
}: {
  decision: DmnDecisionDto
  canManage: boolean
  onDelete: (d: DmnDecisionDto) => void
}) {
  const [isOpen, setIsOpen] = useState(false)
  const status = deriveStatus(decision)
  const tag = extractTag(decision.key)
  const rules = mockRulesCount(decision)

  return (
    <div
      style={{
        background: T.card,
        border: '1px solid ' + (isOpen ? ACCENT + '55' : T.border),
        borderRadius: 12,
        overflow: 'hidden',
        transition: 'border-color 0.2s',
      }}
    >
      {/* Main row */}
      <div
        onClick={() => setIsOpen((v) => !v)}
        style={{
          display: 'grid',
          gridTemplateColumns: '2fr 1.4fr 140px 100px 90px 110px 130px',
          padding: '16px 20px',
          alignItems: 'center',
          cursor: 'pointer',
          gap: 8,
        }}
      >
        {/* Name + tags */}
        <div>
          <div style={{ fontWeight: 600, fontSize: 14, color: T.text, marginBottom: tag ? 4 : 0 }}>
            {decision.name}
          </div>
          {tag && (
            <span
              style={{
                display: 'inline-block',
                fontSize: 11,
                fontWeight: 500,
                padding: '1px 7px',
                borderRadius: 4,
                background: ACCENT + '15',
                color: ACCENT,
                border: '1px solid ' + ACCENT + '30',
              }}
            >
              {tag}
            </span>
          )}
          <div style={{ fontSize: 11, color: T.light, marginTop: 3, fontFamily: 'monospace' }}>
            {decision.key}
          </div>
        </div>

        {/* Process (using key as process ref) */}
        <div style={{ fontSize: 13, color: T.muted, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {decision.key ?? '—'}
        </div>

        {/* Type */}
        <div><TypeBadge type="Decision Table" /></div>

        {/* Hit Policy */}
        <div><HitPolicyBadge policy={decision.hitPolicy} /></div>

        {/* Rules count */}
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
          <span style={{ fontSize: 18, fontWeight: 700, color: T.text }}>{rules}</span>
          <span style={{ fontSize: 11, color: T.light }}>rules</span>
        </div>

        {/* Status */}
        <div><StatusPill status={status} /></div>

        {/* Actions */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 6, justifyContent: 'flex-end' }}
          onClick={(e) => e.stopPropagation()}
        >
          <IconBtn label="Test decision" color={ACCENT}>
            <Play size={13} />
          </IconBtn>
          <Link href={`/decisions/${decision.key}/edit`} style={{ textDecoration: 'none' }} aria-label="Edit decision">
            <IconBtn label="Edit decision">
              <Edit2 size={13} />
            </IconBtn>
          </Link>
          {canManage && (
            <IconBtn label="Delete decision" color={T.danger} onClick={() => onDelete(decision)}>
              <Trash2 size={13} />
            </IconBtn>
          )}
          <button
            aria-label={isOpen ? 'Collapse' : 'Expand'}
            onClick={(e) => { e.stopPropagation(); setIsOpen((v) => !v) }}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: 4,
              color: T.muted,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            {isOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>
        </div>
      </div>

      {/* Expanded preview */}
      {isOpen && <ExpandedPreview decision={decision} />}
    </div>
  )
}

function ColumnHeader({ label, flex }: { label: string; flex?: string }) {
  return (
    <div
      style={{
        fontSize: 11,
        fontWeight: 700,
        color: T.muted,
        textTransform: 'uppercase',
        letterSpacing: '0.06em',
        flex,
      }}
    >
      {label}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------
export default function DecisionsPage() {
  const t = useTranslations('decisions')
  const { status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const queryClient = useQueryClient()
  const [pendingDelete, setPendingDelete] = useState<DmnDecisionDto | null>(null)
  const [activeTab, setActiveTab] = useState<'all' | 'active' | 'drafts'>('all')
  const [search, setSearch] = useState('')
  const [activeTag, setActiveTag] = useState<string | null>(null)

  const canManage = hasAnyRole(ADMIN_ROLES)

  const { data: decisions, isLoading, error, refetch } = useQuery({
    queryKey: ['dmnDecisions'],
    queryFn: listDecisions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
    staleTime: 60_000,
  })

  const deleteMutation = useMutation({
    mutationFn: (deploymentId: string) => deleteDeployment(deploymentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dmnDecisions'] })
      setPendingDelete(null)
      toast.success(t('list.deleted'))
    },
    onError: (err: Error) => {
      toast.error(t('list.deleteFailed'), { description: err.message })
      setPendingDelete(null)
    },
  })

  const all = decisions ?? []

  // Stat counts
  const totalCount = all.length
  const activeCount = all.filter((d) => deriveStatus(d) === 'Active').length
  const draftCount = all.filter((d) => deriveStatus(d) === 'Draft').length
  const tableCount = all.length

  // All unique tags
  const allTags = useMemo(() => {
    const set = new Set<string>()
    all.forEach((d) => { const t = extractTag(d.key); if (t) set.add(t) })
    return Array.from(set).sort()
  }, [all])

  // Filtered list
  const filtered = useMemo(() => {
    let list = all
    if (activeTab === 'active') list = list.filter((d) => deriveStatus(d) === 'Active')
    if (activeTab === 'drafts') list = list.filter((d) => deriveStatus(d) === 'Draft')
    if (activeTag) list = list.filter((d) => extractTag(d.key) === activeTag)
    if (search.trim()) {
      const q = search.trim().toLowerCase()
      list = list.filter(
        (d) =>
          d.name?.toLowerCase().includes(q) ||
          d.key?.toLowerCase().includes(q) ||
          d.tenantId?.toLowerCase().includes(q),
      )
    }
    return list
  }, [all, activeTab, activeTag, search])

  return (
    <div style={{ padding: 28 }}>
      {/* Page header */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: T.text, margin: 0 }}>
          {t('title')}
        </h1>
        <p style={{ fontSize: 14, color: T.muted, marginTop: 4 }}>
          {t('list.subtitle')}
        </p>
      </div>

      {/* Stat cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: 16,
          marginBottom: 24,
        }}
      >
        <StatCard icon={FileText}   label="Total Decisions"   value={totalCount}  color={ACCENT} />
        <StatCard icon={CheckCircle} label="Active"            value={activeCount} color={T.success} />
        <StatCard icon={Activity}    label="Drafts"            value={draftCount}  color={T.warning} />
        <StatCard icon={Grid3X3}     label="Decision Tables"   value={tableCount}  color={T.blue} />
      </div>

      {/* Tabs + New Decision button */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', gap: 6 }}>
          {(
            [
              { key: 'all',    label: 'All' },
              { key: 'active', label: `Active (${activeCount})` },
              { key: 'drafts', label: `Drafts (${draftCount})` },
            ] as const
          ).map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              style={{
                padding: '7px 16px',
                borderRadius: 8,
                border: '1px solid ' + (activeTab === key ? ACCENT : T.border),
                background: activeTab === key ? ACCENT + '12' : T.card,
                color: activeTab === key ? ACCENT : T.muted,
                fontWeight: activeTab === key ? 700 : 500,
                fontSize: 13,
                cursor: 'pointer',
                transition: 'all 0.15s',
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {canManage && (
          <Link href="/decisions/new" style={{ textDecoration: 'none' }}>
            <button
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 18px',
                borderRadius: 8,
                border: 'none',
                background: ACCENT,
                color: '#fff',
                fontWeight: 600,
                fontSize: 14,
                cursor: 'pointer',
                transition: 'opacity 0.15s',
              }}
              onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.opacity = '0.88' }}
              onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.opacity = '1' }}
            >
              <Plus size={15} />
              {t('list.create')}
            </button>
          </Link>
        )}
      </div>

      {/* Search + tag filter bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 20,
          flexWrap: 'wrap',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            background: T.card,
            border: '1px solid ' + T.border,
            borderRadius: 8,
            padding: '7px 12px',
            flex: '0 0 280px',
          }}
        >
          <Search size={14} style={{ color: T.light, flexShrink: 0 }} />
          <input
            type="text"
            placeholder="Search decisions..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              border: 'none',
              outline: 'none',
              background: 'transparent',
              fontSize: 13,
              color: T.text,
              width: '100%',
            }}
          />
        </div>

        {/* Tag pills */}
        {allTags.length > 0 && (
          <FilterPills
            options={[{ key: '', label: 'All' }, ...allTags.map((tag) => ({ key: tag, label: tag }))]}
            active={activeTag ?? ''}
            onChange={(key) => setActiveTag(key === '' ? null : key)}
          />
        )}
      </div>

      {/* Column headers */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '2fr 1.4fr 140px 100px 90px 110px 130px',
          padding: '0 20px 10px',
          gap: 8,
        }}
      >
        <ColumnHeader label="Name" />
        <ColumnHeader label="Process" />
        <ColumnHeader label="Type" />
        <ColumnHeader label="Hit Policy" />
        <ColumnHeader label="Rules" />
        <ColumnHeader label="Status" />
        <ColumnHeader label="Actions" />
      </div>

      {/* Decision rows */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {isLoading && (
          Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              style={{
                height: 64,
                borderRadius: 12,
                background: T.card,
                border: '1px solid ' + T.border,
                animation: 'pulse 1.5s ease-in-out infinite',
                opacity: 0.6,
              }}
            />
          ))
        )}

        {error && !isLoading && (
          <div
            style={{
              padding: '32px 20px',
              textAlign: 'center',
              color: T.danger,
              background: T.card,
              border: '1px solid ' + T.border,
              borderRadius: 12,
            }}
          >
            <p style={{ fontWeight: 600, marginBottom: 8 }}>Failed to load decisions</p>
            <button
              onClick={() => refetch()}
              style={{
                padding: '6px 16px',
                borderRadius: 8,
                border: '1px solid ' + T.danger,
                background: 'transparent',
                color: T.danger,
                cursor: 'pointer',
                fontSize: 13,
              }}
            >
              Retry
            </button>
          </div>
        )}

        {!isLoading && !error && filtered.length === 0 && (
          <div
            style={{
              padding: '48px 20px',
              textAlign: 'center',
              color: T.muted,
              background: T.card,
              border: '1px solid ' + T.border,
              borderRadius: 12,
            }}
          >
            <FileText size={36} style={{ color: T.light, margin: '0 auto 12px' }} />
            <p style={{ fontWeight: 600, color: T.text, marginBottom: 4 }}>No decisions found</p>
            <p style={{ fontSize: 13 }}>
              {search || activeTag
                ? 'Try adjusting your search or filters.'
                : 'Create your first decision to get started.'}
            </p>
          </div>
        )}

        {!isLoading &&
          filtered.map((decision) => (
            <DecisionRow
              key={decision.id}
              decision={decision}
              canManage={canManage}
              onDelete={setPendingDelete}
            />
          ))}
      </div>

      {/* Delete confirmation */}
      {pendingDelete && (
        <ConfirmDialog
          open
          onOpenChange={(open) => { if (!open) setPendingDelete(null) }}
          title={t('list.confirmDelete')}
          description={t('list.confirmDeleteDesc', { name: pendingDelete.name })}
          confirmLabel={t('list.deleteButton')}
          variant="destructive"
          onConfirm={() => deleteMutation.mutate(pendingDelete.deploymentId)}
        />
      )}
    </div>
  )
}
