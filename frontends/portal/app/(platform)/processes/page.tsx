'use client'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import Link from "next/link"
import { FileText, Plus, Trash2, Download, Eye, Activity, ExternalLink, Play, GitBranch, SlidersHorizontal, GitMerge, Rocket, ChevronRight, Pencil } from "lucide-react"
import { Tooltip, TooltipTrigger, TooltipContent } from "@/components/ui/tooltip"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { useSession } from "next-auth/react"
import { getProcessDefinitions, deleteDeployment, getProcessDefinitionXml, listDrafts, deleteDraft } from "@/lib/api/flowable"
import { downloadBpmn } from "@/lib/bpmn/utils"
import { useState } from "react"
import { useTranslations } from "next-intl"
import { ErrorDisplay, LoadingState } from "@/components/ui/error-display"
import { useAuthorization } from "@/lib/auth/use-authorization"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { useToast } from "@/hooks/use-toast"

const MANAGER_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'HR_ADMIN', 'FINANCE_ADMIN', 'PROCUREMENT_ADMIN', 'INVENTORY_ADMIN']

export default function ProcessesPage() {
  const t = useTranslations('processes')
  const { status } = useSession()
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deletingDraftKey, setDeletingDraftKey] = useState<string | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<{ title: string; description: string; onConfirm: () => void } | null>(null)
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const { hasAnyRole, getDepartment } = useAuthorization()

  const isManagerOrAbove = hasAnyRole(MANAGER_ROLES)
  const userDepartment = getDepartment()

  const canEditProcess = (owningDepartment?: string) => {
    if (!isManagerOrAbove) return false
    if (!owningDepartment) return isManagerOrAbove
    return hasAnyRole(['ADMIN', 'SUPER_ADMIN']) || owningDepartment === userDepartment
  }

  // Fetch process definitions
  const { data: processes, isLoading, error, refetch } = useQuery({
    queryKey: ['processDefinitions'],
    queryFn: getProcessDefinitions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
  })

  // Delete deployment mutation
  const deleteMutation = useMutation({
    mutationFn: deleteDeployment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['processDefinitions'] })
      setDeletingId(null)
      toast({ title: t('processDeleted'), description: t('processDeletedDesc') })
    },
    onError: (error: Error) => {
      toast({ title: t('deleteFailed'), description: error.message, variant: 'destructive' })
      setDeletingId(null)
    }
  })

  // Fetch process drafts — only for users who can design processes (WORKFLOW:DESIGN permission)
  const { data: drafts, isLoading: draftsLoading, error: draftsError } = useQuery({
    queryKey: ['processDrafts'],
    queryFn: listDrafts,
    enabled: status === 'authenticated' && isManagerOrAbove,
    retry: 2,
    retryDelay: 1000,
  })

  // Delete draft mutation
  const deleteDraftMutation = useMutation({
    mutationFn: deleteDraft,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['processDrafts'] })
      setDeletingDraftKey(null)
    },
    onError: (error: Error) => {
      toast({ title: t('deleteFailed'), description: error.message, variant: 'destructive' })
      setDeletingDraftKey(null)
    },
  })

  // Download BPMN XML
  const handleDownload = async (processDefinitionId: string, name: string) => {
    try {
      const xml = await getProcessDefinitionXml(processDefinitionId)
      downloadBpmn(xml, `${name}.bpmn20.xml`)
    } catch (error) {
      toast({ title: t('downloadFailed'), description: error instanceof Error ? error.message : 'Unknown error', variant: 'destructive' })
    }
  }

  // Group processes by key to show versions
  const groupedProcesses = processes?.reduce((acc, process) => {
    if (!acc[process.key]) {
      acc[process.key] = []
    }
    acc[process.key].push(process)
    return acc
  }, {} as Record<string, typeof processes>)

  return (
    <div className="container py-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('subtitle')}</p>
        </div>
        {isManagerOrAbove && (
          <Button asChild size="lg">
            <Link href="/processes/new">
              <Plus className="h-4 w-4 mr-2" />
              {t('createNewProcess')}
            </Link>
          </Button>
        )}
      </div>

      {/* Loading state */}
      {isLoading && <LoadingState message={t('loading')} className="mb-6" />}

      {/* Error state */}
      {error && !isLoading && (
        <ErrorDisplay
          error={error as Error}
          onRetry={() => refetch()}
          title={t('failedToLoad')}
          className="mb-6"
        />
      )}

      {/* Deployed Processes */}
      {!isLoading && groupedProcesses && Object.keys(groupedProcesses).length > 0 && (
        <div className="mb-6">
          <h2 className="text-xl font-semibold mb-4">{t('deployedProcesses')}</h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {Object.entries(groupedProcesses).map(([key, versions]) => {
              // Sort by version descending to get latest version
              const sortedVersions = [...versions].sort((a, b) => b.version - a.version)
              const latestVersion = sortedVersions[0]

              return (
                <Card key={key}>
                  <CardHeader>
                    <CardTitle className="text-lg flex items-center gap-2">
                      <FileText className="h-5 w-5" />
                      {latestVersion.name || key}
                    </CardTitle>
                    <CardDescription>
                      Version {latestVersion.version} • {versions.length} version{versions.length > 1 ? 's' : ''} deployed
                      {latestVersion.owningDepartment && ` • ${latestVersion.owningDepartment}`}
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-2">
                      <div className="flex gap-2">
                        {canEditProcess(latestVersion.owningDepartment) && (
                        <Button
                          asChild
                          variant="outline"
                          size="sm"
                          className="flex-1"
                        >
                          <Link href={`/processes/edit/${latestVersion.id}`}>
                            <Eye className="h-4 w-4 mr-2" />
                            Edit
                          </Link>
                        </Button>
                        )}
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleDownload(latestVersion.id, latestVersion.name || key)}
                        >
                          <Download className="h-4 w-4" />
                        </Button>
                        {canEditProcess(latestVersion.owningDepartment) && (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              size="sm"
                              style={{ backgroundColor: '#BA3920', color: 'white' }}
                              className="hover:opacity-90"
                              onClick={() => {
                                setPendingConfirm({
                                  title: t('deleteProcess'),
                                  description: `${t('deleteProcess')}: "${latestVersion.name || key}"? This action cannot be undone.`,
                                  onConfirm: () => {
                                    setDeletingId(latestVersion.deploymentId)
                                    deleteMutation.mutate(latestVersion.deploymentId)
                                  },
                                })
                              }}
                              disabled={deletingId === latestVersion.deploymentId}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Delete Process</TooltipContent>
                        </Tooltip>
                        )}
                      </div>
                      <Button
                        asChild
                        size="sm"
                        variant="secondary"
                        className="w-full"
                      >
                        <Link href={`/processes/start/${key}`}>
                          <Play className="h-4 w-4 mr-2" />
                          Start Process
                        </Link>
                      </Button>
                      {versions.length > 1 && (
                        <div className="text-xs text-muted-foreground">
                          <details className="cursor-pointer">
                            <summary>Show all versions</summary>
                            <div className="mt-2 space-y-1">
                              {sortedVersions.map((version) => (
                                <div key={version.id} className="flex justify-between items-center p-2 bg-muted/50 rounded">
                                  <span>v{version.version}</span>
                                  <div className="flex gap-1">
                                    <Button
                                      asChild
                                      variant="ghost"
                                      size="sm"
                                    >
                                      <Link href={`/processes/edit/${version.id}`}>
                                        Edit
                                      </Link>
                                    </Button>
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      onClick={() => handleDownload(version.id, version.name || key)}
                                    >
                                      <Download className="h-3 w-3" />
                                    </Button>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </details>
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        </div>
      )}

      {draftsError && !draftsLoading && isManagerOrAbove && (
        <ErrorDisplay
          error={draftsError as Error}
          title={t('failedToLoadDrafts')}
          className="mb-6"
        />
      )}

      {/* Drafts */}
      {drafts && drafts.length > 0 && (
        <div className="mb-6">
          <h2 className="text-xl font-semibold mb-4">{t('drafts')}</h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {drafts.map((draft) => {
              // Bug 5 fix: detect whether this draft is linked to a deployed process so
              // we can show a contextual warning before the user deletes it.
              const linkedDeployedProcess = groupedProcesses
                ? Object.values(groupedProcesses)
                    .flat()
                    .find((p) => p.key === draft.processKey)
                : undefined
              const deployedProcessName = linkedDeployedProcess?.name || draft.processKey

              return (
                <Card key={draft.processKey}>
                  <CardHeader>
                    <CardTitle className="text-lg flex items-center gap-2">
                      <FileText className="h-5 w-5" />
                      {draft.name || draft.processKey}
                    </CardTitle>
                    <CardDescription>
                      {draft.processKey}
                      {draft.updatedAt && ` • Last edited ${new Date(draft.updatedAt).toLocaleDateString()}`}
                      {linkedDeployedProcess && (
                        <span className="ml-1 inline-block rounded bg-amber-100 px-1.5 py-0.5 text-xs font-medium text-amber-800">
                          Draft for deployed process
                        </span>
                      )}
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    {isManagerOrAbove && (
                      <div className="flex gap-2">
                        <Button asChild variant="outline" size="sm" className="flex-1">
                          <Link href={`/processes/new?draft=${draft.processKey}`}>
                            <Pencil className="h-4 w-4 mr-2" />
                            Edit
                          </Link>
                        </Button>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              size="sm"
                              style={{ backgroundColor: '#BA3920', color: 'white' }}
                              className="hover:opacity-90"
                              onClick={() => {
                                const description = linkedDeployedProcess
                                  ? `This draft is linked to the deployed process "${deployedProcessName}". Deleting it will also remove the resume option from the edit page. This action cannot be undone.`
                                  : `${t('deleteDraft')}: "${draft.name || draft.processKey}"? This action cannot be undone.`
                                setPendingConfirm({
                                  title: t('deleteDraft'),
                                  description,
                                  onConfirm: () => {
                                    setDeletingDraftKey(draft.processKey)
                                    deleteDraftMutation.mutate(draft.processKey)
                                  },
                                })
                              }}
                              disabled={deletingDraftKey === draft.processKey}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Delete Draft</TooltipContent>
                        </Tooltip>
                      </div>
                    )}
                  </CardContent>
                </Card>
              )
            })}
          </div>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && (!processes || processes.length === 0) && (
        <Card className="mb-6">
          <CardContent className="py-12 text-center">
            <FileText className="h-12 w-12 mx-auto mb-4 text-muted-foreground" />
            <h3 className="text-lg font-semibold mb-2">{t('noProcessesDeployed')}</h3>
            <p className="text-muted-foreground mb-4">{t('noProcessesDesc')}</p>
            {isManagerOrAbove && (
              <Button asChild>
                <Link href="/processes/new">
                  <Plus className="h-4 w-4 mr-2" />
                  {t('createNewProcess')}
                </Link>
              </Button>
            )}
          </CardContent>
        </Card>
      )}


      {/* How to Create a Process Guide */}
      <Card className="border-2">
        <CardHeader className="bg-gradient-to-r from-emerald-50 to-teal-50 dark:from-emerald-950 dark:to-teal-950">
          <CardTitle className="flex items-center gap-2">
            <FileText className="h-6 w-6 text-emerald-600 dark:text-emerald-400" />
            How to Create a Process
          </CardTitle>
          <CardDescription>
            Five steps to design and deploy a BPMN workflow
          </CardDescription>
        </CardHeader>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
            {([
              { Icon: Plus,              title: 'Start with a Blank Process',   desc: 'Open the BPMN designer and begin on a blank canvas or import an existing file.',          bg: 'bg-emerald-100 dark:bg-emerald-900', text: 'text-emerald-600 dark:text-emerald-400', border: 'border-emerald-200 dark:border-emerald-800' },
              { Icon: GitBranch,         title: 'Design Your Workflow',         desc: 'Drag-and-drop tasks, gateways, and events to build the process flow visually.',          bg: 'bg-teal-100 dark:bg-teal-900',     text: 'text-teal-600 dark:text-teal-400',     border: 'border-teal-200 dark:border-teal-800'     },
              { Icon: SlidersHorizontal, title: 'Configure Task Properties',    desc: 'Set assignees, candidate groups, form keys, and DOA-level routing per task.',           bg: 'bg-cyan-100 dark:bg-cyan-900',     text: 'text-cyan-600 dark:text-cyan-400',     border: 'border-cyan-200 dark:border-cyan-800'     },
              { Icon: GitMerge,          title: 'Add Gateway Conditions',       desc: 'Wire exclusive gateways with JUEL expressions to route flows by amount or status.',      bg: 'bg-blue-100 dark:bg-blue-900',     text: 'text-blue-600 dark:text-blue-400',     border: 'border-blue-200 dark:border-blue-800'     },
              { Icon: Rocket,            title: 'Validate and Deploy',          desc: 'Hit Deploy — the BPMN is validated and live in the Flowable engine immediately.',        bg: 'bg-violet-100 dark:bg-violet-900', text: 'text-violet-600 dark:text-violet-400', border: 'border-violet-200 dark:border-violet-800' },
            ] as const).map((step, index, arr) => (
              <div key={index} className="relative flex items-stretch">
                <div className={`flex-1 rounded-xl border ${step.border} bg-card p-4 flex flex-col items-center text-center gap-3`}>
                  <div className={`flex h-11 w-11 items-center justify-center rounded-full ${step.bg} ${step.text} font-bold text-lg`}>
                    {index + 1}
                  </div>
                  <step.Icon className={`h-5 w-5 ${step.text}`} />
                  <p className="text-sm font-semibold leading-snug">{step.title}</p>
                  <p className="text-xs text-muted-foreground leading-relaxed">{step.desc}</p>
                </div>
                {index < arr.length - 1 && (
                  <div className="hidden lg:flex items-center justify-center w-4 shrink-0 text-muted-foreground">
                    <ChevronRight className="h-4 w-4" />
                  </div>
                )}
              </div>
            ))}
          </div>

          {isManagerOrAbove && (
            <div className="mt-6 p-4 bg-gradient-to-r from-emerald-50 to-teal-50 dark:from-emerald-950 dark:to-teal-950 rounded-lg border border-emerald-200 dark:border-emerald-800">
              <p className="text-sm text-muted-foreground mb-3">Ready to design your first workflow?</p>
              <Button asChild className="w-full sm:w-auto">
                <Link href="/processes/new">
                  <Plus className="h-4 w-4 mr-2" />
                  Create New Process
                </Link>
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {pendingConfirm && (
        <ConfirmDialog
          open={true}
          onOpenChange={(open) => { if (!open) setPendingConfirm(null) }}
          title={pendingConfirm.title}
          description={pendingConfirm.description}
          confirmLabel={t('delete')}
          onConfirm={pendingConfirm.onConfirm}
        />
      )}
    </div>
  )
}
