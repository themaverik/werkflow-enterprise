'use client'

import Link from 'next/link'
import { Plus, Search, FileText } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import {
  getProcessDefinitions, deleteDeployment,
  listDrafts, deleteDraft,
} from '@/lib/api/flowable'
import { useState, useEffect, useRef } from 'react'
import { useTranslations } from 'next-intl'
import { useAuthorization } from '@/lib/auth/use-authorization'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { toast } from 'sonner'
import { Skeleton } from '@/components/ui/skeleton'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { FilterPills } from '@/components/ui/filter-pills'
import { cn } from '@/lib/utils'
import { DeployedCard } from './_components/DeployedCard'
import { DraftCard } from './_components/DraftCard'
import { GuideSection } from './_components/GuideSection'
import { type ProcessDef, getProcessTags } from './_utils/tagColors'

// ─── Role constants ───────────────────────────────────────────────────────────
const MANAGER_ROLES = [
  'ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER',
  'HR_ADMIN', 'FINANCE_ADMIN', 'PROCUREMENT_ADMIN', 'INVENTORY_ADMIN',
]

// ─── Page ─────────────────────────────────────────────────────────────────────
export default function ProcessesPage() {
  const t = useTranslations('processes')
  const { status } = useSession()
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deletingDraftKey, setDeletingDraftKey] = useState<string | null>(null)
  const processesErrorShown = useRef(false)
  const draftsErrorShown = useRef(false)
  const [pendingConfirm, setPendingConfirm] = useState<{
    title: string
    description: string
    onConfirm: () => void
  } | null>(null)
  const [activeView, setActiveView] = useState<'deployed' | 'drafts'>('deployed')
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTag, setActiveTag] = useState<string | null>(null)
  const queryClient = useQueryClient()
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
  // useRef guard prevents re-firing on every re-render when error is persistent
  useEffect(() => {
    if (processesError && !processesErrorShown.current) {
      processesErrorShown.current = true
      toast.error(t('failedToLoad') + ': ' + (processesError as Error).message)
    }
  }, [processesError, t])

  useEffect(() => {
    if (draftsError && !draftsErrorShown.current) {
      draftsErrorShown.current = true
      toast.error(t('failedToLoadDrafts') + ': ' + (draftsError as Error).message)
    }
  }, [draftsError, t])

  // ── Mutations ────────────────────────────────────────────────────────────────
  const deleteMutation = useMutation({
    mutationFn: deleteDeployment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['processDefinitions'] })
      setDeletingId(null)
      toast.success(t('processDeleted'), { description: t('processDeletedDesc') })
    },
    onError: (error: Error) => {
      toast.error(t('deleteFailed'), { description: error.message })
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
      toast.error(t('deleteFailed'), { description: error.message })
      setDeletingDraftKey(null)
    },
  })

  // ── Grouping ─────────────────────────────────────────────────────────────────
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
    <div className="p-7">

      {/* ── Header row: tabs + new button ───────────────────────────────────── */}
      <div className="flex items-center gap-2.5 mb-[18px]">
        {/* Tab: Deployed */}
        <button
          onClick={() => setActiveView('deployed')}
          className={cn(
            'px-3.5 py-1.5 rounded-lg text-[13px] font-semibold cursor-pointer transition-all',
            activeView === 'deployed'
              ? 'border border-primary bg-primary/[0.08] text-primary'
              : 'border border-border bg-white text-muted-foreground',
          )}
        >
          Deployed ({deployedCount})
        </button>

        {/* Tab: Drafts — only visible to managers */}
        {isManagerOrAbove && (
          <button
            onClick={() => setActiveView('drafts')}
            className={cn(
              'px-3.5 py-1.5 rounded-lg text-[13px] font-semibold cursor-pointer transition-all',
              activeView === 'drafts'
                ? 'border border-primary bg-primary/[0.08] text-primary'
                : 'border border-border bg-white text-muted-foreground',
            )}
          >
            Drafts ({draftsCount})
          </button>
        )}

        {/* Spacer */}
        <div className="flex-1" />

        {/* + New Process */}
        {isManagerOrAbove && (
          <Button asChild size="sm">
            <Link href="/processes/new">
              <Plus size={14} strokeWidth={2.2} className="mr-1.5" />
              {t('newProcess')}
            </Link>
          </Button>
        )}
      </div>

      {/* ── Search + tag filter bar ──────────────────────────────────────────── */}
      <div className="flex items-center border border-border rounded-[10px] bg-white overflow-hidden mb-[22px] h-10">
        {/* Search input */}
        <div className="flex items-center gap-2 px-3 shrink-0">
          <Search size={14} className="text-muted-foreground" strokeWidth={2} />
          <input
            type="text"
            placeholder="Search processes…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="border-0 outline-none text-[13px] text-foreground bg-transparent w-44"
          />
        </div>

        {/* Divider */}
        {allTags.length > 0 && <div className="w-px h-6 bg-border shrink-0" />}

        {/* Tag pills — horizontal scroll */}
        {allTags.length > 0 && (
          <div className="flex items-center gap-1.5 px-3 overflow-x-auto flex-1">
            <span className="text-[10px] text-muted-foreground shrink-0">Filter:</span>
            <FilterPills
              options={[{ key: '', label: 'All' }, ...allTags.map((tag) => ({ key: tag, label: tag }))]}
              active={activeTag ?? ''}
              onChange={(key) => setActiveTag(key === '' ? null : key)}
            />
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
            <EmptyState
              icon={FileText}
              title={t('noProcessesDeployed')}
              description={t('noProcessesDesc')}
              action={isManagerOrAbove ? (
                <Button asChild variant="outline" size="sm">
                  <Link href="/processes/new">
                    <Plus size={13} strokeWidth={2} className="mr-1" />
                    {t('createNewProcess')}
                  </Link>
                </Button>
              ) : undefined}
            />
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
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
            <EmptyState
              icon={FileText}
              title="No drafts yet"
              description="Start designing a process and save it as a draft."
              action={
                <Button asChild variant="outline" size="sm">
                  <Link href="/processes/new">
                    <Plus size={13} strokeWidth={2} className="mr-1" />
                    Create your first process
                  </Link>
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
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
