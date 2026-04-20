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
 */
export async function fetchDoaLevels(tenantId: string = 'default'): Promise<DoaLevel[]> {
  const response = await apiClient.get('/api/doa-thresholds', {
    params: { tenantId }
  })
  return Array.isArray(response.data) ? response.data : response.data.content || []
}
