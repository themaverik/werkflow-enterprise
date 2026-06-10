'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/lib/auth/auth-context'
import { useToast } from '@/hooks/use-toast'
import { PageSurface } from '@/components/layout/page-surface'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
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
import { Plus, RefreshCw, User } from 'lucide-react'

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

const ROLES = ['ADMIN', 'EMPLOYEE', 'WORKFLOW_ADMIN']

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

export default function TenantUsersPage() {
  const { user } = useAuth()
  const tenantCode = user?.tenantId ?? 'default'
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const [inviteOpen, setInviteOpen] = useState(false)
  const [form, setForm] = useState<InviteFormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)

  const { data: org } = useQuery<OrgInfo>({
    queryKey: ['org', tenantCode],
    queryFn: () => fetchOrg(tenantCode),
    enabled: !!tenantCode,
  })

  const { data: users = [], isFetching, refetch } = useQuery<UserRow[]>({
    queryKey: ['users', org?.id],
    queryFn: () => fetchUsers(org!.id),
    enabled: !!org?.id,
  })

  const invite = useMutation({
    mutationFn: inviteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', org?.id] })
      toast({
        title: 'Invite sent',
        description: `${form.email} will receive an email to set their password.`,
      })
      setInviteOpen(false)
      setForm(EMPTY_FORM)
      setFormError(null)
    },
    onError: (e: Error) => setFormError(e.message),
  })

  function handleInvite() {
    setFormError(null)
    invite.mutate({
      email: form.email,
      firstName: form.firstName,
      lastName: form.lastName,
      roleName: form.roleName,
      doaLevel: form.doaLevel ? parseInt(form.doaLevel, 10) : undefined,
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

        {!isFetching && users.length === 0 && (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16 text-center">
              <User className="h-10 w-10 text-muted-foreground mb-3" strokeWidth={1.5} />
              <p className="text-sm font-medium text-foreground mb-1">No users yet</p>
              <p className="text-xs text-muted-foreground mb-4">
                Use Invite User to add members to this tenant.
              </p>
            </CardContent>
          </Card>
        )}

        {users.length > 0 && (
          <div className="rounded-xl border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/40">
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Name</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Email</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Roles</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">DOA</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Dept</th>
                  <th className="text-left px-4 py-3 font-semibold text-muted-foreground">Status</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 font-medium text-foreground">
                      {u.firstName} {u.lastName}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{u.email}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <Badge key={r.name} variant="secondary">{r.name}</Badge>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {u.doaLevel != null ? `L${u.doaLevel}` : '—'}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {u.departmentCode ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={u.active ? 'default' : 'destructive'}>
                        {u.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite User</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            {formError && <p className="text-sm text-destructive">{formError}</p>}
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
              >
                <SelectTrigger id="invite-role">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ROLES.map((r) => (
                    <SelectItem key={r} value={r}>{r}</SelectItem>
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
            <Button variant="outline" onClick={() => setInviteOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleInvite} disabled={invite.isPending}>
              {invite.isPending ? 'Sending...' : 'Send Invite'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageSurface>
  )
}
