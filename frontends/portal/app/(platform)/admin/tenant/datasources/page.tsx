'use client'

import { useState } from 'react'
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
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { EmptyState } from '@/components/ui/empty-state'
import { Skeleton } from '@/components/ui/skeleton'
import { Plus, RefreshCw, Pencil, Trash2, Database, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  listDatasources,
  deleteDatasource,
  testDatasourceConnection,
  type TenantDatasourceResponse,
} from '@/lib/api/datasources'
import { PageSurface } from '@/components/layout/page-surface'
import { DatasourceForm } from './_components/DatasourceForm'

type HealthResult = { ok: boolean; message: string } | 'testing'

export default function DatasourcesPage() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()

  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<TenantDatasourceResponse | null>(null)
  const [deletingRef, setDeletingRef] = useState<string | null>(null)
  const [healthResults, setHealthResults] = useState<Record<string, HealthResult>>({})

  const handleTest = async (ref: string) => {
    setHealthResults((r) => ({ ...r, [ref]: 'testing' }))
    try {
      const result = await testDatasourceConnection(ref, token)
      setHealthResults((r) => ({ ...r, [ref]: { ok: result.ok, message: result.message ?? '' } }))
    } catch {
      setHealthResults((r) => ({ ...r, [ref]: { ok: false, message: 'Connection failed' } }))
    }
  }

  const { data: datasources = [], isLoading, error, refetch } = useQuery({
    queryKey: ['tenant-datasources'],
    queryFn: () => listDatasources(token),
    enabled: status === 'authenticated',
    retry: 2,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['tenant-datasources'] })

  const handleCreated = () => { setCreateOpen(false); invalidate() }
  const handleUpdated = () => { setEditTarget(null); invalidate() }

  const handleDeleteConfirm = async () => {
    if (!deletingRef) return
    try {
      await deleteDatasource(deletingRef, token)
      invalidate()
      toast.success('Datasource removed')
    } catch {
      toast.error('Failed to delete datasource')
    } finally {
      setDeletingRef(null)
    }
  }

  return (
    <PageSurface>
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Tenant Datasources</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Register and manage JDBC datasource connections for database connector workflows.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => refetch()} disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                Add Datasource
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>Register Datasource</DialogTitle>
                <DialogDescription>
                  Add a new JDBC datasource for use with database connector workflows.
                </DialogDescription>
              </DialogHeader>
              <DatasourceForm onSaved={handleCreated} onCancel={() => setCreateOpen(false)} />
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
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
                <Database size={16} className="text-destructive" />
              </div>
              <div className="flex-1 space-y-1">
                <p className="text-sm font-semibold text-destructive">Unable to load datasources</p>
                <p className="text-sm text-muted-foreground">
                  The admin service may be unavailable. Ensure backend services are running.
                </p>
              </div>
              <Button variant="outline" size="sm" onClick={() => refetch()} className="shrink-0">
                Retry
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && datasources.length === 0 && (
        <EmptyState
          icon={Database}
          title="No datasources registered yet"
          description="Register a JDBC datasource to use with database connector workflows."
          action={
            <Button variant="outline" size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              Add Datasource
            </Button>
          }
        />
      )}

      {!isLoading && !error && datasources.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {datasources.map((ds) => (
            <DatasourceCard
              key={ds.ref}
              datasource={ds}
              onEdit={() => setEditTarget(ds)}
              onDelete={() => setDeletingRef(ds.ref)}
              health={healthResults[ds.ref]}
              onTest={() => handleTest(ds.ref)}
            />
          ))}
        </div>
      )}

      {/* Edit dialog */}
      <Dialog open={!!editTarget} onOpenChange={(open) => !open && setEditTarget(null)}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Edit Datasource</DialogTitle>
            <DialogDescription>
              Update the configuration for{' '}
              <code className="font-mono text-xs bg-muted px-1 rounded">{editTarget?.ref}</code>.
            </DialogDescription>
          </DialogHeader>
          {editTarget && (
            <DatasourceForm existing={editTarget} onSaved={handleUpdated} onCancel={() => setEditTarget(null)} />
          )}
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deletingRef}
        onOpenChange={(open) => !open && setDeletingRef(null)}
        title="Delete datasource?"
        description={`This removes the registration for ${deletingRef ?? ''}. Any deployed connectors using this datasource will fail at runtime.`}
        onConfirm={handleDeleteConfirm}
      />
    </div>
    </PageSurface>
  )
}

// -------------------------------------------------------------------------
// DatasourceCard sub-component
// -------------------------------------------------------------------------

function DatasourceCard({
  datasource,
  onEdit,
  onDelete,
  health,
  onTest,
}: {
  datasource: TenantDatasourceResponse
  onEdit: () => void
  onDelete: () => void
  health?: HealthResult
  onTest: () => void
}) {
  return (
    <Card className="flex flex-col wf-card-interactive">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <Database className="h-4 w-4 text-muted-foreground shrink-0" strokeWidth={1.5} />
            <CardTitle className="text-base leading-tight truncate">
              {datasource.ref}
            </CardTitle>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              title="Edit datasource"
              onClick={onEdit}
            >
              <Pencil className="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-destructive hover:text-destructive hover:bg-destructive/10"
              title="Delete datasource"
              onClick={onDelete}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
        <CardDescription className="mt-1 truncate text-xs font-mono" title={datasource.jdbcUrl}>
          {datasource.jdbcUrl}
        </CardDescription>
      </CardHeader>

      <CardContent className="flex-1 space-y-2 text-sm">
        <div className="flex flex-wrap gap-1.5">
          {datasource.dialect && (
            <Badge variant="secondary" className="text-xs">
              {datasource.dialect}
            </Badge>
          )}
          <Badge variant="outline" className="text-xs font-mono">
            {datasource.credentialRef}
          </Badge>
          <Badge variant="outline" className="text-xs">
            pool {datasource.poolMinSize}–{datasource.poolMaxSize}
          </Badge>
        </div>
        <p className="text-xs text-muted-foreground font-mono truncate">
          {datasource.driverClassName}
        </p>

        {/* Health row */}
        <div className="flex items-center gap-2 pt-1">
          <Button
            variant="outline"
            size="sm"
            className="h-6 text-xs px-2"
            onClick={onTest}
            disabled={health === 'testing'}
          >
            {health === 'testing' ? (
              <Loader2 className="h-3 w-3 animate-spin mr-1" />
            ) : null}
            Test
          </Button>
          {health === 'testing' && (
            <span className="text-xs text-muted-foreground">Testing…</span>
          )}
          {health && health !== 'testing' && health.ok && (
            <span className="flex items-center gap-1 text-xs text-primary">
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-current" />
              Connected
            </span>
          )}
          {health && health !== 'testing' && !health.ok && (
            <span className="flex items-center gap-1 text-xs text-destructive">
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-current" />
              {health.message || 'Failed'}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
