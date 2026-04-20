'use client'

import { useParams, useRouter } from 'next/navigation'
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
  const { toast } = useToast()
  const { status, data: session } = useSession()
  const prevFormValues = useRef<Record<string, any>>({})
  const processDefinitionId = decodeURIComponent(params.id as string)
  const [formData, setFormData] = useState<Record<string, any>>({})

  const { data: startForm, isLoading, error } = useQuery({
    queryKey: ['processStartForm', processDefinitionId],
    queryFn: () => getProcessStartForm(processDefinitionId),
    enabled: status === 'authenticated' && !!processDefinitionId,
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
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <h3 className="text-lg font-semibold mb-2">Start Form Not Available</h3>
            <p className="text-muted-foreground mb-4">
              {error instanceof Error ? error.message : 'This process does not have a start form configured.'}
            </p>
            <Button asChild>
              <Link href="/processes">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Processes
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="container py-6 max-w-3xl">
      <Button variant="ghost" asChild className="mb-4">
        <Link href="/processes">
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Processes
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
