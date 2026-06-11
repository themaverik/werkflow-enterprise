'use client'

import { useState } from 'react'
import axios from 'axios'
import { useSession } from 'next-auth/react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Plus, RefreshCw, Pencil, Trash2, KeyRound, Loader2, CheckCircle2, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import {
  listCredentials,
  deleteCredential,
  testCredential,
  getCredentialType,
  type TenantCredentialResponse,
  type CredentialTestResult,
} from '@/lib/api/credentials'
import { CredentialForm } from '@/components/admin/CredentialForm'
import { PageSurface } from '@/components/layout/page-surface'

type TestState = CredentialTestResult | 'testing'

export default function CredentialsPage() {
  const { status } = useSession()
  const t = useTranslations('admin.credentials')
  const qc = useQueryClient()

  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [testResults, setTestResults] = useState<Record<string, TestState>>({})
  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create')
  const [editTarget, setEditTarget] = useState<TenantCredentialResponse | undefined>(undefined)

  const { data: credentials = [], isLoading, error, refetch } = useQuery({
    queryKey: ['tenant-credentials'],
    queryFn: listCredentials,
    enabled: status === 'authenticated',
    retry: 2,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCredential(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tenant-credentials'] })
      setDeletingId(null)
      toast.success(t('deleteSuccess'))
    },
    onError: (e: unknown) => {
      if (axios.isAxiosError(e) && e.response?.status === 400) {
        toast.error(t('invalidId'))
      } else {
        toast.error(t('deleteFailed'))
      }
    },
  })

  function openCreate() {
    setEditTarget(undefined)
    setFormMode('create')
    setFormOpen(true)
  }

  function openEdit(cred: TenantCredentialResponse) {
    setEditTarget(cred)
    setFormMode('edit')
    setFormOpen(true)
  }

  async function handleTest(id: string) {
    setTestResults((prev) => ({ ...prev, [id]: 'testing' }))
    try {
      const result = await testCredential(id)
      setTestResults((prev) => ({ ...prev, [id]: result }))
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && e.response?.status === 400) {
        toast.error(t('invalidId'))
      }
      setTestResults((prev) => ({ ...prev, [id]: { success: false, message: t('testFailed') } }))
    }
  }

  function formatDate(iso: string): string {
    return new Date(iso).toLocaleString()
  }

  return (
    <PageSurface>
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>
          <p className="text-sm text-muted-foreground mt-0.5">{t('subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => refetch()} disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            {t('refresh')}
          </Button>
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addCredential')}
          </Button>
        </div>
      </div>

      {isLoading && (
        <div className="text-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto text-muted-foreground" />
          <p className="text-muted-foreground mt-2 text-sm">{t('loading')}</p>
        </div>
      )}

      {!isLoading && error && (
        <Card className="border-destructive/40 bg-destructive/5">
          <CardContent className="pt-5 pb-5">
            <div className="flex items-start gap-4">
              <div className="w-9 h-9 rounded-lg bg-destructive/10 flex items-center justify-center shrink-0 mt-0.5">
                <KeyRound size={16} className="text-destructive" />
              </div>
              <div className="flex-1 space-y-1">
                <p className="text-sm font-semibold text-destructive">{t('loadError')}</p>
                <p className="text-sm text-muted-foreground">{t('loadErrorHint')}</p>
              </div>
              <Button variant="outline" size="sm" onClick={() => refetch()} className="shrink-0">
                {t('retry')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && credentials.length === 0 && (
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <KeyRound className="h-10 w-10 mx-auto text-muted-foreground mb-3" strokeWidth={1.5} />
            <p className="text-muted-foreground text-sm">{t('noCredentials')}</p>
            <Button variant="link" className="mt-2" onClick={openCreate}>
              {t('addFirst')}
            </Button>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && credentials.length > 0 && (
        <div className="overflow-hidden rounded-lg border bg-card">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/40">
                <th className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground">{t('colType')}</th>
                <th className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground">{t('colLabel')}</th>
                <th className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground">{t('colCreated')}</th>
                <th className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground">{t('colRotated')}</th>
                <th className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground">{t('colLastTest')}</th>
                <th className="w-28" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {credentials.map((cred) => {
                const typeSchema = getCredentialType(cred.credentialType)
                const displayName = typeSchema?.displayName ?? cred.credentialType
                const testState = testResults[cred.id]
                return (
                  <tr key={cred.id} className="group hover:bg-muted/20 transition-colors">
                    <td className="px-4 py-3 text-sm font-medium">{displayName}</td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{cred.label}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{formatDate(cred.createdAt)}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {cred.rotatedAt ? formatDate(cred.rotatedAt) : <span className="italic">{t('never')}</span>}
                    </td>
                    <td className="px-4 py-3">
                      <TestCell
                        state={testState}
                        neverLabel={t('notTested')}
                        okLabel={t('testOk')}
                        failedShortLabel={t('testFailedShort')}
                      />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1 justify-end">
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-6 text-xs px-2"
                          aria-label={t('testAriaLabel', { label: cred.label })}
                          onClick={() => handleTest(cred.id)}
                          disabled={testState === 'testing'}
                        >
                          {testState === 'testing' ? (
                            <Loader2 className="h-3 w-3 animate-spin mr-1" />
                          ) : null}
                          {t('test')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-7 w-7"
                          title={t('edit')}
                          aria-label={t('edit')}
                          onClick={() => openEdit(cred)}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-7 w-7 text-destructive hover:text-destructive hover:bg-destructive/10"
                          title={t('delete')}
                          aria-label={t('delete')}
                          onClick={() => setDeletingId(cred.id)}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deletingId}
        onOpenChange={(open) => !open && setDeletingId(null)}
        title={t('deleteTitle')}
        description={t('deleteDesc', {
          label: credentials.find((c) => c.id === deletingId)?.label ?? deletingId ?? '',
        })}
        onConfirm={() => { if (deletingId) deleteMutation.mutate(deletingId) }}
      />

      {/* Create / edit modal */}
      <CredentialForm
        open={formOpen}
        mode={formMode}
        initial={editTarget}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          qc.invalidateQueries({ queryKey: ['tenant-credentials'] })
          setFormOpen(false)
        }}
      />
    </div>
    </PageSurface>
  )
}

function TestCell({
  state,
  neverLabel,
  okLabel,
  failedShortLabel,
}: {
  state: TestState | undefined
  neverLabel: string
  okLabel: string
  failedShortLabel: string
}) {
  if (!state) return <span className="text-xs text-muted-foreground italic">{neverLabel}</span>
  if (state === 'testing') return <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />
  if (state.success) {
    return (
      <span className="flex items-center gap-1 text-xs text-[color:var(--wf-accent)]">
        <CheckCircle2 className="h-3.5 w-3.5" />
        {state.message || okLabel}
      </span>
    )
  }
  return (
    <span className="flex items-center gap-1 text-xs text-destructive" title={state.message}>
      <XCircle className="h-3.5 w-3.5" />
      {state.message || failedShortLabel}
    </span>
  )
}
