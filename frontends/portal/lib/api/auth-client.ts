import axios, { AxiosInstance } from 'axios'

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'

/**
 * Create an authenticated API client with the provided access token
 * Use this in server components or API routes where you have access to the session
 */
export function createAuthenticatedClient(accessToken: string): AxiosInstance {
  const client = axios.create({
    baseURL: API_BASE_URL,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${accessToken}`,
    },
    timeout: 30000,
  })

  // Response interceptor for error handling
  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response) {
        console.error('API Error:', error.response.data)

        if (error.response.status === 401) {
          // Unauthorized - token expired or invalid
          // In server components, this should trigger a re-authentication
          console.error('Authentication failed - token may be expired')
        }
      } else if (error.request) {
        console.error('Network Error:', error.request)
      } else {
        console.error('Error:', error.message)
      }

      return Promise.reject(error)
    }
  )

  return client
}
