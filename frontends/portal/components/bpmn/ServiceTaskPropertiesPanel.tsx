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
import { X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useDtdsConnectors } from '@/hooks/useDtdsConnectors'
import { useDtdsOperations } from '@/hooks/useDtdsOperations'
import { useDtdsFields } from '@/hooks/useDtdsFields'
import { DtdsOperationPicker } from './DtdsOperationPicker'
import { DtdsFieldTree } from './DtdsFieldTree'
import { DtdsInputFieldForm } from './DtdsInputFieldForm'
import type { FieldEntry } from '@/lib/api/dtds'
import {
  ACTION_TYPES,
  getApplicableActionTypes,
  setActionType,
  readFlowableField,
  writeFlowableField,
  getNotificationTemplates,
} from '@/lib/bpmn/flowable-properties-provider'
import { VariableComboBoxBpmnAdapter } from '@/components/bpmn/VariableComboBoxBpmnAdapter'


interface ServiceTaskPropertiesPanelProps {
  element: any
  modeler: any
  onShowNativePanel?: () => void
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

// ---------------------------------------------------------------------------
// Helper: produce a human-readable error message for connector field errors.
// Strips raw Spring/Zod validation formats like "(Field 'connection') value is undefined"
// and replaces them with a concise, user-friendly string.
// ---------------------------------------------------------------------------

function formatConnectorError(raw: string): string {
  if (!raw) return 'Failed to load connectors.'
  // Detect Spring Boot / Zod style "(Field 'xxx') value is undefined" patterns
  const technicalPattern = /^\(Field '.*?'\)\s+value is/i
  if (technicalPattern.test(raw)) {
    return 'Connector configuration is incomplete. Please select a valid connector.'
  }
  return raw
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
  onShowNativePanel,
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
  // Track whether the user has interacted with the connector select.
  // Errors are suppressed until isDirty=true to avoid noise on initial render.
  const [connectorFieldDirty, setConnectorFieldDirty] = useState(false)

  // ---- Connector health state ----
  const [connectorHealthy, setConnectorHealthy] = useState<boolean | null>(null)

  // ---- DTDS state ----
  const [selectedOperationId, setSelectedOperationId] = useState('')
  const [inputMappings, setInputMappings] = useState<Array<{ path: string; processVariable: string }>>([])

  const dtdsConnectors = useDtdsConnectors()
  const dtdsOperations = useDtdsOperations(connectorKey || null)
  const dtdsOutputFields = useDtdsFields(connectorKey || null, selectedOperationId || null, 'output')
  const dtdsInputFields = useDtdsFields(connectorKey || null, selectedOperationId || null, 'input')

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

  // ---- Human Approval state ----
  const [outcomeVar, setOutcomeVar] = useState('')

  // ---- Send Notification state ----
  const [notifChannel, setNotifChannel] = useState('email')
  const [notifTemplateKey, setNotifTemplateKey] = useState('')
  const [notifCondition, setNotifCondition] = useState('')
  const [notifTemplates, setNotifTemplates] = useState<Array<{ key: string; name: string }>>([])

  // ---- Manual Step state ----
  const [msDescription, setMsDescription] = useState('')
  const [msConfirmation, setMsConfirmation] = useState('false')

  // ---- Call Subprocess state ----
  const [subCalledElement, setSubCalledElement] = useState('')

  // ---- Set Variables state — rows of { name, value } ----
  const [svRows, setSvRows] = useState<Array<{ name: string; value: string }>>([])
  const [processDefinitions, setProcessDefinitions] = useState<Array<{ key: string; name: string }>>([])
  const [processDefsLoading, setProcessDefsLoading] = useState(true)
  useEffect(() => {
    // Sync process definition options from the lib module-level cache (populated by BpmnDesigner)
    import('@/lib/bpmn/flowable-properties-provider')
      .then(({ getProcessDefinitionOptions }) => {
        setProcessDefinitions(getProcessDefinitionOptions())
      })
      .catch(err => {
        console.error('Failed to load process definitions', err)
      })
      .finally(() => setProcessDefsLoading(false))
  }, [])

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
    setSelectedOperationId(bo.get('ab:operationId') || '')
    // Reset dirty state when switching to a different element so errors are suppressed
    // until the user interacts with the connector select on the new element.
    setConnectorFieldDirty(false)

    // Input mappings: stored as "path:${var}\npath2:${var2}"
    const rawMappings: string = bo.get('ab:inputMappings') || ''
    if (rawMappings.trim()) {
      const parsed = rawMappings
        .split('\n')
        .map((line: string) => line.trim())
        .filter(Boolean)
        .map((line: string) => {
          const idx = line.indexOf(':')
          if (idx === -1) return { path: line, processVariable: '' }
          return { path: line.slice(0, idx), processVariable: line.slice(idx + 1) }
        })
      setInputMappings(parsed)
    } else {
      setInputMappings([])
    }
    // Human Approval fields
    setOutcomeVar(readFlowableField(element, 'outcomeVariable'))
    // Send Notification fields
    setNotifChannel(readFlowableField(element, 'channel') || 'email')
    setNotifTemplateKey(readFlowableField(element, 'templateKey'))
    setNotifCondition(readFlowableField(element, 'condition'))
    // Manual Step fields
    setMsDescription(readFlowableField(element, 'stepDescription'))
    setMsConfirmation(readFlowableField(element, 'confirmationRequired') || 'false')
    // Call Subprocess fields
    setSubCalledElement(bo.get('calledElement') || '')
    // Set Variables: read var.* flowable:field entries
    const ext = bo.extensionElements
    const varFields: Array<{ name: string; value: string }> = []
    if (ext?.values) {
      for (const v of ext.values as any[]) {
        if (v.$type === 'flowable:Field' && (v.name ?? '').startsWith('var.')) {
          varFields.push({ name: v.name.slice(4), value: v.expression ?? v.string ?? '' })
        }
      }
    }
    setSvRows(varFields)
  }, [element, modeler])

  useEffect(() => {
    setNotifTemplates(getNotificationTemplates())
  }, [])

  // ---- Fetch connectors on mount ----
  useEffect(() => {
    listConnectors()
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
    setConnectorFieldDirty(true)
    setSelectedConnectorKey(key)
    setConnectorKey(key)
    setConnectorHealthy(null)
    modeler.get('modeling').updateProperties(element, { 'ab:connector': key || undefined })

    if (!key) {
      setSchemaFields([])
      setContractJson('')
      return
    }

    // Quick health check for selected connector
    fetch(`/api/proxy/admin/connectors/${encodeURIComponent(key)}/health`)
      .then(res => setConnectorHealthy(res.ok))
      .catch(() => setConnectorHealthy(false))

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
      const fields = await getConnectorSchema(key)
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

  // ---- DTDS operation selection ----

  const handleOperationSelect = (operationId: string) => {
    setSelectedOperationId(operationId)
    modeler
      .get('modeling')
      .updateProperties(element, { 'ab:operationId': operationId || undefined })
    setInputMappings([])
    modeler
      .get('modeling')
      .updateProperties(element, { 'ab:inputMappings': undefined })
  }

  const handleInputMappingChange = (path: string, processVariable: string) => {
    const updated = inputMappings.some(m => m.path === path)
      ? inputMappings.map(m => (m.path === path ? { path, processVariable } : m))
      : [...inputMappings, { path, processVariable }]
    setInputMappings(updated)
    const serialized = updated
      .filter(m => m.processVariable.trim())
      .map(m => `${m.path}:${m.processVariable}`)
      .join('\n')
    modeler
      .get('modeling')
      .updateProperties(element, { 'ab:inputMappings': serialized || undefined })
  }

  const handleOutputFieldClick = (field: FieldEntry) => {
    const varName = field.path
      .split('.')
      .pop()
      ?.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase()) ?? field.path
    const token = `\${${varName}}`
    handleBodyTemplateChange(bodyTemplate + token)
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
      const result = await testConnector(selectedConnectorKey, {
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

  // ACTION_TYPES imported from lib/bpmn/flowable-properties-provider — do not duplicate here
  const applicableActionTypes = getApplicableActionTypes(element)

  const currentActionType: string =
    element.businessObject?.get?.('flowable:actionType') ??
    element.businessObject?.['flowable:actionType'] ??
    ''

  const handleActionTypeChange = (value: string) => {
    queueMicrotask(() => {
      const modeling = modeler.get('modeling')
      setActionType(element, modeling, value, modeler)
    })
  }

  return (
    <div className="space-y-3 p-2">
      {onShowNativePanel && (
        <button
          type="button"
          onClick={onShowNativePanel}
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6"/>
          </svg>
          General properties
        </button>
      )}
      {/* Action Type selector — hidden when element type has no applicable action types */}
      {applicableActionTypes.length > 1 && (
      <Card>
        <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
          <CardTitle className="text-xs font-semibold">Action Type</CardTitle>
        </CardHeader>
        <CardContent className="px-3 pb-3 pt-2">
          <Select value={currentActionType || '__none__'} onValueChange={v => handleActionTypeChange(v === '__none__' ? '' : v)}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue placeholder="(none)" />
            </SelectTrigger>
            <SelectContent>
              {applicableActionTypes.map(t => (
                <SelectItem key={t.value || '__none__'} value={t.value || '__none__'}>
                  {t.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardContent>
      </Card>
      )}

      {/* ── HUMAN APPROVAL ────────────────────────────────────────────────── */}
      {currentActionType === 'HUMAN_APPROVAL' && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Assignment</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 pt-2 space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">Candidate Groups</Label>
              <VariableComboBoxBpmnAdapter
                key={`cg-${element.id}`}
                mode="multi"
                sourceKeys={['pss-candidate-groups', 'dtds-variables-string', 'custody-feel']}
                processId={element.businessObject?.$parent?.id}
                activityId={element.businessObject?.id}
                getValue={() => element.businessObject.candidateGroups || ''}
                setValue={(v) => modeler.get('modeling').updateProperties(element, { candidateGroups: v || undefined })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Assignee</Label>
              <VariableComboBoxBpmnAdapter
                key={`as-${element.id}`}
                mode="single"
                sourceKeys={['dtds-variables-string']}
                processId={element.businessObject?.$parent?.id}
                activityId={element.businessObject?.id}
                getValue={() => element.businessObject.assignee || ''}
                setValue={(v) => modeler.get('modeling').updateProperties(element, { assignee: v || undefined })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Candidate Users</Label>
              <VariableComboBoxBpmnAdapter
                key={`cu-${element.id}`}
                mode="multi"
                sourceKeys={['dtds-variables-string']}
                processId={element.businessObject?.$parent?.id}
                activityId={element.businessObject?.id}
                getValue={() => element.businessObject.candidateUsers || ''}
                setValue={(v) => modeler.get('modeling').updateProperties(element, { candidateUsers: v || undefined })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Form Key</Label>
              <VariableComboBoxBpmnAdapter
                key={`fk-${element.id}`}
                mode="single"
                sourceKeys={['forms-deployed']}
                processId={element.businessObject?.$parent?.id}
                activityId={element.businessObject?.id}
                getValue={() => element.businessObject.formKey || element.businessObject.$attrs?.['flowable:formKey'] || ''}
                setValue={(v) => modeler.get('modeling').updateProperties(element, { formKey: v || undefined })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Outcome Variable</Label>
              <Input
                className="h-8 text-xs"
                value={outcomeVar}
                placeholder="approvalOutcome"
                onChange={e => setOutcomeVar(e.target.value)}
                onBlur={() => writeFlowableField(element, modeler.get('modeling'), 'outcomeVariable', outcomeVar)}
              />
            </div>
          </CardContent>
        </Card>
      )}

      {/* ── SEND NOTIFICATION ─────────────────────────────────────────────── */}
      {currentActionType === 'SEND_NOTIFICATION' && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Notification</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">Channel</Label>
              <Select value={notifChannel} onValueChange={v => {
                setNotifChannel(v)
                writeFlowableField(element, modeler.get('modeling'), 'channel', v || 'email')
              }}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="email">Email</SelectItem>
                  <SelectItem value="slack" disabled>Slack (coming soon)</SelectItem>
                  <SelectItem value="whatsapp" disabled>WhatsApp (coming soon)</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Recipient</Label>
              <VariableComboBoxBpmnAdapter
                key={`nr-${element.id}`}
                mode="single"
                sourceKeys={['dtds-variables-string']}
                processId={element.businessObject?.$parent?.id}
                activityId={element.businessObject?.id}
                placeholder="${initiator} or email@example.com"
                getValue={() => readFlowableField(element, 'recipient')}
                setValue={(v) => writeFlowableField(element, modeler.get('modeling'), 'recipient', v)}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Template</Label>
              <Select value={notifTemplateKey || '__none__'} onValueChange={v => {
                const val = v === '__none__' ? '' : v
                setNotifTemplateKey(val)
                writeFlowableField(element, modeler.get('modeling'), 'templateKey', val)
              }}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="(select template)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__none__">(select template)</SelectItem>
                  {notifTemplates.map(tmpl => (
                    <SelectItem key={tmpl.key} value={tmpl.key}>{tmpl.name || tmpl.key}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Condition (optional)</Label>
              <Input
                className="h-8 text-xs font-mono"
                value={notifCondition}
                placeholder="${status == 'approved'}"
                onChange={e => setNotifCondition(e.target.value)}
                onBlur={() => writeFlowableField(element, modeler.get('modeling'), 'condition', notifCondition)}
              />
            </div>
          </CardContent>
        </Card>
      )}

      {/* Connector selector — prefers DTDS list, falls back to legacy connector list */}
      {currentActionType === 'EXTERNAL_API_CALL' && (<>
      <Card>
        <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
          <CardTitle className="text-xs font-semibold">{t('connector')}</CardTitle>
          <CardDescription className="text-xs">{t('connectorDesc')}</CardDescription>
        </CardHeader>
        <CardContent className="px-3 pb-3 space-y-3">
          {dtdsConnectors.isLoading && (
            <p className="text-xs text-muted-foreground">Loading connectors…</p>
          )}
          {!dtdsConnectors.isLoading && (
            <div className="flex items-center gap-2">
              <Select
                value={selectedConnectorKey || '__none__'}
                onValueChange={v => handleConnectorSelect(v === '__none__' ? '' : v)}
              >
                <SelectTrigger className="h-8 text-xs flex-1">
                  <SelectValue placeholder="(none)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__none__">(none)</SelectItem>
                  {dtdsConnectors.connectors.length > 0
                    ? dtdsConnectors.connectors.map(c => (
                        <SelectItem key={c.key} value={c.key}>
                          {c.displayName} ({c.key})
                        </SelectItem>
                      ))
                    : connectors.map(c => (
                        <SelectItem key={c.connectorKey} value={c.connectorKey}>
                          {c.displayName} ({c.connectorKey})
                        </SelectItem>
                      ))}
                </SelectContent>
              </Select>
              {connectorHealthy === true && (
                <span className="flex items-center gap-1 text-xs shrink-0" style={{ color: '#149ba5' }}>
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#149ba5', display: 'inline-block' }} />
                  Connected
                </span>
              )}
              {connectorHealthy === false && (
                <span className="flex items-center gap-1 text-xs shrink-0" style={{ color: 'var(--destructive)' }}>
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--destructive)', display: 'inline-block' }} />
                  Unavailable
                </span>
              )}
            </div>
          )}
          {/* Show a neutral helper when no connector is selected and the field has not been touched */}
          {!selectedConnectorKey && !connectorFieldDirty && (
            <p className="text-xs text-muted-foreground mt-1">
              Select a connector to configure the request.
            </p>
          )}
          {/* Only surface connector load errors after the user has interacted with the field */}
          {connectorFieldDirty && (dtdsConnectors.error || connectorLoadError) && (
            <p className="text-xs text-destructive mt-1">
              {formatConnectorError(dtdsConnectors.error?.message ?? connectorLoadError ?? '')}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Tabbed panel */}
      <Tabs defaultValue="request">
        <TabsList className={`w-full grid h-8 text-xs ${connectorKey ? 'grid-cols-5' : 'grid-cols-3'}`}>
          <TabsTrigger value="request" className="text-xs">Setup</TabsTrigger>
          <TabsTrigger value="contract" className="text-xs">Contract</TabsTrigger>
          <TabsTrigger value="extract" className="text-xs">Extract</TabsTrigger>
          {connectorKey && (
            <TabsTrigger value="inputs" className="text-xs">Inputs</TabsTrigger>
          )}
          {connectorKey && (
            <TabsTrigger value="outputs" className="text-xs">Outputs</TabsTrigger>
          )}
        </TabsList>

        {/* ------------------------------------------------------------------ */}
        {/* REQUEST TAB                                                          */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="request">
          <Card>
            <CardContent className="space-y-3 px-3 pb-3 pt-3">

              {/* Connector mode: operation picker + path + body builder */}
              {connectorKey && (
                <div className="space-y-3">
                  <div className="space-y-1">
                    <Label className="text-xs">Operation</Label>
                    <DtdsOperationPicker
                      operations={dtdsOperations.operations}
                      value={selectedOperationId}
                      isLoading={dtdsOperations.isLoading}
                      error={dtdsOperations.error}
                      onChange={handleOperationSelect}
                    />
                  </div>

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
                              onClick={() => handleOutputFieldClick(f)}
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
                              handleBodyTemplateChange(bodyTemplate + token)
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
                  <label htmlFor={`ef-var-${index}`} className="sr-only">Variable name {index + 1}</label>
                  <Input
                    id={`ef-var-${index}`}
                    value={row.variable}
                    onChange={e => handleExtractFieldChange(index, 'variable', e.target.value)}
                    className="h-7 text-xs flex-1"
                    placeholder="varName"
                  />
                  <label htmlFor={`ef-path-${index}`} className="sr-only">JSON path {index + 1}</label>
                  <Input
                    id={`ef-path-${index}`}
                    value={row.path}
                    onChange={e => handleExtractFieldChange(index, 'path', e.target.value)}
                    className="h-7 text-xs flex-1 font-mono"
                    placeholder="$.field"
                  />
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0 text-destructive shrink-0"
                    aria-label={`Remove extract field row ${index + 1}${row.variable ? ` (${row.variable})` : ''}`}
                    onClick={() => handleRemoveExtractField(index)}
                  >
                    <X className="h-3 w-3" />
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

        {/* ------------------------------------------------------------------ */}
        {/* INPUTS TAB (DTDS)                                                    */}
        {/* ------------------------------------------------------------------ */}
        {connectorKey && (
          <TabsContent value="inputs">
            <Card>
              <CardContent className="space-y-3 px-3 pb-3 pt-3">
                {!selectedOperationId && (
                  <p className="text-xs text-muted-foreground">
                    Select an operation in the Request tab to map input fields.
                  </p>
                )}
                {selectedOperationId && (
                  <DtdsInputFieldForm
                    fields={dtdsInputFields.fields}
                    isLoading={dtdsInputFields.isLoading}
                    error={dtdsInputFields.error}
                    mappings={inputMappings}
                    onMappingChange={handleInputMappingChange}
                  />
                )}
              </CardContent>
            </Card>
          </TabsContent>
        )}

        {/* ------------------------------------------------------------------ */}
        {/* OUTPUTS TAB (DTDS)                                                   */}
        {/* ------------------------------------------------------------------ */}
        {connectorKey && (
          <TabsContent value="outputs">
            <Card>
              <CardContent className="space-y-3 px-3 pb-3 pt-3">
                {!selectedOperationId && (
                  <p className="text-xs text-muted-foreground">
                    Select an operation in the Request tab to see output fields.
                  </p>
                )}
                {selectedOperationId && (
                  <>
                    <p className="text-xs text-muted-foreground">
                      Click a field to insert it into the request body template.
                    </p>
                    <DtdsFieldTree
                      fields={dtdsOutputFields.fields}
                      isLoading={dtdsOutputFields.isLoading}
                      error={dtdsOutputFields.error}
                      onFieldClick={handleOutputFieldClick}
                    />
                  </>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        )}
      </Tabs>
      </>)}

      {/* ── CALL SUBPROCESS ───────────────────────────────────────────────── */}
      {currentActionType === 'CALL_SUBPROCESS' && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Call Subprocess</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 pt-2 space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">Process Key (calledElement)</Label>
              <Select
                value={subCalledElement || '__none__'}
                onValueChange={v => {
                  const val = v === '__none__' ? '' : v
                  setSubCalledElement(val)
                  modeler.get('modeling').updateProperties(element, { calledElement: val || undefined })
                }}
              >
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="(select process)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__none__">(select process)</SelectItem>
                  {processDefinitions.map(d => (
                    <SelectItem key={d.key} value={d.key}>{d.name || d.key}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {processDefinitions.length === 0 && !processDefsLoading && (
                <p className="text-xs text-muted-foreground mt-1">
                  No subprocesses available. Deploy a process first.
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                Writes native <code>calledElement</code> attribute — Flowable CallActivity semantics.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* ── SET VARIABLES ─────────────────────────────────────────────────── */}
      {currentActionType === 'SET_VARIABLES' && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Variable Assignments</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 pt-2 space-y-2">
            {svRows.length === 0 && (
              <p className="text-xs text-muted-foreground">No variable assignments. Add one below.</p>
            )}
            {svRows.map((row, idx) => (
              <div key={idx} className="flex gap-1 items-center">
                <label htmlFor={`sv-name-${idx}`} className="sr-only">Variable name {idx + 1}</label>
                <Input
                  id={`sv-name-${idx}`}
                  value={row.name}
                  onChange={e => {
                    const updated = [...svRows]
                    updated[idx] = { ...updated[idx], name: e.target.value }
                    setSvRows(updated)
                  }}
                  onBlur={() => {
                    const modeling = modeler.get('modeling')
                    // Rename: clear old field, write new
                    const old = svRows[idx]
                    if (old.name.trim()) writeFlowableField(element, modeling, `var.${old.name}`, '')
                    if (row.name.trim()) writeFlowableField(element, modeling, `var.${row.name}`, row.value)
                  }}
                  className="h-7 text-xs flex-1"
                  placeholder="varName"
                />
                <label htmlFor={`sv-value-${idx}`} className="sr-only">Variable value {idx + 1}</label>
                <Input
                  id={`sv-value-${idx}`}
                  value={row.value}
                  onChange={e => {
                    const updated = [...svRows]
                    updated[idx] = { ...updated[idx], value: e.target.value }
                    setSvRows(updated)
                  }}
                  onBlur={() => {
                    if (row.name.trim()) {
                      writeFlowableField(element, modeler.get('modeling'), `var.${row.name}`, row.value)
                    }
                  }}
                  className="h-7 text-xs flex-1 font-mono"
                  placeholder="value or ${expr}"
                />
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 w-7 p-0 text-destructive shrink-0"
                  aria-label={`Remove variable row ${idx + 1}${row.name ? ` (${row.name})` : ''}`}
                  onClick={() => {
                    const modeling = modeler.get('modeling')
                    if (row.name.trim()) writeFlowableField(element, modeling, `var.${row.name}`, '')
                    setSvRows(svRows.filter((_, i) => i !== idx))
                  }}
                >
                  <X className="h-3 w-3" />
                </Button>
              </div>
            ))}
            <Button
              variant="outline"
              size="sm"
              className="w-full h-7 text-xs"
              onClick={() => setSvRows([...svRows, { name: '', value: '' }])}
            >
              + Add Variable
            </Button>
          </CardContent>
        </Card>
      )}

      {/* ── MANUAL STEP ───────────────────────────────────────────────────── */}
      {currentActionType === 'MANUAL_STEP' && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Manual Step</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 pt-2 space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">Step Description</Label>
              <Input
                className="h-8 text-xs"
                value={msDescription}
                placeholder="Instructions shown to the assignee"
                onChange={e => setMsDescription(e.target.value)}
                onBlur={() => writeFlowableField(element, modeler.get('modeling'), 'stepDescription', msDescription)}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Confirmation Required</Label>
              <Select
                value={msConfirmation}
                onValueChange={v => {
                  setMsConfirmation(v)
                  const modeling = modeler.get('modeling')
                  queueMicrotask(() => {
                    try {
                      const bpmnReplace = modeler.get('bpmnReplace')
                      if (v === 'true' && element.type === 'bpmn:ManualTask') {
                        const morphed = bpmnReplace.replaceElement(element, { type: 'bpmn:UserTask' })
                        modeling.updateProperties(morphed, {
                          'flowable:actionType': 'MANUAL_STEP',
                          formKey: '__werkflow_confirm_step__',
                        })
                        writeFlowableField(morphed, modeling, 'confirmationRequired', v)
                      } else if (v === 'false' && element.type === 'bpmn:UserTask') {
                        const morphed = bpmnReplace.replaceElement(element, { type: 'bpmn:ManualTask' })
                        modeling.updateProperties(morphed, {
                          'flowable:actionType': 'MANUAL_STEP',
                          formKey: undefined,
                        })
                        writeFlowableField(morphed, modeling, 'confirmationRequired', v)
                      } else {
                        // No morph needed — write directly on current element
                        writeFlowableField(element, modeling, 'confirmationRequired', v)
                      }
                    } catch {
                      // bpmnReplace unavailable — write on current element as fallback
                      writeFlowableField(element, modeling, 'confirmationRequired', v)
                    }
                  })
                }}
              >
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="false">No</SelectItem>
                  <SelectItem value="true">Yes (morphs to UserTask with confirm form)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
