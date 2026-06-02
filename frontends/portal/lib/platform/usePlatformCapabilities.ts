'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { platformApi } from './api'
import type {
  CandidateGroupEntry,
  CategoryEntry,
  DepartmentEntry,
  FeelExpressionCatalog,
  LocaleEntry,
  PlatformCapabilityResponse,
} from './types'

const FIVE_MINUTES = 5 * 60 * 1000

/** Hook that fetches the full platform capability response for the current tenant. */
export function usePlatformCapabilities() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  const result = useQuery<PlatformCapabilityResponse, Error>({
    queryKey: ['pss', 'capabilities'],
    queryFn: () => platformApi.capabilities(token),
    enabled: status === 'authenticated',
    staleTime: FIVE_MINUTES,
    gcTime: FIVE_MINUTES,
    retry: 1,
  })

  return {
    ...result,
    capabilitiesUnavailable: result.isError || (result.isSuccess && !result.data),
  }
}

/** Hook for the unified candidate-groups list. */
export function useCandidateGroups() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  return useQuery<CandidateGroupEntry[], Error>({
    queryKey: ['pss', 'candidateGroups'],
    queryFn: () => platformApi.candidateGroups(token),
    enabled: status === 'authenticated',
    staleTime: FIVE_MINUTES,
    gcTime: FIVE_MINUTES,
    retry: 1,
    refetchOnMount: 'always',
  })
}

/** Hook for the FEEL expression catalog (DMN designer). */
export function useFeelExpressions() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  return useQuery<FeelExpressionCatalog, Error>({
    queryKey: ['pss', 'feelExpressions'],
    queryFn: () => platformApi.feelExpressions(token),
    enabled: status === 'authenticated',
    staleTime: FIVE_MINUTES,
    gcTime: FIVE_MINUTES,
    retry: 1,
  })
}

/** Hook for tenant categories. */
export function useCategories() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  return useQuery<CategoryEntry[], Error>({
    queryKey: ['pss', 'categories'],
    queryFn: () => platformApi.categories(token),
    enabled: status === 'authenticated',
    staleTime: FIVE_MINUTES,
    gcTime: FIVE_MINUTES,
    retry: 1,
  })
}

/** Hook for ERP departments (visibility scoping). */
export function useDepartments() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  return useQuery<DepartmentEntry[], Error>({
    queryKey: ['pss', 'departments'],
    queryFn: () => platformApi.departments(token),
    enabled: status === 'authenticated',
    staleTime: FIVE_MINUTES,
    gcTime: FIVE_MINUTES,
    retry: 1,
  })
}

/** Hook for tenant locale configuration (currency, timezone, date format). */
export function useLocale() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  return useQuery<LocaleEntry, Error>({
    queryKey: ['pss', 'locale'],
    queryFn: () => platformApi.locale(token),
    enabled: status === 'authenticated',
    staleTime: FIVE_MINUTES,
    gcTime: FIVE_MINUTES,
    retry: 1,
  })
}
