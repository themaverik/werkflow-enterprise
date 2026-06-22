'use client'

import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { toast } from 'sonner'
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
import { EmptyState } from '@/components/ui/empty-state'
import { Plus, RefreshCw, Building2, CheckCircle2, Layers, Info, Pencil, Trash2 } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

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

interface UserResponse {
  id: number
  keycloakId: string
  username: string
  email: string
  firstName: string
  lastName: string
  phone?: string
  mobile?: string
  organizationId: number
  organizationName?: string
  jobTitle?: string
  employeeId?: string
  managerId?: number
  hireDate?: string
  address?: string
  city?: string
  state?: string
  country?: string
  postalCode?: string
  roles?: Array<{ id: number; name: string; description?: string }>
  active?: boolean
  emailVerified?: boolean
  tenantCode?: string
  doaLevel?: number
}

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

  const [adminUser, setAdminUser] = useState<UserResponse | null>(null)
  const [adminFirstName, setAdminFirstName] = useState('')
  const [adminLastName, setAdminLastName] = useState('')
  const [adminLoading, setAdminLoading] = useState(false)
  const [adminFetchError, setAdminFetchError] = useState<string | null>(null)

  useEffect(() => {
    if (tenant) {
      setName(tenant.name)
      setActive(tenant.active)
      setError(null)
    }
  }, [tenant])

  useEffect(() => {
    if (!open || !tenant) return

    setAdminUser(null)
    setAdminFirstName('')
    setAdminLastName('')
    setAdminFetchError(null)
    setAdminLoading(true)

    async function fetchAdminUser() {
      try {
        const orgRes = await fetch(`/api/proxy/admin/organizations/by-tenant/${tenant!.tenantCode}`)
        if (orgRes.status === 404) {
          // No org provisioned yet for this tenant — treat as no admin user, not an error
          return
        }
        if (!orgRes.ok) {
          setAdminFetchError(`Could not load organization (${orgRes.status})`)
          return
        }
        const org: { id: number } = await orgRes.json()

        const usersRes = await fetch(`/api/proxy/admin/users/organization/${org.id}`)
        if (!usersRes.ok) {
          setAdminFetchError(`Could not load users (${usersRes.status})`)
          return
        }
        const users: UserResponse[] = await usersRes.json()

        const admin = users.find((u) =>
          u.roles?.some((r) => r.name.toUpperCase() === 'ADMIN')
        ) ?? null

        setAdminUser(admin)
        if (admin) {
          setAdminFirstName(admin.firstName)
          setAdminLastName(admin.lastName)
        }
      } catch (err) {
        setAdminFetchError(err instanceof Error ? err.message : 'Unexpected error loading admin user')
      } finally {
        setAdminLoading(false)
      }
    }

    fetchAdminUser()
  }, [open, tenant])

  function handleDialogOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      setAdminUser(null)
      setAdminFirstName('')
      setAdminLastName('')
      setAdminLoading(false)
      setAdminFetchError(null)
    }
    onOpenChange(nextOpen)
  }

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

      const firstNameChanged = adminUser && adminFirstName !== adminUser.firstName
      const lastNameChanged = adminUser && adminLastName !== adminUser.lastName
      if (adminUser && (firstNameChanged || lastNameChanged)) {
        try {
          const userRes = await fetch(`/api/proxy/admin/users/${adminUser.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              ...adminUser,
              firstName: adminFirstName,
              lastName: adminLastName,
            }),
          })
          if (!userRes.ok) {
            toast.warning('Tenant saved, but admin user name could not be updated.', {
              description: `User update failed (${userRes.status}). Edit the user directly in Tenant Setup.`,
            })
          }
        } catch (userErr) {
          toast.warning('Tenant saved, but admin user name could not be updated.', {
            description: userErr instanceof Error ? userErr.message : 'Unexpected error',
          })
        }
      }

      onSaved()
      handleDialogOpenChange(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unexpected error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleDialogOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Edit Tenant</DialogTitle>
          <DialogDescription>
            Update the tenant name or active status. Tenant code cannot be changed.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="space-y-1.5">
            <Label className="text-muted-foreground text-xs">Tenant Code</Label>
            <div className="flex items-center h-9 w-full rounded-md border border-input bg-muted px-3 py-1 text-sm text-muted-foreground select-none">
              <code>{tenant?.tenantCode}</code>
            </div>
            <p className="text-xs text-muted-foreground">Permanent — cannot be changed after creation.</p>
          </div>
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

          <div className="border-t border-border pt-4 space-y-3">
            <p className="text-sm font-semibold">Admin User</p>
            {adminLoading && (
              <div className="space-y-2">
                <Skeleton className="h-9 w-full rounded-md" />
                <Skeleton className="h-9 w-full rounded-md" />
                <Skeleton className="h-9 w-full rounded-md" />
              </div>
            )}
            {!adminLoading && adminFetchError && (
              <p className="text-xs text-destructive">{adminFetchError}</p>
            )}
            {!adminLoading && !adminFetchError && !adminUser && (
              <p className="text-xs text-muted-foreground">No admin user found for this tenant.</p>
            )}
            {!adminLoading && !adminFetchError && adminUser && (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <Label className="text-muted-foreground text-xs">Email</Label>
                  <div className="flex items-center h-9 w-full rounded-md border border-input bg-muted px-3 py-1 text-sm text-muted-foreground select-none">
                    {adminUser.email}
                  </div>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="edit-admin-first-name">First Name</Label>
                  <Input
                    id="edit-admin-first-name"
                    value={adminFirstName}
                    onChange={(e) => setAdminFirstName(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="edit-admin-last-name">Last Name</Label>
                  <Input
                    id="edit-admin-last-name"
                    value={adminLastName}
                    onChange={(e) => setAdminLastName(e.target.value)}
                  />
                </div>
              </div>
            )}
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => handleDialogOpenChange(false)}>
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
  useEffect(() => {
    if (status !== 'loading' && !isSuperAdmin) {
      router.replace('/dashboard')
    }
  }, [status, isSuperAdmin, router])

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
        toast.error('Seeding failed', { description: `Could not seed examples for ${tenant.name} (${res.status})` })
        return
      }
      const data = await res.json()
      toast.success('Examples seeded', { description: `${data.deployed ?? 0} deployed, ${data.skipped ?? 0} skipped for ${tenant.name}` })
    } catch (err) {
      toast.error('Seeding failed', { description: err instanceof Error ? err.message : 'Unexpected error' })
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
        toast.error('Delete failed', { description: `Could not delete ${deleting.name} (${res.status})` })
        return
      }
      setDeleting(null)
      refetch()
    } catch (err) {
      toast.error('Delete failed', { description: err instanceof Error ? err.message : 'Unexpected error' })
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
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
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
          <EmptyState
            icon={Building2}
            title="No tenants yet"
            description="Create the first tenant to get started."
            action={
              <Button variant="outline" size="sm" asChild>
                <Link href="/admin/platform/tenants/new">
                  <Plus className="h-4 w-4 mr-2" />
                  New Tenant
                </Link>
              </Button>
            }
          />
        )}

        {!isLoading && !error && tenants && tenants.length > 0 && (
          <div className="rounded-xl border border-border overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tenant Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span className="flex items-center gap-1.5 cursor-default">
                            KC Status
                            <Info className="h-3.5 w-3.5 text-muted-foreground" />
                          </span>
                        </TooltipTrigger>
                        <TooltipContent className="max-w-[260px]">
                          <p className="font-semibold">Keycloak (KC)</p>
                          <p className="mt-1">is the identity provider that manages user authentication.</p>
                          <p className="mt-2 font-semibold">KC Ready</p>
                          <p className="mt-0.5">the tenant admin account has been created in Keycloak and can log in.</p>
                          <p className="mt-2 font-semibold">Pending KC</p>
                          <p className="mt-0.5">the admin account has not yet been provisioned in Keycloak (e.g. creation failed or is deferred).</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  </TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.map((tenant) => (
                  <TableRow key={tenant.id}>
                    <TableCell>
                      <code className="text-xs bg-muted px-1.5 py-0.5 rounded">{tenant.tenantCode}</code>
                    </TableCell>
                    <TableCell className="font-medium text-foreground">{tenant.name}</TableCell>
                    <TableCell>
                      <Badge variant={tenant.active ? 'default' : 'secondary'}>
                        {tenant.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {tenant.createdAt
                        ? new Date(tenant.createdAt).toLocaleDateString()
                        : '—'}
                    </TableCell>
                    <TableCell>
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
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center gap-1 justify-end">
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8"
                          aria-label={`Seed examples for ${tenant.name}`}
                          title={`Seed examples for ${tenant.name}`}
                          disabled={seedingId === tenant.id}
                          onClick={() => handleSeedExamples(tenant)}
                        >
                          {seedingId === tenant.id ? (
                            <RefreshCw className="h-4 w-4 animate-spin" />
                          ) : (
                            <Layers className="h-4 w-4" />
                          )}
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8"
                          aria-label={`Edit ${tenant.name}`}
                          title={`Edit ${tenant.name}`}
                          onClick={() => setEditing(tenant)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
                          aria-label={`Delete ${tenant.name}`}
                          title={`Delete ${tenant.name}`}
                          onClick={() => setDeleting(tenant)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
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
