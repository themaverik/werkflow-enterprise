'use client'

import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { useToast } from '@/hooks/use-toast'
import { PageSurface } from '@/components/layout/page-surface'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Plus, RefreshCw, Building2, CheckCircle2, Layers } from 'lucide-react'

interface TenantRow {
  id: number
  tenantCode: string
  name: string
  active: boolean
  createdAt: string
  keycloakProvisioned: boolean
}

async function fetchTenants(): Promise<TenantRow[]> {
  const res = await fetch('/api/proxy/admin/platform/tenants')
  if (!res.ok) throw new Error(`Failed to load tenants: ${res.status}`)
  return res.json()
}

// ---------------------------------------------------------------------------
// EditModal
// ---------------------------------------------------------------------------

interface EditModalProps {
  tenant: TenantRow | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}

function EditModal({ tenant, open, onOpenChange, onSaved }: EditModalProps) {
  const [name, setName] = useState('')
  const [active, setActive] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (tenant) {
      setName(tenant.name)
      setActive(tenant.active)
      setError(null)
    }
  }, [tenant])

  async function handleSave() {
    if (!tenant) return
    setSaving(true)
    setError(null)
    try {
      const res = await fetch(`/api/proxy/admin/platform/tenants/${tenant.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, active }),
      })
      if (!res.ok) {
        const text = await res.text()
        let msg = `Failed (${res.status})`
        try {
          msg = JSON.parse(text).message ?? msg
        } catch {
          // use default msg
        }
        setError(msg)
        return
      }
      onSaved()
      onOpenChange(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unexpected error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Edit Tenant</DialogTitle>
          <DialogDescription>
            Update the tenant name or active status. Tenant code cannot be changed.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="space-y-1.5">
            <Label htmlFor="edit-name">Name</Label>
            <Input
              id="edit-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-active">Status</Label>
            <Select
              value={active ? 'active' : 'inactive'}
              onValueChange={(val) => setActive(val === 'active')}
            >
              <SelectTrigger id="edit-active">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="active">Active</SelectItem>
                <SelectItem value="inactive">Inactive</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving && <RefreshCw className="h-4 w-4 mr-2 animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ---------------------------------------------------------------------------
// TenantsPage
// ---------------------------------------------------------------------------

export default function TenantsPage() {
  const { status } = useSession()
  const { hasRole } = useAuthorization()
  const router = useRouter()

  const isSuperAdmin = hasRole('SUPER_ADMIN')
  const { toast } = useToast()

  useEffect(() => {
    if (!isSuperAdmin) {
      router.replace('/dashboard')
    }
  }, [isSuperAdmin, router])

  const { data: tenants, isLoading, error, refetch } = useQuery({
    queryKey: ['platform-tenants'],
    queryFn: fetchTenants,
    enabled: status === 'authenticated' && isSuperAdmin,
    retry: false,
  })

  const [editing, setEditing] = useState<TenantRow | null>(null)
  const [deleting, setDeleting] = useState<TenantRow | null>(null)
  const [seedingId, setSeedingId] = useState<number | null>(null)

  if (!isSuperAdmin) {
    return null
  }

  async function handleSeedExamples(tenant: TenantRow) {
    setSeedingId(tenant.id)
    try {
      const res = await fetch(`/api/proxy/admin/platform/tenants/${tenant.id}/seed-examples`, {
        method: 'POST',
      })
      if (!res.ok) {
        toast({
          title: 'Seeding failed',
          description: `Could not seed examples for ${tenant.name} (${res.status})`,
          variant: 'destructive',
        })
        return
      }
      const data = await res.json()
      toast({
        title: 'Examples seeded',
        description: `${data.deployed ?? 0} deployed, ${data.skipped ?? 0} skipped for ${tenant.name}`,
      })
    } catch (err) {
      toast({
        title: 'Seeding failed',
        description: err instanceof Error ? err.message : 'Unexpected error',
        variant: 'destructive',
      })
    } finally {
      setSeedingId(null)
    }
  }

  async function handleDelete() {
    if (!deleting) return
    try {
      const res = await fetch(`/api/proxy/admin/platform/tenants/${deleting.id}`, {
        method: 'DELETE',
      })
      if (!res.ok) {
        toast({
          title: 'Delete failed',
          description: `Could not delete ${deleting.name} (${res.status})`,
          variant: 'destructive',
        })
        return
      }
      setDeleting(null)
      refetch()
    } catch (err) {
      toast({
        title: 'Delete failed',
        description: err instanceof Error ? err.message : 'Unexpected error',
        variant: 'destructive',
      })
    }
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
          <div className="rounded-xl border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/40">
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Tenant Code</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Name</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Status</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Created</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">KC Status</th>
                  <th className="px-4 py-3 font-semibold text-muted-foreground text-right">Actions</th>
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
                    <td className="px-4 py-3">
                      {tenant.keycloakProvisioned ? (
                        <span className="inline-flex items-center gap-1 text-xs text-emerald-700">
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          KC Ready
                        </span>
                      ) : (
                        <Badge variant="outline" className="text-xs text-amber-600 border-amber-300 bg-amber-50">
                          Pending KC
                        </Badge>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 text-xs"
                          aria-label={`Seed examples for ${tenant.name}`}
                          title={`Seed examples for ${tenant.name}`}
                          disabled={seedingId === tenant.id}
                          onClick={() => handleSeedExamples(tenant)}
                        >
                          {seedingId === tenant.id ? (
                            <RefreshCw className="h-3 w-3 animate-spin" />
                          ) : (
                            <Layers className="h-3 w-3" />
                          )}
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 text-xs"
                          aria-label={`Edit ${tenant.name}`}
                          onClick={() => setEditing(tenant)}
                        >
                          Edit
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 text-xs text-destructive hover:text-destructive"
                          aria-label={`Delete ${tenant.name}`}
                          onClick={() => setDeleting(tenant)}
                        >
                          Delete
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <EditModal
          tenant={editing}
          open={editing !== null}
          onOpenChange={(open) => { if (!open) setEditing(null) }}
          onSaved={() => { refetch(); setEditing(null) }}
        />

        <ConfirmDialog
          open={deleting !== null}
          onOpenChange={(open) => { if (!open) setDeleting(null) }}
          title="Delete tenant?"
          description={
            deleting
              ? `This permanently deletes ${deleting.name} (${deleting.tenantCode}). Any associated process data in the engine will be orphaned. This cannot be undone.`
              : ''
          }
          confirmLabel="Delete"
          variant="destructive"
          onConfirm={handleDelete}
        />
      </div>
    </PageSurface>
  )
}
