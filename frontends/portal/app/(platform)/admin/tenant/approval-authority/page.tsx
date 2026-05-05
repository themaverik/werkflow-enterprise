'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useState } from 'react'

interface ConfigVar {
  id?: string
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

async function upsertConfigVar(body: ConfigVar, token: string): Promise<ConfigVar> {
  const url = body.id
    ? `/api/proxy/admin/config/vars/${body.id}`
    : '/api/proxy/admin/config/vars'
  const res = await fetch(url, {
    method: body.id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to save config var')
  return res.json()
}

const DOA_LEVELS = ['L1', 'L2', 'L3', 'L4'] as const

export default function ApprovalAuthorityPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''
  const qc = useQueryClient()
  const [editValues, setEditValues] = useState<Record<string, string>>({})

  const { data: doaVars, isLoading: loadingDoa, isError: doaError } = useQuery({
    queryKey: ['configVars', 'DOA_THRESHOLD'],
    queryFn: () => fetchConfigVars('DOA_THRESHOLD', token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: roleLevelVars, isLoading: loadingRoles } = useQuery({
    queryKey: ['configVars', 'ROLE_DOA_LEVEL'],
    queryFn: () => fetchConfigVars('ROLE_DOA_LEVEL', token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const saveMutation = useMutation({
    mutationFn: (body: ConfigVar) => upsertConfigVar(body, token),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['configVars'] }),
  })

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  const doaMap = Object.fromEntries((doaVars ?? []).map((v) => [v.varKey, v]))

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Approval Authority</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Configure DOA threshold amounts per level and role-to-level assignments</p>
      </div>

      <section>
        <h2 className="text-base font-semibold mb-3">Threshold Amounts</h2>
        {doaError && <p className="text-sm text-destructive mb-2">Failed to load threshold amounts.</p>}
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="DOA threshold amounts">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider w-24">Level</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Max Amount (USD)</th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider w-24">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingDoa && <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading...</td></tr>}
              {DOA_LEVELS.map((level) => {
                const existing = doaMap[level]
                const draft = editValues[level] ?? existing?.varValue ?? ''
                return (
                  <tr key={level} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-semibold">{level}</td>
                    <td className="px-4 py-3">
                      <Input
                        type="number"
                        value={draft}
                        onChange={(e) => setEditValues((prev) => ({ ...prev, [level]: e.target.value }))}
                        className="w-48 h-8 text-sm"
                        placeholder="e.g. 10000"
                        aria-label={`Max amount for ${level}`}
                      />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        size="sm"
                        disabled={saveMutation.isPending}
                        onClick={() => saveMutation.mutate({ ...existing, varKey: level, varValue: draft, varType: 'DOA_THRESHOLD' })}
                      >
                        Save
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-base font-semibold mb-3">Role to Level Mapping</h2>
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Role to level mappings">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Role</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Assigned Level</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {(roleLevelVars ?? []).map((v) => (
                <tr key={v.varKey} className="hover:bg-muted/30">
                  <td className="px-4 py-3 font-mono text-xs">{v.varKey}</td>
                  <td className="px-4 py-3 font-semibold">{v.varValue}</td>
                </tr>
              ))}
              {!loadingRoles && !roleLevelVars?.length && (
                <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">No role mappings configured yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
