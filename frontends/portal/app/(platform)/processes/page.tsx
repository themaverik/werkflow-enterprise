'use client'

import Link from 'next/link'
import {
  Play, Pencil, Trash2, Download, Plus, ChevronRight,
  Search, GitBranch, SlidersHorizontal, GitMerge, Rocket,
} from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import {
  getProcessDefinitions, deleteDeployment, getProcessDefinitionXml,
  listDrafts, deleteDraft,
} from '@/lib/api/flowable'
import { downloadBpmn } from '@/lib/bpmn/utils'
import { useState, useEffect, CSSProperties } from 'react'
import { useTranslations } from 'next-intl'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { useToast } from '@/hooks/use-toast'
import { toast } from 'sonner'

// ─── Design tokens ────────────────────────────────────────────────────────────
const ACCENT = '#149ba5'
const T = {
  text: '#0f1e2a',
  muted: '#6b7e8c',
  light: '#94a3b8',
  bg: '#f0f4f6',
  card: '#ffffff',
  border: '#e2eaee',
  warning: '#c27b00',
  warningBg: '#fffbeb',
  danger: '#dc2626',
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
const GUIDE_STEPS = [
  {
    Icon: Plus,
    title: 'Start blank',
    desc: 'Open the BPMN designer and begin on a blank canvas or import an existing file.',
    num: 1,
  },
  {
    Icon: GitBranch,
    title: 'Design flow',
    desc: 'Drag tasks, gateways, and events to build the process flow visually.',
    num: 2,
  },
  {
    Icon: SlidersHorizontal,
    title: 'Configure tasks',
    desc: 'Set assignees, candidate groups, form keys, and DOA-level routing per task.',
    num: 3,
  },
  {
    Icon: GitMerge,
    title: 'Add conditions',
    desc: 'Wire exclusive gateways with JUEL expressions to route flows by amount or status.',
    num: 4,
  },
  {
    Icon: Rocket,
    title: 'Deploy',
    desc: 'Validate and push live — the BPMN is deployed to the Flowable engine immediately.',
    num: 5,
  },
] as const

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

  // ── Download handler ─────────────────────────────────────────────────────────
  const handleDownload = async (processDefinitionId: string, name: string) => {
    try {
      const xml = await getProcessDefinitionXml(processDefinitionId)
      downloadBpmn(xml, `${name}.bpmn20.xml`)
    } catch (err) {
      toastHook({
        title: t('downloadFailed'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      })
    }
  }

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

  // ── Tag list from owningDepartment values ────────────────────────────────────
  const allTags = Array.from(
    new Set(
      (processes ?? [])
        .map((p) => p.owningDepartment)
        .filter((d): d is string => Boolean(d)),
    ),
  )

  // ── Filtered deployed processes ───────────────────────────────────────────────
  const filteredGroups = Object.entries(groupedProcesses).filter(([key, versions]) => {
    const latest = [...versions].sort((a, b) => b.version - a.version)[0]
    const name = latest.name || key
    const matchesSearch = name.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesTag = !activeTag || latest.owningDepartment === activeTag
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
        <div
          style={{
            textAlign: 'center',
            padding: '48px 0',
            color: T.muted,
            fontSize: 13,
          }}
        >
          Loading…
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
                textAlign: 'center',
                padding: '56px 0',
                color: T.muted,
                fontSize: 14,
              }}
            >
              <div style={{ fontSize: 40, marginBottom: 12 }}>📋</div>
              <div style={{ fontWeight: 600, color: T.text, marginBottom: 6 }}>
                {t('noProcessesDeployed')}
              </div>
              <div style={{ fontSize: 13, marginBottom: 20 }}>{t('noProcessesDesc')}</div>
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
                    padding: '8px 16px',
                    fontSize: 13,
                    fontWeight: 600,
                    textDecoration: 'none',
                  }}
                >
                  <Plus size={14} strokeWidth={2} />
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
                    onDownload={() => handleDownload(latest.id, displayName)}
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
                textAlign: 'center',
                padding: '56px 0',
                color: T.muted,
                fontSize: 14,
              }}
            >
              <div style={{ fontSize: 40, marginBottom: 12 }}>📝</div>
              <div style={{ fontWeight: 600, color: T.text, marginBottom: 6 }}>No drafts yet</div>
              <div style={{ fontSize: 13 }}>Start designing a process and save it as a draft.</div>
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
}

function DeployedCard({
  processKey,
  latest,
  sortedVersions,
  canEdit,
  isDeleting,
  onDownload,
  onDelete,
}: {
  processKey: string
  latest: ProcessDef
  sortedVersions: ProcessDef[]
  canEdit: boolean
  isDeleting: boolean
  onDownload: () => void
  onDelete: () => void
}) {
  const displayName = latest.name || processKey
  const [hoverEdit, setHoverEdit] = useState(false)
  const [hoverDl, setHoverDl] = useState(false)
  const [hoverDel, setHoverDel] = useState(false)

  return (
    <div
      style={{
        background: T.card,
        border: '1px solid ' + T.border,
        borderRadius: 12,
        padding: 20,
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
      }}
    >
      {/* Top row: icon + name + version badge */}
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
        {/* Icon square */}
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 11,
            background: ACCENT + '1d',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <GitBranch size={20} color={ACCENT} strokeWidth={1.8} />
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
              {displayName}
            </span>
            <span
              style={{
                fontSize: 10,
                padding: '1px 6px',
                borderRadius: 99,
                background: ACCENT + '14',
                color: ACCENT,
                border: '1px solid ' + ACCENT + '44',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              v{latest.version}
            </span>
          </div>

          {/* Department tag pills */}
          {latest.owningDepartment && (
            <div style={{ display: 'flex', gap: 4, marginTop: 4, flexWrap: 'wrap' }}>
              <span style={{ ...tagPill(false), cursor: 'default' }}>
                {latest.owningDepartment}
              </span>
              {latest.category && (
                <span style={{ ...tagPill(false), cursor: 'default' }}>
                  {latest.category}
                </span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Stats row */}
      <div style={{ display: 'flex', gap: 16 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: T.text, lineHeight: 1.1 }}>0</div>
          <div style={{ fontSize: 10, color: T.muted, marginTop: 2 }}>Instances</div>
        </div>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: T.text, lineHeight: 1.1 }}>
            {sortedVersions.length}
          </div>
          <div style={{ fontSize: 10, color: T.muted, marginTop: 2 }}>
            Version{sortedVersions.length !== 1 ? 's' : ''}
          </div>
        </div>
      </div>

      {/* Start Workflow button */}
      <Link
        href={`/processes/start/${processKey}`}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 6,
          background: ACCENT,
          color: '#fff',
          borderRadius: 7,
          padding: '7px 14px',
          fontSize: 12,
          fontWeight: 600,
          textDecoration: 'none',
        }}
      >
        <Play size={12} strokeWidth={2.2} />
        Start Workflow
      </Link>

      {/* Icon action buttons */}
      <div style={{ display: 'flex', gap: 6 }}>
        {canEdit && (
          <Link
            href={`/processes/edit/${latest.id}`}
            style={{
              ...iconBtn,
              background: hoverEdit ? T.bg : '#fff',
              textDecoration: 'none',
            }}
            title="Edit process"
            onMouseEnter={() => setHoverEdit(true)}
            onMouseLeave={() => setHoverEdit(false)}
          >
            <Pencil size={13} color={T.muted} strokeWidth={2} />
          </Link>
        )}
        <button
          onClick={onDownload}
          style={{ ...iconBtn, background: hoverDl ? T.bg : '#fff' }}
          title="Download BPMN"
          onMouseEnter={() => setHoverDl(true)}
          onMouseLeave={() => setHoverDl(false)}
        >
          <Download size={13} color={T.muted} strokeWidth={2} />
        </button>
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

      {/* Steps grid */}
      <div
        style={{
          padding: 20,
          display: 'grid',
          gridTemplateColumns: 'repeat(5, 1fr)',
          gap: 0,
        }}
      >
        {GUIDE_STEPS.map((step, index) => {
          const StepIcon = step.Icon
          return (
            <div key={index} style={{ display: 'flex', alignItems: 'stretch' }}>
              <div
                style={{
                  flex: 1,
                  background: T.bg,
                  borderRadius: 10,
                  padding: '16px 12px',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  textAlign: 'center',
                  gap: 8,
                }}
              >
                {/* Step number circle */}
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    background: ACCENT + '1d',
                    color: ACCENT,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 12,
                    fontWeight: 700,
                  }}
                >
                  {step.num}
                </div>
                <StepIcon size={18} color={ACCENT} strokeWidth={1.8} />
                <div style={{ fontSize: 11, fontWeight: 700, color: T.text, lineHeight: 1.3 }}>
                  {step.title}
                </div>
                <div style={{ fontSize: 10, color: T.muted, lineHeight: 1.5 }}>
                  {step.desc}
                </div>
              </div>
              {/* Arrow between steps */}
              {index < GUIDE_STEPS.length - 1 && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 20,
                    flexShrink: 0,
                  }}
                >
                  <ChevronRight size={14} color={T.light} strokeWidth={2} />
                </div>
              )}
            </div>
          )
        })}
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
