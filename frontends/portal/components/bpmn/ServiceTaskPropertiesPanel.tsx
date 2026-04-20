'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import {
  ConnectorResponse,
  ConnectorSchemaField,
  ConnectorTestResponse,
  getConnectorSchema,
  listConnectors,
  testConnector,
} from '@/lib/api/connectors'
import { useTranslations } from 'next-intl'

const TENANT_CODE = process.env.NEXT_PUBLIC_TENANT_CODE ?? 'default'

interface ServiceTaskPropertiesPanelProps {
  element: any
  modeler: any
}

// ---------------------------------------------------------------------------
// Helper: extract top-level keys from a JSON string into extractFields rows
// ---------------------------------------------------------------------------

function extractFieldsFromJson(jsonString: string): Array<{ variable: string; path: string }> {
  try {
    const parsed = JSON.parse(jsonString)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return []
    return Object.keys(parsed).map(key => ({
      variable: key.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase()),
      path: `$.${key}`,
    }))
  } catch {
    return []
  }
}

// ---------------------------------------------------------------------------
// Helper: read ab:extractFields string from BPMN element
// Format stored: "varName:$.path\nvar2:$.path2"
// ---------------------------------------------------------------------------

function readExtractFields(element: any): Array<{ variable: string; path: string }> {
  const raw: string = element.businessObject.get('ab:extractFields') || ''
  if (!raw.trim()) return []
  return raw
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const colonIdx = line.indexOf(':')
      if (colonIdx === -1) return { variable: line, path: '' }
      return { variable: line.slice(0, colonIdx), path: line.slice(colonIdx + 1) }
    })
}

// ---------------------------------------------------------------------------
// Helper: write extract fields rows back to BPMN element
// ---------------------------------------------------------------------------

function writeExtractFields(
  element: any,
  modeler: any,
  rows: Array<{ variable: string; path: string }>
) {
  const modeling = modeler.get('modeling')
  const value = rows
    .filter(r => r.variable.trim())
    .map(r => `${r.variable}:${r.path}`)
    .join('\n')
  modeling.updateProperties(element, { 'ab:extractFields': value || undefined })
}

/**
 * ServiceTaskPropertiesPanel Component
 *
 * Integrated properties panel for ServiceTask / action block configuration.
 * Provides three tabs:
 *  - Request: connector+path (preferred) or legacy URL, method, secret, response variable
 *  - Extract Fields: field extraction mapping rows
 *  - Contract: import sample JSON or run a live test call to auto-populate extract fields
 *
 * Connector mode (preferred): select a connector → fetch schema → path + JSON body template
 * Legacy mode: raw URL field (shown with deprecated badge when no connector selected)
 */
export default function ServiceTaskPropertiesPanel({
  element,
  modeler,
}: ServiceTaskPropertiesPanelProps) {
  const t = useTranslations('bpmn')
  // ---- Request tab state ----
  const [url, setUrl] = useState('')
  const [method, setMethod] = useState('GET')
  const [secretRef, setSecretRef] = useState('')
  const [responseVariable, setResponseVariable] = useState('apiResult')

  // ---- Connector mode state ----
  const [connectorKey, setConnectorKey] = useState('')
  const [connectorPath, setConnectorPath] = useState('')
  const [bodyTemplate, setBodyTemplate] = useState('')
  const [schemaFields, setSchemaFields] = useState<ConnectorSchemaField[]>([])
  const [schemaLoading, setSchemaLoading] = useState(false)

  // ---- Extract Fields tab state ----
  const [extractFields, setExtractFields] = useState<Array<{ variable: string; path: string }>>([])

  // ---- Connector selector state ----
  const [connectors, setConnectors] = useState<ConnectorResponse[]>([])
  const [selectedConnectorKey, setSelectedConnectorKey] = useState('')
  const [connectorLoadError, setConnectorLoadError] = useState<string | null>(null)

  // ---- Contract tab state ----
  const [contractMode, setContractMode] = useState<'import' | 'test'>('import')
  const [contractJson, setContractJson] = useState('')
  const [contractTestPath, setContractTestPath] = useState('')
  const [contractTestMethod, setContractTestMethod] = useState<
    'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  >('GET')
  const [contractTestResult, setContractTestResult] = useState<ConnectorTestResponse | null>(null)
  const [contractTestLoading, setContractTestLoading] = useState(false)
  const [contractTestError, setContractTestError] = useState<string | null>(null)
  const [contractImportError, setContractImportError] = useState<string | null>(null)

  // ---- Load element values on mount / element change ----
  useEffect(() => {
    if (!element || !modeler) return
    const bo = element.businessObject
    setUrl(bo.get('ab:url') || '')
    setMethod(bo.get('ab:method') || 'GET')
    setSecretRef(bo.get('ab:secretRef') || '')
    setResponseVariable(bo.get('ab:responseVariable') || 'apiResult')
    setExtractFields(readExtractFields(element))
    // Connector mode properties
    const savedConnectorKey = bo.get('ab:connector') || ''
    setConnectorKey(savedConnectorKey)
    setSelectedConnectorKey(savedConnectorKey)
    setConnectorPath(bo.get('ab:path') || '')
    setBodyTemplate(bo.get('ab:body') || '')
  }, [element, modeler])

  // ---- Fetch connectors on mount ----
  useEffect(() => {
    listConnectors(TENANT_CODE)
      .then(data => {
        setConnectors(data)
        setConnectorLoadError(null)
      })
      .catch(err => {
        const message = err instanceof Error ? err.message : 'Failed to load connectors'
        setConnectorLoadError(message)
      })
  }, [])

  // ---- BPMN write helpers ----

  const handleUrlChange = (value: string) => {
    setUrl(value)
    modeler.get('modeling').updateProperties(element, { 'ab:url': value || undefined })
  }

  const handleMethodChange = (value: string) => {
    setMethod(value)
    modeler.get('modeling').updateProperties(element, { 'ab:method': value })
  }

  const handleSecretRefChange = (value: string) => {
    setSecretRef(value)
    modeler.get('modeling').updateProperties(element, { 'ab:secretRef': value || undefined })
  }

  const handleResponseVariableChange = (value: string) => {
    setResponseVariable(value)
    modeler.get('modeling').updateProperties(element, {
      'ab:responseVariable': value || undefined,
    })
  }

  const handleConnectorPathChange = (value: string) => {
    setConnectorPath(value)
    modeler.get('modeling').updateProperties(element, { 'ab:path': value || undefined })
  }

  const handleBodyTemplateChange = (value: string) => {
    setBodyTemplate(value)
    modeler.get('modeling').updateProperties(element, { 'ab:body': value || undefined })
  }

  const handleExtractFieldChange = (
    index: number,
    field: 'variable' | 'path',
    value: string
  ) => {
    const updated = extractFields.map((row, i) =>
      i === index ? { ...row, [field]: value } : row
    )
    setExtractFields(updated)
    writeExtractFields(element, modeler, updated)
  }

  const handleAddExtractField = () => {
    const updated = [...extractFields, { variable: '', path: '' }]
    setExtractFields(updated)
    writeExtractFields(element, modeler, updated)
  }

  const handleRemoveExtractField = (index: number) => {
    const updated = extractFields.filter((_, i) => i !== index)
    setExtractFields(updated)
    writeExtractFields(element, modeler, updated)
  }

  // ---- Connector selector ----

  const handleConnectorSelect = async (key: string) => {
    setSelectedConnectorKey(key)
    setConnectorKey(key)
    modeler.get('modeling').updateProperties(element, { 'ab:connector': key || undefined })

    if (!key) {
      setSchemaFields([])
      setContractJson('')
      return
    }

    // Clear legacy url when connector mode is active
    setUrl('')
    modeler.get('modeling').updateProperties(element, { 'ab:url': undefined })

    // Pre-load sampleSchema into Contract tab
    const connector = connectors.find(c => c.connectorKey === key)
    if (connector?.sampleSchema) {
      setContractJson(connector.sampleSchema)
    }

    // Fetch schema fields for body builder
    setSchemaLoading(true)
    try {
      const fields = await getConnectorSchema(TENANT_CODE, key)
      setSchemaFields(fields)

      // Auto-populate extract fields from schema if currently empty
      if (fields.length > 0) {
        setExtractFields(current => {
          if (current.length > 0) return current
          const autoRows = fields
            .filter(f => f.type !== 'object' && f.type !== 'array')
            .map(f => ({
              variable: f.key.replace(/_([a-z])/g, (_: string, c: string) => c.toUpperCase()),
              path: `$.${f.key}`,
            }))
          writeExtractFields(element, modeler, autoRows)
          return autoRows
        })
      }
    } finally {
      setSchemaLoading(false)
    }
  }

  // ---- Contract tab: apply parsed JSON to extractFields ----

  const handleApplyContract = (jsonSource: string) => {
    const rows = extractFieldsFromJson(jsonSource)
    if (rows.length === 0) {
      setContractImportError('Invalid JSON or not a plain object. Please paste a valid JSON object.')
      return
    }
    setContractImportError(null)
    setExtractFields(rows)
    writeExtractFields(element, modeler, rows)
  }

  // ---- Contract tab: test call ----

  const handleTestCall = async () => {
    if (!selectedConnectorKey || !contractTestPath) return
    setContractTestLoading(true)
    setContractTestError(null)
    setContractTestResult(null)
    try {
      const result = await testConnector(TENANT_CODE, selectedConnectorKey, {
        path: contractTestPath,
        method: contractTestMethod,
      })
      setContractTestResult(result)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Test call failed'
      setContractTestError(message)
    } finally {
      setContractTestLoading(false)
    }
  }

  const statusBadgeVariant = (
    code: number
  ): 'default' | 'secondary' | 'destructive' | 'outline' => {
    if (code >= 200 && code < 300) return 'default'
    if (code >= 400) return 'destructive'
    return 'secondary'
  }

  return (
    <div className="space-y-3 p-3">
      {/* Connector selector */}
      <Card>
        <CardHeader className="pb-2 pt-3 px-3">
          <CardTitle className="text-xs font-semibold">{t('connector')}</CardTitle>
          <CardDescription className="text-xs">{t('connectorDesc')}</CardDescription>
        </CardHeader>
        <CardContent className="px-3 pb-3">
          <Select value={selectedConnectorKey} onValueChange={handleConnectorSelect}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue placeholder="(none)" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="">
                (none)
              </SelectItem>
              {connectors.map(c => (
                <SelectItem key={c.connectorKey} value={c.connectorKey}>
                  {c.displayName} ({c.connectorKey})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {connectorLoadError && (
            <p className="text-xs text-destructive mt-1">{connectorLoadError}</p>
          )}
        </CardContent>
      </Card>

      {/* Tabbed panel */}
      <Tabs defaultValue="request">
        <TabsList className="w-full grid grid-cols-3 h-8 text-xs">
          <TabsTrigger value="request" className="text-xs">{t('requestTab')}</TabsTrigger>
          <TabsTrigger value="contract" className="text-xs">{t('contractTab')}</TabsTrigger>
          <TabsTrigger value="extract" className="text-xs">{t('extractFieldsTab')}</TabsTrigger>
        </TabsList>

        {/* ------------------------------------------------------------------ */}
        {/* REQUEST TAB                                                          */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="request">
          <Card>
            <CardContent className="space-y-3 px-3 pb-3 pt-3">

              {/* Connector mode: path + body builder */}
              {connectorKey && (
                <div className="space-y-3">
                  <div className="space-y-1">
                    <Label className="text-xs">{t('path')}</Label>
                    <Input
                      value={connectorPath}
                      onChange={e => handleConnectorPathChange(e.target.value)}
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
                      onChange={e => handleBodyTemplateChange(e.target.value)}
                      className="text-xs font-mono min-h-[100px] resize-y"
                      placeholder={'{\n  "requestId": "${requestId}",\n  "amount": ${amount}\n}'}
                    />
                    {schemaFields.length > 0 && (
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
                              const newBody = bodyTemplate + token
                              handleBodyTemplateChange(newBody)
                            }}
                          >
                            {f.key}
                            <span className="ml-1 text-muted-foreground">{f.type}</span>
                          </Badge>
                        ))}
                      </div>
                    )}
                    {schemaLoading && (
                      <p className="text-xs text-muted-foreground">{t('loadingSchema')}</p>
                    )}
                  </div>
                </div>
              )}

              {/* Legacy URL mode: shown only when no connector selected */}
              {!connectorKey && (
                <div className="space-y-1">
                  <Label className="text-xs flex items-center gap-2">
                    {t('urlLabel')}
                    <Badge variant="outline" className="text-xs text-amber-600">{t('deprecated')}</Badge>
                  </Label>
                  <Input
                    value={url}
                    onChange={e => handleUrlChange(e.target.value)}
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
                <Select value={method} onValueChange={handleMethodChange}>
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
                  onChange={e => handleSecretRefChange(e.target.value)}
                  className="h-8 text-xs"
                  placeholder="e.g. my-api-key-secret"
                />
              </div>
              <div>
                <Label className="text-xs">{t('storeResponseAs')}</Label>
                <Input
                  value={responseVariable}
                  onChange={e => handleResponseVariableChange(e.target.value)}
                  className="h-8 text-xs"
                  placeholder="apiResult"
                />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ------------------------------------------------------------------ */}
        {/* CONTRACT TAB                                                         */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="contract">
          <Card>
            <CardContent className="space-y-3 px-3 pb-3 pt-3">
              {/* Mode toggle */}
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant={contractMode === 'import' ? 'default' : 'outline'}
                  className="flex-1 h-7 text-xs"
                  onClick={() => setContractMode('import')}
                >
                  {t('pasteJson')}
                </Button>
                <Button
                  size="sm"
                  variant={contractMode === 'test' ? 'default' : 'outline'}
                  className="flex-1 h-7 text-xs"
                  onClick={() => setContractMode('test')}
                >
                  {t('testCall')}
                </Button>
              </div>

              {/* Import mode */}
              {contractMode === 'import' && (
                <div className="space-y-2">
                  <Label className="text-xs">{t('sampleResponseJson')}</Label>
                  <Textarea
                    value={contractJson}
                    onChange={e => setContractJson(e.target.value)}
                    className="text-xs font-mono min-h-[120px] resize-y"
                    placeholder={'{\n  "id": 123,\n  "status": "approved"\n}'}
                  />
                  <Button
                    size="sm"
                    className="w-full h-7 text-xs"
                    onClick={() => handleApplyContract(contractJson)}
                    disabled={!contractJson.trim()}
                  >
                    {t('applyContract')}
                  </Button>
                  {contractImportError && (
                    <p className="text-xs text-destructive">{contractImportError}</p>
                  )}
                </div>
              )}

              {/* Test Call mode */}
              {contractMode === 'test' && (
                <div className="space-y-2">
                  {!selectedConnectorKey && (
                    <p className="text-xs text-muted-foreground">{t('selectConnectorForTest')}</p>
                  )}
                  <div className="flex gap-2">
                    <Select
                      value={contractTestMethod}
                      onValueChange={v =>
                        setContractTestMethod(
                          v as 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
                        )
                      }
                    >
                      <SelectTrigger className="h-8 text-xs w-24 shrink-0">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {(['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const).map(m => (
                          <SelectItem key={m} value={m}>
                            {m}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Input
                      value={contractTestPath}
                      onChange={e => setContractTestPath(e.target.value)}
                      className="h-8 text-xs flex-1"
                      placeholder="/api/resource"
                    />
                  </div>
                  <Button
                    size="sm"
                    className="w-full h-7 text-xs"
                    onClick={handleTestCall}
                    disabled={
                      contractTestLoading || !selectedConnectorKey || !contractTestPath.trim()
                    }
                  >
                    {contractTestLoading ? t('sending') : t('send')}
                  </Button>

                  {contractTestError && (
                    <p className="text-xs text-destructive">{contractTestError}</p>
                  )}

                  {contractTestResult && (
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <Badge variant={statusBadgeVariant(contractTestResult.statusCode)}>
                          {contractTestResult.statusCode}
                        </Badge>
                        <span className="text-xs text-muted-foreground">
                          {contractTestResult.durationMs}ms
                        </span>
                        {contractTestResult.truncated && (
                          <span className="text-xs text-amber-600">{t('truncated')}</span>
                        )}
                      </div>
                      <Textarea
                        readOnly
                        value={contractTestResult.body}
                        className="text-xs font-mono min-h-[100px] resize-y"
                      />
                      <Button
                        size="sm"
                        className="w-full h-7 text-xs"
                        onClick={() => handleApplyContract(contractTestResult.body)}
                        disabled={!contractTestResult.body}
                      >
                        {t('applyContract')}
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ------------------------------------------------------------------ */}
        {/* EXTRACT FIELDS TAB                                                   */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="extract">
          <Card>
            <CardContent className="space-y-2 px-3 pb-3 pt-3">
              {extractFields.length === 0 && (
                <p className="text-xs text-muted-foreground">{t('noExtractFields')}</p>
              )}

              {extractFields.map((row, index) => (
                <div key={index} className="flex gap-1 items-center">
                  <Input
                    value={row.variable}
                    onChange={e => handleExtractFieldChange(index, 'variable', e.target.value)}
                    className="h-7 text-xs flex-1"
                    placeholder="varName"
                  />
                  <Input
                    value={row.path}
                    onChange={e => handleExtractFieldChange(index, 'path', e.target.value)}
                    className="h-7 text-xs flex-1 font-mono"
                    placeholder="$.field"
                  />
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0 text-destructive shrink-0"
                    onClick={() => handleRemoveExtractField(index)}
                  >
                    x
                  </Button>
                </div>
              ))}

              <Button
                variant="outline"
                size="sm"
                className="w-full h-7 text-xs"
                onClick={handleAddExtractField}
              >
                {t('addRow')}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
