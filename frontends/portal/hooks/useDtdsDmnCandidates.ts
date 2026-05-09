'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getDmnBindingCandidates, type DmnBindingCandidate } from '@/lib/api/dtds'

export function useDtdsDmnCandidates(
  processDefId: string,
  activityId: string,
  dmnId: string
) {
  const { status } = useSession()

  const { data, isLoading, error } = useQuery({
    queryKey: ['dtds', 'dmn-candidates', processDefId, activityId, dmnId],
    queryFn: () => getDmnBindingCandidates(processDefId, activityId, dmnId),
    enabled: status === 'authenticated' && !!processDefId && !!activityId && !!dmnId,
    staleTime: 60_000,
    retry: 1,
  })

  return {
    candidates: (data ?? []) as DmnBindingCandidate[],
    isLoading,
    error: error as Error | null,
  }
}
