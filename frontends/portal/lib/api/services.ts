import { apiClient } from './client'

/**
 * Service Registry API Client
 *
 * Phase 4 Frontend Integration - Real API Only (Mock Fallback Removed)
 *
 * Manages service discovery, URL configuration, and endpoint documentation
 * for cross-service workflow integration.
 */

// ==================== TYPE DEFINITIONS ====================

export interface Service {
  id: string
  serviceName: string
  displayName: string
  description: string
  serviceType: string
  healthStatus: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN'
  baseUrl?: string
  environment?: 'development' | 'staging' | 'production'
  status?: 'active' | 'inactive' | 'maintenance'
  lastChecked?: Date
  responseTime?: number
  version?: string
  tags?: string[]
  endpoints?: ServiceEndpoint[]
  createdAt?: Date
  updatedAt?: Date
}

export interface ServiceEndpoint {
  id?: string
  serviceName: string
  endpointPath: string
  httpMethod: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  description: string
  requestSchema?: string
  responseSchema?: string
  parameters?: ServiceParameter[]
  exampleRequest?: string
  exampleResponse?: string
}

export interface ServiceParameter {
  name: string
  type: string
  required: boolean
  description: string
  defaultValue?: any
}

export interface ServiceEnvironmentUrl {
  id?: string
  serviceName: string
  environment: string
  baseUrl: string
  priority: number
  isActive: boolean
  createdAt?: Date
  updatedAt?: Date
}

export interface HealthCheckResult {
  status: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN'
  responseTime: number
  errorMessage?: string
  timestamp: Date
}

export interface ServiceConnectivityTestResult {
  online: boolean
  responseTime: number
  error?: string
  timestamp: Date
}

export interface CreateServiceRequest {
  serviceName: string
  displayName: string
  description: string
  serviceType: string
  baseUrl?: string
  environment?: string
  tags?: string[]
}

export interface UpdateServiceRequest {
  displayName?: string
  description?: string
  serviceType?: string
  baseUrl?: string
  environment?: string
  status?: string
  tags?: string[]
}

// ==================== API ERROR CLASSES ====================

export class ServiceRegistryError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public details?: any
  ) {
    super(message)
    this.name = 'ServiceRegistryError'
  }
}

export class ServiceNotFoundError extends ServiceRegistryError {
  constructor(identifier: string) {
    super(`Service '${identifier}' not found`, 404)
    this.name = 'ServiceNotFoundError'
  }
}

export class ServiceAlreadyExistsError extends ServiceRegistryError {
  constructor(serviceName: string) {
    super(`Service '${serviceName}' already exists`, 409)
    this.name = 'ServiceAlreadyExistsError'
  }
}

export class ValidationError extends ServiceRegistryError {
  constructor(message: string, details?: any) {
    super(message, 400, details)
    this.name = 'ValidationError'
  }
}

export class NetworkError extends ServiceRegistryError {
  constructor(message: string = 'Network connection failed. Please check if the backend service is running.') {
    super(message, 0)
    this.name = 'NetworkError'
  }
}

// ==================== ERROR HANDLER ====================

function handleApiError(error: any, context: string): never {
  // Check for axios error structure
  if (error.response) {
    // Server responded with error status
    const status = error.response.status
    const data = error.response.data

    switch (status) {
      case 404:
        throw new ServiceNotFoundError(context)
      case 409:
        throw new ServiceAlreadyExistsError(context)
      case 400:
        throw new ValidationError(
          data.message || 'Invalid request data',
          data.errors || data
        )
      case 401:
        throw new ServiceRegistryError('Authentication required', 401)
      case 403:
        throw new ServiceRegistryError('Access denied', 403)
      case 500:
        throw new ServiceRegistryError(
          'Internal server error. Please try again later.',
          500,
          data
        )
      default:
        throw new ServiceRegistryError(
          data.message || `Service Registry API error (${status})`,
          status,
          data
        )
    }
  }

  // Request made but no response received (network error, timeout, CORS)
  if (error.request) {
    throw new NetworkError('Cannot connect to Service Registry API. Please ensure the backend service is running.')
  }

  // Network error codes
  if (error.code === 'ECONNREFUSED' || error.code === 'ERR_NETWORK' || error.code === 'ETIMEDOUT') {
    throw new NetworkError('Cannot connect to Service Registry API. Please ensure the backend service is running.')
  }

  // Unknown error
  throw new ServiceRegistryError(error.message || 'Unknown error occurred')
}

// ==================== API FUNCTIONS ====================

/**
 * Get all registered services
 *
 * @throws {NetworkError} If backend service is not accessible
 * @throws {ServiceRegistryError} If API returns an error
 */
export async function getServices(): Promise<Service[]> {
  try {
    const response = await apiClient.get('/api/services')
    const services = Array.isArray(response.data) ? response.data : response.data.content || []
    return services.map((service: any) => ({
      ...service,
      lastChecked: service.lastChecked ? new Date(service.lastChecked) : undefined,
      createdAt: service.createdAt ? new Date(service.createdAt) : undefined,
      updatedAt: service.updatedAt ? new Date(service.updatedAt) : undefined
    }))
  } catch (error: any) {
    handleApiError(error, 'services list')
  }
}

/**
 * Get a specific service by ID
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {NetworkError} If backend service is not accessible
 */
export async function getServiceById(serviceId: string): Promise<Service> {
  try {
    const response = await apiClient.get(`/api/services/${serviceId}`)
    return {
      ...response.data,
      lastChecked: response.data.lastChecked ? new Date(response.data.lastChecked) : undefined,
      createdAt: response.data.createdAt ? new Date(response.data.createdAt) : undefined,
      updatedAt: response.data.updatedAt ? new Date(response.data.updatedAt) : undefined
    }
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Get a specific service by name
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {NetworkError} If backend service is not accessible
 */
export async function getServiceByName(serviceName: string): Promise<Service> {
  try {
    const response = await apiClient.get(`/api/services/by-name/${serviceName}`)
    return {
      ...response.data,
      lastChecked: response.data.lastChecked ? new Date(response.data.lastChecked) : undefined,
      createdAt: response.data.createdAt ? new Date(response.data.createdAt) : undefined,
      updatedAt: response.data.updatedAt ? new Date(response.data.updatedAt) : undefined
    }
  } catch (error: any) {
    handleApiError(error, serviceName)
  }
}

/**
 * Create a new service registration
 *
 * @throws {ServiceAlreadyExistsError} If service name already exists
 * @throws {ValidationError} If request data is invalid
 * @throws {NetworkError} If backend service is not accessible
 */
export async function createService(data: CreateServiceRequest): Promise<Service> {
  try {
    const response = await apiClient.post('/api/services', data)
    return {
      ...response.data,
      createdAt: response.data.createdAt ? new Date(response.data.createdAt) : undefined,
      updatedAt: response.data.updatedAt ? new Date(response.data.updatedAt) : undefined
    }
  } catch (error: any) {
    handleApiError(error, data.serviceName)
  }
}

/**
 * Update service configuration
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {ValidationError} If request data is invalid
 * @throws {NetworkError} If backend service is not accessible
 */
export async function updateService(serviceId: string, data: UpdateServiceRequest): Promise<Service> {
  try {
    const response = await apiClient.put(`/api/services/${serviceId}`, data)
    return {
      ...response.data,
      lastChecked: response.data.lastChecked ? new Date(response.data.lastChecked) : undefined,
      createdAt: response.data.createdAt ? new Date(response.data.createdAt) : undefined,
      updatedAt: response.data.updatedAt ? new Date(response.data.updatedAt) : undefined
    }
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Update service base URL (deprecated - use updateServiceEnvironmentUrl instead)
 *
 * @deprecated Use updateServiceEnvironmentUrl for environment-specific URLs
 */
export async function updateServiceUrl(serviceId: string, baseUrl: string, environment?: string): Promise<Service> {
  try {
    const response = await apiClient.patch(`/api/services/${serviceId}/url`, {
      baseUrl,
      environment: environment || 'development'
    })
    return {
      ...response.data,
      lastChecked: response.data.lastChecked ? new Date(response.data.lastChecked) : undefined
    }
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Delete a service registration
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {NetworkError} If backend service is not accessible
 */
export async function deleteService(serviceId: string): Promise<void> {
  try {
    await apiClient.delete(`/api/services/${serviceId}`)
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Test service connectivity
 *
 * @throws {NetworkError} If backend service is not accessible
 */
export async function testServiceConnectivity(serviceUrl: string): Promise<ServiceConnectivityTestResult> {
  try {
    const startTime = Date.now()
    const response = await apiClient.post('/api/services/test-connectivity', {
      url: serviceUrl
    })
    const responseTime = Date.now() - startTime

    return {
      online: response.data.online || true,
      responseTime,
      error: response.data.error,
      timestamp: new Date()
    }
  } catch (error: any) {
    handleApiError(error, 'connectivity test')
  }
}

/**
 * Get service endpoints
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {NetworkError} If backend service is not accessible
 */
export async function getServiceEndpoints(serviceId: string): Promise<ServiceEndpoint[]> {
  try {
    const response = await apiClient.get(`/api/services/${serviceId}/endpoints`)
    return Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Get service URLs for all environments
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {NetworkError} If backend service is not accessible
 */
export async function getServiceUrls(serviceId: string): Promise<ServiceEnvironmentUrl[]> {
  try {
    const response = await apiClient.get(`/api/services/${serviceId}/urls`)
    const urls = Array.isArray(response.data) ? response.data : response.data.content || []
    return urls.map((url: any) => ({
      ...url,
      createdAt: url.createdAt ? new Date(url.createdAt) : undefined,
      updatedAt: url.updatedAt ? new Date(url.updatedAt) : undefined
    }))
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Update or create environment URL for a service
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {ValidationError} If request data is invalid
 */
export async function updateServiceEnvironmentUrl(
  serviceId: string,
  environment: string,
  baseUrl: string,
  priority: number = 1,
  isActive: boolean = true
): Promise<ServiceEnvironmentUrl> {
  try {
    const response = await apiClient.post(`/api/services/${serviceId}/urls`, {
      environment,
      baseUrl,
      priority,
      isActive
    })
    return {
      ...response.data,
      createdAt: response.data.createdAt ? new Date(response.data.createdAt) : undefined,
      updatedAt: response.data.updatedAt ? new Date(response.data.updatedAt) : undefined
    }
  } catch (error: any) {
    handleApiError(error, `${serviceId}/${environment}`)
  }
}

/**
 * Resolve service URL by name and environment
 *
 * @throws {ServiceNotFoundError} If service doesn't exist or no URL for environment
 * @throws {NetworkError} If backend service is not accessible
 */
export async function resolveServiceUrl(serviceName: string, environment: string = 'development'): Promise<string> {
  try {
    const response = await apiClient.get(`/api/services/resolve/${serviceName}`, {
      params: { env: environment }
    })
    return response.data.url || response.data.baseUrl
  } catch (error: any) {
    handleApiError(error, `${serviceName}/${environment}`)
  }
}

/**
 * Get service health status
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 * @throws {NetworkError} If backend service is not accessible
 */
export async function getServiceHealth(serviceId: string): Promise<HealthCheckResult> {
  try {
    const response = await apiClient.get(`/api/services/${serviceId}/health`)
    return {
      ...response.data,
      timestamp: new Date(response.data.timestamp || Date.now())
    }
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}

/**
 * Trigger manual health check for a service
 *
 * @throws {ServiceNotFoundError} If service doesn't exist
 */
export async function triggerHealthCheck(serviceId: string): Promise<HealthCheckResult> {
  try {
    const response = await apiClient.post(`/api/services/${serviceId}/health/check`)
    return {
      ...response.data,
      timestamp: new Date(response.data.timestamp || Date.now())
    }
  } catch (error: any) {
    handleApiError(error, serviceId)
  }
}
