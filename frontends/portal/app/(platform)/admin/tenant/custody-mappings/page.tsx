'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { toast } from 'sonner'
import { useAuthorization } from '@/lib/auth/use-authorization'

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

export default function CustodyMappingsPage() {
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
        <h1 className="text-2xl font-bold text-foreground">Custody Mappings</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Custody owner to candidate group mappings from ERP.</p>
      </div>

      <p className="text-sm text-muted-foreground">
        Custody mappings define which approval groups handle tasks for each process at runtime. To manage who owns a process definition, open the process and edit its Custody tab.
      </p>

      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm" aria-label="Custody mappings">
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
            <dt className="font-semibold text-foreground">Process Custody (set in the process designer)</dt>
            <dd className="mt-0.5 leading-relaxed">
              Definition governance. Records which department owns a process, its category, and searchable tags. Set once per process definition.
            </dd>
          </div>
        </dl>
      </details>
    </div>
  )
}
