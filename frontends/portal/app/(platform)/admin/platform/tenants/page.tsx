'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { PageSurface } from '@/components/layout/page-surface'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Plus, RefreshCw, Building2 } from 'lucide-react'

interface TenantRow {
  id: number
  tenantCode: string
  name: string
  active: boolean
  createdAt: string
}

async function fetchTenants(): Promise<TenantRow[]> {
  const res = await fetch('/api/proxy/admin/platform/tenants')
  if (!res.ok) throw new Error(`Failed to load tenants: ${res.status}`)
  return res.json()
}

export default function TenantsPage() {
  const { status } = useSession()
  const { hasRole } = useAuthorization()
  const router = useRouter()

  const isSuperAdmin = hasRole('SUPER_ADMIN')

  const { data: tenants, isLoading, error, refetch } = useQuery({
    queryKey: ['platform-tenants'],
    queryFn: fetchTenants,
    enabled: status === 'authenticated' && isSuperAdmin,
    retry: false,
  })

  if (!isSuperAdmin) {
    router.replace('/dashboard')
    return null
  }

  return (
    <PageSurface>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Tenants</h1>
            <p className="text-muted-foreground mt-1">Manage platform tenants and provision new ones.</p>
          </div>
          <div className="flex gap-2">
            <Button onClick={() => refetch()} variant="outline" disabled={isLoading}>
              <RefreshCw className={`h-4 w-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            <Button asChild>
              <Link href="/admin/platform/tenants/new">
                <Plus className="h-4 w-4 mr-2" />
                New Tenant
              </Link>
            </Button>
          </div>
        </div>

        {isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-16 rounded-xl border border-border bg-card animate-pulse" />
            ))}
          </div>
        )}

        {!isLoading && error && (
          <Card className="border-destructive/40 bg-destructive/5">
            <CardContent className="pt-5 pb-5">
              <p className="text-sm font-semibold text-destructive">Unable to load tenants</p>
              <p className="text-sm text-muted-foreground mt-1">
                The admin service may be temporarily unavailable.
              </p>
            </CardContent>
          </Card>
        )}

        {!isLoading && !error && tenants?.length === 0 && (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16 text-center">
              <Building2 className="h-10 w-10 text-muted-foreground mb-3" strokeWidth={1.5} />
              <p className="text-sm font-medium text-foreground mb-1">No tenants yet</p>
              <p className="text-xs text-muted-foreground mb-4">
                Create the first tenant to get started.
              </p>
              <Button variant="outline" size="sm" asChild>
                <Link href="/admin/platform/tenants/new">
                  <Plus className="h-4 w-4 mr-2" />
                  New Tenant
                </Link>
              </Button>
            </CardContent>
          </Card>
        )}

        {!isLoading && !error && tenants && tenants.length > 0 && (
          <div className="rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/40">
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Tenant Code</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Name</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Status</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Created</th>
                </tr>
              </thead>
              <tbody>
                {tenants.map((tenant) => (
                  <tr key={tenant.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3">
                      <code className="text-xs bg-muted px-1.5 py-0.5 rounded">{tenant.tenantCode}</code>
                    </td>
                    <td className="px-4 py-3 font-medium text-foreground">{tenant.name}</td>
                    <td className="px-4 py-3">
                      <Badge variant={tenant.active ? 'default' : 'secondary'}>
                        {tenant.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {tenant.createdAt
                        ? new Date(tenant.createdAt).toLocaleDateString()
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </PageSurface>
  )
}
