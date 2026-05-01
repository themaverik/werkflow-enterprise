'use client'

import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { useAuthorization } from '@/lib/auth/use-authorization'

interface Department {
  id: string
  deptCode: string
  deptName: string
  managerId?: string
}

async function fetchDepartments(token: string): Promise<Department[]> {
  const res = await fetch('/api/proxy/erp/departments', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Failed to fetch departments')
  const body: unknown = await res.json()
  return (body as { content?: Department[] }).content ?? (body as Department[])
}

export default function DepartmentsPage() {
  const { status, data: session } = useSession()
  const { hasAnyRole } = useAuthorization()
  const token = (session?.accessToken as string) ?? ''

  if (!hasAnyRole(['ADMIN', 'SUPER_ADMIN'])) return <p className="text-destructive">Access denied.</p>

  const { data: depts, isLoading, error } = useQuery({
    queryKey: ['erpDepartments'],
    queryFn: () => fetchDepartments(token),
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Departments</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Departments are managed in the ERP system. This is a read-only view.</p>
      </div>
      {error && <p className="text-sm text-destructive">Failed to load departments.</p>}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full text-sm" aria-label="Departments">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Code</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">Manager ID</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading && <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">Loading...</td></tr>}
            {(depts ?? []).map((d) => (
              <tr key={d.id} className="hover:bg-muted/30">
                <td className="px-4 py-3 font-mono text-xs font-semibold">{d.deptCode}</td>
                <td className="px-4 py-3">{d.deptName}</td>
                <td className="px-4 py-3 text-muted-foreground text-xs">{d.managerId ?? '—'}</td>
              </tr>
            ))}
            {!isLoading && !error && !depts?.length && (
              <tr><td colSpan={3} className="px-4 py-6 text-center text-muted-foreground text-sm">No departments found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
