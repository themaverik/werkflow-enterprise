import axios, { AxiosInstance } from 'axios'
import { getSession } from 'next-auth/react'
import { emitTokenExpired } from '@/lib/auth/token-expired-event'

// Engine service base URL - supports both Werkflow custom APIs and Flowable APIs
// NOTE: Do not include /api suffix here - different endpoints use different base paths:
// - Flowable APIs: /api/*
// - Werkflow Task APIs: /api/v1/*
// - Service Registry: /werkflow/api/services/*
// - Process Definitions: /werkflow/api/process-definitions/*
// - Forms: /werkflow/api/forms/*
// Client-side Axios client. Only for use in browser components.
// Route handlers must use ENGINE_API_URL (no NEXT_PUBLIC_) to avoid leaking internal hostnames.
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
  // On a silent-refresh retry the response interceptor has already stamped a fresh
  // Bearer token on the config. Don't overwrite it with tokenGetter()'s value — that
  // reads the React session token, which may not have updated yet after getSession().
  if (config._retry && config.headers?.Authorization) {
    return config
  }
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

// Shared in-flight refresh promise — N concurrent 401s share one getSession() call.
let refreshPromise: Promise<string | null> | null = null

/**
 * Attempt a silent token refresh via NextAuth's getSession().
 * Returns the fresh accessToken, or null if the refresh token is dead
 * (session.error is set) or an exception is thrown.
 * Clears the module-level promise in `finally` so the next 401 after a
 * successful refresh can trigger a new attempt if needed.
 */
function acquireRefreshedToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = getSession()
      .then((session) => {
        if (session?.error || !session?.accessToken) return null
        return session.accessToken as string
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

/**
 * Per-instance response error interceptor factory.
 *
 * On HTTP 401:
 *   1. If `_retry` is already set, this is the retry request itself — fall
 *      through to emitTokenExpired() to avoid an infinite loop.
 *   2. Otherwise attempt a deduplicated silent refresh via NextAuth.
 *      - Fresh token → stamp `_retry`, update Authorization header, re-issue
 *        through the same axios instance so interceptors/baseURL are preserved.
 *      - Null (dead refresh token or error) → emitTokenExpired() + reject.
 *
 * All non-401 errors are passed through unchanged.
 */
function makeErrorInterceptor(instance: AxiosInstance) {
  return async (error: any): Promise<any> => {
    if (error.response) {
      if (error.response.status === 401 && !error.config._retry) {
        const token = await acquireRefreshedToken()

        if (token) {
          error.config._retry = true
          error.config.headers = {
            ...error.config.headers,
            Authorization: `Bearer ${token}`,
          }
          return instance.request(error.config)
        }

        // Genuine auth failure — surface the re-login modal.
        console.error('API Error:', error.response.data)
        emitTokenExpired()
        return Promise.reject(error)
      }

      // Non-401, or already-retried 401 — log and reject.
      console.error('API Error:', error.response.data)
      if (error.response.status === 401) {
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
}

apiClient.interceptors.response.use((response) => response, makeErrorInterceptor(apiClient))
adminApiClient.interceptors.response.use((response) => response, makeErrorInterceptor(adminApiClient))
erpApiClient.interceptors.response.use((response) => response, makeErrorInterceptor(erpApiClient))

export default apiClient
