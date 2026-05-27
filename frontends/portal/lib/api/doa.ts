import { apiClient } from './client'

/**
 * DOA (Delegation of Authority) Levels API Client
 *
 * Manages DOA level thresholds for gateway condition builder catalog
 */

// ==================== TYPE DEFINITIONS ====================

export interface DoaLevel {
  id: string
  tenantId: string
  doaLevel: string
  maxAmount: number
  currency: string
  label: string
  description?: string
}

// ==================== API FUNCTIONS ====================

/**
 * Fetch DOA levels for a tenant
 *
 * @param tenantId - Tenant identifier (defaults to 'default')
 * @returns Array of DOA level definitions
 * @throws Error when the response shape is not a recognised array or paginated envelope.
 *         Callers must handle this error — React Query surfaces it as `isError`; manual
 *         callers should catch and log (see BpmnDesigner fetchDoaLevels_ retry pattern).
 */
export async function fetchDoaLevels(tenantId = 'default'): Promise<DoaLevel[]> {
  const response = await apiClient.get('/api/doa-thresholds', { params: { tenantId } })
  if (Array.isArray(response.data)) return response.data
  if (Array.isArray(response.data?.content)) return response.data.content
  throw new Error(`Unexpected DOA levels response shape`)
}
