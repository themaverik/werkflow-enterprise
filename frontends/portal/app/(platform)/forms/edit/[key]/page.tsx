'use client'

import { useParams, useRouter } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getFormDefinition, getFormVersions, rollbackForm } from '@/lib/api/flowable'
import { listConnectors, ConnectorResponse } from '@/lib/api/connectors'
import FormJsBuilder from '@/components/forms/FormJsBuilder'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ArrowLeft, History, RotateCcw, ChevronRight, ChevronLeft } from 'lucide-react'
import Link from 'next/link'
import { useState } from 'react'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'

export default function EditFormPage() {
  const { status } = useSession()
  const params = useParams()
  const router = useRouter()
  const queryClient = useQueryClient()
  const formKey = params.key as string
  const [historyOpen, setHistoryOpen] = useState(false)
  const [showDataSources, setShowDataSources] = useState(false)
  const [pendingConfirm, setPendingConfirm] = useState<{ title: string; description: string; onConfirm: () => void } | null>(null)
  const TENANT_CODE = process.env.NEXT_PUBLIC_TENANT_CODE ?? 'default'

  const { data: formDef, isLoading, error } = useQuery({
    queryKey: ['form', formKey],
    queryFn: () => getFormDefinition(formKey),
    enabled: status === 'authenticated' && !!formKey
  })

  const { data: versions } = useQuery({
    queryKey: ['formVersions', formKey],
    queryFn: () => getFormVersions(formKey),
    enabled: status === 'authenticated' && !!formKey && historyOpen
  })

  const { data: connectors = [] } = useQuery<ConnectorResponse[]>({
    queryKey: ['connectors', TENANT_CODE],
    queryFn: () => listConnectors(TENANT_CODE),
    enabled: status === 'authenticated',
  })

  const rollbackMutation = useMutation({
    mutationFn: (version: number) => rollbackForm(formKey, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['form', formKey] })
      queryClient.invalidateQueries({ queryKey: ['formVersions', formKey] })
      queryClient.invalidateQueries({ queryKey: ['formDefinitions'] })
      alert('Form restored to selected version')
      router.refresh()
    },
    onError: (err: Error) => {
      alert(`Restore failed: ${err.message}`)
    }
  })

  if (isLoading) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-muted-foreground">Loading form definition...</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (error) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-destructive mb-4">
              Failed to load form: {error instanceof Error ? error.message : 'Unknown error'}
            </p>
            <Button asChild>
              <Link href="/forms">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Forms
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="h-screen flex flex-col">
      {/* Back nav */}
      <div className="flex items-center justify-between px-4 py-2 border-b bg-muted/30">
        <Button asChild variant="ghost" size="sm">
          <Link href="/forms">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Forms
          </Link>
        </Button>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setShowDataSources(!showDataSources)}
          >
            Data Sources
            {showDataSources ? <ChevronRight className="h-4 w-4 ml-2" /> : <ChevronLeft className="h-4 w-4 ml-2" />}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setHistoryOpen(!historyOpen)}
          >
            <History className="h-4 w-4 mr-2" />
            Version History
            {historyOpen ? <ChevronRight className="h-4 w-4 ml-2" /> : <ChevronLeft className="h-4 w-4 ml-2" />}
          </Button>
        </div>
      </div>

      {/* Main content */}
      <div className="flex flex-1 overflow-hidden">
        {/* Data Sources reference panel */}
        {showDataSources && (
          <div className="w-72 border-r bg-muted/30 overflow-auto p-3 flex-shrink-0">
            <p className="text-xs font-semibold mb-3">Connector Data Sources</p>
            {connectors.length === 0 ? (
              <p className="text-xs text-muted-foreground">No connectors registered.</p>
            ) : (
              connectors.map((c) => {
                const fields: Array<{ key: string; type: string }> = (() => {
                  try {
                    return c.sampleSchema ? JSON.parse(c.sampleSchema) : []
                  } catch {
                    return []
                  }
                })()

                return (
                  <div key={c.connectorKey} className="mb-4">
                    <p className="text-xs font-medium">{c.displayName}</p>
                    <code className="block text-xs bg-background border rounded px-1 py-0.5 mb-1 font-mono">
                      {c.connectorKey}
                    </code>
                    {fields.length > 0 && (
                      <ul className="text-xs text-muted-foreground space-y-0.5 ml-2">
                        {fields.map((f) => (
                          <li key={f.key} className="font-mono">
                            {f.key}
                            {f.type ? ` (${f.type})` : ''}
                          </li>
                        ))}
                      </ul>
                    )}
                    {fields.length === 0 && (
                      <p className="text-xs text-muted-foreground ml-2">No schema defined</p>
                    )}
                  </div>
                )
              })
            )}
          </div>
        )}

        {/* Existing content: FormJsBuilder + version history */}
        <div className="flex flex-1 overflow-hidden">
          <div className="flex-1 overflow-hidden">
            <FormJsBuilder
              initialForm={formDef?.formJson}
              formKey={formKey}
              initialOwningDepartment={formDef?.owningDepartment}
            />
          </div>

          {/* Version history sidebar */}
          {historyOpen && (
            <div className="w-72 border-l bg-background overflow-y-auto flex-shrink-0">
              <Card className="border-0 rounded-none h-full">
                <CardHeader className="pb-3 border-b">
                  <CardTitle className="text-sm font-semibold">Version History</CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  {!versions ? (
                    <p className="text-sm text-muted-foreground p-4">Loading...</p>
                  ) : versions.length === 0 ? (
                    <p className="text-sm text-muted-foreground p-4">No version history found.</p>
                  ) : (
                    <ul className="divide-y">
                      {versions.map((v) => (
                        <li key={v.id} className="p-3 flex items-start justify-between gap-2">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-medium">v{v.version}</span>
                              {v.isActive && (
                                <span className="text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded">
                                  Active
                                </span>
                              )}
                            </div>
                            <p className="text-xs text-muted-foreground mt-0.5 truncate">
                              {v.createdBy}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {new Date(v.createdAt).toLocaleString()}
                            </p>
                          </div>
                          {!v.isActive && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="flex-shrink-0 h-7 px-2"
                              disabled={rollbackMutation.isPending}
                              onClick={() => {
                                setPendingConfirm({
                                  title: 'Restore Version',
                                  description: `Restore form to version ${v.version}? The current version will be replaced.`,
                                  onConfirm: () => rollbackMutation.mutate(v.version),
                                })
                              }}
                            >
                              <RotateCcw className="h-3 w-3 mr-1" />
                              Restore
                            </Button>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </CardContent>
              </Card>
            </div>
          )}
        </div>
      </div>

      {pendingConfirm && (
        <ConfirmDialog
          open={true}
          onOpenChange={(open) => { if (!open) setPendingConfirm(null) }}
          title={pendingConfirm.title}
          description={pendingConfirm.description}
          confirmLabel="Confirm"
          variant="default"
          onConfirm={pendingConfirm.onConfirm}
        />
      )}
    </div>
  )
}
