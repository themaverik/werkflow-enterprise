'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { toast } from 'sonner'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { Info } from 'lucide-react'

interface CustodyMapping {
  id: string
  custodyOwner: string
  candidateGroups: string[]
}

async function fetchCustodyMappings(token: string): Promise<CustodyMapping[]> {
  const res = await fetch('/api/proxy/erp/custody-mappings', { headers: { Authorization: `Bearer ${token}` } })
  if (!res.ok) throw new Error('Failed to fetch custody mappings')
  const body: unknown = await res.json()
  if (Array.isArray(body)) return body as CustodyMapping[]
  const paged = body as { content?: unknown }
  return Array.isArray(paged.content) ? (paged.content as CustodyMapping[]) : []
}

export default function CustodyGroupsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''

  const { data: mappings, isLoading, error } = useQuery({
    queryKey: ['custodyMappings'],
    queryFn: () => fetchCustodyMappings(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  useEffect(() => {
    if (error) toast.error('Failed to load custody mappings')
  }, [error])

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Custody Groups</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Custody owner to candidate group mappings from ERP.</p>
      </div>

      <div className="flex items-start gap-3 rounded-xl border border-border bg-muted/40 px-4 py-3.5 text-sm">
        <Info size={15} className="mt-0.5 shrink-0 text-muted-foreground" strokeWidth={1.8} />
        <div className="space-y-1.5 text-muted-foreground text-xs leading-relaxed">
          <p>
            <span className="font-semibold text-foreground">Candidate Groups</span> — Flowable task assignment pools.
            Determine who can <em>claim and complete</em> a task. Configured via Role Mappings (Tier 1 &amp; Tier 2).
          </p>
          <p>
            <span className="font-semibold text-foreground">Custody Groups</span> — Accountability ownership.
            The named steward responsible for a process instance (e.g. Finance Director). A custodian maps to the
            groups that act on their behalf in DOA workflows. Stored in ERP.
          </p>
        </div>
      </div>

      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm" aria-label="Custody group mappings">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Owner</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Candidate Groups</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading && <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading...</td></tr>}
            {(mappings ?? []).map((m) => (
              <tr key={m.id} className="hover:bg-muted/30">
                <td className="px-4 py-3 font-mono text-xs">{m.custodyOwner}</td>
                <td className="px-4 py-3">
                  {m.candidateGroups.length === 0 ? (
                    <span className="text-muted-foreground text-xs">—</span>
                  ) : (
                    <div className="flex flex-wrap gap-1">
                      {m.candidateGroups.map((g) => (
                        <span key={g} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-muted-foreground">{g}</span>
                      ))}
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {!isLoading && !error && !mappings?.length && (
              <tr><td colSpan={2} className="px-4 py-6 text-center text-muted-foreground text-sm">No custody mappings found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
