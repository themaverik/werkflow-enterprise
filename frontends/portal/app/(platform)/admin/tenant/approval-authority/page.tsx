'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

const MAX_LEVELS = 10

interface ConfigVar {
  id?: number
  varKey: string
  varValue: string
  varType: string
  description?: string
}

async function fetchConfigVars(type: string, token: string): Promise<ConfigVar[]> {
  const res = await fetch(`/api/proxy/admin/config/vars?type=${type}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch config vars')
  return res.json()
}

async function fetchRealmRoles(token: string): Promise<string[]> {
  const res = await fetch('/api/proxy/admin/keycloak/realm-roles', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`Failed to load realm roles (${res.status})`)
  const data = await res.json()
  return (data.roles ?? []) as string[]
}

async function saveConfigVar(body: ConfigVar, token: string): Promise<ConfigVar> {
  const url = body.id ? `/api/proxy/admin/config/vars/${body.id}` : '/api/proxy/admin/config/vars'
  const res = await fetch(url, {
    method: body.id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to save')
  return res.json()
}

async function deleteConfigVar(id: number, token: string): Promise<void> {
  const res = await fetch(`/api/proxy/admin/config/vars/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(`Failed to delete (${res.status})${body ? ': ' + body : ''}`)
  }
}

function levelNum(key: string) { return parseInt(key.slice(1), 10) }

export default function ApprovalAuthorityPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()

  // Draft edits for existing threshold rows
  const [amountDrafts, setAmountDrafts] = useState<Record<string, string>>({})
  // Pending new level row: value entered but not yet saved
  const [pendingLevel, setPendingLevel] = useState<string | null>(null)
  const [pendingAmount, setPendingAmount] = useState('')
  // New role-level row
  const [newRoleMapping, setNewRoleMapping] = useState({ role: '', level: '' })

  const { data: doaVars = [], isLoading: loadingDoa } = useQuery({
    queryKey: ['configVars', 'DOA_THRESHOLD'],
    queryFn: () => fetchConfigVars('DOA_THRESHOLD', token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: roleLevelVars = [], isLoading: loadingRoles } = useQuery({
    queryKey: ['configVars', 'ROLE_DOA_LEVEL'],
    queryFn: () => fetchConfigVars('ROLE_DOA_LEVEL', token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: realmRoles = [], isLoading: loadingRealmRoles, isError: rolesError } = useQuery({
    queryKey: ['realmRoles'],
    queryFn: () => fetchRealmRoles(token),
    enabled: status === 'authenticated',
    staleTime: 300_000,
    retry: 1,
  })

  const saveMutation = useMutation({
    mutationFn: (body: ConfigVar) => saveConfigVar(body, token),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['configVars'] })
      if (vars.varType === 'DOA_THRESHOLD') {
        setAmountDrafts((prev) => { const n = { ...prev }; delete n[vars.varKey]; return n })
        setPendingLevel(null)
        setPendingAmount('')
      } else {
        setNewRoleMapping({ role: '', level: '' })
      }
      toast.success('Saved')
    },
    onError: () => toast.error('Failed to save'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteConfigVar(id, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['configVars'] })
      // Invalidate realm roles so deleted role assignments re-appear in the dropdown
      qc.invalidateQueries({ queryKey: ['realmRoles'] })
      toast.success('Deleted')
    },
    onError: () => toast.error('Failed to delete'),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  // Sorted existing threshold levels
  const definedLevels = doaVars
    .filter((v) => /^L\d+$/.test(v.varKey))
    .sort((a, b) => levelNum(a.varKey) - levelNum(b.varKey))

  const maxLevelNum = definedLevels.length > 0
    ? Math.max(...definedLevels.map((v) => levelNum(v.varKey)))
    : 0

  const nextLevelKey = `L${maxLevelNum + 1}`
  const atMaxLevels = definedLevels.length >= MAX_LEVELS

  // Roles already assigned a DOA level (for 1:1 enforcement)
  const assignedRoles = new Set(roleLevelVars.map((v) => v.varKey))
  const availableRoles = realmRoles.filter((r) => !assignedRoles.has(r))

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Approval Authority</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          Configure delegation-of-authority threshold amounts and role assignments.
        </p>
      </div>

      {/* Section 1 — Threshold Amounts */}
      <section>
        <h2 className="text-base font-semibold mb-3">Threshold Amounts</h2>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="DOA threshold amounts">
            <thead>
              <tr className="border-b border-border bg-muted/30">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-20">Level</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Max Amount (USD)</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider w-32">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingDoa && (
                <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading…</td></tr>
              )}
              {definedLevels.map((v) => {
                const draft = amountDrafts[v.varKey] ?? v.varValue
                const isDirty = draft !== v.varValue
                return (
                  <tr key={v.varKey} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-semibold text-sm">{v.varKey}</td>
                    <td className="px-4 py-3">
                      <Input
                        value={draft}
                        onChange={(e) => setAmountDrafts((prev) => ({ ...prev, [v.varKey]: e.target.value }))}
                        className="w-48 h-8 text-sm"
                        placeholder="e.g. 10000 or unlimited"
                        aria-label={`Max amount for ${v.varKey}`}
                      />
                    </td>
                    <td className="px-4 py-3 text-right flex items-center justify-end gap-2">
                      {isDirty && (
                        <Button
                          size="sm"
                          disabled={!draft.trim() || saveMutation.isPending}
                          onClick={() => saveMutation.mutate({ ...v, varValue: draft.trim(), varType: 'DOA_THRESHOLD' })}
                        >
                          Save
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-destructive hover:text-destructive"
                        aria-label={`Delete level ${v.varKey}`}
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          if (v.id != null && window.confirm(`Delete level ${v.varKey}? This may affect role assignments.`)) {
                            deleteMutation.mutate(v.id)
                          }
                        }}
                      >
                        <Trash2 size={14} strokeWidth={1.8} />
                      </Button>
                    </td>
                  </tr>
                )
              })}

              {/* Pending new level row */}
              {pendingLevel && (
                <tr className="bg-muted/20 border-t border-border">
                  <td className="px-4 py-3 font-semibold text-sm text-muted-foreground">{pendingLevel}</td>
                  <td className="px-4 py-3">
                    <Input
                      autoFocus
                      value={pendingAmount}
                      onChange={(e) => setPendingAmount(e.target.value)}
                      className="w-48 h-8 text-sm"
                      placeholder="e.g. 500000 or unlimited"
                      aria-label={`Amount for ${pendingLevel}`}
                      onKeyDown={(e) => {
                        if (e.key === 'Escape') { setPendingLevel(null); setPendingAmount('') }
                      }}
                    />
                  </td>
                  <td className="px-4 py-3 text-right flex items-center justify-end gap-2">
                    <Button
                      size="sm"
                      disabled={!pendingAmount.trim() || saveMutation.isPending}
                      onClick={() =>
                        saveMutation.mutate({
                          varKey: pendingLevel,
                          varValue: pendingAmount.trim(),
                          varType: 'DOA_THRESHOLD',
                        })
                      }
                    >
                      Save
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => { setPendingLevel(null); setPendingAmount('') }}
                    >
                      Cancel
                    </Button>
                  </td>
                </tr>
              )}

              {!loadingDoa && definedLevels.length === 0 && !pendingLevel && (
                <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No threshold levels defined. Add the first level below.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="mt-3">
          {atMaxLevels ? (
            <p className="text-xs text-muted-foreground">Maximum of {MAX_LEVELS} levels reached.</p>
          ) : pendingLevel ? null : (
            <Button
              variant="outline"
              size="sm"
              onClick={() => { setPendingLevel(nextLevelKey); setPendingAmount('') }}
            >
              <Plus size={14} strokeWidth={1.8} className="mr-1.5" />
              Add Level ({nextLevelKey})
            </Button>
          )}
        </div>
      </section>

      {/* Section 2 — Role to Level Mapping */}
      <section>
        <h2 className="text-base font-semibold mb-1">Role to Level Mapping</h2>
        <p className="text-xs text-muted-foreground mb-3">
          Each role maps to exactly one level. A role with level L3 can approve up to the L3 threshold.
        </p>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Role to level mappings">
            <thead>
              <tr className="border-b border-border bg-muted/30">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Role</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-32">Level</th>
                <th className="px-4 py-3 w-16" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingRoles && (
                <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading…</td></tr>
              )}
              {roleLevelVars.map((v) => (
                <tr key={v.varKey} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs">{v.varKey}</td>
                  <td className="px-4 py-3 font-semibold text-sm">{v.varValue}</td>
                  <td className="px-4 py-3 text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      aria-label={`Remove level assignment for ${v.varKey}`}
                      disabled={deleteMutation.isPending}
                      onClick={() => {
                        if (v.id != null && window.confirm(`Remove level assignment for role "${v.varKey}"?`)) {
                          deleteMutation.mutate(v.id)
                        }
                      }}
                    >
                      <Trash2 size={14} strokeWidth={1.8} />
                    </Button>
                  </td>
                </tr>
              ))}
              {!loadingRoles && roleLevelVars.length === 0 && (
                <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No role assignments yet.</td></tr>
              )}

              {/* Add row */}
              <tr className="bg-muted/20 border-t border-border">
                <td className="px-4 py-3">
                  <Select
                    value={newRoleMapping.role || '__none__'}
                    onValueChange={(v) => setNewRoleMapping((prev) => ({ ...prev, role: v === '__none__' ? '' : v }))}
                  >
                    <SelectTrigger className="h-8 text-xs font-mono" aria-label="Select role">
                      <SelectValue placeholder="Select role…" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__" className="text-xs text-muted-foreground">Select role…</SelectItem>
                      {availableRoles.map((r) => (
                        <SelectItem key={r} value={r} className="font-mono text-xs">{r}</SelectItem>
                      ))}
                      {loadingRealmRoles && (
                        <div className="px-3 py-2 text-xs text-muted-foreground">Loading roles…</div>
                      )}
                      {rolesError && !loadingRealmRoles && (
                        <div className="px-3 py-2 text-xs text-destructive">Failed to load roles</div>
                      )}
                      {!loadingRealmRoles && !rolesError && availableRoles.length === 0 && (
                        <div className="px-3 py-2 text-xs text-muted-foreground">All roles assigned</div>
                      )}
                    </SelectContent>
                  </Select>
                </td>
                <td className="px-4 py-3">
                  <Select
                    value={newRoleMapping.level || '__none__'}
                    onValueChange={(v) => setNewRoleMapping((prev) => ({ ...prev, level: v === '__none__' ? '' : v }))}
                  >
                    <SelectTrigger className="h-8 text-xs w-28" aria-label="Select level">
                      <SelectValue placeholder="Level…" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__" className="text-xs text-muted-foreground">Level…</SelectItem>
                      {definedLevels.map((l) => (
                        <SelectItem key={l.varKey} value={l.varKey}>{l.varKey}</SelectItem>
                      ))}
                      {definedLevels.length === 0 && (
                        <div className="px-3 py-2 text-xs text-muted-foreground">No levels defined</div>
                      )}
                    </SelectContent>
                  </Select>
                </td>
                <td className="px-4 py-3 text-right">
                  <Button
                    size="sm"
                    aria-label="Add role assignment"
                    disabled={!newRoleMapping.role || !newRoleMapping.level || saveMutation.isPending}
                    onClick={() =>
                      saveMutation.mutate({
                        varKey: newRoleMapping.role,
                        varValue: newRoleMapping.level,
                        varType: 'ROLE_DOA_LEVEL',
                        description: `${newRoleMapping.level} approver role`,
                      })
                    }
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
