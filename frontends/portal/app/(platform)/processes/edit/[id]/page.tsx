'use client'

import { useParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { getProcessDefinitionXml, getDraft, deleteDraft } from '@/lib/api/flowable'
import BpmnDesigner from '@/components/bpmn/BpmnDesigner'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'
import { useState } from 'react'

export default function EditProcessPage() {
  const params = useParams()
  const processDefinitionId = decodeURIComponent(params.id as string)
  const processKey = processDefinitionId.includes(':')
    ? processDefinitionId.split(':')[0]
    : processDefinitionId

  const { status: sessionStatus } = useSession()

  const [xmlToLoad, setXmlToLoad] = useState<string | null>(null)
  const [showDraftBanner, setShowDraftBanner] = useState(false)
  const [draftXml, setDraftXml] = useState<string | null>(null)
  // Bug 4 fix: increment this counter whenever the user switches XML source so
  // BpmnDesigner remounts with the new initialXml (the init effect only runs once on mount).
  const [loadKey, setLoadKey] = useState(0)

  const { isLoading, error } = useQuery({
    queryKey: ['processDefinition', processDefinitionId],
    queryFn: async () => {
      const deployedXml = await getProcessDefinitionXml(processDefinitionId)
      let draft = null
      try {
        draft = await getDraft(processKey)
      } catch (err) {
        console.warn('Failed to check for draft, proceeding with deployed version', err)
      }
      if (draft) {
        setDraftXml(draft.bpmnXml)
        setShowDraftBanner(true)
      }
      setXmlToLoad(deployedXml)
      return deployedXml
    },
    enabled: !!processDefinitionId && sessionStatus === 'authenticated',
    staleTime: 0,
  })

  const handleResumeDraft = () => {
    if (draftXml) {
      setXmlToLoad(draftXml)
      setLoadKey((k) => k + 1)
      setShowDraftBanner(false)
    }
  }

  const handleLoadDeployed = async () => {
    try {
      await deleteDraft(processKey)
    } catch (err) {
      console.warn('Failed to delete draft', err)
    }
    // Remount with the deployed XML (already in xmlToLoad from queryFn)
    setLoadKey((k) => k + 1)
    setShowDraftBanner(false)
  }

  if (isLoading) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-muted-foreground">Loading process definition...</p>
          </CardContent>
        </Card>
      </div>
    )
  }

  if (error) {
    return (
      <div className="container py-6">
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-destructive mb-4">
              Failed to load process: {error instanceof Error ? error.message : 'Unknown error'}
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
    <div className="h-screen flex flex-col">
      {showDraftBanner && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 flex items-center justify-between">
          <span className="text-sm text-amber-800">You have an unsaved draft for this process.</span>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" onClick={handleResumeDraft}>
              Resume draft
            </Button>
            <Button size="sm" variant="ghost" onClick={handleLoadDeployed}>
              Load deployed version
            </Button>
          </div>
        </div>
      )}
      {xmlToLoad && (
        <BpmnDesigner
          key={loadKey}
          initialXml={xmlToLoad}
          processId={processDefinitionId}
        />
      )}
    </div>
  )
}
