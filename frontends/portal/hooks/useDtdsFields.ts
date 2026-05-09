'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { listDtdsFields, type FieldEntry, type FieldDirection } from '@/lib/api/dtds'

export interface UseDtdsFieldsResult {
  fields: FieldEntry[]
  isLoading: boolean
  error: Error | null
}

export function useDtdsFields(
  connectorKey: string | null,
  operationId: string | null,
  direction: FieldDirection
): UseDtdsFieldsResult {
  const { status } = useSession()
  const enabled = status === 'authenticated' && !!connectorKey && !!operationId

  const { data, isLoading, error } = useQuery({
    queryKey: ['dtds', 'fields', connectorKey, operationId, direction],
    queryFn: () => listDtdsFields(connectorKey!, operationId!, direction),
    enabled,
    staleTime: 60_000,
    retry: 1,
  })

  return {
    fields: data ?? [],
    isLoading,
    error: error as Error | null,
  }
}
