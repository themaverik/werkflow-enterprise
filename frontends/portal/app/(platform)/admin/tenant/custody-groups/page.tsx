'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'

interface CustodyMapping {
  id: string
  custodyOwner: string
  candidateGroups: string[]
}

async function fetchCustodyMappings(token: string): Promise<CustodyMapping[]> {
  const res = await fetch('/api/proxy/erp/custody-mappings', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch custody mappings')
  const body: unknown = await res.json()
  return (body as { content?: CustodyMapping[] }).content ?? (body as CustodyMapping[])
}

export default function CustodyGroupsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) return <p className="text-destructive">Access denied.</p>

  const { data: mappings, isLoading, error } = useQuery({
    queryKey: ['custodyMappings'],
    queryFn: () => fetchCustodyMappings(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Custody Groups</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Custody owner to candidate group mappings from ERP.</p>
      </div>
      {error && <p className="text-sm text-destructive">Failed to load custody mappings.</p>}
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
                  <div className="flex flex-wrap gap-1">
                    {m.candidateGroups.map((g) => (
                      <span key={g} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-muted-foreground">{g}</span>
                    ))}
                  </div>
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
