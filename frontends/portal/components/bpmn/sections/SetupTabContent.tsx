'use client'

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
              <Input
                value={connectorPath}
                onChange={e => onConnectorPathChange(e.target.value)}
                className="h-8 text-xs font-mono"
                placeholder="/api/resource/${processVar}"
              />
              <p className="text-xs text-muted-foreground">
                Path appended to connector base URL. Use {'${varName}'} for process variables.
              </p>
            </div>

            <div className="space-y-1">
              <Label className="text-xs">{t('requestBody')}</Label>
              <Textarea
                value={bodyTemplate}
                onChange={e => onBodyTemplateChange(e.target.value)}
                className="text-xs font-mono min-h-[100px] resize-y"
                placeholder={'{\n  "requestId": "${requestId}",\n  "amount": ${amount}\n}'}
              />
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
