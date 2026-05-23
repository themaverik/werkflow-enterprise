'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { useCandidateGroups } from '@/lib/platform/usePlatformCapabilities'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Plus, Pencil, Trash2, X, Check } from 'lucide-react'
import { toast } from 'sonner'
import type { CandidateGroupEntry } from '@/lib/platform/types'
import { PageSurface } from '@/components/layout/page-surface'

interface CustodyMapping {
  id: number
  tenantId: string
  custodyOwner: string
  candidateGroups: string[]
  createdAt: string
  updatedAt: string
}

interface EditState {
  custodyOwner: string
  candidateGroups: string[]
  ownerError: string
  groupsError: string
}

async function fetchCustodyMappings(token: string): Promise<CustodyMapping[]> {
  const res = await fetch('/api/proxy/erp/custody-mappings?size=200', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch custody mappings')
  const body: unknown = await res.json()
  if (Array.isArray(body)) return body as CustodyMapping[]
  const paged = body as { content?: unknown }
  return Array.isArray(paged.content) ? (paged.content as CustodyMapping[]) : []
}

async function createMapping(body: { custodyOwner: string; candidateGroups: string[] }, token: string) {
  const res = await fetch('/api/proxy/erp/custody-mappings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to create custody mapping')
  return res.json()
}

async function updateMapping(id: number, body: { custodyOwner: string; candidateGroups: string[] }, token: string) {
  const res = await fetch(`/api/proxy/erp/custody-mappings/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to update custody mapping')
  return res.json()
}

async function deleteMapping(id: number, token: string) {
  const res = await fetch(`/api/proxy/erp/custody-mappings/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to delete custody mapping')
}

function blankEditState(): EditState {
  return { custodyOwner: '', candidateGroups: [], ownerError: '', groupsError: '' }
}

function validate(state: EditState): EditState {
  return {
    ...state,
    ownerError: state.custodyOwner.trim() === '' ? 'Custody owner is required.' : '',
    groupsError: state.candidateGroups.length === 0 ? 'At least one candidate group is required.' : '',
  }
}

interface ChipRowProps {
  groups: string[]
  onAddGroup: (key: string) => void
  onRemoveGroup: (g: string) => void
  groupsError: string
  availableGroups: CandidateGroupEntry[]
  isLoadingGroups?: boolean
}

function ChipRow({ groups, onAddGroup, onRemoveGroup, groupsError, availableGroups, isLoadingGroups }: ChipRowProps) {
  const unselected = availableGroups.filter((g) => !groups.includes(g.key))
  const tier1 = unselected.filter((g) => g.tier === 1)
  const tier2 = unselected.filter((g) => g.tier === 2)

  return (
    <div>
      <div className="flex flex-wrap items-center gap-1.5 min-h-[2rem]">
        {groups.map((g) => {
          const entry = availableGroups.find((a) => a.key === g)
          return (
            <span
              key={g}
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-mono bg-muted border border-border text-foreground"
              title={entry ? entry.label : undefined}
            >
              {g}
              <button
                type="button"
                aria-label={`Remove ${g}`}
                className="text-muted-foreground hover:text-destructive transition-colors"
                onClick={() => onRemoveGroup(g)}
              >
                <X size={11} strokeWidth={2} />
              </button>
            </span>
          )
        })}

        {unselected.length > 0 && (
          <select
            className="h-7 rounded-md border border-input bg-background px-2 text-xs font-mono text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            value=""
            aria-label="Add candidate group"
            onChange={(e) => {
              if (e.target.value) {
                onAddGroup(e.target.value)
                e.target.value = ''
              }
            }}
          >
            <option value="">Add group…</option>
            {tier1.length > 0 && (
              <optgroup label="System (Tier 1)">
                {tier1.map((g) => (
                  <option key={g.key} value={g.key}>{g.label}</option>
                ))}
              </optgroup>
            )}
            {tier2.length > 0 && (
              <optgroup label="Business (Tier 2)">
                {tier2.map((g) => (
                  <option key={g.key} value={g.key}>{g.label}</option>
                ))}
              </optgroup>
            )}
          </select>
        )}
        {isLoadingGroups && unselected.length === 0 && (
          <select disabled className="h-7 rounded-md border border-input bg-background px-2 text-xs font-mono text-muted-foreground opacity-50">
            <option>Loading groups…</option>
          </select>
        )}
        {!isLoadingGroups && availableGroups.length === 0 && (
          <input
            type="text"
            placeholder="group name + Enter"
            className="h-7 rounded-md border border-input bg-background px-2 text-xs font-mono text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            aria-label="Add candidate group by name"
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                const val = e.currentTarget.value.trim()
                if (val && !groups.includes(val)) {
                  onAddGroup(val)
                  e.currentTarget.value = ''
                }
                e.preventDefault()
              }
            }}
          />
        )}
      </div>
      {groupsError && <p className="text-destructive text-xs mt-1">{groupsError}</p>}
    </div>
  )
}

export default function CustodyMappingsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editState, setEditState] = useState<EditState>(blankEditState())
  const [addingNew, setAddingNew] = useState(false)
  const [newState, setNewState] = useState<EditState>(blankEditState())

  const { data: availableGroups = [], isLoading: isLoadingGroups } = useCandidateGroups()

  const { data: mappings = [], isLoading } = useQuery({
    queryKey: ['custodyMappings'],
    queryFn: () => fetchCustodyMappings(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const createMutation = useMutation({
    mutationFn: (body: { custodyOwner: string; candidateGroups: string[] }) => createMapping(body, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['custodyMappings'] })
      setAddingNew(false)
      setNewState(blankEditState())
      toast.success('Custody mapping created')
    },
    onError: () => toast.error('Failed to create custody mapping'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { custodyOwner: string; candidateGroups: string[] } }) =>
      updateMapping(id, body, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['custodyMappings'] })
      setEditingId(null)
      setEditState(blankEditState())
      toast.success('Custody mapping updated')
    },
    onError: () => toast.error('Failed to update custody mapping'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteMapping(id, token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['custodyMappings'] })
      toast.success('Custody mapping deleted')
    },
    onError: () => toast.error('Failed to delete custody mapping'),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  function startEdit(m: CustodyMapping) {
    setAddingNew(false)
    setEditingId(m.id)
    setEditState({ custodyOwner: m.custodyOwner, candidateGroups: [...m.candidateGroups], ownerError: '', groupsError: '' })
  }

  function cancelEdit() {
    setEditingId(null)
    setEditState(blankEditState())
  }

  function saveEdit(id: number) {
    const validated = validate(editState)
    setEditState(validated)
    if (validated.ownerError || validated.groupsError) return
    updateMutation.mutate({ id, body: { custodyOwner: validated.custodyOwner.trim(), candidateGroups: validated.candidateGroups } })
  }

  function startAdd() {
    cancelEdit()
    setAddingNew(true)
    setNewState(blankEditState())
  }

  function cancelAdd() {
    setAddingNew(false)
    setNewState(blankEditState())
  }

  function saveNew() {
    const validated = validate(newState)
    setNewState(validated)
    if (validated.ownerError || validated.groupsError) return
    createMutation.mutate({ custodyOwner: validated.custodyOwner.trim(), candidateGroups: validated.candidateGroups })
  }

  const isSaving = updateMutation.isPending || createMutation.isPending

  return (
    <PageSurface>
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Custody Mappings</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Custody owner to candidate group mappings from ERP.</p>
      </div>

      <p className="text-sm text-muted-foreground">
        Custody mappings define which approval groups handle tasks for each process at runtime. To manage who owns a process definition, open the process and edit its Custody tab.
      </p>

      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm" aria-label="Custody mappings">
          <thead>
            <tr className="border-b border-border bg-muted/30">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-56">Owner</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Candidate Groups</th>
              <th className="px-4 py-3 w-28" />
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading && (
              <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading…</td></tr>
            )}

            {mappings.map((m) =>
              editingId === m.id ? (
                <tr key={m.id} className="bg-muted/10">
                  <td className="px-4 py-3 align-top">
                    <Input
                      value={editState.custodyOwner}
                      onChange={(e) => setEditState((s) => ({ ...s, custodyOwner: e.target.value, ownerError: '' }))}
                      className="h-8 text-xs font-mono"
                      aria-label="Custody owner"
                    />
                    {editState.ownerError && <p className="text-destructive text-xs mt-1">{editState.ownerError}</p>}
                  </td>
                  <td className="px-4 py-3 align-top">
                    <ChipRow
                      groups={editState.candidateGroups}
                      onAddGroup={(key) => setEditState((s) => ({ ...s, candidateGroups: [...s.candidateGroups, key], groupsError: '' }))}
                      onRemoveGroup={(g) => setEditState((s) => ({ ...s, candidateGroups: s.candidateGroups.filter((x) => x !== g) }))}
                      groupsError={editState.groupsError}
                      availableGroups={availableGroups}
                      isLoadingGroups={isLoadingGroups}
                    />
                  </td>
                  <td className="px-4 py-3 align-top">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        className="h-8 px-2 text-muted-foreground hover:text-foreground"
                        aria-label="Cancel edit"
                        onClick={cancelEdit}
                        disabled={isSaving}
                      >
                        <X size={14} strokeWidth={1.8} />
                      </Button>
                      <Button
                        size="sm"
                        className="h-8 px-2"
                        aria-label="Save changes"
                        onClick={() => saveEdit(m.id)}
                        disabled={isSaving}
                      >
                        <Check size={14} strokeWidth={2} />
                      </Button>
                    </div>
                  </td>
                </tr>
              ) : (
                <tr key={m.id} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs align-middle">{m.custodyOwner}</td>
                  <td className="px-4 py-3 align-middle">
                    {m.candidateGroups.length === 0 ? (
                      <span className="text-muted-foreground text-xs">—</span>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {m.candidateGroups.map((g) => (
                          <span key={g} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-muted border border-border text-foreground">{g}</span>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 align-middle">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-8 px-2"
                        aria-label={`Edit mapping for ${m.custodyOwner}`}
                        onClick={() => startEdit(m)}
                        disabled={deleteMutation.isPending}
                      >
                        <Pencil size={13} strokeWidth={1.8} />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-8 px-2 text-destructive hover:text-destructive"
                        aria-label={`Delete mapping for ${m.custodyOwner}`}
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          if (window.confirm(`Delete custody mapping "${m.custodyOwner}"?`)) {
                            deleteMutation.mutate(m.id)
                          }
                        }}
                      >
                        <Trash2 size={13} strokeWidth={1.8} />
                      </Button>
                    </div>
                  </td>
                </tr>
              )
            )}

            {!isLoading && mappings.length === 0 && !addingNew && (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No custody mappings found.</td>
              </tr>
            )}

            {addingNew && (
              <tr className="bg-muted/10">
                <td className="px-4 py-3 align-top">
                  <Input
                    value={newState.custodyOwner}
                    onChange={(e) => setNewState((s) => ({ ...s, custodyOwner: e.target.value, ownerError: '' }))}
                    placeholder="e.g. IT Custody"
                    className="h-8 text-xs font-mono"
                    aria-label="New custody owner"
                    autoFocus
                  />
                  {newState.ownerError && <p className="text-destructive text-xs mt-1">{newState.ownerError}</p>}
                </td>
                <td className="px-4 py-3 align-top">
                  <ChipRow
                    groups={newState.candidateGroups}
                    onAddGroup={(key) => setNewState((s) => ({ ...s, candidateGroups: [...s.candidateGroups, key], groupsError: '' }))}
                    onRemoveGroup={(g) => setNewState((s) => ({ ...s, candidateGroups: s.candidateGroups.filter((x) => x !== g) }))}
                    groupsError={newState.groupsError}
                    availableGroups={availableGroups}
                    isLoadingGroups={isLoadingGroups}
                  />
                </td>
                <td className="px-4 py-3 align-top">
                  <div className="flex items-center justify-end gap-1">
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-8 px-2 text-muted-foreground hover:text-foreground"
                      aria-label="Cancel new mapping"
                      onClick={cancelAdd}
                      disabled={isSaving}
                    >
                      <X size={14} strokeWidth={1.8} />
                    </Button>
                    <Button
                      size="sm"
                      className="h-8 px-2"
                      aria-label="Save new mapping"
                      onClick={saveNew}
                      disabled={isSaving}
                    >
                      <Check size={14} strokeWidth={2} />
                    </Button>
                  </div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {!addingNew && (
        <Button variant="outline" size="sm" className="gap-1.5" onClick={startAdd}>
          <Plus size={14} strokeWidth={1.8} />
          Add Custody Mapping
        </Button>
      )}

      <details className="text-sm text-muted-foreground mt-8 border rounded-md p-3">
        <summary className="cursor-pointer font-medium text-foreground select-none">Terminology</summary>
        <dl className="mt-3 space-y-3">
          <div>
            <dt className="font-semibold text-foreground">Custody Mappings (this page)</dt>
            <dd className="mt-0.5 leading-relaxed">
              Runtime routing rules. Determines which Keycloak approval group handles a task when a process instance runs. Configured per tenant.
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-foreground">Approval Group</dt>
            <dd className="mt-0.5 leading-relaxed">
              The Flowable candidate group assigned to a BPMN user task. Maps to a set of Keycloak roles. Configured per task in the process designer.
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-foreground">Custody Owner</dt>
            <dd className="mt-0.5 leading-relaxed">
              The department or entity that owns a process (e.g. DEPT:FINANCE). Used to look up which approval groups handle tasks in that process at runtime.
            </dd>
          </div>
        </dl>
      </details>
    </div>
    </PageSurface>
  )
}
