'use client'

import Link from 'next/link'
import {
  Play, Pencil, Trash2, Plus,
  Search, GitBranch, Rocket, Workflow, Bell,
  Link2, FileText, History,
} from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import {
  getProcessDefinitions, deleteDeployment,
  listDrafts, deleteDraft,
} from '@/lib/api/flowable'
import { useState, useEffect, CSSProperties } from 'react'
import { useTranslations } from 'next-intl'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { useToast } from '@/hooks/use-toast'
import { toast } from 'sonner'
import { Skeleton } from '@/components/ui/skeleton'

// ─── Design tokens ────────────────────────────────────────────────────────────
const ACCENT = '#149ba5'
const T = {
  text: '#0f1e2a',
  muted: '#6b7e8c',
  light: '#94a3b8',
  bg: 'hsl(var(--muted))',
  card: 'hsl(var(--card))',
  border: 'hsl(var(--border))',
  warning: '#c27b00',
  warningBg: '#fffbeb',
  danger: '#dc2626',
}

// ─── Tag colour palette ───────────────────────────────────────────────────────
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

function primaryColorForProcess(tags: string[]): string {
  return tags.length > 0 ? tagColor(tags[0]) : ACCENT
}

function getProcessTags(process: ProcessDef): string[] {
  const seen = new Set<string>()
  const tags: string[] = []
  for (const raw of [process.owningDepartment, process.category]) {
    if (!raw) continue
    const label = slugifyTag(raw)
    if (!seen.has(label)) { seen.add(label); tags.push(label) }
  }
  return tags
}

const MANAGER_ROLES = [
  'ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER',
  'HR_ADMIN', 'FINANCE_ADMIN', 'PROCUREMENT_ADMIN', 'INVENTORY_ADMIN',
]

// ─── Shared style helpers ─────────────────────────────────────────────────────
const tagPill = (active: boolean): CSSProperties => ({
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
})

const iconBtn: CSSProperties = {
  width: 30,
  height: 30,
  borderRadius: 7,
  border: '1px solid ' + T.border,
  background: '#fff',
  cursor: 'pointer',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
}

// ─── How-to guide steps ───────────────────────────────────────────────────────
const STEP_COLORS = ['#149ba5', '#7c3aed', '#c2410c', '#0891b2', '#16a34a'] as const

const GUIDE_STEPS = [
  { title: 'Connect a form or Start blank', desc: 'Begin from a linked form or an empty BPMN canvas', num: 1 },
  { title: 'Design the flow',               desc: 'Drag tasks, gateways and events visually',          num: 2 },
  { title: 'Configure tasks and events',    desc: 'Set assignees, forms and DOA routing',              num: 3 },
  { title: 'Add conditions and link DMN',   desc: 'Branch with gateways and wire DMN decisions',       num: 4 },
  { title: 'Deploy',                        desc: 'Validate and push live to the engine',              num: 5 },
] as const

function StepIconSvg({ index, color }: { index: number; color: string }) {
  const paths: string[][] = [
    ['M12 5v14', 'M5 12h14'],
    ['M3 6h3l3 12 3-6 3 6 3-12h3'],
    ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm0 0v6h6', 'M8 13h8', 'M8 17h5'],
    ['M12 3 L21 12 L12 21 L3 12 Z', 'M9.4 9.4 L14.6 14.6', 'M14.6 9.4 L9.4 14.6'],
    ['M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z','M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z','M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0','M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5'],
  ]
  return (
    <svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round">
      {paths[index].map((d, j) => <path key={j} d={d} />)}
    </svg>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────
export default function ProcessesPage() {
  const t = useTranslations('processes')
  const { status } = useSession()
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deletingDraftKey, setDeletingDraftKey] = useState<string | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<{
    title: string
    description: string
    onConfirm: () => void
  } | null>(null)
  const [activeView, setActiveView] = useState<'deployed' | 'drafts'>('deployed')
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTag, setActiveTag] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const { toast: toastHook } = useToast()
  const { hasAnyRole, getDepartment } = useAuthorization()

  const isManagerOrAbove = hasAnyRole(MANAGER_ROLES)
  const userDepartment = getDepartment()

  const canEditProcess = (owningDepartment?: string): boolean => {
    if (!isManagerOrAbove) return false
    if (!owningDepartment) return isManagerOrAbove
    return hasAnyRole(['ADMIN', 'SUPER_ADMIN']) || owningDepartment === userDepartment
  }

  // ── Queries ──────────────────────────────────────────────────────────────────
  const {
    data: processes,
    isLoading,
    error: processesError,
    refetch,
  } = useQuery({
    queryKey: ['processDefinitions'],
    queryFn: getProcessDefinitions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
    staleTime: 60_000,
  })

  const {
    data: drafts,
    isLoading: draftsLoading,
    error: draftsError,
  } = useQuery({
    queryKey: ['processDrafts'],
    queryFn: listDrafts,
    enabled: status === 'authenticated' && isManagerOrAbove,
    retry: 2,
    retryDelay: 1000,
    staleTime: 60_000,
  })

  // Surface query errors via toast (no onError in query options — v5 pattern)
  useEffect(() => {
    if (processesError) {
      toast.error(t('failedToLoad') + ': ' + (processesError as Error).message)
    }
  }, [processesError, t])

  useEffect(() => {
    if (draftsError) {
      toast.error(t('failedToLoadDrafts') + ': ' + (draftsError as Error).message)
    }
  }, [draftsError, t])

  // ── Mutations ────────────────────────────────────────────────────────────────
  const deleteMutation = useMutation({
    mutationFn: deleteDeployment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['processDefinitions'] })
      setDeletingId(null)
      toastHook({ title: t('processDeleted'), description: t('processDeletedDesc') })
    },
    onError: (error: Error) => {
      toastHook({ title: t('deleteFailed'), description: error.message, variant: 'destructive' })
      setDeletingId(null)
    },
  })

  const deleteDraftMutation = useMutation({
    mutationFn: deleteDraft,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['processDrafts'] })
      setDeletingDraftKey(null)
    },
    onError: (error: Error) => {
      toastHook({ title: t('deleteFailed'), description: error.message, variant: 'destructive' })
      setDeletingDraftKey(null)
    },
  })

  // ── Grouping ─────────────────────────────────────────────────────────────────
  type ProcessDef = NonNullable<typeof processes>[number]

  const groupedProcesses = (processes ?? []).reduce<Record<string, ProcessDef[]>>(
    (acc, process) => {
      if (!acc[process.key]) acc[process.key] = []
      acc[process.key].push(process)
      return acc
    },
    {},
  )

  // ── Tag list from owningDepartment + category values ─────────────────────────
  const allTags = Array.from(
    new Set(
      (processes ?? []).flatMap((p) => getProcessTags(p as ProcessDef)),
    ),
  )

  // ── Filtered deployed processes ───────────────────────────────────────────────
  const filteredGroups = Object.entries(groupedProcesses).filter(([key, versions]) => {
    const latest = [...versions].sort((a, b) => b.version - a.version)[0]
    const name = latest.name || key
    const matchesSearch = name.toLowerCase().includes(searchQuery.toLowerCase())
    const tags = getProcessTags(latest as ProcessDef)
    const matchesTag = !activeTag || tags.includes(activeTag)
    return matchesSearch && matchesTag
  })

  // ── Filtered drafts ───────────────────────────────────────────────────────────
  const filteredDrafts = (drafts ?? []).filter((d) =>
    (d.name || d.processKey).toLowerCase().includes(searchQuery.toLowerCase()),
  )

  // ── Tab counts ───────────────────────────────────────────────────────────────
  const deployedCount = Object.keys(groupedProcesses).length
  const draftsCount = (drafts ?? []).length

  // ─────────────────────────────────────────────────────────────────────────────
  return (
    <div style={{ padding: 28 }}>

      {/* ── Header row: tabs + new button ───────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}>
        {/* Tab: Deployed */}
        <button
          onClick={() => setActiveView('deployed')}
          style={{
            padding: '6px 14px',
            borderRadius: 8,
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
            transition: 'all 0.15s',
            ...(activeView === 'deployed'
              ? { border: '1.5px solid ' + ACCENT, background: ACCENT + '14', color: ACCENT }
              : { border: '1px solid ' + T.border, background: '#fff', color: T.muted }),
          }}
        >
          Deployed ({deployedCount})
        </button>

        {/* Tab: Drafts — only visible to managers */}
        {isManagerOrAbove && (
          <button
            onClick={() => setActiveView('drafts')}
            style={{
              padding: '6px 14px',
              borderRadius: 8,
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s',
              ...(activeView === 'drafts'
                ? { border: '1.5px solid ' + ACCENT, background: ACCENT + '14', color: ACCENT }
                : { border: '1px solid ' + T.border, background: '#fff', color: T.muted }),
            }}
          >
            Drafts ({draftsCount})
          </button>
        )}

        {/* Spacer */}
        <div style={{ flex: 1 }} />

        {/* + New Process */}
        {isManagerOrAbove && (
          <Link
            href="/processes/new"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              background: ACCENT,
              color: '#fff',
              borderRadius: 8,
              padding: '7px 14px',
              fontSize: 13,
              fontWeight: 600,
              textDecoration: 'none',
              border: 'none',
              cursor: 'pointer',
            }}
          >
            <Plus size={14} strokeWidth={2.2} />
            {t('newProcess')}
          </Link>
        )}
      </div>

      {/* ── Search + tag filter bar ──────────────────────────────────────────── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          border: '1px solid ' + T.border,
          borderRadius: 10,
          background: '#fff',
          overflow: 'hidden',
          marginBottom: 22,
          height: 40,
        }}
      >
        {/* Search input */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px', flexShrink: 0 }}>
          <Search size={14} color={T.light} strokeWidth={2} />
          <input
            type="text"
            placeholder="Search processes…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              border: 'none',
              outline: 'none',
              fontSize: 13,
              color: T.text,
              background: 'transparent',
              width: 180,
            }}
          />
        </div>

        {/* Divider */}
        {allTags.length > 0 && (
          <div style={{ width: 1, height: 24, background: T.border, flexShrink: 0 }} />
        )}

        {/* Tag pills — horizontal scroll */}
        {allTags.length > 0 && (
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
            <span style={{ fontSize: 10, color: T.light, flexShrink: 0 }}>Filter:</span>
            <button
              onClick={() => setActiveTag(null)}
              style={tagPill(activeTag === null)}
            >
              All
            </button>
            {allTags.map((tag) => (
              <button
                key={tag}
                onClick={() => setActiveTag(activeTag === tag ? null : tag)}
                style={tagPill(activeTag === tag)}
              >
                {tag}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ── Loading state ────────────────────────────────────────────────────── */}
      {(isLoading || (activeView === 'drafts' && draftsLoading)) && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="rounded-xl border border-border bg-card p-4 space-y-3">
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
              <div className="flex gap-2 pt-1">
                <Skeleton className="h-5 w-16 rounded-full" />
                <Skeleton className="h-5 w-20 rounded-full" />
              </div>
              <div className="flex justify-between pt-2">
                <Skeleton className="h-7 w-20 rounded-md" />
                <Skeleton className="h-7 w-16 rounded-md" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ═══════════════════════════════════════════════════════════════════════
          DEPLOYED PROCESSES
      ══════════════════════════════════════════════════════════════════════════ */}
      {activeView === 'deployed' && !isLoading && (
        <>
          {filteredGroups.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '64px 0',
                textAlign: 'center',
              }}
            >
              <FileText size={40} color={T.light} strokeWidth={1.5} style={{ marginBottom: 12 }} />
              <div style={{ fontWeight: 600, fontSize: 14, color: T.text, marginBottom: 4 }}>
                {t('noProcessesDeployed')}
              </div>
              <div style={{ fontSize: 12, color: T.muted, marginBottom: 20 }}>{t('noProcessesDesc')}</div>
              {isManagerOrAbove && (
                <Link
                  href="/processes/new"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    background: 'transparent',
                    color: ACCENT,
                    borderRadius: 8,
                    padding: '6px 14px',
                    fontSize: 13,
                    fontWeight: 600,
                    textDecoration: 'none',
                    border: '1px solid ' + ACCENT,
                  }}
                >
                  <Plus size={13} strokeWidth={2} />
                  {t('createNewProcess')}
                </Link>
              )}
            </div>
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 16,
                marginBottom: 32,
              }}
            >
              {filteredGroups.map(([key, versions]) => {
                const sortedVersions = [...versions].sort((a, b) => b.version - a.version)
                const latest = sortedVersions[0]
                const displayName = latest.name || key

                return (
                  <DeployedCard
                    key={key}
                    processKey={key}
                    latest={latest}
                    sortedVersions={sortedVersions}
                    canEdit={canEditProcess(latest.owningDepartment)}
                    isDeleting={deletingId === latest.deploymentId}
                    onDelete={() =>
                      setPendingConfirm({
                        title: t('deleteProcess'),
                        description: `${t('deleteProcess')}: "${displayName}"? This action cannot be undone.`,
                        onConfirm: () => {
                          setDeletingId(latest.deploymentId)
                          deleteMutation.mutate(latest.deploymentId)
                        },
                      })
                    }
                  />
                )
              })}
            </div>
          )}
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════════
          DRAFTS
      ══════════════════════════════════════════════════════════════════════════ */}
      {activeView === 'drafts' && !draftsLoading && isManagerOrAbove && (
        <>
          {filteredDrafts.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '64px 0',
                textAlign: 'center',
              }}
            >
              <FileText size={40} color={T.light} strokeWidth={1.5} style={{ marginBottom: 12 }} />
              <div style={{ fontWeight: 600, fontSize: 14, color: T.text, marginBottom: 4 }}>No drafts yet</div>
              <div style={{ fontSize: 12, color: T.muted, marginBottom: 20 }}>Start designing a process and save it as a draft.</div>
              <Link
                href="/processes/new"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  background: 'transparent',
                  color: ACCENT,
                  borderRadius: 8,
                  padding: '6px 14px',
                  fontSize: 13,
                  fontWeight: 600,
                  textDecoration: 'none',
                  border: '1px solid ' + ACCENT,
                }}
              >
                <Plus size={13} strokeWidth={2} />
                Create your first process
              </Link>
            </div>
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 16,
                marginBottom: 32,
              }}
            >
              {filteredDrafts.map((draft) => {
                const linkedDeployedProcess = Object.values(groupedProcesses)
                  .flat()
                  .find((p) => p.key === draft.processKey)
                const deployedProcessName = linkedDeployedProcess?.name || draft.processKey

                return (
                  <DraftCard
                    key={draft.processKey}
                    draft={draft}
                    linkedDeployedProcessName={linkedDeployedProcess ? deployedProcessName : undefined}
                    isDeleting={deletingDraftKey === draft.processKey}
                    onDelete={() => {
                      const description = linkedDeployedProcess
                        ? `This draft is linked to the deployed process "${deployedProcessName}". Deleting it will also remove the resume option from the edit page. This action cannot be undone.`
                        : `Delete draft: "${draft.name || draft.processKey}"? This action cannot be undone.`
                      setPendingConfirm({
                        title: 'Delete Draft',
                        description,
                        onConfirm: () => {
                          setDeletingDraftKey(draft.processKey)
                          deleteDraftMutation.mutate(draft.processKey)
                        },
                      })
                    }}
                  />
                )
              })}
            </div>
          )}
        </>
      )}

      {/* ── How-to guide ────────────────────────────────────────────────────── */}
      <GuideSection isManagerOrAbove={isManagerOrAbove} />

      {/* ── Confirm dialog ───────────────────────────────────────────────────── */}
      {pendingConfirm && (
        <ConfirmDialog
          open={true}
          onOpenChange={(open) => { if (!open) setPendingConfirm(null) }}
          title={pendingConfirm.title}
          description={pendingConfirm.description}
          confirmLabel="Delete"
          onConfirm={pendingConfirm.onConfirm}
        />
      )}
    </div>
  )
}

// ─── Deployed process card ────────────────────────────────────────────────────
interface ProcessDef {
  id: string
  key: string
  name: string
  version: number
  deploymentId: string
  owningDepartment?: string
  category?: string
  hasStartFormKey?: boolean
  startFormKey?: string
  hasDmn?: boolean
  hasConnector?: boolean
  hasNotification?: boolean
}

function DeployedCard({
  processKey,
  latest,
  sortedVersions,
  canEdit,
  isDeleting,
  onDelete,
}: {
  processKey: string
  latest: ProcessDef
  sortedVersions: ProcessDef[]
  canEdit: boolean
  isDeleting: boolean
  onDelete: () => void
}) {
  const displayName = latest.name || processKey
  const [hoverEdit, setHoverEdit] = useState(false)
  const [hoverDel, setHoverDel] = useState(false)
  const [hoverConn, setHoverConn] = useState(false)
  const [hoverDmn, setHoverDmn] = useState(false)
  const [hoverNotif, setHoverNotif] = useState(false)
  const [hoverForm, setHoverForm] = useState(false)

  const tags = getProcessTags(latest)
  const primaryColor = primaryColorForProcess(tags)
  const hasForm = Boolean(latest.hasStartFormKey || latest.startFormKey)
  const hasDmn = Boolean(latest.hasDmn)
  const hasConnector = Boolean(latest.hasConnector)
  const hasNotification = Boolean(latest.hasNotification)

  return (
    <div
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
      {/* Top row: icon + name + version badge + tags */}
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 11,
            background: primaryColor + '20',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <GitBranch size={20} color={primaryColor} strokeWidth={1.8} />
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 14, fontWeight: 600, color: T.text, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {displayName}
            </span>
            <span
              style={{
                fontSize: 10,
                padding: '1px 6px',
                borderRadius: 99,
                background: primaryColor + '18',
                color: primaryColor,
                border: '1px solid ' + primaryColor + '40',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              v{latest.version}
            </span>
          </div>

          {tags.length > 0 && (
            <div style={{ display: 'flex', gap: 4, marginTop: 5, flexWrap: 'wrap' }}>
              {tags.map((tag) => (
                <span
                  key={tag}
                  style={{
                    fontSize: 10,
                    padding: '2px 7px',
                    borderRadius: 99,
                    background: tagColor(tag) + '18',
                    color: tagColor(tag),
                    border: '1px solid ' + tagColor(tag) + '40',
                    fontWeight: 500,
                  }}
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Stats row */}
      <div style={{ display: 'flex', gap: 20 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: T.text, lineHeight: 1.1 }}>0</div>
          <div style={{ fontSize: 10, color: T.muted, marginTop: 2 }}>Active instances</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 4 }}>
          <div>
            <div style={{ fontSize: 20, fontWeight: 700, color: T.text, lineHeight: 1.1 }}>{sortedVersions.length}</div>
            <div style={{ fontSize: 10, color: T.muted, marginTop: 2 }}>Versions deployed</div>
          </div>
          {sortedVersions.length > 1 && (
            <span title={`Deployed versions: v${sortedVersions.map(v => v.version).join(', v')}`} style={{ display: 'flex', marginTop: 3 }}>
              <History size={11} color={T.light} strokeWidth={2} />
            </span>
          )}
        </div>
      </div>

      {/* Process Custody */}
      {(latest.owningDepartment || latest.category) && (
        <div style={{ borderTop: '1px solid ' + T.border, paddingTop: 10 }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
            Process Custody
          </div>
          <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', columnGap: 8, rowGap: 3 }}>
            <dt style={{ fontSize: 10, color: T.light }}>Department</dt>
            <dd style={{ fontSize: 10, color: T.muted, fontFamily: 'monospace' }}>{latest.owningDepartment ?? '—'}</dd>
            <dt style={{ fontSize: 10, color: T.light }}>Category</dt>
            <dd style={{ fontSize: 10, color: T.muted, fontFamily: 'monospace' }}>{latest.category ?? '—'}</dd>
          </dl>
        </div>
      )}

      {/* Divider */}
      <div style={{ height: 1, background: T.border }} />

      {/* Action row: Start Process + icon buttons */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {/* Start Process */}
        <Link
          href={`/processes/start/${processKey}`}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 5,
            background: primaryColor,
            color: '#fff',
            borderRadius: 7,
            padding: '6px 12px',
            fontSize: 12,
            fontWeight: 600,
            textDecoration: 'none',
            whiteSpace: 'nowrap',
            flexShrink: 0,
          }}
        >
          <Play size={11} strokeWidth={2.5} />
          Start Process
        </Link>

        <div style={{ flex: 1 }} />

        {/* Edit */}
        {canEdit ? (
          <Link
            href={`/processes/edit/${latest.id}`}
            style={{ ...iconBtn, background: hoverEdit ? T.bg : '#fff', textDecoration: 'none' }}
            title="Edit process"
            onMouseEnter={() => setHoverEdit(true)}
            onMouseLeave={() => setHoverEdit(false)}
          >
            <Pencil size={13} color={T.muted} strokeWidth={2} />
          </Link>
        ) : (
          <span style={{ ...iconBtn, opacity: 0.3, cursor: 'not-allowed' }} title="No edit permission">
            <Pencil size={13} color={T.light} strokeWidth={2} />
          </span>
        )}

        {/* Connector — enabled only if process invokes a connector delegate */}
        {hasConnector ? (
          <Link
            href="/admin/connectors"
            style={{ ...iconBtn, background: hoverConn ? T.bg : '#fff', textDecoration: 'none' }}
            title="View connectors"
            onMouseEnter={() => setHoverConn(true)}
            onMouseLeave={() => setHoverConn(false)}
          >
            <Link2 size={13} color={T.muted} strokeWidth={2} />
          </Link>
        ) : (
          <span
            role="img"
            aria-label="No connectors configured"
            style={{ ...iconBtn, opacity: 0.3, cursor: 'not-allowed' }}
            title="No connectors configured"
          >
            <Link2 size={13} color={T.light} strokeWidth={2} />
          </span>
        )}

        {/* DMN — enabled only if process invokes a DMN decision */}
        {hasDmn ? (
          <Link
            href="/decisions"
            style={{ ...iconBtn, background: hoverDmn ? T.bg : '#fff', textDecoration: 'none' }}
            title="View DMN decisions"
            onMouseEnter={() => setHoverDmn(true)}
            onMouseLeave={() => setHoverDmn(false)}
          >
            <Workflow size={13} color={T.muted} strokeWidth={2} />
          </Link>
        ) : (
          <span
            role="img"
            aria-label="No DMN configured"
            style={{ ...iconBtn, opacity: 0.3, cursor: 'not-allowed' }}
            title="No DMN configured"
          >
            <Workflow size={13} color={T.light} strokeWidth={2} />
          </span>
        )}

        {/* Notification — enabled only if process invokes the notification delegate */}
        {hasNotification ? (
          <Link
            href="/admin/email-templates"
            style={{ ...iconBtn, background: hoverNotif ? T.bg : '#fff', textDecoration: 'none' }}
            title="View notification templates"
            onMouseEnter={() => setHoverNotif(true)}
            onMouseLeave={() => setHoverNotif(false)}
          >
            <Bell size={13} color={T.muted} strokeWidth={2} />
          </Link>
        ) : (
          <span
            role="img"
            aria-label="No notifications configured"
            style={{ ...iconBtn, opacity: 0.3, cursor: 'not-allowed' }}
            title="No notifications configured"
          >
            <Bell size={13} color={T.light} strokeWidth={2} />
          </span>
        )}

        {/* Form — enabled only if process has a start form */}
        {hasForm ? (
          <Link
            href={`/processes/start/${processKey}`}
            style={{ ...iconBtn, background: hoverForm ? T.bg : '#fff', textDecoration: 'none' }}
            title="Open start form"
            onMouseEnter={() => setHoverForm(true)}
            onMouseLeave={() => setHoverForm(false)}
          >
            <FileText size={13} color={T.muted} strokeWidth={2} />
          </Link>
        ) : (
          <span
            role="img"
            aria-label="No form configured"
            style={{ ...iconBtn, opacity: 0.3, cursor: 'not-allowed' }}
            title="No form configured"
          >
            <FileText size={13} color={T.light} strokeWidth={2} />
          </span>
        )}

        {/* Delete */}
        {canEdit && (
          <button
            onClick={onDelete}
            disabled={isDeleting}
            style={{
              ...iconBtn,
              background: hoverDel ? '#fff1f1' : '#fff',
              border: '1px solid ' + (hoverDel ? '#fca5a5' : T.border),
              opacity: isDeleting ? 0.5 : 1,
            }}
            title="Delete process"
            onMouseEnter={() => setHoverDel(true)}
            onMouseLeave={() => setHoverDel(false)}
          >
            <Trash2 size={13} color={hoverDel ? T.danger : T.muted} strokeWidth={2} />
          </button>
        )}
      </div>
    </div>
  )
}

// ─── Draft card ───────────────────────────────────────────────────────────────
interface DraftSummary {
  processKey: string
  name: string
  updatedAt?: string
  departmentCode?: string
  categoryCode?: string
  tags?: string[]
}

function DraftCard({
  draft,
  linkedDeployedProcessName,
  isDeleting,
  onDelete,
}: {
  draft: DraftSummary
  linkedDeployedProcessName?: string
  isDeleting: boolean
  onDelete: () => void
}) {
  const displayName = draft.name || draft.processKey
  const [hoverEdit, setHoverEdit] = useState(false)
  const [hoverDel, setHoverDel] = useState(false)

  return (
    <div
      style={{
        background: T.card,
        border: '1.5px dashed ' + T.border,
        borderRadius: 12,
        padding: 20,
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
        position: 'relative',
      }}
    >
      {/* Draft badge top-right */}
      <span
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          fontSize: 10,
          fontWeight: 600,
          padding: '2px 7px',
          borderRadius: 99,
          background: T.warningBg,
          color: T.warning,
          border: '1px solid #fde68a',
        }}
      >
        Draft
      </span>

      {/* Top row: icon + name */}
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 11,
            background: T.warning + '22',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <GitBranch size={20} color={T.warning} strokeWidth={1.8} />
        </div>

        <div style={{ flex: 1, minWidth: 0, paddingRight: 48 }}>
          <div
            style={{
              fontSize: 14,
              fontWeight: 600,
              color: T.text,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {displayName}
          </div>
          <div style={{ fontSize: 11, color: T.muted, marginTop: 2 }}>
            {draft.processKey}
          </div>
          {draft.updatedAt && (
            <div style={{ fontSize: 10, color: T.light, marginTop: 2 }}>
              Last edited {new Date(draft.updatedAt).toLocaleDateString()}
            </div>
          )}
          {linkedDeployedProcessName && (
            <span
              style={{
                display: 'inline-block',
                marginTop: 4,
                fontSize: 10,
                fontWeight: 600,
                padding: '2px 7px',
                borderRadius: 99,
                background: T.warningBg,
                color: T.warning,
                border: '1px solid #fde68a',
              }}
            >
              Draft for deployed process
            </span>
          )}
        </div>
      </div>

      {/* Test Workflow button (amber) */}
      <Link
        href={`/processes/new?draft=${draft.processKey}`}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 6,
          background: T.warning,
          color: '#fff',
          borderRadius: 7,
          padding: '7px 14px',
          fontSize: 12,
          fontWeight: 600,
          textDecoration: 'none',
        }}
      >
        <Play size={12} strokeWidth={2.2} />
        Test Workflow
      </Link>

      {/* Process Custody */}
      {(draft.departmentCode || draft.categoryCode || (draft.tags && draft.tags.length > 0)) && (
        <div style={{ borderTop: '1px solid ' + T.border, paddingTop: 10 }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
            Process Custody
          </div>
          <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', columnGap: 8, rowGap: 3 }}>
            <dt style={{ fontSize: 10, color: T.light }}>Department</dt>
            <dd style={{ fontSize: 10, color: T.muted, fontFamily: 'monospace' }}>{draft.departmentCode ?? '—'}</dd>
            <dt style={{ fontSize: 10, color: T.light }}>Category</dt>
            <dd style={{ fontSize: 10, color: T.muted, fontFamily: 'monospace' }}>{draft.categoryCode ?? '—'}</dd>
            <dt style={{ fontSize: 10, color: T.light }}>Tags</dt>
            <dd style={{ fontSize: 10, color: T.muted }}>
              {draft.tags && draft.tags.length > 0 ? draft.tags.join(', ') : '—'}
            </dd>
          </dl>
        </div>
      )}

      {/* Icon action buttons */}
      <div style={{ display: 'flex', gap: 6 }}>
        <Link
          href={`/processes/new?draft=${draft.processKey}`}
          style={{
            ...iconBtn,
            background: hoverEdit ? T.bg : '#fff',
            textDecoration: 'none',
          }}
          title="Edit draft"
          onMouseEnter={() => setHoverEdit(true)}
          onMouseLeave={() => setHoverEdit(false)}
        >
          <Pencil size={13} color={T.muted} strokeWidth={2} />
        </Link>
        <button
          onClick={onDelete}
          disabled={isDeleting}
          style={{
            ...iconBtn,
            background: hoverDel ? '#fff1f1' : '#fff',
            border: '1px solid ' + (hoverDel ? '#fca5a5' : T.border),
            opacity: isDeleting ? 0.5 : 1,
          }}
          title="Delete draft"
          onMouseEnter={() => setHoverDel(true)}
          onMouseLeave={() => setHoverDel(false)}
        >
          <Trash2 size={13} color={hoverDel ? T.danger : T.muted} strokeWidth={2} />
        </button>
      </div>
    </div>
  )
}

// ─── How-to guide section ─────────────────────────────────────────────────────
function GuideSection({ isManagerOrAbove }: { isManagerOrAbove: boolean }) {
  return (
    <div
      style={{
        background: T.card,
        border: '1px solid ' + T.border,
        borderRadius: 12,
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <div
        style={{
          background: 'linear-gradient(90deg, #f0fdf9, #f0f9fa)',
          padding: '18px 24px',
          borderBottom: '1px solid ' + T.border,
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontSize: 15,
            fontWeight: 700,
            color: T.text,
          }}
        >
          <Rocket size={18} color={ACCENT} strokeWidth={1.8} />
          How to create a process
        </div>
        <div style={{ fontSize: 12, color: T.muted, marginTop: 4 }}>
          Five steps to design and deploy a BPMN workflow
        </div>
      </div>

      {/* Steps — gradient rail */}
      <div style={{ padding: '28px 24px 24px', position: 'relative' }}>
        {/* Connector rail behind nodes */}
        <div style={{
          position: 'absolute', top: 56, left: '10%', right: '10%',
          height: 2.5, borderRadius: 2, opacity: 0.45, zIndex: 0,
          background: 'linear-gradient(90deg,#149ba5,#7c3aed,#c2410c,#0891b2,#16a34a)',
        }} />
        <div style={{ position: 'relative', zIndex: 1, display: 'flex', gap: 8 }}>
          {GUIDE_STEPS.map((step, i) => {
            const color = STEP_COLORS[i]
            return (
              <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                {/* Icon node */}
                <div style={{
                  position: 'relative', width: 56, height: 56, borderRadius: 16,
                  background: '#fff', border: `1.5px solid ${color}30`,
                  boxShadow: '0 1px 2px rgba(15,30,42,0.05)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <div style={{
                    width: 44, height: 44, borderRadius: 12,
                    background: color + '18',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <StepIconSvg index={i} color={color} />
                  </div>
                  {/* Number chip */}
                  <div style={{
                    position: 'absolute', top: -8, right: -8,
                    width: 22, height: 22, borderRadius: 99,
                    background: color, color: '#fff',
                    fontSize: 11, fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    border: '2.5px solid #fff',
                    boxShadow: '0 1px 3px rgba(15,30,42,0.18)',
                  }}>
                    {step.num}
                  </div>
                </div>
                {/* Title */}
                <div style={{
                  marginTop: 16, fontSize: 13, fontWeight: 700, color: T.text,
                  textAlign: 'center', lineHeight: 1.35, maxWidth: 170,
                  minHeight: 53, display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
                }}>
                  {step.title}
                </div>
                {/* Description */}
                <div style={{
                  marginTop: 5, fontSize: 11.5, color: T.muted,
                  lineHeight: 1.55, textAlign: 'center', maxWidth: 168,
                }}>
                  {step.desc}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* CTA for managers */}
      {isManagerOrAbove && (
        <div
          style={{
            padding: '16px 24px',
            background: 'linear-gradient(90deg, #f0fdf9, #f0f9fa)',
            borderTop: '1px solid ' + T.border,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span style={{ fontSize: 13, color: T.muted }}>
            Ready to design your first workflow?
          </span>
          <Link
            href="/processes/new"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              background: ACCENT,
              color: '#fff',
              borderRadius: 8,
              padding: '7px 14px',
              fontSize: 13,
              fontWeight: 600,
              textDecoration: 'none',
            }}
          >
            <Plus size={13} strokeWidth={2} />
            Create New Process
          </Link>
        </div>
      )}
    </div>
  )
}
