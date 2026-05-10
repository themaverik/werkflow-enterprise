'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useState } from 'react'
import { Info, Lock, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

interface Tier1Mapping { role: string; groups: string[] }
interface Tier2Mapping { id: string; roleName: string; groupName: string; tier: 2 }

async function fetchTier1(token: string): Promise<Tier1Mapping[]> {
  const res = await fetch('/api/proxy/engine/config/flowable-role-mappings', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch Tier 1 mappings')
  const data = await res.json()
  return (data.mappings ?? []) as Tier1Mapping[]
}

async function fetchTier2(token: string): Promise<Tier2Mapping[]> {
  const res = await fetch('/api/proxy/admin/config/role-mappings', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch role mappings')
  const data = await res.json()
  return (Array.isArray(data) ? data : []).filter((m: Tier2Mapping) => m.tier === 2)
}

async function fetchRealmRoles(token: string): Promise<string[]> {
  const res = await fetch('/api/proxy/admin/keycloak/realm-roles', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`Failed to load realm roles (${res.status})`)
  const data = await res.json()
  return (data.roles ?? []) as string[]
}

async function addMapping(body: { roleName: string; groupName: string }, token: string) {
  const res = await fetch('/api/proxy/admin/config/role-mappings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ ...body, tier: 2 }),
  })
  if (!res.ok) throw new Error('Failed to add mapping')
  return res.json()
}

async function deleteMapping(id: string, token: string) {
  const res = await fetch(`/api/proxy/admin/config/role-mappings/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to delete mapping')
}

export default function RoleMappingsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()
  const [newRole, setNewRole] = useState('')
  const [newGroup, setNewGroup] = useState('')

  const { data: tier1 = [], isLoading: loadingTier1 } = useQuery({
    queryKey: ['tier1Mappings'],
    queryFn: () => fetchTier1(token),
    enabled: status === 'authenticated',
    staleTime: 300_000,
  })

  const { data: tier2 = [], isLoading: loadingTier2 } = useQuery({
    queryKey: ['tier2Mappings'],
    queryFn: () => fetchTier2(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: realmRoles = [], isLoading: loadingRoles, isError: rolesError } = useQuery({
    queryKey: ['realmRoles'],
    queryFn: () => fetchRealmRoles(token),
    enabled: status === 'authenticated',
    staleTime: 300_000,
    retry: 1,
  })

  const addMutation = useMutation({
    mutationFn: (body: { roleName: string; groupName: string }) => addMapping(body, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tier2Mappings'] })
      qc.invalidateQueries({ queryKey: ['pss', 'candidateGroups'] })
      setNewRole('')
      setNewGroup('')
      toast.success('Mapping added')
    },
    onError: () => toast.error('Failed to add mapping'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteMapping(id, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tier2Mappings'] })
      qc.invalidateQueries({ queryKey: ['pss', 'candidateGroups'] })
    },
    onError: () => toast.error('Failed to delete mapping'),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Role Mappings</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          Maps Keycloak realm roles to Flowable candidate groups for task assignment.
        </p>
      </div>

      {/* Tier 1 — YAML-backed, read-only */}
      <section>
        <div className="flex items-center gap-2 mb-3">
          <Lock size={13} className="text-muted-foreground" strokeWidth={1.8} />
          <h2 className="text-base font-semibold">Tier 1 — System Roles</h2>
        </div>
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 dark:bg-amber-950/20 dark:border-amber-800 px-3 py-2.5 text-xs text-amber-800 dark:text-amber-300 mb-3">
          <Info size={12} className="mt-0.5 shrink-0" strokeWidth={1.8} />
          Changes require redeployment. Contact your platform administrator.
        </div>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Tier 1 role mappings">
            <thead>
              <tr className="border-b border-border bg-muted/30">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-44">Role</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Candidate Groups</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingTier1 && (
                <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading…</td></tr>
              )}
              {tier1.map((m) => (
                <tr key={m.role} className="bg-muted/10">
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{m.role}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1.5">
                      {m.groups.map((g) => (
                        <span key={g} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-muted border border-border text-foreground">{g}</span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
              {!loadingTier1 && tier1.length === 0 && (
                <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">No Tier 1 mappings configured.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Tier 2 — DB-backed, editable */}
      <section>
        <h2 className="text-base font-semibold mb-3">Tier 2 — Business Role Mappings</h2>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Tier 2 role mappings">
            <thead>
              <tr className="border-b border-border bg-muted/30">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-56">Keycloak Role</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Candidate Group</th>
                <th className="px-4 py-3 w-16" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingTier2 && (
                <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading…</td></tr>
              )}
              {tier2.map((m) => (
                <tr key={m.id} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs">{m.roleName}</td>
                  <td className="px-4 py-3 font-mono text-xs">{m.groupName}</td>
                  <td className="px-4 py-3 text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      aria-label={`Delete mapping for ${m.roleName}`}
                      disabled={deleteMutation.isPending}
                      onClick={() => {
                        if (window.confirm(`Delete mapping for role "${m.roleName}"?`)) {
                          deleteMutation.mutate(m.id)
                        }
                      }}
                    >
                      <Trash2 size={14} strokeWidth={1.8} />
                    </Button>
                  </td>
                </tr>
              ))}
              {!loadingTier2 && tier2.length === 0 && (
                <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No business role mappings yet.</td></tr>
              )}
              {/* Add row */}
              <tr className="bg-muted/20 border-t border-border">
                <td className="px-4 py-3">
                  <Select
                    value={newRole || '__none__'}
                    onValueChange={(v) => setNewRole(v === '__none__' ? '' : v)}
                  >
                    <SelectTrigger className="h-8 text-xs font-mono" aria-label="Select Keycloak role">
                      <SelectValue placeholder="Select role…" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__" className="text-xs text-muted-foreground">Select role…</SelectItem>
                      {realmRoles.map((r) => (
                        <SelectItem key={r} value={r} className="font-mono text-xs">{r}</SelectItem>
                      ))}
                      {loadingRoles && (
                        <div className="px-3 py-2 text-xs text-muted-foreground">Loading roles…</div>
                      )}
                      {rolesError && !loadingRoles && (
                        <div className="px-3 py-2 text-xs text-destructive">Failed to load roles</div>
                      )}
                      {!loadingRoles && !rolesError && realmRoles.length === 0 && (
                        <div className="px-3 py-2 text-xs text-muted-foreground">No roles found</div>
                      )}
                    </SelectContent>
                  </Select>
                </td>
                <td className="px-4 py-3">
                  <Input
                    value={newGroup}
                    onChange={(e) => setNewGroup(e.target.value)}
                    placeholder="candidate_group_name"
                    className="h-8 text-xs font-mono"
                    aria-label="Candidate group name"
                  />
                </td>
                <td className="px-4 py-3 text-right">
                  <Button
                    size="sm"
                    aria-label="Add mapping"
                    disabled={!newRole || !newGroup.trim() || addMutation.isPending}
                    onClick={() => addMutation.mutate({ roleName: newRole, groupName: newGroup.trim() })}
                  >
                    <Plus size={14} strokeWidth={1.8} />
                  </Button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
