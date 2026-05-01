import { adminApiClient, apiClient, erpApiClient } from './client'

// ==================== TYPE DEFINITIONS ====================

export interface DoaThreshold {
  id: number
  tenantId: string
  doaLevel: string
  maxAmount: number | null
  currency: string
  label: string | null
  description: string | null
}

export interface Department {
  id: number
  name: string
  code: string
  description: string | null
  tenantCode: string
  organizationId: number
  active: boolean
}

export interface DepartmentRequest {
  name: string
  code: string
  description?: string
  tenantCode: string
  organizationId: number
}

// ==================== API ERROR CLASSES ====================

export class AdminTenantApiError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public details?: any
  ) {
    super(message)
    this.name = 'AdminTenantApiError'
  }
}

// ==================== ERROR HANDLER ====================

function handleApiError(error: any, context: string): never {
  if (error.response) {
    const status = error.response.status
    const data = error.response.data
    throw new AdminTenantApiError(
      data?.message || `Admin API error (${status}) — ${context}`,
      status,
      data
    )
  }

  if (error.request || error.code === 'ECONNREFUSED' || error.code === 'ERR_NETWORK') {
    throw new AdminTenantApiError(
      'Cannot connect to Admin service. Please ensure the backend is running.',
      0
    )
  }

  throw new AdminTenantApiError(error.message || 'Unknown error occurred')
}

// ==================== API FUNCTIONS ====================

export async function getDoaThresholds(tenantId = 'default'): Promise<DoaThreshold[]> {
  try {
    const response = await apiClient.get('/api/doa-thresholds', {
      params: { tenantId },
    })
    return Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: any) {
    handleApiError(error, 'doa-thresholds')
  }
}

export interface DoaThresholdUpdate {
  label: string | null
  description: string | null
  maxAmount: number | null
  currency: string
}

export async function updateDoaThreshold(id: number, update: DoaThresholdUpdate): Promise<DoaThreshold> {
  try {
    const response = await apiClient.put(`/api/doa-thresholds/${id}`, update)
    return response.data
  } catch (error: any) {
    handleApiError(error, `doa-threshold-${id}`)
  }
}

export async function getDepartments(tenantCode = 'default'): Promise<Department[]> {
  try {
    const response = await erpApiClient.get('/api/v1/departments', {
      params: { tenantCode },
    })
    return Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: any) {
    handleApiError(error, 'departments')
  }
}

export async function createDepartment(req: DepartmentRequest): Promise<Department> {
  try {
    const response = await erpApiClient.post('/api/v1/departments', req)
    return response.data
  } catch (error: any) {
    handleApiError(error, req.name)
  }
}
