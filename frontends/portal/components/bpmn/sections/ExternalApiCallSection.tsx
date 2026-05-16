'use client'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useTranslations } from 'next-intl'
import { DtdsInputFieldForm } from '@/components/bpmn/DtdsInputFieldForm'
import { DtdsFieldTree } from '@/components/bpmn/DtdsFieldTree'
import { formatConnectorError } from './externalApiHelpers'
import { useExternalApiState } from './useExternalApiState'
import SetupTabContent from './SetupTabContent'
import ContractTabContent from './ContractTabContent'
import ExtractFieldsTabContent from './ExtractFieldsTabContent'

interface ExternalApiCallSectionProps {
  element: any
  modeler: any
}

export default function ExternalApiCallSection({ element, modeler }: ExternalApiCallSectionProps) {
  const t = useTranslations('bpmn')
  const s = useExternalApiState(element, modeler)

  return (
    <>
      <Card>
        <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
          <CardTitle className="text-xs font-semibold">{t('connector')}</CardTitle>
          <CardDescription className="text-xs">{t('connectorDesc')}</CardDescription>
        </CardHeader>
        <CardContent className="px-3 pb-3 space-y-3">
          {s.dtdsConnectors.isLoading && (
            <p className="text-xs text-muted-foreground">Loading connectors…</p>
          )}
          {!s.dtdsConnectors.isLoading && (
            <div className="flex items-center gap-2">
              <Select
                value={s.selectedConnectorKey || '__none__'}
                onValueChange={v => s.handleConnectorSelect(v === '__none__' ? '' : v)}
              >
                <SelectTrigger className="h-8 text-xs flex-1">
                  <SelectValue placeholder="(none)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__none__">(none)</SelectItem>
                  {s.dtdsConnectors.connectors.length > 0
                    ? s.dtdsConnectors.connectors.map(c => (
                        <SelectItem key={c.key} value={c.key}>
                          {c.displayName} ({c.key})
                        </SelectItem>
                      ))
                    : s.connectors.map(c => (
                        <SelectItem key={c.connectorKey} value={c.connectorKey}>
                          {c.displayName} ({c.connectorKey})
                        </SelectItem>
                      ))}
                </SelectContent>
              </Select>
              {s.connectorHealthy === true && (
                <span className="flex items-center gap-1 text-xs shrink-0" style={{ color: '#149ba5' }}>
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#149ba5', display: 'inline-block' }} />
                  Connected
                </span>
              )}
              {s.connectorHealthy === false && (
                <span className="flex items-center gap-1 text-xs shrink-0" style={{ color: 'var(--destructive)' }}>
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--destructive)', display: 'inline-block' }} />
                  Unavailable
                </span>
              )}
            </div>
          )}
          {!s.selectedConnectorKey && !s.connectorFieldDirty && (
            <p className="text-xs text-muted-foreground mt-1">
              Select a connector to configure the request.
            </p>
          )}
          {s.connectorFieldDirty && (s.dtdsConnectors.error || s.connectorLoadError) && (
            <p className="text-xs text-destructive mt-1">
              {formatConnectorError(s.dtdsConnectors.error?.message ?? s.connectorLoadError ?? '')}
            </p>
          )}
        </CardContent>
      </Card>

      <Tabs defaultValue="request">
        <TabsList className={`w-full grid h-8 text-xs ${s.connectorKey ? 'grid-cols-5' : 'grid-cols-3'}`}>
          <TabsTrigger value="request" className="text-xs">Setup</TabsTrigger>
          <TabsTrigger value="contract" className="text-xs">Contract</TabsTrigger>
          <TabsTrigger value="extract" className="text-xs">Extract</TabsTrigger>
          {s.connectorKey && <TabsTrigger value="inputs" className="text-xs">Inputs</TabsTrigger>}
          {s.connectorKey && <TabsTrigger value="outputs" className="text-xs">Outputs</TabsTrigger>}
        </TabsList>

        <TabsContent value="request">
          <SetupTabContent
            connectorKey={s.connectorKey}
            connectorPath={s.connectorPath}
            bodyTemplate={s.bodyTemplate}
            schemaFields={s.schemaFields}
            schemaLoading={s.schemaLoading}
            url={s.url}
            method={s.method}
            secretRef={s.secretRef}
            responseVariable={s.responseVariable}
            selectedOperationId={s.selectedOperationId}
            dtdsOperations={s.dtdsOperations}
            dtdsOutputFields={s.dtdsOutputFields}
            onConnectorPathChange={s.handleConnectorPathChange}
            onBodyTemplateChange={s.handleBodyTemplateChange}
            onUrlChange={s.handleUrlChange}
            onMethodChange={s.handleMethodChange}
            onSecretRefChange={s.handleSecretRefChange}
            onResponseVariableChange={s.handleResponseVariableChange}
            onOperationSelect={s.handleOperationSelect}
            onOutputFieldClick={s.handleOutputFieldClick}
          />
        </TabsContent>

        <TabsContent value="contract">
          <ContractTabContent
            selectedConnectorKey={s.selectedConnectorKey}
            initialContractJson={s.contractJson}
            onApplyContract={s.handleApplyContract}
          />
        </TabsContent>

        <TabsContent value="extract">
          <ExtractFieldsTabContent
            extractFields={s.extractFields}
            onFieldChange={s.handleExtractFieldChange}
            onAddField={s.handleAddExtractField}
            onRemoveField={s.handleRemoveExtractField}
          />
        </TabsContent>

        {s.connectorKey && (
          <TabsContent value="inputs">
            <Card>
              <CardContent className="space-y-3 px-3 pb-3 pt-3">
                {!s.selectedOperationId && (
                  <p className="text-xs text-muted-foreground">
                    Select an operation in the Request tab to map input fields.
                  </p>
                )}
                {s.selectedOperationId && (
                  <DtdsInputFieldForm
                    fields={s.dtdsInputFields.fields}
                    isLoading={s.dtdsInputFields.isLoading}
                    error={s.dtdsInputFields.error}
                    mappings={s.inputMappings}
                    onMappingChange={s.handleInputMappingChange}
                  />
                )}
              </CardContent>
            </Card>
          </TabsContent>
        )}

        {s.connectorKey && (
          <TabsContent value="outputs">
            <Card>
              <CardContent className="space-y-3 px-3 pb-3 pt-3">
                {!s.selectedOperationId && (
                  <p className="text-xs text-muted-foreground">
                    Select an operation in the Request tab to see output fields.
                  </p>
                )}
                {s.selectedOperationId && (
                  <>
                    <p className="text-xs text-muted-foreground">
                      Click a field to insert it into the request body template.
                    </p>
                    <DtdsFieldTree
                      fields={s.dtdsOutputFields.fields}
                      isLoading={s.dtdsOutputFields.isLoading}
                      error={s.dtdsOutputFields.error}
                      onFieldClick={s.handleOutputFieldClick}
                    />
                  </>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        )}
      </Tabs>
    </>
  )
}
