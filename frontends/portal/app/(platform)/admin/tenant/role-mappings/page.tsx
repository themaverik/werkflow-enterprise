'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

interface RoleGroupMapping {
  id?: string
  roleName: string
  groupName: string
  tier: 1 | 2
}

async function fetchRoleMappings(token: string): Promise<RoleGroupMapping[]> {
  const res = await fetch('/api/proxy/admin/config/role-mappings', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch role mappings')
  return res.json()
}

async function addRoleMapping(body: Omit<RoleGroupMapping, 'id'>, token: string): Promise<RoleGroupMapping> {
  const res = await fetch('/api/proxy/admin/config/role-mappings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to add role mapping')
  return res.json()
}

async function deleteRoleMapping(id: string, token: string): Promise<void> {
  const res = await fetch(`/api/proxy/admin/config/role-mappings/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to delete role mapping')
}

export default function RoleMappingsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()
  const [newRole, setNewRole] = useState('')
  const [newGroup, setNewGroup] = useState('')

  const { data: mappings, isLoading } = useQuery({
    queryKey: ['roleMappings'],
    queryFn: () => fetchRoleMappings(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const addMutation = useMutation({
    mutationFn: (body: Omit<RoleGroupMapping, 'id'>) => addRoleMapping(body, token),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['roleMappings'] }); setNewRole(''); setNewGroup('') },
    onError: () => toast.error('Failed to add role mapping — admin service may be unavailable'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteRoleMapping(id, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roleMappings'] }),
    onError: () => toast.error('Failed to delete role mapping'),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  const tier1 = mappings?.filter((m) => m.tier === 1) ?? []
  const tier2 = mappings?.filter((m) => m.tier === 2) ?? []

  const sections = [
    { tier: 1, label: 'Tier 1 — Realm Roles (read-only)', rows: tier1, editable: false },
    { tier: 2, label: 'Tier 2 — Custom Group Mappings', rows: tier2, editable: true },
  ] as const

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Role Mappings</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Tier 1 is read-only (Keycloak realm roles). Tier 2 is editable (custom group assignments).</p>
      </div>


      {sections.map(({ tier, label, rows, editable }) => (
        <section key={tier}>
          <h2 className="text-base font-semibold mb-3">{label}</h2>
          <div className="bg-card border border-border rounded-xl overflow-hidden">
            <table className="w-full text-sm" aria-label={label}>
              <thead>
                <tr className="border-b border-border">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Role</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Group</th>
                  {editable && <th className="px-4 py-3 w-16" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {rows.map((m) => (
                  <tr key={m.id ?? m.roleName} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-mono text-xs">{m.roleName}</td>
                    <td className="px-4 py-3 font-mono text-xs">{m.groupName}</td>
                    {editable && (
                      <td className="px-4 py-3 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive"
                          aria-label={`Delete mapping for ${m.roleName}`}
                          onClick={() => {
                            if (m.id && window.confirm(`Delete mapping for role "${m.roleName}"?`)) {
                              deleteMutation.mutate(m.id)
                            }
                          }}
                          disabled={deleteMutation.isPending}
                        >
                          <Trash2 size={14} strokeWidth={1.8} />
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
                {!isLoading && rows.length === 0 && (
                  <tr><td colSpan={editable ? 3 : 2} className="px-4 py-6 text-center text-muted-foreground text-sm">No mappings.</td></tr>
                )}
                {editable && (
                  <tr className="bg-muted/30">
                    <td className="px-4 py-3">
                      <Input value={newRole} onChange={(e) => setNewRole(e.target.value)} placeholder="role_name" className="h-8 text-sm font-mono" aria-label="New role name" />
                    </td>
                    <td className="px-4 py-3">
                      <Input value={newGroup} onChange={(e) => setNewGroup(e.target.value)} placeholder="group_name" className="h-8 text-sm font-mono" aria-label="New group name" />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        size="sm"
                        aria-label="Add mapping"
                        disabled={!newRole || !newGroup || addMutation.isPending}
                        onClick={() => addMutation.mutate({ roleName: newRole, groupName: newGroup, tier: 2 })}
                      >
                        <Plus size={14} strokeWidth={1.8} />
                      </Button>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      ))}
    </div>
  )
}
