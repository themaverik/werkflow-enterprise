import { adminApiClient } from './client'

// ==================== TYPE DEFINITIONS ====================

export type ConnectorType = 'API' | 'WEBHOOK' | 'MCP' | 'OTHER'

export interface ConnectorResponse {
  endpointId: number
  credentialId: number
  tenantCode: string
  connectorKey: string
  displayName: string
  baseUrl: string
  environment: string
  active: boolean
  connectorType: ConnectorType
  authScheme: string
  headerName: string | null
  sampleSchema: string | null
  hasSecret: boolean
  createdAt: string
  updatedAt: string
}

export interface ConnectorRequest {
  /** Optional — backend resolves from JWT tenant_id claim when omitted */
  tenantCode?: string
  connectorKey: string
  displayName: string
  baseUrl: string
  environment: string
  active: boolean
  connectorType?: ConnectorType
  authScheme: string
  secretValue: string
  headerName?: string
  sampleSchema?: string
}

export interface ConnectorTestRequest {
  path: string
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  requestBody?: string
  environment?: string
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

export async function listConnectors(): Promise<ConnectorResponse[]> {
  try {
    const response = await adminApiClient.get('/api/connectors')
    return Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: unknown) {
    handleApiError(error, 'list-connectors')
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
  connectorType?: ConnectorType
  authScheme: string
  secretValue?: string
  headerName?: string
}

export async function updateConnector(
  connectorKey: string,
  request: ConnectorUpdateRequest
): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.put(
      `/api/connectors/${connectorKey}`,
      request
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-connector-${connectorKey}`)
  }
}

export async function updateConnectorSchema(
  connectorKey: string,
  sampleSchema: string
): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.patch(
      `/api/connectors/${connectorKey}/schema`,
      sampleSchema,
      { headers: { 'Content-Type': 'application/json' } }
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-schema-${connectorKey}`)
  }
}

export async function updateEndpointSchema(
  connectorKey: string,
  endpointId: number,
  sampleSchema: string
): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.patch(
      `/api/connectors/${connectorKey}/endpoints/${endpointId}/schema`,
      sampleSchema,
      { headers: { 'Content-Type': 'application/json' } }
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-endpoint-schema-${endpointId}`)
  }
}

export async function testConnector(
  connectorKey: string,
  request: ConnectorTestRequest
): Promise<ConnectorTestResponse> {
  try {
    const response = await adminApiClient.post(
      `/api/connectors/${connectorKey}/test`,
      request
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
  connectorKey: string
): Promise<ConnectorSchemaField[]> {
  try {
    const response = await adminApiClient.get(
      `/api/connectors/${encodeURIComponent(connectorKey)}/schema`
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

export async function deleteConnector(connectorKey: string): Promise<void> {
  try {
    await adminApiClient.delete(`/api/connectors/${connectorKey}`)
  } catch (error: unknown) {
    handleApiError(error, `delete-connector-${connectorKey}`)
  }
}

export interface ConnectorEndpointRequest {
  baseUrl: string
  environment: string
  connectorType?: ConnectorType
  active?: boolean
}

export async function addConnectorEndpoint(
  connectorKey: string,
  request: ConnectorEndpointRequest
): Promise<ConnectorResponse> {
  try {
    const response = await adminApiClient.post(
      `/api/connectors/${connectorKey}/endpoints`,
      request
    )
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `add-endpoint-${connectorKey}`)
  }
}

export async function deleteConnectorEndpoint(
  connectorKey: string,
  endpointId: number
): Promise<void> {
  try {
    await adminApiClient.delete(
      `/api/connectors/${connectorKey}/endpoints/${endpointId}`
    )
  } catch (error: unknown) {
    handleApiError(error, `delete-endpoint-${connectorKey}-${endpointId}`)
  }
}

export interface ConnectorApiKeyRequest {
  rawKey: string
  keyHash: string
  keyName: string
}

export async function registerApiKey(
  connectorKey: string,
  request: ConnectorApiKeyRequest
): Promise<void> {
  try {
    await adminApiClient.post(
      `/api/connectors/${connectorKey}/api-key`,
      request
    )
  } catch (error: unknown) {
    handleApiError(error, `register-api-key-${connectorKey}`)
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
