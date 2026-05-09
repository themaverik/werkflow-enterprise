'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getConnectorFormOptions, type FormFieldOption } from '@/lib/api/dtds'

export function useDtdsFormOptions(connectorKey: string, operationId: string) {
  const { status } = useSession()

  const { data, isLoading, error } = useQuery({
    queryKey: ['dtds', 'form-options', connectorKey, operationId],
    queryFn: () => getConnectorFormOptions(connectorKey, operationId),
    enabled: status === 'authenticated' && !!connectorKey && !!operationId,
    staleTime: 60_000,
    retry: 1,
  })

  return {
    options: (data ?? []) as FormFieldOption[],
    isLoading,
    error: error as Error | null,
  }
}
