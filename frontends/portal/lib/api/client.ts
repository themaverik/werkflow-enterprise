import axios from 'axios'
import { emitTokenExpired } from '@/lib/auth/token-expired-event'

// Engine service base URL - supports both Werkflow custom APIs and Flowable APIs
// NOTE: Do not include /api suffix here - different endpoints use different base paths:
// - Flowable APIs: /api/*
// - Werkflow Task APIs: /api/v1/*
// - Service Registry: /werkflow/api/services/*
// - Process Definitions: /werkflow/api/process-definitions/*
// - Forms: /werkflow/api/forms/*
const API_BASE_URL = process.env.NEXT_PUBLIC_ENGINE_API_URL || 'http://localhost:8081'
const ADMIN_API_BASE_URL = process.env.NEXT_PUBLIC_ADMIN_SERVICE_URL || 'http://localhost:8083'
const ERP_API_BASE_URL = process.env.NEXT_PUBLIC_ERP_API_URL || 'http://localhost:8084'

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000, // 30 seconds
  validateStatus: (status) => {
    // Accept 2xx and 3xx status codes as successful
    return status >= 200 && status < 400
  },
})

export const adminApiClient = axios.create({
  baseURL: ADMIN_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
  validateStatus: (status) => status >= 200 && status < 400,
})

export const erpApiClient = axios.create({
  baseURL: ERP_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
  validateStatus: (status) => status >= 200 && status < 400,
})

// Token storage for the interceptor
let tokenGetter: (() => Promise<string | null>) | null = null

// Function to set the token getter (called from client components)
export function setApiClientToken(getter: () => Promise<string | null>) {
  tokenGetter = getter
}

const authInterceptor = async (config: any) => {
  if (tokenGetter) {
    try {
      const token = await tokenGetter()
      if (token) config.headers.Authorization = `Bearer ${token}`
    } catch (error) {
      console.error('Error getting auth token:', error)
    }
  }
  return config
}

apiClient.interceptors.request.use(authInterceptor, (error) => Promise.reject(error))
adminApiClient.interceptors.request.use(authInterceptor, (error) => Promise.reject(error))
erpApiClient.interceptors.request.use(authInterceptor, (error) => Promise.reject(error))

// Response interceptor for error handling
const errorInterceptor = (error: any) => {
  if (error.response) {
    // Server responded with error status
    console.error('API Error:', error.response.data)

    if (error.response.status === 401) {
      // Token expired - emit event to trigger re-login dialog
      emitTokenExpired()
    }
  } else if (error.request) {
    // Request made but no response received
    console.error('Network Error:', error.request)
  } else {
    // Something else happened
    console.error('Error:', error.message)
  }

  return Promise.reject(error)
}

apiClient.interceptors.response.use((response) => response, errorInterceptor)
adminApiClient.interceptors.response.use((response) => response, errorInterceptor)
erpApiClient.interceptors.response.use((response) => response, errorInterceptor)

export default apiClient
