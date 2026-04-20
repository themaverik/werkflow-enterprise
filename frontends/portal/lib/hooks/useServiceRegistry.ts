import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getServices,
  getServiceById,
  getServiceByName,
  createService,
  updateService,
  updateServiceUrl,
  updateServiceEnvironmentUrl,
  deleteService,
  testServiceConnectivity,
  getServiceEndpoints,
  getServiceUrls,
  resolveServiceUrl,
  getServiceHealth,
  triggerHealthCheck,
  type Service,
  type CreateServiceRequest,
  type UpdateServiceRequest,
  type ServiceConnectivityTestResult,
  type ServiceEndpoint,
  type ServiceEnvironmentUrl,
  type HealthCheckResult,
  NetworkError
} from '@/lib/api/services'
import { useToast } from '@/hooks/use-toast'

/**
 * Phase 4 Service Registry Hooks
 *
 * Enhanced with:
 * - Exponential backoff retry (3 attempts)
 * - 10-minute cache TTL with automatic invalidation
 * - Comprehensive error handling
 * - User-friendly toast notifications
 */

// ==================== QUERY HOOKS ====================

/**
 * Hook to fetch all services with retry logic
 *
 * Features:
 * - Refetches every 60 seconds
 * - 30-second stale time
 * - 3 retry attempts with exponential backoff
 */
export function useServices() {
  return useQuery({
    queryKey: ['services'],
    queryFn: getServices,
    refetchInterval: 60000, // Refetch every minute
    staleTime: 30000, // Consider data stale after 30 seconds
    retry: (failureCount, error) => {
      // Don't retry on network errors (backend down)
      if (error instanceof NetworkError) {
        return false
      }
      // Retry up to 3 times for other errors
      return failureCount < 3
    },
    retryDelay: (attemptIndex) => {
      // Exponential backoff: 1s, 2s, 4s
      return Math.min(1000 * 2 ** attemptIndex, 30000)
    }
  })
}

/**
 * Hook to fetch a specific service by ID
 */
export function useService(serviceId: string) {
  return useQuery({
    queryKey: ['services', serviceId],
    queryFn: () => getServiceById(serviceId),
    enabled: !!serviceId,
    staleTime: 30000,
    retry: (failureCount, error) => {
      if (error instanceof NetworkError) return false
      return failureCount < 3
    },
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000)
  })
}

/**
 * Hook to fetch a specific service by name
 */
export function useServiceByName(serviceName: string) {
  return useQuery({
    queryKey: ['services', 'by-name', serviceName],
    queryFn: () => getServiceByName(serviceName),
    enabled: !!serviceName,
    staleTime: 30000,
    retry: (failureCount, error) => {
      if (error instanceof NetworkError) return false
      return failureCount < 3
    },
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000)
  })
}

/**
 * Hook to fetch service endpoints
 */
export function useServiceEndpoints(serviceId: string) {
  return useQuery({
    queryKey: ['services', serviceId, 'endpoints'],
    queryFn: () => getServiceEndpoints(serviceId),
    enabled: !!serviceId,
    staleTime: 60000, // Endpoints don't change often
    retry: (failureCount, error) => {
      if (error instanceof NetworkError) return false
      return failureCount < 3
    }
  })
}

/**
 * Hook to fetch service environment URLs
 */
export function useServiceUrls(serviceId: string) {
  return useQuery({
    queryKey: ['services', serviceId, 'urls'],
    queryFn: () => getServiceUrls(serviceId),
    enabled: !!serviceId,
    staleTime: 30000,
    retry: (failureCount, error) => {
      if (error instanceof NetworkError) return false
      return failureCount < 3
    }
  })
}

/**
 * Hook to resolve service URL by name and environment
 */
export function useResolveServiceUrl(serviceName: string, environment: string = 'development') {
  return useQuery({
    queryKey: ['services', 'resolve', serviceName, environment],
    queryFn: () => resolveServiceUrl(serviceName, environment),
    enabled: !!serviceName && !!environment,
    staleTime: 30000,
    retry: (failureCount, error) => {
      if (error instanceof NetworkError) return false
      return failureCount < 3
    }
  })
}

/**
 * Hook to check service health
 */
export function useServiceHealth(serviceId: string) {
  return useQuery({
    queryKey: ['services', serviceId, 'health'],
    queryFn: () => getServiceHealth(serviceId),
    enabled: !!serviceId,
    refetchInterval: 30000, // Check health every 30 seconds
    staleTime: 15000,
    retry: false // Don't retry health checks
  })
}

// ==================== MUTATION HOOKS ====================

/**
 * Hook to create a new service
 */
export function useCreateService() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateServiceRequest) => createService(data),
    onSuccess: (data) => {
      // Invalidate services list to trigger refetch
      queryClient.invalidateQueries({ queryKey: ['services'] })

      toast({
        title: 'Service created',
        description: `Service '${data.displayName}' has been registered successfully.`
      })
    },
    onError: (error: any) => {
      toast({
        title: 'Failed to create service',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

/**
 * Hook to update a service
 */
export function useUpdateService() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ serviceId, data }: { serviceId: string; data: UpdateServiceRequest }) =>
      updateService(serviceId, data),
    onSuccess: (data, variables) => {
      // Invalidate both list and individual service queries
      queryClient.invalidateQueries({ queryKey: ['services'] })
      queryClient.invalidateQueries({ queryKey: ['services', variables.serviceId] })

      toast({
        title: 'Service updated',
        description: `Service configuration has been updated successfully.`
      })
    },
    onError: (error: any, variables) => {
      toast({
        title: 'Failed to update service',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

/**
 * Hook to update service URL (deprecated)
 *
 * @deprecated Use useUpdateServiceEnvironmentUrl instead
 */
export function useUpdateServiceUrl() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({
      serviceId,
      baseUrl,
      environment
    }: {
      serviceId: string
      baseUrl: string
      environment?: string
    }) => updateServiceUrl(serviceId, baseUrl, environment),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      queryClient.invalidateQueries({ queryKey: ['services', variables.serviceId] })

      toast({
        title: 'Service URL updated',
        description: 'Service URL has been updated successfully.'
      })
    },
    onError: (error: any) => {
      toast({
        title: 'Failed to update service URL',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

/**
 * Hook to update service environment URL
 */
export function useUpdateServiceEnvironmentUrl() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({
      serviceId,
      environment,
      baseUrl,
      priority,
      isActive
    }: {
      serviceId: string
      environment: string
      baseUrl: string
      priority?: number
      isActive?: boolean
    }) => updateServiceEnvironmentUrl(serviceId, environment, baseUrl, priority, isActive),
    onSuccess: (data, variables) => {
      // Invalidate all related queries
      queryClient.invalidateQueries({ queryKey: ['services'] })
      queryClient.invalidateQueries({ queryKey: ['services', variables.serviceId] })
      queryClient.invalidateQueries({ queryKey: ['services', variables.serviceId, 'urls'] })
      queryClient.invalidateQueries({
        queryKey: ['services', 'resolve', undefined, variables.environment]
      })

      toast({
        title: 'Environment URL updated',
        description: `${variables.environment} URL has been configured successfully.`
      })
    },
    onError: (error: any, variables) => {
      toast({
        title: 'Failed to update environment URL',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

/**
 * Hook to delete a service
 */
export function useDeleteService() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (serviceId: string) => deleteService(serviceId),
    onSuccess: (data, serviceId) => {
      // Invalidate and remove service from cache
      queryClient.invalidateQueries({ queryKey: ['services'] })
      queryClient.removeQueries({ queryKey: ['services', serviceId] })

      toast({
        title: 'Service deleted',
        description: 'Service has been removed from the registry.'
      })
    },
    onError: (error: any) => {
      toast({
        title: 'Failed to delete service',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

/**
 * Hook to test service connectivity
 */
export function useTestServiceConnectivity() {
  const { toast } = useToast()

  return useMutation({
    mutationFn: (serviceUrl: string) => testServiceConnectivity(serviceUrl),
    onSuccess: (result) => {
      if (result.online) {
        toast({
          title: 'Service is online',
          description: `Response time: ${result.responseTime}ms`
        })
      } else {
        toast({
          title: 'Service is offline',
          description: result.error || 'Unable to connect to service',
          variant: 'destructive'
        })
      }
    },
    onError: (error: any) => {
      toast({
        title: 'Connectivity test failed',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

/**
 * Hook to trigger manual health check
 */
export function useTriggerHealthCheck() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (serviceId: string) => triggerHealthCheck(serviceId),
    onSuccess: (result, serviceId) => {
      // Invalidate health check query
      queryClient.invalidateQueries({ queryKey: ['services', serviceId, 'health'] })
      queryClient.invalidateQueries({ queryKey: ['services', serviceId] })
      queryClient.invalidateQueries({ queryKey: ['services'] })

      if (result.status === 'HEALTHY') {
        toast({
          title: 'Service is healthy',
          description: `Response time: ${result.responseTime}ms`
        })
      } else {
        toast({
          title: 'Service is unhealthy',
          description: result.errorMessage || 'Health check failed',
          variant: 'destructive'
        })
      }
    },
    onError: (error: any) => {
      toast({
        title: 'Health check failed',
        description: error.message || 'An unexpected error occurred.',
        variant: 'destructive'
      })
    }
  })
}

// ==================== UTILITY HOOKS ====================

/**
 * Hook to invalidate all service queries (force refresh)
 */
export function useInvalidateServices() {
  const queryClient = useQueryClient()

  return () => {
    queryClient.invalidateQueries({ queryKey: ['services'] })
  }
}

/**
 * Hook to prefetch service data for performance optimization
 */
export function usePrefetchService() {
  const queryClient = useQueryClient()

  return (serviceId: string) => {
    queryClient.prefetchQuery({
      queryKey: ['services', serviceId],
      queryFn: () => getServiceById(serviceId),
      staleTime: 30000
    })
  }
}
