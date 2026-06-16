'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { toast } from 'sonner'
import { PageSurface } from '@/components/layout/page-surface'
import { EmptyState } from '@/components/ui/empty-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { MoreHorizontal, Plus, RefreshCw, User } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

interface UserRow {
  id: number
  keycloakId: string
  username: string
  email: string
  firstName: string
  lastName: string
  tenantCode: string
  doaLevel: number | null
  departmentCode: string | null
  active: boolean
  emailVerified: boolean
  roles: { name: string }[]
}

interface OrgInfo {
  id: number
  name: string
  tenantCode: string
}

interface InviteFormState {
  email: string
  firstName: string
  lastName: string
  roleName: string
  doaLevel: string
  departmentCode: string
}

interface InvitePayload {
  email: string
  firstName: string
  lastName: string
  roleName: string
  doaLevel?: number
  departmentCode?: string
}

interface EditModalProps {
  open: boolean
  onOpenChange: (v: boolean) => void
  target: UserRow | null
  onSave: (vars: {
    id: number
    firstName: string
    lastName: string
    doaLevel: string
    departmentCode: string
  }) => void
  saving: boolean
}

const INTERNAL_ROLES = new Set(['ENGINE_SERVICE'])
const ASSIGNABLE_ROLES_NON_SUPER = new Set(['SUPER_ADMIN'])

const EMPTY_FORM: InviteFormState = {
  email: '',
  firstName: '',
  lastName: '',
  roleName: 'EMPLOYEE',
  doaLevel: '',
  departmentCode: '',
}

async function fetchOrg(tenantCode: string): Promise<OrgInfo> {
  const res = await fetch(`/api/proxy/admin/organizations/by-tenant/${tenantCode}`)
  if (!res.ok) throw new Error(`Failed to load organisation: ${res.status}`)
  return res.json()
}

async function fetchUsers(orgId: number): Promise<UserRow[]> {
  const res = await fetch(`/api/proxy/admin/users/organization/${orgId}`)
  if (!res.ok) throw new Error(`Failed to load users: ${res.status}`)
  return res.json()
}

async function fetchRealmRoles(): Promise<string[]> {
  const res = await fetch('/api/proxy/admin/keycloak/realm-roles')
  if (!res.ok) throw new Error(`Failed to load roles: ${res.status}`)
  const data = await res.json() as { roles: unknown }
  if (!Array.isArray(data.roles)) throw new Error('Unexpected roles response shape')
  return (data.roles as string[]).map((r) => r.toUpperCase())
}

async function inviteUser(payload: InvitePayload): Promise<UserRow> {
  const res = await fetch('/api/proxy/admin/users/invite', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    const text = await res.text()
    let msg = `Failed (${res.status})`
    try {
      msg = (JSON.parse(text) as { message?: string }).message ?? msg
    } catch {
      // use default msg
    }
    throw new Error(msg)
  }
  return res.json()
}

function EditUserModal({ open, onOpenChange, target, onSave, saving }: EditModalProps) {
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [doaLevel, setDoaLevel] = useState('')
  const [departmentCode, setDepartmentCode] = useState('')

  useEffect(() => {
    if (target) {
      setFirstName(target.firstName ?? '')
      setLastName(target.lastName ?? '')
      setDoaLevel(target.doaLevel != null ? String(target.doaLevel) : '')
      setDepartmentCode(target.departmentCode ?? '')
    }
  }, [target])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Edit User</DialogTitle>
          <DialogDescription>{target?.email}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="edit-fn">First Name</Label>
              <Input
                id="edit-fn"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="edit-ln">Last Name</Label>
              <Input
                id="edit-ln"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="edit-doa">DOA Level</Label>
              <Input
                id="edit-doa"
                type="number"
                min={1}
                value={doaLevel}
                onChange={(e) => setDoaLevel(e.target.value)}
                placeholder="Optional"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="edit-dept">Department</Label>
              <Input
                id="edit-dept"
                value={departmentCode}
                onChange={(e) => setDepartmentCode(e.target.value)}
                placeholder="Optional"
              />
            </div>
          </div>
          <p className="text-xs text-muted-foreground border-t border-border pt-3">
            Role and email changes are managed in Keycloak Admin Console.
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>
            Cancel
          </Button>
          <Button
            disabled={saving || !firstName.trim() || !lastName.trim()}
            onClick={() =>
              target &&
              onSave({ id: target.id, firstName, lastName, doaLevel, departmentCode })
            }
          >
            {saving && <RefreshCw className="h-4 w-4 mr-2 animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default function TenantUsersPage() {
  const { user } = useAuth()
  const { hasRole } = useAuthorization()
  const isSuperAdmin = hasRole('SUPER_ADMIN')
  const tenantCode = user?.tenantId ?? ''
  const queryClient = useQueryClient()

  const [inviteOpen, setInviteOpen] = useState(false)
  const [form, setForm] = useState<InviteFormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<UserRow | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<UserRow | null>(null)

  const { data: org } = useQuery<OrgInfo>({
    queryKey: ['org', tenantCode],
    queryFn: () => fetchOrg(tenantCode),
    enabled: !!user && !!tenantCode,
  })

  const { data: users = [], isFetching, refetch } = useQuery<UserRow[]>({
    queryKey: ['users', org?.id],
    queryFn: () => fetchUsers(org!.id),
    enabled: !!org?.id,
  })

  const {
    data: allRoles = ['ADMIN', 'EMPLOYEE', 'WORKFLOW_ADMIN'],
    isPending: rolesLoading,
    isError: rolesError,
  } = useQuery<string[]>({
    queryKey: ['realmRoles'],
    queryFn: fetchRealmRoles,
    enabled: !!user,
    staleTime: 5 * 60_000,
    gcTime: 10 * 60_000,
    retry: 1,
  })

  // Values in INTERNAL_ROLES and ASSIGNABLE_ROLES_NON_SUPER must be uppercase;
  // fetchRealmRoles normalises all KC role names to uppercase before returning.
  const inviteRoles = allRoles.filter((r) => {
    if (INTERNAL_ROLES.has(r)) return false
    if (!isSuperAdmin && ASSIGNABLE_ROLES_NON_SUPER.has(r)) return false
    return true
  })

  const invite = useMutation({
    mutationFn: inviteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', org?.id] })
      toast.success('Invite sent', {
        description: `${form.email} will receive an email to set their password.`,
      })
      setInviteOpen(false)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e: unknown) => setFormError(e instanceof Error ? e.message : 'Unexpected error'),
  })

  const statusMutation = useMutation({
    mutationFn: (vars: { id: number; active: boolean }) =>
      fetch(`/api/proxy/admin/users/${vars.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active: vars.active }),
      }).then(async (res) => {
        if (!res.ok) {
          const data = await res.json().catch(() => ({}))
          throw new Error((data as { message?: string }).message ?? `Failed (${res.status})`)
        }
        return res.json()
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', org?.id] })
      toast.success('User status updated')
    },
    onError: (e: unknown) =>
      toast.error(e instanceof Error ? e.message : 'Failed to update status'),
  })

  const editMutation = useMutation({
    mutationFn: (vars: {
      id: number
      firstName: string
      lastName: string
      doaLevel: string
      departmentCode: string
    }) =>
      fetch(`/api/proxy/admin/users/${vars.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          firstName: vars.firstName,
          lastName: vars.lastName,
          doaLevel: vars.doaLevel ? parseInt(vars.doaLevel, 10) : null,
          departmentCode: vars.departmentCode || null,
        }),
      }).then(async (res) => {
        if (!res.ok) {
          const data = await res.json().catch(() => ({}))
          throw new Error((data as { message?: string }).message ?? `Failed (${res.status})`)
        }
        return res.json()
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', org?.id] })
      toast.success('User updated')
      setEditOpen(false)
      setEditTarget(null)
    },
    onError: (e: unknown) =>
      toast.error(e instanceof Error ? e.message : 'Failed to update user'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      fetch(`/api/proxy/admin/users/${id}`, { method: 'DELETE' }).then(async (res) => {
        if (!res.ok) {
          const data = await res.json().catch(() => ({}))
          throw new Error((data as { message?: string }).message ?? `Failed (${res.status})`)
        }
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', org?.id] })
      toast.success('User deleted.')
      setDeleteTarget(null)
    },
    onError: (e: unknown) => {
      toast.error(e instanceof Error ? e.message : 'Failed to delete user')
      setDeleteTarget(null)
    },
  })

  const resendInviteMutation = useMutation({
    mutationFn: (id: number) =>
      fetch(`/api/proxy/admin/users/${id}/resend-invite`, { method: 'POST' }).then(async (res) => {
        if (!res.ok) {
          const data = await res.json().catch(() => ({}))
          throw new Error((data as { message?: string }).message ?? `Failed (${res.status})`)
        }
      }),
    onSuccess: () => toast.success('Invite resent'),
    onError: (e: unknown) =>
      toast.error(e instanceof Error ? e.message : 'Failed to resend invite'),
  })

  function handleInvite() {
    setFormError(null)
    if (!form.firstName.trim() || !form.lastName.trim()) {
      setFormError('First name and last name are required.')
      return
    }
    if (!form.email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      setFormError('A valid email address is required.')
      return
    }
    if (inviteRoles.length > 0 && !inviteRoles.includes(form.roleName)) {
      setFormError('Selected role is not available. Please choose a valid role.')
      return
    }
    const doaRaw = form.doaLevel.trim()
    const doa = doaRaw ? parseInt(doaRaw, 10) : null
    if (doa !== null && (isNaN(doa) || doa < 1)) {
      setFormError('DOA Level must be a positive number.')
      return
    }
    invite.mutate({
      email: form.email,
      firstName: form.firstName,
      lastName: form.lastName,
      roleName: form.roleName,
      doaLevel: doa ?? undefined,
      departmentCode: form.departmentCode || undefined,
    })
  }

  return (
    <PageSurface>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Users</h1>
            <p className="text-muted-foreground mt-1">Manage users for this tenant.</p>
          </div>
          <div className="flex gap-2">
            <Button onClick={() => refetch()} variant="outline" disabled={isFetching}>
              <RefreshCw className={`h-4 w-4 mr-2 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            <Button onClick={() => setInviteOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              Invite User
            </Button>
          </div>
        </div>

        {isFetching && users.length === 0 && (
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-12 rounded-lg" />
            ))}
          </div>
        )}

        {!isFetching && users.length === 0 && (
          <EmptyState
            icon={User}
            title="No users yet"
            description="Use Invite User to add members to this tenant."
          />
        )}

        {users.length > 0 && (
          <div className="rounded-xl border border-border overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Roles</TableHead>
                  <TableHead>DOA</TableHead>
                  <TableHead>Dept</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((u) => {
                  const isSelf = user?.username === u.keycloakId
                  return (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium text-foreground">
                      {u.firstName} {u.lastName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{u.email}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <Badge key={r.name} variant="secondary">
                            {r.name}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {u.doaLevel != null ? `L${u.doaLevel}` : '—'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {u.departmentCode ?? '—'}
                    </TableCell>
                    <TableCell>
                      {!u.emailVerified && u.active ? (
                        <Badge
                          variant="outline"
                          style={{
                            color: 'var(--badge-warning)',
                            borderColor: 'var(--badge-warning-border)',
                            backgroundColor: 'var(--badge-warning-bg)',
                          }}
                        >
                          Pending Invite
                        </Badge>
                      ) : u.active ? (
                        <Badge>Active</Badge>
                      ) : (
                        <Badge variant="secondary">Inactive</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                            <span className="sr-only">Actions</span>
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem
                            onClick={() => {
                              setEditTarget(u)
                              setEditOpen(true)
                            }}
                            disabled={editMutation.isPending}
                          >
                            Edit
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() =>
                              statusMutation.mutate({ id: u.id, active: !u.active })
                            }
                            disabled={statusMutation.isPending || isSelf}
                          >
                            {u.active ? 'Deactivate' : 'Activate'}
                          </DropdownMenuItem>
                          {!u.emailVerified && u.active && (
                            <DropdownMenuItem
                              onClick={() => resendInviteMutation.mutate(u.id)}
                              disabled={resendInviteMutation.isPending}
                            >
                              Resend Invite
                            </DropdownMenuItem>
                          )}
                          {isSuperAdmin && !isSelf && (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                className="text-destructive focus:text-destructive"
                                onClick={() => setDeleteTarget(u)}
                              >
                                Delete
                              </DropdownMenuItem>
                            </>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {/* Invite dialog */}
      <Dialog
        open={inviteOpen}
        onOpenChange={(open) => {
          setInviteOpen(open)
          if (!open) {
            setForm(EMPTY_FORM)
            setFormError(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite User</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            {formError && <p className="text-sm text-destructive">{formError}</p>}
            {rolesError && (
              <p className="text-xs text-muted-foreground">
                Role list unavailable — showing defaults.
              </p>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="invite-first-name">First Name</Label>
                <Input
                  id="invite-first-name"
                  value={form.firstName}
                  onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="invite-last-name">Last Name</Label>
                <Input
                  id="invite-last-name"
                  value={form.lastName}
                  onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="invite-email">Email</Label>
              <Input
                id="invite-email"
                type="email"
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="invite-role">Role</Label>
              <Select
                value={form.roleName}
                onValueChange={(v) => setForm((f) => ({ ...f, roleName: v }))}
                disabled={rolesLoading}
              >
                <SelectTrigger id="invite-role">
                  <SelectValue placeholder={rolesLoading ? 'Loading roles…' : undefined} />
                </SelectTrigger>
                <SelectContent>
                  {inviteRoles.map((r) => (
                    <SelectItem key={r} value={r}>
                      {r}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="invite-doa">DOA Level (optional)</Label>
                <Input
                  id="invite-doa"
                  type="number"
                  min={1}
                  max={4}
                  value={form.doaLevel}
                  onChange={(e) => setForm((f) => ({ ...f, doaLevel: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="invite-dept">Department (optional)</Label>
                <Input
                  id="invite-dept"
                  value={form.departmentCode}
                  onChange={(e) => setForm((f) => ({ ...f, departmentCode: e.target.value }))}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setInviteOpen(false)}
              disabled={invite.isPending}
            >
              Cancel
            </Button>
            <Button onClick={handleInvite} disabled={invite.isPending}>
              {invite.isPending ? 'Sending...' : 'Send Invite'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit dialog */}
      <EditUserModal
        open={editOpen}
        onOpenChange={(v) => {
          setEditOpen(v)
          if (!v) setEditTarget(null)
        }}
        target={editTarget}
        onSave={(vars) => editMutation.mutate(vars)}
        saving={editMutation.isPending}
      />

      {/* Delete confirmation dialog */}
      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(v) => {
          if (!v) setDeleteTarget(null)
        }}
        title={`Delete ${deleteTarget?.firstName ?? ''} ${deleteTarget?.lastName ?? ''}?`}
        description={`This removes the user from the system. Login access will also be revoked if the identity provider is reachable. This action cannot be undone.`}
        confirmLabel={deleteMutation.isPending ? 'Deleting...' : 'Delete'}
        variant="destructive"
        onConfirm={() => {
          if (deleteTarget) deleteMutation.mutate(deleteTarget.id)
        }}
      />
    </PageSurface>
  )
}
