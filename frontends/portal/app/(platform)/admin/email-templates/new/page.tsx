'use client'

import { useRef, useCallback, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'
import { ArrowLeft, Save } from 'lucide-react'
import Link from 'next/link'
import { createEmailTemplate } from '@/lib/api/email-templates'
import { getFormDefinitions } from '@/lib/api/flowable'
import dynamic from 'next/dynamic'
import type { EmailTemplateEditorApi } from '@/components/admin/EmailTemplateEditor'

const EmailTemplateEditor = dynamic(
  () => import('@/components/admin/EmailTemplateEditor'),
  { ssr: false, loading: () => <div className="h-[600px] flex items-center justify-center text-muted-foreground text-sm">Loading editor...</div> }
)

function extractFormFields(formJson: string): string[] {
  try {
    const schema = JSON.parse(formJson)
    const components: any[] = schema.components ?? schema.fields ?? []
    return components
      .filter((c: any) => c.key && c.key !== 'submit')
      .map((c: any) => c.key as string)
  } catch {
    return []
  }
}

export default function NewEmailTemplatePage() {
  const router = useRouter()
  const { toast } = useToast()
  const { status } = useSession()
  const queryClient = useQueryClient()
  const editorApiRef = useRef<EmailTemplateEditorApi | null>(null)
  const handleEditorReady = useCallback((api: EmailTemplateEditorApi) => {
    editorApiRef.current = api
  }, [])

  const [key, setKey] = useState('')
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [linkedFormKey, setLinkedFormKey] = useState('')

  const { data: forms } = useQuery({
    queryKey: ['formDefinitions'],
    queryFn: getFormDefinitions,
    enabled: status === 'authenticated',
  })

  const formFields = linkedFormKey
    ? extractFormFields(forms?.find(f => f.key === linkedFormKey)?.formJson ?? '')
    : []

  const createMutation = useMutation({
    mutationFn: async () => {
      if (!editorApiRef.current) throw new Error('Editor not ready')
      const { html, design } = await editorApiRef.current.exportHtml()
      return createEmailTemplate({
        key: key.trim(),
        name: name.trim() || key.trim(),
        channel: 'email',
        subject: subject.trim() || undefined,
        body: html,
        designJson: design,
        linkedFormKey: linkedFormKey || null,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['emailTemplates'] })
      queryClient.invalidateQueries({ queryKey: ['notificationTemplates'] })
      toast({ title: 'Template created' })
      router.push('/admin/email-templates')
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.message || err?.message || 'Failed to create template'
      toast({ title: 'Create failed', description: msg, variant: 'destructive' })
    },
  })

  const canSave = key.trim().length > 0 && !createMutation.isPending

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/admin/email-templates">
            <ArrowLeft className="h-4 w-4 mr-1" />
            Back
          </Link>
        </Button>
        <h1 className="text-2xl font-bold">New Email Template</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-6 items-start">
        {/* Unlayer editor */}
        <div className="rounded-lg border bg-card shadow-sm overflow-hidden">
          <EmailTemplateEditor formFields={formFields} onReady={handleEditorReady} />
        </div>

        {/* Sidebar */}
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">Template Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="key">Key *</Label>
                <Input
                  id="key"
                  value={key}
                  onChange={e => setKey(e.target.value)}
                  placeholder="e.g. leave-approved"
                  className="font-mono text-sm"
                />
                <p className="text-xs text-muted-foreground">Unique identifier used in BPMN NOTIFICATION tasks</p>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="name">Display Name</Label>
                <Input
                  id="name"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder="e.g. Leave Approved"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="subject">Email Subject</Label>
                <Input
                  id="subject"
                  value={subject}
                  onChange={e => setSubject(e.target.value)}
                  placeholder="e.g. Your leave has been approved"
                />
                <p className="text-xs text-muted-foreground">Supports {'{{'+'variable'+'}}' } merge tags</p>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="linkedForm">Linked Form (optional)</Label>
                <select
                  id="linkedForm"
                  className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                  value={linkedFormKey}
                  onChange={e => setLinkedFormKey(e.target.value)}
                >
                  <option value="">None</option>
                  {forms?.map(f => (
                    <option key={f.key} value={f.key}>{f.name || f.key}</option>
                  ))}
                </select>
                <p className="text-xs text-muted-foreground">Adds form field keys as merge tags</p>
              </div>
            </CardContent>
          </Card>

          <Button
            className="w-full"
            onClick={() => createMutation.mutate()}
            disabled={!canSave}
          >
            <Save className="h-4 w-4 mr-2" />
            {createMutation.isPending ? 'Saving...' : 'Save Template'}
          </Button>
        </div>
      </div>
    </div>
  )
}
