'use client'

import { useParams, useRouter } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQuery } from '@tanstack/react-query'
import dynamic from 'next/dynamic'
import { toast } from 'sonner'
import { getFormDefinition } from '@/lib/api/flowable'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'

// @bpmn-io/form-js references browser-only globals at module load —
// dynamic-import with ssr:false keeps it out of the server bundle.
const FormJsViewer = dynamic(() => import('@/components/forms/FormJsViewer'), { ssr: false })

export default function PreviewFormPage() {
  const { status } = useSession()
  const params = useParams()
  const router = useRouter()
  const formKey = params.key as string

  const { data: formDef, isLoading, error } = useQuery({
    queryKey: ['formDefinition', formKey],
    queryFn: () => getFormDefinition(formKey),
    enabled: status === 'authenticated' && !!formKey,
  })

  if (isLoading) {
    return (
      <div className="container py-6 max-w-4xl">
        <p className="text-muted-foreground">Loading form...</p>
      </div>
    )
  }

  if (error || !formDef) {
    return (
      <div className="container py-6 max-w-4xl">
        <p className="text-destructive">Failed to load form.</p>
        <Button asChild variant="outline" size="sm" className="mt-4">
          <Link href="/forms"><ArrowLeft className="h-4 w-4 mr-2" />Back to Forms</Link>
        </Button>
      </div>
    )
  }

  let schema: any
  try {
    schema = typeof formDef.formJson === 'string' ? JSON.parse(formDef.formJson) : formDef.formJson
  } catch {
    return (
      <div className="container py-6 max-w-4xl">
        <p className="text-destructive">Failed to parse form schema.</p>
      </div>
    )
  }

  return (
    <div className="container py-6 max-w-4xl">
      <div className="mb-6 flex items-center gap-4">
        <Button asChild variant="outline" size="sm">
          <Link href="/forms">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Forms
          </Link>
        </Button>
        <h1 className="text-2xl font-bold">Form Preview</h1>
      </div>

      <Card>
        <CardContent className="pt-6">
          <FormJsViewer
            schema={schema}
            onSubmit={() => {
              toast.success('Form submitted (preview only — no data sent).')
            }}
          />
        </CardContent>
      </Card>
    </div>
  )
}
