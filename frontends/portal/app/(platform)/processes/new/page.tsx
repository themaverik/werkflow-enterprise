'use client'

import { useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import BpmnDesigner from '@/components/bpmn/BpmnDesigner'
import { getDraft } from '@/lib/api/flowable'
import { Card, CardContent } from '@/components/ui/card'

export default function NewProcessPage() {
  const searchParams = useSearchParams()
  const draftKey = searchParams.get('draft')

  const [initialXml, setInitialXml] = useState<string | undefined>(undefined)
  // null = not yet resolved; string = key to use as processId so Save Draft preserves it
  const [draftProcessId, setDraftProcessId] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(!!draftKey)

  useEffect(() => {
    if (!draftKey) return

    getDraft(draftKey)
      .then((draft) => {
        if (draft) {
          // Pass bpmnXml so BpmnDesigner seeds from it; use processKey as processId
          // so subsequent Save Draft calls reuse the same key (Bug 2 + Bug 3 fix).
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
