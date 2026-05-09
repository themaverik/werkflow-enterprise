import { apiClient } from './client'

// ==================== TYPE DEFINITIONS ====================

export interface ConnectorSummary {
  key: string
  displayName: string
  version: string
  category: string
  description?: string
  tags: string[]
}

export interface ConnectorDefinition extends ConnectorSummary {
  operations?: OperationSummary[]
}

export interface OperationSummary {
  id: string
  displayName: string
  category: 'read' | 'write' | 'list' | 'delete' | 'stream'
  description?: string
}

export type FieldDirection = 'input' | 'output'

export interface FieldEntry {
  path: string
  type: string
  isArrayItem: boolean
  required: boolean
  depth: number
  description?: string
}

export interface ProcessVariable {
  name: string
  type?: string
  setByActivity: string
  setByTask: string
}

// JSON Schema 2020-12 — kept intentionally open since the structure varies per operation
export type JsonSchema = Record<string, unknown>

// ==================== ERROR CLASS ====================

export class DtdsApiError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public details?: unknown
  ) {
    super(message)
    this.name = 'DtdsApiError'
  }
}

// ==================== INTERNAL ERROR HANDLER ====================

function handleDtdsError(error: unknown, context: string): never {
  if (error && typeof error === 'object' && 'response' in error) {
    const e = error as { response: { status: number; data: { message?: string } } }
    const status = e.response.status
    const message = e.response.data?.message ?? `DTDS error (${status}) — ${context}`
    throw new DtdsApiError(message, status, e.response.data)
  }

  if (
    error &&
    typeof error === 'object' &&
    ('request' in error ||
      (error as { code?: string }).code === 'ECONNREFUSED' ||
      (error as { code?: string }).code === 'ERR_NETWORK')
  ) {
    throw new DtdsApiError(
      'Cannot reach engine service. Ensure the backend is running.',
      0
    )
  }

  throw new DtdsApiError(error instanceof Error ? error.message : 'Unknown error')
}

// ==================== API FUNCTIONS ====================

export async function listDtdsConnectors(): Promise<ConnectorSummary[]> {
  try {
    const res = await apiClient.get<{ connectors: ConnectorSummary[] }>(
      '/api/v1/design/connectors'
    )
    return res.data.connectors
  } catch (error: unknown) {
    handleDtdsError(error, 'list-connectors')
  }
}

export async function getDtdsConnector(key: string): Promise<ConnectorDefinition> {
  try {
    const res = await apiClient.get<ConnectorDefinition>(
      `/api/v1/design/connectors/${encodeURIComponent(key)}`
    )
    return res.data
  } catch (error: unknown) {
    handleDtdsError(error, `get-connector-${key}`)
  }
}

export async function listDtdsOperations(connectorKey: string): Promise<OperationSummary[]> {
  try {
    const res = await apiClient.get<{ operations: OperationSummary[] }>(
      `/api/v1/design/connectors/${encodeURIComponent(connectorKey)}/operations`
    )
    return res.data.operations
  } catch (error: unknown) {
    handleDtdsError(error, `list-operations-${connectorKey}`)
  }
}

export async function getDtdsOperationSchema(
  connectorKey: string,
  operationId: string,
  direction: FieldDirection
): Promise<JsonSchema> {
  try {
    const res = await apiClient.get<JsonSchema>(
      `/api/v1/design/connectors/${encodeURIComponent(connectorKey)}/operations/${encodeURIComponent(operationId)}/schema`,
      { params: { direction } }
    )
    return res.data
  } catch (error: unknown) {
    handleDtdsError(error, `get-schema-${connectorKey}-${operationId}-${direction}`)
  }
}

export async function listDtdsFields(
  connectorKey: string,
  operationId: string,
  direction: FieldDirection
): Promise<FieldEntry[]> {
  try {
    const res = await apiClient.get<{ fields: FieldEntry[] }>(
      `/api/v1/design/connectors/${encodeURIComponent(connectorKey)}/operations/${encodeURIComponent(operationId)}/fields`,
      { params: { direction } }
    )
    return res.data.fields
  } catch (error: unknown) {
    handleDtdsError(error, `list-fields-${connectorKey}-${operationId}-${direction}`)
  }
}

export async function listProcessVariablesAt(
  processDefId: string,
  activityId: string
): Promise<ProcessVariable[]> {
  try {
    const res = await apiClient.get<{ variables: ProcessVariable[] }>(
      `/api/v1/design/bpmn/processes/${encodeURIComponent(processDefId)}/variables-at/${encodeURIComponent(activityId)}`
    )
    return res.data.variables
  } catch (error: unknown) {
    handleDtdsError(error, `variables-at-${processDefId}-${activityId}`)
  }
}

export async function importConnectorFromOpenApi(
  urlOrYaml: string
): Promise<ConnectorDefinition> {
  try {
    const res = await apiClient.post<ConnectorDefinition>(
      '/api/v1/connectors/import-openapi',
      { source: urlOrYaml }
    )
    return res.data
  } catch (error: unknown) {
    handleDtdsError(error, 'import-openapi')
  }
}
