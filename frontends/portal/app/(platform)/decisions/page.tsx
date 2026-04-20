'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useTranslations } from 'next-intl'
import { Plus, Trash2, Pencil, Clock, Table2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ErrorDisplay, LoadingState } from '@/components/ui/error-display'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { useToast } from '@/hooks/use-toast'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { listDecisions, deleteDeployment, type DmnDecisionDto } from '@/lib/api/dmn'

const ADMIN_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER']

export default function DecisionsPage() {
  const t = useTranslations('decisions')
  const { status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [pendingDelete, setPendingDelete] = useState<DmnDecisionDto | null>(null)

  const canManage = hasAnyRole(ADMIN_ROLES)

  const { data: decisions, isLoading, error, refetch } = useQuery({
    queryKey: ['dmnDecisions'],
    queryFn: listDecisions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
  })

  const deleteMutation = useMutation({
    mutationFn: (deploymentId: string) => deleteDeployment(deploymentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dmnDecisions'] })
      setPendingDelete(null)
      toast({ title: t('list.deleted'), description: t('list.deletedDesc') })
    },
    onError: (err: Error) => {
      toast({ title: t('list.deleteFailed'), description: err.message, variant: 'destructive' })
      setPendingDelete(null)
    },
  })

  if (isLoading) return <LoadingState />
  if (error) return <ErrorDisplay error={error} onRetry={refetch} />

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('list.subtitle')}</p>
        </div>
        {canManage && (
          <Button asChild>
            <Link href="/decisions/new">
              <Plus className="mr-2 h-4 w-4" />
              {t('list.create')}
            </Link>
          </Button>
        )}
      </div>

      {!decisions || decisions.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <Table2 className="mb-4 h-12 w-12 text-muted-foreground" />
            <p className="text-muted-foreground">{t('list.empty')}</p>
            {canManage && (
              <Button asChild className="mt-4">
                <Link href="/decisions/new">
                  <Plus className="mr-2 h-4 w-4" />
                  {t('list.create')}
                </Link>
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4">
          {decisions.map((decision) => (
            <Card key={decision.id}>
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="text-base">{decision.name}</CardTitle>
                    <CardDescription className="font-mono text-xs">{decision.key}</CardDescription>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="ghost" size="sm" asChild>
                      <Link href={`/decisions/${decision.key}/executions`}>
                        <Clock className="mr-1 h-4 w-4" />
                        {t('list.history')}
                      </Link>
                    </Button>
                    {canManage && (
                      <>
                        <Button variant="ghost" size="sm" asChild>
                          <Link href={`/decisions/${decision.key}/edit`}>
                            <Pencil className="mr-1 h-4 w-4" />
                            {t('list.edit')}
                          </Link>
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setPendingDelete(decision)}
                          className="text-destructive hover:text-destructive"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex gap-4 text-sm text-muted-foreground">
                  <span>{t('list.version', { version: decision.version })}</span>
                  <span>{t('list.deployed', { date: new Date(decision.deployedAt).toLocaleDateString() })}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {pendingDelete && (
        <ConfirmDialog
          open
          onOpenChange={(open) => { if (!open) setPendingDelete(null) }}
          title={t('list.confirmDelete')}
          description={t('list.confirmDeleteDesc', { name: pendingDelete.name })}
          confirmLabel={t('list.deleteButton')}
          variant="destructive"
          onConfirm={() => deleteMutation.mutate(pendingDelete.deploymentId)}
        />
      )}
    </div>
  )
}
