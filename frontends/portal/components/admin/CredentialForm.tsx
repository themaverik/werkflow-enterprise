'use client'

import { useState, useEffect } from 'react'
import { useTranslations } from 'next-intl'
import {
  Dialog,
  DialogContent,
  DialogDescription,
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

type ValidationResult = { field: 'label' | 'value'; message: string } | null

// L3: shared helper — builds a fresh values record for a given field list.
function defaultValuesFor(
  fields: CredentialFieldSchema[],
  isEdit: boolean,
): Record<string, string | number | boolean> {
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

export function CredentialForm({ open, mode, initial, onClose, onSaved }: Props) {
  const t = useTranslations('admin.credentials')

  const isEdit = mode === 'edit'

  const defaultType = isEdit ? (initial?.credentialType ?? '') : CREDENTIAL_TYPES[0].name
  const [selectedType, setSelectedType] = useState(defaultType)
  const [label, setLabel] = useState(isEdit ? (initial?.label ?? '') : '')

  const schema = CREDENTIAL_TYPES.find((ct) => ct.name === selectedType)
  const fields: CredentialFieldSchema[] = schema?.fields ?? []

  const [values, setValues] = useState<Record<string, string | number | boolean>>(
    () => defaultValuesFor(fields, isEdit),
  )
  const [saving, setSaving] = useState(false)
  const [labelError, setLabelError] = useState('')

  // H3: reset form state when dialog opens or the target credential changes.
  useEffect(() => {
    if (!open) return
    const freshType = isEdit ? (initial?.credentialType ?? '') : CREDENTIAL_TYPES[0].name
    const freshFields = CREDENTIAL_TYPES.find((ct) => ct.name === freshType)?.fields ?? []
    setSelectedType(freshType)
    setLabel(isEdit ? (initial?.label ?? '') : '')
    setValues(defaultValuesFor(freshFields, isEdit))
    setLabelError('')
  }, [open, mode, initial?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  function handleTypeChange(type: string) {
    setSelectedType(type)
    const newFields = CREDENTIAL_TYPES.find((ct) => ct.name === type)?.fields ?? []
    setValues(defaultValuesFor(newFields, false))
  }

  function setField(name: string, value: string | number | boolean) {
    setValues((prev) => ({ ...prev, [name]: value }))
  }

  // L1: returns a structured result instead of a translated string so callers branch on field, not text.
  function validate(): ValidationResult {
    if (!isEdit && !LABEL_REGEX.test(label)) {
      return { field: 'label', message: t('labelError') }
    }
    for (const f of fields) {
      if (!f.required) continue
      const raw = values[f.name]
      if (f.type === 'INT') {
        const n = Number(raw)
        if (raw === '' || raw === undefined || isNaN(n)) {
          return { field: 'value', message: t('fieldRequired', { field: f.displayName }) }
        }
      } else if (f.type === 'BOOL') {
        // boolean always has a value
      } else {
        if (!raw || String(raw).trim() === '') {
          return { field: 'value', message: t('fieldRequired', { field: f.displayName }) }
        }
      }
    }
    return null
  }

  async function handleSubmit() {
    const result = validate()
    if (result) {
      if (result.field === 'label') {
        setLabelError(result.message)
      } else {
        toast.error(result.message)
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
      // H4: call only onSaved on success — do not also call onClose.
      // The parent's onSaved callback owns query invalidation and dialog close.
      onSaved()
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
          {/* L2: DialogDescription required for a11y — Radix warns without it. */}
          <DialogDescription>
            {isEdit ? t('rotateWarning') : t('createSubtitle')}
          </DialogDescription>
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
            {/* M6: aria-describedby + aria-invalid associate the error message with this input. */}
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
              aria-describedby={labelError ? 'cf-label-error' : undefined}
              aria-invalid={!!labelError}
            />
            {labelError && (
              <p id="cf-label-error" className="text-xs text-destructive">{labelError}</p>
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
                // M5: aria-required on the SelectTrigger for BOOL fields.
                <Select
                  value={String(values[f.name] ?? false)}
                  onValueChange={(v) => setField(f.name, v === 'true')}
                >
                  <SelectTrigger
                    id={`cf-${f.name}`}
                    aria-required={f.required ? 'true' : undefined}
                  >
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">{t('boolTrue')}</SelectItem>
                    <SelectItem value="false">{t('boolFalse')}</SelectItem>
                  </SelectContent>
                </Select>
              ) : (
                // M5: pass required={f.required} to Input for native validation + AT announcements.
                <Input
                  id={`cf-${f.name}`}
                  type={f.type === 'SECRET' ? 'password' : f.type === 'INT' ? 'number' : 'text'}
                  autoComplete={f.type === 'SECRET' ? 'new-password' : undefined}
                  placeholder={isEdit && f.type === 'SECRET' ? '••••••••' : undefined}
                  value={String(values[f.name] ?? '')}
                  onChange={(e) => setField(f.name, e.target.value)}
                  required={f.required}
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
