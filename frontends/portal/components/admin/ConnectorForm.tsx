'use client'

import { useState } from 'react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/hooks/use-toast'
import { RefreshCw, Send } from 'lucide-react'
import {
  createConnector,
  testConnector,
  type ConnectorRequest,
  type ConnectorTestResponse,
} from '@/lib/api/connectors'

interface ConnectorFormProps {
  tenantCode: string
  onCreated?: () => void
}

export function ConnectorForm({ tenantCode, onCreated }: ConnectorFormProps) {
  const { toast } = useToast()

  // General
  const [connectorKey, setConnectorKey] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [environment, setEnvironment] = useState('production')

  // Authentication
  const [authScheme, setAuthScheme] = useState('BEARER')
  const [secretRef, setSecretRef] = useState('')
  const [headerName, setHeaderName] = useState('')

  // Contract
  const [contractMode, setContractMode] = useState<'import' | 'test'>('import')
  const [importJson, setImportJson] = useState('')
  const [testPath, setTestPath] = useState('')
  const [testMethod, setTestMethod] = useState<'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'>('GET')
  const [testResult, setTestResult] = useState<ConnectorTestResponse | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  const [saving, setSaving] = useState(false)

  const capturedSchema = testResult?.body ?? importJson

  const canSave = connectorKey.trim() !== '' && displayName.trim() !== '' && baseUrl.trim() !== '' && secretRef.trim() !== ''

  const handleTestCall = async () => {
    if (!connectorKey.trim()) {
      toast({ title: 'Connector key required', description: 'Set a connector key on the General tab first.', variant: 'destructive' })
      return
    }
    setTestLoading(true)
    setTestResult(null)
    try {
      const result = await testConnector(tenantCode, connectorKey, { path: testPath, method: testMethod })
      setTestResult(result)
    } catch (err: any) {
      toast({ title: 'Test failed', description: err.message, variant: 'destructive' })
    } finally {
      setTestLoading(false)
    }
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const req: ConnectorRequest = {
        tenantCode,
        connectorKey: connectorKey.trim(),
        displayName: displayName.trim(),
        baseUrl: baseUrl.trim(),
        environment,
        active: true,
        authScheme,
        secretRef: secretRef.trim(),
        headerName: headerName.trim() || undefined,
        sampleSchema: capturedSchema.trim() || undefined,
      }
      await createConnector(req)
      toast({ title: 'Connector created', description: `${displayName} registered successfully.` })
      onCreated?.()
    } catch (err: any) {
      toast({ title: 'Failed to create connector', description: err.message, variant: 'destructive' })
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
    <div className="space-y-4">
      <Tabs defaultValue="general">
        <TabsList className="w-full">
          <TabsTrigger value="general" className="flex-1">General</TabsTrigger>
          <TabsTrigger value="auth" className="flex-1">Authentication</TabsTrigger>
          <TabsTrigger value="contract" className="flex-1">Contract</TabsTrigger>
        </TabsList>

        {/* General tab */}
        <TabsContent value="general" className="space-y-4 pt-4">
          <div className="space-y-2">
            <Label htmlFor="displayName">Display Name</Label>
            <Input
              id="displayName"
              placeholder="e.g. Stripe Payments"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="connectorKey">Connector Key</Label>
            <Input
              id="connectorKey"
              placeholder="e.g. stripe-payments"
              value={connectorKey}
              onChange={(e) => setConnectorKey(e.target.value.toLowerCase())}
            />
            <p className="text-xs text-muted-foreground">Lowercase, hyphens allowed. Used as process variable prefix.</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="baseUrl">Base URL</Label>
            <Input
              id="baseUrl"
              placeholder="https://api.example.com"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              className="w-full"
            />
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
          <div className="space-y-2">
            <Label htmlFor="secretRef">Secret Ref</Label>
            <Input
              id="secretRef"
              placeholder="e.g. stripe.api.key"
              value={secretRef}
              onChange={(e) => setSecretRef(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">Must exist in werkflow.secrets.allowed-keys</p>
          </div>
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
          <div className="flex gap-2">
            <Button
              type="button"
              variant={contractMode === 'import' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setContractMode('import')}
            >
              Paste JSON / OpenAPI URL
            </Button>
            <Button
              type="button"
              variant={contractMode === 'test' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setContractMode('test')}
            >
              Test Call
            </Button>
          </div>

          {contractMode === 'import' && (
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
            </div>
          )}

          {contractMode === 'test' && (
            <div className="space-y-4">
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
                    placeholder="/v1/health"
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

              {testResult && (
                <div className="space-y-2 border rounded-md p-3 bg-muted/30">
                  <div className="flex items-center gap-2 text-sm">
                    <Badge variant={statusColor(testResult.statusCode) as any}>{testResult.statusCode}</Badge>
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
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Button onClick={handleSave} disabled={!canSave || saving} className="w-full">
        {saving ? (
          <>
            <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
            Saving...
          </>
        ) : (
          'Save Connector'
        )}
      </Button>
    </div>
  )
}
