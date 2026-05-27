import { useQuery, UseQueryOptions } from '@tanstack/react-query'
import { fetchDoaLevels, type DoaLevel } from '@/lib/api/doa'

export const DOA_QUERY_KEYS = {
  all: ['doaLevels'] as const,
  byTenant: (tenantId: string) => [...DOA_QUERY_KEYS.all, tenantId] as const,
}

/**
 * Fetches the configured DOA (Delegation of Authority) levels for a tenant.
 *
 * @param tenantId - Tenant identifier. Defaults to 'default' when not provided.
 * @param options  - Optional React Query overrides.
 */
export function useDoaLevels(
  tenantId: string = 'default',
  options?: UseQueryOptions<DoaLevel[], Error>
) {
  return useQuery<DoaLevel[], Error>({
    queryKey: DOA_QUERY_KEYS.byTenant(tenantId),
    queryFn: () => fetchDoaLevels(tenantId),
    staleTime: 300_000, // 5 minutes — DOA config changes infrequently
    ...options,
  })
}

/**
 * Parses a DoaLevel.doaLevel string to a numeric level.
 *
 * Accepts the config `doaLevel` STRING field only (e.g. "1", "L2").
 * Callers must NOT pass the numeric JWT claim (user.doaLevel) directly — that
 * is already a number and does not need parsing.
 *
 * The admin service stores doaLevel as a string. In practice values are:
 *   - Numeric strings: "1", "2", "3", "4"
 *   - Possibly prefixed: "L1", "L2" etc.
 *
 * Strips any non-numeric prefix, then parses as integer.
 * Returns 0 on parse failure (safe default — treated as below any configured level).
 */
export function parseDoaLevelNumber(doaLevel: string): number {
  const stripped = doaLevel.replace(/^[^0-9]+/, '')
  const parsed = parseInt(stripped, 10)
  return isNaN(parsed) ? 0 : parsed
}
