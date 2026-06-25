/**
 * Tests for the silent-refresh + retry-once behaviour in client.ts.
 *
 * We do NOT spin up real axios adapters. Instead each test constructs a
 * minimal fake error object (the shape axios passes to an error interceptor)
 * and exercises the interceptor directly by extracting it from the registered
 * interceptors stack.
 *
 * Three scenarios:
 *   1. Dedup — N concurrent 401s share ONE getSession() call.
 *   2. Retry-once success — fresh token → re-issues request, caller resolves.
 *   3. Fallback — null token (dead refresh token) → emitTokenExpired() + rejects.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { Mock } from 'vitest'

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('next-auth/react', () => ({
  getSession: vi.fn(),
}))

vi.mock('@/lib/auth/token-expired-event', () => ({
  emitTokenExpired: vi.fn(),
}))

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Pull the registered response-error handler off an axios instance.
 * Axios stores interceptors in instance.interceptors.response.handlers as
 * [{ fulfilled, rejected }, ...]; we want the `rejected` (error) handler that
 * makeErrorInterceptor() registered.
 */
function getErrorHandler(instance: import('axios').AxiosInstance) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handlers = (instance.interceptors.response as any).handlers as Array<{
    fulfilled: ((v: unknown) => unknown) | null
    rejected: ((e: unknown) => unknown) | null
  }>
  const handler = handlers.find((h) => h?.rejected != null)
  if (!handler?.rejected) throw new Error('No error interceptor found')
  return handler.rejected
}

/** Build the minimal fake axios 401 error shape. */
function make401Error(retried = false) {
  const config: Record<string, unknown> = { headers: {}, _retry: retried ? true : undefined }
  return {
    response: { status: 401, data: 'Unauthorized' },
    config,
    request: {},
    message: 'Request failed with status code 401',
  }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('client.ts — silent refresh + retry-once on 401', () => {
  let getSession: Mock
  let emitTokenExpired: Mock

  beforeEach(async () => {
    // Reset module registry so the module-level refreshPromise is fresh per test.
    vi.resetModules()

    const nextAuthMock = await import('next-auth/react')
    const tokenEventMock = await import('@/lib/auth/token-expired-event')
    getSession = nextAuthMock.getSession as Mock
    emitTokenExpired = tokenEventMock.emitTokenExpired as Mock
    getSession.mockReset()
    emitTokenExpired.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('dedup: N concurrent 401s share one getSession() call', async () => {
    // Arrange
    let resolveSession!: (v: unknown) => void
    const sessionPromise = new Promise((res) => {
      resolveSession = res
    })
    getSession.mockReturnValue(sessionPromise)

    // Re-import after resetModules so each test gets a pristine module.
    const { apiClient } = await import('./client')
    const errorHandler = getErrorHandler(apiClient)

    // Simulate a successful retry: instance.request succeeds.
    vi.spyOn(apiClient, 'request').mockResolvedValue({ data: 'ok' })

    // Act — fire three simultaneous 401s before the session resolves.
    const p1 = errorHandler(make401Error())
    const p2 = errorHandler(make401Error())
    const p3 = errorHandler(make401Error())

    // Resolve the shared getSession call.
    resolveSession({ accessToken: 'new-token-xyz', error: undefined })
    await Promise.all([p1, p2, p3])

    // Assert
    expect(getSession).toHaveBeenCalledTimes(1)
  })

  it('retry-once success: fresh token → re-issues request, caller resolves', async () => {
    // Arrange
    getSession.mockResolvedValue({ accessToken: 'fresh-token', error: undefined })

    const { apiClient } = await import('./client')
    const errorHandler = getErrorHandler(apiClient)
    const requestSpy = vi.spyOn(apiClient, 'request').mockResolvedValue({ data: 'retried-ok' })

    const fakeError = make401Error()

    // Act
    const result = await errorHandler(fakeError)

    // Assert
    expect(getSession).toHaveBeenCalledTimes(1)
    expect(requestSpy).toHaveBeenCalledTimes(1)
    expect((fakeError.config as Record<string, unknown>)._retry).toBe(true)
    expect((fakeError.config as Record<string, unknown>).headers).toMatchObject({
      Authorization: 'Bearer fresh-token',
    })
    expect(result).toEqual({ data: 'retried-ok' })
    expect(emitTokenExpired).not.toHaveBeenCalled()
  })

  it('fallback: null token (dead refresh) → emitTokenExpired() + rejects', async () => {
    // Arrange — session error set, accessToken absent.
    getSession.mockResolvedValue({ error: 'RefreshAccessTokenError', accessToken: undefined })

    const { apiClient } = await import('./client')
    const errorHandler = getErrorHandler(apiClient)
    vi.spyOn(apiClient, 'request')

    const fakeError = make401Error()

    // Act
    await expect(errorHandler(fakeError)).rejects.toBe(fakeError)

    // Assert
    expect(emitTokenExpired).toHaveBeenCalledTimes(1)
    expect(apiClient.request).not.toHaveBeenCalled()
  })

  it('already-retried 401: _retry=true → does not call getSession, emits expired', async () => {
    // Arrange
    getSession.mockResolvedValue({ accessToken: 'token', error: undefined })

    const { apiClient } = await import('./client')
    const errorHandler = getErrorHandler(apiClient)
    vi.spyOn(apiClient, 'request')

    const fakeError = make401Error(/* retried= */ true)

    // Act
    await expect(errorHandler(fakeError)).rejects.toBe(fakeError)

    // Assert — _retry guard kicks in; getSession must NOT be called again.
    expect(getSession).not.toHaveBeenCalled()
    expect(emitTokenExpired).toHaveBeenCalledTimes(1)
    expect(apiClient.request).not.toHaveBeenCalled()
  })

  it('retry request keeps the fresh token: authInterceptor does not clobber a _retry Authorization', async () => {
    // Arrange
    const { apiClient, setApiClientToken } = await import('./client')
    // tokenGetter returns a STALE token (the React session not yet updated after getSession()).
    setApiClientToken(async () => 'stale-token')

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const reqHandlers = (apiClient.interceptors.request as any).handlers as Array<{
      fulfilled: ((c: Record<string, unknown>) => Promise<Record<string, unknown>>) | null
    }>
    const authInterceptor = reqHandlers.find((h) => h?.fulfilled != null)?.fulfilled
    if (!authInterceptor) throw new Error('No request interceptor found')

    // Act — a retry config already carrying the fresh token.
    const retryConfig = { _retry: true, headers: { Authorization: 'Bearer fresh-token' } }
    const retried = await authInterceptor(retryConfig)

    // Assert — fresh token preserved, stale token NOT applied.
    expect((retried.headers as Record<string, string>).Authorization).toBe('Bearer fresh-token')

    // Sanity — a normal (non-retry) request still gets the token applied.
    const normalConfig = { headers: {} as Record<string, string> }
    const normal = await authInterceptor(normalConfig)
    expect((normal.headers as Record<string, string>).Authorization).toBe('Bearer stale-token')
  })

  it('getSession throwing → emitTokenExpired() + rejects', async () => {
    // Arrange — getSession itself rejects (network error).
    getSession.mockRejectedValue(new Error('network'))

    const { apiClient } = await import('./client')
    const errorHandler = getErrorHandler(apiClient)
    vi.spyOn(apiClient, 'request')

    const fakeError = make401Error()

    // Act / Assert
    await expect(errorHandler(fakeError)).rejects.toBe(fakeError)
    expect(emitTokenExpired).toHaveBeenCalledTimes(1)
    expect(apiClient.request).not.toHaveBeenCalled()
  })

  it('non-401 error → rejects without refresh or expired event', async () => {
    const { apiClient } = await import('./client')
    const errorHandler = getErrorHandler(apiClient)
    vi.spyOn(apiClient, 'request')

    const serverError = {
      response: { status: 500, data: 'Server Error' },
      config: { headers: {} },
      request: {},
      message: 'Request failed with status code 500',
    }

    // Act / Assert — 500 passes through untouched.
    await expect(errorHandler(serverError)).rejects.toBe(serverError)
    expect(getSession).not.toHaveBeenCalled()
    expect(emitTokenExpired).not.toHaveBeenCalled()
    expect(apiClient.request).not.toHaveBeenCalled()
  })
})
