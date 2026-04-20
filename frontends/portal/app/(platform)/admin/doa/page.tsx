'use client'

import { useState, useEffect } from 'react'
import { useSession } from 'next-auth/react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { RefreshCw, Pencil, Check, X } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { useTranslations } from 'next-intl'
import {
  getDoaThresholds,
  updateDoaThreshold,
  type DoaThreshold,
  type DoaThresholdUpdate,
} from '@/lib/api/adminTenantApi'

const CURRENCY_OPTIONS = [
  { value: 'USD', label: 'US Dollar (USD)' },
  { value: 'INR', label: 'Indian Rupee (INR)' },
  { value: 'AUD', label: 'Australian Dollar (AUD)' },
  { value: 'EUR', label: 'Euro (EUR)' },
  { value: 'GBP', label: 'British Pound (GBP)' },
  { value: 'SEK', label: 'Swedish Krona (SEK)' },
]

interface EditingRow {
  id: number
  label: string
  description: string
  maxAmount: string
  currency: string
}

export default function DoaConfigPage() {
  const t = useTranslations('admin.doa')
  const { status } = useSession()
  const { toast } = useToast()
  const [thresholds, setThresholds] = useState<DoaThreshold[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingRow, setEditingRow] = useState<EditingRow | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const loadThresholds = async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await getDoaThresholds()
      setThresholds(data)
    } catch (err: any) {
      setError(err.message || 'Failed to load DoA thresholds')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (status === 'authenticated') {
      loadThresholds()
    }
  }, [status])

  const startEdit = (row: DoaThreshold) => {
    setEditingRow({
      id: row.id,
      label: row.label ?? '',
      description: row.description ?? '',
      maxAmount: row.maxAmount !== null ? String(row.maxAmount) : '',
      currency: row.currency,
    })
  }

  const cancelEdit = () => setEditingRow(null)

  const saveEdit = async () => {
    if (!editingRow) return
    setIsSaving(true)
    try {
      const update: DoaThresholdUpdate = {
        label: editingRow.label || null,
        description: editingRow.description || null,
        maxAmount: editingRow.maxAmount === '' ? null : Number(editingRow.maxAmount),
        currency: editingRow.currency,
      }
      const updated = await updateDoaThreshold(editingRow.id, update)
      setThresholds(prev => prev.map(t => t.id === updated.id ? updated : t))
      setEditingRow(null)
      toast({ title: t('thresholdUpdated'), description: t('thresholdSaved', { level: updated.doaLevel }) })
    } catch (err: any) {
      toast({ title: t('saveFailed'), description: err.message, variant: 'destructive' })
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground mt-1">{t('subtitle')}</p>
        </div>
        <Button onClick={loadThresholds} variant="outline" disabled={isLoading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
          {t('refresh')}
        </Button>
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
              <p className="text-sm text-muted-foreground">{error}</p>
              <Button variant="outline" size="sm" onClick={loadThresholds}>
                {t('retry')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && thresholds.length === 0 && (
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <p className="text-muted-foreground">{t('noThresholds')}</p>
          </CardContent>
        </Card>
      )}

      {!isLoading && !error && thresholds.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>{t('thresholds')}</CardTitle>
            <CardDescription>{t('thresholdsDesc')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('level')}</th>
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('label')}</th>
                    <th className="text-right py-3 px-4 font-medium text-muted-foreground">{t('maxAmount')}</th>
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('currency')}</th>
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('description')}</th>
                    <th className="py-3 px-4" />
                  </tr>
                </thead>
                <tbody>
                  {thresholds.map((threshold) => {
                    const isEditing = editingRow?.id === threshold.id
                    return (
                      <tr key={threshold.id} className="border-b last:border-0 hover:bg-muted/30">
                        <td className="py-3 px-4 font-medium">{threshold.doaLevel}</td>
                        {isEditing && editingRow ? (
                          <>
                            <td className="py-2 px-4">
                              <Input
                                value={editingRow.label}
                                onChange={e => setEditingRow({ ...editingRow, label: e.target.value })}
                                className="h-8 text-sm"
                                placeholder="Label"
                              />
                            </td>
                            <td className="py-2 px-4">
                              <Input
                                value={editingRow.maxAmount}
                                onChange={e => setEditingRow({ ...editingRow, maxAmount: e.target.value })}
                                className="h-8 text-sm text-right"
                                placeholder="Unlimited"
                                type="number"
                                min="0"
                              />
                            </td>
                            <td className="py-2 px-4">
                              <Select value={editingRow.currency} onValueChange={value => setEditingRow({ ...editingRow, currency: value })}>
                                <SelectTrigger className="h-8 text-sm w-28">
                                  <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                  {CURRENCY_OPTIONS.map(option => (
                                    <SelectItem key={option.value} value={option.value}>
                                      {option.label}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </td>
                            <td className="py-2 px-4">
                              <Input
                                value={editingRow.description}
                                onChange={e => setEditingRow({ ...editingRow, description: e.target.value })}
                                className="h-8 text-sm"
                                placeholder="Description"
                              />
                            </td>
                            <td className="py-2 px-4">
                              <div className="flex gap-1">
                                <Button size="sm" variant="default" onClick={saveEdit} disabled={isSaving} className="h-8 px-2">
                                  <Check className="h-3.5 w-3.5" />
                                </Button>
                                <Button size="sm" variant="outline" onClick={cancelEdit} disabled={isSaving} className="h-8 px-2">
                                  <X className="h-3.5 w-3.5" />
                                </Button>
                              </div>
                            </td>
                          </>
                        ) : (
                          <>
                            <td className="py-3 px-4">{threshold.label ?? '—'}</td>
                            <td className="py-3 px-4 text-right">
                              {threshold.maxAmount !== null
                                ? threshold.maxAmount.toLocaleString()
                                : t('unlimited')}
                            </td>
                            <td className="py-3 px-4">{threshold.currency}</td>
                            <td className="py-3 px-4 text-muted-foreground">
                              {threshold.description ?? '—'}
                            </td>
                            <td className="py-3 px-4">
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => startEdit(threshold)}
                                disabled={!!editingRow}
                                className="h-8 px-2"
                              >
                                <Pencil className="h-3.5 w-3.5" />
                              </Button>
                            </td>
                          </>
                        )}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
