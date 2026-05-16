'use client'

import { useCallback, useRef, useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { ConnectorSchemaField } from '@/lib/api/connectors'
import { useTranslations } from 'next-intl'
import { DtdsOperationPicker } from '@/components/bpmn/DtdsOperationPicker'
import { VariableComboBoxBpmnAdapter } from '@/components/bpmn/VariableComboBoxBpmnAdapter'
import type { FieldEntry } from '@/lib/api/dtds'

interface DtdsOperationsState {
  operations: any[]
  isLoading: boolean
  error: Error | null
}

interface DtdsFieldsState {
  fields: FieldEntry[]
  isLoading: boolean
  error: Error | null
}

interface SetupTabContentProps {
  connectorKey: string
  connectorPath: string
  bodyTemplate: string
  schemaFields: ConnectorSchemaField[]
  schemaLoading: boolean
  url: string
  method: string
  secretRef: string
  responseVariable: string
  selectedOperationId: string
  dtdsOperations: DtdsOperationsState
  dtdsOutputFields: DtdsFieldsState
  processId: string
  activityId: string
  onConnectorPathChange: (value: string) => void
  onBodyTemplateChange: (value: string) => void
  onUrlChange: (value: string) => void
  onMethodChange: (value: string) => void
  onSecretRefChange: (value: string) => void
  onResponseVariableChange: (value: string) => void
  onOperationSelect: (operationId: string) => void
  onOutputFieldClick: (field: FieldEntry) => void
}

export default function SetupTabContent({
  connectorKey,
  connectorPath,
  bodyTemplate,
  schemaFields,
  schemaLoading,
  url,
  method,
  secretRef,
  responseVariable,
  selectedOperationId,
  dtdsOperations,
  dtdsOutputFields,
  processId,
  activityId,
  onConnectorPathChange,
  onBodyTemplateChange,
  onUrlChange,
  onMethodChange,
  onSecretRefChange,
  onResponseVariableChange,
  onOperationSelect,
  onOutputFieldClick,
}: SetupTabContentProps) {
  const t = useTranslations('bpmn')
  // F6b: ref to the body Textarea for cursor-position insertion
  const bodyTextareaRef = useRef<HTMLTextAreaElement>(null)
  // F6b: controls whether the variable insertion picker is visible
  const [showBodyVarPicker, setShowBodyVarPicker] = useState(false)
  // HIGH #4: ref for the body variable picker wrapper — used to move focus in on open
  const bodyPickerWrapperRef = useRef<HTMLDivElement>(null)

  // HIGH #2: memoized callbacks for the Path combobox adapter
  const getPathValue = useCallback(() => connectorPath, [connectorPath])
  const setPathValue = useCallback((v: string) => onConnectorPathChange(v), [onConnectorPathChange])

  // HIGH #2: memoized callbacks for the Body variable picker adapter
  const getBodyPickerValue = useCallback(() => '', [])
  const setBodyPickerValue = useCallback(
    (token: string) => {
      const textarea = bodyTextareaRef.current
      if (!textarea) {
        onBodyTemplateChange(bodyTemplate + token)
        return
      }
      const start = textarea.selectionStart ?? bodyTemplate.length
      const end = textarea.selectionEnd ?? bodyTemplate.length
      const next = bodyTemplate.slice(0, start) + token + bodyTemplate.slice(end)
      onBodyTemplateChange(next)
      setShowBodyVarPicker(false)
    },
    [bodyTemplate, onBodyTemplateChange]
  )

  return (
    <Card>
      <CardContent className="space-y-3 px-3 pb-3 pt-3">
        {connectorKey && (
          <div className="space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">Operation</Label>
              <DtdsOperationPicker
                operations={dtdsOperations.operations}
                value={selectedOperationId}
                isLoading={dtdsOperations.isLoading}
                error={dtdsOperations.error}
                onChange={onOperationSelect}
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs">{t('path')}</Label>
              {/* F6a: VariableComboBoxBpmnAdapter for path with ${varName} token support */}
              {/* MEDIUM #7: label prop provides accessible label association */}
              <VariableComboBoxBpmnAdapter
                key={`path-${activityId}`}
                mode="single"
                sourceKeys={['dtds-variables-string']}
                processId={processId}
                activityId={activityId}
                placeholder="/api/resource/${processVar}"
                label={t('path')}
                getValue={getPathValue}
                setValue={setPathValue}
              />
              <p className="text-xs text-muted-foreground">
                Path appended to connector base URL. Use {'${varName}'} for process variables.
              </p>
            </div>

            <div className="space-y-1">
              <Label className="text-xs">{t('requestBody')}</Label>
              <Textarea
                ref={bodyTextareaRef}
                value={bodyTemplate}
                onChange={e => onBodyTemplateChange(e.target.value)}
                className="text-xs font-mono min-h-[100px] resize-y"
                placeholder={'{\n  "requestId": "${requestId}",\n  "amount": ${amount}\n}'}
              />
              {/* F6b: variable insertion affordance for the body textarea */}
              <div className="space-y-1">
                {/* MEDIUM #4: aria-expanded + aria-controls on toggle button */}
                <button
                  type="button"
                  className="text-xs text-muted-foreground underline-offset-2 hover:underline"
                  aria-expanded={showBodyVarPicker}
                  aria-controls="body-variable-picker"
                  onClick={() => {
                    const next = !showBodyVarPicker
                    setShowBodyVarPicker(next)
                    if (next) {
                      setTimeout(() => {
                        bodyPickerWrapperRef.current?.querySelector('input')?.focus()
                      }, 0)
                    }
                  }}
                >
                  {showBodyVarPicker ? 'Hide variable picker' : 'Insert variable'}
                </button>
                {showBodyVarPicker && (
                  <div id="body-variable-picker" ref={bodyPickerWrapperRef}>
                    <VariableComboBoxBpmnAdapter
                      key={`body-var-${activityId}`}
                      mode="single"
                      sourceKeys={['dtds-variables-string', 'dtds-variables-number', 'dtds-variables-date']}
                      processId={processId}
                      activityId={activityId}
                      placeholder="Pick a variable to insert…"
                      getValue={getBodyPickerValue}
                      setValue={setBodyPickerValue}
                    />
                  </div>
                )}
              </div>
              {dtdsOutputFields.fields.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-1">
                  <span className="text-xs text-muted-foreground">{t('schemaFields')}</span>
                  {dtdsOutputFields.fields
                    .filter(f => f.depth === 0)
                    .map(f => (
                      <Badge
                        key={f.path}
                        variant="outline"
                        className="text-xs cursor-pointer hover:bg-accent"
                        onClick={() => onOutputFieldClick(f)}
                      >
                        {f.path}
                        <span className="ml-1 text-muted-foreground">{f.type}</span>
                      </Badge>
                    ))}
                </div>
              )}
              {!dtdsOutputFields.fields.length && schemaFields.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-1">
                  <span className="text-xs text-muted-foreground">{t('schemaFields')}</span>
                  {schemaFields.map(f => (
                    <Badge
                      key={f.key}
                      variant="outline"
                      className="text-xs cursor-pointer hover:bg-accent"
                      onClick={() => {
                        const token = f.type === 'string'
                          ? `"\${${f.key}}"`
                          : `\${${f.key}}`
                        onBodyTemplateChange(bodyTemplate + token)
                      }}
                    >
                      {f.key}
                      <span className="ml-1 text-muted-foreground">{f.type}</span>
                    </Badge>
                  ))}
                </div>
              )}
              {(schemaLoading || dtdsOutputFields.isLoading) && (
                <p className="text-xs text-muted-foreground">{t('loadingSchema')}</p>
              )}
            </div>
          </div>
        )}

        {!connectorKey && (
          <div className="space-y-1">
            <Label className="text-xs flex items-center gap-2">
              {t('urlLabel')}
              <Badge variant="outline" className="text-xs text-amber-600">{t('deprecated')}</Badge>
            </Label>
            <Input
              value={url}
              onChange={e => onUrlChange(e.target.value)}
              className="h-8 text-xs font-mono"
              placeholder="https://api.example.com/endpoint"
            />
            <p className="text-xs text-muted-foreground">
              Select a connector above to use tenant-aware endpoint resolution instead.
            </p>
          </div>
        )}

        <div>
          <Label className="text-xs">{t('method')}</Label>
          <Select value={method} onValueChange={onMethodChange}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(m => (
                <SelectItem key={m} value={m}>
                  {m}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div>
          <Label className="text-xs">{t('secretRef')}</Label>
          <Input
            value={secretRef}
            onChange={e => onSecretRefChange(e.target.value)}
            className="h-8 text-xs"
            placeholder="e.g. my-api-key-secret"
          />
        </div>
        <div>
          <Label className="text-xs">{t('storeResponseAs')}</Label>
          <Input
            value={responseVariable}
            onChange={e => onResponseVariableChange(e.target.value)}
            className="h-8 text-xs"
            placeholder="apiResult"
          />
        </div>
      </CardContent>
    </Card>
  )
}
