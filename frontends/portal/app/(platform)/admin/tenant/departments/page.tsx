'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { toast } from 'sonner'
import { AlertTriangle } from 'lucide-react'
import Link from 'next/link'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { listConnectors } from '@/lib/api/connectors'

const DEPARTMENTS_CONNECTOR_KEY = 'hr-service'

interface Department {
  id: string | number
  deptCode?: string
  code?: string
  name?: string
  deptName?: string
  leadUserId?: string
  managerId?: string
  departmentType?: string
  officeLocation?: string
  departmentEmail?: string
  isActive?: boolean
}

async function fetchDepartments(token: string): Promise<Department[]> {
  const res = await fetch('/api/proxy/erp/departments', { headers: { Authorization: `Bearer ${token}` } })
  if (!res.ok) throw new Error('Failed to fetch departments')
  const body: unknown = await res.json()
  if (Array.isArray(body)) return body as Department[]
  const paged = body as { content?: unknown }
  return Array.isArray(paged.content) ? (paged.content as Department[]) : []
}

export default function DepartmentsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''

  const { data: connectors, isLoading: loadingConnectors } = useQuery({
    queryKey: ['connectors'],
    queryFn: () => listConnectors(),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const hasConnector = connectors?.some(
    (c) => c.connectorKey === DEPARTMENTS_CONNECTOR_KEY && c.active
  ) ?? false

  const { data: depts, isLoading: loadingDepts, error } = useQuery({
    queryKey: ['erpDepartments'],
    queryFn: () => fetchDepartments(token),
    enabled: status === 'authenticated' && hasConnector,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (error) toast.error('Failed to load departments')
  }, [error])

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) {
    return <p className="text-destructive">Access denied.</p>
  }

  const isLoading = loadingConnectors || (hasConnector && loadingDepts)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Departments</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          {hasConnector
            ? 'Read-only view of departments from the connected HR data source.'
            : 'Department data is sourced from a registered connector.'}
        </p>
      </div>

      {!loadingConnectors && !hasConnector && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-4 dark:border-amber-800 dark:bg-amber-950/30">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" />
          <div className="text-sm">
            <p className="font-medium text-amber-800 dark:text-amber-300">No departments connector registered</p>
            <p className="mt-0.5 text-amber-700 dark:text-amber-400">
              Register a connector with key{' '}
              <code className="rounded bg-amber-100 px-1 dark:bg-amber-900">{DEPARTMENTS_CONNECTOR_KEY}</code>{' '}
              to load department data from your HR system.{' '}
              <Link href="/admin/connectors" className="underline hover:no-underline">
                Go to Connectors →
              </Link>
            </p>
          </div>
        </div>
      )}

      {hasConnector && (
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Departments">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Code</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Name</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Type</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Location</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Email</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Lead</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {isLoading && (
                <tr><td colSpan={7} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading...</td></tr>
              )}
              {(depts ?? []).map((d) => {
                const code = d.deptCode ?? d.code ?? '—'
                const name = d.name ?? d.deptName ?? '—'
                const lead = d.leadUserId ?? d.managerId ?? '—'
                const location = d.officeLocation
                  ? d.officeLocation.replace(/_/g, ' ').replace(/([A-Z]{2,})$/, (m) => m)
                  : '—'
                return (
                  <tr key={d.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-mono text-xs font-semibold">{code}</td>
                    <td className="px-4 py-3 font-medium">{name}</td>
                    <td className="px-4 py-3 text-muted-foreground text-xs">{d.departmentType ?? '—'}</td>
                    <td className="px-4 py-3 text-xs">{location}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{d.departmentEmail ?? '—'}</td>
                    <td className="px-4 py-3 text-xs font-mono text-muted-foreground">{lead}</td>
                    <td className="px-4 py-3">
                      {d.isActive === undefined ? '—' : (
                        <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${d.isActive ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-muted text-muted-foreground'}`}>
                          {d.isActive ? 'Active' : 'Inactive'}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {!isLoading && !error && !depts?.length && (
                <tr><td colSpan={7} className="px-4 py-6 text-center text-muted-foreground text-sm">No departments found.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
