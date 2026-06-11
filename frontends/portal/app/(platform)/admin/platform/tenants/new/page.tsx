'use client'

import { useState, useEffect } from 'react'
import { useSession } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { PageSurface } from '@/components/layout/page-surface'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ArrowLeft, RefreshCw } from 'lucide-react'
import Link from 'next/link'

interface FormState {
  tenantCode: string
  name: string
  adminEmail: string
  adminFirstName: string
  adminLastName: string
  seedExamples: boolean
}

const EMPTY_FORM: FormState = {
  tenantCode: '',
  name: '',
  adminEmail: '',
  adminFirstName: '',
  adminLastName: '',
  seedExamples: true,
}

export default function NewTenantPage() {
  const router = useRouter()
  const { status } = useSession()
  const { hasRole } = useAuthorization()
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isSuperAdmin = hasRole('SUPER_ADMIN')

  useEffect(() => {
    if (status !== 'loading' && !isSuperAdmin) {
      router.replace('/dashboard')
    }
  }, [status, isSuperAdmin, router])

  if (!isSuperAdmin) {
    return null
  }

  function handleChange(field: keyof FormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      const res = await fetch('/api/proxy/admin/platform/tenants', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })

      if (!res.ok) {
        const text = await res.text()
        let message = `Request failed (${res.status})`
        try {
          const json = JSON.parse(text)
          message = json.message ?? json.error ?? message
        } catch {
          // use default message
        }
        setError(message)
        return
      }

      router.push('/admin/platform/tenants')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An unexpected error occurred')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PageSurface>
      <div className="max-w-lg space-y-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" asChild>
            <Link href="/admin/platform/tenants">
              <ArrowLeft className="h-4 w-4" />
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-foreground">New Tenant</h1>
            <p className="text-muted-foreground mt-0.5 text-sm">
              Provision a new tenant and send an invite to the admin.
            </p>
          </div>
        </div>

        <Card>
          <CardHeader className="pb-4">
            <CardTitle className="text-base">Tenant Details</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="tenantCode">Tenant Code</Label>
                <Input
                  id="tenantCode"
                  placeholder="e.g. acme-corp"
                  value={form.tenantCode}
                  onChange={(e) => handleChange('tenantCode', e.target.value)}
                  required
                />
                <p className="text-xs text-muted-foreground">
                  URL-safe slug, unique across the platform.
                </p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="name">Tenant Name</Label>
                <Input
                  id="name"
                  placeholder="e.g. Acme Corporation"
                  value={form.name}
                  onChange={(e) => handleChange('name', e.target.value)}
                  required
                />
              </div>

              <div className="border-t border-border pt-4">
                <p className="text-sm font-medium text-foreground mb-3">Admin User</p>
                <div className="space-y-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="adminEmail">Email</Label>
                    <Input
                      id="adminEmail"
                      type="email"
                      placeholder="admin@acme-corp.com"
                      value={form.adminEmail}
                      onChange={(e) => handleChange('adminEmail', e.target.value)}
                      required
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                      <Label htmlFor="adminFirstName">First Name</Label>
                      <Input
                        id="adminFirstName"
                        placeholder="Jane"
                        value={form.adminFirstName}
                        onChange={(e) => handleChange('adminFirstName', e.target.value)}
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="adminLastName">Last Name</Label>
                      <Input
                        id="adminLastName"
                        placeholder="Smith"
                        value={form.adminLastName}
                        onChange={(e) => handleChange('adminLastName', e.target.value)}
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 pt-1">
                <Checkbox
                  id="seedExamples"
                  checked={form.seedExamples}
                  onCheckedChange={(checked: boolean | 'indeterminate') =>
                    setForm((prev) => ({ ...prev, seedExamples: checked === true }))
                  }
                />
                <Label htmlFor="seedExamples" className="font-normal cursor-pointer">
                  Seed starter content
                </Label>
              </div>
              <p className="text-xs text-muted-foreground -mt-1 pl-6">
                Deploys example workflows (Capex Approval, Leave Request) for this tenant.
              </p>

              {error && (
                <p className="text-sm text-destructive rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2">
                  {error}
                </p>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <Button variant="outline" type="button" asChild>
                  <Link href="/admin/platform/tenants">Cancel</Link>
                </Button>
                <Button type="submit" disabled={submitting}>
                  {submitting && <RefreshCw className="h-4 w-4 mr-2 animate-spin" />}
                  Create Tenant
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </PageSurface>
  )
}
