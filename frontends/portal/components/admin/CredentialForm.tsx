'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { RefreshCw, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import {
  CREDENTIAL_TYPES,
  createCredential,
  updateCredential,
  type TenantCredentialResponse,
  type CredentialFieldSchema,
} from '@/lib/api/credentials'

const LABEL_REGEX = /^[a-z][a-z0-9-]*$/

interface Props {
  open: boolean
  mode: 'create' | 'edit'
  initial?: TenantCredentialResponse
  onClose: () => void
  onSaved: () => void
}

export function CredentialForm({ open, mode, initial, onClose, onSaved }: Props) {
  const t = useTranslations('admin.credentials')
  const qc = useQueryClient()

  const isEdit = mode === 'edit'

  const defaultType = isEdit ? (initial?.credentialType ?? '') : CREDENTIAL_TYPES[0].name
  const [selectedType, setSelectedType] = useState(defaultType)
  const [label, setLabel] = useState(isEdit ? (initial?.label ?? '') : '')

  const schema = CREDENTIAL_TYPES.find((ct) => ct.name === selectedType)
  const fields: CredentialFieldSchema[] = schema?.fields ?? []

  function buildDefaultValues(): Record<string, string | number | boolean> {
    const result: Record<string, string | number | boolean> = {}
    for (const f of fields) {
      if (f.type === 'BOOL') {
        result[f.name] = isEdit ? false : (typeof f.defaultValue === 'boolean' ? f.defaultValue : false)
      } else if (f.type === 'INT') {
        result[f.name] = isEdit ? '' : (typeof f.defaultValue === 'number' ? String(f.defaultValue) : '')
      } else {
        result[f.name] = isEdit ? '' : (typeof f.defaultValue === 'string' ? f.defaultValue : '')
      }
    }
    return result
  }

  const [values, setValues] = useState<Record<string, string | number | boolean>>(buildDefaultValues)
  const [saving, setSaving] = useState(false)
  const [labelError, setLabelError] = useState('')

  function handleTypeChange(type: string) {
    setSelectedType(type)
    const newSchema = CREDENTIAL_TYPES.find((ct) => ct.name === type)
    const newFields = newSchema?.fields ?? []
    const reset: Record<string, string | number | boolean> = {}
    for (const f of newFields) {
      if (f.type === 'BOOL') {
        reset[f.name] = typeof f.defaultValue === 'boolean' ? f.defaultValue : false
      } else if (f.type === 'INT') {
        reset[f.name] = typeof f.defaultValue === 'number' ? String(f.defaultValue) : ''
      } else {
        reset[f.name] = typeof f.defaultValue === 'string' ? f.defaultValue : ''
      }
    }
    setValues(reset)
  }

  function setField(name: string, value: string | number | boolean) {
    setValues((prev) => ({ ...prev, [name]: value }))
  }

  function validate(): string | null {
    if (!isEdit && !LABEL_REGEX.test(label)) {
      return t('labelError')
    }
    for (const f of fields) {
      if (!f.required) continue
      const raw = values[f.name]
      if (f.type === 'INT') {
        const n = Number(raw)
        if (raw === '' || raw === undefined || isNaN(n)) {
          return t('fieldRequired', { field: f.displayName })
        }
      } else if (f.type === 'BOOL') {
        // boolean always has a value
      } else {
        if (!raw || String(raw).trim() === '') {
          return t('fieldRequired', { field: f.displayName })
        }
      }
    }
    return null
  }

  async function handleSubmit() {
    const err = validate()
    if (err) {
      if (!isEdit && err === t('labelError')) {
        setLabelError(err)
      } else {
        toast.error(err)
      }
      return
    }
    setLabelError('')

    const coercedValues: Record<string, string | number | boolean> = {}
    for (const f of fields) {
      const raw = values[f.name]
      if (f.type === 'INT') {
        coercedValues[f.name] = Number(raw)
      } else {
        coercedValues[f.name] = raw
      }
    }

    setSaving(true)
    try {
      if (isEdit && initial) {
        await updateCredential(initial.id, { values: coercedValues })
        toast.success(t('rotateSuccess'))
      } else {
        await createCredential({ credentialType: selectedType, label, values: coercedValues })
        toast.success(t('createSuccess'))
      }
      qc.invalidateQueries({ queryKey: ['tenant-credentials'] })
      onSaved()
      onClose()
    } catch (e: unknown) {
      toast.error(isEdit ? t('rotateFailed') : t('createFailed'), {
        description: e instanceof Error ? e.message : undefined,
      })
    } finally {
      setSaving(false)
    }
  }

  function handleOpenChange(open: boolean) {
    if (!open) onClose()
  }

  const title = isEdit ? t('editTitle') : t('createTitle')

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          {isEdit && (
            <div className="rounded-lg border border-amber-300 bg-amber-50 dark:bg-amber-950/30 dark:border-amber-800 px-3 py-2.5 flex items-start gap-2">
              <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
              <p className="text-xs text-amber-800 dark:text-amber-300">{t('rotateWarning')}</p>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="cf-type">{t('type')}</Label>
            <Select
              value={selectedType}
              onValueChange={handleTypeChange}
              disabled={isEdit}
            >
              <SelectTrigger id="cf-type" className={isEdit ? 'bg-muted text-muted-foreground' : ''}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {CREDENTIAL_TYPES.map((ct) => (
                  <SelectItem key={ct.name} value={ct.name}>
                    {ct.displayName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="cf-label">{t('label')}</Label>
            <Input
              id="cf-label"
              placeholder={t('labelPlaceholder')}
              value={label}
              onChange={(e) => {
                setLabel(e.target.value)
                setLabelError('')
              }}
              readOnly={isEdit}
              className={isEdit ? 'bg-muted text-muted-foreground' : ''}
            />
            {labelError && (
              <p className="text-xs text-destructive">{labelError}</p>
            )}
            {!isEdit && (
              <p className="text-xs text-muted-foreground">{t('labelHint')}</p>
            )}
          </div>

          {fields.map((f) => (
            <div key={f.name} className="space-y-2">
              <Label htmlFor={`cf-${f.name}`}>
                {f.displayName}
                {!f.required && (
                  <span className="text-muted-foreground font-normal ml-1">{t('optional')}</span>
                )}
              </Label>
              {f.type === 'BOOL' ? (
                <Select
                  value={String(values[f.name] ?? false)}
                  onValueChange={(v) => setField(f.name, v === 'true')}
                >
                  <SelectTrigger id={`cf-${f.name}`}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">{t('boolTrue')}</SelectItem>
                    <SelectItem value="false">{t('boolFalse')}</SelectItem>
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  id={`cf-${f.name}`}
                  type={f.type === 'SECRET' ? 'password' : f.type === 'INT' ? 'number' : 'text'}
                  autoComplete={f.type === 'SECRET' ? 'new-password' : undefined}
                  placeholder={isEdit && f.type === 'SECRET' ? '••••••••' : undefined}
                  value={String(values[f.name] ?? '')}
                  onChange={(e) => setField(f.name, e.target.value)}
                />
              )}
            </div>
          ))}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={onClose} disabled={saving}>
              {t('cancel')}
            </Button>
            <Button onClick={handleSubmit} disabled={saving}>
              {saving && <RefreshCw className="h-4 w-4 mr-2 animate-spin" />}
              {isEdit ? t('saveRotate') : t('save')}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
