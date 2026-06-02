'use client'

import Link from 'next/link'
import { FileText, Plus, Trash2, Eye, Edit2, Search, CheckCircle, TrendingUp, Activity } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getFormDefinitions, deleteFormDefinition } from '@/lib/api/flowable'
import { useState, useMemo } from 'react'
import { useTranslations } from 'next-intl'
import { ErrorDisplay } from '@/components/ui/error-display'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { toast } from 'sonner'

// ─── Design tokens ────────────────────────────────────────────────────────────
const ACCENT = '#149ba5'
const T = {
  text: '#0f1e2a', muted: '#6b7e8c', light: '#94a3b8',
  bg: 'hsl(var(--muted))', card: 'hsl(var(--card))', border: 'hsl(var(--border))',
  success: '#16a34a', successBg: '#f0fdf4', successBorder: '#bbf7d0',
  warning: '#c27b00', warningBg: '#fffbeb', warningBorder: '#fde68a',
  danger: '#dc2626',
}

// ─── Role constants ───────────────────────────────────────────────────────────
const MANAGER_ROLES = [
  'ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER',
  'HR_ADMIN', 'FINANCE_ADMIN', 'PROCUREMENT_ADMIN', 'INVENTORY_ADMIN',
]

// ─── Types ────────────────────────────────────────────────────────────────────
interface FormDefinition {
  key: string
  name: string
  formJson: string
  owningDepartment?: string
  status?: string
  updatedAt?: string
}


// ─── Sub-components ───────────────────────────────────────────────────────────
function StatusPill({ status }: { status: string }) {
  const active = status === 'active'
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '3px 9px', borderRadius: 99, fontSize: 11, fontWeight: 600,
      background: active ? T.successBg : T.warningBg,
      color: active ? T.success : T.warning,
      border: '1px solid ' + (active ? T.successBorder : T.warningBorder),
    }}>
      {active ? 'Active' : 'Draft'}
    </span>
  )
}

function IconBtn({ children, onClick, title, danger = false }: {
  children: React.ReactNode
  onClick?: () => void
  title?: string
  danger?: boolean
}) {
  return (
    <button
      title={title}
      onClick={onClick}
      style={{
        width: 28, height: 28, borderRadius: 6, border: '1px solid ' + T.border,
        background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer', color: danger ? T.danger : T.muted,
        transition: 'border-color 0.15s, color 0.15s',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = danger ? T.danger : ACCENT
        e.currentTarget.style.color = danger ? T.danger : ACCENT
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = T.border
        e.currentTarget.style.color = danger ? T.danger : T.muted
      }}
    >
      {children}
    </button>
  )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function getFieldCount(formJson: string): number {
  try {
    return JSON.parse(formJson).components?.length ?? 0
  } catch {
    return 0
  }
}

function getFormStatus(form: FormDefinition): string {
  return form.status ?? 'active'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

// ─── Page ─────────────────────────────────────────────────────────────────────
export default function FormsPage() {
  const t = useTranslations('forms')
  const { status } = useSession()
  const [activeTab, setActiveTab] = useState<'all' | 'active' | 'draft'>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTag, setActiveTag] = useState<string | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<{
    title: string; description: string; onConfirm: () => void
  } | null>(null)
  const queryClient = useQueryClient()
  const { hasAnyRole, getDepartment } = useAuthorization()

  const isManagerOrAbove = hasAnyRole(MANAGER_ROLES)
  const userDepartment = getDepartment()

  const canEditForm = (form: FormDefinition) => {
    if (!isManagerOrAbove) return false
    if (!form.owningDepartment) return isManagerOrAbove
    return hasAnyRole(['ADMIN', 'SUPER_ADMIN']) || form.owningDepartment === userDepartment
  }

  // ── Data fetching ────────────────────────────────────────────────────────
  const { data: forms, isLoading, error, refetch } = useQuery({
    queryKey: ['formDefinitions'],
    queryFn: getFormDefinitions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteFormDefinition,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['formDefinitions'] })
      toast.success('Form deleted successfully')
    },
    onError: (err: Error) => {
      toast.error(`Failed to delete form: ${err.message}`)
    },
  })

  // ── Download ─────────────────────────────────────────────────────────────
  const handleDownload = (formKey: string, formJson: string) => {
    const blob = new Blob([formJson], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${formKey}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  // ── Derived stats ────────────────────────────────────────────────────────
  const allForms: FormDefinition[] = forms ?? []
  const activeForms = allForms.filter((f) => getFormStatus(f) === 'active')
  const draftForms  = allForms.filter((f) => getFormStatus(f) === 'draft')

  // ── Unique tags (departments) ────────────────────────────────────────────
  const tags = useMemo(() => {
    const set = new Set<string>()
    allForms.forEach((f) => { if (f.owningDepartment) set.add(f.owningDepartment) })
    return Array.from(set)
  }, [allForms])

  // ── Filtered rows ────────────────────────────────────────────────────────
  const visibleForms = useMemo(() => {
    let list = allForms
    if (activeTab === 'active') list = activeForms
    if (activeTab === 'draft')  list = draftForms
    if (activeTag) list = list.filter((f) => f.owningDepartment === activeTag)
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase()
      list = list.filter((f) => f.name.toLowerCase().includes(q))
    }
    return list
  }, [allForms, activeTab, activeTag, searchQuery, activeForms, draftForms])

  // ─── Styles ───────────────────────────────────────────────────────────────
  const tabBase: React.CSSProperties = {
    padding: '7px 16px', borderRadius: 8, fontSize: 13, fontWeight: 500,
    cursor: 'pointer', fontFamily: 'inherit',
  }
  const tabActive: React.CSSProperties = {
    ...tabBase,
    border: '1.5px solid ' + ACCENT,
    background: ACCENT + '14',
    color: ACCENT,
  }
  const tabInactive: React.CSSProperties = {
    ...tabBase,
    border: '1px solid ' + T.border,
    background: '#fff',
    color: T.muted,
  }

  const headerCell: React.CSSProperties = {
    padding: '10px 20px',
    background: T.bg,
    borderBottom: '1px solid ' + T.border,
    fontSize: 11, fontWeight: 600,
    color: T.muted,
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    textAlign: 'left',
  }

  const GRID = '2fr 1.4fr 80px 100px 110px 120px'

  return (
    <div style={{ padding: 28, fontFamily: 'inherit' }}>

      {/* ── Page header ───────────────────────────────────────────────────── */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: T.text, margin: 0 }}>{t('title')}</h1>
        <p style={{ fontSize: 13, color: T.muted, marginTop: 4 }}>{t('subtitle')}</p>
      </div>

      {/* ── Loading / error ────────────────────────────────────────────────── */}
      {isLoading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="rounded-xl border border-border bg-card p-4 space-y-3">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-3 w-1/2" />
              <div className="flex gap-2">
                <Skeleton className="h-5 w-14 rounded-full" />
                <Skeleton className="h-5 w-18 rounded-full" />
              </div>
              <Skeleton className="h-7 w-full rounded-md" />
            </div>
          ))}
        </div>
      )}
      {error && !isLoading && (
        <ErrorDisplay
          error={error as Error}
          onRetry={() => refetch()}
          title={t('failedToLoad')}
          className="mb-6"
        />
      )}

      {/* ── Stat cards ────────────────────────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14, marginBottom: 24 }}>
        {[
          { label: 'Total Forms',       value: allForms.length,        color: ACCENT,      Icon: FileText      },
          { label: 'Active Forms',      value: activeForms.length,     color: T.success,   Icon: CheckCircle   },
          { label: 'Drafts',            value: draftForms.length,      color: T.warning,   Icon: Activity      },
          { label: 'Submissions (30d)', value: 0,                      color: '#7c3aed',   Icon: TrendingUp    },
        ].map(({ label, value, color, Icon }) => (
          <div key={label} style={{
            background: T.card, border: '1px solid ' + T.border, borderRadius: 12,
            padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 14,
          }}>
            <div style={{
              width: 40, height: 40, borderRadius: 10, background: color + '18',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon size={20} strokeWidth={1.8} style={{ color }} />
            </div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 700, color: T.text, lineHeight: 1 }}>{value}</div>
              <div style={{ fontSize: 12, color: T.muted, marginTop: 3 }}>{label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* ── Tabs + New Form button ─────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <div style={{ display: 'flex', gap: 8 }}>
          {([
            ['all',    'All Forms'],
            ['active', `Active (${activeForms.length})`],
            ['draft',  `Drafts (${draftForms.length})`],
          ] as const).map(([key, label]) => (
            <button key={key} style={activeTab === key ? tabActive : tabInactive} onClick={() => setActiveTab(key)}>
              {label}
            </button>
          ))}
        </div>
        {isManagerOrAbove && (
          <Link
            href="/forms/new"
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '8px 16px', borderRadius: 8, fontSize: 13, fontWeight: 600,
              background: ACCENT, color: '#fff', textDecoration: 'none',
              border: 'none', cursor: 'pointer',
            }}
          >
            <Plus size={15} strokeWidth={2.2} />
            {t('createNewForm')}
          </Link>
        )}
      </div>

      {/* ── Search + tag filter ────────────────────────────────────────────── */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16,
        background: T.card, border: '1px solid ' + T.border, borderRadius: 10, padding: '8px 14px',
      }}>
        <Search size={16} style={{ color: T.light, flexShrink: 0 }} />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search forms…"
          style={{
            flex: '0 0 220px', border: 'none', outline: 'none', fontSize: 13,
            color: T.text, background: 'transparent', fontFamily: 'inherit',
          }}
        />
        <div style={{
          flex: 1, display: 'flex', gap: 6, overflowX: 'auto', paddingBottom: 2,
        }}>
          <button
            onClick={() => setActiveTag(null)}
            style={{
              flexShrink: 0, padding: '3px 10px', borderRadius: 99, fontSize: 11, fontWeight: 500,
              cursor: 'pointer', fontFamily: 'inherit',
              background: activeTag === null ? ACCENT : T.bg,
              color: activeTag === null ? '#fff' : T.muted,
              border: '1px solid ' + (activeTag === null ? ACCENT : T.border),
            }}
          >
            All
          </button>
          {tags.map((tag) => (
            <button
              key={tag}
              onClick={() => setActiveTag(activeTag === tag ? null : tag)}
              style={{
                flexShrink: 0, padding: '3px 10px', borderRadius: 99, fontSize: 11, fontWeight: 500,
                cursor: 'pointer', fontFamily: 'inherit',
                background: activeTag === tag ? ACCENT : T.bg,
                color: activeTag === tag ? '#fff' : T.muted,
                border: '1px solid ' + (activeTag === tag ? ACCENT : T.border),
              }}
            >
              {tag}
            </button>
          ))}
        </div>
      </div>

      {/* ── Table ──────────────────────────────────────────────────────────── */}
      <div style={{
        background: T.card, border: '1px solid ' + T.border, borderRadius: 12, overflow: 'hidden',
        marginBottom: 32,
      }}>
        {/* Header */}
        <div style={{ display: 'grid', gridTemplateColumns: GRID }}>
          {['Form', 'Process', 'Fields', 'Status', 'Updated', 'Actions'].map((col) => (
            <div key={col} style={headerCell}>{col}</div>
          ))}
        </div>

        {/* Rows */}
        {visibleForms.map((form, idx) => {
          const formStatus = getFormStatus(form)
          const fieldCount = getFieldCount(form.formJson)
          const editable   = canEditForm(form)
          const isLast     = idx === visibleForms.length - 1

          return (
            <div
              key={form.key}
              style={{
                display: 'grid', gridTemplateColumns: GRID,
                alignItems: 'center',
                borderBottom: isLast ? 'none' : '1px solid ' + T.border,
                transition: 'background 0.1s',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = T.bg }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
            >
              {/* Form name + tag */}
              <div style={{ padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{
                  width: 36, height: 36, borderRadius: 8, flexShrink: 0,
                  background: ACCENT + '18',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <FileText size={18} strokeWidth={1.8} style={{ color: ACCENT }} />
                </div>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: T.text }}>{form.name}</div>
                  {form.owningDepartment && (
                    <div style={{ fontSize: 11, color: T.muted, marginTop: 2 }}>{form.owningDepartment}</div>
                  )}
                </div>
              </div>

              {/* Process key */}
              <div style={{ padding: '12px 20px', fontSize: 12, color: T.muted, fontFamily: 'monospace' }}>
                {form.key}
              </div>

              {/* Fields count */}
              <div style={{ padding: '12px 20px', fontSize: 13, color: T.text, fontWeight: 500 }}>
                {fieldCount}
              </div>

              {/* Status */}
              <div style={{ padding: '12px 20px' }}>
                <StatusPill status={formStatus} />
              </div>

              {/* Updated */}
              <div style={{ padding: '12px 20px', fontSize: 12, color: T.muted }}>
                {formatDate(form.updatedAt)}
              </div>

              {/* Actions */}
              <div style={{ padding: '12px 20px', display: 'flex', gap: 6, alignItems: 'center' }}>
                <Link href={`/forms/preview/${form.key}`} title="Preview">
                  <IconBtn title="Preview">
                    <Eye size={14} />
                  </IconBtn>
                </Link>
                {editable && (
                  <>
                    <Link href={`/forms/edit/${form.key}`} title="Edit" aria-label="Edit">
                      <IconBtn title="Edit">
                        <Edit2 size={14} />
                      </IconBtn>
                    </Link>
                    <IconBtn
                      title="Delete"
                      danger
                      onClick={() =>
                        setPendingConfirm({
                          title: t('deleteForm'),
                          description: `Delete "${form.name}"? This cannot be undone.`,
                          onConfirm: () => deleteMutation.mutate(form.key),
                        })
                      }
                    >
                      <Trash2 size={14} />
                    </IconBtn>
                  </>
                )}
              </div>
            </div>
          )
        })}

        {/* Empty state */}
        {!isLoading && visibleForms.length === 0 && (
          <div style={{
            padding: '64px 20px',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            textAlign: 'center',
          }}>
            <FileText size={40} strokeWidth={1.5} style={{ color: T.light, marginBottom: 12 }} />
            <div style={{ fontSize: 13, fontWeight: 600, color: T.text, marginBottom: 16 }}>Create a form to start a process</div>
            {isManagerOrAbove && (
              <Link
                href="/forms/new"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '6px 14px',
                  borderRadius: 8,
                  fontSize: 13,
                  fontWeight: 600,
                  color: ACCENT,
                  border: '1px solid ' + ACCENT,
                  textDecoration: 'none',
                  background: 'transparent',
                }}
              >
                <Plus size={13} strokeWidth={2} />
                Create your first form
              </Link>
            )}
          </div>
        )}
      </div>


      {/* ── Confirm dialog ────────────────────────────────────────────────── */}
      {pendingConfirm && (
        <ConfirmDialog
          open={true}
          onOpenChange={(open) => { if (!open) setPendingConfirm(null) }}
          title={pendingConfirm.title}
          description={pendingConfirm.description}
          confirmLabel={t('delete')}
          onConfirm={pendingConfirm.onConfirm}
        />
      )}
    </div>
  )
}
