'use client'

import { useState, useEffect } from 'react'
import { useSession } from 'next-auth/react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { RefreshCw, Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import {
  getDepartments,
  createDepartment,
  type Department,
  type DepartmentRequest,
} from '@/lib/api/adminTenantApi'

const EMPTY_FORM: DepartmentRequest = {
  name: '',
  code: '',
  description: '',
  tenantCode: 'default',
  organizationId: 1,
}

/**
 * Departments Management Page
 *
 * Lists all departments for the current tenant and allows administrators
 * to add new departments.
 */
export default function DepartmentsPage() {
  const t = useTranslations('admin.departments')
  const { status } = useSession()
  const [departments, setDepartments] = useState<Department[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<DepartmentRequest>(EMPTY_FORM)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const loadDepartments = async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await getDepartments()
      setDepartments(data)
    } catch (err: any) {
      setError(err.message || 'Failed to load departments')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (status === 'authenticated') {
      loadDepartments()
    }
  }, [status])

  const handleFieldChange = (field: keyof DepartmentRequest, value: string | number) => {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)

    if (!form.name.trim()) {
      setFormError('Department name is required')
      return
    }
    if (!form.code.trim()) {
      setFormError('Department code is required')
      return
    }

    setIsSubmitting(true)
    try {
      const created = await createDepartment(form)
      setDepartments(prev => [...prev, created])
      setForm(EMPTY_FORM)
      setShowForm(false)
    } catch (err: any) {
      setFormError(err.message || 'Failed to create department')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleCancelForm = () => {
    setForm(EMPTY_FORM)
    setFormError(null)
    setShowForm(false)
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground mt-1">{t('subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={loadDepartments} variant="outline" disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            {t('refresh')}
          </Button>
          {!showForm && (
            <Button onClick={() => setShowForm(true)}>
              <Plus className="h-4 w-4 mr-2" />
              {t('addDepartment')}
            </Button>
          )}
        </div>
      </div>

      {/* Add department form */}
      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle>{t('newDepartment')}</CardTitle>
            <CardDescription>{t('newDepartmentDesc')}</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="dept-name">{t('name')}</Label>
                  <Input
                    id="dept-name"
                    placeholder={t('namePlaceholder')}
                    value={form.name}
                    onChange={(e) => handleFieldChange('name', e.target.value)}
                    disabled={isSubmitting}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="dept-code">{t('code')}</Label>
                  <Input
                    id="dept-code"
                    placeholder={t('codePlaceholder')}
                    value={form.code}
                    onChange={(e) => handleFieldChange('code', e.target.value.toUpperCase())}
                    disabled={isSubmitting}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="dept-description">{t('description')}</Label>
                <Input
                  id="dept-description"
                  placeholder={t('descriptionPlaceholder')}
                  value={form.description ?? ''}
                  onChange={(e) => handleFieldChange('description', e.target.value)}
                  disabled={isSubmitting}
                />
              </div>

              {formError && (
                <p className="text-sm text-destructive">{formError}</p>
              )}

              <div className="flex gap-2">
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <>
                      <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                      {t('saving')}
                    </>
                  ) : (
                    t('save')
                  )}
                </Button>
                <Button type="button" variant="outline" onClick={handleCancelForm} disabled={isSubmitting}>
                  {t('cancel')}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Loading state */}
      {isLoading && (
        <div className="text-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin mx-auto text-muted-foreground" />
          <p className="text-muted-foreground mt-2">{t('loading')}</p>
        </div>
      )}

      {/* Error state */}
      {!isLoading && error && (
        <Card className="border-destructive">
          <CardContent className="pt-6">
            <div className="space-y-3">
              <p className="text-destructive font-semibold">{t('error')}</p>
              <p className="text-sm text-muted-foreground">{error}</p>
              <Button variant="outline" size="sm" onClick={loadDepartments}>
                {t('retry')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Empty state */}
      {!isLoading && !error && departments.length === 0 && (
        <Card>
          <CardContent className="pt-6 text-center py-12">
            <p className="text-muted-foreground">{t('noDepartments')}</p>
            {!showForm && (
              <Button variant="link" className="mt-2" onClick={() => setShowForm(true)}>
                Add the first department
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      {/* Departments table */}
      {!isLoading && !error && departments.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>{t('allDepartments')}</CardTitle>
            <CardDescription>{departments.length} department{departments.length !== 1 ? 's' : ''} configured</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('name')}</th>
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('code')}</th>
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('description')}</th>
                    <th className="text-left py-3 px-4 font-medium text-muted-foreground">{t('status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {departments.map((dept) => (
                    <tr key={dept.id} className="border-b last:border-0 hover:bg-muted/30">
                      <td className="py-3 px-4 font-medium">{dept.name}</td>
                      <td className="py-3 px-4">
                        <code className="text-xs bg-muted px-2 py-1 rounded">{dept.code}</code>
                      </td>
                      <td className="py-3 px-4 text-muted-foreground">
                        {dept.description ?? '—'}
                      </td>
                      <td className="py-3 px-4">
                        {dept.active ? (
                          <Badge variant="success">Active</Badge>
                        ) : (
                          <Badge variant="secondary">Inactive</Badge>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
