'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getProcessDefinitions } from '@/lib/api/flowable'
import { useCategories, useDepartments, usePlatformCapabilities } from '@/lib/platform/usePlatformCapabilities'
import { FeelChip, Note } from '@/components/design/panel-primitives'
import { ChevronRight, Play, Tag, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { CategoryEntry, DepartmentEntry } from '@/lib/platform/types'

// ── Visibility rule (ADR-010 §3, client-side) ─────────────────────────────
// admins/designers: see all
// managers with managerScope=ALL_DEPTS: see all
// employees: see own-dept + null-dept artifacts
// null departmentCode = visible to all

interface ProcessDef {
  id: string
  key: string
  name: string | null
  category: string | undefined
}

function matchesVisibility(
  process: ProcessDef,
  userDeptCode: string | undefined,
  userRoles: string[],
  managerScope: string | undefined,
): boolean {
  const isAdmin = userRoles.some((r) =>
    r === 'ROLE_ADMIN' || r === 'ROLE_DESIGNER' || r === 'ROLE_IT_ADMIN'
  )
  if (isAdmin) return true
  const isManager = userRoles.some((r) => r === 'ROLE_MANAGER' || r === 'ROLE_DEPT_MANAGER')
  if (isManager && managerScope === 'ALL_DEPTS') return true
  // process.category is used as departmentCode on the process definition
  const artifactDept = process.category ?? null
  if (!artifactDept) return true // visible to all
  return artifactDept === userDeptCode
}

// ── Category color fallback ────────────────────────────────────────────────
const DEFAULT_COLORS: string[] = [
  '#149ba5', '#16a34a', '#0891b2', '#7c3aed', '#c27b00', '#dc2626', '#475569',
]

function getCategoryColor(cat: CategoryEntry | undefined, index: number): string {
  return cat?.color ?? DEFAULT_COLORS[index % DEFAULT_COLORS.length]
}

// ── Main page ──────────────────────────────────────────────────────────────
export default function ServiceCatalogPage() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  const [activeDeptCode, setActiveDeptCode] = useState<string | null>(null)
  const [tagFilter, setTagFilter] = useState('')

  const { data: processes = [], isLoading, error } = useQuery({
    queryKey: ['processDefinitions'],
    queryFn: getProcessDefinitions,
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: categories = [] } = useCategories()
  const { data: departments = [] } = useDepartments()
  const { data: capabilities } = usePlatformCapabilities()

  // Derive user roles and dept from session token claims
  const userRoles: string[] = session?.user?.roles ?? []
  // departmentCode is not in the standard session type; access via unknown cast safely
  const userDeptCode: string | undefined = (session?.user as unknown as { departmentCode?: string })?.departmentCode
  const managerScope = capabilities?.configured?.visibilityPolicy?.managerScope

  // Client-side visibility filter
  const visible = (processes as ProcessDef[]).filter((p) =>
    matchesVisibility(p, userDeptCode, userRoles, managerScope)
  )
  const hidden = (processes as ProcessDef[]).filter((p) =>
    !matchesVisibility(p, userDeptCode, userRoles, managerScope)
  )

  // Apply dept chip filter
  const afterDeptFilter = activeDeptCode
    ? visible.filter((p) => p.category === activeDeptCode)
    : visible

  // Apply tag filter (tag matching against process name/key — no tag field on process defs yet)
  const afterTagFilter = tagFilter.trim()
    ? afterDeptFilter.filter((p) =>
        (p.name ?? p.key).toLowerCase().includes(tagFilter.toLowerCase().trim())
      )
    : afterDeptFilter

  // Group by PSS category code (process.category field)
  type GroupMap = Record<string, { category: CategoryEntry | undefined; colorIndex: number; items: ProcessDef[] }>
  const grouped: GroupMap = {}
  afterTagFilter.forEach((p) => {
    const code = p.category ?? '__uncategorized__'
    if (!grouped[code]) {
      const cat = categories.find((c) => c.code === code)
      const colorIndex = Object.keys(grouped).length
      grouped[code] = { category: cat, colorIndex, items: [] }
    }
    grouped[code].items.push(p)
  })

  const groupEntries = Object.entries(grouped).sort(([, a], [, b]) => {
    const aOrder = a.category?.displayOrder ?? 99
    const bOrder = b.category?.displayOrder ?? 99
    return aOrder - bOrder
  })

  // Dept filter pills from PSS departments
  const deptPills: DepartmentEntry[] = departments

  // Compute hidden dept breakdown for notice
  const hiddenByDept: Record<string, number> = {}
  hidden.forEach((p) => {
    const dept = p.category ?? 'Unknown'
    hiddenByDept[dept] = (hiddenByDept[dept] ?? 0) + 1
  })
  const hiddenCount = hidden.length
  const hiddenDescription = Object.entries(hiddenByDept)
    .map(([d, n]) => `${d} (${n})`)
    .join(', ')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* ── Page header ── */}
      <div>
        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f1e2a', margin: 0 }}>
          Service Catalog
        </h1>
        <p style={{ fontSize: '13px', color: '#6b7e8c', marginTop: '4px' }}>
          Browse and start available workflows
        </p>
      </div>

      {/* ── Filter bar ── */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
        {/* Dept chips */}
        <button
          type="button"
          onClick={() => setActiveDeptCode(null)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '4px 10px',
            borderRadius: '20px',
            fontSize: '12px',
            fontWeight: 600,
            cursor: 'pointer',
            border: `1px solid ${activeDeptCode === null ? '#149ba5' : '#e2eaee'}`,
            background: activeDeptCode === null ? '#f0fafb' : '#fff',
            color: activeDeptCode === null ? '#149ba5' : '#6b7e8c',
          }}
        >
          All Services
        </button>
        {deptPills.map((d) => (
          <button
            key={d.code}
            type="button"
            onClick={() => setActiveDeptCode(activeDeptCode === d.code ? null : d.code)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px',
              padding: '4px 10px',
              borderRadius: '20px',
              fontSize: '12px',
              fontWeight: 600,
              cursor: 'pointer',
              border: `1px solid ${activeDeptCode === d.code ? '#0c447c' : '#e2eaee'}`,
              background: activeDeptCode === d.code ? '#e6f1fb' : '#fff',
              color: activeDeptCode === d.code ? '#0c447c' : '#6b7e8c',
            }}
          >
            {d.displayName}
            {activeDeptCode === d.code && (
              <X size={10} aria-hidden="true" />
            )}
          </button>
        ))}

        {/* Tag filter input */}
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 10px',
            border: '1px solid #e2eaee',
            borderRadius: '20px',
            background: '#fff',
            fontSize: '12px',
          }}
        >
          <Tag size={12} style={{ color: '#6b7e8c' }} aria-hidden="true" />
          <input
            type="text"
            placeholder="Filter by tag or name…"
            value={tagFilter}
            onChange={(e) => setTagFilter(e.target.value)}
            aria-label="Filter services by name or tag"
            style={{
              border: 'none',
              outline: 'none',
              background: 'transparent',
              fontSize: '12px',
              color: '#0f1e2a',
              width: '160px',
              fontFamily: 'inherit',
            }}
          />
          {tagFilter && (
            <button
              type="button"
              onClick={() => setTagFilter('')}
              aria-label="Clear filter"
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#6b7e8c', padding: 0, display: 'flex' }}
            >
              <X size={11} aria-hidden="true" />
            </button>
          )}
        </div>
      </div>

      {/* ── Hidden artifacts notice ── */}
      {hiddenCount > 0 && (
        <Note variant="muted">
          {hiddenCount} artifact{hiddenCount !== 1 ? 's' : ''} hidden — {hiddenDescription}
        </Note>
      )}

      {/* ── Content ── */}
      {isLoading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '16px' }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              style={{
                background: '#fff',
                border: '1px solid #e2eaee',
                borderRadius: '12px',
                height: '180px',
                animation: 'pulse 1.5s ease-in-out infinite',
                opacity: 0.6,
              }}
            />
          ))}
        </div>
      ) : error ? (
        <Note variant="warn">Failed to load services. Please try again.</Note>
      ) : afterTagFilter.length === 0 ? (
        <p style={{ fontSize: '13px', color: '#6b7e8c' }}>No services match the current filter.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '28px' }}>
          {groupEntries.map(([code, group]) => {
            const catColor = getCategoryColor(group.category, group.colorIndex)
            const catLabel = group.category?.displayName ?? 'General'

            return (
              <section key={code} aria-label={catLabel}>
                {/* Category section header */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    marginBottom: '14px',
                    paddingBottom: '10px',
                    borderBottom: '1px solid #eef2f5',
                  }}
                >
                  <div
                    aria-hidden="true"
                    style={{
                      width: '28px',
                      height: '28px',
                      borderRadius: '8px',
                      background: catColor + '18',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <span style={{ fontSize: '14px' }}>{group.category?.icon ?? '□'}</span>
                  </div>
                  <div>
                    <p style={{ fontWeight: 700, fontSize: '14px', color: '#0f1e2a', margin: 0 }}>
                      {catLabel}
                    </p>
                    <p style={{ fontSize: '11px', color: '#6b7e8c', margin: 0 }}>
                      {group.items.length} service{group.items.length !== 1 ? 's' : ''}
                    </p>
                  </div>
                </div>

                {/* Process cards grid */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                    gap: '14px',
                  }}
                >
                  {group.items.map((process) => {
                    const deptCode = process.category
                    const deptEntry = deptCode
                      ? departments.find((d) => d.code === deptCode)
                      : undefined
                    const artifactDept = deptEntry?.displayName ?? deptCode ?? null

                    return (
                      <div
                        key={process.id}
                        style={{
                          background: '#fff',
                          border: '1px solid #e2eaee',
                          borderRadius: '12px',
                          padding: '18px',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '12px',
                        }}
                      >
                        {/* Card header */}
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                          <div
                            aria-hidden="true"
                            style={{
                              width: '36px',
                              height: '36px',
                              borderRadius: '8px',
                              background: catColor + '18',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              flexShrink: 0,
                            }}
                          >
                            <Play size={15} style={{ color: catColor }} strokeWidth={1.8} aria-hidden="true" />
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <p
                              style={{
                                fontWeight: 600,
                                fontSize: '13px',
                                color: '#0f1e2a',
                                margin: 0,
                                lineHeight: 1.3,
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {process.name || process.key}
                            </p>
                            {/* Dept visibility badge */}
                            <div style={{ marginTop: '4px' }}>
                              {artifactDept ? (
                                <FeelChip label={artifactDept.toUpperCase()} kind="dept" />
                              ) : (
                                <FeelChip label="all departments" kind="system" />
                              )}
                            </div>
                          </div>
                        </div>

                        <p style={{ fontSize: '12px', color: '#6b7e8c', lineHeight: 1.5, margin: 0, flex: 1 }}>
                          Start this workflow to submit a request.
                        </p>

                        <Button asChild size="sm" style={{ width: '100%' }}>
                          <Link href={`/processes/start/${process.key}`}>
                            Submit Request
                            <ChevronRight size={13} style={{ marginLeft: '4px' }} aria-hidden="true" />
                          </Link>
                        </Button>
                      </div>
                    )
                  })}
                </div>
              </section>
            )
          })}
        </div>
      )}
    </div>
  )
}
