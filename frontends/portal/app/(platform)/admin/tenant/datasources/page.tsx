'use client'

import { useState } from 'react'
import { useSession } from 'next-auth/react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Plus, RefreshCw, Pencil, Trash2, Database, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  listDatasources,
  deleteDatasource,
  testDatasourceConnection,
  type TenantDatasourceResponse,
} from '@/lib/api/datasources'

type HealthResult = { ok: boolean; message: string } | 'testing'

export default function DatasourcesPage() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''
  const router = useRouter()
  const qc = useQueryClient()
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

  const deleteMutation = useMutation({
    mutationFn: (ref: string) => deleteDatasource(ref, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tenant-datasources'] })
      setDeletingRef(null)
      toast.success('Datasource removed')
    },
    onError: () => toast.error('Failed to delete datasource'),
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Tenant Datasources</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Register and manage JDBC datasource connections for database connector workflows.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => refetch()} disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => router.push('/admin/tenant/datasources/new')}>
            <Plus className="h-4 w-4 mr-2" />
            Add Datasource
          </Button>
        </div>
      </div>

      {isLoading && (
        <div className="text-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto text-muted-foreground" />
          <p className="text-muted-foreground mt-2 text-sm">Loading datasources…</p>
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
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <Database className="h-10 w-10 mx-auto text-muted-foreground mb-3" strokeWidth={1.5} />
            <p className="text-muted-foreground text-sm">No datasources registered yet.</p>
            <Button
              variant="link"
              className="mt-2"
              onClick={() => router.push('/admin/tenant/datasources/new')}
            >
              Register the first datasource
            </Button>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && datasources.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {datasources.map((ds) => (
            <DatasourceCard
              key={ds.ref}
              datasource={ds}
              onEdit={() => router.push(`/admin/tenant/datasources/${ds.ref}/edit`)}
              onDelete={() => setDeletingRef(ds.ref)}
              health={healthResults[ds.ref]}
              onTest={() => handleTest(ds.ref)}
            />
          ))}
        </div>
      )}

      {/* Delete confirmation */}
      <Dialog open={!!deletingRef} onOpenChange={(open) => !open && setDeletingRef(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Delete datasource?</DialogTitle>
            <DialogDescription>
              This removes the registration for{' '}
              <code className="font-mono text-xs bg-muted px-1 rounded">{deletingRef}</code>.
              Any deployed connectors using this datasource will fail at runtime.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setDeletingRef(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => deletingRef && deleteMutation.mutate(deletingRef)}
            >
              {deleteMutation.isPending && (
                <RefreshCw className="h-4 w-4 animate-spin mr-2" />
              )}
              Delete
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
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
    <Card className="flex flex-col">
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
            {datasource.username}
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
            <span className="flex items-center gap-1 text-xs" style={{ color: '#149ba5' }}>
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
