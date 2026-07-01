'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuth } from '@/lib/auth/auth-context'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import React, { useState, useId } from 'react'
import { Info, Lock, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { PageSurface } from '@/components/layout/page-surface'

interface Tier1Mapping { role: string; groups: string[] }
interface Tier2Mapping { id: string; roleName: string; groupName: string; tier: 2; isManagerTier?: boolean }

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

async function fetchCandidateGroups(
  token: string
): Promise<Array<{ key: string; label: string; tier: number; isManagerTier?: boolean }>> {
  const res = await fetch('/api/proxy/admin/design/platform/candidate-groups', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to load candidate groups')
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

// Accepts UPPER_SNAKE_CASE or the scoped DOA form DEPT:<ID>::DOA:L<N>
const GROUP_RE = /^[A-Z0-9_]+$|^DEPT:[A-Z0-9_]+::DOA:L[0-9]+$/

// ── Group combobox ─────────────────────────────────────────────────────────────

interface GroupOption {
  key: string
  label: string
}

interface GroupComboBoxProps {
  value: string
  onChange: (v: string) => void
  options: GroupOption[]
  loading: boolean
  isError: boolean
}

function GroupComboBox({ value, onChange, options, loading, isError }: GroupComboBoxProps) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const listboxId = useId()

  const trimmed = value.trim()
  const filtered = options
    .filter(
      (o) =>
        o.key.toLowerCase().includes(trimmed.toLowerCase()) ||
        o.label.toLowerCase().includes(trimmed.toLowerCase()),
    )
    .sort((a, b) => a.key.localeCompare(b.key))

  const exactMatch = options.some((o) => o.key.toLowerCase() === trimmed.toLowerCase())
  const showAddOption = trimmed.length > 0 && !exactMatch
  const hasContent = loading || isError || filtered.length > 0 || showAddOption

  const totalOptions = filtered.length + (showAddOption ? 1 : 0)

  const activeDescendant =
    open && activeIndex >= 0 && activeIndex < filtered.length
      ? `${listboxId}-opt-${activeIndex}`
      : open && showAddOption && activeIndex === filtered.length
        ? `${listboxId}-opt-add`
        : undefined

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault()
        if (!open) {
          setOpen(true)
          setActiveIndex(totalOptions > 0 ? 0 : -1)
        } else if (totalOptions > 0) {
          setActiveIndex((prev) => Math.min(prev + 1, totalOptions - 1))
        }
        break
      case 'ArrowUp':
        e.preventDefault()
        if (open) {
          setActiveIndex((prev) => Math.max(prev - 1, 0))
        }
        break
      case 'Enter': {
        e.preventDefault()
        if (!open) break
        if (activeIndex >= 0 && activeIndex < filtered.length) {
          onChange(filtered[activeIndex].key)
          setOpen(false)
          setActiveIndex(-1)
        } else if (showAddOption && (activeIndex === filtered.length || filtered.length === 0)) {
          onChange(trimmed)
          setOpen(false)
          setActiveIndex(-1)
        }
        break
      }
      case 'Escape':
        e.preventDefault()
        setOpen(false)
        setActiveIndex(-1)
        break
    }
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    onChange(e.target.value)
    setActiveIndex(-1)
  }

  return (
    <div className="relative flex-1">
      <Input
        value={value}
        onChange={handleChange}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => { setOpen(false); setActiveIndex(-1) }, 150)}
        onKeyDown={handleKeyDown}
        placeholder="Select or type group…"
        className="h-8 text-xs font-mono w-full"
        aria-label="Candidate group name"
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-haspopup="listbox"
        aria-autocomplete="list"
        aria-activedescendant={activeDescendant}
        autoComplete="off"
      />
      {open && hasContent && (
        <div
          id={listboxId}
          role="listbox"
          aria-label="Candidate group options"
          className="absolute z-50 top-full mt-1 w-full bg-popover border border-border rounded-md shadow-md max-h-48 overflow-y-auto"
        >
          {loading && (
            <div
              role="option"
              aria-disabled="true"
              aria-selected={false}
              className="px-3 py-2 text-xs text-muted-foreground"
            >
              Loading groups…
            </div>
          )}
          {isError && !loading && (
            <div
              role="option"
              aria-disabled="true"
              aria-selected={false}
              className="px-3 py-2 text-xs text-muted-foreground italic"
            >
              Could not load groups — you can still type a new one
            </div>
          )}
          {!loading &&
            filtered.map((o, i) => (
              <div
                key={o.key}
                id={`${listboxId}-opt-${i}`}
                role="option"
                aria-selected={value === o.key}
                className={`flex items-center gap-2 px-3 py-1.5 text-xs cursor-pointer select-none hover:bg-accent hover:text-accent-foreground${activeIndex === i ? ' bg-accent text-accent-foreground' : ''}`}
                onMouseDown={(e) => {
                  e.preventDefault()
                  onChange(o.key)
                  setOpen(false)
                  setActiveIndex(-1)
                }}
              >
                <span className="font-mono">{o.key}</span>
                {o.label !== o.key && (
                  <span className="text-muted-foreground font-sans truncate">{o.label}</span>
                )}
              </div>
            ))}
          {!loading && showAddOption && (
            <div
              id={`${listboxId}-opt-add`}
              role="option"
              aria-selected={false}
              className={`px-3 py-1.5 text-xs cursor-pointer select-none hover:bg-accent hover:text-accent-foreground italic${activeIndex === filtered.length ? ' bg-accent text-accent-foreground' : ' text-muted-foreground'}`}
              onMouseDown={(e) => {
                e.preventDefault()
                onChange(trimmed)
                setOpen(false)
                setActiveIndex(-1)
              }}
            >
              Add &ldquo;{trimmed}&rdquo;
            </div>
          )}
          {!loading && !isError && filtered.length === 0 && !showAddOption && (
            <div
              role="option"
              aria-disabled="true"
              aria-selected={false}
              className="px-3 py-2 text-xs text-muted-foreground"
            >
              No Tier 2 groups found — type a new group name
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default function RoleMappingsPage() {
  const { status } = useSession()
  const { hasAnyRole } = useAuthorization()
  const { token } = useAuth()
  const qc = useQueryClient()
  const [newRole, setNewRole] = useState('')
  const [newGroup, setNewGroup] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  const { data: tier1 = [], isLoading: loadingTier1 } = useQuery({
    queryKey: ['tier1Mappings'],
    queryFn: () => fetchTier1(token ?? ''),
    enabled: status === 'authenticated',
    staleTime: 300_000,
  })

  const { data: tier2 = [], isLoading: loadingTier2 } = useQuery({
    queryKey: ['tier2Mappings'],
    queryFn: () => fetchTier2(token ?? ''),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const {
    data: candidateGroups = [],
    isLoading: loadingGroups,
    isError: groupsError,
  } = useQuery({
    queryKey: ['pss', 'candidateGroups'],
    queryFn: () => fetchCandidateGroups(token ?? ''),
    enabled: status === 'authenticated',
    staleTime: 300_000,
  })

  const { data: realmRoles = [], isLoading: loadingRoles, isError: rolesError } = useQuery({
    queryKey: ['realmRoles'],
    queryFn: () => fetchRealmRoles(token ?? ''),
    enabled: status === 'authenticated',
    staleTime: 300_000,
    retry: 1,
  })

  const addMutation = useMutation({
    mutationFn: (body: { roleName: string; groupName: string }) => addMapping(body, token ?? ''),
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
    mutationFn: (id: string) => deleteMapping(id, token ?? ''),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tier2Mappings'] })
      qc.invalidateQueries({ queryKey: ['pss', 'candidateGroups'] })
    },
    onError: () => toast.error('Failed to delete mapping'),
  })

  const tier2Groups = candidateGroups.filter((g) => g.tier === 2)

  function handleAddMapping() {
    const trimmed = newGroup.trim()
    const existing = tier2Groups.find((g) => g.key.toLowerCase() === trimmed.toLowerCase())
    const resolvedGroup = existing ? existing.key : trimmed

    if (!GROUP_RE.test(resolvedGroup)) {
      toast.error(
        'Group must be UPPER_SNAKE_CASE — A–Z, 0–9, _ (e.g. MANAGER, FINANCE_APPROVER, DOA_L1)',
      )
      return
    }

    const isDuplicate = tier2.some(
      (m) => m.roleName === newRole && m.groupName === resolvedGroup,
    )
    if (isDuplicate) {
      toast.error('This role is already mapped to ' + resolvedGroup)
      return
    }

    addMutation.mutate({ roleName: newRole, groupName: resolvedGroup })
  }

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  return (
    <PageSurface>
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
          {loadingTier2 && (
            <p className="px-4 py-6 text-center text-muted-foreground text-sm">Loading…</p>
          )}
          {!loadingTier2 && tier2.length === 0 && (
            <p className="px-4 py-6 text-center text-muted-foreground text-sm">No business role mappings yet.</p>
          )}
          {!loadingTier2 && tier2.length > 0 && (
            <div className="p-3 space-y-2">
              {tier2.map((m) => (
                <div
                  key={m.id}
                  className="flex items-center gap-2 p-2 rounded-lg border border-border bg-card"
                >
                  <span className="font-mono text-xs bg-muted px-1.5 py-0.5 rounded">{m.roleName}</span>
                  <span className="text-muted-foreground text-xs">→</span>
                  <span className="font-mono text-xs bg-muted px-1.5 py-0.5 rounded">{m.groupName}</span>
                  {m.isManagerTier && (
                    <span className="text-xs px-1.5 py-0.5 rounded bg-violet-50 text-violet-800 border border-violet-200 dark:bg-violet-950/20 dark:text-violet-300 dark:border-violet-800">
                      manager-tier
                    </span>
                  )}
                  <div className="ml-auto">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      aria-label={`Delete mapping for ${m.roleName}`}
                      disabled={deleteMutation.isPending}
                      onClick={() => { setPendingDeleteId(m.id); setConfirmOpen(true) }}
                    >
                      <Trash2 size={14} strokeWidth={1.8} />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Add row */}
        <div className="mt-3 space-y-1.5">
          <div className="flex items-center gap-2">
            <Select
              value={newRole || '__none__'}
              onValueChange={(v) => setNewRole(v === '__none__' ? '' : v)}
            >
              <SelectTrigger className="h-8 text-xs font-mono flex-1" aria-label="Select Keycloak role">
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

            <GroupComboBox
              value={newGroup}
              onChange={setNewGroup}
              options={tier2Groups}
              loading={loadingGroups}
              isError={groupsError}
            />

            <Button
              size="sm"
              aria-label="Add mapping"
              disabled={!newRole || !newGroup.trim() || addMutation.isPending}
              onClick={handleAddMapping}
            >
              <Plus size={14} strokeWidth={1.8} />
            </Button>
          </div>
          <div className="flex items-start gap-1.5 text-xs text-muted-foreground">
            <Info size={12} className="mt-0.5 shrink-0" strokeWidth={1.8} />
            <span>
              Group names are UPPER_SNAKE_CASE (A–Z, 0–9, _) — or a scoped form like{' '}
              <span className="font-mono">DEPT:FIN::DOA:L2</span> — and must match a candidate
              group used in a process (e.g. <span className="font-mono">MANAGER</span>,{' '}
              <span className="font-mono">FINANCE_APPROVER</span>,{' '}
              <span className="font-mono">DOA_L1</span>).
            </span>
          </div>
        </div>
      </section>
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Confirm Deletion"
        description="This action cannot be undone."
        onConfirm={() => { if (pendingDeleteId !== null) { deleteMutation.mutate(pendingDeleteId); setPendingDeleteId(null) } }}
      />
    </div>
    </PageSurface>
  )
}
