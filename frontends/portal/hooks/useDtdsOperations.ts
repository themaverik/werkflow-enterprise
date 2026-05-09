'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { listDtdsOperations, type OperationSummary } from '@/lib/api/dtds'

export interface UseDtdsOperationsResult {
  operations: OperationSummary[]
  isLoading: boolean
  error: Error | null
}

export function useDtdsOperations(connectorKey: string | null): UseDtdsOperationsResult {
  const { status } = useSession()

  const { data, isLoading, error } = useQuery({
    queryKey: ['dtds', 'operations', connectorKey],
    queryFn: () => listDtdsOperations(connectorKey!),
    enabled: status === 'authenticated' && !!connectorKey,
    staleTime: 60_000,
    retry: 1,
  })

  return {
    operations: data ?? [],
    isLoading,
    error: error as Error | null,
  }
}
