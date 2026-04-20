import { adminApiClient } from './client'

// ==================== TYPE DEFINITIONS ====================

export interface CustodyMappingResponse {
  id: number
  tenantCode: string
  categoryKey: string
  custodyGroup: string
  displayName: string | null
  createdAt: string
  updatedAt: string
}

export interface CustodyMappingRequest {
  tenantCode: string
  categoryKey: string
  custodyGroup: string
  displayName?: string
}

// ==================== API ERROR CLASS ====================

export class CustodyApiError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public details?: unknown
  ) {
    super(message)
    this.name = 'CustodyApiError'
  }
}

// ==================== ERROR HANDLER ====================

function handleApiError(error: unknown, context: string): never {
  if (error && typeof error === 'object' && 'response' in error) {
    const e = error as { response: { status: number; data: { message?: string } } }
    const status = e.response.status
    const data = e.response.data
    throw new CustodyApiError(
      data?.message || `Custody API error (${status}) — ${context}`,
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
    throw new CustodyApiError(
      'Cannot connect to Admin service. Please ensure the backend is running.',
      0
    )
  }

  throw new CustodyApiError(
    error instanceof Error ? error.message : 'Unknown error occurred'
  )
}

// ==================== API FUNCTIONS ====================

export async function listCustodyMappings(tenantCode: string): Promise<CustodyMappingResponse[]> {
  try {
    const response = await adminApiClient.get('/api/custody-mappings', {
      params: { tenantCode },
    })
    return Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: unknown) {
    handleApiError(error, `list-custody-mappings-${tenantCode}`)
  }
}

export async function createCustodyMapping(request: CustodyMappingRequest): Promise<CustodyMappingResponse> {
  try {
    const response = await adminApiClient.post('/api/custody-mappings', request)
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `create-custody-mapping-${request.categoryKey}`)
  }
}

export async function updateCustodyMapping(
  id: number,
  request: CustodyMappingRequest
): Promise<CustodyMappingResponse> {
  try {
    const response = await adminApiClient.put(`/api/custody-mappings/${id}`, request)
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-custody-mapping-${id}`)
  }
}

export async function deleteCustodyMapping(id: number): Promise<void> {
  try {
    await adminApiClient.delete(`/api/custody-mappings/${id}`)
  } catch (error: unknown) {
    handleApiError(error, `delete-custody-mapping-${id}`)
  }
}
