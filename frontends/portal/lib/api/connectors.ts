import { adminApiClient } from './client'

// ==================== TYPE DEFINITIONS ====================

export interface ConnectorResponse {
  endpointId: number
  credentialId: number
  tenantCode: string
  connectorKey: string
  displayName: string
  baseUrl: string
  environment: string
  active: boolean
  authScheme: string
  headerName: string | null
  sampleSchema: string | null
  createdAt: string
  updatedAt: string
}

export interface ConnectorRequest {
  tenantCode: string
  connectorKey: string
  displayName: string
  baseUrl: string
  environment: string
  active: boolean
  authScheme: string
  secretRef: string
  headerName?: string
  sampleSchema?: string
}

export interface ConnectorTestRequest {
  path: string
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  requestBody?: string
}

export interface ConnectorTestResponse {
  statusCode: number
  headers: Record<string, string>
  body: string
  truncated: boolean
  durationMs: number
}

// ==================== API ERROR CLASS ====================

export class ConnectorApiError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public details?: unknown
  ) {
    super(message)
    this.name = 'ConnectorApiError'
  }
}

// ==================== ERROR HANDLER ====================

function handleApiError(error: unknown, context: string): never {
  if (error && typeof error === 'object' && 'response' in error) {
    const e = error as { response: { status: number; data: { message?: string } } }
    const status = e.response.status
    const data = e.response.data
    throw new ConnectorApiError(
      data?.message || `Connector API error (${status}) — ${context}`,
      status,
      data
    )
  }

  if (
    error &&
    typeof error === 'object' &&
    ('request' in error || (error as { code?: string }).code === 'ECONNREFUSED' ||
      (error as { code?: string }).code === 'ERR_NETWORK')
  ) {
    throw new ConnectorApiError(
      'Cannot connect to Admin service. Please ensure the backend is running.',
      0
    )
  }

  throw new ConnectorApiError(
    error instanceof Error ? error.message : 'Unknown error occurred'
  )
}

// ==================== API FUNCTIONS ====================

export async function listConnectors(tenantCode: string): Promise<ConnectorResponse[]> {
  try {
    const response = await adminApiClient.get('/api/connectors', {
      params: { tenantCode },
    })
    return Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: unknown) {
    handleApiError(error, `list-connectors-${tenantCode}`)
  }
}

export async function createConnector(request: ConnectorRequest): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.post('/api/connectors', request)
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `create-connector-${request.connectorKey}`)
  }
}

export interface ConnectorUpdateRequest {
  displayName: string
  baseUrl: string
  environment: string
  active: boolean
  authScheme: string
  secretRef?: string
  headerName?: string
}

export async function updateConnector(
  tenantCode: string,
  connectorKey: string,
  request: ConnectorUpdateRequest
): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.put(
      `/api/connectors/${connectorKey}`,
      request,
      { params: { tenantCode } }
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-connector-${connectorKey}`)
  }
}

export async function updateConnectorSchema(
  tenantCode: string,
  connectorKey: string,
  sampleSchema: string
): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.patch(
      `/api/connectors/${connectorKey}/schema`,
      sampleSchema,
      {
        params: { tenantCode },
        headers: { 'Content-Type': 'application/json' },
      }
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-schema-${connectorKey}`)
  }
}

export async function testConnector(
  tenantCode: string,
  connectorKey: string,
  request: ConnectorTestRequest
): Promise<ConnectorTestResponse> {
  try {
    const response = await adminApiClient.post(
      `/api/connectors/${connectorKey}/test`,
      request,
      {
        params: { tenantCode },
      }
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `test-connector-${connectorKey}`)
  }
}

export interface ConnectorSchemaField {
  key: string
  type: 'string' | 'number' | 'boolean' | 'object' | 'array' | 'unknown'
}

/**
 * Fetch the sampleSchema for a connector and parse it into field descriptors.
 * Returns empty array if connector has no schema or schema is not a JSON object.
 */
export async function getConnectorSchema(
  tenantCode: string,
  connectorKey: string
): Promise<ConnectorSchemaField[]> {
  try {
    const response = await adminApiClient.get(
      `/api/connectors/${encodeURIComponent(connectorKey)}/schema`,
      { params: { tenantCode } }
    )
    const raw = response.data
    if (!raw) return []
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (typeof parsed !== 'object' || Array.isArray(parsed)) return []
    return Object.entries(parsed).map(([key, val]) => ({
      key,
      type: inferJsonType(val),
    }))
  } catch {
    return []
  }
}

function inferJsonType(val: unknown): ConnectorSchemaField['type'] {
  if (val === null) return 'unknown'
  if (typeof val === 'string') return 'string'
  if (typeof val === 'number') return 'number'
  if (typeof val === 'boolean') return 'boolean'
  if (Array.isArray(val)) return 'array'
  if (typeof val === 'object') return 'object'
  return 'unknown'
}
