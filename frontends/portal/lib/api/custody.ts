import { erpApiClient } from './client'

// ==================== TYPE DEFINITIONS ====================

export interface CustodyMappingResponse {
  id: number
  tenantId: string
  custodyOwner: string
  candidateGroups: string[]
  createdAt: string
  updatedAt: string
}

export interface CustodyMappingRequest {
  custodyOwner: string
  candidateGroups: string[]
}

export interface CustodyMappingPage {
  content: CustodyMappingResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
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
      'Cannot connect to ERP service. Please ensure the backend is running.',
      0
    )
  }

  throw new CustodyApiError(
    error instanceof Error ? error.message : 'Unknown error occurred'
  )
}

// ==================== API FUNCTIONS ====================

export async function listCustodyMappings(page = 0, size = 50): Promise<CustodyMappingPage> {
  try {
    const response = await erpApiClient.get('/api/v1/custody-mappings', {
      params: { page, size, sort: 'custodyOwner,asc' },
    })
    return response.data
  } catch (error: unknown) {
    handleApiError(error, 'list-custody-mappings')
  }
}

export async function createCustodyMapping(request: CustodyMappingRequest): Promise<CustodyMappingResponse> {
  try {
    const response = await erpApiClient.post('/api/v1/custody-mappings', request)
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `create-custody-mapping-${request.custodyOwner}`)
  }
}

export async function updateCustodyMapping(
  id: number,
  request: CustodyMappingRequest
): Promise<CustodyMappingResponse> {
  try {
    const response = await erpApiClient.put(`/api/v1/custody-mappings/${id}`, request)
    return response.data
  } catch (error: unknown) {
    handleApiError(error, `update-custody-mapping-${id}`)
  }
}

export async function deleteCustodyMapping(id: number): Promise<void> {
  try {
    await erpApiClient.delete(`/api/v1/custody-mappings/${id}`)
  } catch (error: unknown) {
    handleApiError(error, `delete-custody-mapping-${id}`)
  }
}
