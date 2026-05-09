'use client'

import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { RefreshCw, Check } from 'lucide-react'
import { importConnectorFromOpenApi, type ConnectorDefinition } from '@/lib/api/dtds'
import { DtdsOperationDetail } from './DtdsOperationDetail'

type ImportMode = 'url' | 'paste'

interface ImportOpenApiModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onImported: (connector: ConnectorDefinition) => void
}

export function ImportOpenApiModal({
  open,
  onOpenChange,
  onImported,
}: ImportOpenApiModalProps) {
  const [mode, setMode] = useState<ImportMode>('url')
  const [urlInput, setUrlInput] = useState('')
  const [yamlInput, setYamlInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [imported, setImported] = useState<ConnectorDefinition | null>(null)
  const [confirmed, setConfirmed] = useState(false)

  const source = mode === 'url' ? urlInput.trim() : yamlInput.trim()
  const canImport = source.length > 0 && !isLoading

  const handleImport = async () => {
    setIsLoading(true)
    setError(null)
    setImported(null)
    try {
      const result = await importConnectorFromOpenApi(source)
      setImported(result)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Import failed')
    } finally {
      setIsLoading(false)
    }
  }

  const handleConfirm = () => {
    if (!imported) return
    setConfirmed(true)
    onImported(imported)
    onOpenChange(false)
  }

  const handleClose = (nextOpen: boolean) => {
    if (!nextOpen) {
      setUrlInput('')
      setYamlInput('')
      setImported(null)
      setError(null)
      setConfirmed(false)
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Import from OpenAPI</DialogTitle>
          <DialogDescription>
            Provide a URL or paste an OpenAPI 3.x spec (YAML or JSON) to generate a connector.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Mode toggle */}
          <div className="flex gap-2">
            <Button
              variant={mode === 'url' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('url')}
              className="flex-1"
            >
              From URL
            </Button>
            <Button
              variant={mode === 'paste' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('paste')}
              className="flex-1"
            >
              Paste YAML / JSON
            </Button>
          </div>

          {/* Input */}
          {mode === 'url' ? (
            <div className="space-y-1.5">
              <Label htmlFor="openapi-url">OpenAPI URL</Label>
              <Input
                id="openapi-url"
                placeholder="https://api.example.com/openapi.yaml"
                value={urlInput}
                onChange={e => setUrlInput(e.target.value)}
              />
            </div>
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="openapi-paste">Paste spec</Label>
              <Textarea
                id="openapi-paste"
                placeholder={'openapi: "3.0.0"\ninfo:\n  title: My API\n  version: "1.0"'}
                rows={8}
                className="font-mono text-xs"
                value={yamlInput}
                onChange={e => setYamlInput(e.target.value)}
              />
            </div>
          )}

          {/* Error */}
          {error && (
            <p className="text-sm text-destructive rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2">
              {error}
            </p>
          )}

          {/* Import preview */}
          {imported && (
            <div className="space-y-3 rounded-lg border bg-muted/20 p-3">
              <div className="flex items-center gap-2">
                <Check className="h-4 w-4 text-green-600" />
                <span className="text-sm font-medium">Import preview</span>
                <Badge variant="secondary">{imported.version}</Badge>
              </div>

              <div>
                <p className="text-sm font-semibold">{imported.displayName}</p>
                <code className="text-xs text-muted-foreground">{imported.key}</code>
                {imported.description && (
                  <p className="text-xs text-muted-foreground mt-0.5">{imported.description}</p>
                )}
              </div>

              {imported.operations && imported.operations.length > 0 && (
                <div className="space-y-1.5">
                  <p className="text-xs font-medium text-muted-foreground">
                    {imported.operations.length} operation{imported.operations.length !== 1 ? 's' : ''} detected
                  </p>
                  {imported.operations.map(op => (
                    <DtdsOperationDetail
                      key={op.id}
                      connectorKey={imported.key}
                      operation={op}
                    />
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-2 justify-end pt-1">
            <Button variant="outline" onClick={() => handleClose(false)}>
              Cancel
            </Button>
            {!imported ? (
              <Button onClick={handleImport} disabled={!canImport}>
                {isLoading && <RefreshCw className="h-4 w-4 mr-2 animate-spin" />}
                Parse &amp; Preview
              </Button>
            ) : (
              <Button onClick={handleConfirm} disabled={confirmed}>
                {confirmed ? (
                  <><Check className="h-4 w-4 mr-2" />Saved</>
                ) : (
                  'Confirm &amp; Save'
                )}
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
