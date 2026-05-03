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
  updateConnector,
  testConnector,
  type ConnectorRequest,
  type ConnectorResponse,
  type ConnectorTestResponse,
} from '@/lib/api/connectors'

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

  const [authScheme, setAuthScheme] = useState(existingConnector?.authScheme ?? 'BEARER')
  const [secretRef, setSecretRef] = useState('')
  const [headerName, setHeaderName] = useState(existingConnector?.headerName ?? '')

  const [importJson, setImportJson] = useState(existingConnector?.sampleSchema ?? '')

  // Test call (only for edit mode — connector must exist in DB)
  const [testPath, setTestPath] = useState('')
  const [testMethod, setTestMethod] = useState<'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'>('GET')
  const [testResult, setTestResult] = useState<ConnectorTestResponse | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  const [saving, setSaving] = useState(false)

  const canSave = isEdit
    ? displayName.trim() !== '' && baseUrl.trim() !== ''
    : connectorKey.trim() !== '' && displayName.trim() !== '' && baseUrl.trim() !== '' && secretRef.trim() !== ''

  const handleTestCall = async () => {
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
      if (isEdit) {
        await updateConnector(tenantCode, connectorKey, {
          displayName: displayName.trim(),
          baseUrl: baseUrl.trim(),
          environment,
          active,
          authScheme,
          secretRef: secretRef.trim() || undefined,
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
          authScheme,
          secretRef: secretRef.trim(),
          headerName: headerName.trim() || undefined,
          sampleSchema: importJson.trim() || undefined,
        }
        await createConnector(req)
        toast({ title: 'Connector created', description: `${displayName} registered successfully.` })
      }
      onSaved?.()
    } catch (err: any) {
      toast({ title: isEdit ? 'Failed to update connector' : 'Failed to create connector', description: err.message, variant: 'destructive' })
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
          <div className="space-y-2">
            <Label htmlFor="secretRef">
              Secret Ref{isEdit && <span className="text-muted-foreground font-normal ml-1">(leave blank to keep existing)</span>}
            </Label>
            <Input
              id="secretRef"
              placeholder="e.g. erp.api.key"
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

        {/* Test Call tab — only in edit mode (connector exists in DB) */}
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
  )
}
