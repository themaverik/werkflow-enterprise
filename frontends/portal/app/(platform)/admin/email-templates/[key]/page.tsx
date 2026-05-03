'use client'

import { useRef, useCallback, useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'
import { ArrowLeft, Save } from 'lucide-react'
import Link from 'next/link'
import { getEmailTemplate, updateEmailTemplate } from '@/lib/api/email-templates'
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

export default function EditEmailTemplatePage() {
  const params = useParams()
  const router = useRouter()
  const { toast } = useToast()
  const { status } = useSession()
  const queryClient = useQueryClient()
  const editorApiRef = useRef<EmailTemplateEditorApi | null>(null)
  const handleEditorReady = useCallback((api: EmailTemplateEditorApi) => {
    editorApiRef.current = api
  }, [])

  const templateKey = decodeURIComponent(params.key as string)

  const { data: template, isLoading: templateLoading } = useQuery({
    queryKey: ['emailTemplate', templateKey],
    queryFn: () => getEmailTemplate(templateKey),
    enabled: status === 'authenticated',
  })

  const { data: forms } = useQuery({
    queryKey: ['formDefinitions'],
    queryFn: getFormDefinitions,
    enabled: status === 'authenticated',
  })

  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [linkedFormKey, setLinkedFormKey] = useState('')

  // Seed form fields from loaded template
  useEffect(() => {
    if (template) {
      setName(template.name ?? '')
      setSubject(template.subject ?? '')
      setLinkedFormKey(template.linkedFormKey ?? '')
    }
  }, [template])

  const formFields = linkedFormKey
    ? extractFormFields(forms?.find(f => f.key === linkedFormKey)?.formJson ?? '')
    : []

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (!editorApiRef.current || !template) throw new Error('Not ready')
      const { html, design } = await editorApiRef.current.exportHtml()
      return updateEmailTemplate(templateKey, {
        key: templateKey,
        name: name.trim() || templateKey,
        channel: template.channel,
        subject: subject.trim() || undefined,
        body: html,
        designJson: design,
        linkedFormKey: linkedFormKey || null,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['emailTemplates'] })
      queryClient.invalidateQueries({ queryKey: ['emailTemplate', templateKey] })
      queryClient.invalidateQueries({ queryKey: ['notificationTemplates'] })
      toast({ title: 'Template saved' })
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.message || err?.message || 'Failed to save template'
      toast({ title: 'Save failed', description: msg, variant: 'destructive' })
    },
  })

  if (templateLoading) {
    return <div className="space-y-6 text-muted-foreground text-sm">Loading template...</div>
  }

  if (!template) {
    return (
      <div className="space-y-6">
        <p className="text-destructive">Template not found: {templateKey}</p>
        <Button variant="link" asChild className="mt-2 px-0">
          <Link href="/admin/email-templates">Back to templates</Link>
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6 space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/admin/email-templates">
            <ArrowLeft className="h-4 w-4 mr-1" />
            Back
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-bold">{template.name}</h1>
          <p className="text-xs text-muted-foreground font-mono mt-0.5">{templateKey}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-6 items-start">
        {/* Unlayer editor */}
        <div className="rounded-lg border bg-card shadow-sm overflow-hidden">
          <EmailTemplateEditor
            initialDesign={template.designJson}
            formFields={formFields}
            onReady={handleEditorReady}
          />
        </div>

        {/* Sidebar */}
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">Template Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label>Key</Label>
                <Input value={templateKey} disabled className="font-mono text-sm bg-muted" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="name">Display Name</Label>
                <Input
                  id="name"
                  value={name}
                  onChange={e => setName(e.target.value)}
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
                <Label htmlFor="linkedForm">Linked Form</Label>
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
              </div>
            </CardContent>
          </Card>

          <Button
            className="w-full"
            onClick={() => updateMutation.mutate()}
            disabled={updateMutation.isPending}
          >
            <Save className="h-4 w-4 mr-2" />
            {updateMutation.isPending ? 'Saving...' : 'Save Template'}
          </Button>
        </div>
      </div>
    </div>
  )
}
