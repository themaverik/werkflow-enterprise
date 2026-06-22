'use client'

import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { PageSurface } from '@/components/layout/page-surface'
import { Plus, Pencil, Trash2, RefreshCw, Mail, Search, LayoutTemplate } from 'lucide-react'
import { toast } from 'sonner'
import { listEmailTemplatesFull, deleteEmailTemplate, type EmailTemplateResponse } from '@/lib/api/email-templates'

// ─── Design tokens (mirrors /forms/page.tsx) ──────────────────────────────────
const ACCENT = '#149ba5'
const T = {
  text: '#0f1e2a', muted: '#6b7e8c', light: '#94a3b8',
  bg: 'hsl(var(--muted))', card: 'hsl(var(--card))', border: 'hsl(var(--border))',
  danger: '#dc2626',
}

// ─── Tag badge (preserved from original) ─────────────────────────────────────
const TAG_COLORS: Record<string, { bg: string; color: string; border: string }> = {
  Approval:     { bg: '#e6f7f8', color: ACCENT,    border: '#a8dde0' },
  Notification: { bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe' },
  Action:       { bg: '#fff7ed', color: '#c2410c', border: '#fed7aa' },
  Procurement:  { bg: '#fef9c3', color: '#854d0e', border: '#fde68a' },
  HR:           { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
  Onboarding:   { bg: '#f5f3ff', color: '#7c3aed', border: '#ddd6fe' },
  default:      { bg: '#f1f5f9', color: '#475569', border: '#e2e8f0' },
}

function TagBadge({ tag }: { tag: string }) {
  const c = TAG_COLORS[tag] ?? TAG_COLORS.default
  return (
    <span style={{ fontSize: 10, fontWeight: 600, padding: '2px 7px', borderRadius: 4, background: c.bg, color: c.color, border: `1px solid ${c.border}` }}>
      {tag}
    </span>
  )
}

// ─── Icon button (mirrors /forms/page.tsx IconBtn) ────────────────────────────
function IconBtn({ children, onClick, title, danger = false }: {
  children: React.ReactNode
  onClick?: () => void
  title?: string
  danger?: boolean
}) {
  return (
    <button
      title={title}
      aria-label={title}
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
function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

function channelLabel(channel: string): string {
  if (channel === 'email') return 'Notification'
  return channel.charAt(0).toUpperCase() + channel.slice(1)
}

// ─── Page ─────────────────────────────────────────────────────────────────────
export default function EmailTemplatesPage() {
  const { status } = useSession()
  const queryClient = useQueryClient()
  const router = useRouter()
  const [deleteTarget, setDeleteTarget] = useState<EmailTemplateResponse | null>(null)
  const [searchQuery, setSearchQuery] = useState('')

  const { data: templates, isLoading, error, refetch } = useQuery({
    queryKey: ['emailTemplates'],
    queryFn: listEmailTemplatesFull,
    enabled: status === 'authenticated',
  })

  const deleteMutation = useMutation({
    mutationFn: (key: string) => deleteEmailTemplate(key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['emailTemplates'] })
      queryClient.invalidateQueries({ queryKey: ['notificationTemplates'] })
      toast.success('Template deleted')
      setDeleteTarget(null)
    },
    onError: () => toast.error('Failed to delete template'),
  })

  const allTemplates: EmailTemplateResponse[] = templates ?? []

  const visibleTemplates = useMemo(() => {
    if (!searchQuery.trim()) return allTemplates
    const q = searchQuery.trim().toLowerCase()
    return allTemplates.filter(
      (t) => t.name.toLowerCase().includes(q) || t.key.toLowerCase().includes(q)
    )
  }, [allTemplates, searchQuery])

  // ─── Styles ───────────────────────────────────────────────────────────────
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

  const GRID = '2fr 1.4fr 120px 110px 100px'

  return (
    <PageSurface>

      {/* ── Page header ───────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, color: T.text, margin: 0 }}>Email Templates</h1>
          <p style={{ fontSize: 13, color: T.muted, marginTop: 4 }}>Manage transactional email templates used in workflow notifications.</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isLoading}>
            <RefreshCw size={14} className={`mr-1.5 ${isLoading ? 'animate-spin' : ''}`} strokeWidth={1.8} />
            Refresh
          </Button>
          <Button asChild size="sm">
            <Link href="/admin/email-templates/new">
              <Plus size={14} className="mr-1.5" strokeWidth={1.8} />
              New Template
            </Link>
          </Button>
        </div>
      </div>

      {/* ── Stat cards ────────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 14, marginBottom: 24 }}>
        <div style={{
          maxWidth: 320,
          background: T.card, border: '1px solid ' + T.border, borderRadius: 12,
          padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 14,
        }}>
          <div style={{
            width: 40, height: 40, borderRadius: 10, background: ACCENT + '18',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <LayoutTemplate size={20} strokeWidth={1.8} style={{ color: ACCENT }} />
          </div>
          <div>
            <div style={{ fontSize: 22, fontWeight: 700, color: T.text, lineHeight: 1 }}>{allTemplates.length}</div>
            <div style={{ fontSize: 12, color: T.muted, marginTop: 3 }}>Total Templates</div>
          </div>
        </div>
      </div>

      {/* ── Search bar ────────────────────────────────────────────────────── */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16,
        background: T.card, border: '1px solid ' + T.border, borderRadius: 10, padding: '8px 14px',
      }}>
        <Search size={16} style={{ color: T.light, flexShrink: 0 }} />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search templates…"
          style={{
            flex: '0 0 220px', border: 'none', outline: 'none', fontSize: 13,
            color: T.text, background: 'transparent', fontFamily: 'inherit',
          }}
        />
      </div>

      {/* ── Error ─────────────────────────────────────────────────────────── */}
      {error && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive" style={{ marginBottom: 16 }}>
          Failed to load templates. Please try again.
        </div>
      )}

      {/* ── Loading skeleton ───────────────────────────────────────────────── */}
      {isLoading && (
        <div className="space-y-2">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="rounded-xl border border-border bg-card p-4 space-y-3">
              <div className="flex items-center justify-between">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-7 w-16 rounded-md" />
              </div>
              <Skeleton className="h-3 w-56" />
            </div>
          ))}
        </div>
      )}

      {/* ── Empty state ────────────────────────────────────────────────────── */}
      {!isLoading && allTemplates.length === 0 && (
        <div style={{
          background: T.card, border: '1px solid ' + T.border, borderRadius: 12,
          padding: '64px 20px', display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center', textAlign: 'center',
        }}>
          <Mail size={40} strokeWidth={1.5} style={{ color: T.light, marginBottom: 12 }} />
          <div style={{ fontSize: 13, fontWeight: 600, color: T.text, marginBottom: 4 }}>No email templates yet</div>
          <Link href="/admin/email-templates/new" style={{ fontSize: 13, fontWeight: 500, color: ACCENT, textDecoration: 'none' }}>
            Create the first one →
          </Link>
        </div>
      )}

      {/* ── Table ──────────────────────────────────────────────────────────── */}
      {!isLoading && allTemplates.length > 0 && (
        <div style={{
          background: T.card, border: '1px solid ' + T.border, borderRadius: 12, overflow: 'hidden',
          marginBottom: 32,
        }}>
          {/* Header */}
          <div style={{ display: 'grid', gridTemplateColumns: GRID }}>
            {['Template Name', 'Template Key', 'Channel', 'Updated', 'Actions'].map((col) => (
              <div key={col} style={headerCell}>{col}</div>
            ))}
          </div>

          {/* Rows */}
          {visibleTemplates.length === 0 ? (
            <div style={{
              padding: '40px 20px', textAlign: 'center',
              fontSize: 13, color: T.muted,
            }}>
              No templates match the search.
            </div>
          ) : (
            visibleTemplates.map((template, idx) => {
              const isLast = idx === visibleTemplates.length - 1
              return (
                <div
                  key={template.key}
                  style={{
                    display: 'grid', gridTemplateColumns: GRID,
                    alignItems: 'center',
                    borderBottom: isLast ? 'none' : '1px solid ' + T.border,
                    transition: 'background 0.1s',
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = T.bg }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
                >
                  {/* Template name + mail icon */}
                  <div style={{ padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 12 }}>
                    <div style={{
                      width: 36, height: 36, borderRadius: 8, flexShrink: 0,
                      background: ACCENT + '18',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}>
                      <Mail size={16} strokeWidth={1.8} style={{ color: ACCENT }} />
                    </div>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: T.text }}>{template.name}</div>
                      {template.subject && (
                        <div style={{ fontSize: 11, color: T.muted, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 240 }}>
                          {template.subject}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Template key */}
                  <div style={{ padding: '12px 20px', fontSize: 12, color: T.muted, fontFamily: 'monospace' }}>
                    {template.key}
                  </div>

                  {/* Channel */}
                  <div style={{ padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }}>
                    {template.channel && <TagBadge tag={channelLabel(template.channel)} />}
                    {template.linkedFormKey && <TagBadge tag="Action" />}
                  </div>

                  {/* Updated */}
                  <div style={{ padding: '12px 20px', fontSize: 12, color: T.muted }}>
                    {formatDate(template.updatedAt)}
                  </div>

                  {/* Actions */}
                  <div style={{ padding: '12px 20px', display: 'flex', gap: 6, alignItems: 'center' }}>
                    <IconBtn
                      title="Edit template"
                      onClick={() => router.push(`/admin/email-templates/${encodeURIComponent(template.key)}`)}
                    >
                      <Pencil size={14} />
                    </IconBtn>
                    <IconBtn
                      title="Delete template"
                      danger
                      onClick={() => setDeleteTarget(template)}
                    >
                      <Trash2 size={14} />
                    </IconBtn>
                  </div>
                </div>
              )
            })
          )}
        </div>
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}
        title={`Delete "${deleteTarget?.name}"?`}
        description="This will soft-delete the template. In-flight process notifications using this template will fail until a replacement is created with the same key."
        confirmLabel="Delete"
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.key)}
      />
    </PageSurface>
  )
}
