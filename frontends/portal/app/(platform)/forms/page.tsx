'use client'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import Link from "next/link"
import { FileText, Plus, Trash2, Download, Eye, Edit, Activity, ExternalLink, LayoutGrid, SlidersHorizontal, CloudUpload, Link2, ChevronRight } from "lucide-react"
import { Tooltip, TooltipTrigger, TooltipContent } from "@/components/ui/tooltip"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { useSession } from "next-auth/react"
import { getFormDefinitions, deleteFormDefinition } from "@/lib/api/flowable"
import { useState } from "react"
import { useTranslations } from "next-intl"
import { ErrorDisplay, LoadingState } from "@/components/ui/error-display"
import { useAuthorization } from "@/lib/auth/use-authorization"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"

const MANAGER_ROLES = ['ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN', 'WORKFLOW_DESIGNER', 'HR_ADMIN', 'FINANCE_ADMIN', 'PROCUREMENT_ADMIN', 'INVENTORY_ADMIN']

export default function FormsPage() {
  const t = useTranslations('forms')
  const { status } = useSession()
  const [deletingKey, setDeletingKey] = useState<string | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<{ title: string; description: string; onConfirm: () => void } | null>(null)
  const queryClient = useQueryClient()
  const { hasAnyRole, getDepartment } = useAuthorization()

  const isManagerOrAbove = hasAnyRole(MANAGER_ROLES)
  const userDepartment = getDepartment()

  const canEditForm = (form: any) => {
    if (!isManagerOrAbove) return false
    if (!form.owningDepartment) return isManagerOrAbove // unowned: any manager
    return hasAnyRole(['ADMIN', 'SUPER_ADMIN']) || form.owningDepartment === userDepartment
  }

  // Fetch form definitions
  const { data: forms, isLoading, error, refetch } = useQuery({
    queryKey: ['formDefinitions'],
    queryFn: getFormDefinitions,
    enabled: status === 'authenticated',
    retry: 2,
    retryDelay: 1000,
  })

  // Delete form mutation
  const deleteMutation = useMutation({
    mutationFn: deleteFormDefinition,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['formDefinitions'] })
      setDeletingKey(null)
      alert('Form deleted successfully')
    },
    onError: (error: Error) => {
      alert(`Failed to delete form: ${error.message}`)
      setDeletingKey(null)
    }
  })

  // Download form JSON
  const handleDownload = (formKey: string, formJson: string) => {
    const blob = new Blob([formJson], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${formKey}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  return (
    <div className="container py-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('subtitle')}</p>
        </div>
        {isManagerOrAbove && (
          <Button asChild size="lg">
            <Link href="/forms/new">
              <Plus className="h-4 w-4 mr-2" />
              {t('createNewForm')}
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

      {/* Deployed Forms */}
      {!isLoading && forms && forms.length > 0 && (
        <div className="mb-6">
          <h2 className="text-xl font-semibold mb-4">{t('deployedForms')}</h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {forms.map((form) => (
              <Card key={form.key}>
                <CardHeader>
                  <CardTitle className="text-lg flex items-center gap-2">
                    <FileText className="h-5 w-5" />
                    {form.name}
                  </CardTitle>
                  <CardDescription className="font-mono text-xs">
                    Key: {form.key}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    {form.owningDepartment && (
                      <p className="text-xs text-muted-foreground">Dept: {form.owningDepartment}</p>
                    )}
                    {canEditForm(form) && (
                      <div className="flex gap-2">
                        <Button asChild variant="outline" size="sm" className="flex-1">
                          <Link href={`/forms/edit/${form.key}`}>
                            <Edit className="h-4 w-4 mr-2" />
                            Edit
                          </Link>
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleDownload(form.key, form.formJson)}
                        >
                          <Download className="h-4 w-4" />
                        </Button>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              size="sm"
                              style={{ backgroundColor: '#BA3920', color: 'white' }}
                              className="hover:opacity-90"
                              onClick={() => {
                                setPendingConfirm({
                                  title: t('deleteForm'),
                                  description: `${t('deleteForm')}: "${form.name}"? This action cannot be undone.`,
                                  onConfirm: () => {
                                    setDeletingKey(form.key)
                                    deleteMutation.mutate(form.key)
                                  },
                                })
                              }}
                              disabled={deletingKey === form.key}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Delete Form</TooltipContent>
                        </Tooltip>
                      </div>
                    )}
                    <Button asChild variant="secondary" size="sm" className="w-full">
                      <Link href={`/forms/preview/${form.key}`}>
                        <Eye className="h-4 w-4 mr-2" />
                        Preview
                      </Link>
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && (!forms || forms.length === 0) && (
        <Card className="mb-6">
          <CardContent className="py-12 text-center">
            <FileText className="h-12 w-12 mx-auto mb-4 text-muted-foreground" />
            <h3 className="text-lg font-semibold mb-2">{t('noFormsCreated')}</h3>
            <p className="text-muted-foreground mb-4">{t('noFormsDesc')}</p>
            {isManagerOrAbove && (
              <Button asChild>
                <Link href="/forms/new">
                  <Plus className="h-4 w-4 mr-2" />
                  {t('createNewForm')}
                </Link>
              </Button>
            )}
          </CardContent>
        </Card>
      )}


      {/* How to Create a Form Guide */}
      <Card className="border-2">
        <CardHeader className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-950 dark:to-indigo-950">
          <CardTitle className="flex items-center gap-2">
            <FileText className="h-6 w-6 text-blue-600 dark:text-blue-400" />
            How to Create a Form
          </CardTitle>
          <CardDescription>
            Six steps to build and link a custom form to your workflow
          </CardDescription>
        </CardHeader>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {([
              { Icon: Plus,              title: 'Click Create New Form',    desc: 'Hit "Create New Form" to open the form-js visual designer.',                              bg: 'bg-blue-100 dark:bg-blue-900',    text: 'text-blue-600 dark:text-blue-400',    border: 'border-blue-200 dark:border-blue-800'    },
              { Icon: LayoutGrid,        title: 'Design Your Form',         desc: 'Drag-and-drop 20+ field types — text, dropdowns, dates, files, and more.',               bg: 'bg-purple-100 dark:bg-purple-900',text: 'text-purple-600 dark:text-purple-400',border: 'border-purple-200 dark:border-purple-800'},
              { Icon: SlidersHorizontal, title: 'Configure Field Settings', desc: 'Set labels, placeholders, validation rules, and conditional visibility logic.',          bg: 'bg-green-100 dark:bg-green-900',  text: 'text-green-600 dark:text-green-400',  border: 'border-green-200 dark:border-green-800'  },
              { Icon: Eye,               title: 'Preview and Test',         desc: 'Switch to preview mode, fill the form, and verify validations work as expected.',        bg: 'bg-orange-100 dark:bg-orange-900',text: 'text-orange-600 dark:text-orange-400',border: 'border-orange-200 dark:border-orange-800'},
              { Icon: CloudUpload,       title: 'Save and Deploy',          desc: 'Enter a unique form key and save — the schema is deployed and immediately available.',   bg: 'bg-indigo-100 dark:bg-indigo-900',text: 'text-indigo-600 dark:text-indigo-400',border: 'border-indigo-200 dark:border-indigo-800'},
              { Icon: Link2,             title: 'Link to Workflow',         desc: 'Set the form key on a BPMN task in the Process Designer to attach the form to a step.',  bg: 'bg-pink-100 dark:bg-pink-900',    text: 'text-pink-600 dark:text-pink-400',    border: 'border-pink-200 dark:border-pink-800'    },
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
                {index < arr.length - 1 && index % 3 !== 2 && (
                  <div className="hidden lg:flex items-center justify-center w-4 shrink-0 text-muted-foreground">
                    <ChevronRight className="h-4 w-4" />
                  </div>
                )}
              </div>
            ))}
          </div>

          {isManagerOrAbove && (
            <div className="mt-6 p-4 bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-950 dark:to-indigo-950 rounded-lg border border-blue-200 dark:border-blue-800">
              <p className="text-sm text-muted-foreground mb-3">Ready to create your first form?</p>
              <Button asChild className="w-full sm:w-auto">
                <Link href="/forms/new">
                  <Plus className="h-4 w-4 mr-2" />
                  Create New Form
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
