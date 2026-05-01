'use client'

import { Button } from "@/components/ui/button"
import Link from "next/link"
import { FileText, Plus, Trash2, Download, Eye, Edit, Activity } from "lucide-react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { useSession } from "next-auth/react"
import { getFormDefinitions, deleteFormDefinition } from "@/lib/api/flowable"
import { useState } from "react"
import { useTranslations } from "next-intl"
import { ErrorDisplay, LoadingState } from "@/components/ui/error-display"
import { useAuthorization } from "@/lib/auth/use-authorization"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { StatCard } from '@/components/ui/stat-card'
import { FilterPills } from '@/components/ui/filter-pills'
import { StatusBadge } from '@/components/ui/status-badge'

const MANAGER_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'HR_ADMIN', 'FINANCE_ADMIN', 'PROCUREMENT_ADMIN', 'INVENTORY_ADMIN']

export default function FormsPage() {
  const t = useTranslations('forms')
  const { status } = useSession()
  const [activeTab, setActiveTab] = useState('all')
  const [deletingKey, setDeletingKey] = useState<string | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<{ title: string; description: string; onConfirm: () => void } | null>(null)
  const queryClient = useQueryClient()
  const { hasAnyRole, getDepartment } = useAuthorization()

  const isManagerOrAbove = hasAnyRole(MANAGER_ROLES)
  const userDepartment = getDepartment()

  const canEditForm = (form: any) => {
    if (!isManagerOrAbove) return false
    if (!form.owningDepartment) return isManagerOrAbove // unowned: any manager
    return hasAnyRole(['ADMIN', 'SUPER_ADMIN']) || form.owningDepartment === userDepartment
  }

  // Fetch form definitions
  const { data: forms, isLoading, error, refetch } = useQuery({
    queryKey: ['formDefinitions'],
    queryFn: getFormDefinitions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
  })

  // Delete form mutation
  const deleteMutation = useMutation({
    mutationFn: deleteFormDefinition,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['formDefinitions'] })
      setDeletingKey(null)
      alert('Form deleted successfully')
    },
    onError: (error: Error) => {
      alert(`Failed to delete form: ${error.message}`)
      setDeletingKey(null)
    }
  })

  // Download form JSON
  const handleDownload = (formKey: string, formJson: string) => {
    const blob = new Blob([formJson], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${formKey}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  return (
    <div className="container py-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('subtitle')}</p>
        </div>
      </div>

      {/* Loading state */}
      {isLoading && <LoadingState message={t('loading')} className="mb-6" />}

      {/* Error state */}
      {error && !isLoading && (
        <ErrorDisplay
          error={error as Error}
          onRetry={() => refetch()}
          title={t('failedToLoad')}
          className="mb-6"
        />
      )}

      {/* Stat cards */}
      <div className="grid gap-4 sm:grid-cols-3 mb-6">
        <StatCard icon={FileText} label="Total Forms"   value={forms?.length ?? 0}                        iconColor="#149ba5" />
        <StatCard icon={Activity}  label="Deployed"      value={forms?.filter((f) => !!f.key).length ?? 0} iconColor="#16a34a" />
        <StatCard icon={Plus}      label="Created Today" value={0}                                         iconColor="#1d4ed8" />
      </div>

      {/* Filter pills + create button */}
      <div className="flex items-center justify-between mb-4">
        <FilterPills
          options={[{ key: 'all', label: 'All' }, { key: 'deployed', label: 'Deployed' }]}
          active={activeTab}
          onChange={setActiveTab}
        />
        {isManagerOrAbove && (
          <Button asChild size="sm">
            <Link href="/forms/new">
              <Plus size={14} className="mr-1" />{t('createNewForm')}
            </Link>
          </Button>
        )}
      </div>

      {/* Table */}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Key</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Department</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Status</th>
              <th className="px-4 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {forms?.map((form) => (
              <tr key={form.key} className="hover:bg-muted/30 transition-colors">
                <td className="px-4 py-3 font-medium">{form.name}</td>
                <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{form.key}</td>
                <td className="px-4 py-3 text-muted-foreground">{form.owningDepartment ?? '—'}</td>
                <td className="px-4 py-3"><StatusBadge status="completed" /></td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-1">
                    <Button asChild variant="ghost" size="sm">
                      <Link href={`/forms/preview/${form.key}`}><Eye size={14} /></Link>
                    </Button>
                    {canEditForm(form) && (
                      <>
                        <Button asChild variant="ghost" size="sm">
                          <Link href={`/forms/edit/${form.key}`}><Edit size={14} /></Link>
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDownload(form.key, form.formJson)}
                        >
                          <Download size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          onClick={() =>
                            setPendingConfirm({
                              title: t('deleteForm'),
                              description: `Delete "${form.name}"? This cannot be undone.`,
                              onConfirm: () => { setDeletingKey(form.key); deleteMutation.mutate(form.key) },
                            })
                          }
                        >
                          <Trash2 size={14} />
                        </Button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {!isLoading && (!forms || forms.length === 0) && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground text-sm">
                  {t('noFormsCreated')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {pendingConfirm && (
        <ConfirmDialog
          open={true}
          onOpenChange={(open) => { if (!open) setPendingConfirm(null) }}
          title={pendingConfirm.title}
          description={pendingConfirm.description}
          confirmLabel={t('delete')}
          onConfirm={pendingConfirm.onConfirm}
        />
      )}
    </div>
  )
}
