import { useEffect, useRef, useState } from 'react'
import {
  ConnectorResponse,
  ConnectorSchemaField,
  getConnectorSchema,
  listConnectors,
} from '@/lib/api/connectors'
import { useDtdsConnectors } from '@/hooks/useDtdsConnectors'
import { useDtdsOperations } from '@/hooks/useDtdsOperations'
import { useDtdsFields } from '@/hooks/useDtdsFields'
import type { FieldEntry } from '@/lib/api/dtds'
import {
  extractFieldsFromJson,
  tryExtractFieldsFromJson,
  readExtractFields,
  writeExtractFields,
} from './externalApiHelpers'

export function useExternalApiState(element: any, modeler: any) {
  const [url, setUrl] = useState('')
  const [method, setMethod] = useState('GET')
  const [secretRef, setSecretRef] = useState('')
  const [responseVariable, setResponseVariable] = useState('apiResult')
  const [connectorKey, setConnectorKey] = useState('')
  const [connectorPath, setConnectorPath] = useState('')
  const [bodyTemplate, setBodyTemplate] = useState('')
  const [schemaFields, setSchemaFields] = useState<ConnectorSchemaField[]>([])
  const [schemaLoading, setSchemaLoading] = useState(false)
  const [extractFields, setExtractFields] = useState<Array<{ variable: string; path: string }>>([])
  const [connectors, setConnectors] = useState<ConnectorResponse[]>([])
  const [selectedConnectorKey, setSelectedConnectorKey] = useState('')
  const [connectorLoadError, setConnectorLoadError] = useState<string | null>(null)
  const [connectorFieldDirty, setConnectorFieldDirty] = useState(false)
  const [connectorHealthy, setConnectorHealthy] = useState<boolean | null>(null)
  const [selectedOperationId, setSelectedOperationId] = useState('')
  const [inputMappings, setInputMappings] = useState<Array<{ path: string; processVariable: string }>>([])
  const [contractJson, setContractJson] = useState('')

  const dtdsConnectors = useDtdsConnectors()
  const dtdsOperations = useDtdsOperations(connectorKey || null)
  const dtdsOutputFields = useDtdsFields(connectorKey || null, selectedOperationId || null, 'output')
  const dtdsInputFields = useDtdsFields(connectorKey || null, selectedOperationId || null, 'input')

  // F3: AbortController ref for the connector health-check fetch
  const healthAbortRef = useRef<AbortController | null>(null)

  // HIGH #1: abort any in-flight health check when the hook unmounts
  useEffect(() => () => { healthAbortRef.current?.abort() }, [])

  useEffect(() => {
    if (!element || !modeler) return
    const bo = element.businessObject
    setUrl(bo.get('ab:url') || '')
    setMethod(bo.get('ab:method') || 'GET')
    setSecretRef(bo.get('ab:secretRef') || '')
    setResponseVariable(bo.get('ab:responseVariable') || 'apiResult')
    setExtractFields(readExtractFields(element))
    const savedConnectorKey = bo.get('ab:connector') || ''
    setConnectorKey(savedConnectorKey)
    setSelectedConnectorKey(savedConnectorKey)
    setConnectorPath(bo.get('ab:path') || '')
    setBodyTemplate(bo.get('ab:body') || '')
    setSelectedOperationId(bo.get('ab:operationId') || '')
    setConnectorFieldDirty(false)

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
  }, [element, modeler])

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
    modeler.get('modeling').updateProperties(element, { 'ab:responseVariable': value || undefined })
  }

  const handleConnectorPathChange = (value: string) => {
    setConnectorPath(value)
    modeler.get('modeling').updateProperties(element, { 'ab:path': value || undefined })
  }

  const handleBodyTemplateChange = (value: string) => {
    setBodyTemplate(value)
    modeler.get('modeling').updateProperties(element, { 'ab:body': value || undefined })
  }

  const handleExtractFieldChange = (index: number, field: 'variable' | 'path', value: string) => {
    const updated = extractFields.map((row, i) => i === index ? { ...row, [field]: value } : row)
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

  const handleConnectorSelect = async (key: string) => {
    setConnectorFieldDirty(true)
    setSelectedConnectorKey(key)
    setConnectorKey(key)
    setConnectorHealthy(null)
    // F5: clear stale contractJson immediately before fetching the new connector's schema
    setContractJson('')
    modeler.get('modeling').updateProperties(element, { 'ab:connector': key || undefined })

    if (!key) {
      setSchemaFields([])
      return
    }

    // F3: abort any in-flight health check before starting a new one
    if (healthAbortRef.current) {
      healthAbortRef.current.abort()
    }
    const controller = new AbortController()
    healthAbortRef.current = controller

    // Combine the controller signal with a 5-second timeout signal
    const timeoutSignal = AbortSignal.timeout(5000)
    const signal = AbortSignal.any
      ? AbortSignal.any([controller.signal, timeoutSignal])
      : controller.signal

    fetch(`/api/proxy/admin/connectors/${encodeURIComponent(key)}/health`, { signal })
      .then(res => {
        if (!controller.signal.aborted) setConnectorHealthy(res.ok)
      })
      .catch((err: unknown) => {
        // F3: AbortError means the fetch was cancelled — do NOT mark as unhealthy
        if (err instanceof Error && err.name === 'AbortError') return
        if (!controller.signal.aborted) setConnectorHealthy(false)
      })

    setUrl('')
    modeler.get('modeling').updateProperties(element, { 'ab:url': undefined })

    // F5: only set contractJson in the success path after the connector is found
    const connector = connectors.find(c => c.connectorKey === key)
    if (connector?.sampleSchema) {
      setContractJson(connector.sampleSchema)
    }

    setSchemaLoading(true)
    try {
      const fields = await getConnectorSchema(key)
      setSchemaFields(fields)
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

  const handleOperationSelect = (operationId: string) => {
    setSelectedOperationId(operationId)
    modeler.get('modeling').updateProperties(element, { 'ab:operationId': operationId || undefined })
    setInputMappings([])
    modeler.get('modeling').updateProperties(element, { 'ab:inputMappings': undefined })
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
    modeler.get('modeling').updateProperties(element, { 'ab:inputMappings': serialized || undefined })
  }

  const handleOutputFieldClick = (field: FieldEntry) => {
    const varName = field.path
      .split('.')
      .pop()
      ?.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase()) ?? field.path
    handleBodyTemplateChange(bodyTemplate + `\${${varName}}`)
  }

  const handleApplyContract = (jsonSource: string): { ok: true } | { ok: false; error: string } => {
    const result = tryExtractFieldsFromJson(jsonSource)
    if (!result.ok) return result
    if (result.rows.length === 0) return { ok: false, error: 'JSON object has no top-level keys.' }
    setExtractFields(result.rows)
    writeExtractFields(element, modeler, result.rows)
    return { ok: true }
  }

  const processId: string = element?.businessObject?.$parent?.id ?? ''
  const activityId: string = element?.businessObject?.id ?? ''

  return {
    url, method, secretRef, responseVariable,
    connectorKey, connectorPath, bodyTemplate, schemaFields, schemaLoading,
    extractFields, connectors, selectedConnectorKey,
    connectorLoadError, connectorFieldDirty, connectorHealthy,
    selectedOperationId, inputMappings, contractJson,
    processId, activityId,
    dtdsConnectors, dtdsOperations, dtdsOutputFields, dtdsInputFields,
    handleUrlChange, handleMethodChange, handleSecretRefChange, handleResponseVariableChange,
    handleConnectorPathChange, handleBodyTemplateChange,
    handleExtractFieldChange, handleAddExtractField, handleRemoveExtractField,
    handleConnectorSelect, handleOperationSelect,
    handleInputMappingChange, handleOutputFieldClick, handleApplyContract,
  }
}
