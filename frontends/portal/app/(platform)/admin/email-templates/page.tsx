'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Plus, Edit, Trash2, RefreshCw, Mail, Eye } from 'lucide-react'
import { toast } from 'sonner'
import { listEmailTemplatesFull, deleteEmailTemplate, type EmailTemplateResponse } from '@/lib/api/email-templates'

const ACCENT = '#149ba5'

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

export default function EmailTemplatesPage() {
  const { status } = useSession()
  const queryClient = useQueryClient()
  const router = useRouter()
  const [deleteTarget, setDeleteTarget] = useState<EmailTemplateResponse | null>(null)

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

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Email Templates</h1>
          <p className="text-sm text-muted-foreground mt-0.5">Manage transactional email templates used in workflow notifications.</p>
        </div>
        <div className="flex gap-2">
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

      {error && (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          Failed to load templates. Please try again.
        </div>
      )}

      {isLoading && (
        <div className="space-y-0 bg-card border border-border rounded-xl overflow-hidden">
          {[1, 2, 3].map((i) => (
            <div key={i} className="flex items-center gap-4 px-5 py-4 border-b border-border last:border-0 animate-pulse">
              <div className="w-9 h-9 rounded-lg bg-muted shrink-0" />
              <div className="flex-1 space-y-2">
                <div className="h-3 bg-muted rounded w-1/3" />
                <div className="h-2.5 bg-muted rounded w-2/3" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!isLoading && templates && templates.length === 0 && (
        <div className="bg-card border border-border rounded-xl p-12 text-center">
          <Mail size={32} strokeWidth={1.5} className="mx-auto mb-3 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">No email templates yet.</p>
          <Link href="/admin/email-templates/new" className="text-sm font-medium mt-1 inline-block" style={{ color: ACCENT }}>
            Create the first one →
          </Link>
        </div>
      )}

      {!isLoading && templates && templates.length > 0 && (
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          {templates.map((template, i) => (
            <div
              key={template.key}
              className="flex items-center gap-4 px-5 py-4 border-b border-border last:border-0 hover:bg-muted/30 transition-colors"
            >
              {/* Mail icon */}
              <div style={{ width: 38, height: 38, borderRadius: 9, background: ACCENT + '15', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Mail size={16} strokeWidth={1.8} style={{ color: ACCENT }} />
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap mb-1">
                  <span className="text-sm font-semibold text-foreground">{template.name}</span>
                  {template.channel && <TagBadge tag={template.channel === 'email' ? 'Notification' : template.channel} />}
                  {template.linkedFormKey && <TagBadge tag="Action" />}
                </div>
                {template.subject && (
                  <p className="text-xs text-muted-foreground truncate">Subject: {template.subject}</p>
                )}
              </div>

              {/* Right side */}
              <div className="text-right shrink-0">
                <p className="text-xs text-muted-foreground mb-2">
                  Edited {new Date(template.updatedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}
                </p>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 px-2.5 text-xs"
                    onClick={() => router.push(`/admin/email-templates/${encodeURIComponent(template.key)}`)}
                  >
                    <Edit size={12} strokeWidth={1.8} className="mr-1" />
                    Edit
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 px-2 text-destructive hover:text-destructive"
                    onClick={() => setDeleteTarget(template)}
                  >
                    <Trash2 size={12} strokeWidth={1.8} />
                  </Button>
                </div>
              </div>
            </div>
          ))}
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
    </div>
  )
}
