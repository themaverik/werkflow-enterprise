'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
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
      <Card className="border-b rounded-none">
        <div className="flex items-center justify-between p-4">
          <div className="flex items-center gap-4 flex-1">
            <input
              type="text"
              value={formKey}
              onChange={(e) => setFormKey(e.target.value)}
              placeholder={t('formKeyPlaceholder')}
              className="border rounded px-3 py-2 w-64 text-sm"
              disabled={isEditMode}
            />
            {!isEditMode && (
              <select
                value={owningDepartment}
                onChange={(e) => setOwningDepartment(e.target.value)}
                className="border rounded px-3 py-2 text-sm"
                aria-label="Owning department"
              >
                <option value="">Select department</option>
                {DEPARTMENTS.map(d => <option key={d} value={d}>{d}</option>)}
              </select>
            )}
            {hasChanges && !saveSuccess && !saveError && (
              <span className="text-sm text-amber-600">{t('unsavedChanges')}</span>
            )}
            {saveSuccess && (
              <span className="text-sm text-green-600 flex items-center gap-1">
                <CheckCircle className="h-4 w-4" /> {t('saveSuccess')}
              </span>
            )}
            {saveError && (
              <span className="text-sm text-red-600">{t('saveFailedInline', { error: saveError })}</span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <input
              type="file"
              accept=".json"
              onChange={handleUpload}
              className="hidden"
              id="form-upload"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => document.getElementById('form-upload')?.click()}
              title="Load from file"
            >
              <Upload className="h-4 w-4 mr-2" />
              {t('load')}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleDownload}
              title="Download as JSON"
            >
              <Download className="h-4 w-4 mr-2" />
              {t('download')}
            </Button>
            <Button
              size="sm"
              onClick={() => saveMutation.mutate()}
              disabled={saveMutation.isPending || !formKey}
            >
              <Save className="h-4 w-4 mr-2" />
              {saveMutation.isPending ? t('saving') : t('save')}
            </Button>
          </div>
        </div>
      </Card>

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
