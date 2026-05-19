'use client'

import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { readFlowableField, writeFlowableField } from '@/lib/bpmn/extension-elements'
import { listDtdsConnectors, listDtdsOperations, type ConnectorSummary, type OperationSummary } from '@/lib/api/dtds'
import { listDatasourcesCatalog, type TenantDatasourceResponse } from '@/lib/api/datasources'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Transport tag shown alongside each unified connector entry. */
type ConnectorTransport = 'rest' | 'database'

/** A unified entry combining both admin registries. */
interface UnifiedConnectorEntry {
  /** Stable key written to the BPMN field — e.g. `hr-service`, `default-2`. */
  ref: string
  displayName: string
  transport: ConnectorTransport
}

type OnErrorMode = 'FAIL' | 'CONTINUE' | 'THROW_BPMN_ERROR'

// ---------------------------------------------------------------------------
// Delegate resolution by transport (ADR-014)
// ---------------------------------------------------------------------------

const TRANSPORT_DELEGATE: Record<ConnectorTransport, string> = {
  rest:     '${externalApiCallDelegate}',
  database: '${databaseConnectorDelegate}',
}

const TRANSPORT_LABEL: Record<ConnectorTransport, string> = {
  rest:     'REST',
  database: 'DB',
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface ConnectorOperationSectionProps {
  element: any
  modeler: any
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Panel section for the CONNECTOR_OPERATION action block (ADR-014, D-DC-1).
 *
 * Renders:
 * 1. Connector picker — unified REST connectors + Data Sources, grouped.
 * 2. Operation picker — DTDS `/api/v1/design/connectors/{key}/operations`.
 * 3. Inputs — JSON field MVP for queryParams / request input.
 *    TODO(D-DC): ship the schema-driven typed-widget form when operation input
 *    JSON Schema is surfaced via DTDS (see §4.5 in Database-And-Connector.md).
 * 4. Response variable + On-Error handling carried over from ExternalApiCallSection.
 *
 * Writes via writeFlowableField / modeling.updateProperties to preserve
 * bpmn-js commandStack integrity (ADR-011).
 */
export default function ConnectorOperationSection({ element, modeler }: ConnectorOperationSectionProps) {
  const { status } = useSession()
  const isAuthenticated = status === 'authenticated'

  // ── local state ──────────────────────────────────────────────────────────
  const [selectedRef, setSelectedRef] = useState('')
  const [selectedTransport, setSelectedTransport] = useState<ConnectorTransport | null>(null)
  const [selectedOperationId, setSelectedOperationId] = useState('')
  const [queryParams, setQueryParams] = useState('')
  const [responseVariable, setResponseVariable] = useState('')
  const [onError, setOnError] = useState<OnErrorMode>('FAIL')

  // ── read BPMN fields on mount / element change ────────────────────────────
  useEffect(() => {
    if (!element || !modeler) return
    setSelectedRef(readFlowableField(element, 'connector'))
    setSelectedOperationId(readFlowableField(element, 'operationId'))
    setQueryParams(readFlowableField(element, 'queryParams'))
    setResponseVariable(readFlowableField(element, 'responseVariable') || 'connectorResult')
    const raw = readFlowableField(element, 'onError') as OnErrorMode
    setOnError(raw || 'FAIL')
    // Resolve transport from saved delegate to restore the transport badge on re-open
    const savedDelegate = element.businessObject?.get?.('flowable:delegateExpression') ?? ''
    if (savedDelegate.includes('databaseConnectorDelegate')) {
      setSelectedTransport('database')
    } else if (savedDelegate.includes('externalApiCallDelegate')) {
      setSelectedTransport('rest')
    } else {
      setSelectedTransport(null)
    }
  }, [element, modeler])

  // ── REST connectors (DTDS catalog) ───────────────────────────────────────
  const { data: dtdsConnectors = [], isLoading: dtdsLoading } = useQuery({
    queryKey: ['dtds', 'connectors'],
    queryFn: listDtdsConnectors,
    enabled: isAuthenticated,
    staleTime: 60_000,
    retry: 1,
  })

  // ── Data Sources (admin registry) ────────────────────────────────────────
  const { data: datasources = [], isLoading: dsLoading } = useQuery<TenantDatasourceResponse[]>({
    queryKey: ['admin', 'datasources', 'catalog'],
    queryFn: listDatasourcesCatalog,
    enabled: isAuthenticated,
    staleTime: 60_000,
    retry: 1,
  })

  // ── unified entries ───────────────────────────────────────────────────────
  const restEntries: UnifiedConnectorEntry[] = dtdsConnectors.map((c: ConnectorSummary) => ({
    ref:         c.key,
    displayName: c.displayName,
    transport:   'rest',
  }))

  const dbEntries: UnifiedConnectorEntry[] = datasources.map((ds: TenantDatasourceResponse) => ({
    ref:         ds.ref,
    displayName: ds.ref,
    transport:   'database',
  }))

  // ── operations for the selected connector ─────────────────────────────────
  const { data: operations = [], isLoading: opsLoading } = useQuery<OperationSummary[]>({
    queryKey: ['dtds', 'operations', selectedRef],
    queryFn:  () => listDtdsOperations(selectedRef),
    enabled:  isAuthenticated && !!selectedRef && selectedTransport === 'rest',
    staleTime: 60_000,
    retry:     1,
  })

  // ── transport lookup map — built from both query datasets ────────────────
  // Keyed by ref; O(1) lookup prevents misclassification when dbEntries is []
  // while a datasources query is still in-flight.
  const transportMap = useMemo<Map<string, ConnectorTransport>>(() => {
    const map = new Map<string, ConnectorTransport>()
    restEntries.forEach(e => map.set(e.ref, 'rest'))
    dbEntries.forEach(e => map.set(e.ref, 'database'))
    return map
  }, [restEntries, dbEntries])

  // ── write helpers ─────────────────────────────────────────────────────────
  const modeling = useMemo(() => modeler.get('modeling'), [modeler])

  const handleConnectorSelect = (ref: string) => {
    // Guard: do not process selections while either registry is still loading —
    // the transportMap would be incomplete and could misclassify the transport.
    if (dtdsLoading || dsLoading) return

    if (ref === '__none__') {
      setSelectedRef('')
      setSelectedTransport(null)
      setSelectedOperationId('')
      writeFlowableField(element, modeling, 'connector', '')
      writeFlowableField(element, modeling, 'operationId', '')
      // Clear delegate — element stays bpmn:ServiceTask but is unexecutable until a connector is chosen
      modeling.updateProperties(element, {
        'flowable:delegateExpression': undefined,
        delegateExpression:            undefined,
      })
      return
    }

    // O(1) provably-correct transport lookup using the combined map
    const transport: ConnectorTransport = transportMap.get(ref) ?? 'rest'

    setSelectedRef(ref)
    setSelectedTransport(transport)
    setSelectedOperationId('')

    writeFlowableField(element, modeling, 'connector', ref)
    writeFlowableField(element, modeling, 'operationId', '')

    // Write the transport-resolved delegate (ADR-014 §1)
    const delegate = TRANSPORT_DELEGATE[transport]
    modeling.updateProperties(element, {
      'flowable:delegateExpression': delegate,
      delegateExpression:            delegate,
    })
  }

  const handleOperationSelect = (opId: string) => {
    const val = opId === '__none__' ? '' : opId
    setSelectedOperationId(val)
    writeFlowableField(element, modeling, 'operationId', val)
  }

  const handleQueryParamsBlur = () => {
    writeFlowableField(element, modeling, 'queryParams', queryParams)
  }

  const handleResponseVariableBlur = () => {
    writeFlowableField(element, modeling, 'responseVariable', responseVariable)
  }

  const handleOnErrorChange = (val: string) => {
    const mode = val as OnErrorMode
    setOnError(mode)
    writeFlowableField(element, modeling, 'onError', mode)
  }

  // ── derived ───────────────────────────────────────────────────────────────
  const isLoading = dtdsLoading || dsLoading
  const hasNoConnectors = !isLoading && restEntries.length === 0 && dbEntries.length === 0

  // DB connectors have no operations in DTDS yet (named queries UI deferred — D-DC-4)
  const isDatabaseConnector = selectedTransport === 'database'

  // ── render ────────────────────────────────────────────────────────────────
  return (
    <>
      {/* 1. Connector Picker */}
      <Card>
        <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
          <CardTitle className="text-xs font-semibold">Connector</CardTitle>
        </CardHeader>
        <CardContent className="px-3 pb-3 pt-2 space-y-2">
          {isLoading && (
            <p className="text-xs text-muted-foreground">Loading connectors…</p>
          )}
          {hasNoConnectors && (
            <p className="text-xs text-muted-foreground">
              No connectors registered. Add one in Admin &gt; Connectors or Data Sources.
            </p>
          )}
          {!isLoading && (
            <Select value={selectedRef || '__none__'} onValueChange={handleConnectorSelect}>
              <SelectTrigger className="h-8 text-xs">
                <SelectValue placeholder="(none — diagram only)" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">(none — diagram only)</SelectItem>
                {restEntries.length > 0 && (
                  <SelectGroup>
                    <SelectLabel className="text-xs font-medium">Connectors</SelectLabel>
                    {restEntries.map(c => (
                      <SelectItem key={c.ref} value={c.ref}>
                        <span className="flex items-center gap-2">
                          <span
                            className="shrink-0 rounded px-1 py-0 text-[9px] font-semibold uppercase leading-4"
                            style={{ background: '#f3e5f5', color: '#6a1b9a' }}
                          >
                            {TRANSPORT_LABEL[c.transport]}
                          </span>
                          {c.displayName} ({c.ref})
                        </span>
                      </SelectItem>
                    ))}
                  </SelectGroup>
                )}
                {dbEntries.length > 0 && (
                  <SelectGroup>
                    <SelectLabel className="text-xs font-medium">Data Sources</SelectLabel>
                    {dbEntries.map(c => (
                      <SelectItem key={c.ref} value={c.ref}>
                        <span className="flex items-center gap-2">
                          <span
                            className="shrink-0 rounded px-1 py-0 text-[9px] font-semibold uppercase leading-4"
                            style={{ background: '#e8f5e9', color: '#2e7d32' }}
                          >
                            {TRANSPORT_LABEL[c.transport]}
                          </span>
                          {c.displayName}
                        </span>
                      </SelectItem>
                    ))}
                  </SelectGroup>
                )}
              </SelectContent>
            </Select>
          )}
          {!selectedRef && (
            <p className="text-xs text-muted-foreground">
              Select a connector to configure the operation.
            </p>
          )}
          {selectedRef && selectedTransport && (
            <p className="text-xs text-muted-foreground">
              Transport: <strong>{TRANSPORT_LABEL[selectedTransport]}</strong>
            </p>
          )}
        </CardContent>
      </Card>

      {/* 2. Operation Picker */}
      <Card>
        <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
          <CardTitle className="text-xs font-semibold">Operation</CardTitle>
        </CardHeader>
        <CardContent className="px-3 pb-3 pt-2 space-y-2">
          {isDatabaseConnector && (
            <p className="text-xs text-muted-foreground">
              No operations — register named queries on this Data Source to expose operations here.
            </p>
          )}
          {!isDatabaseConnector && !selectedRef && (
            <p className="text-xs text-muted-foreground">Select a connector first.</p>
          )}
          {!isDatabaseConnector && selectedRef && (
            <>
              {opsLoading && (
                <p className="text-xs text-muted-foreground">Loading operations…</p>
              )}
              {!opsLoading && operations.length === 0 && (
                <p className="text-xs text-muted-foreground">
                  No operations found for this connector.
                </p>
              )}
              {!opsLoading && operations.length > 0 && (
                <Select
                  value={selectedOperationId || '__none__'}
                  onValueChange={handleOperationSelect}
                >
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue placeholder="(select operation)" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">(none)</SelectItem>
                    {operations.map((op: OperationSummary) => (
                      <SelectItem key={op.id} value={op.id}>
                        {op.displayName} ({op.id})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </>
          )}
        </CardContent>
      </Card>

      {/* 3. Inputs — JSON-field MVP */}
      {/* TODO(D-DC): replace with schema-driven typed-widget form once DTDS
          operation input JSON Schema is wired as a VariableComboBox source
          (Database-And-Connector.md §4.5, F6). */}
      {selectedRef && (
        <Card>
          <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
            <CardTitle className="text-xs font-semibold">Inputs</CardTitle>
          </CardHeader>
          <CardContent className="px-3 pb-3 pt-2 space-y-1">
            <Label htmlFor="co-query-params" className="text-xs">
              Query parameters / request body (JSON)
            </Label>
            <Textarea
              id="co-query-params"
              className="text-xs font-mono resize-none"
              rows={4}
              placeholder={`{\n  "paramName": "\${processVar}"\n}`}
              value={queryParams}
              onChange={e => setQueryParams(e.target.value)}
              onBlur={handleQueryParamsBlur}
            />
            <p className="text-xs text-muted-foreground">
              Use <code className="text-[10px]">{'${variable}'}</code> to reference process variables.
            </p>
          </CardContent>
        </Card>
      )}

      {/* 4. Response Variable + Error Handling */}
      <Card>
        <CardHeader className="pb-2 pt-3 px-3 bg-muted border-b">
          <CardTitle className="text-xs font-semibold">Output &amp; Error Handling</CardTitle>
        </CardHeader>
        <CardContent className="px-3 pb-3 pt-2 space-y-3">
          <div className="space-y-1">
            <Label htmlFor="co-response-variable" className="text-xs">Response variable</Label>
            <Input
              id="co-response-variable"
              className="h-8 text-xs"
              value={responseVariable}
              placeholder="connectorResult"
              onChange={e => setResponseVariable(e.target.value)}
              onBlur={handleResponseVariableBlur}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="co-on-error" className="text-xs">On error</Label>
            <Select value={onError} onValueChange={handleOnErrorChange}>
              <SelectTrigger id="co-on-error" className="h-8 text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FAIL">FAIL (throw exception)</SelectItem>
                <SelectItem value="CONTINUE">CONTINUE (ignore error)</SelectItem>
                <SelectItem value="THROW_BPMN_ERROR">THROW_BPMN_ERROR (catch with boundary event)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>
    </>
  )
}
