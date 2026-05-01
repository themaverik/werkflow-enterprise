'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getProcessDefinitions } from '@/lib/api/flowable'
import { FilterPills } from '@/components/ui/filter-pills'
import { Clock, ChevronRight, Play } from 'lucide-react'
import { Button } from '@/components/ui/button'

const DEPT_COLORS: Record<string, string> = {
  HR: '#7c3aed',
  Finance: '#16a34a',
  IT: '#0891b2',
  Procurement: '#dc2626',
  default: '#6b7e8c',
}

export default function ServiceCatalogPage() {
  const { status } = useSession()
  const [activeDept, setActiveDept] = useState('all')

  const { data: processes, isLoading } = useQuery({
    queryKey: ['processDefinitions'],
    queryFn: getProcessDefinitions,
    enabled: status === 'authenticated',
    staleTime: 60000,
  })

  const departments = [
    'all',
    ...Array.from(
      new Set(
        (processes ?? []).map(
          (p) => (p as { owningDepartment?: string }).owningDepartment ?? 'General'
        )
      )
    ),
  ]
  const deptOptions = departments.map((d) => ({
    key: d,
    label: d === 'all' ? 'All Services' : d,
  }))

  const filtered =
    activeDept === 'all'
      ? (processes ?? [])
      : (processes ?? []).filter(
          (p) =>
            ((p as { owningDepartment?: string }).owningDepartment ?? 'General') === activeDept
        )

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Service Catalog</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Browse and start available workflows</p>
      </div>

      <FilterPills options={deptOptions} active={activeDept} onChange={setActiveDept} />

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="bg-card border border-border rounded-xl p-5 h-48 animate-pulse" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <p className="text-muted-foreground text-sm">No services available.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((process) => {
            const dept =
              (process as { owningDepartment?: string }).owningDepartment ?? 'General'
            const color = DEPT_COLORS[dept] ?? DEPT_COLORS.default
            return (
              <div
                key={process.id}
                className="bg-card border border-border rounded-xl p-5 flex flex-col gap-3 hover:shadow-sm transition-shadow"
              >
                <div className="flex items-center gap-2">
                  <div
                    className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                    style={{ background: color + '18' }}
                  >
                    <Play size={16} style={{ color }} strokeWidth={1.8} />
                  </div>
                  <div>
                    <p className="font-semibold text-foreground text-sm leading-snug">
                      {process.name || process.key}
                    </p>
                    <p className="text-xs text-muted-foreground">{dept}</p>
                  </div>
                </div>

                <p className="text-xs text-muted-foreground leading-relaxed flex-1">
                  Start this workflow to submit a request.
                </p>

                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Clock size={12} />
                  <span>Working days may vary</span>
                </div>

                <Button asChild size="sm" className="w-full mt-auto">
                  <Link href={`/processes/start/${process.key}`}>
                    Submit Request <ChevronRight size={14} className="ml-1" />
                  </Link>
                </Button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
