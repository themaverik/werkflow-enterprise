'use client'

import { useParams, useRouter } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getFormDefinition, getFormVersions, rollbackForm } from '@/lib/api/flowable'
import { listConnectors, ConnectorResponse } from '@/lib/api/connectors'
import FormJsBuilder from '@/components/forms/FormJsBuilder'
import { Card, CardContent } from '@/components/ui/card'
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
    queryKey: ['connectors'],
    queryFn: () => listConnectors(),
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
        {/* Data Sources reference panel — matches the form-js palette panel
            aesthetic: white surface, --panel-card-border separator, uppercase
            small-caps section title. */}
        {showDataSources && (
          <aside
            className="w-72 overflow-auto flex-shrink-0 form-designer-aside"
            aria-label="Connector data sources"
          >
            <div className="form-designer-aside-header">Connector Data Sources</div>
            <div className="form-designer-aside-body">
              {connectors.length === 0 ? (
                <p className="form-designer-aside-muted">No connectors registered.</p>
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
                    <div key={c.connectorKey} className="form-designer-aside-item">
                      <p className="form-designer-aside-item-title">{c.displayName}</p>
                      <code className="form-designer-aside-code">{c.connectorKey}</code>
                      {fields.length > 0 ? (
                        <ul className="form-designer-aside-fieldlist">
                          {fields.map((f) => (
                            <li key={f.key}>
                              <span className="font-mono">{f.key}</span>
                              {f.type ? <span className="form-designer-aside-fieldtype"> {f.type}</span> : null}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className="form-designer-aside-muted">No schema defined</p>
                      )}
                    </div>
                  )
                })
              )}
            </div>
          </aside>
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

          {/* Version history sidebar — same shell pattern as the Data
              Sources aside above so both right-side panels look uniform. */}
          {historyOpen && (
            <aside
              className="w-72 overflow-y-auto flex-shrink-0 form-designer-aside form-designer-aside--right"
              aria-label="Version history"
            >
              <div className="form-designer-aside-header">Version History</div>
              <div className="form-designer-aside-body form-designer-aside-body--flush">
                {!versions ? (
                  <p className="form-designer-aside-muted form-designer-aside-muted--padded">Loading...</p>
                ) : versions.length === 0 ? (
                  <p className="form-designer-aside-muted form-designer-aside-muted--padded">No version history found.</p>
                ) : (
                  <ul className="form-designer-aside-list">
                    {versions.map((v) => (
                      <li key={v.id} className="form-designer-aside-listitem">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium">v{v.version}</span>
                            {v.isActive && (
                              <span className="form-designer-aside-badge form-designer-aside-badge--active">
                                Active
                              </span>
                            )}
                          </div>
                          <p className="form-designer-aside-meta truncate">{v.createdBy}</p>
                          <p className="form-designer-aside-meta">
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
              </div>
            </aside>
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
