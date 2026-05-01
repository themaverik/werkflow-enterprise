'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useTranslations } from 'next-intl'
import { Plus, Trash2, Pencil, Clock, Table2, GitBranch, Activity, Edit } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ErrorDisplay, LoadingState } from '@/components/ui/error-display'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { useToast } from '@/hooks/use-toast'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { listDecisions, deleteDeployment, type DmnDecisionDto } from '@/lib/api/dmn'
import { StatCard } from '@/components/ui/stat-card'
import { FilterPills } from '@/components/ui/filter-pills'
import { StatusBadge } from '@/components/ui/status-badge'

const ADMIN_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER']

export default function DecisionsPage() {
  const t = useTranslations('decisions')
  const { status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [pendingDelete, setPendingDelete] = useState<DmnDecisionDto | null>(null)
  const [activeTab, setActiveTab] = useState('all')

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

  const filteredDecisions =
    activeTab === 'deployed'
      ? (decisions ?? []).filter((d) => !!d.key)
      : (decisions ?? [])

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold">{t('title')}</h1>
        <p className="text-muted-foreground">{t('list.subtitle')}</p>
      </div>

      {/* Stat cards */}
      <div className="grid gap-4 sm:grid-cols-2">
        <StatCard icon={GitBranch} label="Total Decisions" value={decisions?.length ?? 0}                        iconColor="#7c3aed" />
        <StatCard icon={Activity}  label="Deployed"         value={decisions?.filter((d) => !!d.key).length ?? 0} iconColor="#16a34a" />
      </div>

      <div className="flex items-center justify-between">
        <FilterPills
          options={[{ key: 'all', label: 'All' }, { key: 'deployed', label: 'Deployed' }]}
          active={activeTab}
          onChange={setActiveTab}
        />
        {canManage && (
          <Button asChild size="sm">
            <Link href="/decisions/new"><Plus size={14} className="mr-1" />{t('list.create')}</Link>
          </Button>
        )}
      </div>

      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Key</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Status</th>
              <th className="px-4 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {filteredDecisions.map((decision) => (
              <tr key={decision.id} className="hover:bg-muted/30 transition-colors">
                <td className="px-4 py-3 font-medium">{decision.name}</td>
                <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{decision.key}</td>
                <td className="px-4 py-3"><StatusBadge status="completed" /></td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-1">
                    <Button asChild variant="ghost" size="sm">
                      <Link href={`/decisions/${decision.key}/edit`}><Edit size={14} /></Link>
                    </Button>
                    <Button asChild variant="ghost" size="sm">
                      <Link href={`/decisions/${decision.key}/executions`}><Activity size={14} /></Link>
                    </Button>
                    {canManage && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setPendingDelete(decision)}
                        className="text-destructive hover:text-destructive"
                      >
                        <Trash2 size={14} />
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {filteredDecisions.length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground text-sm">
                  No decisions found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

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
