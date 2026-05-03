'use client'

import { useState } from 'react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { useToast } from '@/hooks/use-toast'
import { RefreshCw, Send, Copy, Check, Eye, EyeOff } from 'lucide-react'
import {
  createConnector,
  updateConnector,
  testConnector,
  registerApiKey,
  type ConnectorType,
  type ConnectorRequest,
  type ConnectorResponse,
  type ConnectorTestResponse,
} from '@/lib/api/connectors'

// Generates a cryptographically random hex key and its SHA-256 hash
async function generateApiKey(): Promise<{ rawKey: string; keyHash: string }> {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  const rawKey = Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('')
  const hashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rawKey))
  const keyHash = Array.from(new Uint8Array(hashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('')
  return { rawKey, keyHash }
}

interface ConnectorFormProps {
  tenantCode: string
  existingConnector?: ConnectorResponse
  defaultTab?: 'general' | 'auth' | 'contract' | 'test'
  onSaved?: () => void
}

export function ConnectorForm({ tenantCode, existingConnector, defaultTab, onSaved }: ConnectorFormProps) {
  const { toast } = useToast()
  const isEdit = !!existingConnector

  const [connectorKey, setConnectorKey] = useState(existingConnector?.connectorKey ?? '')
  const [displayName, setDisplayName] = useState(existingConnector?.displayName ?? '')
  const [baseUrl, setBaseUrl] = useState(existingConnector?.baseUrl ?? '')
  const [environment, setEnvironment] = useState(existingConnector?.environment ?? 'production')
  const [active, setActive] = useState(existingConnector?.active ?? true)
  const [connectorType, setConnectorType] = useState<ConnectorType>(existingConnector?.connectorType ?? 'API')

  const [authScheme, setAuthScheme] = useState(existingConnector?.authScheme ?? 'BEARER')
  const [secretValue, setSecretValue] = useState('')
  const [showSecret, setShowSecret] = useState(false)
  const [headerName, setHeaderName] = useState(existingConnector?.headerName ?? '')

  const [importJson, setImportJson] = useState(existingConnector?.sampleSchema ?? '')

  // Test call (only for edit mode — connector must exist in DB)
  const [testPath, setTestPath] = useState('')
  const [testMethod, setTestMethod] = useState<'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'>('GET')
  const [testBody, setTestBody] = useState('')
  const [testResult, setTestResult] = useState<ConnectorTestResponse | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  // ERP API key copy-once modal
  const [copyOnceOpen, setCopyOnceOpen] = useState(false)
  const [generatedRawKey, setGeneratedRawKey] = useState('')
  const [copied, setCopied] = useState(false)
  const [erpKeyLoading, setErpKeyLoading] = useState(false)

  const [saving, setSaving] = useState(false)

  const canSave = isEdit
    ? displayName.trim() !== '' && baseUrl.trim() !== ''
    : connectorKey.trim() !== '' && displayName.trim() !== '' && baseUrl.trim() !== '' &&
      (authScheme === 'NONE' || secretValue.trim() !== '')

  const handleTestCall = async () => {
    setTestLoading(true)
    setTestResult(null)
    try {
      const result = await testConnector(tenantCode, connectorKey, {
        path: testPath,
        method: testMethod,
        requestBody: testBody.trim() || undefined,
      })
      setTestResult(result)
    } catch (err: unknown) {
      toast({ title: 'Test failed', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setTestLoading(false)
    }
  }

  const handleErpAutoRegister = async () => {
    if (!isEdit) return
    setErpKeyLoading(true)
    try {
      const { rawKey, keyHash } = await generateApiKey()
      await registerApiKey(tenantCode, connectorKey, {
        rawKey,
        keyHash,
        keyName: `${displayName} — auto`,
      })
      setGeneratedRawKey(rawKey)
      setCopied(false)
      setCopyOnceOpen(true)
      toast({ title: 'API key registered', description: 'Copy the key now — it will not be shown again.' })
      onSaved?.()
    } catch (err: unknown) {
      toast({ title: 'Key registration failed', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setErpKeyLoading(false)
    }
  }

  const handleCopy = () => {
    navigator.clipboard.writeText(generatedRawKey)
    setCopied(true)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      if (isEdit) {
        await updateConnector(tenantCode, connectorKey, {
          displayName: displayName.trim(),
          baseUrl: baseUrl.trim(),
          environment,
          active,
          connectorType,
          authScheme,
          secretValue: secretValue.trim() || undefined,
          headerName: headerName.trim() || undefined,
        })
        toast({ title: 'Connector updated', description: `${displayName} saved.` })
      } else {
        const req: ConnectorRequest = {
          tenantCode,
          connectorKey: connectorKey.trim(),
          displayName: displayName.trim(),
          baseUrl: baseUrl.trim(),
          environment,
          active: true,
          connectorType,
          authScheme,
          secretValue: secretValue.trim(),
          headerName: headerName.trim() || undefined,
          sampleSchema: importJson.trim() || undefined,
        }
        await createConnector(req)
        toast({ title: 'Connector created', description: `${displayName} registered successfully.` })
      }
      onSaved?.()
    } catch (err: unknown) {
      toast({ title: isEdit ? 'Failed to update connector' : 'Failed to create connector', description: (err as Error).message, variant: 'destructive' })
    } finally {
      setSaving(false)
    }
  }

  const statusColor = (code: number) => {
    if (code >= 200 && code < 300) return 'success'
    if (code >= 400 && code < 500) return 'warning'
    return 'destructive'
  }

  return (
    <>
      <div className="space-y-4">
        <Tabs defaultValue={defaultTab ?? 'general'}>
          <TabsList className="w-full">
            <TabsTrigger value="general" className="flex-1">General</TabsTrigger>
            <TabsTrigger value="auth" className="flex-1">Authentication</TabsTrigger>
            <TabsTrigger value="contract" className="flex-1">Contract</TabsTrigger>
            {isEdit && <TabsTrigger value="test" className="flex-1">Test Call</TabsTrigger>}
          </TabsList>

          {/* General tab */}
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
                <SelectTrigger id="connectorType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="API">API — HTTP REST</SelectItem>
                  <SelectItem value="WEBHOOK">Webhook — Outbound event</SelectItem>
                  <SelectItem value="MCP">MCP — AI tool (coming soon)</SelectItem>
                  <SelectItem value="OTHER">Other</SelectItem>
                </SelectContent>
              </Select>
            </div>
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
                  Loopback addresses are blocked. In Docker, use the service hostname instead (e.g.{' '}
                  <code className="font-mono">http://werkflow-erp:8084/api/v1</code>).
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="environment">Environment</Label>
              <Select value={environment} onValueChange={setEnvironment}>
                <SelectTrigger id="environment">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="development">Development</SelectItem>
                  <SelectItem value="staging">Staging</SelectItem>
                  <SelectItem value="production">Production</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </TabsContent>

          {/* Authentication tab */}
          <TabsContent value="auth" className="space-y-4 pt-4">
            <div className="space-y-2">
              <Label htmlFor="authScheme">Auth Scheme</Label>
              <Select value={authScheme} onValueChange={setAuthScheme}>
                <SelectTrigger id="authScheme">
                  <SelectValue />
                </SelectTrigger>
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
                <Label htmlFor="secretValue">
                  Secret
                  {isEdit && existingConnector?.hasSecret && (
                    <span className="text-muted-foreground font-normal ml-1">(stored — leave blank to keep)</span>
                  )}
                </Label>
                <div className="relative">
                  <Input
                    id="secretValue"
                    type={showSecret ? 'text' : 'password'}
                    placeholder={isEdit && existingConnector?.hasSecret ? '••••••••' : 'Paste secret value'}
                    value={secretValue}
                    onChange={(e) => setSecretValue(e.target.value)}
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowSecret(p => !p)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    tabIndex={-1}
                  >
                    {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                <p className="text-xs text-muted-foreground">
                  Stored encrypted (AES-256-GCM). Never shown again after saving.
                </p>
              </div>
            )}

            {isEdit && authScheme === 'API_KEY' && (
              <div className="rounded-md border border-dashed p-3 space-y-2">
                <p className="text-sm font-medium">ERP API Key Auto-Registration</p>
                <p className="text-xs text-muted-foreground">
                  Generates a key, registers its hash in werkflow-erp, and stores the encrypted raw key.
                  The raw key is shown once — copy it immediately.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleErpAutoRegister}
                  disabled={erpKeyLoading}
                >
                  {erpKeyLoading ? <RefreshCw className="h-3 w-3 mr-1 animate-spin" /> : null}
                  Generate & Register ERP Key
                </Button>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="headerName">Header Name <span className="text-muted-foreground font-normal">(optional)</span></Label>
              <Input
                id="headerName"
                placeholder="e.g. X-Api-Key"
                value={headerName}
                onChange={(e) => setHeaderName(e.target.value)}
              />
            </div>
          </TabsContent>

          {/* Contract tab */}
          <TabsContent value="contract" className="space-y-4 pt-4">
            <div className="space-y-2">
              <Label htmlFor="importJson">Sample JSON Response</Label>
              <Textarea
                id="importJson"
                placeholder='{"id": "123", "status": "active"}'
                value={importJson}
                onChange={(e) => setImportJson(e.target.value)}
                rows={8}
                className="font-mono text-xs"
              />
              <p className="text-xs text-muted-foreground">
                Paste a sample response to capture the contract schema.
                {!isEdit && ' After saving, use Test Call on the connector card to verify the endpoint.'}
              </p>
            </div>
          </TabsContent>

          {/* Test Call tab — only in edit mode */}
          {isEdit && (
            <TabsContent value="test" className="space-y-4 pt-4">
              <div className="flex gap-2 items-end">
                <div className="space-y-2">
                  <Label htmlFor="testMethod">Method</Label>
                  <Select value={testMethod} onValueChange={(v) => setTestMethod(v as typeof testMethod)}>
                    <SelectTrigger id="testMethod" className="w-28">
                      <SelectValue />
                    </SelectTrigger>
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
                    placeholder="/purchase-orders"
                    value={testPath}
                    onChange={(e) => setTestPath(e.target.value)}
                  />
                </div>
                <Button
                  type="button"
                  onClick={handleTestCall}
                  disabled={testLoading || !testPath.trim()}
                >
                  {testLoading ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
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

              {testResult && (
                <div className="space-y-2 border rounded-md p-3 bg-muted/30">
                  <div className="flex items-center gap-2 text-sm">
                    <Badge variant={statusColor(testResult.statusCode) as 'success' | 'warning' | 'destructive'}>{testResult.statusCode}</Badge>
                    <span className="text-muted-foreground">{testResult.durationMs}ms</span>
                    {testResult.truncated && <Badge variant="outline">Truncated</Badge>}
                  </div>
                  <Textarea
                    value={testResult.body}
                    readOnly
                    rows={8}
                    className="font-mono text-xs"
                  />
                </div>
              )}
            </TabsContent>
          )}
        </Tabs>

        <Button onClick={handleSave} disabled={!canSave || saving} className="w-full">
          {saving ? (
            <>
              <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
              Saving...
            </>
          ) : isEdit ? (
            'Save Changes'
          ) : (
            'Save Connector'
          )}
        </Button>
      </div>

      {/* Copy-once modal — shown after ERP key is registered */}
      <Dialog open={copyOnceOpen} onOpenChange={setCopyOnceOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Copy your API key now</DialogTitle>
            <DialogDescription>
              This is the only time this key will be displayed. It cannot be retrieved later.
            </DialogDescription>
          </DialogHeader>
          <div className="flex items-center gap-2">
            <Input
              value={generatedRawKey}
              readOnly
              className="font-mono text-xs bg-muted"
            />
            <Button variant="outline" size="icon" onClick={handleCopy}>
              {copied ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
            </Button>
          </div>
          {copied && <p className="text-xs text-green-600">Copied to clipboard.</p>}
          <DialogFooter>
            <Button variant="default" onClick={() => setCopyOnceOpen(false)} disabled={!copied}>
              I have copied it
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
