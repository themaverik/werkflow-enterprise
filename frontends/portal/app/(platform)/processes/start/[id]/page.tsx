'use client'

import { useParams, useRouter, useSearchParams } from 'next/navigation'
import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { ArrowLeft } from "lucide-react"
import Link from "next/link"
import { useToast } from "@/hooks/use-toast"
import { getProcessStartForm } from "@/lib/api/flowable"
import { startProcess } from "@/lib/api/workflows"
import FormJsViewer from "@/components/forms/FormJsViewer"
import { resolveFormData, resolveDependentData } from '@/lib/forms/resolveFormData'
import { apiClient } from '@/lib/api/client'
import { useSession } from 'next-auth/react'

export default function StartProcessPage() {
  const params = useParams()
  const router = useRouter()
  const searchParams = useSearchParams()
  const { toast } = useToast()
  const { status, data: session } = useSession()
  const prevFormValues = useRef<Record<string, any>>({})
  const processDefinitionId = decodeURIComponent(params.id as string)
  const [formData, setFormData] = useState<Record<string, any>>({})

  // Source-aware back navigation: consumers landing here from /services should
  // bounce back to /services on cancel (not /processes, which is admin-gated and
  // 403s for non-admin employees). The link source is passed as ?from=services.
  const fromSource = searchParams.get('from')
  const backHref = fromSource === 'services' ? '/services' : '/processes'
  const backLabel = fromSource === 'services' ? 'Back to Services' : 'Back to Processes'

  const { data: startForm, isLoading, error } = useQuery({
    queryKey: ['processStartForm', processDefinitionId],
    queryFn: () => getProcessStartForm(processDefinitionId),
    enabled: status === 'authenticated' && !!processDefinitionId,
    retry: false,
  })

  const { data: initialFormData } = useQuery({
    queryKey: ['startFormData', processDefinitionId],
    queryFn: () =>
      resolveFormData(
        startForm!.schema,
        (url, params) => apiClient.get(url, { params }).then((r) => r.data),
        (url) =>
          toast({
            title: 'Failed to load options',
            description: url,
            variant: 'destructive',
          })
      ),
    enabled: !!startForm,
  })

  useEffect(() => {
    if (initialFormData) setFormData(initialFormData)
  }, [initialFormData])

  const startProcessMutation = useMutation({
    mutationFn: async (variables: Record<string, any>) => {
      // Extract process definition key from the ID (format: key:version:hash)
      const processDefinitionKey = processDefinitionId.split(':')[0]
      return startProcess({
        processDefinitionKey,
        variables,
      })
    },
    onSuccess: (data) => {
      toast({
        title: 'Process Started',
        description: `Process instance ${data.processInstanceId} created successfully.`,
      })
      router.push('/requests')
    },
    onError: (error: Error) => {
      toast({
        title: 'Failed to Start Process',
        description: error.message || 'An error occurred while starting the process.',
        variant: 'destructive',
      })
    },
  })

  const handleFormChange = async (currentValues: Record<string, any>) => {
    // Only compare actual form field keys (not options-data keys like categoryOptions, assetDefinitions)
    // to avoid false positives from new array references on every render.
    const fieldKeys: string[] = (startForm?.schema?.components ?? [])
      .map((c: any) => c.key)
      .filter(Boolean)
    const changedKey = fieldKeys.find(
      (k) => currentValues[k] !== prevFormValues.current[k]
    )
    // Store a snapshot copy (not a reference) because form-js mutates its state
    // data object in-place inside _update before cloning, so any stored reference
    // would reflect the mutation and make the next comparison see no change.
    prevFormValues.current = Object.fromEntries(fieldKeys.map(k => [k, currentValues[k]]))

    if (!changedKey || !startForm) return

    const changedValue = currentValues[changedKey]
    const accessToken = (session as any)?.accessToken
    const dependentData = await resolveDependentData(
      startForm.schema,
      changedKey,
      changedValue,
      (url, params) => apiClient.get(url, { params }).then((r) => r.data),
      (url) =>
        toast({
          title: 'Failed to load options',
          description: url,
          variant: 'destructive',
        })
    )

    // Always remove changedKey from formData: its value is owned by form-js.
    // Keeping a stale null here would re-apply via _setState and override the
    // user's new selection on the next render.  Dependent data (options + null
    // clears for downstream fields) is merged in at the same time.
    setFormData((prev) => {
      const { [changedKey]: _, ...rest } = prev
      return { ...rest, ...dependentData }
    })
  }

  const handleFormSubmit = async (data: Record<string, any>) => {
    // Persist submitted field values in formData so that when form-js resets the
    // form after submit (its default behaviour), the useEffect re-injects them
    // and the form appears unchanged to the user on a failed submission.
    const fieldKeys: string[] = (startForm?.schema?.components ?? [])
      .map((c: any) => c.key)
      .filter(Boolean)
    setFormData((prev) => ({
      ...prev,
      ...Object.fromEntries(fieldKeys.map(k => [k, data[k]])),
    }))
    startProcessMutation.mutate(data)
  }

  if (isLoading) {
    return (
      <div className="container py-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-muted rounded w-1/3" />
          <div className="h-64 bg-muted rounded" />
        </div>
      </div>
    )
  }

  if (error || !startForm) {
    // No-start-form case: many BPMNs collect data on the first user task
    // instead of the start event (e.g. general-approval, onboarding-checklist).
    // Offer to start the process directly so the user lands on /requests and
    // can claim the first task from there.
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <h3 className="text-lg font-semibold mb-2">No Start Form</h3>
            <p className="text-muted-foreground mb-6">
              This process collects details on its first task. Start it now and complete
              the first step from your tasks list.
            </p>
            <div className="flex gap-2 justify-center">
              <Button asChild variant="outline">
                <Link href={backHref}>
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  {backLabel}
                </Link>
              </Button>
              <Button
                onClick={() => startProcessMutation.mutate({})}
                disabled={startProcessMutation.isPending}
              >
                {startProcessMutation.isPending ? 'Starting…' : 'Start Process Anyway'}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="container py-6 max-w-3xl">
      <Button variant="ghost" asChild className="mb-4">
        <Link href={backHref}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          {backLabel}
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>Start New Process</CardTitle>
          {startForm.description && (
            <CardDescription>{startForm.description}</CardDescription>
          )}
        </CardHeader>
        <CardContent>
          {initialFormData ? (
            <FormJsViewer
              schema={startForm.schema}
              data={{
                ...initialFormData,
                ...formData,
                ...(session?.user?.name ? { requesterName: session.user.name } : {}),
                ...(session?.user?.email ? { requesterEmail: session.user.email } : {}),
              }}
              onSubmit={handleFormSubmit}
              onChange={handleFormChange}
            />
          ) : (
            <div className="animate-pulse space-y-4">
              <div className="h-10 bg-muted rounded" />
              <div className="h-10 bg-muted rounded" />
              <div className="h-10 bg-muted rounded" />
            </div>
          )}
          {startProcessMutation.isPending && (
            <p className="text-sm text-muted-foreground mt-4">Starting process...</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
