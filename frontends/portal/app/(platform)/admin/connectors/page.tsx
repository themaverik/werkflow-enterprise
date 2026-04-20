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
import { RefreshCw, Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { listConnectors } from '@/lib/api/connectors'
import { ConnectorForm } from '@/components/admin/ConnectorForm'

const TENANT_CODE = process.env.NEXT_PUBLIC_TENANT_CODE ?? 'default'

export default function ConnectorsPage() {
  const t = useTranslations('admin.connectors')
  const { status } = useSession()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data: connectors, isLoading, error, refetch } = useQuery({
    queryKey: ['connectors', TENANT_CODE],
    queryFn: () => listConnectors(TENANT_CODE),
    enabled: status === 'authenticated',
    retry: 2,
  })

  const handleCreated = () => {
    setDialogOpen(false)
    queryClient.invalidateQueries({ queryKey: ['connectors', TENANT_CODE] })
  }

  const environmentVariant = (env: string): 'default' | 'warning' | 'secondary' => {
    if (env === 'production') return 'default'
    if (env === 'staging') return 'warning'
    return 'secondary'
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground mt-1">{t('subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => refetch()} variant="outline" disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            {t('refresh')}
          </Button>
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
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
              <ConnectorForm tenantCode={TENANT_CODE} onCreated={handleCreated} />
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
        <Card className="border-destructive">
          <CardContent className="pt-6">
            <div className="space-y-3">
              <p className="text-destructive font-semibold">{t('error')}</p>
              <p className="text-sm text-muted-foreground">{(error as Error).message}</p>
              <Button variant="outline" size="sm" onClick={() => refetch()}>
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
            <Button variant="link" className="mt-2" onClick={() => setDialogOpen(true)}>
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
                  <div className="flex flex-wrap gap-1 shrink-0">
                    <Badge variant={environmentVariant(connector.environment)}>
                      {connector.environment}
                    </Badge>
                    <Badge variant={connector.active ? 'success' : 'secondary'}>
                      {connector.active ? 'Active' : 'Inactive'}
                    </Badge>
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
                <div className="flex items-center gap-2">
                  <Badge variant="outline">{connector.authScheme}</Badge>
                  {connector.sampleSchema !== null && (
                    <Badge variant="secondary">Contract captured</Badge>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
