'use client'

import { useState, useEffect, useCallback } from 'react'
import dynamic from 'next/dynamic'
import { Button } from '@/components/ui/button'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createForm, updateForm } from '@/lib/api/flowable'
import { getDepartments } from '@/lib/api/adminTenantApi'
import { useAuth } from '@/lib/auth/auth-context'
import { Save, Download, Upload, CheckCircle } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'

// @bpmn-io/form-js-editor references browser-only globals (KeyboardEvent
// etc.) at module load. Loading it via next/dynamic with ssr:false keeps
// it out of the server bundle compilation step.
const FormJsEditor = dynamic(() => import('./FormJsEditor'), { ssr: false })

/**
 * Structural guard for a form-js schema. Mirrors the minimum shape
 * `FormEditor.importSchema` will accept without throwing — top-level
 * `type` string, `components` array, optional `schemaVersion` number,
 * and every component carrying a string `type`.
 */
function isValidFormJsSchema(value: unknown): value is { type: string; components: unknown[]; id?: string; schemaVersion?: number } {
  if (!value || typeof value !== 'object') return false
  const obj = value as Record<string, unknown>
  if (typeof obj.type !== 'string') return false
  if (!Array.isArray(obj.components)) return false
  if (obj.schemaVersion !== undefined && typeof obj.schemaVersion !== 'number') return false
  return obj.components.every((c) => c && typeof c === 'object' && typeof (c as Record<string, unknown>).type === 'string')
}

interface FormJsBuilderProps {
  initialForm?: string
  formKey?: string
  initialOwningDepartment?: string
}

export default function FormJsBuilder({
  initialForm,
  formKey: initialFormKey,
  initialOwningDepartment,
}: FormJsBuilderProps) {
  const t = useTranslations('formBuilder')
  const { user } = useAuth()
  const [formSchema, setFormSchema] = useState<any>({
    type: 'default',
    components: [],
    schemaVersion: 9
  })
  const [formKey, setFormKey] = useState(initialFormKey || '')
  const [owningDepartment, setOwningDepartment] = useState(initialOwningDepartment || '')
  const [hasChanges, setHasChanges] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const isEditMode = !!initialFormKey

  // Fetch tenant departments for the owning-department dropdown.
  // Replaces the previous hardcoded DEPARTMENTS array; the source of
  // truth is /api/v1/departments (managed in /admin/tenant/departments).
  const { data: departments = [], isLoading: departmentsLoading } = useQuery({
    queryKey: ['departments', user?.tenantId],
    queryFn: () => getDepartments(user?.tenantId),
    staleTime: 5 * 60 * 1000,
    enabled: !isEditMode && !!user?.tenantId, // dropdown only shows when creating a new form
  })

  useEffect(() => {
    if (initialForm) {
      try {
        const parsed = JSON.parse(initialForm)
        // Older stored schemas may have `components` but be missing the
        // top-level `type` field. Fill in a sensible default rather than
        // discarding the stored components.
        if (Array.isArray(parsed?.components) && typeof parsed.type !== 'string') {
          setFormSchema({ type: 'default', schemaVersion: 9, ...parsed })
        } else if (parsed && typeof parsed === 'object') {
          setFormSchema(parsed)
        }
      } catch {
        toast.error(t('invalidInitialForm'))
      }
    }
  }, [initialForm, t])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!formKey) throw new Error('Form key is required')
      const schemaToSave = { ...formSchema, id: formKey }
      if (isEditMode) {
        return updateForm(formKey, schemaToSave)
      }
      return createForm({
        formKey,
        schemaJson: schemaToSave,
        owningDepartment: owningDepartment || undefined,
      })
    },
    onSuccess: () => {
      setHasChanges(false)
      setSaveSuccess(true)
      setSaveError(null)
      queryClient.invalidateQueries({ queryKey: ['formDefinitions'] })
      setTimeout(() => setSaveSuccess(false), 3000)
    },
    onError: (error: Error) => {
      setSaveError(error.message)
    }
  })

  const handleSchemaChange = useCallback((newSchema: any) => {
    setFormSchema(newSchema)
    setHasChanges(true)
    setSaveSuccess(false)
    setSaveError(null)
  }, [])

  const handleEditorSave = useCallback(async (schema: any) => {
    setFormSchema(schema)
    saveMutation.mutate()
  }, [saveMutation])

  const handleDownload = () => {
    const schemaToDownload = { ...formSchema, id: formKey }
    const blob = new Blob([JSON.stringify(schemaToDownload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${formKey || 'form'}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  const handleUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const json = JSON.parse(e.target?.result as string)
        if (!isValidFormJsSchema(json)) {
          toast.error(t('uploadInvalidSchema'))
          return
        }
        setFormSchema(json)
        if (typeof json.id === 'string') setFormKey(json.id)
        setHasChanges(true)
        toast.success(t('uploadSuccess'))
      } catch {
        toast.error(t('uploadInvalidJson'))
      }
    }
    reader.readAsText(file)
  }

  return (
    <div className="flex flex-col h-screen">
      {/* Dark header — uses --panel-* tokens for cross-editor uniformity */}
      <div
        className="form-designer-dark-toolbar flex items-center justify-between px-4 py-2 shrink-0"
        style={{
          background:   'var(--panel-hdr-bg)',
          borderBottom: '1px solid var(--panel-hdr-border)',
          fontFamily:   'var(--panel-font)',
        }}
      >
        <div className="flex items-center gap-3 flex-1">
          <span
            className="text-sm font-semibold"
            style={{ color: 'var(--panel-hdr-text)' }}
          >
            Form Editor
          </span>
          <input
            type="text"
            value={formKey}
            onChange={(e) => setFormKey(e.target.value)}
            placeholder={t('formKeyPlaceholder')}
            disabled={isEditMode}
            className="rounded px-3 py-1.5 text-xs w-48 disabled:opacity-60 disabled:cursor-not-allowed"
            style={{
              background:   'rgba(255,255,255,0.08)',
              border:       '1px solid rgba(255,255,255,0.15)',
              color:        'var(--panel-hdr-text)',
              outline:      'none',
              fontFamily:   'var(--panel-font)',
            }}
          />
          {!isEditMode && (
            <select
              value={owningDepartment}
              onChange={(e) => setOwningDepartment(e.target.value)}
              aria-label="Owning department"
              disabled={departmentsLoading}
              className="rounded px-3 py-1.5 text-xs disabled:opacity-60 disabled:cursor-not-allowed"
              style={{
                background:   'rgba(255,255,255,0.08)',
                border:       '1px solid rgba(255,255,255,0.15)',
                color:        'var(--panel-hdr-text)',
                outline:      'none',
                fontFamily:   'var(--panel-font)',
              }}
            >
              <option value="" style={{ background: 'var(--panel-hdr-bg)' }}>
                {departmentsLoading ? 'Loading departments…' : departments.length === 0 ? 'No departments configured' : 'Select department'}
              </option>
              {departments.map(d => (
                <option key={d.code} value={d.code} style={{ background: 'var(--panel-hdr-bg)' }}>
                  {d.name}
                </option>
              ))}
            </select>
          )}
          {hasChanges && !saveSuccess && !saveError && (
            <span className="text-xs font-medium text-amber-300">{t('unsavedChanges')}</span>
          )}
          {saveSuccess && (
            <span className="text-xs text-emerald-400 flex items-center gap-1">
              <CheckCircle className="h-3.5 w-3.5" /> {t('saveSuccess')}
            </span>
          )}
          {saveError && (
            <span className="text-xs text-red-400">{t('saveFailedInline', { error: saveError })}</span>
          )}
        </div>

        <div className="flex items-center gap-2">
          <input type="file" accept=".json" onChange={handleUpload} className="hidden" id="form-upload" />
          <Button
            variant="ghost"
            size="sm"
            onClick={() => document.getElementById('form-upload')?.click()}
            className="text-white/70 hover:text-white hover:bg-white/10 h-7 px-3 text-xs"
          >
            <Upload className="h-3.5 w-3.5 mr-1.5" />
            {t('load')}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleDownload}
            className="text-white/70 hover:text-white hover:bg-white/10 h-7 px-3 text-xs"
          >
            <Download className="h-3.5 w-3.5 mr-1.5" />
            {t('download')}
          </Button>
          <button
            onClick={() => saveMutation.mutate()}
            disabled={saveMutation.isPending || !formKey}
            className="h-7 px-4 rounded-md text-xs font-semibold disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            style={{
              background: 'var(--panel-accent)',
              color:      'var(--panel-hdr-text)',
              fontFamily: 'var(--panel-font)',
            }}
          >
            <Save className="h-3.5 w-3.5 mr-1.5 inline" />
            {saveMutation.isPending ? t('saving') : t('save')}
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-hidden">
        <FormJsEditor
          schema={formSchema}
          onSchemaChange={handleSchemaChange}
          onSave={handleEditorSave}
          onError={(err) => {
            const message = err instanceof Error ? err.message : String(err)
            toast.error(t('editorError', { error: message }))
          }}
          className="h-full"
        />
      </div>
    </div>
  )
}
