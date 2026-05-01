'use client'

import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import BpmnDesigner from '@/components/bpmn/BpmnDesigner'
import { getDraft } from '@/lib/api/flowable'
import { Card, CardContent } from '@/components/ui/card'

function NewProcessContent() {
  const searchParams = useSearchParams()
  const draftKey = searchParams.get('draft')

  const [initialXml, setInitialXml] = useState<string | undefined>(undefined)
  const [draftProcessId, setDraftProcessId] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(!!draftKey)

  useEffect(() => {
    if (!draftKey) return

    getDraft(draftKey)
      .then((draft) => {
        if (draft) {
          setInitialXml(draft.bpmnXml)
          setDraftProcessId(draft.processKey)
        }
      })
      .catch((err) => {
        console.warn('Failed to load draft, starting with blank canvas:', err)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [draftKey])

  if (loading) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-muted-foreground">Loading draft...</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <BpmnDesigner
      initialXml={initialXml}
      processId={draftProcessId}
    />
  )
}

export default function NewProcessPage() {
  return (
    <Suspense fallback={
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-muted-foreground">Loading...</p>
          </CardContent>
        </Card>
      </div>
    }>
      <NewProcessContent />
    </Suspense>
  )
}
