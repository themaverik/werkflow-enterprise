'use client'

import { useState, type CSSProperties } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getProcessDefinitions } from '@/lib/api/flowable'
import { useCategories, useDepartments } from '@/lib/platform/usePlatformCapabilities'
import { Note } from '@/components/design/panel-primitives'
import { ChevronRight, Play, Search } from 'lucide-react'
import type { CategoryEntry, DepartmentEntry } from '@/lib/platform/types'

interface ProcessDef {
  id: string
  key: string
  name: string | null
  category: string | undefined
  hasStartFormKey?: boolean
  startFormKey?: string
}

interface VisibleProcessEntry {
  processKey: string
  name: string
  departmentCode: string | null
}

// ── Category color fallback ────────────────────────────────────────────────
const DEFAULT_COLORS: string[] = [
  '#149ba5', '#16a34a', '#0891b2', '#7c3aed', '#c27b00', '#dc2626', '#475569',
]

function getCategoryColor(cat: CategoryEntry | undefined, index: number): string {
  return cat?.color ?? DEFAULT_COLORS[index % DEFAULT_COLORS.length]
}

// Shared theme tokens (mirrors /processes page.tsx — keep in sync to prevent drift)
const ACCENT = '#149ba5'
const T = {
  text: '#0f1e2a',
  muted: '#6b7e8c',
  light: '#94a3b8',
  bg: 'hsl(var(--muted))',
  card: 'hsl(var(--card))',
  border: 'hsl(var(--border))',
}

// Category tag palette + helpers — MIRROR OF /processes page.tsx (lines 38-66).
// Keep in sync until extracted to lib/process-tags.ts.
const TAG_PALETTE: Record<string, string> = {
  'Approval':    '#2563eb',
  'Legal':       '#7c3aed',
  'Operations':  '#0891b2',
  'Procurement': '#059669',
  'Finance':     '#d97706',
  'HR':          '#dc2626',
  'IT':          '#6b7280',
  'Onboarding':  '#0d9488',
  'Cap-Ex':      '#b45309',
  'Expense':     '#c026d3',
}
const FALLBACK_COLORS = [
  '#2563eb', '#7c3aed', '#059669', '#d97706',
  '#dc2626', '#0891b2', '#c026d3', '#b45309',
]
function slugifyTag(raw: string): string {
  const segment = raw.split('/').filter(Boolean).pop() ?? raw
  return segment.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}
function tagColor(tag: string): string {
  if (TAG_PALETTE[tag]) return TAG_PALETTE[tag]
  let hash = 0
  for (let i = 0; i < tag.length; i++) hash = (hash * 31 + tag.charCodeAt(i)) >>> 0
  return FALLBACK_COLORS[hash % FALLBACK_COLORS.length]
}

// Department filter pill — mirrors the tagPill pattern in /processes for visual parity
const deptPill = (active: boolean): CSSProperties => ({
  fontSize: 10,
  padding: '2px 7px',
  borderRadius: 99,
  background: active ? ACCENT + '14' : T.bg,
  color: active ? ACCENT : T.muted,
  border: active ? '1.5px solid ' + ACCENT : '1px solid ' + T.border,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
  userSelect: 'none',
  lineHeight: '18px',
  fontWeight: 600,
})

// ── Main page ──────────────────────────────────────────────────────────────
export default function ServiceCatalogPage() {
  const { data: session, status } = useSession()
  const token = (session?.accessToken as string) ?? ''

  const [activeDeptCode, setActiveDeptCode] = useState<string | null>(null)
  const [nameFilter, setNameFilter] = useState('')

  const { data: processes = [], isLoading, error } = useQuery({
    queryKey: ['processDefinitions'],
    queryFn: getProcessDefinitions,
    enabled: status === 'authenticated',
    staleTime: 60_000,
  })

  const { data: categories = [] } = useCategories()
  const { data: departments = [] } = useDepartments()

  // Server-side visibility: fetch visible process keys per ADR-010 §3
  const { data: visibleKeys } = useQuery<VisibleProcessEntry[] | null>({
    queryKey: ['pss', 'visibleProcesses'],
    queryFn: async () => {
      const res = await fetch('/api/proxy/admin/platform/visible-processes', {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!res.ok) return null // null = unrestricted (admin or endpoint unavailable)
      return res.json() as Promise<VisibleProcessEntry[]>
    },
    enabled: status === 'authenticated',
    staleTime: 2 * 60 * 1000,
  })

  // Server-side visibility filter: if visibleKeys is non-null, restrict to those keys only
  const visible = visibleKeys !== null && visibleKeys !== undefined
    ? (processes as ProcessDef[]).filter((p) =>
        visibleKeys.some((vk) => vk.processKey === p.key)
      )
    : (processes as ProcessDef[])

  // Apply dept chip filter
  const afterDeptFilter = activeDeptCode
    ? visible.filter((p) => p.category === activeDeptCode)
    : visible

  // Apply tag filter (tag matching against process name/key — no tag field on process defs yet)
  const afterTagFilter = nameFilter.trim()
    ? afterDeptFilter.filter((p) =>
        (p.name ?? p.key).toLowerCase().includes(nameFilter.toLowerCase().trim())
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* ── Page header ── */}
      <div>
        <h1 style={{ fontSize: '22px', fontWeight: 700, color: T.text, margin: 0 }}>
          Service Catalog
        </h1>
        <p style={{ fontSize: '13px', color: T.muted, marginTop: '4px' }}>
          Browse and start available workflows
        </p>
      </div>

      {/* ── Search + dept filter bar (matches /processes pattern) ────────────── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          border: '1px solid ' + T.border,
          borderRadius: 10,
          background: '#fff',
          overflow: 'hidden',
          height: 40,
        }}
      >
        {/* Search input */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px', flexShrink: 0 }}>
          <Search size={14} color={T.light} strokeWidth={2} aria-hidden="true" />
          <input
            type="text"
            placeholder="Search services…"
            value={nameFilter}
            onChange={(e) => setNameFilter(e.target.value)}
            aria-label="Search services by name"
            style={{
              border: 'none',
              outline: 'none',
              fontSize: 13,
              color: T.text,
              background: 'transparent',
              width: 180,
              fontFamily: 'inherit',
            }}
          />
        </div>

        {/* Divider */}
        {deptPills.length > 0 && (
          <div style={{ width: 1, height: 24, background: T.border, flexShrink: 0 }} />
        )}

        {/* Department pills — horizontal scroll */}
        {deptPills.length > 0 && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '0 12px',
              overflowX: 'auto',
              flex: 1,
            }}
          >
            <span style={{ fontSize: 10, color: T.light, flexShrink: 0 }}>Department:</span>
            <button
              type="button"
              onClick={() => setActiveDeptCode(null)}
              style={deptPill(activeDeptCode === null)}
            >
              All
            </button>
            {deptPills.map((d) => (
              <button
                key={d.code}
                type="button"
                onClick={() => setActiveDeptCode(activeDeptCode === d.code ? null : d.code)}
                style={deptPill(activeDeptCode === d.code)}
              >
                {d.displayName}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ── Content ── */}
      {isLoading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '16px' }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              style={{
                background: T.card,
                border: '1px solid ' + T.border,
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
        <p style={{ fontSize: '13px', color: T.muted }}>No services match the current filter.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '28px' }}>
          {groupEntries.map(([code, group]) => {
            const catColor = getCategoryColor(group.category, group.colorIndex)
            const catLabel = group.category?.displayName
              ?? (code === '__uncategorized__' ? 'General' : slugifyTag(code))

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
                    borderBottom: '1px solid ' + T.border,
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
                    {group.category?.icon ? (
                      <span style={{ fontSize: '14px' }}>{group.category.icon}</span>
                    ) : (
                      <Play size={14} style={{ color: catColor }} strokeWidth={2} aria-hidden="true" />
                    )}
                  </div>
                  <div>
                    <p style={{ fontWeight: 700, fontSize: '14px', color: T.text, margin: 0 }}>
                      {catLabel}
                    </p>
                    <p style={{ fontSize: '11px', color: T.muted, margin: 0 }}>
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
                    const rawCategory = process.category ?? ''
                    const categoryTag = rawCategory ? slugifyTag(rawCategory) : null
                    const hasStartForm = Boolean(process.hasStartFormKey || process.startFormKey)

                    return (
                      <div
                        key={process.id}
                        style={{
                          background: T.card,
                          border: '1px solid ' + T.border,
                          borderRadius: 12,
                          padding: 20,
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 12,
                        }}
                      >
                        {/* Top row: icon + name + dept badge */}
                        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                          <div
                            aria-hidden="true"
                            style={{
                              width: 44,
                              height: 44,
                              borderRadius: 11,
                              background: catColor + '20',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              flexShrink: 0,
                            }}
                          >
                            <Play size={20} style={{ color: catColor }} strokeWidth={1.8} aria-hidden="true" />
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                              <span
                                style={{
                                  fontSize: 14,
                                  fontWeight: 600,
                                  color: T.text,
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                }}
                              >
                                {process.name || process.key}
                              </span>
                            </div>
                            {categoryTag && (
                              <div style={{ display: 'flex', gap: 4, marginTop: 5, flexWrap: 'wrap' }}>
                                <span
                                  style={{
                                    fontSize: 10,
                                    padding: '2px 7px',
                                    borderRadius: 99,
                                    background: tagColor(categoryTag) + '18',
                                    color: tagColor(categoryTag),
                                    border: '1px solid ' + tagColor(categoryTag) + '40',
                                    fontWeight: 500,
                                  }}
                                >
                                  {categoryTag}
                                </span>
                              </div>
                            )}
                          </div>
                        </div>

                        <p style={{ fontSize: 12, color: T.muted, lineHeight: 1.5, margin: 0, flex: 1 }}>
                          {hasStartForm
                            ? 'Start this workflow to submit a request.'
                            : 'No intake form.'}
                        </p>

                        {/* Divider + action row: only rendered when the process has a start form */}
                        {hasStartForm && (
                          <>
                            {/* Divider */}
                            <div style={{ height: 1, background: T.border }} />

                            {/* Action row: Submit Request (consumer label; button styling matches /processes DeployedCard) */}
                            <div style={{ display: 'flex', alignItems: 'center' }}>
                              <Link
                                href={`/processes/start/${process.key}?from=services`}
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: 5,
                                  background: catColor,
                                  color: '#fff',
                                  borderRadius: 7,
                                  padding: '6px 12px',
                                  fontSize: 12,
                                  fontWeight: 600,
                                  textDecoration: 'none',
                                  whiteSpace: 'nowrap',
                                }}
                              >
                                Submit Request
                                <ChevronRight size={13} strokeWidth={2.5} aria-hidden="true" />
                              </Link>
                            </div>
                          </>
                        )}
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
