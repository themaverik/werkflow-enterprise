'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { listDtdsConnectors, type ConnectorSummary } from '@/lib/api/dtds'

export interface UseDtdsConnectorsResult {
  connectors: ConnectorSummary[]
  isLoading: boolean
  error: Error | null
  refetch: () => void
}

export function useDtdsConnectors(): UseDtdsConnectorsResult {
  const { status } = useSession()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dtds', 'connectors'],
    queryFn: listDtdsConnectors,
    enabled: status === 'authenticated',
    staleTime: 60_000,
    retry: 1,
  })

  return {
    connectors: data ?? [],
    isLoading,
    error: error as Error | null,
    refetch,
  }
}
