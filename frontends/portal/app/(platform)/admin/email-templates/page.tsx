'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Plus, Edit, Trash2, RefreshCw } from 'lucide-react'
import { listEmailTemplatesFull, deleteEmailTemplate, type EmailTemplateResponse } from '@/lib/api/email-templates'

export default function EmailTemplatesPage() {
  const { status } = useSession()
  const queryClient = useQueryClient()
  const router = useRouter()
  const { toast } = useToast()
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
      toast({ title: 'Template deleted' })
      setDeleteTarget(null)
    },
    onError: () => {
      toast({ title: 'Delete failed', variant: 'destructive' })
    },
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Email Templates</h1>
          <p className="text-muted-foreground mt-1">Design and manage notification email templates</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => refetch()} disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button asChild>
            <Link href="/admin/email-templates/new">
              <Plus className="h-4 w-4 mr-2" />
              New Template
            </Link>
          </Button>
        </div>
      </div>

      {error && (
        <div className="rounded-md border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          Failed to load templates: {(error as Error).message}
        </div>
      )}

      {isLoading && (
        <div className="text-muted-foreground text-sm">Loading templates...</div>
      )}

      {!isLoading && templates && templates.length === 0 && (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            No email templates yet.{' '}
            <Link href="/admin/email-templates/new" className="text-primary hover:underline">
              Create the first one
            </Link>
          </CardContent>
        </Card>
      )}

      {templates && templates.length > 0 && (
        <div className="grid gap-4">
          {templates.map(template => (
            <Card key={template.key} className="hover:shadow-sm transition-shadow">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <CardTitle className="text-base">{template.name}</CardTitle>
                    <Badge variant="secondary" className="text-xs font-mono">{template.key}</Badge>
                    <Badge variant="outline" className="text-xs">{template.channel}</Badge>
                    {template.linkedFormKey && (
                      <Badge variant="outline" className="text-xs text-blue-600 border-blue-300">
                        Form: {template.linkedFormKey}
                      </Badge>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => router.push(`/admin/email-templates/${encodeURIComponent(template.key)}`)}
                    >
                      <Edit className="h-3.5 w-3.5 mr-1.5" />
                      Edit
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      onClick={() => setDeleteTarget(template)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="pt-0">
                {template.subject && (
                  <p className="text-sm text-muted-foreground">
                    <span className="font-medium">Subject:</span> {template.subject}
                  </p>
                )}
                <p className="text-xs text-muted-foreground mt-1">
                  Updated {new Date(template.updatedAt).toLocaleDateString()}
                </p>
              </CardContent>
            </Card>
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
