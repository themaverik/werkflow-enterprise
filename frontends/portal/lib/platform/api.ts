// Platform Semantics Service API client — proxied through /api/proxy/admin/design/platform/
import type {
  CandidateGroupEntry,
  CategoryEntry,
  CategoryRequest,
  DepartmentEntry,
  FeelExpressionCatalog,
  PlatformCapabilityResponse,
  TagEntry,
  VisibilityPolicyEntry,
} from './types'

const BASE = '/api/proxy/admin/design/platform'

async function pssGet<T>(path: string, token: string, search?: URLSearchParams): Promise<T> {
  const url = search ? `${BASE}${path}?${search}` : `${BASE}${path}`
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
  if (!res.ok) throw new Error(`PSS ${path} failed (${res.status})`)
  return res.json() as Promise<T>
}

async function pssMutate<T>(
  method: string,
  path: string,
  token: string,
  body?: unknown
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`PSS ${method} ${path} failed (${res.status})`)
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const platformApi = {
  capabilities: (token: string) =>
    pssGet<PlatformCapabilityResponse>('/capabilities', token),

  candidateGroups: (token: string) =>
    pssGet<CandidateGroupEntry[]>('/candidate-groups', token),

  feelExpressions: (token: string) =>
    pssGet<FeelExpressionCatalog>('/feel-expressions', token),

  categories: (token: string) =>
    pssGet<CategoryEntry[]>('/categories', token),

  createCategory: (token: string, body: CategoryRequest) =>
    pssMutate<CategoryEntry>('POST', '/categories', token, body),

  updateCategory: (token: string, id: string, body: CategoryRequest) =>
    pssMutate<CategoryEntry>('PUT', `/categories/${id}`, token, body),

  deleteCategory: (token: string, id: string) =>
    pssMutate<void>('DELETE', `/categories/${id}`, token),

  tags: (token: string, prefix?: string) => {
    const sp = prefix ? new URLSearchParams({ prefix }) : undefined
    return pssGet<TagEntry[]>('/tags', token, sp)
  },

  departments: (token: string) =>
    pssGet<DepartmentEntry[]>('/departments', token),

  visibilityPolicy: (token: string) =>
    pssGet<VisibilityPolicyEntry>('/visibility-policy', token),
}
