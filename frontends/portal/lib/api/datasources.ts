import { adminApiClient } from './client'

// ==================== TYPES ====================

export interface TenantDatasourceResponse {
  id: string
  tenantId: string
  ref: string
  jdbcUrl: string
  driverClassName: string
  credentialRef: string
  dialect: string | null
  poolMinSize: number
  poolMaxSize: number
  connectionTimeoutSeconds: number
  idleTimeoutSeconds: number
  createdAt: string
  updatedAt: string
}

export interface TenantDatasourceRequest {
  ref: string
  jdbcUrl: string
  driverClassName: string
  credentialRef: string
  dialect?: string
  poolMinSize: number
  poolMaxSize: number
  connectionTimeoutSeconds: number
  idleTimeoutSeconds: number
}

export interface DatasourceTestResult {
  ok: boolean
  message: string
  latencyMs: number
}

// ==================== API FUNCTIONS ====================

export async function listDatasources(token: string): Promise<TenantDatasourceResponse[]> {
  const res = await adminApiClient.get('/api/v1/config/datasources', {
    headers: { Authorization: `Bearer ${token}` },
  })
  return Array.isArray(res.data) ? res.data : []
}

export async function getDatasource(
  ref: string,
  token: string
): Promise<TenantDatasourceResponse> {
  const res = await adminApiClient.get(`/api/v1/config/datasources/${encodeURIComponent(ref)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.data
}

export async function createDatasource(
  request: TenantDatasourceRequest,
  token: string
): Promise<TenantDatasourceResponse> {
  const res = await adminApiClient.post('/api/v1/config/datasources', request, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.data
}

export async function updateDatasource(
  ref: string,
  request: TenantDatasourceRequest,
  token: string
): Promise<TenantDatasourceResponse> {
  const res = await adminApiClient.put(
    `/api/v1/config/datasources/${encodeURIComponent(ref)}`,
    request,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  return res.data
}

export async function deleteDatasource(ref: string, token: string): Promise<void> {
  await adminApiClient.delete(`/api/v1/config/datasources/${encodeURIComponent(ref)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
}

export async function testDatasourceConnection(
  ref: string,
  token: string
): Promise<DatasourceTestResult> {
  const res = await adminApiClient.post(
    `/api/v1/config/datasources/${encodeURIComponent(ref)}/test`,
    {},
    { headers: { Authorization: `Bearer ${token}` } }
  )
  return res.data
}

/**
 * List all data sources for the current tenant using the shared adminApiClient
 * interceptor — no manual token required.
 *
 * Used by the CONNECTOR_OPERATION connector picker to populate the Data Sources group.
 */
export async function listDatasourcesCatalog(): Promise<TenantDatasourceResponse[]> {
  const res = await adminApiClient.get('/api/v1/config/datasources')
  return Array.isArray(res.data) ? res.data : []
}
