import { adminApiClient } from './client'

// ==================== CREDENTIAL TYPE REGISTRY ====================

export type CredentialFieldType = 'STRING' | 'SECRET' | 'INT' | 'BOOL'

export interface CredentialFieldSchema {
  name: string
  displayName: string
  type: CredentialFieldType
  required: boolean
  defaultValue: string | number | boolean | null
}

export interface CredentialTypeSchema {
  name: string
  displayName: string
  fields: CredentialFieldSchema[]
}

// ADR-020: type registry is hardcoded client-side; backend is the source of truth for slugs.
// To add a new type, add an entry here and ship new Java credential type on the backend.
export const CREDENTIAL_TYPES: CredentialTypeSchema[] = [
  {
    name: 'smtp',
    displayName: 'SMTP Server',
    fields: [
      { name: 'host',     displayName: 'Host',       type: 'STRING', required: true,  defaultValue: null },
      { name: 'port',     displayName: 'Port',       type: 'INT',    required: true,  defaultValue: 587 },
      { name: 'username', displayName: 'Username',   type: 'STRING', required: true,  defaultValue: null },
      { name: 'password', displayName: 'Password',   type: 'SECRET', required: true,  defaultValue: null },
      { name: 'useTls',   displayName: 'Enable TLS', type: 'BOOL',   required: true,  defaultValue: true },
    ],
  },
  {
    name: 'slack-bot-token',
    displayName: 'Slack Bot Token',
    fields: [
      { name: 'botToken',      displayName: 'Bot Token',      type: 'SECRET', required: true, defaultValue: null },
      { name: 'signingSecret', displayName: 'Signing Secret', type: 'SECRET', required: true, defaultValue: null },
    ],
  },
  {
    name: 'whatsapp-cloud-api',
    displayName: 'WhatsApp Cloud API',
    fields: [
      { name: 'accessToken',   displayName: 'Access Token',    type: 'SECRET', required: true, defaultValue: null },
      { name: 'phoneNumberId', displayName: 'Phone Number ID', type: 'STRING', required: true, defaultValue: null },
      { name: 'apiVersion',    displayName: 'API Version',     type: 'STRING', required: true, defaultValue: 'v18.0' },
    ],
  },
]

export function getCredentialType(name: string): CredentialTypeSchema | undefined {
  return CREDENTIAL_TYPES.find((t) => t.name === name)
}

// ==================== RESPONSE / REQUEST TYPES ====================

export interface TenantCredentialResponse {
  id: string
  tenantId: string
  credentialType: string
  label: string
  /** Names of the Vault keys set for this credential — values are never returned. */
  fieldNames: string[]
  createdAt: string
  updatedAt: string
  rotatedAt: string
}

export interface CreateTenantCredentialRequest {
  credentialType: string
  label: string
  values: Record<string, string | number | boolean>
}

export interface UpdateTenantCredentialRequest {
  values: Record<string, string | number | boolean>
}

export interface CredentialTestResult {
  success: boolean
  message: string
}

// ==================== API FUNCTIONS ====================

export async function listCredentials(): Promise<TenantCredentialResponse[]> {
  const res = await adminApiClient.get('/api/v1/config/credentials')
  return Array.isArray(res.data) ? res.data : []
}

export async function getCredential(id: string): Promise<TenantCredentialResponse> {
  const res = await adminApiClient.get(`/api/v1/config/credentials/${encodeURIComponent(id)}`)
  return res.data
}

export async function createCredential(
  req: CreateTenantCredentialRequest,
): Promise<TenantCredentialResponse> {
  const res = await adminApiClient.post('/api/v1/config/credentials', req)
  return res.data
}

export async function updateCredential(
  id: string,
  req: UpdateTenantCredentialRequest,
): Promise<TenantCredentialResponse> {
  const res = await adminApiClient.put(
    `/api/v1/config/credentials/${encodeURIComponent(id)}`,
    req,
  )
  return res.data
}

export async function deleteCredential(id: string): Promise<void> {
  await adminApiClient.delete(`/api/v1/config/credentials/${encodeURIComponent(id)}`)
}

export async function testCredential(id: string): Promise<CredentialTestResult> {
  const res = await adminApiClient.post(
    `/api/v1/config/credentials/${encodeURIComponent(id)}/test`,
    {},
  )
  return res.data
}
