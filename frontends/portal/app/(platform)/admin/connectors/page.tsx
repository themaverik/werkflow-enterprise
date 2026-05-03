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
import { RefreshCw, Plus, Pencil, FlaskConical } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { listConnectors, type ConnectorResponse } from '@/lib/api/connectors'
import { ConnectorForm } from '@/components/admin/ConnectorForm'

const TENANT_CODE = process.env.NEXT_PUBLIC_TENANT_CODE ?? 'default'

export default function ConnectorsPage() {
  const t = useTranslations('admin.connectors')
  const { status } = useSession()
  const queryClient = useQueryClient()

  const [createOpen, setCreateOpen] = useState(false)
  const [editingConnector, setEditingConnector] = useState<ConnectorResponse | null>(null)
  const [editDefaultTab, setEditDefaultTab] = useState<'general' | 'auth' | 'contract' | 'test'>('general')

  const { data: connectors, isLoading, error, refetch } = useQuery({
    queryKey: ['connectors', TENANT_CODE],
    queryFn: () => listConnectors(TENANT_CODE),
    enabled: status === 'authenticated',
    retry: 2,
  })

  const handleCreated = () => {
    setCreateOpen(false)
    queryClient.invalidateQueries({ queryKey: ['connectors', TENANT_CODE] })
  }

  const handleUpdated = () => {
    setEditingConnector(null)
    queryClient.invalidateQueries({ queryKey: ['connectors', TENANT_CODE] })
  }

  const environmentVariant = (env: string): 'default' | 'warning' | 'secondary' => {
    if (env === 'production') return 'default'
    if (env === 'staging') return 'warning'
    return 'secondary'
  }

  return (
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
              <ConnectorForm tenantCode={TENANT_CODE} onSaved={handleCreated} />
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {isLoading && (
        <div className="text-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto text-muted-foreground" />
          <p className="text-muted-foreground mt-2">{t('loading')}</p>
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

      {!isLoading && !error && connectors?.length === 0 && (
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <p className="text-muted-foreground">{t('noConnectors')}</p>
            <Button variant="link" className="mt-2" onClick={() => setCreateOpen(true)}>
              Register the first connector
            </Button>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && connectors && connectors.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {connectors.map((connector) => (
            <Card key={connector.endpointId} className="flex flex-col">
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between gap-2">
                  <CardTitle className="text-base leading-tight">{connector.displayName}</CardTitle>
                  <div className="flex items-center gap-1 shrink-0">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7"
                      title="Edit connector"
                      onClick={() => { setEditDefaultTab('general'); setEditingConnector(connector) }}
                    >
                      <Pencil className="h-3.5 w-3.5" />
                    </Button>
                    <div className="flex gap-1">
                      <Badge variant={environmentVariant(connector.environment)}>
                        {connector.environment}
                      </Badge>
                      <Badge variant={connector.active ? 'success' : 'secondary'}>
                        {connector.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </div>
                  </div>
                </div>
                <CardDescription className="mt-1">
                  <code className="text-xs bg-muted px-1.5 py-0.5 rounded">{connector.connectorKey}</code>
                </CardDescription>
              </CardHeader>
              <CardContent className="flex-1 space-y-2 text-sm">
                <div className="text-muted-foreground truncate" title={connector.baseUrl}>
                  {connector.baseUrl}
                </div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{connector.authScheme}</Badge>
                    {connector.sampleSchema !== null && (
                      <Badge variant="secondary">Contract captured</Badge>
                    )}
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs gap-1"
                    onClick={() => { setEditDefaultTab('test'); setEditingConnector(connector) }}
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

      {/* Edit / Test dialog */}
      <Dialog open={!!editingConnector} onOpenChange={(open) => !open && setEditingConnector(null)}>
        <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingConnector?.displayName}
            </DialogTitle>
            <DialogDescription>
              <code className="text-xs">{editingConnector?.connectorKey}</code>
            </DialogDescription>
          </DialogHeader>
          {editingConnector && (
            <ConnectorForm
              tenantCode={TENANT_CODE}
              existingConnector={editingConnector}
              defaultTab={editDefaultTab}
              onSaved={handleUpdated}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
