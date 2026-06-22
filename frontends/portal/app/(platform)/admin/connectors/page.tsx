'use client'

import { useMemo, useState } from 'react'
import { useSession } from 'next-auth/react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { RefreshCw, Plus, Pencil, FlaskConical, Trash2, Upload, Plug } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { EmptyState } from '@/components/ui/empty-state'
import { Skeleton } from '@/components/ui/skeleton'
import { useTranslations } from 'next-intl'
import { PageSurface } from '@/components/layout/page-surface'
import { listConnectors, deleteConnector, type ConnectorResponse } from '@/lib/api/connectors'
import { ConnectorForm } from '@/components/admin/ConnectorForm'
import { DtdsConnectorCard } from '@/components/admin/DtdsConnectorCard'
import { ImportOpenApiModal } from '@/components/admin/ImportOpenApiModal'
import { useDtdsConnectors } from '@/hooks/useDtdsConnectors'
import type { ConnectorDefinition } from '@/lib/api/dtds'

interface ConnectorGroup {
  connectorKey: string
  displayName: string
  endpoints: ConnectorResponse[]
  primary: ConnectorResponse
}

function groupConnectors(flat: ConnectorResponse[]): ConnectorGroup[] {
  const map = new Map<string, ConnectorResponse[]>()
  for (const c of flat) {
    const list = map.get(c.connectorKey) ?? []
    list.push(c)
    map.set(c.connectorKey, list)
  }
  return Array.from(map.entries()).map(([key, endpoints]) => {
    const primary =
      endpoints.find(e => e.environment === 'production') ??
      endpoints.find(e => e.environment === 'staging') ??
      endpoints[0]
    return { connectorKey: key, displayName: primary.displayName, endpoints, primary }
  })
}

const envVariant = (env: string): 'default' | 'warning' | 'secondary' => {
  if (env === 'production') return 'default'
  if (env === 'staging') return 'warning'
  return 'secondary'
}

export default function ConnectorsPage() {
  const t = useTranslations('admin.connectors')
  const { status } = useSession()
  const queryClient = useQueryClient()

  const [createOpen, setCreateOpen] = useState(false)
  const [editingGroup, setEditingGroup] = useState<ConnectorGroup | null>(null)
  const [editDefaultTab, setEditDefaultTab] = useState<'general' | 'auth' | 'contract' | 'test'>('general')
  const [deletingGroup, setDeletingGroup] = useState<ConnectorGroup | null>(null)
  const [importOpenApiOpen, setImportOpenApiOpen] = useState(false)

  const dtdsConnectors = useDtdsConnectors()

  const { data: connectors, isLoading, error, refetch } = useQuery({
    queryKey: ['connectors'],
    queryFn: () => listConnectors(),
    enabled: status === 'authenticated',
    retry: 2,
  })

  const groups = useMemo(() => groupConnectors(connectors ?? []), [connectors])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['connectors'] })

  const handleCreated = () => { setCreateOpen(false); invalidate() }
  const handleUpdated = () => { invalidate() }

  const handleOpenApiImported = (_connector: ConnectorDefinition) => {
    dtdsConnectors.refetch()
    invalidate()
  }

  const handleDeleteConfirm = async () => {
    if (!deletingGroup) return
    try {
      await deleteConnector(deletingGroup.connectorKey)
      invalidate()
    } finally {
      setDeletingGroup(null)
    }
  }

  return (
    <PageSurface>
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>
          <p className="text-muted-foreground mt-1">{t('subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => refetch()} variant="outline" disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            {t('refresh')}
          </Button>
          <Button variant="outline" onClick={() => setImportOpenApiOpen(true)}>
            <Upload className="h-4 w-4 mr-2" />
            Import from OpenAPI
          </Button>
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                {t('newConnector')}
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>{t('registerConnector')}</DialogTitle>
                <DialogDescription>{t('registerDesc')}</DialogDescription>
              </DialogHeader>
              <ConnectorForm onSaved={handleCreated} />
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {isLoading && (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="rounded-xl border border-border bg-card p-4 space-y-3">
              <div className="flex items-center justify-between">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-7 w-16 rounded-md" />
              </div>
              <Skeleton className="h-3 w-56" />
              <div className="flex gap-2">
                <Skeleton className="h-5 w-20 rounded-full" />
                <Skeleton className="h-5 w-20 rounded-full" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!isLoading && error && (
        <Card className="border-destructive/40 bg-destructive/5">
          <CardContent className="pt-5 pb-5">
            <div className="flex items-start gap-4">
              <div className="w-9 h-9 rounded-lg bg-destructive/10 flex items-center justify-center shrink-0 mt-0.5">
                <RefreshCw size={16} className="text-destructive" />
              </div>
              <div className="flex-1 space-y-1">
                <p className="text-sm font-semibold text-destructive">Unable to load connectors</p>
                <p className="text-sm text-muted-foreground">The admin service may be temporarily unavailable. Ensure all backend services are running.</p>
              </div>
              <Button variant="outline" size="sm" onClick={() => refetch()} className="shrink-0">
                {t('retry')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && groups.length === 0 && (
        <EmptyState
          icon={Plug}
          title="No connectors configured"
          description="Register an external API connector to use in your BPMN processes."
          action={
            <Button variant="outline" size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              Add connector
            </Button>
          }
        />
      )}

      {!isLoading && !error && groups.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {groups.map((group) => (
            <Card key={group.connectorKey} className="flex flex-col wf-card-interactive">
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between gap-2">
                  <CardTitle className="text-base leading-tight">{group.displayName}</CardTitle>
                  <div className="flex items-center gap-1 shrink-0">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7"
                      title="Edit connector"
                      aria-label="Edit connector"
                      onClick={() => { setEditDefaultTab('general'); setEditingGroup(group) }}
                    >
                      <Pencil className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-destructive hover:text-destructive hover:bg-destructive/10"
                      title="Delete connector"
                      aria-label="Delete connector"
                      onClick={() => setDeletingGroup(group)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
                <CardDescription className="mt-1">
                  <code className="text-xs bg-muted px-1.5 py-0.5 rounded">{group.connectorKey}</code>
                </CardDescription>
              </CardHeader>

              <CardContent className="flex-1 space-y-3 text-sm">
                {/* Endpoint list */}
                <div className="space-y-1.5">
                  {group.endpoints.map((ep) => (
                    <div key={ep.endpointId} className="flex items-center gap-2">
                      <Badge variant={envVariant(ep.environment)} className="w-24 justify-center shrink-0">
                        {ep.environment}
                      </Badge>
                      <span
                        className="text-xs text-muted-foreground truncate flex-1 font-mono"
                        title={ep.baseUrl}
                      >
                        {ep.baseUrl}
                      </span>
                      {!ep.active && <Badge variant="secondary">Off</Badge>}
                    </div>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{group.primary.authScheme}</Badge>
                    {group.primary.sampleSchema !== null && (
                      <Badge variant="secondary">Contract</Badge>
                    )}
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs gap-1"
                    onClick={() => { setEditDefaultTab('test'); setEditingGroup(group) }}
                  >
                    <FlaskConical className="h-3 w-3" />
                    Test
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deletingGroup}
        onOpenChange={(open) => !open && setDeletingGroup(null)}
        title={`Delete ${deletingGroup?.displayName}?`}
        description={`This permanently removes all endpoints and the stored credential for ${deletingGroup?.connectorKey ?? ''}. Any BPMN processes referencing this connector will fail at runtime.`}
        onConfirm={handleDeleteConfirm}
      />

      {/* Edit / Test dialog */}
      <Dialog open={!!editingGroup} onOpenChange={(open) => !open && setEditingGroup(null)}>
        <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingGroup?.displayName}</DialogTitle>
            <DialogDescription>
              <code className="text-xs">{editingGroup?.connectorKey}</code>
            </DialogDescription>
          </DialogHeader>
          {editingGroup && (
            <ConnectorForm
              existingConnector={editingGroup.primary}
              allEndpoints={editingGroup.endpoints}
              defaultTab={editDefaultTab}
              onSaved={handleUpdated}
            />
          )}
        </DialogContent>
      </Dialog>

      {/* DTDS Connector Catalog */}
      {(dtdsConnectors.connectors.length > 0 || dtdsConnectors.isLoading) && (
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-foreground">Connector Catalog</h2>
              <p className="text-sm text-muted-foreground">
                Design-time connector definitions available for process design.
              </p>
            </div>
            {dtdsConnectors.isLoading && (
              <RefreshCw className="h-4 w-4 animate-spin text-muted-foreground" />
            )}
          </div>

          {dtdsConnectors.error && (
            <Card className="border-destructive/40 bg-destructive/5">
              <CardContent className="pt-4 pb-4">
                <p className="text-sm text-destructive">
                  Failed to load connector catalog: {dtdsConnectors.error.message}
                </p>
              </CardContent>
            </Card>
          )}

          {!dtdsConnectors.isLoading && dtdsConnectors.connectors.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {dtdsConnectors.connectors.map(connector => (
                <DtdsConnectorCard key={connector.key} connector={connector} />
              ))}
            </div>
          )}

          {dtdsConnectors.isLoading && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {[1, 2, 3].map(i => (
                <Card key={i}>
                  <CardHeader className="pb-2">
                    <Skeleton className="h-5 w-40" />
                    <Skeleton className="h-3 w-24 mt-1" />
                  </CardHeader>
                  <CardContent>
                    <Skeleton className="h-3 w-full mb-2" />
                    <Skeleton className="h-3 w-3/4" />
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </section>
      )}

      {/* Import OpenAPI Modal */}
      <ImportOpenApiModal
        open={importOpenApiOpen}
        onOpenChange={setImportOpenApiOpen}
        onImported={handleOpenApiImported}
      />
    </div>
    </PageSurface>
  )
}
