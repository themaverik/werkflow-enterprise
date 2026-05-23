'use client'

import { useState, useMemo, useEffect } from 'react'
import Link from 'next/link'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/hooks/use-toast'
import {
  RefreshCw, Send, Copy, Check, Eye, EyeOff,
  Plus, Trash2, Save, ChevronDown, ChevronUp, AlertTriangle,
} from 'lucide-react'
import {
  createConnector,
  updateConnector,
  testConnector,
  updateEndpointSchema,
  registerApiKey,
  addConnectorEndpoint,
  deleteConnectorEndpoint,
  type ConnectorType,
  type ConnectorRequest,
  type ConnectorResponse,
  type ConnectorTestResponse,
} from '@/lib/api/connectors'
import {
  listCredentials,
  getCredentialType,
  AUTH_SCHEME_TO_CREDENTIAL_TYPE,
  type TenantCredentialResponse,
} from '@/lib/api/credentials'

// ─── Schema types ────────────────────────────────────────────
export interface SampleRequest {
  id: string
  method: string
  path: string
  requestBody?: string | null
  response: string
  statusCode: number
  durationMs: number
  savedAt: string
}

export interface ConnectorSchema {
  version: 2
  requests: SampleRequest[]
}

function parseSchema(raw: string | null | undefined): ConnectorSchema {
  if (!raw) return { version: 2, requests: [] }
  try {
    const parsed = JSON.parse(raw)
    if (parsed && parsed.version === 2 && Array.isArray(parsed.requests)) {
      return parsed as ConnectorSchema
    }
    // Legacy: raw response body — migrate to a single unlabelled entry
    return {
      version: 2,
      requests: [{
        id: crypto.randomUUID(),
        method: '?',
        path: '?',
        response: raw,
        statusCode: 200,
        durationMs: 0,
        savedAt: new Date().toISOString(),
      }],
    }
  } catch {
    return { version: 2, requests: [] }
  }
}

function serializeSchema(schema: ConnectorSchema): string {
  return JSON.stringify(schema)
}

// ─── Key generation ──────────────────────────────────────────
async function generateApiKey(): Promise<{ rawKey: string; keyHash: string }> {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  const rawKey = Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('')
  const hashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rawKey))
  const keyHash = Array.from(new Uint8Array(hashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('')
  return { rawKey, keyHash }
}

// ─── Helpers ─────────────────────────────────────────────────
const ENV_ORDER = ['production', 'staging', 'development']

const envVariant = (env: string): 'default' | 'warning' | 'secondary' => {
  if (env === 'production') return 'default'
  if (env === 'staging') return 'warning'
  return 'secondary'
}

const statusColor = (code: number): 'success' | 'warning' | 'destructive' => {
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'warning'
  return 'destructive'
}

// ─── Component ───────────────────────────────────────────────
interface ConnectorFormProps {
  existingConnector?: ConnectorResponse
  allEndpoints?: ConnectorResponse[]
  defaultTab?: 'general' | 'auth' | 'contract' | 'test'
  onSaved?: () => void
}

export function ConnectorForm({
  existingConnector,
  allEndpoints,
  defaultTab,
  onSaved,
}: ConnectorFormProps) {
  const { toast } = useToast()
  const isEdit = !!existingConnector

  // ── General
  const [connectorKey, setConnectorKey] = useState(existingConnector?.connectorKey ?? '')
  const [displayName, setDisplayName] = useState(existingConnector?.displayName ?? '')
  const [connectorType, setConnectorType] = useState<ConnectorType>(existingConnector?.connectorType ?? 'API')
  const [baseUrl, setBaseUrl] = useState(existingConnector?.baseUrl ?? '')
  const [environment, setEnvironment] = useState(existingConnector?.environment ?? 'production')
  const [active, setActive] = useState(existingConnector?.active ?? true)

  // ── Auth (credential picker — Phase B.6)
  const [authScheme, setAuthScheme] = useState(existingConnector?.authScheme ?? 'BEARER')
  const [credentialRef, setCredentialRef] = useState(existingConnector?.credentialRef ?? '')
  const [credentials, setCredentials] = useState<TenantCredentialResponse[]>([])
  const [credentialsLoading, setCredentialsLoading] = useState(true)

  // ── ERP key inline reveal
  const [generatedRawKey, setGeneratedRawKey] = useState('')
  const [showRawKey, setShowRawKey] = useState(false)
  const [copiedKey, setCopiedKey] = useState(false)
  const [erpKeyLoading, setErpKeyLoading] = useState(false)

  // ── Multi-endpoint management
  const [endpoints, setEndpoints] = useState<ConnectorResponse[]>(
    allEndpoints
      ? [...allEndpoints].sort((a, b) => ENV_ORDER.indexOf(a.environment) - ENV_ORDER.indexOf(b.environment))
      : existingConnector ? [existingConnector] : []
  )
  const [addingEndpoint, setAddingEndpoint] = useState(false)
  const [newEpUrl, setNewEpUrl] = useState('')
  const [newEpEnv, setNewEpEnv] = useState('staging')
  const [addEpLoading, setAddEpLoading] = useState(false)

  // ── Test call
  const [testEnvId, setTestEnvId] = useState<number | null>(
    existingConnector?.endpointId ?? null
  )
  const [testPath, setTestPath] = useState('')
  const [testMethod, setTestMethod] = useState<'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'>('GET')
  const [testBody, setTestBody] = useState('')
  const [testResult, setTestResult] = useState<ConnectorTestResponse | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  // ── Save contract (overwrite confirmation)
  const [savingContract, setSavingContract] = useState(false)
  const [pendingContractSave, setPendingContractSave] = useState<SampleRequest | null>(null)
  const [overwriteTarget, setOverwriteTarget] = useState<SampleRequest | null>(null)

  // ── Per-endpoint schema state (live — updated on save)
  const [endpointSchemas, setEndpointSchemas] = useState<Record<number, ConnectorSchema>>(
    Object.fromEntries(
      (allEndpoints ?? (existingConnector ? [existingConnector] : [])).map(ep => [
        ep.endpointId,
        parseSchema(ep.sampleSchema),
      ])
    )
  )

  // ── Expanded cards
  const [expandedCards, setExpandedCards] = useState<Set<string>>(new Set())

  const [saving, setSaving] = useState(false)

  // ── Credential picker data (Phase B.6) ───────────────────────
  useEffect(() => {
    listCredentials()
      .then(setCredentials)
      .catch((err) => {
        console.error('Failed to load credentials for connector picker', err)
        setCredentials([])
      })
      .finally(() => setCredentialsLoading(false))
  }, [])

  const credentialType = AUTH_SCHEME_TO_CREDENTIAL_TYPE[authScheme]
  const matchingCredentials = useMemo(
    () => credentials.filter((c) => c.credentialType === credentialType),
    [credentials, credentialType],
  )
  // Edit mode keeps the picker visible with its pre-selected ref; only create mode
  // shows the empty-state link when no credential of the required type exists yet.
  const showCredEmptyState =
    !isEdit && !credentialsLoading && credentialType !== undefined && matchingCredentials.length === 0
  const showCredPicker = !credentialsLoading && credentialType !== undefined && !showCredEmptyState

  // authScheme drives the credential type; switching it invalidates the current pick.
  const handleAuthSchemeChange = (v: string) => {
    setAuthScheme(v)
    setCredentialRef('')
  }

  // True when an edit-mode connector references a credential that no longer exists for
  // its current type (e.g. deleted in OpenBao) — the picker would render with no selection.
  const credentialStale =
    isEdit && !credentialsLoading && authScheme !== 'NONE' && credentialRef !== '' &&
    !matchingCredentials.some((c) => c.label === credentialRef)

  const credentialOk = authScheme === 'NONE' || credentialRef !== ''
  const canSave = isEdit
    ? displayName.trim() !== '' && baseUrl.trim() !== '' && credentialOk
    : connectorKey.trim() !== '' && displayName.trim() !== '' && baseUrl.trim() !== '' && credentialOk

  const usedEnvironments = new Set(endpoints.map(e => e.environment))
  const availableEnvs = ['development', 'staging', 'production'].filter(e => !usedEnvironments.has(e))

  const testEndpoint = endpoints.find(e => e.endpointId === testEnvId) ?? endpoints[0]

  // ─── All saved requests across all endpoints (for Sample Requests tab) ───
  const allSampleCards = useMemo(() => {
    const cards: { ep: ConnectorResponse; req: SampleRequest }[] = []
    for (const ep of endpoints) {
      const schema = endpointSchemas[ep.endpointId]
      if (schema) {
        for (const req of schema.requests) {
          cards.push({ ep, req })
        }
      }
    }
    return cards
  }, [endpoints, endpointSchemas])

  // ─── Handlers ────────────────────────────────────────────────

  const handleTestCall = async () => {
    if (!testEndpoint) return
    setTestLoading(true)
    setTestResult(null)
    try {
      const result = await testConnector(connectorKey, {
        path: testPath,
        method: testMethod,
        requestBody: testBody.trim() || undefined,
        environment: testEndpoint.environment,
      })
      setTestResult(result)
    } catch (err: unknown) {
      toast({ title: 'Test failed', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setTestLoading(false)
    }
  }

  const doSaveContract = async (ep: ConnectorResponse, newReq: SampleRequest) => {
    setSavingContract(true)
    try {
      const current = endpointSchemas[ep.endpointId] ?? { version: 2, requests: [] }
      // Remove any existing entry for same method+path on this endpoint
      const filtered = current.requests.filter(
        r => !(r.method === newReq.method && r.path === newReq.path)
      )
      const updated: ConnectorSchema = { version: 2, requests: [...filtered, newReq] }
      await updateEndpointSchema(connectorKey, ep.endpointId, serializeSchema(updated))
      setEndpointSchemas(prev => ({ ...prev, [ep.endpointId]: updated }))
      toast({ title: 'Contract saved', description: `${newReq.method} ${newReq.path} saved for ${ep.environment}.` })
    } catch (err: unknown) {
      toast({ title: 'Failed to save', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setSavingContract(false)
      setPendingContractSave(null)
      setOverwriteTarget(null)
    }
  }

  const handleSaveContract = () => {
    if (!testResult || !testEndpoint) return
    const newReq: SampleRequest = {
      id: crypto.randomUUID(),
      method: testMethod,
      path: testPath,
      requestBody: testBody.trim() || null,
      response: testResult.body,
      statusCode: testResult.statusCode,
      durationMs: testResult.durationMs,
      savedAt: new Date().toISOString(),
    }
    const current = endpointSchemas[testEndpoint.endpointId]
    const existing = current?.requests.find(r => r.method === newReq.method && r.path === newReq.path)
    if (existing) {
      // Show inline overwrite confirmation
      setPendingContractSave(newReq)
      setOverwriteTarget(existing)
    } else {
      doSaveContract(testEndpoint, newReq)
    }
  }

  const handleDeleteSampleRequest = async (ep: ConnectorResponse, reqId: string) => {
    const current = endpointSchemas[ep.endpointId] ?? { version: 2, requests: [] }
    const updated: ConnectorSchema = {
      version: 2,
      requests: current.requests.filter(r => r.id !== reqId),
    }
    try {
      await updateEndpointSchema(connectorKey, ep.endpointId, serializeSchema(updated))
      setEndpointSchemas(prev => ({ ...prev, [ep.endpointId]: updated }))
    } catch (err: unknown) {
      toast({ title: 'Failed to delete', description: (err as Error).message, variant: 'destructive' })
    }
  }

  const handleErpAutoRegister = async () => {
    if (!isEdit) return
    setErpKeyLoading(true)
    setGeneratedRawKey('')
    setCopiedKey(false)
    try {
      const { rawKey, keyHash } = await generateApiKey()
      await registerApiKey(connectorKey, {
        rawKey,
        keyHash,
        keyName: `${displayName} — auto`,
      })
      setGeneratedRawKey(rawKey)
      setShowRawKey(true)
      toast({ title: 'API key registered', description: 'Copy the key now — it will not be shown again.' })
      onSaved?.()
    } catch (err: unknown) {
      toast({ title: 'Key registration failed', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setErpKeyLoading(false)
    }
  }

  const handleAddEndpoint = async () => {
    if (!newEpUrl.trim()) return
    setAddEpLoading(true)
    try {
      const created = await addConnectorEndpoint(connectorKey, {
        baseUrl: newEpUrl.trim(),
        environment: newEpEnv,
        connectorType,
        active: true,
      })

      // Seed new endpoint with existing request templates (no responses) from primary
      const primaryEp = endpoints[0]
      if (primaryEp) {
        const primarySchema = endpointSchemas[primaryEp.endpointId]
        if (primarySchema && primarySchema.requests.length > 0) {
          const seedSchema: ConnectorSchema = {
            version: 2,
            requests: primarySchema.requests.map(r => ({
              id: crypto.randomUUID(),
              method: r.method,
              path: r.path,
              requestBody: r.requestBody,
              response: '',
              statusCode: 0,
              durationMs: 0,
              savedAt: new Date().toISOString(),
            })),
          }
          try {
            await updateEndpointSchema(connectorKey, created.endpointId, serializeSchema(seedSchema))
            setEndpointSchemas(prev => ({ ...prev, [created.endpointId]: seedSchema }))
          } catch {
            // seed failure is non-fatal
          }
        } else {
          setEndpointSchemas(prev => ({ ...prev, [created.endpointId]: { version: 2, requests: [] } }))
        }
      }

      const sorted = [...endpoints, created].sort(
        (a, b) => ENV_ORDER.indexOf(a.environment) - ENV_ORDER.indexOf(b.environment)
      )
      setEndpoints(sorted)
      setAddingEndpoint(false)
      setNewEpUrl('')
      toast({ title: 'Endpoint added', description: `${newEpEnv} endpoint registered.` })
      onSaved?.()
    } catch (err: unknown) {
      toast({ title: 'Failed to add endpoint', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setAddEpLoading(false)
    }
  }

  const handleDeleteEndpoint = async (ep: ConnectorResponse) => {
    try {
      await deleteConnectorEndpoint(connectorKey, ep.endpointId)
      setEndpoints(prev => prev.filter(e => e.endpointId !== ep.endpointId))
      setEndpointSchemas(prev => {
        const next = { ...prev }
        delete next[ep.endpointId]
        return next
      })
      toast({ title: 'Endpoint removed', description: `${ep.environment} endpoint deleted.` })
      onSaved?.()
    } catch (err: unknown) {
      toast({ title: 'Failed to remove endpoint', description: (err as Error).message, variant: 'destructive' })
    }
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      if (isEdit) {
        await updateConnector(connectorKey, {
          displayName: displayName.trim(),
          baseUrl: baseUrl.trim(),
          environment,
          active,
          connectorType,
          authScheme,
          credentialRef: authScheme === 'NONE' ? undefined : (credentialRef || undefined),
        })
        toast({ title: 'Connector updated', description: `${displayName} saved.` })
      } else {
        const req: ConnectorRequest = {
          // tenantCode omitted — backend resolves from JWT tenant_id claim
          connectorKey: connectorKey.trim(),
          displayName: displayName.trim(),
          baseUrl: baseUrl.trim(),
          environment,
          active: true,
          connectorType,
          authScheme,
          credentialRef: authScheme === 'NONE' ? undefined : credentialRef,
        }
        await createConnector(req)
        toast({ title: 'Connector created', description: `${displayName} registered successfully.` })
      }
      onSaved?.()
    } catch (err: unknown) {
      toast({
        title: isEdit ? 'Failed to update connector' : 'Failed to create connector',
        description: (err as Error).message,
        variant: 'destructive',
      })
    } finally {
      setSaving(false)
    }
  }

  // ─── Render ──────────────────────────────────────────────────
  return (
    <div className="space-y-4">
      <Tabs defaultValue={defaultTab ?? 'general'}>
        <TabsList className="w-full">
          <TabsTrigger value="general" className="flex-1">General</TabsTrigger>
          <TabsTrigger value="auth" className="flex-1">Authentication</TabsTrigger>
          {isEdit && (
            <TabsTrigger value="contract" className="flex-1">
              Sample Requests
              {allSampleCards.length > 0 && (
                <span className="ml-1.5 inline-flex items-center justify-center h-4 min-w-4 px-1 rounded-full bg-primary/15 text-primary text-[10px] font-semibold">
                  {allSampleCards.length}
                </span>
              )}
            </TabsTrigger>
          )}
          {!isEdit && <TabsTrigger value="contract" className="flex-1">Contract</TabsTrigger>}
          {isEdit && <TabsTrigger value="test" className="flex-1">Test Call</TabsTrigger>}
        </TabsList>

        {/* ── General tab ── */}
        <TabsContent value="general" className="space-y-4 pt-4">
          <div className="space-y-2">
            <Label htmlFor="displayName">Display Name</Label>
            <Input
              id="displayName"
              placeholder="e.g. Procurement Service"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="connectorKey">Connector Key</Label>
            <Input
              id="connectorKey"
              placeholder="e.g. procurement"
              value={connectorKey}
              onChange={(e) => !isEdit && setConnectorKey(e.target.value.toLowerCase())}
              readOnly={isEdit}
              className={isEdit ? 'bg-muted text-muted-foreground' : ''}
            />
            {!isEdit && (
              <p className="text-xs text-muted-foreground">Lowercase, hyphens allowed. Cannot be changed after creation.</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="connectorType">Connector Type</Label>
            <Select value={connectorType} onValueChange={(v) => setConnectorType(v as ConnectorType)}>
              <SelectTrigger id="connectorType"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="API">API — HTTP REST</SelectItem>
                <SelectItem value="WEBHOOK">Webhook — Outbound event</SelectItem>
                <SelectItem value="MCP">MCP — AI tool (coming soon)</SelectItem>
                <SelectItem value="OTHER">Other</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {isEdit ? (
            /* Endpoint table */
            <div className="space-y-2">
              <Label>Endpoints</Label>
              <div className="overflow-hidden rounded-lg border bg-card">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-muted/40">
                      <th className="px-3 py-2 text-left text-xs font-medium text-muted-foreground">Environment</th>
                      <th className="px-3 py-2 text-left text-xs font-medium text-muted-foreground">Base URL</th>
                      <th className="px-3 py-2 text-left text-xs font-medium text-muted-foreground">Status</th>
                      <th className="w-8" />
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {endpoints.map((ep) => (
                      <tr key={ep.endpointId} className="group">
                        <td className="px-3 py-2.5">
                          <Badge variant={envVariant(ep.environment)}>{ep.environment}</Badge>
                        </td>
                        <td className="px-3 py-2.5 max-w-[200px]">
                          <span className="font-mono text-xs text-muted-foreground truncate block" title={ep.baseUrl}>
                            {ep.baseUrl}
                          </span>
                        </td>
                        <td className="px-3 py-2.5">
                          <Badge variant={ep.active ? 'success' : 'secondary'}>
                            {ep.active ? 'Active' : 'Off'}
                          </Badge>
                        </td>
                        <td className="px-3 py-2.5">
                          {endpoints.length > 1 && (
                            <button
                              type="button"
                              onClick={() => handleDeleteEndpoint(ep)}
                              className="opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive transition-all"
                              title="Remove endpoint"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {availableEnvs.length > 0 && !addingEndpoint && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="gap-1.5"
                  onClick={() => { setAddingEndpoint(true); setNewEpEnv(availableEnvs[0]) }}
                >
                  <Plus className="h-3.5 w-3.5" />
                  Add Environment
                </Button>
              )}

              {addingEndpoint && (
                <div className="rounded-lg border bg-muted/20 p-3 space-y-3">
                  <p className="text-xs font-medium text-muted-foreground">New environment endpoint</p>
                  <div className="flex gap-2">
                    <div className="space-y-1 shrink-0">
                      <Label className="text-xs">Environment</Label>
                      <Select value={newEpEnv} onValueChange={setNewEpEnv}>
                        <SelectTrigger className="w-32 h-8 text-xs"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {availableEnvs.map(e => (
                            <SelectItem key={e} value={e}>{e}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="flex-1 space-y-1">
                      <Label className="text-xs">Base URL</Label>
                      <Input
                        className="h-8 text-xs"
                        placeholder="https://staging.api.example.com"
                        value={newEpUrl}
                        onChange={(e) => setNewEpUrl(e.target.value)}
                      />
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Button type="button" size="sm" onClick={handleAddEndpoint} disabled={addEpLoading || !newEpUrl.trim()}>
                      {addEpLoading ? <RefreshCw className="h-3 w-3 animate-spin mr-1" /> : null}
                      Save Endpoint
                    </Button>
                    <Button type="button" variant="ghost" size="sm" onClick={() => { setAddingEndpoint(false); setNewEpUrl('') }}>
                      Cancel
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <>
              <div className="space-y-2">
                <Label htmlFor="baseUrl">Base URL</Label>
                <Input
                  id="baseUrl"
                  placeholder="https://api.example.com"
                  value={baseUrl}
                  onChange={(e) => setBaseUrl(e.target.value)}
                />
                {/localhost|127\.0\.0\.1/.test(baseUrl) && (
                  <p className="text-xs text-amber-600 dark:text-amber-400">
                    Loopback addresses are blocked. Use the service hostname instead (e.g.{' '}
                    <code className="font-mono">http://werkflow-erp:8084/api/v1</code>).
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="environment">Environment</Label>
                <Select value={environment} onValueChange={setEnvironment}>
                  <SelectTrigger id="environment"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="development">Development</SelectItem>
                    <SelectItem value="staging">Staging</SelectItem>
                    <SelectItem value="production">Production</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </>
          )}
        </TabsContent>

        {/* ── Auth tab ── */}
        <TabsContent value="auth" className="space-y-4 pt-4">
          <div className="space-y-2">
            <Label htmlFor="authScheme">Auth Scheme</Label>
            <Select value={authScheme} onValueChange={handleAuthSchemeChange}>
              <SelectTrigger id="authScheme"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="BEARER">Bearer Token</SelectItem>
                <SelectItem value="API_KEY">API Key</SelectItem>
                <SelectItem value="BASIC">Basic Auth</SelectItem>
                <SelectItem value="NONE">None</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {authScheme !== 'NONE' && (
            <div className="space-y-2">
              <Label htmlFor={showCredPicker ? 'credentialRef' : undefined}>Credential</Label>
              {credentialsLoading ? (
                <p className="text-sm text-muted-foreground">Loading credentials…</p>
              ) : showCredEmptyState ? (
                <p className="text-sm text-muted-foreground">
                  No {getCredentialType(credentialType)?.displayName ?? credentialType} credentials found.{' '}
                  <Link href="/admin/tenant/credentials" className="underline underline-offset-2">
                    Create one first
                  </Link>
                </p>
              ) : (
                <Select value={credentialRef} onValueChange={setCredentialRef}>
                  <SelectTrigger id="credentialRef" className="font-mono text-sm">
                    <SelectValue placeholder="Select credential…" />
                  </SelectTrigger>
                  <SelectContent>
                    {matchingCredentials.map((c) => (
                      <SelectItem key={c.id} value={c.label} className="font-mono text-xs">
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {credentialStale && (
                <p className="text-xs text-amber-600 dark:text-amber-400">
                  The bound credential <code className="font-mono">{credentialRef}</code> no longer exists for this
                  auth scheme. Select another, or it will be left unchanged on save.
                </p>
              )}
              {showCredPicker && (
                <p className="text-xs text-muted-foreground">
                  Secret material is stored in OpenBao. Manage credentials on the{' '}
                  <Link href="/admin/tenant/credentials" className="underline underline-offset-2">Credentials page</Link>.
                </p>
              )}
            </div>
          )}

          {isEdit && authScheme === 'API_KEY' && (
            <div className="rounded-lg border border-dashed p-3 space-y-3">
              <div>
                <p className="text-sm font-medium">ERP API Key Auto-Registration</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Generates a key, registers its hash in werkflow-erp, and stores the raw key in OpenBao.
                </p>
              </div>
              {!generatedRawKey ? (
                <Button type="button" variant="outline" size="sm" onClick={handleErpAutoRegister} disabled={erpKeyLoading}>
                  {erpKeyLoading ? <RefreshCw className="h-3 w-3 mr-1 animate-spin" /> : null}
                  Generate &amp; Register ERP Key
                </Button>
              ) : (
                <div className="space-y-2">
                  <p className="text-xs font-medium text-amber-600 dark:text-amber-400">
                    Copy this key now — it will not be shown again.
                  </p>
                  <div className="flex items-center gap-2">
                    <div className="relative flex-1">
                      <Input
                        value={generatedRawKey}
                        readOnly
                        type={showRawKey ? 'text' : 'password'}
                        className="font-mono text-xs bg-muted pr-10"
                      />
                      <button
                        type="button"
                        onClick={() => setShowRawKey(p => !p)}
                        className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                        tabIndex={-1}
                      >
                        {showRawKey ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                      </button>
                    </div>
                    <Button type="button" variant="outline" size="icon" onClick={() => { navigator.clipboard.writeText(generatedRawKey); setCopiedKey(true) }} className="shrink-0">
                      {copiedKey ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
                    </Button>
                  </div>
                  {copiedKey && (
                    <div className="flex items-center justify-between">
                      <p className="text-xs text-green-600">Copied to clipboard.</p>
                      <Button type="button" variant="ghost" size="sm" onClick={() => { setGeneratedRawKey(''); setCopiedKey(false) }} className="h-6 text-xs">
                        Dismiss
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </TabsContent>

        {/* ── Sample Requests / Contract tab ── */}
        <TabsContent value="contract" className="space-y-3 pt-4">
          {isEdit ? (
            allSampleCards.length === 0 ? (
              <div className="rounded-lg border border-dashed p-6 text-center space-y-1">
                <p className="text-sm text-muted-foreground">No sample requests saved yet.</p>
                <p className="text-xs text-muted-foreground">Run a test call and save the response to capture a contract.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {allSampleCards.map(({ ep, req }) => {
                  const cardKey = `${ep.endpointId}-${req.id}`
                  const expanded = expandedCards.has(cardKey)
                  const isPending = !req.response
                  return (
                    <div key={cardKey} className={`rounded-lg border overflow-hidden ${isPending ? 'opacity-60' : ''}`}>
                      <div className="flex items-center gap-2 px-3 py-2.5 bg-muted/30">
                        <Badge variant={envVariant(ep.environment)} className="shrink-0">{ep.environment}</Badge>
                        <Badge variant="outline" className="font-mono shrink-0">{req.method}</Badge>
                        <code className="flex-1 text-xs truncate" title={req.path}>{req.path}</code>
                        {!isPending && (
                          <>
                            <Badge variant={statusColor(req.statusCode)} className="shrink-0">{req.statusCode}</Badge>
                            <span className="text-xs text-muted-foreground shrink-0">{req.durationMs}ms</span>
                          </>
                        )}
                        {isPending && (
                          <span className="text-xs text-muted-foreground shrink-0 italic">no response</span>
                        )}
                        <div className="flex items-center gap-1 shrink-0">
                          {!isPending && (
                            <button
                              type="button"
                              onClick={() => setExpandedCards(prev => {
                                const s = new Set(prev)
                                s.has(cardKey) ? s.delete(cardKey) : s.add(cardKey)
                                return s
                              })}
                              className="text-muted-foreground hover:text-foreground"
                            >
                              {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => handleDeleteSampleRequest(ep, req.id)}
                            className="text-muted-foreground hover:text-destructive"
                            title="Delete"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </div>
                      {expanded && (
                        <div className="border-t">
                          {req.requestBody && (
                            <div className="px-3 py-2 border-b bg-background">
                              <p className="text-xs text-muted-foreground font-medium mb-1">Request body</p>
                              <pre className="font-mono text-xs whitespace-pre-wrap break-all text-foreground">{req.requestBody}</pre>
                            </div>
                          )}
                          <div className="px-3 py-2 bg-background">
                            <p className="text-xs text-muted-foreground font-medium mb-1">Response</p>
                            <pre className="font-mono text-xs whitespace-pre-wrap break-all text-foreground max-h-48 overflow-y-auto">{req.response}</pre>
                          </div>
                          <div className="px-3 py-1.5 bg-muted/20 border-t">
                            <p className="text-[10px] text-muted-foreground">
                              Saved {new Date(req.savedAt).toLocaleString()}
                            </p>
                          </div>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )
          ) : (
            /* Create mode — manual schema paste */
            <div className="space-y-2">
              <Label htmlFor="importJson">Sample JSON Response</Label>
              <Textarea
                id="importJson"
                placeholder='{"id": "123", "status": "active"}'
                rows={8}
                className="font-mono text-xs"
              />
              <p className="text-xs text-muted-foreground">
                Paste a sample response to capture the contract schema.
              </p>
            </div>
          )}
        </TabsContent>

        {/* ── Test Call tab (edit only) ── */}
        {isEdit && (
          <TabsContent value="test" className="space-y-4 pt-4">
            {/* Endpoint selector */}
            {endpoints.length > 1 && (
              <div className="space-y-2">
                <Label>Test against</Label>
                <div className="flex flex-wrap gap-1.5">
                  {endpoints.map(ep => (
                    <button
                      key={ep.endpointId}
                      type="button"
                      onClick={() => setTestEnvId(ep.endpointId)}
                      className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-medium transition-colors
                        ${testEnvId === ep.endpointId
                          ? 'border-primary bg-primary/10 text-primary'
                          : 'border-border bg-card text-muted-foreground hover:border-primary/50 hover:text-foreground'
                        }`}
                    >
                      <span className={`h-1.5 w-1.5 rounded-full ${ep.environment === 'production' ? 'bg-green-500' : ep.environment === 'staging' ? 'bg-yellow-500' : 'bg-blue-400'}`} />
                      {ep.environment}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Method + path + send */}
            <div className="flex gap-2 items-end">
              <div className="space-y-2">
                <Label htmlFor="testMethod">Method</Label>
                <Select value={testMethod} onValueChange={(v) => setTestMethod(v as typeof testMethod)}>
                  <SelectTrigger id="testMethod" className="w-28"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GET">GET</SelectItem>
                    <SelectItem value="POST">POST</SelectItem>
                    <SelectItem value="PUT">PUT</SelectItem>
                    <SelectItem value="PATCH">PATCH</SelectItem>
                    <SelectItem value="DELETE">DELETE</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="flex-1 space-y-2">
                <Label htmlFor="testPath">Path</Label>
                <Input
                  id="testPath"
                  placeholder="/departments"
                  value={testPath}
                  onChange={(e) => setTestPath(e.target.value)}
                />
              </div>
              <Button type="button" onClick={handleTestCall} disabled={testLoading || !testPath.trim()}>
                {testLoading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                <span className="ml-2">Send</span>
              </Button>
            </div>

            {testMethod !== 'GET' && (
              <div className="space-y-2">
                <Label htmlFor="testBody">Request Body (optional)</Label>
                <Textarea
                  id="testBody"
                  placeholder='{"key": "value"}'
                  value={testBody}
                  onChange={(e) => setTestBody(e.target.value)}
                  rows={4}
                  className="font-mono text-xs"
                />
              </div>
            )}

            {/* Overwrite confirmation banner */}
            {pendingContractSave && overwriteTarget && (
              <div className="rounded-lg border border-amber-300 bg-amber-50 dark:bg-amber-950/30 dark:border-amber-800 p-3 space-y-2">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
                  <div className="text-sm">
                    <p className="font-medium text-amber-800 dark:text-amber-300">Contract already saved</p>
                    <p className="text-xs text-amber-700 dark:text-amber-400 mt-0.5">
                      A response for <code className="font-mono">{overwriteTarget.method} {overwriteTarget.path}</code> on{' '}
                      <strong>{testEndpoint?.environment}</strong> already exists. Overwrite it?
                    </p>
                  </div>
                </div>
                <div className="flex gap-2 pl-6">
                  <Button
                    type="button"
                    size="sm"
                    variant="destructive"
                    className="h-7 text-xs"
                    onClick={() => doSaveContract(testEndpoint!, pendingContractSave)}
                    disabled={savingContract}
                  >
                    {savingContract ? <RefreshCw className="h-3 w-3 animate-spin mr-1" /> : null}
                    Overwrite
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="h-7 text-xs"
                    onClick={() => { setPendingContractSave(null); setOverwriteTarget(null) }}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            )}

            {/* Test result */}
            {testResult && !pendingContractSave && (
              <div className="rounded-lg border overflow-hidden">
                <div className="flex items-center justify-between gap-2 px-3 py-2 bg-muted/30 border-b">
                  <div className="flex items-center gap-2 text-sm">
                    <Badge variant={statusColor(testResult.statusCode)}>{testResult.statusCode}</Badge>
                    <span className="text-muted-foreground text-xs">{testResult.durationMs}ms</span>
                    {testResult.truncated && <Badge variant="outline" className="text-xs">Truncated</Badge>}
                    {testEndpoint && <Badge variant={envVariant(testEndpoint.environment)} className="text-xs">{testEndpoint.environment}</Badge>}
                  </div>
                  {testResult.statusCode >= 200 && testResult.statusCode < 300 && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      onClick={handleSaveContract}
                      disabled={savingContract}
                    >
                      {savingContract ? <RefreshCw className="h-3 w-3 animate-spin" /> : <Save className="h-3 w-3" />}
                      Save as Contract
                    </Button>
                  )}
                </div>
                <Textarea value={testResult.body} readOnly rows={8} className="font-mono text-xs border-0 rounded-none resize-none" />
              </div>
            )}
          </TabsContent>
        )}
      </Tabs>

      <Button onClick={handleSave} disabled={!canSave || saving} className="w-full">
        {saving ? (
          <><RefreshCw className="h-4 w-4 mr-2 animate-spin" />Saving...</>
        ) : isEdit ? (
          'Save Changes'
        ) : (
          'Save Connector'
        )}
      </Button>
    </div>
  )
}
