'use client'

import { useState } from 'react'
import { useSession } from 'next-auth/react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { RefreshCw, Plus, Pencil, Trash2, X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import {
  listCustodyMappings,
  createCustodyMapping,
  updateCustodyMapping,
  deleteCustodyMapping,
  type CustodyMappingResponse,
  type CustodyMappingRequest,
} from '@/lib/api/custody'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'

interface FormState {
  custodyOwner: string
  candidateGroupInput: string
  candidateGroups: string[]
}

const emptyForm: FormState = {
  custodyOwner: '',
  candidateGroupInput: '',
  candidateGroups: [],
}

export default function CustodyMappingsPage() {
  const t = useTranslations('admin.custody')
  const { status } = useSession()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [formState, setFormState] = useState<FormState>(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const { data: page, isLoading, error, refetch } = useQuery({
    queryKey: ['custody-mappings'],
    queryFn: () => listCustodyMappings(),
    enabled: status === 'authenticated',
    retry: 2,
  })

  const mappings = page?.content ?? []

  const handleAddGroup = () => {
    const group = formState.candidateGroupInput.trim()
    if (!group || formState.candidateGroups.includes(group)) return
    setFormState(prev => ({
      ...prev,
      candidateGroups: [...prev.candidateGroups, group],
      candidateGroupInput: '',
    }))
  }

  const handleRemoveGroup = (group: string) => {
    setFormState(prev => ({
      ...prev,
      candidateGroups: prev.candidateGroups.filter(g => g !== group),
    }))
  }

  const validateForm = (): boolean => {
    if (!formState.custodyOwner.trim()) {
      setFormError('Custody Owner is required')
      return false
    }
    if (formState.candidateGroups.length === 0) {
      setFormError('At least one Candidate Group is required')
      return false
    }
    return true
  }

  const handleSave = async () => {
    if (!validateForm()) return

    setSubmitting(true)
    try {
      const request: CustodyMappingRequest = {
        custodyOwner: formState.custodyOwner.trim(),
        candidateGroups: formState.candidateGroups,
      }

      if (editingId !== null) {
        await updateCustodyMapping(editingId, request)
      } else {
        await createCustodyMapping(request)
      }

      setDialogOpen(false)
      setFormState(emptyForm)
      setEditingId(null)
      queryClient.invalidateQueries({ queryKey: ['custody-mappings'] })
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to save mapping')
    } finally {
      setSubmitting(false)
    }
  }

  const handleEdit = (mapping: CustodyMappingResponse) => {
    setFormState({
      custodyOwner: mapping.custodyOwner,
      candidateGroupInput: '',
      candidateGroups: [...mapping.candidateGroups],
    })
    setEditingId(mapping.id)
    setDialogOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (deleteTarget === null) return

    setSubmitting(true)
    try {
      await deleteCustodyMapping(deleteTarget)
      setDeleteDialogOpen(false)
      setDeleteTarget(null)
      queryClient.invalidateQueries({ queryKey: ['custody-mappings'] })
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to delete mapping')
      setDeleteDialogOpen(false)
    } finally {
      setSubmitting(false)
    }
  }

  const handleDialogOpenChange = (open: boolean) => {
    if (!open) {
      setFormState(emptyForm)
      setEditingId(null)
      setFormError(null)
    }
    setDialogOpen(open)
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
          <Dialog open={dialogOpen} onOpenChange={handleDialogOpenChange}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="h-4 w-4 mr-2" />
                {t('newMapping')}
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-lg">
              <DialogHeader>
                <DialogTitle>{editingId !== null ? t('editMapping') : t('createMapping')}</DialogTitle>
                <DialogDescription>
                  {editingId !== null ? t('updateDesc') : t('createDesc')}
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="custodyOwner">Custody Owner</Label>
                  <Input
                    id="custodyOwner"
                    placeholder="e.g., LAPTOP_TEAM"
                    value={formState.custodyOwner}
                    onChange={e => {
                      setFormState(prev => ({ ...prev, custodyOwner: e.target.value }))
                      setFormError(null)
                    }}
                    disabled={submitting}
                  />
                </div>
                <div className="space-y-2">
                  <Label>Candidate Groups</Label>
                  <div className="flex gap-2">
                    <Input
                      placeholder="e.g., IT_APPROVERS"
                      value={formState.candidateGroupInput}
                      onChange={e => setFormState(prev => ({ ...prev, candidateGroupInput: e.target.value }))}
                      onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleAddGroup() } }}
                      disabled={submitting}
                    />
                    <Button type="button" variant="outline" onClick={handleAddGroup} disabled={submitting}>
                      Add
                    </Button>
                  </div>
                  {formState.candidateGroups.length > 0 && (
                    <div className="flex flex-wrap gap-1 pt-1">
                      {formState.candidateGroups.map(group => (
                        <Badge key={group} variant="secondary" className="gap-1">
                          {group}
                          <button
                            type="button"
                            onClick={() => handleRemoveGroup(group)}
                            className="ml-1 hover:text-destructive"
                            disabled={submitting}
                          >
                            <X className="h-3 w-3" />
                          </button>
                        </Badge>
                      ))}
                    </div>
                  )}
                </div>
                {formError && (
                  <div className="bg-destructive/10 border border-destructive text-destructive text-sm p-3 rounded">
                    {formError}
                  </div>
                )}
                <div className="flex gap-3 justify-end pt-4">
                  <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={submitting}>
                    {t('cancel')}
                  </Button>
                  <Button onClick={handleSave} disabled={submitting}>
                    {t('save')}
                  </Button>
                </div>
              </div>
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
              <p className="text-destructive font-semibold">Error loading custody mappings</p>
              <p className="text-sm text-muted-foreground">{(error as Error).message}</p>
              <Button variant="outline" size="sm" onClick={() => refetch()}>
                Retry
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && mappings.length === 0 && (
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <p className="text-muted-foreground">{t('noMappings')}</p>
            <Button variant="link" className="mt-2" onClick={() => setDialogOpen(true)}>
              Create the first mapping
            </Button>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && mappings.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Mappings</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-semibold">Custody Owner</th>
                    <th className="text-left py-3 px-4 font-semibold">Candidate Groups</th>
                    <th className="text-right py-3 px-4 font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {mappings.map((mapping) => (
                    <tr key={mapping.id} className="border-b hover:bg-muted/50 transition-colors">
                      <td className="py-3 px-4">
                        <code className="bg-muted px-2 py-1 rounded text-xs">{mapping.custodyOwner}</code>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {mapping.candidateGroups.map(g => (
                            <Badge key={g} variant="secondary">{g}</Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex gap-2 justify-end">
                          <Button variant="ghost" size="sm" onClick={() => handleEdit(mapping)}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setDeleteTarget(mapping.id)
                              setDeleteDialogOpen(true)
                            }}
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        title={t('delete')}
        description="Are you sure you want to delete this custody mapping? This action cannot be undone."
        confirmLabel={t('delete')}
        cancelLabel={t('cancel')}
        variant="destructive"
        onConfirm={handleDeleteConfirm}
      />
    </div>
  )
}
