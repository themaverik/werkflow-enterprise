'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createForm, updateForm } from '@/lib/api/flowable'
import { Save, Download, Upload, CheckCircle } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import FormJsEditor from './FormJsEditor'

const DEPARTMENTS = ['HR', 'Finance', 'Procurement', 'Inventory', 'IT', 'Operations', 'Legal', 'Executive']

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
  const router = useRouter()
  const isEditMode = !!initialFormKey

  useEffect(() => {
    if (initialForm) {
      try {
        const parsed = JSON.parse(initialForm)
        if (parsed.components && !parsed.type) {
          setFormSchema({ type: 'default', components: [], schemaVersion: 9 })
        } else {
          setFormSchema(parsed)
        }
      } catch (err) {
        console.error('Error parsing initial form:', err)
      }
    }
  }, [initialForm])

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
        if (!json.type || !json.components) {
          alert('Invalid form-js schema. Please ensure the file contains a valid form-js form definition.')
          return
        }
        setFormSchema(json)
        if (json.id) setFormKey(json.id)
        setHasChanges(true)
      } catch (err) {
        alert('Invalid JSON file')
      }
    }
    reader.readAsText(file)
  }

  return (
    <div className="flex flex-col h-screen">
      {/* Dark header matching design spec */}
      <div
        className="flex items-center justify-between px-4 py-2 shrink-0"
        style={{ background: '#111c27', borderBottom: '1px solid rgba(255,255,255,0.08)' }}
      >
        <div className="flex items-center gap-3 flex-1">
          <span className="text-sm font-semibold text-white">Form Editor</span>
          <input
            type="text"
            value={formKey}
            onChange={(e) => setFormKey(e.target.value)}
            placeholder={t('formKeyPlaceholder')}
            disabled={isEditMode}
            className="rounded px-3 py-1.5 text-xs w-48"
            style={{
              background: 'rgba(255,255,255,0.08)',
              border: '1px solid rgba(255,255,255,0.15)',
              color: '#fff',
              outline: 'none',
            }}
          />
          {!isEditMode && (
            <select
              value={owningDepartment}
              onChange={(e) => setOwningDepartment(e.target.value)}
              aria-label="Owning department"
              className="rounded px-3 py-1.5 text-xs"
              style={{
                background: 'rgba(255,255,255,0.08)',
                border: '1px solid rgba(255,255,255,0.15)',
                color: '#fff',
                outline: 'none',
              }}
            >
              <option value="" style={{ background: '#111c27' }}>Select department</option>
              {DEPARTMENTS.map(d => <option key={d} value={d} style={{ background: '#111c27' }}>{d}</option>)}
            </select>
          )}
          {hasChanges && !saveSuccess && !saveError && (
            <span className="text-xs" style={{ color: 'rgba(255,200,80,0.85)' }}>{t('unsavedChanges')}</span>
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
            style={{ background: '#149ba5', color: '#fff' }}
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
          className="h-full"
        />
      </div>
    </div>
  )
}
